package com.lyle.scheduler;
import jakarta.validation.constraints.*;
public record JobRequest(
 @NotBlank @Size(max=100) String name,
 @NotNull TaskType taskType,
 @NotBlank @Size(max=100) String cronExpression,
 @NotBlank @Size(max=80) String zoneId,
 @Size(max=1000) String targetUrl,
 @Min(100) @Max(599) Integer expectedStatus,
 @Min(1) @Max(30) Integer timeoutSeconds) {
 // Preserve the original API's demo-job defaults and Java callers.
 public JobRequest(String name,TaskType type,String cron,String zone) {
  this(name,type,cron,zone,null,null,null);
 }
}
