package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.Plan;
import com.whatsmine.repository.PlanRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/admin/plans")
public class AdminPlanController {

    private final PlanRepository planRepository;

    public AdminPlanController(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    private Map<String, Object> planToArray(Plan p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("name", p.getName());
        map.put("slug", p.getSlug());
        map.put("description", p.getDescription());
        map.put("currency_code", p.getCurrencyCode() != null ? p.getCurrencyCode() : "USD");
        map.put("monthly_price_cents", p.getMonthlyPriceCents() != null ? p.getMonthlyPriceCents() : p.getPriceCents());
        map.put("yearly_price_cents", p.getYearlyPriceCents());
        map.put("trial_days", p.getTrialDays() != null ? p.getTrialDays() : 0);
        map.put("stripe_monthly_id", p.getStripeMonthlyId());
        map.put("stripe_yearly_id", p.getStripeYearlyId());
        map.put("features", p.getFeatures() != null ? p.getFeatures() : Map.of());
        Map<String, Object> limits = p.getLimits();
        if (limits == null) {
            limits = new LinkedHashMap<>();
            limits.put("users", null);
            limits.put("storage", null);
        }
        map.put("limits", limits);
        map.put("enabled", Boolean.TRUE.equals(p.getEnabled()));
        map.put("featured", Boolean.TRUE.equals(p.getFeatured()));
        map.put("popular", Boolean.TRUE.equals(p.getPopular()));
        map.put("sort_order", p.getSortOrder() != null ? p.getSortOrder() : 0);
        map.put("white_label_enabled", Boolean.TRUE.equals(p.getWhiteLabelEnabled()));
        return map;
    }

    @GetMapping
    public Object index() {
        List<Plan> plans = planRepository.findAllByOrderBySortOrderAsc();
        List<Map<String, Object>> planList = new ArrayList<>();
        for (Plan p : plans) {
            planList.add(planToArray(p));
        }

        List<Map<String, String>> currencies = List.of(
                Map.of("code", "USD", "symbol", "$"),
                Map.of("code", "EUR", "symbol", "€"),
                Map.of("code", "GBP", "symbol", "£")
        );

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("plans", planList);
        props.put("currencies", currencies);
        props.put("defaultCurrency", "USD");

        return Inertia.render("Admin/Plans/Index", props);
    }

    @PostMapping
    public Object store(@RequestBody Map<String, Object> payload, HttpSession session) {
        String name = (String) payload.get("name");
        String slug = (String) payload.get("slug");
        if (slug == null || slug.isBlank()) {
            slug = name != null ? name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "") : "plan-" + System.currentTimeMillis();
        }

        Plan plan = new Plan();
        plan.setName(name);
        plan.setSlug(slug);
        plan.setDescription((String) payload.get("description"));
        plan.setCurrencyCode(payload.containsKey("currency_code") ? ((String) payload.get("currency_code")).toUpperCase() : "USD");

        Long monthlyCents = payload.get("monthly_price_cents") != null ? Long.valueOf(payload.get("monthly_price_cents").toString()) : 0L;
        plan.setMonthlyPriceCents(monthlyCents);
        plan.setPriceCents(monthlyCents);
        if (payload.get("yearly_price_cents") != null) {
            plan.setYearlyPriceCents(Long.valueOf(payload.get("yearly_price_cents").toString()));
        }
        if (payload.get("trial_days") != null) {
            plan.setTrialDays(Integer.valueOf(payload.get("trial_days").toString()));
        }
        plan.setStripeMonthlyId((String) payload.get("stripe_monthly_id"));
        plan.setStripeYearlyId((String) payload.get("stripe_yearly_id"));

        if (payload.get("features") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> feat = (Map<String, Object>) payload.get("features");
            plan.setFeatures(feat);
        }
        if (payload.get("limits") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> lim = (Map<String, Object>) payload.get("limits");
            plan.setLimits(lim);
        }

        plan.setEnabled(payload.get("enabled") == null || Boolean.TRUE.equals(payload.get("enabled")));
        plan.setFeatured(Boolean.TRUE.equals(payload.get("featured")));
        plan.setPopular(Boolean.TRUE.equals(payload.get("popular")));
        plan.setWhiteLabelEnabled(Boolean.TRUE.equals(payload.get("white_label_enabled")));

        int maxOrder = planRepository.findMaxSortOrder();
        plan.setSortOrder(maxOrder + 1);

        planRepository.save(plan);

        Inertia.flashSuccess(session, "Plan created successfully.");
        return Inertia.redirect("/admin/plans");
    }

