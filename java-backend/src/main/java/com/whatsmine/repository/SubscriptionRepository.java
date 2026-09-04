package com.whatsmine.repository;

import com.whatsmine.model.Subscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByUserId(Long userId);

    Optional<Subscription> findByGatewaySubscriptionId(String gatewaySubscriptionId);

    List<Subscription> findByUserIdAndStatusIn(Long userId, List<String> statuses);

    Optional<Subscription> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(Long userId, List<String> statuses);

    List<Subscription> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<Subscription> findByStatus(String status, Pageable pageable);

    Page<Subscription> findByGateway(String gateway, Pageable pageable);

    Page<Subscription> findByStatusAndGateway(String status, String gateway, Pageable pageable);
}
