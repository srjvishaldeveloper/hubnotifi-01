package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.EcommerceStore;
import com.whatsmine.repository.EcommerceStoreRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.ecommerce.EcommerceOAuthManager;
import com.whatsmine.service.ecommerce.StoreConnector;
import com.whatsmine.service.ecommerce.StoreUrlGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/**
 * Shopify/Woo are connected via a redirect the app initiates (connect());
 * BigCommerce is installed from the merchant's BigCommerce control panel, so
 * its callback does all the work with no connect() step of its own. Ports
 * php/app/Modules/Ecommerce/Http/Controllers/EcommerceOAuthController.php +
 * .../Services/OAuth/EcommerceOAuthManager.php.
 */
@RestController
public class EcommerceOAuthController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String OAUTH_SESSION_KEY = "ecom_oauth";

    @Autowired
    private EcommerceStoreRepository storeRepository;

    @Autowired
    private StoreConnector storeConnector;

    @Autowired
    private EcommerceOAuthManager oauthManager;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

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
            @RequestParam(required = false) String store_url,
            HttpSession session
    ) {
        Long workspaceId = getWorkspaceId(userDetails);

        if ("shopify".equalsIgnoreCase(platform)) {
            String normalizedShop = StoreConnector.normalizeDomain("shopify", shop != null ? shop : "");
            String error = StoreUrlGuard.validate("shopify", normalizedShop);
            if (error != null) {
                Inertia.flashError(session, error);
                return Inertia.redirect("/app/ecommerce/stores");
            }

            String state = generateState();
            Map<String, Object> oauthState = new HashMap<>();
            oauthState.put("state", state);
            oauthState.put("shop", normalizedShop);
            oauthState.put("workspace", workspaceId);
            session.setAttribute(OAUTH_SESSION_KEY, oauthState);

            String callbackUrl = appUrl + "/app/ecommerce/oauth/shopify/callback";
            try {
                String url = oauthManager.shopifyAuthUrl(normalizedShop, state, callbackUrl);
                return Inertia.location(url);
            } catch (IllegalStateException e) {
                Inertia.flashError(session, "Shopify OAuth is not configured by the administrator.");
                return Inertia.redirect("/app/ecommerce/stores");
            }
        }

        if ("woocommerce".equalsIgnoreCase(platform)) {
            String domain = StoreConnector.normalizeDomain("woocommerce", store_url != null ? store_url : "");
            String error = StoreUrlGuard.validate("woocommerce", domain);
            if (error != null) {
                Inertia.flashError(session, error);
                return Inertia.redirect("/app/ecommerce/stores");
            }

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

            String callbackUrl = appUrl + "/webhooks/ecommerce/woo-auth";
            String returnUrl = appUrl + "/app/ecommerce/oauth/woocommerce/return";
            String url = domain.replaceAll("/+$", "")
                    + "/wc-auth/v1/authorize"
                    + "?app_name=" + encode("Hub Notification")
                    + "&scope=" + encode("read_write")
                    + "&user_id=" + encode(store.getUuid())
                    + "&return_url=" + encode(returnUrl)
                    + "&callback_url=" + encode(callbackUrl);

            return Inertia.location(url);
        }

        Inertia.flashError(session, "BigCommerce connects by installing the app from your BigCommerce control panel.");
        return Inertia.redirect("/app/ecommerce/stores");
    }

    @GetMapping("/app/ecommerce/oauth/shopify/callback")
    @SuppressWarnings("unchecked")
    public Object shopifyCallback(HttpServletRequest request, HttpSession session) {
        Map<String, Object> stored = (Map<String, Object>) session.getAttribute(OAUTH_SESSION_KEY);
        session.removeAttribute(OAUTH_SESSION_KEY);

        Map<String, String> query = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) query.put(key, values[0]);
        });

        String shop = StoreConnector.normalizeDomain("shopify", query.getOrDefault("shop", ""));
        String state = query.getOrDefault("state", "");
        String code = query.getOrDefault("code", "");

        String storedState = stored != null ? (String) stored.get("state") : null;
        String storedShop = stored != null ? (String) stored.get("shop") : null;
        Object storedWorkspace = stored != null ? stored.get("workspace") : null;

        if (storedState == null || storedState.isBlank() || !constantTimeEquals(storedState, state) || !Objects.equals(storedShop, shop)) {
            Inertia.flashError(session, "Invalid OAuth state. Please try connecting again.");
            return Inertia.redirect("/app/ecommerce/stores");
        }

        String urlError = StoreUrlGuard.validate("shopify", shop);
        if (urlError != null) {
            Inertia.flashError(session, urlError);
            return Inertia.redirect("/app/ecommerce/stores");
        }

        if (!oauthManager.shopifyVerifyHmac(query)) {
            Inertia.flashError(session, "Shopify request signature could not be verified.");
            return Inertia.redirect("/app/ecommerce/stores");
        }

        String token = code.isBlank() ? null : oauthManager.shopifyExchange(shop, code);
        if (token == null) {
            Inertia.flashError(session, "Failed to obtain Shopify access token.");
            return Inertia.redirect("/app/ecommerce/stores");
        }

        Long workspaceId = storedWorkspace instanceof Number number ? number.longValue() : null;
        Map<String, Object> result = storeConnector.connect(workspaceId, "shopify", shop, Map.of("access_token", token), null);

        boolean ok = Boolean.TRUE.equals(result.get("ok"));
        if (ok) {
            Inertia.flashSuccess(session, "Shopify store connected.");
        } else {
            Inertia.flashError(session, "Connected but: " + result.get("message"));
        }
        return Inertia.redirect("/app/ecommerce/stores");
    }

    @GetMapping("/app/ecommerce/oauth/bigcommerce/callback")
    public Object bigcommerceCallback(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String context,
            HttpSession session
    ) {
        Long workspaceId = getWorkspaceId(userDetails);

        if (code == null || code.isBlank() || context == null || context.isBlank()) {
            Inertia.flashError(session, "BigCommerce did not return an authorization code.");
            return Inertia.redirect("/app/ecommerce/stores");
        }

        String callbackUrl = appUrl + "/app/ecommerce/oauth/bigcommerce/callback";
        Map<String, String> result = oauthManager.bigcommerceExchange(code, scope, context, callbackUrl);
        if (result == null) {
            Inertia.flashError(session, "Failed to obtain BigCommerce access token.");
            return Inertia.redirect("/app/ecommerce/stores");
        }

        Map<String, Object> connect = storeConnector.connect(
                workspaceId, "bigcommerce", result.get("store_hash"), Map.of("access_token", result.get("access_token")), null);

        boolean ok = Boolean.TRUE.equals(connect.get("ok"));
        if (ok) {
            Inertia.flashSuccess(session, "BigCommerce store connected.");
        } else {
            Inertia.flashError(session, "Connected but: " + connect.get("message"));
        }
        return Inertia.redirect("/app/ecommerce/stores");
    }

    @GetMapping("/app/ecommerce/oauth/woocommerce/return")
    public Object woocommerceReturn(HttpSession session) {
        Inertia.flashSuccess(session, "WooCommerce authorization complete. Finishing connection…");
        return Inertia.redirect("/app/ecommerce/stores");
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

    private static String generateState() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.substring(0, 40);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
