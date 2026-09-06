package com.whatsmine.service.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Real Razorpay integration — HTTP Basic Auth against Razorpay's REST API
 * (no dedicated SDK, matching PHP's RazorpayGateway which also just uses
 * plain HTTP + Basic Auth). Checkout = create a Plan then a Subscription
 * referencing it, returning the subscription's hosted short_url. Credentials
 * come from PaymentGatewayConfig (Admin > Payments), same store as Stripe.
 */
@Service
public class RazorpayGateway implements BillingGatewayInterface {

    private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);
    private static final String BASE_URL = "https://api.razorpay.com/v1";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final WebhookIdempotencyService idempotencyService;
    private final PaymentGatewayConfigRepository gatewayConfigRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    public RazorpayGateway(SubscriptionRepository subscriptionRepository,
                            PaymentTransactionRepository paymentTransactionRepository,
                            WebhookIdempotencyService idempotencyService,
                            PaymentGatewayConfigRepository gatewayConfigRepository,
                            ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.idempotencyService = idempotencyService;
        this.gatewayConfigRepository = gatewayConfigRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "Razorpay";
    }

    private PaymentGatewayConfig config() {
        return gatewayConfigRepository.findByGateway("razorpay").orElse(null);
    }

    private String credential(String key) {
        PaymentGatewayConfig config = config();
        if (config == null) return null;
        Object value = config.getActiveCredentials().get(key);
        return value != null ? value.toString() : null;
    }

    // Reuses the generic publishable_key/secret_key fields the admin credentials
    // form already has (same shape for every gateway) — Razorpay just calls
    // these "Key ID" and "Key Secret".
    private String keyId() {
        return credential("publishable_key");
    }

    private String keySecret() {
        return credential("secret_key");
    }

    private String webhookSecret() {
        return credential("webhook_secret");
    }

    @Override
    public boolean isConfigured() {
        PaymentGatewayConfig config = config();
        String keyId = keyId();
        String keySecret = keySecret();
        return config != null && Boolean.TRUE.equals(config.getEnabled())
                && keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }

    private String basicAuthHeader() {
        String creds = keyId() + ":" + keySecret();
        return "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", basicAuthHeader())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);

        if (response.statusCode() >= 400) {
            Map<String, Object> error = parsed.get("error") instanceof Map ? (Map<String, Object>) parsed.get("error") : Map.of();
            String message = error.getOrDefault("description", "HTTP " + response.statusCode()).toString();
            throw new IllegalStateException("Razorpay API error: " + message);
        }
        return parsed;
    }

    @Override
    @Transactional
    public Map<String, Object> createCheckout(User user, Plan plan, String billingCycle) {
        if (plan == null || !Boolean.TRUE.equals(plan.getEnabled())) {
            return Map.of("error", "Plan not available");
        }
        if (!isConfigured()) {
            return Map.of("error", "Razorpay is not configured. Add a Key ID and Key Secret under Admin > Payments.");
        }

        boolean yearly = "yearly".equalsIgnoreCase(billingCycle) || "annual".equalsIgnoreCase(billingCycle);
        long amountPaise = (yearly
                ? (plan.getYearlyPriceCents() != null ? plan.getYearlyPriceCents() : plan.getPriceCents())
                : (plan.getMonthlyPriceCents() != null ? plan.getMonthlyPriceCents() : plan.getPriceCents()));

        try {
            Map<String, Object> planBody = new HashMap<>();
            planBody.put("period", yearly ? "yearly" : "monthly");
            planBody.put("interval", 1);
            Map<String, Object> item = new HashMap<>();
            item.put("name", plan.getName());
            item.put("amount", amountPaise);
            item.put("currency", plan.getCurrencyCode() != null ? plan.getCurrencyCode().toUpperCase() : "INR");
            planBody.put("item", item);

            Map<String, Object> rzpPlan = post("/plans", planBody);
            String rzpPlanId = String.valueOf(rzpPlan.get("id"));

            Map<String, Object> subBody = new HashMap<>();
            subBody.put("plan_id", rzpPlanId);
            subBody.put("total_count", 120); // effectively "until cancelled" — Razorpay requires a bound
            subBody.put("quantity", 1);
            Map<String, Object> notes = new HashMap<>();
            notes.put("user_id", String.valueOf(user.getId()));
            notes.put("plan_id", String.valueOf(plan.getId()));
            notes.put("billing_cycle", billingCycle != null ? billingCycle : "monthly");
            subBody.put("notes", notes);
            if (plan.getTrialDays() != null && plan.getTrialDays() > 0) {
                subBody.put("start_at", java.time.Instant.now().plusSeconds(plan.getTrialDays() * 86400L).getEpochSecond());
            }

            Map<String, Object> rzpSub = post("/subscriptions", subBody);
            Object shortUrl = rzpSub.get("short_url");
            if (shortUrl == null) {
                return Map.of("error", "Razorpay did not return a checkout URL.");
            }
            return Map.of("url", shortUrl.toString(), "session_id", String.valueOf(rzpSub.get("id")));
        } catch (Exception e) {
            log.warn("Razorpay checkout creation failed: {}", e.getMessage());
            return Map.of("error", "Razorpay error: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Map<String, Object> fulfillCheckoutSession(String sessionId) {
        // Razorpay confirms subscription activation via webhook (subscription.activated /
        // subscription.charged), not a browser-side session fetch like Stripe Checkout —
        // there's nothing to fulfil here.
        return Map.of("ok", true);
    }

    @Override
    @Transactional
    public ResponseEntity<?> handleWebhook(HttpServletRequest request) {
        String webhookSecret = webhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("Razorpay webhook received but no webhook secret is configured — rejecting.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Webhook not configured"));
        }

        String payload;
        try {
            payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Could not read request body"));
        }

        String signature = request.getHeader("X-Razorpay-Signature");
        if (!verifySignature(payload, signature, webhookSecret)) {
            log.warn("Razorpay webhook signature verification failed.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid signature"));
        }

        Map<String, Object> event;
        try {
            event = objectMapper.readValue(payload, Map.class);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid payload"));
        }

        String eventType = String.valueOf(event.get("event"));
        String eventId = request.getHeader("X-Razorpay-Event-Id");
        if (eventId == null || eventId.isBlank()) {
            // Razorpay doesn't always send an explicit event id header; fall back to a
            // content hash so idempotency still holds across genuine retries.
            eventId = "razorpay_" + Integer.toHexString(payload.hashCode());
        }

        if (idempotencyService.isAlreadyProcessed(eventId)) {
            return ResponseEntity.ok(Map.of("status", "already_processed"));
        }
        idempotencyService.recordEvent("razorpay", eventId, eventType, Map.of());

        try {
            handleEvent(eventType, event);
            idempotencyService.markProcessed(eventId);
            return ResponseEntity.ok(Map.of("status", "received"));
        } catch (Exception e) {
            log.error("Razorpay webhook processing failed for event {}: {}", eventId, e.getMessage(), e);
            idempotencyService.markFailed(eventId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Processing failed"));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleEvent(String eventType, Map<String, Object> event) {
        Map<String, Object> payloadObj = event.get("payload") instanceof Map ? (Map<String, Object>) event.get("payload") : Map.of();

        switch (eventType) {
            case "subscription.activated", "subscription.authenticated", "subscription.resumed" -> {
                Map<String, Object> subEntity = extractEntity(payloadObj, "subscription");
                if (subEntity != null) activateSubscription(subEntity);
            }
            case "subscription.charged" -> {
                Map<String, Object> subEntity = extractEntity(payloadObj, "subscription");
                Map<String, Object> paymentEntity = extractEntity(payloadObj, "payment");
                if (subEntity != null) activateSubscription(subEntity);
                if (paymentEntity != null) recordPayment(subEntity, paymentEntity);
            }
            case "subscription.cancelled", "subscription.completed", "subscription.expired" -> {
                Map<String, Object> subEntity = extractEntity(payloadObj, "subscription");
                if (subEntity != null) cancelFromWebhook(subEntity);
            }
            case "subscription.halted", "subscription.pending", "subscription.paused" -> {
                Map<String, Object> subEntity = extractEntity(payloadObj, "subscription");
                if (subEntity != null) markPastDue(subEntity);
            }
            default -> log.debug("Unhandled Razorpay event type: {}", eventType);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractEntity(Map<String, Object> payloadObj, String key) {
        Object node = payloadObj.get(key);
        if (node instanceof Map) {
            Object entity = ((Map<String, Object>) node).get("entity");
            if (entity instanceof Map) return (Map<String, Object>) entity;
        }
        return null;
    }

    private void activateSubscription(Map<String, Object> subEntity) {
        String rzpSubId = String.valueOf(subEntity.get("id"));
        Map<String, Object> notes = subEntity.get("notes") instanceof Map ? (Map<String, Object>) subEntity.get("notes") : Map.of();
        Long userId = parseLong(notes.get("user_id"));
        Long planId = parseLong(notes.get("plan_id"));
        String billingCycle = notes.getOrDefault("billing_cycle", "monthly").toString();

        Subscription sub = subscriptionRepository.findByGatewaySubscriptionId(rzpSubId).orElseGet(Subscription::new);
        if (sub.getUserId() == null && userId != null) sub.setUserId(userId);
        if (sub.getPlanId() == null && planId != null) sub.setPlanId(planId);
        sub.setBillingCycle(sub.getBillingCycle() != null ? sub.getBillingCycle() : billingCycle);
        sub.setGateway("razorpay");
        sub.setGatewaySubscriptionId(rzpSubId);
        sub.setStatus("active");
        if (sub.getStartsAt() == null) sub.setStartsAt(LocalDateTime.now());
        Object chargeAt = subEntity.get("charge_at");
        if (chargeAt != null) {
            try {
                sub.setRenewsAt(LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(Long.parseLong(chargeAt.toString())), java.time.ZoneOffset.UTC));
            } catch (Exception ignored) {}
        }
        if (sub.getUserId() != null && sub.getPlanId() != null) {
            subscriptionRepository.save(sub);
        }
    }

    private void recordPayment(Map<String, Object> subEntity, Map<String, Object> paymentEntity) {
        String paymentId = String.valueOf(paymentEntity.get("id"));
        if (paymentTransactionRepository.findByGatewayTransactionId(paymentId).isPresent()) return;

        String rzpSubId = subEntity != null ? String.valueOf(subEntity.get("id")) : null;
        Subscription sub = rzpSubId != null ? subscriptionRepository.findByGatewaySubscriptionId(rzpSubId).orElse(null) : null;
        if (sub == null) {
            log.warn("Razorpay payment for unknown subscription {} — skipping PaymentTransaction.", rzpSubId);
            return;
        }

        PaymentTransaction txn = new PaymentTransaction();
        txn.setUserId(sub.getUserId());
        txn.setSubscriptionId(sub.getId());
        txn.setGateway("razorpay");
        txn.setGatewayTransactionId(paymentId);
        Object amount = paymentEntity.get("amount");
        txn.setAmountCents(amount != null ? Integer.parseInt(amount.toString()) : 0);
        Object currency = paymentEntity.get("currency");
        txn.setCurrencyCode(currency != null ? currency.toString().toUpperCase() : "INR");
        txn.setStatus("succeeded");
        paymentTransactionRepository.save(txn);
    }

    private void cancelFromWebhook(Map<String, Object> subEntity) {
        String rzpSubId = String.valueOf(subEntity.get("id"));
        subscriptionRepository.findByGatewaySubscriptionId(rzpSubId).ifPresent(sub -> {
            sub.setStatus("canceled");
            sub.setEndsAt(LocalDateTime.now());
            subscriptionRepository.save(sub);
        });
    }

    private void markPastDue(Map<String, Object> subEntity) {
        String rzpSubId = String.valueOf(subEntity.get("id"));
        subscriptionRepository.findByGatewaySubscriptionId(rzpSubId).ifPresent(sub -> {
            sub.setStatus("past_due");
            subscriptionRepository.save(sub);
        });
    }

    private boolean verifySignature(String payload, String signature, String secret) {
        if (signature == null || signature.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(hash);
            return expected.equalsIgnoreCase(signature);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    @Transactional
    public boolean cancel(Subscription subscription) {
        if (subscription == null) return false;
        if (isConfigured() && subscription.getGatewaySubscriptionId() != null) {
            try {
                post("/subscriptions/" + subscription.getGatewaySubscriptionId() + "/cancel", Map.of("cancel_at_cycle_end", 0));
            } catch (Exception e) {
                log.warn("Razorpay cancel failed for subscription {}: {}", subscription.getId(), e.getMessage());
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
            String json = null;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/subscriptions/" + subscription.getGatewaySubscriptionId()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", basicAuthHeader())
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);
            if (response.statusCode() >= 400) {
                log.warn("Razorpay sync failed for subscription {}: HTTP {}", subscription.getId(), response.statusCode());
                return false;
            }
            Object status = parsed.get("status");
            if (status != null) {
                subscription.setStatus(mapRazorpayStatus(status.toString()));
                subscriptionRepository.save(subscription);
            }
            return true;
        } catch (Exception e) {
            log.warn("Razorpay sync failed for subscription {}: {}", subscription.getId(), e.getMessage());
            return false;
        }
    }

    private String mapRazorpayStatus(String rzpStatus) {
        return switch (rzpStatus) {
            case "active", "authenticated" -> "active";
            case "cancelled", "completed", "expired" -> "canceled";
            case "halted", "pending", "paused" -> "past_due";
            case "created" -> "trialing";
            default -> rzpStatus;
        };
    }

    @Override
    @Transactional
    public Map<String, Object> changePlan(Subscription subscription, Plan newPlan, String billingCycle) {
        // Razorpay subscriptions are tied 1:1 to a Plan resource — changing plans
        // means cancelling and re-subscribing, which needs a fresh checkout rather
        // than an in-place update. Not implemented; report clearly instead of
        // silently mutating local state without touching the real subscription.
        return Map.of("ok", false, "error", "Changing plans on Razorpay requires cancelling and starting a new checkout — not supported as an in-place update.");
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
                Map<String, Object> body = new HashMap<>();
                body.put("amount", refundCents);
                post("/payments/" + transaction.getGatewayTransactionId() + "/refund", body);
            } catch (Exception e) {
                log.warn("Razorpay refund failed: {}", e.getMessage());
                return Map.of("ok", false, "error", "Razorpay error: " + e.getMessage());
            }
        }

        transaction.setRefundedCents(refundCents);
        transaction.setRefundedAt(LocalDateTime.now());
        transaction.setStatus("refunded");
        paymentTransactionRepository.save(transaction);
        return Map.of("ok", true);
    }

    private Long parseLong(Object o) {
        if (o == null) return null;
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
