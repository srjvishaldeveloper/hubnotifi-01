package com.whatsmine.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.IntegrationConfig;
import com.whatsmine.service.IntegrationCredentialsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin Integrations page. The provider catalog (labels/categories) covers
 * all 17 providers so the list page renders correctly, but only `meta_app`
 * has real field definitions and is backed by {@link IntegrationCredentialsService}
 * — that's the one actually needed to make WhatsApp sending real. The rest
 * still show as "not configured" until they get the same treatment.
 */
@RestController
@RequestMapping("/admin/integrations")
public class AdminIntegrationController {

    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    private static final Map<String, String> CATEGORIES = new LinkedHashMap<>();
    private static final Map<String, List<Map<String, Object>>> FIELDS = new LinkedHashMap<>();

    static {
        LABELS.put("meta_app", "Meta App (WhatsApp / Instagram / Messenger / Facebook)");
        LABELS.put("sms_twilio", "SMS (Twilio)");
        LABELS.put("oauth_linkedin", "LinkedIn OAuth");
        LABELS.put("oauth_twitter", "Twitter / X OAuth");
        LABELS.put("oauth_youtube", "YouTube / Google OAuth");
        LABELS.put("oauth_tiktok", "TikTok OAuth");
        LABELS.put("oauth_shopify", "Shopify App (OAuth)");
        LABELS.put("oauth_bigcommerce", "BigCommerce App (OAuth)");
        LABELS.put("llm_openai_default", "OpenAI (Default)");
        LABELS.put("llm_anthropic_default", "Anthropic Claude (Default)");
        LABELS.put("llm_gemini_default", "Google Gemini (Default)");
        LABELS.put("google_places", "Google Places API");
        LABELS.put("google_workspace", "Google Workspace (Sheets / Docs / Calendar / Meet)");
        LABELS.put("qdrant", "Qdrant Vector Store");
        LABELS.put("storage_local", "Local Storage (server disk)");
        LABELS.put("storage_s3", "Amazon S3");
        LABELS.put("storage_do", "DigitalOcean Spaces");
        LABELS.put("storage_wasabi", "Wasabi Cloud Storage");

        CATEGORIES.put("meta_app", "Meta");
        CATEGORIES.put("sms_twilio", "SMS");
        CATEGORIES.put("oauth_linkedin", "Social OAuth");
        CATEGORIES.put("oauth_twitter", "Social OAuth");
        CATEGORIES.put("oauth_youtube", "Social OAuth");
        CATEGORIES.put("oauth_tiktok", "Social OAuth");
        CATEGORIES.put("oauth_shopify", "E-Commerce OAuth");
        CATEGORIES.put("oauth_bigcommerce", "E-Commerce OAuth");
        CATEGORIES.put("llm_openai_default", "AI / LLM");
        CATEGORIES.put("llm_anthropic_default", "AI / LLM");
        CATEGORIES.put("llm_gemini_default", "AI / LLM");
        CATEGORIES.put("google_places", "Maps");
        CATEGORIES.put("google_workspace", "Google Workspace");
        CATEGORIES.put("qdrant", "Vector Store");
        CATEGORIES.put("storage_local", "Storage");
        CATEGORIES.put("storage_s3", "Storage");
        CATEGORIES.put("storage_do", "Storage");
        CATEGORIES.put("storage_wasabi", "Storage");

        FIELDS.put("meta_app", List.of(
                field("app_id", "App ID", "text", true, null),
                field("app_secret", "App Secret", "password", true, null),
                field("system_user_token", "System User Access Token", "password", false,
                        "Long-lived token from Meta Business Manager > System Users, with whatsapp_business_messaging permission."),
                field("verify_token", "Webhook Verify Token", "text", false, null),
                field("config_id_whatsapp", "Embedded Signup Config ID (WhatsApp)", "text", false,
                        "Facebook Login for Business configuration ID for the WhatsApp Embedded Signup flow, from Meta App Dashboard > Facebook Login for Business > Configurations."),
                field("config_id_social", "Embedded Signup Config ID (Instagram/Messenger)", "text", false,
                        "Facebook Login for Business configuration ID for the Instagram/Messenger Embedded Signup flow.")
        ));
        FIELDS.put("sms_twilio", List.of(
                field("account_sid", "Account SID", "text", true, null),
                field("auth_token", "Auth Token", "password", true, null),
                field("from_number", "From Number", "text", true, "The Twilio phone number or Messaging Service SID to send from, e.g. +15551234567.")
        ));
        FIELDS.put("google_places", List.of(
                field("api_key", "API Key", "password", true, "A Google Cloud API key with the Places API enabled.")
        ));
        FIELDS.put("oauth_shopify", List.of(
                field("client_id", "Client ID (API Key)", "text", true, null),
                field("client_secret", "Client Secret", "password", true, null)
        ));
        FIELDS.put("oauth_bigcommerce", List.of(
                field("client_id", "Client ID", "text", true, null),
                field("client_secret", "Client Secret", "password", true, null)
        ));
        FIELDS.put("oauth_linkedin", List.of(
                field("client_id", "Client ID", "text", true, null),
                field("client_secret", "Client Secret", "password", true, null)
        ));
        FIELDS.put("oauth_twitter", List.of(
                field("client_id", "Client ID", "text", true, null),
                field("client_secret", "Client Secret", "password", true, null)
        ));
        FIELDS.put("oauth_youtube", List.of(
                field("client_id", "Client ID", "text", true, null),
                field("client_secret", "Client Secret", "password", true, null)
        ));
        FIELDS.put("oauth_tiktok", List.of(
                field("client_id", "Client Key", "text", true, null),
                field("client_secret", "Client Secret", "password", true, null)
        ));
    }

