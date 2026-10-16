package com.lyle.scheduler;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;
public interface ExecutionRepository extends JpaRepository<Execution,UUID> {
 List<Execution> findAllByOrderByStartedAtDesc(Pageable page);
 List<Execution> findByJobIdOrderByStartedAtDesc(UUID jobId, Pageable page);
 List<Execution> findByStatus(String status);
}
