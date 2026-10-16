package com.lyle.scheduler;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="scheduled_jobs")
public class ScheduledTask {
 @Id public UUID id;
 @Column(nullable=false, length=100) public String name;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=40) public TaskType taskType;
 @Column(nullable=false, length=100) public String cronExpression;
 @Column(nullable=false, length=80) public String zoneId;
 @Column(nullable=false) public boolean enabled;
 @Column(nullable=false) public Instant createdAt;
 protected ScheduledTask() {}
 public ScheduledTask(String name, TaskType type, String cron, String zone) {
  id=UUID.randomUUID(); this.name=name; taskType=type; cronExpression=cron;
  zoneId=zone; enabled=true; createdAt=Instant.now();
 }
}
