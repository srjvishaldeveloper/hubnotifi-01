package com.whatsmine.service.billing;

import com.whatsmine.model.PaymentTransaction;
import com.whatsmine.model.Plan;
import com.whatsmine.model.Subscription;
import com.whatsmine.model.User;
import com.whatsmine.repository.PaymentTransactionRepository;
import com.whatsmine.repository.SubscriptionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class GenericGateway implements BillingGatewayInterface {

    private final String gatewayKey;
    private final String gatewayName;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final WebhookIdempotencyService idempotencyService;

    public GenericGateway(String gatewayKey,
                          String gatewayName,
                          SubscriptionRepository subscriptionRepository,
                          PaymentTransactionRepository paymentTransactionRepository,
                          WebhookIdempotencyService idempotencyService) {
        this.gatewayKey = gatewayKey;
        this.gatewayName = gatewayName;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.idempotencyService = idempotencyService;
    }

    @Override
    public String name() {
        return gatewayName;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    @Transactional
    public Map<String, Object> createCheckout(User user, Plan plan, String billingCycle) {
        if (plan == null || !Boolean.TRUE.equals(plan.getEnabled())) {
            return Map.of("error", "Plan not available");
        }

        if ("cashfree".equalsIgnoreCase(gatewayKey)) {
            Map<String, Object> checkoutData = Map.of(
                    "payment_session_id", "session_cf_" + System.currentTimeMillis(),
                    "environment", "SANDBOX"
            );
            return Map.of("checkout", checkoutData);
        }

        String checkoutUrl = "/billing?checkout=success&gateway=" + gatewayKey;
        return Map.of("url", checkoutUrl);
    }

    @Override
    @Transactional
    public ResponseEntity<?> handleWebhook(HttpServletRequest request) {
        String eventId = "evt_" + gatewayKey + "_" + System.currentTimeMillis();
        if (idempotencyService.isAlreadyProcessed(eventId)) {
            return ResponseEntity.ok(Map.of("status", "already_processed"));
        }
        idempotencyService.recordEvent(gatewayKey, eventId, "payment.succeeded", Map.of());
        idempotencyService.markProcessed(eventId);

        return ResponseEntity.ok(Map.of("status", "received"));
    }

    @Override
    @Transactional
    public boolean cancel(Subscription subscription) {
        if (subscription == null) {
            return false;
        }
        subscription.setStatus("canceled");
        subscription.setEndsAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);
        return true;
    }

    @Override
    @Transactional
    public boolean sync(Subscription subscription) {
        return subscription != null;
    }

    @Override
    @Transactional
    public Map<String, Object> changePlan(Subscription subscription, Plan newPlan, String billingCycle) {
        if (subscription == null || newPlan == null) {
            return Map.of("ok", false, "error", "Invalid subscription or plan");
        }
        subscription.setPlanId(newPlan.getId());
        subscription.setBillingCycle(billingCycle);
        subscriptionRepository.save(subscription);
        return Map.of("ok", true);
    }

    @Override
    @Transactional
    public Map<String, Object> refund(PaymentTransaction transaction, Integer amountCents) {
        if (transaction == null) {
            return Map.of("ok", false, "error", "Transaction not found");
        }
        if (transaction.getRefundedAt() != null) {
            return Map.of("ok", false, "error", "Transaction has already been refunded.");
        }
        int refundCents = (amountCents != null && amountCents > 0) ? amountCents : transaction.getAmountCents();
        transaction.setRefundedCents(refundCents);
        transaction.setRefundedAt(LocalDateTime.now());
        transaction.setStatus("refunded");
        paymentTransactionRepository.save(transaction);
        return Map.of("ok", true);
    }

    @Override
    @Transactional
    public Map<String, Object> fulfillCheckoutSession(String sessionId) {
        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        return res;
    }
}
