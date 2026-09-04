package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.PaymentGatewayConfig;
import com.whatsmine.repository.PaymentGatewayConfigRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/admin/payment-gateways")
public class AdminPaymentGatewayConfigController {

    private final PaymentGatewayConfigRepository gatewayConfigRepository;

    private static final List<String> GATEWAYS = List.of(
            "stripe", "paypal", "paddle", "razorpay", "cashfree", "tap",
            "paystack", "paymob", "myfatoorah", "xendit", "mollie", "square", "mercadopago"
    );

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("stripe", "Stripe"),
            Map.entry("paypal", "PayPal"),
            Map.entry("paddle", "Paddle"),
            Map.entry("razorpay", "Razorpay"),
            Map.entry("cashfree", "Cashfree"),
            Map.entry("tap", "Tap"),
            Map.entry("paystack", "Paystack"),
            Map.entry("paymob", "Paymob"),
            Map.entry("myfatoorah", "MyFatoorah"),
            Map.entry("xendit", "Xendit"),
            Map.entry("mollie", "Mollie"),
            Map.entry("square", "Square"),
            Map.entry("mercadopago", "Mercado Pago")
    );

    public AdminPaymentGatewayConfigController(PaymentGatewayConfigRepository gatewayConfigRepository) {
        this.gatewayConfigRepository = gatewayConfigRepository;
    }

    @GetMapping
    public Object index() {
        List<PaymentGatewayConfig> configs = gatewayConfigRepository.findAll();
        Map<String, PaymentGatewayConfig> map = new LinkedHashMap<>();
        for (PaymentGatewayConfig c : configs) {
            map.put(c.getGateway(), c);
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (String key : GATEWAYS) {
            PaymentGatewayConfig config = map.get(key);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("gateway", key);
            item.put("name", LABELS.getOrDefault(key, key.substring(0, 1).toUpperCase() + key.substring(1)));
            item.put("enabled", config != null && Boolean.TRUE.equals(config.getEnabled()));
            item.put("test_mode", config == null || Boolean.TRUE.equals(config.getTestMode()));
            item.put("configured", config != null && config.hasActiveCredentials());
            list.add(item);
        }

        return Inertia.render("Admin/PaymentGateways/Index", Map.of("gateways", list));
    }

    @GetMapping("/{gateway}")
    @ResponseBody
    public ResponseEntity<?> show(@PathVariable String gateway) {
        String key = gateway.toLowerCase();
        if (!GATEWAYS.contains(key)) {
            return ResponseEntity.notFound().build();
        }

        Optional<PaymentGatewayConfig> configOpt = gatewayConfigRepository.findByGateway(key);
        PaymentGatewayConfig config;
        if (configOpt.isPresent()) {
            config = configOpt.get();
        } else {
            config = new PaymentGatewayConfig();
            config.setGateway(key);
            config.setTestMode(true);
            config.setEnabled(false);
            config.setCredentials(Map.of(
                    "test", Map.of("publishable_key", "", "secret_key", "", "webhook_secret", ""),
                    "live", Map.of("publishable_key", "", "secret_key", "", "webhook_secret", "")
            ));
            config = gatewayConfigRepository.save(config);
        }

        Map<String, Object> creds = config.getCredentials() != null ? config.getCredentials() : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> test = creds.get("test") instanceof Map ? (Map<String, Object>) creds.get("test") : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> live = creds.get("live") instanceof Map ? (Map<String, Object>) creds.get("live") : Map.of();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("gateway", config.getGateway());
        res.put("name", LABELS.getOrDefault(key, key));
        res.put("test_mode", config.getTestMode());
        res.put("enabled", config.getEnabled());
        res.put("test_publishable_key", test.getOrDefault("publishable_key", ""));
        res.put("test_secret_key", test.get("secret_key") != null && !test.get("secret_key").toString().isBlank() ? "••••••••" : "");
        res.put("test_webhook_secret", test.get("webhook_secret") != null && !test.get("webhook_secret").toString().isBlank() ? "••••••••" : "");
        res.put("live_publishable_key", live.getOrDefault("publishable_key", ""));
        res.put("live_secret_key", live.get("secret_key") != null && !live.get("secret_key").toString().isBlank() ? "••••••••" : "");
        res.put("live_webhook_secret", live.get("webhook_secret") != null && !live.get("webhook_secret").toString().isBlank() ? "••••••••" : "");

        return ResponseEntity.ok(res);
    }

    @PutMapping("/{gateway}")
    public Object update(@PathVariable String gateway, @RequestBody Map<String, Object> payload, HttpSession session) {
        String key = gateway.toLowerCase();
        if (!GATEWAYS.contains(key)) {
            return Inertia.redirect("/admin/payment-gateways");
        }

        PaymentGatewayConfig config = gatewayConfigRepository.findByGateway(key).orElseGet(() -> {
            PaymentGatewayConfig c = new PaymentGatewayConfig();
            c.setGateway(key);
            return c;
        });

        config.setTestMode(payload.get("test_mode") == null || Boolean.TRUE.equals(payload.get("test_mode")));
        config.setEnabled(Boolean.TRUE.equals(payload.get("enabled")));

        Map<String, Object> existingCreds = config.getCredentials() != null ? new LinkedHashMap<>(config.getCredentials()) : new LinkedHashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> testMap = existingCreds.get("test") instanceof Map ? new LinkedHashMap<>((Map<String, Object>) existingCreds.get("test")) : new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> liveMap = existingCreds.get("live") instanceof Map ? new LinkedHashMap<>((Map<String, Object>) existingCreds.get("live")) : new LinkedHashMap<>();

        if (payload.get("test_publishable_key") != null && !payload.get("test_publishable_key").toString().startsWith("•")) {
            testMap.put("publishable_key", payload.get("test_publishable_key"));
        }
        if (payload.get("test_secret_key") != null && !payload.get("test_secret_key").toString().startsWith("•")) {
            testMap.put("secret_key", payload.get("test_secret_key"));
        }
        if (payload.get("test_webhook_secret") != null && !payload.get("test_webhook_secret").toString().startsWith("•")) {
            testMap.put("webhook_secret", payload.get("test_webhook_secret"));
        }

        if (payload.get("live_publishable_key") != null && !payload.get("live_publishable_key").toString().startsWith("•")) {
            liveMap.put("publishable_key", payload.get("live_publishable_key"));
        }
        if (payload.get("live_secret_key") != null && !payload.get("live_secret_key").toString().startsWith("•")) {
            liveMap.put("secret_key", payload.get("live_secret_key"));
        }
        if (payload.get("live_webhook_secret") != null && !payload.get("live_webhook_secret").toString().startsWith("•")) {
            liveMap.put("webhook_secret", payload.get("live_webhook_secret"));
        }

        existingCreds.put("test", testMap);
        existingCreds.put("live", liveMap);
        config.setCredentials(existingCreds);

        gatewayConfigRepository.save(config);

        Inertia.flashSuccess(session, "Payment gateway updated.");
        return Inertia.redirect("/admin/payment-gateways");
    }
}
