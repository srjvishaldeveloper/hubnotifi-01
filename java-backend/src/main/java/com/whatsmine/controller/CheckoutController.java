package com.whatsmine.controller;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.Plan;
import com.whatsmine.model.User;
import com.whatsmine.repository.PlanRepository;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.service.billing.BillingGatewayInterface;
import com.whatsmine.service.billing.BillingGatewayRegistry;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class CheckoutController {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final BillingGatewayRegistry gatewayRegistry;

    public CheckoutController(UserRepository userRepository,
                              PlanRepository planRepository,
                              BillingGatewayRegistry gatewayRegistry) {
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.gatewayRegistry = gatewayRegistry;
    }

    private User getAuthenticatedUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userRepository.findByEmail(principal.getName()).orElse(null);
    }

    @PostMapping("/checkout")
    public Object store(@RequestBody Map<String, Object> payload, Principal principal, HttpSession session) {
        User user = getAuthenticatedUser(principal);
        if (user == null) {
            return Inertia.redirect("/login");
        }

        Object planIdObj = payload.get("plan_id");
        String billingCycle = (String) payload.get("billing_cycle");
        String gatewayKey = (String) payload.get("gateway");

        if (planIdObj == null || billingCycle == null || gatewayKey == null) {
            Inertia.flashError(session, "Plan, billing cycle, and gateway are required.");
            return Inertia.redirect("/pricing");
        }

        Long planId = Long.valueOf(planIdObj.toString());
        Plan plan = planRepository.findById(planId).orElse(null);
        if (plan == null || !Boolean.TRUE.equals(plan.getEnabled())) {
            Inertia.flashError(session, "Selected plan is not available.");
            return Inertia.redirect("/pricing");
        }

        BillingGatewayInterface gateway = gatewayRegistry.get(gatewayKey);
        if (gateway == null || !gateway.isConfigured()) {
            Inertia.flashError(session, "That payment gateway is not configured.");
            return Inertia.redirect("/pricing");
        }

        Map<String, Object> result = gateway.createCheckout(user, plan, billingCycle);

        if (result.containsKey("error")) {
            Inertia.flashError(session, (String) result.get("error"));
            return Inertia.redirect("/pricing");
        }

        if (result.containsKey("url")) {
            return Inertia.location((String) result.get("url"));
        }

        if (result.containsKey("checkout")) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("checkout", result.get("checkout"));
            props.put("plan_name", plan.getName());
            props.put("pricing_url", "/pricing");
            return Inertia.render("client/Checkout/Sdk", props);
        }

        Inertia.flashError(session, "Checkout could not be started.");
        return Inertia.redirect("/pricing");
    }
}
