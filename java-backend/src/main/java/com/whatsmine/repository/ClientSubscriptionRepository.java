package com.whatsmine.repository;

import com.whatsmine.model.ClientSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientSubscriptionRepository extends JpaRepository<ClientSubscription, Long> {

    List<ClientSubscription> findByClientId(Long clientId);

    Optional<ClientSubscription> findFirstByClientIdAndStatusOrderByCreatedAtDesc(Long clientId, String status);
}
