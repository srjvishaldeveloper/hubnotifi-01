package com.whatsmine.repository;

import com.whatsmine.model.AutomationRunLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutomationRunLogRepository extends JpaRepository<AutomationRunLog, Long> {

    List<AutomationRunLog> findByRunIdOrderByCreatedAtAsc(Long runId);

    List<AutomationRunLog> findByRunIdOrderByIdAsc(Long runId);
}
