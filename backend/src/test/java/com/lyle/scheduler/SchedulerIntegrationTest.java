package com.lyle.scheduler;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.quartz.*;
import java.util.*;
import java.time.Duration;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class SchedulerIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired JobService service;
 @Autowired ExecutionRepository executions;
 @Autowired TaskRepository tasks;
 @Autowired Scheduler scheduler;
 @Autowired ExecutionRecorder recorder;
 @Autowired HttpCheckRunner http;
 static HttpServer fixture;
 static ExecutorService fixtureThreads;
 static AtomicInteger redirectDestinationHits=new AtomicInteger();
 @BeforeAll static void startHttpFixture() throws Exception {
  fixture=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  fixtureThreads=Executors.newVirtualThreadPerTaskExecutor();fixture.setExecutor(fixtureThreads);
  fixture.createContext("/ok",exchange->{
   if(!exchange.getRequestMethod().equals("GET")) { exchange.sendResponseHeaders(405,-1);exchange.close();return; }
   exchange.sendResponseHeaders(200,0);exchange.getResponseBody().write("healthy".getBytes());exchange.close();
  });
  fixture.createContext("/unavailable",exchange->{exchange.sendResponseHeaders(503,-1);exchange.close();});
  fixture.createContext("/destination",exchange->{redirectDestinationHits.incrementAndGet();exchange.sendResponseHeaders(200,-1);exchange.close();});
  fixture.createContext("/redirect",exchange->{exchange.getResponseHeaders().add("Location","/destination");exchange.sendResponseHeaders(302,-1);exchange.close();});
  fixture.createContext("/slow",exchange->{
   try { Thread.sleep(2200);exchange.sendResponseHeaders(200,-1); }
   catch(InterruptedException e) { Thread.currentThread().interrupt(); }
   catch(java.io.IOException ignored) { /* A timed-out client is expected to disconnect. */ }
   finally {exchange.close();}
  });
  fixture.createContext("/body-stalls",exchange->{
   try { exchange.sendResponseHeaders(200,1000000);exchange.getResponseBody().flush();Thread.sleep(4000); }
   catch(InterruptedException e) {Thread.currentThread().interrupt();}
   finally {exchange.close();}
  });
  fixture.start();
 }
 @AfterAll static void stopHttpFixture() {fixture.stop(0);fixtureThreads.shutdownNow();}
 private String url(String path) {return "http://127.0.0.1:"+fixture.getAddress().getPort()+path;}
 private ScheduledTask httpJob(String path,int expected,int timeout) throws Exception {
  return service.create(new JobRequest("HTTP check",TaskType.HTTP_HEALTH_CHECK,"0 0 0 1 1 ? 2099","UTC",url(path),expected,timeout));
 }
 private Execution waitForRun(UUID id,String status) {
  await().atMost(Duration.ofSeconds(10)).untilAsserted(()->{
   var list=executions.findByJobIdOrderByStartedAtDesc(id,PageRequest.of(0,1));
   assertThat(list).hasSize(1);assertThat(list.getFirst().status).isEqualTo(status);
  });
  return executions.findByJobIdOrderByStartedAtDesc(id,PageRequest.of(0,1)).getFirst();
 }
 @BeforeEach void clean() throws Exception { scheduler.clear();executions.deleteAll();tasks.deleteAll(); }
 private ScheduledTask create(TaskType type) throws Exception {
  return service.create(new JobRequest("Test job",type,"0 0 0 1 1 ? 2099","Europe/London"));
 }
 @Test void createsValidScheduleAndRejectsBadInput() throws Exception {
  mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
   new JobRequest("Report",TaskType.REPORT_DEMO,"0 */5 * * * ?","Europe/London"))))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Report"));
  assertThat(tasks.count()).isEqualTo(1);
  mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
   new JobRequest("Bad",TaskType.REPORT_DEMO,"nonsense","Europe/London")))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
   new JobRequest("Bad",TaskType.REPORT_DEMO,"0 */5 * * * ?","Mars/Olympus")))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
   new JobRequest("  ",TaskType.REPORT_DEMO,"0 */5 * * * ?","UTC")))).andExpect(status().isBadRequest());
  assertThat(tasks.count()).isEqualTo(1);
 }
 @Test void manualSuccessPersistsHistory() throws Exception {
  var task=create(TaskType.REPORT_DEMO);
  mvc.perform(post("/api/jobs/"+task.id+"/run")).andExpect(status().isAccepted());
  await().atMost(Duration.ofSeconds(10)).untilAsserted(()->{
   var list=executions.findByJobIdOrderByStartedAtDesc(task.id,PageRequest.of(0,10));
   assertThat(list).hasSize(1); assertThat(list.getFirst().status).isEqualTo("SUCCESS");
   assertThat(list.getFirst().durationMs).isGreaterThanOrEqualTo(1800);
   assertThat(list.getFirst().source).isEqualTo("MANUAL");
  });
  mvc.perform(get("/api/executions").param("jobId",task.id.toString()))
   .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("SUCCESS"));
 }
 @Test void failureIsRecordedWithUsefulError() throws Exception {
  var task=create(TaskType.FAILURE_DEMO);service.run(task.id);
  await().atMost(Duration.ofSeconds(10)).untilAsserted(()->{
   var list=executions.findByJobIdOrderByStartedAtDesc(task.id,PageRequest.of(0,10));
   assertThat(list).hasSize(1);assertThat(list.getFirst().status).isEqualTo("FAILED");
   assertThat(list.getFirst().message).contains("Intentional demo failure");
  });
 }
 @Test void pauseRemovesScheduleAndResumeRestoresIt() throws Exception {
  var task=create(TaskType.REPORT_DEMO);service.enabled(task.id,false);
  assertThat(scheduler.getTrigger(TriggerKey.triggerKey(task.id.toString()))).isNull();
  mvc.perform(post("/api/jobs/"+task.id+"/run")).andExpect(status().isConflict());
  service.enabled(task.id,true);
  assertThat(scheduler.getTrigger(TriggerKey.triggerKey(task.id.toString()))).isNotNull();
 }
 @Test void unknownJobAndInvalidLimitReturnClientErrors() throws Exception {
  mvc.perform(post("/api/jobs/"+UUID.randomUUID()+"/run")).andExpect(status().isNotFound());
  mvc.perform(get("/api/executions").param("limit","201")).andExpect(status().isBadRequest());
 }
 @Test void repeatedManualRunsAreSerialized() throws Exception {
  var task=create(TaskType.REPORT_DEMO);service.run(task.id);service.run(task.id);
  await().atMost(Duration.ofSeconds(12)).untilAsserted(()->{
   var list=executions.findByJobIdOrderByStartedAtDesc(task.id,PageRequest.of(0,10));
   assertThat(list).hasSize(2);assertThat(list).allSatisfy(r->assertThat(r.status).isEqualTo("SUCCESS"));
   var ordered=list.stream().sorted(Comparator.comparing(r->r.startedAt)).toList();
   assertThat(ordered.get(1).startedAt).isAfterOrEqualTo(ordered.get(0).finishedAt);
  });
 }
 @Test void restartRecoveryPreservesUncertainty() throws Exception {
  var task=create(TaskType.REPORT_DEMO);var run=recorder.start(task,"SCHEDULED");recorder.recoverInterrupted();
  var recovered=executions.findById(run.id).orElseThrow();
  assertThat(recovered.status).isEqualTo("INTERRUPTED");assertThat(recovered.durationMs).isNull();
 }
 @Test void quartzFiresCronWithoutManualRequest() throws Exception {
  var task=service.create(new JobRequest("Automatic",TaskType.REPORT_DEMO,"0/3 * * * * ?","UTC"));
  try {
   await().atMost(Duration.ofSeconds(12)).untilAsserted(()->{
    var list=executions.findByJobIdOrderByStartedAtDesc(task.id,PageRequest.of(0,10));
    assertThat(list).anySatisfy(r->{assertThat(r.status).isEqualTo("SUCCESS");assertThat(r.source).isEqualTo("SCHEDULED");});
   });
  } finally {
   service.enabled(task.id,false);
   await().atMost(Duration.ofSeconds(10)).until(()->scheduler.getCurrentlyExecutingJobs().isEmpty());
  }
 }
 @Test void rebuildsPersistedDefinitionsWithoutReenablingPausedJobs() throws Exception {
  var active=create(TaskType.REPORT_DEMO);var paused=create(TaskType.REPORT_DEMO);service.enabled(paused.id,false);
  scheduler.clear();
  for(var task:tasks.findAll())service.register(task);
  assertThat(scheduler.getTrigger(TriggerKey.triggerKey(active.id.toString()))).isNotNull();
  assertThat(scheduler.getTrigger(TriggerKey.triggerKey(paused.id.toString()))).isNull();
  assertThat(scheduler.checkExists(JobKey.jobKey(paused.id.toString()))).isTrue();
 }

 @Test void httpSuccessIsPersistedAndReturnedByApi() throws Exception {
  var task=httpJob("/ok",200,5);service.run(task.id);
  var run=waitForRun(task.id,"SUCCESS");
  assertThat(run.httpStatus).isEqualTo(200);assertThat(run.responseTimeMs).isNotNegative();
  assertThat(run.targetUrl).isEqualTo(url("/ok"));assertThat(run.expectedStatus).isEqualTo(200);
  assertThat(run.errorCategory).isNull();
  mvc.perform(get("/api/executions").param("jobId",task.id.toString()))
   .andExpect(status().isOk()).andExpect(jsonPath("$[0].httpStatus").value(200))
   .andExpect(jsonPath("$[0].targetUrl").value(url("/ok")));
  assertThat(service.jobs().getFirst().targetUrl()).isEqualTo(url("/ok"));
 }
 @Test void unexpectedHttpStatusIsAnInspectableFailure() throws Exception {
  var task=httpJob("/unavailable",200,5);service.run(task.id);
  var run=waitForRun(task.id,"FAILED");
  assertThat(run.httpStatus).isEqualTo(503);assertThat(run.errorCategory).isEqualTo("HTTP_STATUS");
  assertThat(run.message).contains("expected 200");assertThat(run.responseTimeMs).isNotNull();
 }
 @Test void configuredExpectedStatusIsHonoured() throws Exception {
  var task=httpJob("/unavailable",503,5);
  assertThat(http.run(task).status()).isEqualTo("SUCCESS");
 }
 @Test void delayedHeadersTimeOutAndKeepHttpStatusUnknown() throws Exception {
  var task=httpJob("/slow",200,1);service.run(task.id);
  var run=waitForRun(task.id,"FAILED");
  assertThat(run.errorCategory).isEqualTo("TIMEOUT");assertThat(run.httpStatus).isNull();
  assertThat(run.responseTimeMs).isNull();assertThat(run.durationMs).isBetween(850L,8000L);
 }
 @Test void redirectsAreReportedWithoutFollowingTheDestination() throws Exception {
  var task=httpJob("/redirect",200,5);redirectDestinationHits.set(0);
  var result=http.run(task);
  assertThat(result.status()).isEqualTo("FAILED");assertThat(result.httpStatus()).isEqualTo(302);
  assertThat(redirectDestinationHits.get()).isZero();
 }
 @Test void refusedConnectionHasNoInventedHttpStatus() throws Exception {
  int unused;
  try(var socket=new ServerSocket(0,0,java.net.InetAddress.getLoopbackAddress())) {unused=socket.getLocalPort();}
  var task=service.create(new JobRequest("Offline",TaskType.HTTP_HEALTH_CHECK,"0 0 0 1 1 ? 2099","UTC","http://127.0.0.1:"+unused+"/",200,2));
  service.run(task.id);var run=waitForRun(task.id,"FAILED");
  assertThat(run.errorCategory).isEqualTo("CONNECTION");assertThat(run.httpStatus).isNull();
 }
 @Test void responseBodyIsNotDownloadedOrWaitedFor() throws Exception {
  var task=httpJob("/body-stalls",200,1);long start=System.nanoTime();
  var result=http.run(task);
  assertThat(result.status()).isEqualTo("SUCCESS");
  assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(2));
 }
 @Test void httpConfigurationValidationAndDefaultsAreApplied() throws Exception {
  for(String invalid:List.of("file:///etc/passwd","ftp://example.com","http://user:secret@example.com/","https://example.com/#fragment","http://example.com:70000/","not a URL")) {
   mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
    new JobRequest("Invalid",TaskType.HTTP_HEALTH_CHECK,"0 */5 * * * ?","UTC",invalid,200,5))))
    .andExpect(status().isBadRequest());
  }
  mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
   new JobRequest("Missing URL",TaskType.HTTP_HEALTH_CHECK,"0 */5 * * * ?","UTC")))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
   new JobRequest("Bad timeout",TaskType.HTTP_HEALTH_CHECK,"0 */5 * * * ?","UTC",url("/ok"),200,0))))
   .andExpect(status().isBadRequest());
  mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
   new JobRequest("Bad status",TaskType.HTTP_HEALTH_CHECK,"0 */5 * * * ?","UTC",url("/ok"),600,5))))
   .andExpect(status().isBadRequest());
  mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
   new JobRequest("Defaults",TaskType.HTTP_HEALTH_CHECK,"0 */5 * * * ?","UTC",url("/ok"),null,null))))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.expectedStatus").value(200))
   .andExpect(jsonPath("$.timeoutSeconds").value(5));
  assertThat(tasks.count()).isEqualTo(1);
 }
 @Test void migrationUpgradesExistingDefinitionsAndHistory() throws Exception {
  String jdbc="jdbc:h2:mem:upgrade;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
  org.flywaydb.core.Flyway.configure().dataSource(jdbc,"sa","").target("1").load().migrate();
  UUID jobId=UUID.randomUUID(),runId=UUID.randomUUID();
  try(var connection=java.sql.DriverManager.getConnection(jdbc,"sa","");var statement=connection.createStatement()) {
   statement.execute("insert into scheduled_jobs(id,name,task_type,cron_expression,zone_id,enabled,created_at) values ('"+jobId+"','Existing job','REPORT_DEMO','0 * * * * ?','UTC',true,current_timestamp)");
   statement.execute("insert into job_executions(id,job_id,job_name,status,source,started_at) values ('"+runId+"','"+jobId+"','Existing job','SUCCESS','MANUAL',current_timestamp)");
  }
  org.flywaydb.core.Flyway.configure().dataSource(jdbc,"sa","").load().migrate();
  try(var connection=java.sql.DriverManager.getConnection(jdbc,"sa","");var statement=connection.createStatement();var rs=statement.executeQuery("select j.name,e.status,e.http_status from scheduled_jobs j join job_executions e on e.job_id=j.id")) {
   assertThat(rs.next()).isTrue();assertThat(rs.getString(1)).isEqualTo("Existing job");
   assertThat(rs.getString(2)).isEqualTo("SUCCESS");assertThat(rs.getObject(3)).isNull();
  }
 }

}
