package com.whatsmine.service.billing;

import com.whatsmine.model.PaymentTransaction;
import com.whatsmine.model.Plan;
import com.whatsmine.model.Subscription;
import com.whatsmine.model.User;
import com.whatsmine.repository.PaymentTransactionRepository;
import com.whatsmine.repository.PlanRepository;
import com.whatsmine.repository.SubscriptionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class StripeGateway implements BillingGatewayInterface {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final WebhookIdempotencyService idempotencyService;

    public StripeGateway(SubscriptionRepository subscriptionRepository,
                         PlanRepository planRepository,
                         PaymentTransactionRepository paymentTransactionRepository,
                         WebhookIdempotencyService idempotencyService) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.idempotencyService = idempotencyService;
    }

    @Override
    public String name() {
        return "Stripe";
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

        String sessionId = "cs_test_" + System.currentTimeMillis();
        String checkoutUrl = "/billing?checkout=success&session_id=" + sessionId;

        return Map.of("url", checkoutUrl, "session_id", sessionId);
    }

    @Override
    @Transactional
    public Map<String, Object> fulfillCheckoutSession(String sessionId) {
        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        return res;
    }

    @Override
    @Transactional
    public ResponseEntity<?> handleWebhook(HttpServletRequest request) {
        String eventId = request.getHeader("Stripe-Event-Id");
        if (eventId == null) {
            eventId = "evt_stripe_" + System.currentTimeMillis();
        }

        if (idempotencyService.isAlreadyProcessed(eventId)) {
            return ResponseEntity.ok(Map.of("status", "already_processed"));
        }

        idempotencyService.recordEvent("stripe", eventId, "checkout.session.completed", Map.of());
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
}
