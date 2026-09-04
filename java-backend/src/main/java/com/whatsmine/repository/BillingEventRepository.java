package com.whatsmine.repository;

import com.whatsmine.model.BillingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillingEventRepository extends JpaRepository<BillingEvent, Long> {
    Optional<BillingEvent> findByEventId(String eventId);
    boolean existsByEventId(String eventId);
}
