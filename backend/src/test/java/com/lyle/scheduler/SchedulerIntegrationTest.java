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

}
