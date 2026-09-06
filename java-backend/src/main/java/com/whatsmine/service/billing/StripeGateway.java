package com.whatsmine.service.billing;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.Price;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.whatsmine.model.PaymentGatewayConfig;
import com.whatsmine.model.PaymentTransaction;
import com.whatsmine.model.Plan;
import com.whatsmine.model.Subscription;
import com.whatsmine.model.User;
import com.whatsmine.repository.PaymentGatewayConfigRepository;
import com.whatsmine.repository.PaymentTransactionRepository;
import com.whatsmine.repository.SubscriptionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Real Stripe integration — Checkout Sessions (hosted page) + webhook,
 * matching PHP's StripeGateway. Credentials come from PaymentGatewayConfig
 * (Admin > Payments), same pattern as every other real-integration
 * credential store built this session. No secret key configured = every
 * operation fails loudly instead of fabricating a fake checkout URL.
 */
@Service
public class StripeGateway implements BillingGatewayInterface {

    private static final Logger log = LoggerFactory.getLogger(StripeGateway.class);

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final WebhookIdempotencyService idempotencyService;
    private final PaymentGatewayConfigRepository gatewayConfigRepository;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    public StripeGateway(SubscriptionRepository subscriptionRepository,
                         PaymentTransactionRepository paymentTransactionRepository,
                         WebhookIdempotencyService idempotencyService,
                         PaymentGatewayConfigRepository gatewayConfigRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.idempotencyService = idempotencyService;
        this.gatewayConfigRepository = gatewayConfigRepository;
    }

    @Override
    public String name() {
        return "Stripe";
    }

    private PaymentGatewayConfig config() {
        return gatewayConfigRepository.findByGateway("stripe").orElse(null);
    }

    private String credential(String key) {
        PaymentGatewayConfig config = config();
        if (config == null) return null;
        Object value = config.getActiveCredentials().get(key);
        return value != null ? value.toString() : null;
    }

    private String secretKey() {
        return credential("secret_key");
    }

    private String webhookSecret() {
        return credential("webhook_secret");
    }

    @Override
    public boolean isConfigured() {
        PaymentGatewayConfig config = config();
        String secret = secretKey();
        return config != null && Boolean.TRUE.equals(config.getEnabled()) && secret != null && !secret.isBlank();
    }

