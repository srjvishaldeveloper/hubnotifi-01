package com.whatsmine.controller.client;

import com.whatsmine.model.EcommerceStore;
import com.whatsmine.repository.EcommerceStoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/webhooks/ecommerce")
public class EcommerceWebhookController {

    @Autowired
    private EcommerceStoreRepository storeRepository;

    @PostMapping("/shopify/{storeId}")
    public ResponseEntity<Map<String, String>> handleShopify(
            @PathVariable Long storeId,
            @RequestParam(required = false) String token,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        EcommerceStore store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found."));

        verifySecret(store, token);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/woocommerce/{storeId}")
    public ResponseEntity<Map<String, String>> handleWooCommerce(
            @PathVariable Long storeId,
            @RequestParam(required = false) String token,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        EcommerceStore store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found."));

        verifySecret(store, token);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/bigcommerce/{storeId}")
    public ResponseEntity<Map<String, String>> handleBigCommerce(
            @PathVariable Long storeId,
            @RequestParam(required = false) String token,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        EcommerceStore store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found."));

        verifySecret(store, token);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private void verifySecret(EcommerceStore store, String token) {
        if (token != null && !token.isBlank() && store.getWebhookSecret() != null) {
            if (!store.getWebhookSecret().equals(token)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid webhook token.");
            }
        }
    }
}
