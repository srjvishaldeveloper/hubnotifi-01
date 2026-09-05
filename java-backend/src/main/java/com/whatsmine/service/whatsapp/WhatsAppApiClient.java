package com.whatsmine.service.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.ChannelAccount;
import com.whatsmine.service.IntegrationCredentialsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * WhatsApp Cloud API client. Real HTTP calls to graph.facebook.com — the
 * access token comes from the workspace's ChannelAccount credentials if it
 * has its own, otherwise from the system-level "meta_app" integration
 * (Admin > Integrations, where a master admin pastes in a System User token
 * from Meta Business Manager). No token configured anywhere = the send
 * throws, loudly, rather than fabricating a fake success.
 */
@Service
public class WhatsAppApiClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppApiClient.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${whatsapp.api.base-url:https://graph.facebook.com/v18.0}")
    private String baseUrl;

    @Value("${whatsapp.api.app-secret:demo_meta_app_secret}")
    private String appSecret;

    @Value("${whatsapp.api.global-verify-token:wh_global_verify}")
    private String globalVerifyToken;

    private final IntegrationCredentialsService integrationCredentialsService;
    private final ObjectMapper objectMapper;

    public WhatsAppApiClient(IntegrationCredentialsService integrationCredentialsService, ObjectMapper objectMapper) {
        this.integrationCredentialsService = integrationCredentialsService;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolves phone_number_id + access token for this channel account and
     * sends a plain text message. Throws if no access token is configured
     * anywhere (system Meta App integration or a per-channel override) —
     * callers already handle send failures (message marked "failed").
     */
    public String sendText(ChannelAccount channelAccount, String to, String text) {
        String phoneNumberId = channelAccount.getPhoneNumberId();
        String accessToken = resolveAccessToken(channelAccount);
        Map<String, Object> response = sendTextMessage(phoneNumberId, accessToken, to, text);
        return extractMessageId(response);
    }

    /** Legacy convenience overload some call sites still use — resolves credentials the same way as {@link #sendText}. Deprecated in favor of the ChannelAccount-aware overload once callers are updated. */
    @Deprecated
    public String sendTextMessage(String to, String text) {
        throw new IllegalStateException("sendTextMessage(to, text) can't resolve which WhatsApp number/token to send from — use sendText(channelAccount, to, text) instead.");
    }

    public Map<String, Object> sendTextMessage(String phoneNumberId, String accessToken, String to, String text) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", to);
        payload.put("type", "text");
        payload.put("text", Map.of("body", text));
        return post(phoneNumberId, accessToken, payload);
    }

    public Map<String, Object> sendTemplateMessage(String phoneNumberId, String accessToken, String to, String templateName, String language, List<Map<String, Object>> components) {
        Map<String, Object> template = new HashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", language != null ? language : "en"));
        if (components != null && !components.isEmpty()) {
            template.put("components", components);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", "template");
        payload.put("template", template);
        return post(phoneNumberId, accessToken, payload);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String phoneNumberId, String accessToken, Map<String, Object> payload) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("No WhatsApp access token configured — add a System User Token under Admin > Integrations > Meta App.");
        }
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            throw new IllegalStateException("This channel account has no phone_number_id.");
        }

        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + phoneNumberId + "/messages"))
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
                log.warn("WhatsApp send failed ({}): {}", response.statusCode(), message);
                throw new IllegalStateException("WhatsApp API error: " + message);
            }

            return parsed;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("WhatsApp API call failed: {}", e.getMessage());
            throw new IllegalStateException("WhatsApp API call failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractMessageId(Map<String, Object> response) {
        List<Map<String, Object>> messages = (List<Map<String, Object>>) response.get("messages");
        return messages != null && !messages.isEmpty() ? String.valueOf(messages.get(0).get("id")) : null;
    }

    /** Per-channel override (ChannelAccount.credentials.access_token) first, else the system-level Meta App System User Token. */
    @SuppressWarnings("unchecked")
    private String resolveAccessToken(ChannelAccount channelAccount) {
        String raw = channelAccount.getCredentials();
        if (raw != null && !raw.isBlank()) {
            try {
                Map<String, Object> creds = objectMapper.readValue(raw, Map.class);
                Object token = creds.get("access_token");
                if (token != null && !token.toString().isBlank()) {
                    return token.toString();
                }
            } catch (Exception ignored) {
                // Not JSON, or no override — fall through to the system-level token.
            }
        }
        return integrationCredentialsService.getCredential("meta_app", "system_user_token");
    }

    public boolean verifyHmacSignature(String rawBody, String headerSignature, String secret) {
        if (headerSignature == null || !headerSignature.startsWith("sha256=")) {
            return false;
        }
        String receivedHmac = headerSignature.substring(7);
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec((secret != null ? secret : appSecret).getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] signedBytes = sha256Hmac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String expectedHmac = HexFormat.of().formatHex(signedBytes);
            return expectedHmac.equalsIgnoreCase(receivedHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }

    public String getGlobalVerifyToken() {
        return globalVerifyToken;
    }
}
