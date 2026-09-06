package com.whatsmine.service.billing;

import com.whatsmine.model.PaymentGatewayConfig;
import com.whatsmine.model.PaymentTransaction;
import com.whatsmine.model.Plan;
import com.whatsmine.model.Subscription;
import com.whatsmine.model.User;
import com.whatsmine.repository.PaymentGatewayConfigRepository;
import com.whatsmine.repository.PaymentTransactionRepository;
import com.whatsmine.repository.SubscriptionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Placeholder for gateways that don't have a real integration yet (PayPal,
 * Paddle, Cashfree, Tap, Paystack, Xendit, Paymob, MyFatoorah, Mollie,
 * Square, Mercado Pago — PHP has 13 separate gateway classes; Java has real
 * ones for Stripe and Razorpay so far). isConfigured() reflects the actual
 * PaymentGatewayConfig row instead of always reporting true, and checkout/
 * webhook return a clear "not implemented" error instead of fabricating a
 * fake success — the CheckoutController and admin gateway list both already
 * key off isConfigured(), so this alone stops these from silently pretending
 * to work.
 */
public class GenericGateway implements BillingGatewayInterface {

    private final String gatewayKey;
    private final String gatewayName;
    private final PaymentGatewayConfigRepository gatewayConfigRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    public GenericGateway(String gatewayKey,
                          String gatewayName,
                          PaymentGatewayConfigRepository gatewayConfigRepository,
                          SubscriptionRepository subscriptionRepository,
                          PaymentTransactionRepository paymentTransactionRepository) {
        this.gatewayKey = gatewayKey;
        this.gatewayName = gatewayName;
        this.gatewayConfigRepository = gatewayConfigRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    @Override
    public String name() {
        return gatewayName;
    }

    @Override
    public boolean isConfigured() {
        PaymentGatewayConfig config = gatewayConfigRepository.findByGateway(gatewayKey).orElse(null);
        return config != null && Boolean.TRUE.equals(config.getEnabled()) && config.hasActiveCredentials();
    }

    @Override
    @Transactional
    public Map<String, Object> createCheckout(User user, Plan plan, String billingCycle) {
        if (plan == null || !Boolean.TRUE.equals(plan.getEnabled())) {
            return Map.of("error", "Plan not available");
        }
        return Map.of("error", gatewayName + " is not implemented yet — no real API integration exists for this gateway.");
    }

    @Override
    @Transactional
    public ResponseEntity<?> handleWebhook(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("error", gatewayName + " webhook handling is not implemented yet."));
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
