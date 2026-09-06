package com.whatsmine.repository;

import com.whatsmine.model.WhatsappAutoReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WhatsappAutoReplyRepository extends JpaRepository<WhatsappAutoReply, Long> {

    List<WhatsappAutoReply> findByWorkspaceIdOrderByPriorityAsc(Long workspaceId);

    List<WhatsappAutoReply> findByWorkspaceIdAndEnabledTrueOrderByPriorityAsc(Long workspaceId);

    Optional<WhatsappAutoReply> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
