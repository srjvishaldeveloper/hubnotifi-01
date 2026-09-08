package com.whatsmine.repository;

import com.whatsmine.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE m.conversationId IN " +
            "(SELECT c.id FROM Conversation c WHERE c.workspaceId = :workspaceId) " +
            "AND m.createdAt BETWEEN :from AND :to")
    List<Message> findByWorkspaceAndCreatedAtBetween(@Param("workspaceId") Long workspaceId,
                                                       @Param("from") LocalDateTime from,
                                                       @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversationId IN " +
            "(SELECT c.id FROM Conversation c WHERE c.workspaceId = :workspaceId) " +
            "AND m.direction = :direction AND m.createdAt BETWEEN :from AND :to")
    long countByWorkspaceAndDirectionAndCreatedAtBetween(@Param("workspaceId") Long workspaceId,
                                                          @Param("direction") String direction,
                                                          @Param("from") LocalDateTime from,
                                                          @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(m) > 0 FROM Message m WHERE m.conversationId IN " +
            "(SELECT c.id FROM Conversation c WHERE c.workspaceId = :workspaceId) " +
            "AND m.direction = :direction")
    boolean existsByWorkspaceAndDirection(@Param("workspaceId") Long workspaceId, @Param("direction") String direction);

    List<Message> findByConversationIdOrderBySentAtAsc(Long conversationId);

    List<Message> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    List<Message> findTop20ByConversationIdOrderByIdDesc(Long conversationId);

    Optional<Message> findFirstByConversationIdOrderBySentAtDesc(Long conversationId);

    Optional<Message> findByProviderMessageId(String providerMessageId);

    List<Message> findByConversationId(Long conversationId);

    long countByConversationId(Long conversationId);
}
