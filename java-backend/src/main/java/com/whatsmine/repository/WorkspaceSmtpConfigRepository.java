package com.whatsmine.repository;

import com.whatsmine.model.WorkspaceSmtpConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkspaceSmtpConfigRepository extends JpaRepository<WorkspaceSmtpConfig, Long> {

    Optional<WorkspaceSmtpConfig> findByWorkspaceId(Long workspaceId);
}
