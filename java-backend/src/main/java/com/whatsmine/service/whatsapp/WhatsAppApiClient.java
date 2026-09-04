package com.whatsmine.service.whatsapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class WhatsAppApiClient {

    @Value("${whatsapp.api.base-url:https://graph.facebook.com/v18.0}")
    private String baseUrl;

    @Value("${whatsapp.api.app-secret:demo_meta_app_secret}")
    private String appSecret;

    @Value("${whatsapp.api.global-verify-token:wh_global_verify}")
    private String globalVerifyToken;

    public String sendTextMessage(String to, String text) {
        Map<String, Object> resp = sendTextMessage("default_phone", "default_token", to, text);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) resp.get("messages");
        return messages != null && !messages.isEmpty() ? (String) messages.get(0).get("id") : "wamid.mock" + System.currentTimeMillis();
    }

    public Map<String, Object> sendTextMessage(String phoneNumberId, String accessToken, String to, String text) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", to);
        payload.put("type", "text");
        payload.put("text", Map.of("body", text));

        Map<String, Object> response = new HashMap<>();
        response.put("messaging_product", "whatsapp");
        response.put("contacts", List.of(Map.of("input", to, "wa_id", to.replaceAll("[^0-9]", ""))));
        response.put("messages", List.of(Map.of("id", "wamid.HBgL" + System.currentTimeMillis())));
        return response;
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

        Map<String, Object> response = new HashMap<>();
        response.put("messaging_product", "whatsapp");
        response.put("contacts", List.of(Map.of("input", to, "wa_id", to.replaceAll("[^0-9]", ""))));
        response.put("messages", List.of(Map.of("id", "wamid.HBgL" + System.currentTimeMillis())));
        return response;
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
