package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only port of the PHP IntegrationConfigController's index page. The
 * PHP side backs this with a full credential-management module (encrypted
 * storage, per-provider connection testers, OAuth clients) that hasn't been
 * ported to Java yet — this only renders the provider catalog so the admin
 * page loads instead of 404ing. Every provider shows as "not configured";
 * saving/testing credentials isn't wired up.
 */
@RestController
@RequestMapping("/admin/integrations")
public class AdminIntegrationController {

    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    private static final Map<String, String> CATEGORIES = new LinkedHashMap<>();

    static {
        LABELS.put("meta_app", "Meta App (WhatsApp / Instagram / Messenger / Facebook)");
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
    }

    @GetMapping
    public Object index() {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : LABELS.entrySet()) {
            String provider = entry.getKey();
            String category = CATEGORIES.getOrDefault(provider, "Other");

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("provider", provider);
            item.put("label", entry.getValue());
            item.put("category", category);
            item.put("enabled", false);
            item.put("is_default", false);
            item.put("mode", "live");
            item.put("configured", false);
            item.put("last_test_status", "untested");
            item.put("last_test_message", null);
            item.put("last_tested_at", null);

            grouped.computeIfAbsent(category, k -> new java.util.ArrayList<>()).add(item);
        }

        return Inertia.render("Admin/Integrations/Index", Map.of("grouped", grouped));
    }
}
