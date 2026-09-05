package com.whatsmine.controller.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.service.social.MetaMessagingInboundProcessor;
import com.whatsmine.service.whatsapp.WhatsAppApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Shared webhook receiver for Facebook Messenger + Instagram DMs — same
 * Meta App, same callback URL convention as WhatsApp's /global endpoint.
 * HMAC verification and the verify-token challenge reuse WhatsAppApiClient's
 * implementation since both are generic Meta-app-level behaviors (same app
 * secret/verify token across all of a Meta App's products), not actually
 * WhatsApp-specific despite the class name.
 */
@RestController
@RequestMapping("/webhooks/meta")
public class MetaMessagingWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MetaMessagingWebhookController.class);

    private final WhatsAppApiClient metaAppClient;
    private final MetaMessagingInboundProcessor inboundProcessor;
    private final ObjectMapper objectMapper;

    public MetaMessagingWebhookController(WhatsAppApiClient metaAppClient, MetaMessagingInboundProcessor inboundProcessor, ObjectMapper objectMapper) {
        this.metaAppClient = metaAppClient;
        this.inboundProcessor = inboundProcessor;
        this.objectMapper = objectMapper;
    }

    // GET /webhooks/meta/global
    @GetMapping("/global")
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String hubMode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        if ("subscribe".equals(hubMode) && metaAppClient.getGlobalVerifyToken().equals(verifyToken)) {
            return ResponseEntity.ok(challenge != null ? challenge : "");
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid verify token");
    }

    // POST /webhooks/meta/global
    @SuppressWarnings("unchecked")
    @PostMapping("/global")
    public ResponseEntity<Map<String, Object>> receive(
            @RequestBody String rawBody,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {

        if (!metaAppClient.verifyHmacSignature(rawBody, signature, null)) {
            log.warn("meta.webhook.signature_mismatch");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid signature"));
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(rawBody, Map.class);
            String object = String.valueOf(payload.get("object"));
            switch (object) {
                case "page" -> inboundProcessor.processMessenger(payload);
                case "instagram" -> inboundProcessor.processInstagram(payload);
                default -> log.info("meta.webhook.ignored_object object={}", object);
            }
        } catch (Exception e) {
            log.error("Failed to parse/process Meta messaging webhook payload: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
