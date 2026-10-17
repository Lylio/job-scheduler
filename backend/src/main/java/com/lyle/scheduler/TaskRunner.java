package com.lyle.scheduler;
import org.springframework.stereotype.Component;
@Component
public class TaskRunner {
 private final HttpCheckRunner http;
 public TaskRunner(HttpCheckRunner http) { this.http=http; }
 public TaskResult run(ScheduledTask task) throws InterruptedException {
  return switch(task.taskType) {
   case REPORT_DEMO -> { Thread.sleep(1800); yield TaskResult.success("Demo report generated: 250 sample records processed. No real data modified."); }
   case SLOW_DEMO -> { Thread.sleep(12000); yield TaskResult.success("Slow demo completed. The same job cannot execute concurrently."); }
   case FAILURE_DEMO -> { Thread.sleep(1200); throw new IllegalStateException("Intentional demo failure: simulated import service unavailable."); }
   case HTTP_HEALTH_CHECK -> http.run(task);
  };
 }
}
