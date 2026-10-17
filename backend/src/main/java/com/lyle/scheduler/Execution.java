package com.lyle.scheduler;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="job_executions")
public class Execution {
 @Id public UUID id;
 @Column(nullable=false) public UUID jobId;
 @Column(nullable=false,length=100) public String jobName;
 @Column(nullable=false,length=20) public String status;
 @Column(nullable=false,length=20) public String source;
 @Column(nullable=false) public Instant startedAt;
 public Instant finishedAt;
 public Long durationMs;
 @Column(length=2000) public String message;
 @Column(length=1000) public String targetUrl;
 public Integer expectedStatus;
 public Integer httpStatus;
 public Long responseTimeMs;
 @Column(length=40) public String errorCategory;
 protected Execution() {}
 public Execution(ScheduledTask job, String source) {
  id=UUID.randomUUID(); jobId=job.id; jobName=job.name;
  status="RUNNING"; this.source=source; startedAt=Instant.now();
  targetUrl=job.targetUrl; expectedStatus=job.expectedStatus;
 }
}
