package com.whatsmine.service.billing;

import com.whatsmine.model.PaymentTransaction;
import com.whatsmine.model.Plan;
import com.whatsmine.model.Subscription;
import com.whatsmine.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface BillingGatewayInterface {

    String name();

    boolean isConfigured();

    Map<String, Object> createCheckout(User user, Plan plan, String billingCycle);

    ResponseEntity<?> handleWebhook(HttpServletRequest request);

    boolean cancel(Subscription subscription);

    boolean sync(Subscription subscription);

    Map<String, Object> changePlan(Subscription subscription, Plan newPlan, String billingCycle);

    Map<String, Object> refund(PaymentTransaction transaction, Integer amountCents);

    Map<String, Object> fulfillCheckoutSession(String sessionId);
}
