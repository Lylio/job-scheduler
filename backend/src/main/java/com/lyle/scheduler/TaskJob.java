package com.lyle.scheduler;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
@DisallowConcurrentExecution
public class TaskJob implements Job {
 @Autowired private TaskRepository tasks;
 @Autowired private ExecutionRecorder recorder;
 @Autowired private TaskRunner runner;
 @Override public void execute(JobExecutionContext context) throws JobExecutionException {
  var id=java.util.UUID.fromString(context.getJobDetail().getKey().getName());
  var task=tasks.findById(id).orElseThrow();
  var source=context.getMergedJobDataMap().getString("source");
  var run=recorder.start(task, source==null?"SCHEDULED":source);
  try { recorder.finish(run.id,"SUCCESS",runner.run(task.taskType)); }
  catch(InterruptedException e) {
   Thread.currentThread().interrupt(); recorder.finish(run.id,"INTERRUPTED","Execution interrupted.");
  } catch(Exception e) {
   recorder.finish(run.id,"FAILED",e.getMessage()==null?e.getClass().getSimpleName():e.getMessage().substring(0,Math.min(2000,e.getMessage().length())));
  }
 }
}
