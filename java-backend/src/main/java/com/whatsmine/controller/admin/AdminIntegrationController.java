package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.IntegrationConfig;
import com.whatsmine.service.IntegrationCredentialsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
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
                field("verify_token", "Webhook Verify Token", "text", false, null)
        ));
        FIELDS.put("sms_twilio", List.of(
                field("account_sid", "Account SID", "text", true, null),
                field("auth_token", "Auth Token", "password", true, null),
                field("from_number", "From Number", "text", true, "The Twilio phone number or Messaging Service SID to send from, e.g. +15551234567.")
        ));
        FIELDS.put("google_places", List.of(
                field("api_key", "API Key", "password", true, "A Google Cloud API key with the Places API enabled.")
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

    private final IntegrationCredentialsService credentialsService;

    public AdminIntegrationController(IntegrationCredentialsService credentialsService) {
        this.credentialsService = credentialsService;
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

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("provider", provider);
        props.put("label", LABELS.get(provider));
        props.put("category", CATEGORIES.getOrDefault(provider, "Other"));
        props.put("fields", fields);
        props.put("config", config);

        return Inertia.render("Admin/Integrations/Edit", props);
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/{provider}")
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

    private List<String> requiredFieldKeys(String provider) {
        List<Map<String, Object>> fields = FIELDS.getOrDefault(provider, List.of());
        List<String> keys = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            if (Boolean.TRUE.equals(f.get("required"))) keys.add((String) f.get("key"));
        }
        return keys;
    }
}
