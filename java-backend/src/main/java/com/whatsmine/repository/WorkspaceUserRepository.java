package com.whatsmine.repository;

import com.whatsmine.model.WorkspaceUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceUserRepository extends JpaRepository<WorkspaceUser, Long> {

    List<WorkspaceUser> findByWorkspaceId(Long workspaceId);

    List<WorkspaceUser> findByUserId(Long userId);

    Optional<WorkspaceUser> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);
}
