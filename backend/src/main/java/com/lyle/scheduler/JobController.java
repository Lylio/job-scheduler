package com.lyle.scheduler;
import jakarta.validation.Valid;
import org.quartz.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import java.util.*;
@RestController @RequestMapping("/api")
public class JobController {
 private final JobService service; private final ExecutionRepository executions;
 public JobController(JobService service,ExecutionRepository executions) { this.service=service; this.executions=executions; }
 @GetMapping("/jobs") public List<JobService.JobView> jobs() throws SchedulerException { return service.jobs(); }
 @PostMapping("/jobs") @ResponseStatus(HttpStatus.CREATED)
 public ScheduledTask create(@Valid @RequestBody JobRequest request) throws SchedulerException { return service.create(request); }
 @PostMapping("/jobs/{id}/run") @ResponseStatus(HttpStatus.ACCEPTED)
 public void run(@PathVariable UUID id) throws SchedulerException { service.run(id); }
 @PutMapping("/jobs/{id}/enabled") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void enabled(@PathVariable UUID id,@Valid @RequestBody Enabled request) throws SchedulerException { service.enabled(id,request.enabled()); }
 @GetMapping("/executions") public List<Execution> executions(@RequestParam(defaultValue="50") int limit,@RequestParam(required=false) UUID jobId) {
  if(limit<1||limit>200) throw new IllegalArgumentException("Limit must be between 1 and 200.");
  var page=PageRequest.of(0,limit);
  if(jobId!=null) { service.get(jobId); return executions.findByJobIdOrderByStartedAtDesc(jobId,page); }
  return executions.findAllByOrderByStartedAtDesc(page);
 }
 public record Enabled(@jakarta.validation.constraints.NotNull Boolean enabled) {}
}
