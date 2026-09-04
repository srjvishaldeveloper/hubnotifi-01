package com.whatsmine.controller.webhook;

import com.whatsmine.service.billing.BillingGatewayInterface;
import com.whatsmine.service.billing.BillingGatewayRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final BillingGatewayRegistry gatewayRegistry;

    public WebhookController(BillingGatewayRegistry gatewayRegistry) {
        this.gatewayRegistry = gatewayRegistry;
    }

    @PostMapping("/{gateway:stripe|paddle|paypal|razorpay|mollie}")
    public ResponseEntity<?> handleGatewayWebhook(@PathVariable String gateway, HttpServletRequest request) {
        BillingGatewayInterface gatewayImpl = gatewayRegistry.get(gateway);
        if (gatewayImpl == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Gateway not configured");
        }
        return gatewayImpl.handleWebhook(request);
    }
}
