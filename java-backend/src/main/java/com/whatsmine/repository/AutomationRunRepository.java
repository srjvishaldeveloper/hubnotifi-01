package com.whatsmine.repository;

import com.whatsmine.model.AutomationRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AutomationRunRepository extends JpaRepository<AutomationRun, Long> {

    Page<AutomationRun> findByAutomationIdOrderByCreatedAtDesc(Long automationId, Pageable pageable);

    List<AutomationRun> findByAutomationIdOrderByStartedAtDesc(Long automationId);

    List<AutomationRun> findByAutomationIdIn(Collection<Long> automationIds);

    long countByAutomationId(Long automationId);

    List<AutomationRun> findByContactIdAndStatus(Long contactId, String status);
}
