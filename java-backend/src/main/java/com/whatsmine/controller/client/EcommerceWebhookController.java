package com.whatsmine.controller.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.EcommerceStore;
import com.whatsmine.repository.EcommerceStoreRepository;
import com.whatsmine.service.billing.WebhookIdempotencyService;
import com.whatsmine.service.ecommerce.EcommerceWebhookProcessor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

/**
 * Receives order webhooks from connected stores. Previously verified a
 * plain shared-secret token and then discarded the payload entirely — no
 * order was ever created. Now, porting
 * php/app/Modules/Ecommerce/Http/Controllers/EcommerceWebhookController.php:
 * the shared token check always applies (as before), Shopify/WooCommerce
 * additionally verify the platform's own HMAC signature when the merchant
 * has supplied one, and a verified, non-duplicate delivery is handed to
 * EcommerceWebhookProcessor to actually create the order.
 */
@RestController
@RequestMapping("/webhooks/ecommerce")
public class EcommerceWebhookController {

    private static final Logger log = LoggerFactory.getLogger(EcommerceWebhookController.class);

    @Autowired
    private EcommerceStoreRepository storeRepository;

    @Autowired
    private EcommerceWebhookProcessor webhookProcessor;

    @Autowired
    private WebhookIdempotencyService idempotencyService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping("/shopify/{storeId}")
    public ResponseEntity<Map<String, String>> handleShopify(
            @PathVariable Long storeId,
            @RequestParam(required = false) String token,
            HttpServletRequest request
    ) throws Exception {
        EcommerceStore store = findStore(storeId);
        verifyToken(store, token);

        byte[] rawBody = request.getInputStream().readAllBytes();

        Object secret = store.getCredentials() != null ? store.getCredentials().get("api_secret_key") : null;
        if (secret != null && !String.valueOf(secret).isBlank()) {
            String expected = hmacSha256Base64(rawBody, String.valueOf(secret));
            String provided = request.getHeader("X-Shopify-Hmac-Sha256");
            if (provided == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
                log.warn("ecommerce.webhook.shopify.signature_mismatch store={}", storeId);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid signature");
            }
        }

        String topic = header(request, "X-Shopify-Topic", "");
        String eventId = firstNonBlank(request.getHeader("X-Shopify-Webhook-Id"), sha256Hex(rawBody));

        return ingest(store, "shopify", topic, eventId, rawBody);
    }

    @PostMapping("/woocommerce/{storeId}")
    public ResponseEntity<Map<String, String>> handleWooCommerce(
            @PathVariable Long storeId,
            @RequestParam(required = false) String token,
            HttpServletRequest request
    ) throws Exception {
        EcommerceStore store = findStore(storeId);
        verifyToken(store, token);

        byte[] rawBody = request.getInputStream().readAllBytes();

        String signature = request.getHeader("x-wc-webhook-signature");
        if (signature != null && !signature.isBlank()) {
            String secret = store.getWebhookSecret();
            String expected = hmacSha256Base64(rawBody, secret != null ? secret : "");
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
                log.warn("ecommerce.webhook.woo.signature_mismatch store={}", storeId);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid signature");
            }
        }

        String topic = header(request, "x-wc-webhook-topic", "");
        String eventId = firstNonBlank(request.getHeader("x-wc-webhook-id"), sha256Hex(rawBody));

        return ingest(store, "woocommerce", topic, eventId, rawBody);
    }

    @PostMapping("/bigcommerce/{storeId}")
    public ResponseEntity<Map<String, String>> handleBigCommerce(
            @PathVariable Long storeId,
            @RequestParam(required = false) String token,
            HttpServletRequest request
    ) throws Exception {
        EcommerceStore store = findStore(storeId);

        // BigCommerce can't sign payloads — accept the shared token from
        // either the URL or the header we set at webhook-registration time.
        String provided = firstNonBlank(token, request.getHeader("X-Webhook-Token"));
        if (store.getWebhookSecret() == null || provided == null
                || !MessageDigest.isEqual(store.getWebhookSecret().getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            log.warn("ecommerce.webhook.invalid_token store={}", storeId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid webhook token.");
        }

        byte[] rawBody = request.getInputStream().readAllBytes();
        Map<String, Object> payload = rawBody.length > 0 ? objectMapper.readValue(rawBody, Map.class) : Map.of();

        String topic = String.valueOf(payload.getOrDefault("scope", ""));
        String eventId = firstNonBlank(
                payload.get("hash") != null ? String.valueOf(payload.get("hash")) : null,
                sha256Hex(rawBody));

        return ingest(store, "bigcommerce", topic, eventId, payload);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map<String, String>> ingest(EcommerceStore store, String platform, String topic, String eventId, byte[] rawBody) throws Exception {
        Map<String, Object> payload = rawBody.length > 0 ? objectMapper.readValue(rawBody, Map.class) : Map.of();
        return ingest(store, platform, topic, eventId, payload);
    }

    private ResponseEntity<Map<String, String>> ingest(EcommerceStore store, String platform, String topic, String eventId, Map<String, Object> payload) {
        if (topic == null || topic.isBlank()) {
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }

        String dedupeKey = "ecommerce_" + platform + "_" + store.getId() + "_" + topic + "_" + eventId;
        if (idempotencyService.isAlreadyProcessed(dedupeKey)) {
            return ResponseEntity.ok(Map.of("status", "duplicate"));
        }
        idempotencyService.recordEvent("ecommerce_" + platform, dedupeKey, topic, payload);

        try {
            webhookProcessor.process(store, platform, topic, payload);
            idempotencyService.markProcessed(dedupeKey);
        } catch (Exception e) {
            log.error("ecommerce.webhook.process_failed store={} platform={} topic={}: {}", store.getId(), platform, topic, e.getMessage());
            idempotencyService.markFailed(dedupeKey, e.getMessage());
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private EcommerceStore findStore(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found."));
    }

    private void verifyToken(EcommerceStore store, String token) {
        if (store.getWebhookSecret() == null || token == null
                || !MessageDigest.isEqual(store.getWebhookSecret().getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            log.warn("ecommerce.webhook.invalid_token store={}", store.getId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid webhook token.");
        }
    }

    private String header(HttpServletRequest request, String name, String fallback) {
        String v = request.getHeader(name);
        return v != null ? v : fallback;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private String hmacSha256Base64(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body));
    }

    private String sha256Hex(byte[] body) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(body);
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
