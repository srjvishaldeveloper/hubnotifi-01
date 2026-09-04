package com.whatsmine.controller.api;

import com.whatsmine.model.User;
import com.whatsmine.model.WebhookEndpoint;
import com.whatsmine.repository.WebhookEndpointRepository;
import com.whatsmine.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
public class OutboundWebhookApiController {

    private final WebhookEndpointRepository webhookEndpointRepository;

    public OutboundWebhookApiController(WebhookEndpointRepository webhookEndpointRepository) {
        this.webhookEndpointRepository = webhookEndpointRepository;
    }

    @GetMapping
    public ResponseEntity<List<WebhookEndpoint>> index(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        return ResponseEntity.ok(webhookEndpointRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
    }

    @PostMapping
    public ResponseEntity<WebhookEndpoint> store(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                 @RequestBody Map<String, Object> payload) {
        User user = userDetails.getUser();
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setUserId(user.getId());
        endpoint.setUrl((String) payload.get("url"));
        endpoint.setDescription((String) payload.get("description"));
        endpoint.setSecret("whsec_" + UUID.randomUUID().toString().replace("-", ""));
        endpoint.setEnabled(true);
        return ResponseEntity.ok(webhookEndpointRepository.save(endpoint));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> destroy(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                       @PathVariable Long id) {
        User user = userDetails.getUser();
        WebhookEndpoint endpoint = webhookEndpointRepository.findById(id).orElse(null);
        if (endpoint != null && endpoint.getUserId().equals(user.getId())) {
            webhookEndpointRepository.delete(endpoint);
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
