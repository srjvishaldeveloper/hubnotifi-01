package com.whatsmine.repository;

import com.whatsmine.model.AiKnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiKnowledgeBaseRepository extends JpaRepository<AiKnowledgeBase, Long> {

    List<AiKnowledgeBase> findByWorkspaceIdOrderByIdDesc(Long workspaceId);

    Optional<AiKnowledgeBase> findByWorkspaceIdAndUuid(Long workspaceId, String uuid);

    Optional<AiKnowledgeBase> findByWorkspaceIdAndId(Long workspaceId, Long id);
}
