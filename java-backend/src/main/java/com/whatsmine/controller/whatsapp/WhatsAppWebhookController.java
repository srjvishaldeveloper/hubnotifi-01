package com.whatsmine.controller.whatsapp;

import com.whatsmine.model.WhatsappBusinessAccount;
import com.whatsmine.repository.WhatsappBusinessAccountRepository;
import com.whatsmine.service.whatsapp.WhatsAppApiClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/webhooks/whatsapp")
public class WhatsAppWebhookController {

    private final WhatsAppApiClient apiClient;
    private final WhatsappBusinessAccountRepository wabaRepository;

    public WhatsAppWebhookController(WhatsAppApiClient apiClient, WhatsappBusinessAccountRepository wabaRepository) {
        this.apiClient = apiClient;
        this.wabaRepository = wabaRepository;
    }

    // GET /webhooks/whatsapp/global
    @GetMapping("/global")
    public ResponseEntity<String> verifyGlobal(
            @RequestParam(name = "hub.mode", required = false) String hubMode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        if ("subscribe".equals(hubMode) && apiClient.getGlobalVerifyToken().equals(verifyToken)) {
            return ResponseEntity.ok(challenge != null ? challenge : "");
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid verify token");
    }

    // POST /webhooks/whatsapp/global
    @PostMapping("/global")
    public ResponseEntity<Map<String, Object>> receiveGlobal(
            @RequestBody String rawBody,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // GET /webhooks/whatsapp/{token}
    @GetMapping("/{token}")
    public ResponseEntity<String> verify(
            @PathVariable String token,
            @RequestParam(name = "hub.mode", required = false) String hubMode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        Optional<WhatsappBusinessAccount> waba = wabaRepository.findByWebhookVerifyToken(token);
        if (waba.isEmpty()) {
            waba = wabaRepository.findByWebhookVerifyTokenHash(token);
        }

        if (waba.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid verify token");
        }

        if ("subscribe".equals(hubMode) && token.equals(verifyToken)) {
            return ResponseEntity.ok(challenge != null ? challenge : "");
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    // POST /webhooks/whatsapp/{token}
    @PostMapping("/{token}")
    public ResponseEntity<Map<String, Object>> receive(
            @PathVariable String token,
            @RequestBody String rawBody,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {

        Optional<WhatsappBusinessAccount> waba = wabaRepository.findByWebhookVerifyToken(token);
        if (waba.isEmpty()) {
            waba = wabaRepository.findByWebhookVerifyTokenHash(token);
        }

        if (waba.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid verify token"));
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
