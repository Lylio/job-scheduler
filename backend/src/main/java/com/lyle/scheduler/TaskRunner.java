package com.lyle.scheduler;
import org.springframework.stereotype.Component;
@Component
public class TaskRunner {
 public String run(TaskType type) throws InterruptedException {
  return switch(type) {
   case REPORT_DEMO -> { Thread.sleep(1800); yield "Demo report generated: 250 sample records processed. No real data modified."; }
   case SLOW_DEMO -> { Thread.sleep(12000); yield "Slow demo completed. The same job cannot execute concurrently."; }
   case FAILURE_DEMO -> { Thread.sleep(1200); throw new IllegalStateException("Intentional demo failure: simulated import service unavailable."); }
  };
 }
}
