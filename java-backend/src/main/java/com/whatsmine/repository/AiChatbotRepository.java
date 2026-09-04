package com.whatsmine.repository;

import com.whatsmine.model.AiChatbot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiChatbotRepository extends JpaRepository<AiChatbot, Long> {

    List<AiChatbot> findByWorkspaceIdOrderByIdDesc(Long workspaceId);

    Optional<AiChatbot> findByWorkspaceIdAndUuid(Long workspaceId, String uuid);

    Optional<AiChatbot> findByWorkspaceIdAndId(Long workspaceId, Long id);
}
