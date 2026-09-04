package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.User;
import com.whatsmine.model.WebhookDelivery;
import com.whatsmine.model.WebhookEndpoint;
import com.whatsmine.repository.WebhookDeliveryRepository;
import com.whatsmine.repository.WebhookEndpointRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.WebhookDispatchService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/webhooks")
public class ClientWebhookEndpointController {

    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final WebhookDispatchService webhookDispatchService;

    public ClientWebhookEndpointController(WebhookEndpointRepository webhookEndpointRepository,
                                           WebhookDeliveryRepository webhookDeliveryRepository,
                                           WebhookDispatchService webhookDispatchService) {
        this.webhookEndpointRepository = webhookEndpointRepository;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.webhookDispatchService = webhookDispatchService;
    }

    private Map<String, Object> endpointToArray(WebhookEndpoint e) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", e.getId());
        map.put("url", e.getUrl());
        map.put("description", e.getDescription());
        map.put("events", e.getEvents());
        map.put("enabled", Boolean.TRUE.equals(e.getEnabled()));
        map.put("created_at", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);

        List<WebhookDelivery> deliveries = webhookDeliveryRepository.findTop5ByWebhookEndpointIdOrderByCreatedAtDesc(e.getId());
        List<Map<String, Object>> rec = new ArrayList<>();
        for (WebhookDelivery d : deliveries) {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("id", d.getId());
            dm.put("event", d.getEvent());
            dm.put("response_status", d.getResponseStatus());
            dm.put("delivered_at", d.getDeliveredAt() != null ? d.getDeliveredAt().toString() : null);
            dm.put("created_at", d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
            rec.add(dm);
        }
        map.put("recent_deliveries", rec);
        return map;
    }

    @GetMapping
    public Object index(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        List<WebhookEndpoint> endpoints = webhookEndpointRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        List<Map<String, Object>> list = new ArrayList<>();
        for (WebhookEndpoint e : endpoints) {
            list.add(endpointToArray(e));
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("endpoints", list);

        return Inertia.render("client/Webhooks/Index", props);
    }

    @PostMapping
    public Object store(@AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestBody Map<String, Object> payload,
                        HttpSession session) {
        User user = userDetails.getUser();

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setUserId(user.getId());
        endpoint.setUrl((String) payload.get("url"));
        endpoint.setDescription((String) payload.get("description"));
        endpoint.setSecret("whsec_" + UUID.randomUUID().toString().replace("-", ""));
        endpoint.setEnabled(true);

        if (payload.get("events") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> evs = (List<String>) payload.get("events");
            endpoint.setEvents(evs);
        }

        webhookEndpointRepository.save(endpoint);

        Inertia.flashSuccess(session, "Webhook endpoint created.");
        return Inertia.redirect("/webhooks");
    }

    @PutMapping("/{id}")
    public Object update(@AuthenticationPrincipal CustomUserDetails userDetails,
                         @PathVariable Long id,
                         @RequestBody Map<String, Object> payload,
                         HttpSession session) {
        User user = userDetails.getUser();
        WebhookEndpoint endpoint = webhookEndpointRepository.findById(id).orElse(null);
        if (endpoint != null && endpoint.getUserId().equals(user.getId())) {
            if (payload.get("url") != null) endpoint.setUrl((String) payload.get("url"));
            if (payload.containsKey("description")) endpoint.setDescription((String) payload.get("description"));
            if (payload.containsKey("enabled")) endpoint.setEnabled(Boolean.TRUE.equals(payload.get("enabled")));
            if (payload.get("events") instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> evs = (List<String>) payload.get("events");
                endpoint.setEvents(evs);
            }
            webhookEndpointRepository.save(endpoint);
        }

        Inertia.flashSuccess(session, "Webhook endpoint updated.");
        return Inertia.redirect("/webhooks");
    }

    @PostMapping("/{id}/rotate-secret")
    public ResponseEntity<Map<String, Object>> rotateSecret(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                            @PathVariable Long id) {
        User user = userDetails.getUser();
        WebhookEndpoint endpoint = webhookEndpointRepository.findById(id).orElse(null);
        if (endpoint != null && endpoint.getUserId().equals(user.getId())) {
            String newSecret = "whsec_" + UUID.randomUUID().toString().replace("-", "");
            endpoint.setSecret(newSecret);
            webhookEndpointRepository.save(endpoint);
            return ResponseEntity.ok(Map.of("secret", newSecret));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public Object destroy(@AuthenticationPrincipal CustomUserDetails userDetails,
                         @PathVariable Long id,
                         HttpSession session) {
        User user = userDetails.getUser();
        WebhookEndpoint endpoint = webhookEndpointRepository.findById(id).orElse(null);
        if (endpoint != null && endpoint.getUserId().equals(user.getId())) {
            webhookEndpointRepository.delete(endpoint);
        }

        Inertia.flashSuccess(session, "Webhook endpoint deleted.");
        return Inertia.redirect("/webhooks");
    }

    @PostMapping("/{id}/test")
    public Object testDelivery(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @PathVariable Long id,
                               HttpSession session) {
        User user = userDetails.getUser();
        WebhookEndpoint endpoint = webhookEndpointRepository.findById(id).orElse(null);
        if (endpoint != null && endpoint.getUserId().equals(user.getId())) {
            webhookDispatchService.dispatchToEndpoint(endpoint, "test.ping", Map.of("message", "Test webhook delivery"));
        }

        Inertia.flashSuccess(session, "Test webhook queued.");
        return Inertia.redirect("/webhooks");
    }

    @GetMapping("/{id}/deliveries")
    public Object deliveries(@AuthenticationPrincipal CustomUserDetails userDetails,
                             @PathVariable Long id,
                             @RequestParam(defaultValue = "1") int page) {
        User user = userDetails.getUser();
        WebhookEndpoint endpoint = webhookEndpointRepository.findById(id).orElse(null);
        if (endpoint == null || !endpoint.getUserId().equals(user.getId())) {
            return Inertia.redirect("/webhooks");
        }

        Page<WebhookDelivery> deliveries = webhookDeliveryRepository.findByWebhookEndpointIdOrderByCreatedAtDesc(id, PageRequest.of(page - 1, 25));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("endpoint", Map.of("id", endpoint.getId(), "url", endpoint.getUrl()));
        props.put("deliveries", deliveries.getContent());

        return Inertia.render("client/Webhooks/Deliveries", props);
    }
}
