package com.whatsmine.repository;

import com.whatsmine.model.IntegrationAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IntegrationAuditLogRepository extends JpaRepository<IntegrationAuditLog, Long> {
    Page<IntegrationAuditLog> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId, Pageable pageable);
}