    private static Map<String, Object> field(String key, String label, String type, boolean required, String hint) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("key", key);
        f.put("label", label);
        f.put("type", type);
        f.put("required", required);
        if (hint != null) f.put("hint", hint);
        return f;
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final IntegrationCredentialsService credentialsService;
    private final ObjectMapper objectMapper;

    public AdminIntegrationController(IntegrationCredentialsService credentialsService, ObjectMapper objectMapper) {
        this.credentialsService = credentialsService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public Object index() {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : LABELS.entrySet()) {
            String provider = entry.getKey();
            String category = CATEGORIES.getOrDefault(provider, "Other");
            IntegrationConfig config = credentialsService.find(provider);
            List<String> requiredKeys = requiredFieldKeys(provider);
            Map<String, String> creds = credentialsService.getCredentials(provider);
            boolean configured = !requiredKeys.isEmpty() && requiredKeys.stream()
                    .allMatch(k -> creds.get(k) != null && !creds.get(k).isBlank());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("provider", provider);
            item.put("label", entry.getValue());
            item.put("category", category);
            item.put("enabled", config != null && Boolean.TRUE.equals(config.getEnabled()));
            item.put("is_default", config != null && Boolean.TRUE.equals(config.getIsDefault()));
            item.put("mode", config != null ? config.getMode() : "live");
            item.put("configured", configured);
            item.put("last_test_status", config != null && config.getLastTestStatus() != null ? config.getLastTestStatus() : "untested");
            item.put("last_test_message", config != null ? config.getLastTestMessage() : null);
            item.put("last_tested_at", config != null ? config.getLastTestedAt() : null);

            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(item);
        }

        return Inertia.render("Admin/Integrations/Index", Map.of("grouped", grouped));
    }

