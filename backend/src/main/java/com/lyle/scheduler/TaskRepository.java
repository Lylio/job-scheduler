package com.lyle.scheduler;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface TaskRepository extends JpaRepository<ScheduledTask, UUID> {}
