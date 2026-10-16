package com.lyle.scheduler;
import org.quartz.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.PageRequest;
import java.time.*;
import java.util.*;
@Service
public class JobService {
 private final TaskRepository tasks;
 private final ExecutionRepository executions;
 private final Scheduler scheduler;
 public JobService(TaskRepository tasks,ExecutionRepository executions,Scheduler scheduler) {
  this.tasks=tasks; this.executions=executions; this.scheduler=scheduler;
 }
 public static void validate(JobRequest request) {
  if(!CronExpression.isValidExpression(request.cronExpression())) throw new IllegalArgumentException("Invalid Quartz cron expression. Use six fields, e.g. 0 */5 * * * ?");
  ZoneId.of(request.zoneId());
 }
 public synchronized ScheduledTask create(JobRequest request) throws SchedulerException {
  validate(request);
  var task=tasks.saveAndFlush(new ScheduledTask(request.name().strip(),request.taskType(),request.cronExpression(),request.zoneId()));
  try { register(task); }
  catch(SchedulerException | RuntimeException e) { tasks.deleteById(task.id); throw e; }
  return task;
 }
 public void register(ScheduledTask task) throws SchedulerException {
  var detail=JobBuilder.newJob(TaskJob.class).withIdentity(task.id.toString()).storeDurably().build();
  scheduler.addJob(detail,true);
  if(task.enabled) scheduler.scheduleJob(TriggerBuilder.newTrigger().withIdentity(task.id.toString()).forJob(detail)
   .withSchedule(CronScheduleBuilder.cronSchedule(task.cronExpression)
    .inTimeZone(TimeZone.getTimeZone(ZoneId.of(task.zoneId)))
    .withMisfireHandlingInstructionDoNothing()).build());
 }
 public ScheduledTask get(UUID id) {
  return tasks.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Job not found"));
 }
 public synchronized void run(UUID id) throws SchedulerException {
  var task=get(id);
  if(!task.enabled) throw new ResponseStatusException(HttpStatus.CONFLICT,"Resume the job before running it.");
  var data=new JobDataMap(); data.put("source","MANUAL");
  // Quartz serializes executions by JobKey. Repeated requests may queue, never overlap.
  scheduler.triggerJob(JobKey.jobKey(id.toString()),data);
 }
 public synchronized void enabled(UUID id,boolean enabled) throws SchedulerException {
  var task=get(id); if(task.enabled==enabled) return;
  var key=TriggerKey.triggerKey(id.toString());
  if(enabled) {
   scheduler.scheduleJob(TriggerBuilder.newTrigger().withIdentity(id.toString()).forJob(JobKey.jobKey(id.toString()))
    .withSchedule(CronScheduleBuilder.cronSchedule(task.cronExpression).inTimeZone(TimeZone.getTimeZone(ZoneId.of(task.zoneId)))
    .withMisfireHandlingInstructionDoNothing()).build());
  } else scheduler.unscheduleJob(key);
  try { task.enabled=enabled; tasks.saveAndFlush(task); }
  catch(RuntimeException e) {
   // Rebuild scheduler state from the unchanged persisted definition.
   scheduler.deleteJob(JobKey.jobKey(id.toString())); register(get(id)); throw e;
  }
 }
 public List<JobView> jobs() throws SchedulerException {
  var result=new ArrayList<JobView>();
  for(var task:tasks.findAll().stream().sorted(Comparator.comparing(t->t.createdAt)).toList()) {
   var trigger=scheduler.getTrigger(TriggerKey.triggerKey(task.id.toString()));
   var history=executions.findByJobIdOrderByStartedAtDesc(task.id,PageRequest.of(0,1));
   result.add(new JobView(task.id,task.name,task.taskType,task.cronExpression,task.zoneId,task.enabled,
    trigger==null||trigger.getNextFireTime()==null?null:trigger.getNextFireTime().toInstant(),history.isEmpty()?null:history.getFirst()));
  }
  return result;
 }
 public record JobView(UUID id,String name,TaskType taskType,String cronExpression,String zoneId,boolean enabled,Instant nextRun,Execution lastExecution) {}
}
