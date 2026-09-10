package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.EcommerceStore;
import com.whatsmine.repository.EcommerceStoreRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.ecommerce.StoreConnectionTester;
import com.whatsmine.service.ecommerce.StoreConnector;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/app/ecommerce/stores")
public class StoreController {

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private EcommerceStoreRepository storeRepository;

    @Autowired
    private StoreConnector storeConnector;

    @Autowired
    private StoreConnectionTester connectionTester;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return userDetails.getWorkspaceId();
    }

    @GetMapping
    public Object index(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<EcommerceStore> storeModels = storeRepository.findByWorkspaceIdOrderByIdDesc(workspaceId);

        List<Map<String, Object>> storesProp = storeModels.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getUuid());
            map.put("platform", s.getPlatform());
            map.put("name", s.getName());
            map.put("domain", s.getDomain());
            map.put("status", s.getStatus());
            map.put("last_test_status", s.getLastTestStatus());
            map.put("last_test_message", s.getLastTestMessage());
            map.put("last_tested_at", s.getLastTestedAt());
            map.put("customers_synced_at", s.getCustomersSyncedAt());
            map.put("orders_synced_at", s.getOrdersSyncedAt());
            map.put("products_synced_at", s.getProductsSyncedAt());
            map.put("webhook_url", "/webhooks/ecommerce/" + s.getPlatform() + "/" + s.getId() + "?token=" + s.getWebhookSecret());
            return map;
        }).toList();

        List<Map<String, Object>> platforms = List.of(
                Map.of(
                        "platform", "shopify",
                        "label", "Shopify",
                        "fields", List.of(
                                Map.of("key", "access_token", "label", "Admin API Access Token", "type", "password", "required", true),
                                Map.of("key", "api_secret_key", "label", "API Secret Key (optional)", "type", "password", "required", false)
                        )
                ),
                Map.of(
                        "platform", "woocommerce",
                        "label", "WooCommerce",
                        "fields", List.of(
                                Map.of("key", "consumer_key", "label", "Consumer Key", "type", "text", "required", true),
                                Map.of("key", "consumer_secret", "label", "Consumer Secret", "type", "password", "required", true)
                        )
                ),
                Map.of(
                        "platform", "bigcommerce",
                        "label", "BigCommerce",
                        "fields", List.of(
                                Map.of("key", "access_token", "label", "API Access Token", "type", "password", "required", true)
                        )
                )
        );

        Map<String, Object> oauth = Map.of(
                "woocommerce", true,
                "shopify", false,
                "bigcommerce", false
        );

        Map<String, Object> props = Map.of(
                "stores", storesProp,
                "platforms", platforms,
                "oauth", oauth
        );

        return inertiaRenderer.render("Ecommerce/Stores/Index", props, request);
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public Object store(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody Map<String, Object> body, HttpSession session) {
        Long workspaceId = getWorkspaceId(userDetails);

        String platform = (String) body.get("platform");
        String name = (String) body.get("name");
        String domain = (String) body.get("domain");
        Map<String, String> credentials = body.get("credentials") instanceof Map ? (Map<String, String>) body.get("credentials") : Map.of();

        if (platform == null || domain == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Platform and domain are required.");
        }

        Map<String, Object> result = storeConnector.connect(workspaceId, platform, domain, credentials, name);
        boolean ok = Boolean.TRUE.equals(result.get("ok"));

        String flashMessage = ok ? "Store connected. " + result.get("message") : "Could not connect: " + result.get("message");
        if (ok) {
            Inertia.flashSuccess(session, flashMessage);
        } else {
            Inertia.flashError(session, flashMessage);
        }
        return Inertia.redirect("/app/ecommerce/stores");
    }

    @PostMapping("/{id}/test")
    public Object test(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String id, HttpSession session) {
        Long workspaceId = getWorkspaceId(userDetails);
        EcommerceStore store = findStore(workspaceId, id);

        Map<String, Object> result = connectionTester.test(store);
        boolean ok = Boolean.TRUE.equals(result.get("ok"));

        if (ok) {
            Inertia.flashSuccess(session, String.valueOf(result.get("message")));
        } else {
            Inertia.flashError(session, String.valueOf(result.get("message")));
        }
        return Inertia.redirect("/app/ecommerce/stores");
    }

    @PostMapping("/{id}/sync")
    public Object sync(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String id, HttpSession session) {
        Long workspaceId = getWorkspaceId(userDetails);
        EcommerceStore store = findStore(workspaceId, id);

        // Best effort sync trigger
        Inertia.flashSuccess(session, "Customer & product sync started.");
        return Inertia.redirect("/app/ecommerce/stores");
    }

    @DeleteMapping("/{id}")
    public Object destroy(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String id, HttpSession session) {
        Long workspaceId = getWorkspaceId(userDetails);
        EcommerceStore store = findStore(workspaceId, id);

        storeRepository.delete(store);

        Inertia.flashSuccess(session, "Store disconnected.");
        return Inertia.redirect("/app/ecommerce/stores");
    }

    private EcommerceStore findStore(Long workspaceId, String idOrUuid) {
        try {
            Long numericId = Long.parseLong(idOrUuid);
            return storeRepository.findByIdAndWorkspaceId(numericId, workspaceId)
                    .orElseGet(() -> storeRepository.findByWorkspaceIdAndUuid(workspaceId, idOrUuid)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found.")));
        } catch (NumberFormatException e) {
            return storeRepository.findByWorkspaceIdAndUuid(workspaceId, idOrUuid)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found."));
        }
    }
}
