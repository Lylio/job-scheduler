package com.lyle.scheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;
@Service
public class ExecutionRecorder {
 private final ExecutionRepository executions;
 public ExecutionRecorder(ExecutionRepository executions) { this.executions=executions; }
 @Transactional public Execution start(ScheduledTask task,String source) {
  return executions.saveAndFlush(new Execution(task,source));
 }
 @Transactional public void finish(UUID id,String status,String message) {
  finish(id,new TaskResult(status,message,null,null,null));
 }
 @Transactional public void finish(UUID id,TaskResult result) {
  var run=executions.findById(id).orElseThrow();
  run.status=result.status(); run.message=result.message(); run.finishedAt=Instant.now();
  run.httpStatus=result.httpStatus();run.responseTimeMs=result.responseTimeMs();run.errorCategory=result.errorCategory();
  run.durationMs=Duration.between(run.startedAt,run.finishedAt).toMillis();
 }
 @Transactional public void recoverInterrupted() {
  for(var run:executions.findByStatus("RUNNING")) {
   run.status="INTERRUPTED"; run.finishedAt=Instant.now();
   run.message="Application stopped before this execution completed. Review before retrying.";
   // Actual task duration is unknown after a crash.
   run.durationMs=null;
  }
 }
}
