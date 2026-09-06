package com.whatsmine.repository;

import com.whatsmine.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId, Pageable pageable);
    Page<AuditLog> findByWorkspaceIdAndEventOrderByCreatedAtDesc(Long workspaceId, String event, Pageable pageable);
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<AuditLog> findByEventOrderByCreatedAtDesc(String event, Pageable pageable);
    Page<AuditLog> findByUserIdAndEventOrderByCreatedAtDesc(Long userId, String event, Pageable pageable);
}
