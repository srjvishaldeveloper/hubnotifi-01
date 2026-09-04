package com.whatsmine.repository;

import com.whatsmine.model.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, Long> {
    List<WebhookEndpoint> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<WebhookEndpoint> findByUserIdAndEnabledTrue(Long userId);
}
