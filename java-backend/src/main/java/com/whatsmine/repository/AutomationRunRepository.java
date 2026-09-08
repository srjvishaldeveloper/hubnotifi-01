package com.whatsmine.repository;

import com.whatsmine.model.AutomationRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AutomationRunRepository extends JpaRepository<AutomationRun, Long> {

    Page<AutomationRun> findByAutomationIdOrderByCreatedAtDesc(Long automationId, Pageable pageable);

    List<AutomationRun> findByAutomationIdOrderByStartedAtDesc(Long automationId);

    List<AutomationRun> findByAutomationIdIn(Collection<Long> automationIds);

    long countByAutomationId(Long automationId);

    List<AutomationRun> findByContactIdAndStatus(Long contactId, String status);

    @Query("SELECT r FROM AutomationRun r WHERE r.automationId IN " +
            "(SELECT a.id FROM Automation a WHERE a.workspaceId = :workspaceId) " +
            "AND r.createdAt BETWEEN :from AND :to")
    List<AutomationRun> findByWorkspaceAndCreatedAtBetween(@Param("workspaceId") Long workspaceId,
                                                            @Param("from") LocalDateTime from,
                                                            @Param("to") LocalDateTime to);
}
