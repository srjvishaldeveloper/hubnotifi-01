package com.whatsmine.controller.client;

import com.whatsmine.model.EcommerceStore;
import com.whatsmine.repository.EcommerceStoreRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.ecommerce.StoreConnector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
public class EcommerceOAuthController {

    @Autowired
    private EcommerceStoreRepository storeRepository;

    @Autowired
    private StoreConnector storeConnector;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return userDetails.getWorkspaceId();
    }

    @GetMapping("/app/ecommerce/oauth/{platform}/connect")
    public Object connect(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String platform,
            @RequestParam(required = false) String shop,
            @RequestParam(required = false) String store_url
    ) {
        Long workspaceId = getWorkspaceId(userDetails);

        if ("woocommerce".equalsIgnoreCase(platform)) {
            String domain = store_url != null ? StoreConnector.normalizeDomain("woocommerce", store_url) : "https://example-store.com";
            EcommerceStore store = storeRepository.findByWorkspaceIdAndPlatformAndDomain(workspaceId, "woocommerce", domain)
                    .orElseGet(() -> {
                        EcommerceStore s = new EcommerceStore();
                        s.setWorkspaceId(workspaceId);
                        s.setPlatform("woocommerce");
                        s.setDomain(domain);
                        s.setName("WooCommerce Store");
                        s.setStatus("pending");
                        s.setUuid(UUID.randomUUID().toString());
                        return storeRepository.save(s);
                    });

            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .header("Location", "/app/ecommerce/stores")
                    .body(Map.of("success", "WooCommerce OAuth initiated."));
        }

        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header("Location", "/app/ecommerce/stores")
                .body(Map.of("error", "OAuth for " + platform + " is not configured."));
    }

    @GetMapping("/app/ecommerce/oauth/shopify/callback")
    public Object shopifyCallback() {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header("Location", "/app/ecommerce/stores")
                .body(Map.of("success", "Shopify OAuth completed."));
    }

    @GetMapping("/app/ecommerce/oauth/bigcommerce/callback")
    public Object bigcommerceCallback() {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header("Location", "/app/ecommerce/stores")
                .body(Map.of("success", "BigCommerce OAuth completed."));
    }

    @GetMapping("/app/ecommerce/oauth/woocommerce/return")
    public Object woocommerceReturn() {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header("Location", "/app/ecommerce/stores")
                .body(Map.of("success", "WooCommerce authorization complete."));
    }

    @PostMapping("/webhooks/ecommerce/woo-auth")
    public ResponseEntity<Map<String, String>> woocommerceCallback(@RequestBody Map<String, Object> body) {
        String userId = body.get("user_id") != null ? String.valueOf(body.get("user_id")) : "";
        String key = body.get("consumer_key") != null ? String.valueOf(body.get("consumer_key")) : "";
        String secret = body.get("consumer_secret") != null ? String.valueOf(body.get("consumer_secret")) : "";

        Optional<EcommerceStore> storeOpt = storeRepository.findByUuid(userId);
        if (storeOpt.isPresent() && !key.isBlank() && !secret.isBlank()) {
            EcommerceStore store = storeOpt.get();
            storeConnector.connect(store.getWorkspaceId(), "woocommerce", store.getDomain(), Map.of("consumer_key", key, "consumer_secret", secret), store.getName());
            return ResponseEntity.ok(Map.of("status", "ok"));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "error"));
    }
}
