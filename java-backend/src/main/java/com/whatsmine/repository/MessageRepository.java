package com.whatsmine.repository;

import com.whatsmine.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderBySentAtAsc(Long conversationId);

    List<Message> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    List<Message> findTop20ByConversationIdOrderByIdDesc(Long conversationId);

    Optional<Message> findFirstByConversationIdOrderBySentAtDesc(Long conversationId);

    Optional<Message> findByProviderMessageId(String providerMessageId);

    List<Message> findByConversationId(Long conversationId);
}
