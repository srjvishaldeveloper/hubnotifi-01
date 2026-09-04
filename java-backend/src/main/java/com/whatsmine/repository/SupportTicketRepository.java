package com.whatsmine.repository;

import com.whatsmine.model.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    Page<SupportTicket> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<SupportTicket> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<SupportTicket> findByUserIdOrderByCreatedAtDesc(Long userId);
}
