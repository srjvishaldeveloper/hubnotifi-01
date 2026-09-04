package com.whatsmine.repository;

import com.whatsmine.model.WebhookDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {
    List<WebhookDelivery> findTop5ByWebhookEndpointIdOrderByCreatedAtDesc(Long webhookEndpointId);
    Page<WebhookDelivery> findByWebhookEndpointIdOrderByCreatedAtDesc(Long webhookEndpointId, Pageable pageable);
}
