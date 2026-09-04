package com.whatsmine.repository;

import com.whatsmine.model.SupportReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportReplyRepository extends JpaRepository<SupportReply, Long> {
    List<SupportReply> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