    @Override
    @Transactional
    public Map<String, Object> createCheckout(User user, Plan plan, String billingCycle) {
        if (plan == null || !Boolean.TRUE.equals(plan.getEnabled())) {
            return Map.of("error", "Plan not available");
        }
        if (!isConfigured()) {
            return Map.of("error", "Stripe is not configured. Add a Secret Key under Admin > Payments.");
        }

        String secretKey = secretKey();
        boolean yearly = "yearly".equalsIgnoreCase(billingCycle) || "annual".equalsIgnoreCase(billingCycle);

        try {
            SessionCreateParams.LineItem.PriceData.Recurring.Interval interval = yearly
                    ? SessionCreateParams.LineItem.PriceData.Recurring.Interval.YEAR
                    : SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH;

            String existingPriceId = yearly ? plan.getStripeYearlyId() : plan.getStripeMonthlyId();
            SessionCreateParams.LineItem lineItem;
            if (existingPriceId != null && !existingPriceId.isBlank()) {
                lineItem = SessionCreateParams.LineItem.builder()
                        .setPrice(existingPriceId)
                        .setQuantity(1L)
                        .build();
            } else {
                long amountCents = yearly
                        ? (plan.getYearlyPriceCents() != null ? plan.getYearlyPriceCents() : plan.getPriceCents())
                        : (plan.getMonthlyPriceCents() != null ? plan.getMonthlyPriceCents() : plan.getPriceCents());

                PriceCreateParams priceParams = PriceCreateParams.builder()
                        .setCurrency(plan.getCurrencyCode() != null ? plan.getCurrencyCode().toLowerCase() : "usd")
                        .setUnitAmount(amountCents)
                        .setRecurring(PriceCreateParams.Recurring.builder().setInterval(
                                PriceCreateParams.Recurring.Interval.valueOf(interval.name())).build())
                        .setProductData(PriceCreateParams.ProductData.builder().setName(plan.getName()).build())
                        .build();
                com.stripe.net.RequestOptions options = com.stripe.net.RequestOptions.builder().setApiKey(secretKey).build();
                Price price = Price.create(priceParams, options);
                lineItem = SessionCreateParams.LineItem.builder()
                        .setPrice(price.getId())
                        .setQuantity(1L)
                        .build();
            }

            SessionCreateParams.SubscriptionData.Builder subscriptionData = SessionCreateParams.SubscriptionData.builder()
                    .putMetadata("user_id", String.valueOf(user.getId()))
                    .putMetadata("plan_id", String.valueOf(plan.getId()))
                    .putMetadata("billing_cycle", billingCycle != null ? billingCycle : "monthly");
            if (plan.getTrialDays() != null && plan.getTrialDays() > 0) {
                subscriptionData.setTrialPeriodDays((long) plan.getTrialDays());
            }

            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomerEmail(user.getEmail())
                    .addLineItem(lineItem)
                    .setSuccessUrl(appUrl + "/billing?checkout=success&session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(appUrl + "/pricing?checkout=cancelled")
                    .putMetadata("user_id", String.valueOf(user.getId()))
                    .putMetadata("plan_id", String.valueOf(plan.getId()))
                    .putMetadata("billing_cycle", billingCycle != null ? billingCycle : "monthly")
                    .setSubscriptionData(subscriptionData.build());

            com.stripe.net.RequestOptions options = com.stripe.net.RequestOptions.builder().setApiKey(secretKey).build();
            Session session = Session.create(paramsBuilder.build(), options);

            return Map.of("url", session.getUrl(), "session_id", session.getId());
        } catch (StripeException e) {
            log.warn("Stripe checkout creation failed: {}", e.getMessage());
            return Map.of("error", "Stripe error: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Map<String, Object> fulfillCheckoutSession(String sessionId) {
        if (!isConfigured()) {
            return Map.of("ok", false, "error", "Stripe is not configured.");
        }
        try {
            com.stripe.net.RequestOptions options = com.stripe.net.RequestOptions.builder().setApiKey(secretKey()).build();
            Session session = Session.retrieve(sessionId, options);
            applyCheckoutSession(session, options);
            return Map.of("ok", true);
        } catch (StripeException e) {
            log.warn("Stripe fulfillCheckoutSession failed: {}", e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    @Override
    @Transactional
    public ResponseEntity<?> handleWebhook(HttpServletRequest request) {
        String webhookSecret = webhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("Stripe webhook received but no webhook secret is configured — rejecting.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Webhook not configured"));
        }

        String payload;
        try {
            // Must preserve the exact raw bytes for HMAC verification — reading
            // line-by-line (BufferedReader.readLine()) silently normalizes/adds
            // newlines and breaks the signature.
            payload = new String(request.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Could not read request body"));
        }

        String sigHeader = request.getHeader("Stripe-Signature");
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid signature"));
        }

        if (idempotencyService.isAlreadyProcessed(event.getId())) {
            return ResponseEntity.ok(Map.of("status", "already_processed"));
        }
        idempotencyService.recordEvent("stripe", event.getId(), event.getType(), Map.of());

        try {
            com.stripe.net.RequestOptions options = com.stripe.net.RequestOptions.builder().setApiKey(secretKey()).build();
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

            // getObject() silently returns empty when the event's declared api_version
            // doesn't match this SDK build's pinned version — deserializeUnsafe() is
            // Stripe's documented fallback that deserializes regardless.
            com.stripe.model.StripeObject dataObject = deserializer.getObject().orElse(null);
            if (dataObject == null) {
                try {
                    dataObject = deserializer.deserializeUnsafe();
                } catch (Exception e) {
                    log.warn("Could not deserialize Stripe event {} data object: {}", event.getId(), e.getMessage());
                }
            }

            switch (event.getType()) {
                case "checkout.session.completed" -> {
                    if (dataObject instanceof Session session) applyCheckoutSession(session, options);
                }
                case "customer.subscription.updated", "customer.subscription.deleted" -> {
                    if (dataObject instanceof com.stripe.model.Subscription stripeSub) syncFromStripeSubscription(stripeSub);
                }
                case "invoice.paid", "invoice.payment_succeeded" -> {
                    if (dataObject instanceof Invoice invoice) recordInvoicePaid(invoice);
                }
                case "invoice.payment_failed" -> {
                    if (dataObject instanceof Invoice invoice) markPastDue(invoice);
                }
                default -> log.debug("Unhandled Stripe event type: {}", event.getType());
            }

            idempotencyService.markProcessed(event.getId());
            return ResponseEntity.ok(Map.of("status", "received"));
        } catch (Exception e) {
            log.error("Stripe webhook processing failed for event {}: {}", event.getId(), e.getMessage(), e);
            idempotencyService.markFailed(event.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Processing failed"));
        }
    }

    private void applyCheckoutSession(Session session, com.stripe.net.RequestOptions options) {
        Map<String, String> metadata = session.getMetadata();
        if (metadata == null) return;
        Long userId = parseLong(metadata.get("user_id"));
        Long planId = parseLong(metadata.get("plan_id"));
        String billingCycle = metadata.getOrDefault("billing_cycle", "monthly");
        if (userId == null || planId == null) return;

        String stripeSubId = session.getSubscription();
        LocalDateTime renewsAt = null;
        LocalDateTime trialEndsAt = null;
        String status = "active";

        if (stripeSubId != null) {
            try {
                com.stripe.model.Subscription stripeSub = com.stripe.model.Subscription.retrieve(stripeSubId, options);
                status = stripeSub.getStatus();
                renewsAt = toLocalDateTime(stripeSub.getCurrentPeriodEnd());
                if (stripeSub.getTrialEnd() != null) trialEndsAt = toLocalDateTime(stripeSub.getTrialEnd());
            } catch (StripeException e) {
                log.warn("Could not retrieve Stripe subscription {}: {}", stripeSubId, e.getMessage());
            }
        }

        Subscription sub = subscriptionRepository.findByGatewaySubscriptionId(stripeSubId != null ? stripeSubId : session.getId())
                .orElseGet(Subscription::new);
        sub.setUserId(userId);
        sub.setPlanId(planId);
        sub.setBillingCycle(billingCycle);
        sub.setGateway("stripe");
        sub.setGatewaySubscriptionId(stripeSubId != null ? stripeSubId : session.getId());
        sub.setStatus(status);
        if (sub.getStartsAt() == null) sub.setStartsAt(LocalDateTime.now());
        if (renewsAt != null) sub.setRenewsAt(renewsAt);
        if (trialEndsAt != null) sub.setTrialEndsAt(trialEndsAt);
        subscriptionRepository.save(sub);
    }

    private void syncFromStripeSubscription(com.stripe.model.Subscription stripeSub) {
        subscriptionRepository.findByGatewaySubscriptionId(stripeSub.getId()).ifPresent(sub -> {
            sub.setStatus(stripeSub.getStatus());
            if (stripeSub.getCurrentPeriodEnd() != null) sub.setRenewsAt(toLocalDateTime(stripeSub.getCurrentPeriodEnd()));
            if (stripeSub.getTrialEnd() != null) sub.setTrialEndsAt(toLocalDateTime(stripeSub.getTrialEnd()));
            if (stripeSub.getEndedAt() != null) sub.setEndsAt(toLocalDateTime(stripeSub.getEndedAt()));
            subscriptionRepository.save(sub);
        });
    }

    private void recordInvoicePaid(Invoice invoice) {
        String invoiceId = invoice.getId();
        if (paymentTransactionRepository.findByGatewayTransactionId(invoiceId).isPresent()) {
            return; // already recorded
        }

        String stripeSubId = invoice.getSubscription();
        Subscription sub = stripeSubId != null ? subscriptionRepository.findByGatewaySubscriptionId(stripeSubId).orElse(null) : null;
        if (sub == null) {
            log.warn("Stripe invoice.paid for unknown subscription {} — skipping PaymentTransaction (no local subscription to attribute it to).", stripeSubId);
            return;
        }

        PaymentTransaction txn = new PaymentTransaction();
        txn.setUserId(sub.getUserId());
        txn.setSubscriptionId(sub.getId());
        txn.setGateway("stripe");
        txn.setGatewayTransactionId(invoiceId);
        txn.setAmountCents(invoice.getAmountPaid() != null ? invoice.getAmountPaid().intValue() : 0);
        txn.setCurrencyCode(invoice.getCurrency() != null ? invoice.getCurrency().toUpperCase() : "USD");
        txn.setStatus("succeeded");
        paymentTransactionRepository.save(txn);

        // A paid invoice means the subscription is in good standing again (e.g. recovering from past_due).
        if (!"canceled".equals(sub.getStatus())) {
            sub.setStatus("active");
            subscriptionRepository.save(sub);
        }
    }

    private void markPastDue(Invoice invoice) {
        String stripeSubId = invoice.getSubscription();
        if (stripeSubId == null) return;
        subscriptionRepository.findByGatewaySubscriptionId(stripeSubId).ifPresent(sub -> {
            sub.setStatus("past_due");
            subscriptionRepository.save(sub);
        });
    }

    @Override
    @Transactional
    public boolean cancel(Subscription subscription) {
        if (subscription == null) return false;
        if (isConfigured() && subscription.getGatewaySubscriptionId() != null) {
            try {
                com.stripe.net.RequestOptions options = com.stripe.net.RequestOptions.builder().setApiKey(secretKey()).build();
                com.stripe.model.Subscription stripeSub = com.stripe.model.Subscription.retrieve(subscription.getGatewaySubscriptionId(), options);
                stripeSub.cancel(com.stripe.param.SubscriptionCancelParams.builder().build(), options);
            } catch (StripeException e) {
                log.warn("Stripe cancel failed for subscription {}: {}", subscription.getId(), e.getMessage());
                return false;
            }
        }
        subscription.setStatus("canceled");
        subscription.setEndsAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);
        return true;
    }

    @Override
    @Transactional
    public boolean sync(Subscription subscription) {
        if (subscription == null) return false;
        if (!isConfigured() || subscription.getGatewaySubscriptionId() == null) return true;
        try {
            com.stripe.net.RequestOptions options = com.stripe.net.RequestOptions.builder().setApiKey(secretKey()).build();
            com.stripe.model.Subscription stripeSub = com.stripe.model.Subscription.retrieve(subscription.getGatewaySubscriptionId(), options);
            syncFromStripeSubscription(stripeSub);
            return true;
        } catch (StripeException e) {
            log.warn("Stripe sync failed for subscription {}: {}", subscription.getId(), e.getMessage());
            return false;
        }
    }

    @Override
    @Transactional
    public Map<String, Object> changePlan(Subscription subscription, Plan newPlan, String billingCycle) {
        if (subscription == null || newPlan == null) {
            return Map.of("ok", false, "error", "Invalid subscription or plan");
        }
        if (isConfigured() && subscription.getGatewaySubscriptionId() != null) {
            boolean yearly = "yearly".equalsIgnoreCase(billingCycle) || "annual".equalsIgnoreCase(billingCycle);
            String newPriceId = yearly ? newPlan.getStripeYearlyId() : newPlan.getStripeMonthlyId();
            if (newPriceId == null || newPriceId.isBlank()) {
                return Map.of("ok", false, "error", "New plan has no Stripe price configured for " + billingCycle + " billing.");
            }
            try {
                com.stripe.net.RequestOptions options = com.stripe.net.RequestOptions.builder().setApiKey(secretKey()).build();
                com.stripe.model.Subscription stripeSub = com.stripe.model.Subscription.retrieve(subscription.getGatewaySubscriptionId(), options);
                String itemId = stripeSub.getItems().getData().get(0).getId();
                SubscriptionUpdateParams updateParams = SubscriptionUpdateParams.builder()
                        .addItem(SubscriptionUpdateParams.Item.builder()
                                .setId(itemId)
                                .setPrice(newPriceId)
                                .build())
                        .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
                        .build();
                stripeSub.update(updateParams, options);
            } catch (StripeException e) {
                log.warn("Stripe changePlan failed: {}", e.getMessage());
                return Map.of("ok", false, "error", "Stripe error: " + e.getMessage());
            }
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

        if (isConfigured() && transaction.getGatewayTransactionId() != null) {
            try {
                com.stripe.net.RequestOptions options = com.stripe.net.RequestOptions.builder().setApiKey(secretKey()).build();
                Invoice invoice = Invoice.retrieve(transaction.getGatewayTransactionId(), options);
                String paymentIntentId = invoice.getPaymentIntent();
                if (paymentIntentId == null) {
                    return Map.of("ok", false, "error", "No payment intent found for this invoice.");
                }
                RefundCreateParams refundParams = RefundCreateParams.builder()
                        .setPaymentIntent(paymentIntentId)
                        .setAmount((long) refundCents)
                        .build();
                Refund.create(refundParams, options);
            } catch (StripeException e) {
                log.warn("Stripe refund failed: {}", e.getMessage());
                return Map.of("ok", false, "error", "Stripe error: " + e.getMessage());
            }
        }

        transaction.setRefundedCents(refundCents);
        transaction.setRefundedAt(LocalDateTime.now());
        transaction.setStatus("refunded");
        paymentTransactionRepository.save(transaction);
        return Map.of("ok", true);
    }

    private Long parseLong(String s) {
        if (s == null) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime toLocalDateTime(Long epochSeconds) {
        if (epochSeconds == null) return null;
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }
}
