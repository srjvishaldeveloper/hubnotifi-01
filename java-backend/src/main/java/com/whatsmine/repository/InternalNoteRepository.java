package com.whatsmine.repository;

import com.whatsmine.model.InternalNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InternalNoteRepository extends JpaRepository<InternalNote, Long> {

    List<InternalNote> findByConversationIdOrderByCreatedAtDesc(Long conversationId);
}
