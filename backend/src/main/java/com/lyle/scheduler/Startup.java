package com.lyle.scheduler;
import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.quartz.Scheduler;
@Component
public class Startup implements ApplicationRunner {
 private final TaskRepository tasks; private final JobService service;
 private final ExecutionRecorder recorder; private final Scheduler scheduler; private final boolean seed;
 public Startup(TaskRepository tasks,JobService service,ExecutionRecorder recorder,Scheduler scheduler,@Value("${app.seed:true}") boolean seed) {
  this.tasks=tasks; this.service=service; this.recorder=recorder; this.scheduler=scheduler; this.seed=seed;
 }
 public void run(ApplicationArguments args) throws Exception {
  recorder.recoverInterrupted();
  if(seed && tasks.count()==0) {
   tasks.save(new ScheduledTask("Sample sales report",TaskType.REPORT_DEMO,"0 * * * * ?","Europe/London"));
   tasks.save(new ScheduledTask("Slow processing demo",TaskType.SLOW_DEMO,"0 */2 * * * ?","Europe/London"));
   tasks.save(new ScheduledTask("Failing import demo",TaskType.FAILURE_DEMO,"0 */5 * * * ?","Europe/London"));
  }
  for(var task:tasks.findAll()) service.register(task);
  scheduler.start();
 }
}
