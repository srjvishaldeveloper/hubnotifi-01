package com.whatsmine.controller;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.Plan;
import com.whatsmine.model.User;
import com.whatsmine.repository.PlanRepository;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.service.billing.BillingGatewayRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class PricingController {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final BillingGatewayRegistry gatewayRegistry;

    public PricingController(UserRepository userRepository,
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

    @GetMapping("/pricing")
    public Object index(Principal principal) {
        User user = getAuthenticatedUser(principal);
        List<Plan> plans = planRepository.findByEnabledTrueOrderBySortOrderAsc();

        List<Map<String, Object>> planList = new ArrayList<>();
        for (Plan plan : plans) {
            Long monthlyCents = plan.getMonthlyPriceCents() != null ? plan.getMonthlyPriceCents() : plan.getPriceCents();
            Long yearlyCents = plan.getYearlyPriceCents();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", plan.getId());
            item.put("name", plan.getName());
            item.put("slug", plan.getSlug());
            item.put("description", plan.getDescription());
            item.put("monthly_price_display", monthlyCents != null ? "$" + String.format("%.2f", monthlyCents / 100.0) : null);
            item.put("yearly_price_display", yearlyCents != null ? "$" + String.format("%.2f", yearlyCents / 100.0) : null);
            item.put("monthly_price_cents", monthlyCents);
            item.put("yearly_price_cents", yearlyCents);
            item.put("features", plan.getFeatures() != null ? plan.getFeatures() : Map.of());
            item.put("limits", plan.getLimits() != null ? plan.getLimits() : Map.of());
            item.put("white_label_enabled", Boolean.TRUE.equals(plan.getWhiteLabelEnabled()));
            item.put("popular", Boolean.TRUE.equals(plan.getPopular()));
            item.put("featured", Boolean.TRUE.equals(plan.getFeatured()));
            item.put("is_free", (monthlyCents == null || monthlyCents == 0) && (yearlyCents == null || yearlyCents == 0));
            item.put("trial_days", plan.getTrialDays());
            planList.add(item);
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("plans", planList);
        props.put("gateways", gatewayRegistry.listForFrontend());
        props.put("is_authenticated", user != null);
        props.put("register_url", "/register");
        props.put("checkout_url", "/checkout");
        props.put("flash", Map.of());

        return Inertia.render("client/Pricing", props);
    }
}