    @GetMapping("/{provider}")
    public Object edit(@PathVariable String provider) {
        if (!LABELS.containsKey(provider)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        IntegrationConfig existing = credentialsService.find(provider);
        List<Map<String, Object>> fields = FIELDS.getOrDefault(provider, List.of());

        // Never send secret values back to the browser — blank means "unchanged" on save.
        Map<String, Object> credentialsForForm = new LinkedHashMap<>();
        Map<String, String> stored = credentialsService.getCredentials(provider);
        for (Map<String, Object> f : fields) {
            String key = (String) f.get("key");
            boolean isSecret = "password".equals(f.get("type"));
            String value = stored.get(key);
            credentialsForForm.put(key, isSecret ? (value != null && !value.isBlank() ? "" : "") : (value != null ? value : ""));
            if (isSecret) f.put("has_value", value != null && !value.isBlank());
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", existing != null && Boolean.TRUE.equals(existing.getEnabled()));
        config.put("mode", existing != null ? existing.getMode() : "live");
        config.put("credentials", credentialsForForm);
        config.put("last_test_status", existing != null && existing.getLastTestStatus() != null ? existing.getLastTestStatus() : "untested");
        config.put("last_test_message", existing != null ? existing.getLastTestMessage() : null);
        config.put("last_tested_at", existing != null ? existing.getLastTestedAt() : null);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("provider", provider);
        props.put("label", LABELS.get(provider));
        props.put("category", CATEGORIES.getOrDefault(provider, "Other"));
        props.put("fields", fields);
        props.put("config", config);

        return Inertia.render("Admin/Integrations/Edit", props);
    }

    @SuppressWarnings("unchecked")
    @RequestMapping(value = "/{provider}", method = { RequestMethod.PUT, RequestMethod.POST })
    public Object update(@PathVariable String provider, @RequestBody Map<String, Object> body) {
        if (!LABELS.containsKey(provider)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        Map<String, Object> rawCredentials = body.get("credentials") instanceof Map
                ? (Map<String, Object>) body.get("credentials") : Map.of();
        Map<String, String> credentials = new LinkedHashMap<>();
        rawCredentials.forEach((k, v) -> credentials.put(k, v != null ? v.toString() : null));

        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        credentialsService.saveCredentials(provider, LABELS.get(provider), credentials, enabled, "live");

        return edit(provider);
    }

    @PostMapping("/{provider}/test")
    public Map<String, Object> test(@PathVariable String provider) {
        if (!LABELS.containsKey(provider)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        Map<String, Object> result = testConnection(provider);
        credentialsService.recordTestResult(provider, Boolean.TRUE.equals(result.get("ok")), (String) result.get("message"));
        return result;
    }

    @PostMapping("/{provider}/rotate")
    public Map<String, Object> rotate(@PathVariable String provider) {
        if (!LABELS.containsKey(provider)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        if (!"meta_app".equals(provider)) {
            return Map.of("ok", false, "message", "This integration does not use a webhook secret.");
        }

        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String newToken = HexFormat.of().formatHex(bytes);
        credentialsService.updateCredentialField(provider, "verify_token", newToken);
        return Map.of("ok", true, "message", "Webhook verify token rotated.");
    }

    /** Mirrors the PHP ConnectionTester: a real live check for the providers this UI actually configures. */
    private Map<String, Object> testConnection(String provider) {
        Map<String, String> creds = credentialsService.getCredentials(provider);
        try {
            return switch (provider) {
                case "meta_app" -> testMeta(creds);
                case "sms_twilio" -> testTwilio(creds);
                case "google_places" -> testGooglePlaces(creds);
                case "oauth_shopify", "oauth_bigcommerce", "oauth_linkedin", "oauth_twitter", "oauth_youtube", "oauth_tiktok" -> testOAuthPresence(creds);
                default -> Map.of("ok", false, "message", "No test available for this provider.");
            };
        } catch (Exception e) {
            return Map.of("ok", false, "message", "Connection test failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> testMeta(Map<String, String> creds) throws Exception {
        String token = creds.get("system_user_token");
        if (token == null || token.isBlank()) {
            return Map.of("ok", false, "message", "System user token is not configured.");
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://graph.facebook.com/v20.0/me?access_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);
        if (response.statusCode() < 400 && body.get("id") != null) {
            return Map.of("ok", true, "message", "Connected. User ID: " + body.get("id"));
        }
        Object error = body.get("error");
        String message = error instanceof Map ? String.valueOf(((Map<String, Object>) error).getOrDefault("message", "Meta API error.")) : "Meta API error.";
        return Map.of("ok", false, "message", message);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> testTwilio(Map<String, String> creds) throws Exception {
        String sid = creds.get("account_sid");
        String token = creds.get("auth_token");
        if (sid == null || sid.isBlank() || token == null || token.isBlank()) {
            return Map.of("ok", false, "message", "Account SID and Auth Token required.");
        }
        String basicAuth = java.util.Base64.getEncoder().encodeToString((sid + ":" + token).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + sid + ".json"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Basic " + basicAuth)
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);
        if (response.statusCode() < 400) {
            return Map.of("ok", true, "message", "Twilio account connected: " + body.get("friendly_name"));
        }
        return Map.of("ok", false, "message", String.valueOf(body.getOrDefault("message", "Twilio error.")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> testGooglePlaces(Map<String, String> creds) throws Exception {
        String key = creds.get("api_key");
        if (key == null || key.isBlank()) {
            return Map.of("ok", false, "message", "API Key is required.");
        }
        String query = "query=" + URLEncoder.encode("restaurants in New York", StandardCharsets.UTF_8) + "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://maps.googleapis.com/maps/api/place/textsearch/json?" + query))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);
        String status = String.valueOf(body.getOrDefault("status", ""));
        if ("OK".equals(status) || "ZERO_RESULTS".equals(status)) {
            return Map.of("ok", true, "message", "Google Places API connected.");
        }
        return Map.of("ok", false, "message", "Places API error: " + status);
    }

    private Map<String, Object> testOAuthPresence(Map<String, String> creds) {
        String id = creds.get("client_id");
        String secret = creds.get("client_secret");
        if (id == null || id.isBlank() || secret == null || secret.isBlank()) {
            return Map.of("ok", false, "message", "Client ID and Secret are required.");
        }
        return Map.of("ok", true, "message", "Credentials are present. OAuth flow will validate them at runtime.");
    }

    private List<String> requiredFieldKeys(String provider) {
        List<Map<String, Object>> fields = FIELDS.getOrDefault(provider, List.of());
        List<String> keys = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            if (Boolean.TRUE.equals(f.get("required"))) keys.add((String) f.get("key"));
        }
        return keys;
    }
}
