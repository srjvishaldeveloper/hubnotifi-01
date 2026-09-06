package com.whatsmine.repository;

import com.whatsmine.model.AiRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiRunRepository extends JpaRepository<AiRun, Long> {

    List<AiRun> findByChatbotId(Long chatbotId);

    List<AiRun> findByConversationId(Long conversationId);

    List<AiRun> findByChatbotIdIn(List<Long> chatbotIds);
}
