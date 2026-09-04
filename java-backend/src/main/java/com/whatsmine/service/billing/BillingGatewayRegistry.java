package com.whatsmine.service.billing;

import com.whatsmine.model.PaymentGatewayConfig;
import com.whatsmine.repository.PaymentGatewayConfigRepository;
import com.whatsmine.repository.PaymentTransactionRepository;
import com.whatsmine.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BillingGatewayRegistry {

    private final Map<String, BillingGatewayInterface> gateways = new LinkedHashMap<>();
    private final PaymentGatewayConfigRepository gatewayConfigRepository;

    private static final Map<String, String> GATEWAY_LABELS = Map.ofEntries(
            Map.entry("stripe", "Stripe"),
            Map.entry("paypal", "PayPal"),
            Map.entry("paddle", "Paddle"),
            Map.entry("razorpay", "Razorpay"),
            Map.entry("cashfree", "Cashfree"),
            Map.entry("tap", "Tap"),
            Map.entry("paystack", "Paystack"),
            Map.entry("xendit", "Xendit"),
            Map.entry("paymob", "Paymob"),
            Map.entry("myfatoorah", "MyFatoorah"),
            Map.entry("mollie", "Mollie"),
            Map.entry("square", "Square"),
            Map.entry("mercadopago", "Mercado Pago")
    );

    public BillingGatewayRegistry(StripeGateway stripeGateway,
                                  SubscriptionRepository subscriptionRepository,
                                  PaymentTransactionRepository paymentTransactionRepository,
                                  WebhookIdempotencyService idempotencyService,
                                  PaymentGatewayConfigRepository gatewayConfigRepository) {
        this.gatewayConfigRepository = gatewayConfigRepository;

        gateways.put("stripe", stripeGateway);

        GATEWAY_LABELS.forEach((key, name) -> {
            if (!gateways.containsKey(key)) {
                gateways.put(key, new GenericGateway(key, name, subscriptionRepository, paymentTransactionRepository, idempotencyService));
            }
        });
    }

    public BillingGatewayInterface get(String key) {
        if (key == null) {
            return null;
        }
        return gateways.get(key.toLowerCase());
    }

    public Map<String, BillingGatewayInterface> all() {
        return gateways;
    }

    public List<Map<String, Object>> listForFrontend() {
        List<Map<String, Object>> list = new ArrayList<>();
        GATEWAY_LABELS.forEach((key, name) -> {
            BillingGatewayInterface gateway = gateways.get(key);
            boolean isConfigured = true;
            try {
                PaymentGatewayConfig config = gatewayConfigRepository.findByGateway(key).orElse(null);
                if (config != null) {
                    isConfigured = Boolean.TRUE.equals(config.getEnabled()) && config.hasActiveCredentials();
                }
            } catch (Exception ignored) {}

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("name", gateway != null ? gateway.name() : name);
            item.put("configured", isConfigured);
            list.add(item);
        });
        return list;
    }
}
