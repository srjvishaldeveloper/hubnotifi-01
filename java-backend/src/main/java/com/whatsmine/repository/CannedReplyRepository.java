package com.whatsmine.repository;

import com.whatsmine.model.CannedReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CannedReplyRepository extends JpaRepository<CannedReply, Long> {

    List<CannedReply> findByWorkspaceIdOrderByShortcutAsc(Long workspaceId);

    Optional<CannedReply> findByWorkspaceIdAndShortcut(Long workspaceId, String shortcut);

    Optional<CannedReply> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
