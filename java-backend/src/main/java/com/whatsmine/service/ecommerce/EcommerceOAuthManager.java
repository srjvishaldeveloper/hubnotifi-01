package com.whatsmine.service.ecommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.service.IntegrationCredentialsService;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds OAuth authorization URLs and performs token exchange for Shopify and
 * BigCommerce, porting php/app/Modules/Ecommerce/Services/OAuth/EcommerceOAuthManager.php.
 * The app's own OAuth client_id/client_secret (one app registered with each
 * platform, shared by every workspace) is resolved system-wide via
 * IntegrationCredentialsService, providers "oauth_shopify"/"oauth_bigcommerce"
 * — same pattern as meta_app/google_places.
 */
@Service
public class EcommerceOAuthManager {

    public static final String SHOPIFY_SCOPES =
            "read_orders,write_orders,read_customers,read_products,read_checkouts,read_fulfillments,write_fulfillments";
    public static final String BIGCOMMERCE_SCOPES =
            "store_v2_orders store_v2_customers_read_only store_v2_products store_v2_information_read_only store_cart_read_only store_webhooks";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final IntegrationCredentialsService credentialsService;
    private final ObjectMapper objectMapper;

    public EcommerceOAuthManager(IntegrationCredentialsService credentialsService, ObjectMapper objectMapper) {
        this.credentialsService = credentialsService;
        this.objectMapper = objectMapper;
    }

    // ── Shopify ──────────────────────────────────────────────────────────

    public String shopifyAuthUrl(String shop, String state, String callbackUrl) {
        String clientId = credentialsService.getCredential("oauth_shopify", "client_id");
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Shopify OAuth is not configured.");
        }

        return "https://" + shop + "/admin/oauth/authorize"
                + "?client_id=" + encode(clientId)
                + "&scope=" + encode(SHOPIFY_SCOPES)
                + "&redirect_uri=" + encode(callbackUrl)
                + "&state=" + encode(state);
    }

    /**
     * Verifies the HMAC Shopify appends to OAuth callback query params:
     * sort every param except hmac/signature, join as decoded "key=value"
     * pairs with "&", HMAC-SHA256 with the app's client secret, compare hex.
     */
    public boolean shopifyVerifyHmac(Map<String, String> query) {
        String secret = credentialsService.getCredential("oauth_shopify", "client_secret");
        String hmac = query.get("hmac");
        if (secret == null || secret.isBlank() || hmac == null || hmac.isBlank()) {
            return false;
        }

        TreeMap<String, String> sorted = new TreeMap<>(query);
        sorted.remove("hmac");
        sorted.remove("signature");

        StringBuilder message = new StringBuilder();
        for (Iterator<Map.Entry<String, String>> it = sorted.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String> entry = it.next();
            message.append(entry.getKey()).append('=').append(entry.getValue());
            if (it.hasNext()) message.append('&');
        }

        String computed = hmacSha256Hex(message.toString(), secret);
        return MessageDigest.isEqual(computed.getBytes(StandardCharsets.UTF_8), hmac.getBytes(StandardCharsets.UTF_8));
    }

    public String shopifyExchange(String shop, String code) {
        String clientId = credentialsService.getCredential("oauth_shopify", "client_id");
        String clientSecret = credentialsService.getCredential("oauth_shopify", "client_secret");
        if (clientId == null || clientSecret == null) {
            return null;
        }

        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("client_id", clientId);
            body.put("client_secret", clientSecret);
            body.put("code", code);

            JsonNode response = postJson("https://" + shop + "/admin/oauth/access_token", body);
            String accessToken = response.path("access_token").asText(null);
            return (accessToken != null && !accessToken.isBlank()) ? accessToken : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ── BigCommerce ──────────────────────────────────────────────────────

    /** Returns {access_token, store_hash} on success, or null. */
    public Map<String, String> bigcommerceExchange(String code, String scope, String context, String callbackUrl) {
        String clientId = credentialsService.getCredential("oauth_bigcommerce", "client_id");
        String clientSecret = credentialsService.getCredential("oauth_bigcommerce", "client_secret");
        if (clientId == null || clientSecret == null) {
            return null;
        }

        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("client_id", clientId);
            body.put("client_secret", clientSecret);
            body.put("code", code);
            body.put("scope", scope != null ? scope : "");
            body.put("grant_type", "authorization_code");
            body.put("redirect_uri", callbackUrl);
            body.put("context", context);

            JsonNode response = postJson("https://login.bigcommerce.com/oauth2/token", body);
            String accessToken = response.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                return null;
            }
            String returnedContext = response.path("context").asText(context);

            Map<String, String> result = new LinkedHashMap<>();
            result.put("access_token", accessToken);
            result.put("store_hash", returnedContext.replace("stores/", ""));
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private JsonNode postJson(String url, Map<String, String> body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private static String hmacSha256Hex(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
