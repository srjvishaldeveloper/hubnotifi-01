package com.whatsmine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.WebhookDelivery;
import com.whatsmine.model.WebhookEndpoint;
import com.whatsmine.repository.WebhookDeliveryRepository;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

@Service
public class WebhookDispatchService {

    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final ObjectMapper objectMapper;

    public WebhookDispatchService(WebhookDeliveryRepository webhookDeliveryRepository, ObjectMapper objectMapper) {
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.objectMapper = objectMapper;
    }

    public WebhookDelivery dispatchToEndpoint(WebhookEndpoint endpoint, String event, Map<String, Object> payload) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setWebhookEndpointId(endpoint.getId());
        delivery.setEvent(event);
        delivery.setPayload(payload);

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            String signature = calculateHmacSha256(jsonPayload, endpoint.getSecret());

            // Simulate HTTP delivery
            delivery.setResponseStatus(200);
            delivery.setResponseBody("{\"status\":\"success\",\"signature\":\"" + signature + "\"}");
            delivery.setDeliveredAt(LocalDateTime.now());
        } catch (Exception e) {
            delivery.setResponseStatus(500);
            delivery.setResponseBody("{\"error\":\"" + e.getMessage() + "\"}");
        }

        return webhookDeliveryRepository.save(delivery);
    }

    private String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            return "";
        }
    }
}
