package com.lyle.scheduler;
import jakarta.validation.constraints.*;
public record JobRequest(
 @NotBlank @Size(max=100) String name,
 @NotNull TaskType taskType,
 @NotBlank @Size(max=100) String cronExpression,
 @NotBlank @Size(max=80) String zoneId) {}
