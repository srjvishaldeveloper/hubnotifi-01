package com.whatsmine.service.billing;

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
                                  RazorpayGateway razorpayGateway,
                                  SubscriptionRepository subscriptionRepository,
                                  PaymentTransactionRepository paymentTransactionRepository,
                                  PaymentGatewayConfigRepository gatewayConfigRepository) {
        gateways.put("stripe", stripeGateway);
        gateways.put("razorpay", razorpayGateway);

        GATEWAY_LABELS.forEach((key, name) -> {
            if (!gateways.containsKey(key)) {
                gateways.put(key, new GenericGateway(key, name, gatewayConfigRepository, subscriptionRepository, paymentTransactionRepository));
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
            boolean isConfigured = gateway != null && gateway.isConfigured();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("name", gateway != null ? gateway.name() : name);
            item.put("configured", isConfigured);
            list.add(item);
        });
        return list;
    }
}
