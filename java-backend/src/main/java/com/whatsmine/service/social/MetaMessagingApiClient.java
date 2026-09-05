package com.whatsmine.service.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.ChannelAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Facebook Messenger + Instagram DM send API. Both ride the same Meta
 * "Send API" (graph.facebook.com), just a different endpoint/token source —
 * ChannelAccount.credentials holds a per-page/per-IG-account token (set up
 * via manual entry today, same as the Meta App/WhatsApp credentials — no
 * embedded-signup OAuth wizard exists yet, tracked as a separate follow-up).
 * No credentials configured = the send throws, loudly, rather than
 * fabricating a fake success.
 */
@Service
public class MetaMessagingApiClient {

    private static final Logger log = LoggerFactory.getLogger(MetaMessagingApiClient.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${whatsapp.api.base-url:https://graph.facebook.com/v18.0}")
    private String baseUrl;

    private final ObjectMapper objectMapper;

    public MetaMessagingApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Sends a text message via a Facebook Page (Messenger). Returns the message_id. */
    public String sendMessengerText(ChannelAccount channelAccount, String psid, String text) {
        String token = credential(channelAccount, "page_access_token");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("This Messenger channel has no page_access_token configured.");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("recipient", Map.of("id", psid));
        payload.put("message", Map.of("text", text));
        payload.put("messaging_type", "RESPONSE");

        Map<String, Object> response = post("/me/messages", token, payload);
        return str(response.get("message_id"));
    }

    /**
     * Sends a text message via an Instagram Business Account DM. Falls back
     * to /me/messages on error code 3 (missing permission) — matches PHP's
     * InstagramDriver, needed for some Facebook-Login-based IG connections.
     */
    public String sendInstagramText(ChannelAccount channelAccount, String igsid, String text) {
        String token = credential(channelAccount, "access_token");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("This Instagram channel has no access_token configured.");
        }
        String igAccountId = credential(channelAccount, "instagram_account_id");

        Map<String, Object> payload = new HashMap<>();
        payload.put("recipient", Map.of("id", igsid));
        payload.put("message", Map.of("text", text));

        try {
            if (igAccountId == null || igAccountId.isBlank()) {
                throw new IllegalStateException("no instagram_account_id — falling back");
            }
            Map<String, Object> response = post("/" + igAccountId + "/messages", token, payload);
            return str(response.get("message_id"));
        } catch (Exception primaryError) {
            log.warn("Instagram send via /{}/messages failed ({}), falling back to /me/messages", igAccountId, primaryError.getMessage());
            Map<String, Object> response = post("/me/messages", token, payload);
            return str(response.get("message_id"));
        }
    }

    @SuppressWarnings("unchecked")
    private String credential(ChannelAccount channelAccount, String key) {
        String raw = channelAccount.getCredentials();
        if (raw == null || raw.isBlank()) return null;
        try {
            Map<String, Object> creds = objectMapper.readValue(raw, Map.class);
            Object value = creds.get(key);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, String accessToken, Map<String, Object> payload) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);

            if (response.statusCode() >= 400) {
                Map<String, Object> error = parsed.get("error") instanceof Map ? (Map<String, Object>) parsed.get("error") : Map.of();
                String message = error.getOrDefault("message", "HTTP " + response.statusCode()).toString();
                log.warn("Meta messaging send failed ({}): {}", response.statusCode(), message);
                throw new IllegalStateException("Meta API error: " + message);
            }

            return parsed;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Meta messaging API call failed: {}", e.getMessage());
            throw new IllegalStateException("Meta API call failed: " + e.getMessage(), e);
        }
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
