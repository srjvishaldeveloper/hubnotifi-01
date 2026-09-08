package com.whatsmine.repository;

import com.whatsmine.model.Automation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AutomationRepository extends JpaRepository<Automation, Long> {

    List<Automation> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId);

    List<Automation> findByWorkspaceIdOrderByIdDesc(Long workspaceId);

    Optional<Automation> findByWorkspaceIdAndUuid(Long workspaceId, String uuid);

    Optional<Automation> findByTriggerToken(String triggerToken);

    Optional<Automation> findByTriggerTokenAndStatus(String triggerToken, String status);

    List<Automation> findByWorkspaceIdAndTriggerTypeAndStatus(Long workspaceId, String triggerType, String status);

    List<Automation> findByWorkspaceIdAndStatusAndTriggerType(Long workspaceId, String status, String triggerType);

    @Query("SELECT a FROM Automation a WHERE a.workspaceId = :wid AND a.id != :excludeId ORDER BY a.name")
    List<Automation> findSubflowCandidates(@Param("wid") Long workspaceId, @Param("excludeId") Long excludeId);

    long countByWorkspaceId(Long workspaceId);

    long countByWorkspaceIdAndStatus(Long workspaceId, String status);
}
