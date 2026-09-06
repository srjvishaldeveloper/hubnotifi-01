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

    public String sendTemplate(ChannelAccount channelAccount, String to, String templateName, String language, List<Map<String, Object>> components) {
        Map<String, Object> response = sendTemplateMessage(channelAccount.getPhoneNumberId(), resolveAccessToken(channelAccount), to, templateName, language, components);
        return extractMessageId(response);
    }

    /** mediaType is one of image/video/document/audio. Exactly one of link or mediaId should be set (link is the common case — a publicly reachable URL). */
    public String sendMedia(ChannelAccount channelAccount, String to, String mediaType, String link, String mediaId, String caption, String filename) {
        Map<String, Object> mediaObject = new HashMap<>();
        if (link != null && !link.isBlank()) mediaObject.put("link", link);
        if (mediaId != null && !mediaId.isBlank()) mediaObject.put("id", mediaId);
        if (caption != null && !caption.isBlank()) mediaObject.put("caption", caption);
        if (filename != null && !filename.isBlank()) mediaObject.put("filename", filename);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", mediaType);
        payload.put(mediaType, mediaObject);

        Map<String, Object> response = post(channelAccount.getPhoneNumberId(), resolveAccessToken(channelAccount), payload);
        return extractMessageId(response);
    }

    /** interactivePayload is the full WhatsApp `interactive` object (type: button/list/cta_url, body, action, ...). */
    public String sendInteractive(ChannelAccount channelAccount, String to, Map<String, Object> interactivePayload) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", "interactive");
        payload.put("interactive", interactivePayload);

        Map<String, Object> response = post(channelAccount.getPhoneNumberId(), resolveAccessToken(channelAccount), payload);
        return extractMessageId(response);
    }

    public String sendLocation(ChannelAccount channelAccount, String to, double latitude, double longitude, String name, String address) {
        Map<String, Object> location = new HashMap<>();
        location.put("latitude", latitude);
        location.put("longitude", longitude);
        if (name != null && !name.isBlank()) location.put("name", name);
        if (address != null && !address.isBlank()) location.put("address", address);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", "location");
        payload.put("location", location);

        Map<String, Object> response = post(channelAccount.getPhoneNumberId(), resolveAccessToken(channelAccount), payload);
        return extractMessageId(response);
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

    /** Uploads a file to WhatsApp's Media endpoint (multipart/form-data) and returns its media id. */
    @SuppressWarnings("unchecked")
    public String uploadMedia(ChannelAccount channelAccount, byte[] fileBytes, String filename, String mimeType) {
        String phoneNumberId = channelAccount.getPhoneNumberId();
        String accessToken = resolveAccessToken(channelAccount);
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("No WhatsApp access token configured — add a System User Token under Admin > Integrations > Meta App.");
        }
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            throw new IllegalStateException("This channel account has no phone_number_id.");
        }

        String boundary = "----WhatsMineBoundary" + System.currentTimeMillis();
        byte[] body = buildMultipartBody(boundary, fileBytes, filename, mimeType);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + phoneNumberId + "/media"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);

            if (response.statusCode() >= 400) {
                Map<String, Object> error = parsed.get("error") instanceof Map ? (Map<String, Object>) parsed.get("error") : Map.of();
                String message = error.getOrDefault("message", "HTTP " + response.statusCode()).toString();
                throw new IllegalStateException("WhatsApp media upload failed: " + message);
            }
            return String.valueOf(parsed.get("id"));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("WhatsApp media upload failed: " + e.getMessage(), e);
        }
    }

    private byte[] buildMultipartBody(String boundary, byte[] fileBytes, String filename, String mimeType) {
        String CRLF = "\r\n";
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"messaging_product\"" + CRLF + CRLF + "whatsapp" + CRLF).getBytes(StandardCharsets.UTF_8));
            out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"type\"" + CRLF + CRLF + mimeType + CRLF).getBytes(StandardCharsets.UTF_8));
            out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"" + CRLF).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: " + mimeType + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
            out.write(fileBytes);
            out.write((CRLF + "--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
        return out.toByteArray();
    }

    /** Resolves a media id to its (short-lived) download URL + mime type. */
    @SuppressWarnings("unchecked")
    public Map<String, String> getMediaUrl(ChannelAccount channelAccount, String mediaId) {
        String accessToken = resolveAccessToken(channelAccount);
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("No WhatsApp access token configured — add a System User Token under Admin > Integrations > Meta App.");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + mediaId))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);
            if (response.statusCode() >= 400) {
                Map<String, Object> error = parsed.get("error") instanceof Map ? (Map<String, Object>) parsed.get("error") : Map.of();
                throw new IllegalStateException("WhatsApp media lookup failed: " + error.getOrDefault("message", "HTTP " + response.statusCode()));
            }
            return Map.of("url", String.valueOf(parsed.get("url")), "mime_type", String.valueOf(parsed.get("mime_type")));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("WhatsApp media lookup failed: " + e.getMessage(), e);
        }
    }

    /** Downloads media bytes from a Graph API media URL (requires the same bearer token). */
    public byte[] downloadMedia(ChannelAccount channelAccount, String url) {
        String accessToken = resolveAccessToken(channelAccount);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("WhatsApp media download failed: HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("WhatsApp media download failed: " + e.getMessage(), e);
        }
    }

    /** Lists the phone numbers assigned to a WABA in Meta Business Manager, ported from CloudApiClient::fetchWabaPhoneNumbers(). */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchWabaPhoneNumbers(String wabaId, String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + wabaId + "/phone_numbers?fields=id,display_phone_number,verified_name,quality_rating,throughput"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Meta phone_numbers request failed (" + response.statusCode() + "): " + response.body());
            }
            Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);
            Object data = parsed.get("data");
            return data instanceof List ? (List<Map<String, Object>>) data : List.of();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Meta phone_numbers request failed: " + e.getMessage(), e);
        }
    }

    /** Loads a single phone-number node (verification/name status), ported from CloudApiClient::fetchPhoneNumberDetails(). Returns null on any failure rather than throwing, matching PHP. */
    public Map<String, Object> fetchPhoneNumberDetails(String phoneNumberId, String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + phoneNumberId
                            + "?fields=id,display_phone_number,verified_name,quality_rating,throughput,code_verification_status,name_status,requested_verified_name,account_mode"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return null;
            }
            return objectMapper.readValue(response.body(), Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** Requests a verified-name change on a phone number, ported from CloudApiClient::requestDisplayNameChangeDirect(). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> requestDisplayNameChange(String phoneNumberId, String newName, String accessToken) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("verified_name", newName));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + phoneNumberId))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> parsed = response.body() != null && !response.body().isBlank()
                    ? objectMapper.readValue(response.body(), Map.class) : Map.of();
            Map<String, Object> result = new HashMap<>();
            result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
            result.put("status", response.statusCode());
            result.put("response", parsed);
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("status", 0);
            result.put("response", Map.of("error", Map.of("message", e.getMessage())));
            return result;
        }
    }

    /** Submits a new template for approval, ported from CloudApiClient::submitTemplate(). Returns Meta's raw JSON response (an "id" key on success, an "error" object on failure). */
    public Map<String, Object> submitTemplate(String wabaId, Map<String, Object> payload, String accessToken) {
        return graphPost(wabaId + "/message_templates", payload, accessToken);
    }

    /** Edits an existing template's category/components, ported from CloudApiClient::editTemplate(). Name/language cannot be changed on Meta once created. */
    public Map<String, Object> editTemplate(String metaTemplateId, Map<String, Object> payload, String accessToken) {
        return graphPost(metaTemplateId, payload, accessToken);
    }

    /** Deletes every language variant of a template by name, ported from CloudApiClient::deleteTemplate(). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> deleteTemplate(String wabaId, String name, String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + wabaId + "/message_templates?name=" + java.net.URLEncoder.encode(name, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken)
                    .DELETE()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> parsed = response.body() != null && !response.body().isBlank()
                    ? objectMapper.readValue(response.body(), Map.class) : new HashMap<>();
            parsed.put("_success", response.statusCode() >= 200 && response.statusCode() < 300);
            parsed.put("_status", response.statusCode());
            return parsed;
        } catch (Exception e) {
            return Map.of("_success", false, "_status", 0, "error", Map.of("message", e.getMessage()));
        }
    }

    /** Lists every template registered on Meta for this WABA, ported from CloudApiClient::fetchTemplates(). */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchTemplates(String wabaId, String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + wabaId + "/message_templates?limit=200"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Meta message_templates request failed (" + response.statusCode() + "): " + response.body());
            }
            Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);
            Object data = parsed.get("data");
            return data instanceof List ? (List<Map<String, Object>>) data : List.of();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Meta message_templates request failed: " + e.getMessage(), e);
        }
    }

    /** Meta's resumable-upload protocol for template header media, ported from CloudApiClient::resumableUpload(). Returns the upload handle to embed in components[].example.header_handle. */
    @SuppressWarnings("unchecked")
    public String resumableUpload(String appId, String accessToken, byte[] fileBytes, String mimeType) {
        try {
            String sessionBody = objectMapper.writeValueAsString(Map.of("file_length", fileBytes.length, "file_type", mimeType));
            HttpRequest sessionRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + appId + "/uploads"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(sessionBody))
                    .build();
            HttpResponse<String> sessionResponse = HTTP_CLIENT.send(sessionRequest, HttpResponse.BodyHandlers.ofString());
            if (sessionResponse.statusCode() >= 400) {
                throw new IllegalStateException("Meta upload session failed (" + sessionResponse.statusCode() + "): " + sessionResponse.body());
            }
            Map<String, Object> sessionParsed = objectMapper.readValue(sessionResponse.body(), Map.class);
            String sessionId = str(sessionParsed.get("id"));
            if (sessionId == null || sessionId.isBlank()) {
                throw new IllegalStateException("Meta upload session returned no id: " + sessionResponse.body());
            }

            HttpRequest uploadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + sessionId))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "OAuth " + accessToken)
                    .header("file_offset", "0")
                    .header("Content-Type", mimeType)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(fileBytes))
                    .build();
            HttpResponse<String> uploadResponse = HTTP_CLIENT.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
            if (uploadResponse.statusCode() >= 400) {
                throw new IllegalStateException("Meta resumable upload failed (" + uploadResponse.statusCode() + "): " + uploadResponse.body());
            }
            Map<String, Object> uploadParsed = objectMapper.readValue(uploadResponse.body(), Map.class);
            String handle = str(uploadParsed.get("h"));
            if (handle == null || handle.isBlank()) {
                throw new IllegalStateException("Meta resumable upload returned no handle: " + uploadResponse.body());
            }
            return handle;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Meta resumable upload failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> graphPost(String path, Map<String, Object> payload, String accessToken) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> parsed = response.body() != null && !response.body().isBlank()
                    ? objectMapper.readValue(response.body(), Map.class) : new HashMap<>();
            parsed.put("_success", response.statusCode() >= 200 && response.statusCode() < 300);
            parsed.put("_status", response.statusCode());
            return parsed;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("_success", false);
            result.put("_status", 0);
            result.put("error", Map.of("message", e.getMessage()));
            return result;
        }
    }

    private String str(Object o) {
        return o != null ? String.valueOf(o) : null;
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
