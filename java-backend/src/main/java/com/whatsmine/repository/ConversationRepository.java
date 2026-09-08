package com.whatsmine.repository;

import com.whatsmine.model.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByUuid(String uuid);

    Optional<Conversation> findByWorkspaceIdAndId(Long workspaceId, Long id);

    Optional<Conversation> findByWorkspaceIdAndUuid(Long workspaceId, String uuid);

    Optional<Conversation> findFirstByWorkspaceIdAndContactIdAndChannelAccountIdAndStatusOrderByCreatedAtDesc(
            Long workspaceId, Long contactId, Long channelAccountId, String status
    );

    @Query("SELECT c FROM Conversation c WHERE c.workspaceId = :workspaceId " +
            "AND (:folder IS NULL OR " +
            "     (:folder = 'mine' AND c.assignedUserId = :userId) OR " +
            "     (:folder = 'unassigned' AND c.assignedUserId IS NULL) OR " +
            "     (:folder = 'resolved' AND c.status = 'resolved') OR " +
            "     (:folder = 'snoozed' AND c.status = 'snoozed') OR " +
            "     (:folder NOT IN ('resolved', 'snoozed', 'mine', 'unassigned') AND c.status = 'open')) " +
            "AND (:folder IS NOT NULL OR c.status = 'open') " +
            "ORDER BY c.lastMessageAt DESC NULLS LAST")
    Page<Conversation> findFilteredConversations(
            @Param("workspaceId") Long workspaceId,
            @Param("userId") Long userId,
            @Param("folder") String folder,
            Pageable pageable
    );

    List<Conversation> findByWorkspaceIdOrderByLastMessageAtDesc(Long workspaceId);

    Optional<Conversation> findFirstByWorkspaceIdAndContactIdOrderByLastMessageAtDesc(Long workspaceId, Long contactId);

    long countByWorkspaceIdAndStatusIn(Long workspaceId, List<String> statuses);

    long countByWorkspaceIdAndCreatedAtBetween(Long workspaceId, java.time.LocalDateTime from, java.time.LocalDateTime to);

    List<Conversation> findTop6ByWorkspaceIdOrderByLastMessageAtDesc(Long workspaceId);

    @Query("SELECT c FROM Conversation c WHERE c.workspaceId = :workspaceId " +
            "AND (c.createdAt BETWEEN :from AND :to OR c.resolvedAt BETWEEN :from AND :to)")
    List<Conversation> findByWorkspaceIdAndActivityBetween(
            @Param("workspaceId") Long workspaceId,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );
}
