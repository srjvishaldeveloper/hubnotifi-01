package com.whatsmine.service.billing;

import com.whatsmine.model.BillingEvent;
import com.whatsmine.repository.BillingEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class WebhookIdempotencyService {

    private final BillingEventRepository billingEventRepository;

    public WebhookIdempotencyService(BillingEventRepository billingEventRepository) {
        this.billingEventRepository = billingEventRepository;
    }

    @Transactional
    public boolean isAlreadyProcessed(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        Optional<BillingEvent> existing = billingEventRepository.findByEventId(eventId);
        return existing.isPresent() && existing.get().isProcessed();
    }

    @Transactional
    public BillingEvent recordEvent(String gateway, String eventId, String eventType, Map<String, Object> payload) {
        Optional<BillingEvent> existing = billingEventRepository.findByEventId(eventId);
        if (existing.isPresent()) {
            BillingEvent event = existing.get();
            event.setAttempts(event.getAttempts() + 1);
            return billingEventRepository.save(event);
        }

        BillingEvent event = new BillingEvent();
        event.setGateway(gateway);
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setAttempts(1);
        return billingEventRepository.save(event);
    }

    @Transactional
    public void markProcessed(String eventId) {
        billingEventRepository.findByEventId(eventId).ifPresent(event -> {
            event.setProcessedAt(LocalDateTime.now());
            event.setError(null);
            billingEventRepository.save(event);
        });
    }

    @Transactional
    public void markFailed(String eventId, String error) {
        billingEventRepository.findByEventId(eventId).ifPresent(event -> {
            event.setError(error);
            billingEventRepository.save(event);
        });
    }

    /** Deletes billing event records older than the given retention window. Returns how many were removed. */
    @Transactional
    public long prune(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        return billingEventRepository.deleteByCreatedAtBefore(cutoff);
    }
}