    @GetMapping("/{id}/edit")
    public Object edit(@PathVariable Long id) {
        Plan plan = planRepository.findById(id).orElse(null);
        if (plan == null) {
            return Inertia.redirect("/admin/plans");
        }

        List<Map<String, String>> currencies = List.of(
                Map.of("code", "USD", "symbol", "$"),
                Map.of("code", "EUR", "symbol", "€"),
                Map.of("code", "GBP", "symbol", "£")
        );

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("plan", planToArray(plan));
        props.put("currencies", currencies);
        props.put("defaultCurrency", "USD");

        return Inertia.render("Admin/Plans/Edit", props);
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @RequestBody Map<String, Object> payload, HttpSession session) {
        Plan plan = planRepository.findById(id).orElse(null);
        if (plan == null) {
            return Inertia.redirect("/admin/plans");
        }

        if (payload.get("name") != null) {
            plan.setName((String) payload.get("name"));
        }
        if (payload.get("slug") != null) {
            plan.setSlug((String) payload.get("slug"));
        }
        if (payload.containsKey("description")) {
            plan.setDescription((String) payload.get("description"));
        }
        if (payload.get("currency_code") != null) {
            plan.setCurrencyCode(((String) payload.get("currency_code")).toUpperCase());
        }
        if (payload.get("monthly_price_cents") != null) {
            Long monthly = Long.valueOf(payload.get("monthly_price_cents").toString());
            plan.setMonthlyPriceCents(monthly);
            plan.setPriceCents(monthly);
        }
        if (payload.containsKey("yearly_price_cents")) {
            plan.setYearlyPriceCents(payload.get("yearly_price_cents") != null ? Long.valueOf(payload.get("yearly_price_cents").toString()) : null);
        }
        if (payload.containsKey("trial_days")) {
            plan.setTrialDays(payload.get("trial_days") != null ? Integer.valueOf(payload.get("trial_days").toString()) : 0);
        }
        if (payload.containsKey("stripe_monthly_id")) {
            plan.setStripeMonthlyId((String) payload.get("stripe_monthly_id"));
        }
        if (payload.containsKey("stripe_yearly_id")) {
            plan.setStripeYearlyId((String) payload.get("stripe_yearly_id"));
        }
        if (payload.get("features") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> feat = (Map<String, Object>) payload.get("features");
            plan.setFeatures(feat);
        }
        if (payload.get("limits") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> lim = (Map<String, Object>) payload.get("limits");
            plan.setLimits(lim);
        }

        if (payload.containsKey("enabled")) {
            plan.setEnabled(Boolean.TRUE.equals(payload.get("enabled")));
        }
        if (payload.containsKey("featured")) {
            plan.setFeatured(Boolean.TRUE.equals(payload.get("featured")));
        }
        if (payload.containsKey("popular")) {
            plan.setPopular(Boolean.TRUE.equals(payload.get("popular")));
        }
        if (payload.containsKey("white_label_enabled")) {
            plan.setWhiteLabelEnabled(Boolean.TRUE.equals(payload.get("white_label_enabled")));
        }

        planRepository.save(plan);

        Inertia.flashSuccess(session, "Plan updated successfully.");
        return Inertia.redirect("/admin/plans");
    }

    @DeleteMapping("/{id}")
    public Object destroy(@PathVariable Long id, HttpSession session) {
        planRepository.deleteById(id);
        Inertia.flashSuccess(session, "Plan deleted successfully.");
        return Inertia.redirect("/admin/plans");
    }

    @PostMapping("/{id}/duplicate")
    public Object duplicate(@PathVariable Long id, HttpSession session) {
        Plan plan = planRepository.findById(id).orElse(null);
        if (plan == null) {
            return Inertia.redirect("/admin/plans");
        }

        Plan copy = new Plan();
        copy.setName(plan.getName() + " (Copy)");
        copy.setSlug(plan.getSlug() + "-copy-" + (1000 + new Random().nextInt(9000)));
        copy.setDescription(plan.getDescription());
        copy.setCurrencyCode(plan.getCurrencyCode());
        copy.setPriceCents(plan.getPriceCents());
        copy.setMonthlyPriceCents(plan.getMonthlyPriceCents());
        copy.setYearlyPriceCents(plan.getYearlyPriceCents());
        copy.setTrialDays(plan.getTrialDays());
        copy.setStripeMonthlyId(plan.getStripeMonthlyId());
        copy.setStripeYearlyId(plan.getStripeYearlyId());
        copy.setFeatures(plan.getFeatures());
        copy.setLimits(plan.getLimits());
        copy.setEnabled(plan.getEnabled());
        copy.setFeatured(plan.getFeatured());
        copy.setPopular(plan.getPopular());
        copy.setWhiteLabelEnabled(plan.getWhiteLabelEnabled());

        int maxOrder = planRepository.findMaxSortOrder();
        copy.setSortOrder(maxOrder + 1);

        planRepository.save(copy);

        Inertia.flashSuccess(session, "Plan duplicated successfully.");
        return Inertia.redirect("/admin/plans");
    }

    @PostMapping("/reorder")
    public Object reorder(@RequestBody Map<String, Object> payload, HttpSession session) {
        if (payload.get("order") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> order = (List<Object>) payload.get("order");
            for (int i = 0; i < order.size(); i++) {
                Long id = Long.valueOf(order.get(i).toString());
                final int pos = i;
                planRepository.findById(id).ifPresent(p -> {
                    p.setSortOrder(pos);
                    planRepository.save(p);
                });
            }
        }
        Inertia.flashSuccess(session, "Plans reordered.");
        return Inertia.redirect("/admin/plans");
    }
}
