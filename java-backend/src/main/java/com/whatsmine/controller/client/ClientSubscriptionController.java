package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.ClientSubscription;
import com.whatsmine.model.Coupon;
import com.whatsmine.model.PaymentTransaction;
import com.whatsmine.model.Plan;
import com.whatsmine.model.Subscription;
import com.whatsmine.model.User;
import com.whatsmine.repository.ClientSubscriptionRepository;
import com.whatsmine.repository.CouponRepository;
import com.whatsmine.repository.PaymentTransactionRepository;
import com.whatsmine.repository.PlanRepository;
import com.whatsmine.repository.SubscriptionRepository;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.service.billing.BillingGatewayInterface;
import com.whatsmine.service.billing.BillingGatewayRegistry;
import com.whatsmine.service.billing.InvoiceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class ClientSubscriptionController {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ClientSubscriptionRepository clientSubscriptionRepository;
    private final PlanRepository planRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CouponRepository couponRepository;
    private final BillingGatewayRegistry gatewayRegistry;
    private final InvoiceService invoiceService;

    public ClientSubscriptionController(UserRepository userRepository,
                                        SubscriptionRepository subscriptionRepository,
                                        ClientSubscriptionRepository clientSubscriptionRepository,
                                        PlanRepository planRepository,
                                        PaymentTransactionRepository paymentTransactionRepository,
                                        CouponRepository couponRepository,
                                        BillingGatewayRegistry gatewayRegistry,
                                        InvoiceService invoiceService) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.clientSubscriptionRepository = clientSubscriptionRepository;
        this.planRepository = planRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.couponRepository = couponRepository;
        this.gatewayRegistry = gatewayRegistry;
        this.invoiceService = invoiceService;
    }

    private User getAuthenticatedUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userRepository.findByEmail(principal.getName()).orElse(null);
    }

    @GetMapping("/subscription")
    public Object show(HttpServletRequest request, Principal principal) {
        User user = getAuthenticatedUser(principal);
        if (user == null) {
            return Inertia.redirect("/login");
        }

        Map<String, Object> subscriptionDto = null;
        boolean canCancel = false;
        boolean canUpgrade = true;

        List<Subscription> subs = subscriptionRepository.findByUserIdAndStatusIn(user.getId(), List.of("active", "trialing"));
        Subscription sub = subs.isEmpty() ? null : subs.get(0);
        ClientSubscription clientSub = null;
        if (sub == null && user.getClientId() != null) {
            clientSub = clientSubscriptionRepository.findFirstByClientIdAndStatusOrderByCreatedAtDesc(user.getClientId(), "active").orElse(null);
        }

        if (sub != null) {
            canCancel = "active".equalsIgnoreCase(sub.getStatus()) || "trialing".equalsIgnoreCase(sub.getStatus());
            Plan plan = planRepository.findById(sub.getPlanId()).orElse(null);

            Map<String, Object> planDto = new LinkedHashMap<>();
            if (plan != null) {
                planDto.put("id", plan.getId());
                planDto.put("name", plan.getName());
                planDto.put("slug", plan.getSlug());
                planDto.put("trial_days", plan.getTrialDays() != null ? plan.getTrialDays() : 0);
            }

            subscriptionDto = new LinkedHashMap<>();
            subscriptionDto.put("plan", planDto);
            subscriptionDto.put("billing_cycle", sub.getBillingCycle());
            subscriptionDto.put("status", sub.getStatus());
            subscriptionDto.put("renews_at", sub.getRenewsAt() != null ? sub.getRenewsAt().format(DateTimeFormatter.ISO_DATE_TIME) : null);
            subscriptionDto.put("ends_at", sub.getEndsAt() != null ? sub.getEndsAt().format(DateTimeFormatter.ISO_DATE_TIME) : null);
            subscriptionDto.put("trial_ends_at", sub.getTrialEndsAt() != null ? sub.getTrialEndsAt().format(DateTimeFormatter.ISO_DATE_TIME) : null);
            subscriptionDto.put("gateway", sub.getGateway());
            subscriptionDto.put("managed_by_admin", false);
        } else if (clientSub != null) {
            canCancel = false;
            canUpgrade = false;
            Plan plan = planRepository.findById(clientSub.getPlanId()).orElse(null);

            Map<String, Object> planDto = new LinkedHashMap<>();
            if (plan != null) {
                planDto.put("id", plan.getId());
                planDto.put("name", plan.getName());
                planDto.put("slug", plan.getSlug());
                planDto.put("trial_days", plan.getTrialDays() != null ? plan.getTrialDays() : 0);
            }

            subscriptionDto = new LinkedHashMap<>();
            subscriptionDto.put("plan", planDto);
            subscriptionDto.put("billing_cycle", clientSub.getBillingCycle());
            subscriptionDto.put("status", clientSub.getStatus());
            subscriptionDto.put("renews_at", null);
            subscriptionDto.put("ends_at", clientSub.getEndsAt() != null ? clientSub.getEndsAt().format(DateTimeFormatter.ISO_DATE_TIME) : null);
            subscriptionDto.put("trial_ends_at", null);
            subscriptionDto.put("gateway", null);
            subscriptionDto.put("managed_by_admin", true);
        }

        List<Plan> plans = planRepository.findByEnabledTrueOrderBySortOrderAsc();
        List<Map<String, Object>> planList = new ArrayList<>();
        for (Plan p : plans) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("name", p.getName());
            item.put("slug", p.getSlug());
            item.put("monthly_price", p.getMonthlyPriceCents());
            item.put("annual_price", p.getYearlyPriceCents());
            planList.add(item);
        }

        List<PaymentTransaction> txs = paymentTransactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        List<Map<String, Object>> txList = new ArrayList<>();
        for (int i = 0; i < Math.min(txs.size(), 10); i++) {
            PaymentTransaction t = txs.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", t.getId());
            item.put("amount_cents", t.getAmountCents());
            item.put("currency_code", t.getCurrencyCode());
            item.put("status", t.getStatus());
            item.put("refunded_at", t.getRefundedAt() != null ? t.getRefundedAt().format(DateTimeFormatter.ISO_DATE_TIME) : null);
            item.put("refunded_cents", t.getRefundedCents());
            item.put("created_at", t.getCreatedAt() != null ? t.getCreatedAt().format(DateTimeFormatter.ISO_DATE_TIME) : null);
            item.put("invoice_url", t.getInvoicePath() != null ? "/subscription/invoice/" + t.getId() : null);
            txList.add(item);
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("subscription", subscriptionDto);
        props.put("canCancel", canCancel);
        props.put("canUpgrade", canUpgrade);
        props.put("plans", planList);
        props.put("transactions", txList);

        return Inertia.render("client/Subscription/Show", props);
    }

    @PostMapping("/subscription/change-plan")
    public Object changePlan(@RequestBody Map<String, Object> payload, Principal principal, HttpSession session) {
        User user = getAuthenticatedUser(principal);
        if (user == null) {
            return Inertia.redirect("/login");
        }

        Object planIdObj = payload.get("plan_id");
        String billingCycle = (String) payload.get("billing_cycle");
        if (planIdObj == null || billingCycle == null) {
            Inertia.flashError(session, "Plan and billing cycle are required.");
            return Inertia.redirect("/subscription");
        }

        Long planId = Long.valueOf(planIdObj.toString());
        List<Subscription> subs = subscriptionRepository.findByUserIdAndStatusIn(user.getId(), List.of("active", "trialing"));
        if (subs.isEmpty()) {
            Inertia.flashError(session, "No active subscription to change.");
            return Inertia.redirect("/subscription");
        }

        Subscription sub = subs.get(0);
        Plan newPlan = planRepository.findById(planId).orElse(null);
        if (newPlan == null || !Boolean.TRUE.equals(newPlan.getEnabled())) {
            Inertia.flashError(session, "Selected plan is not available.");
            return Inertia.redirect("/subscription");
        }

        BillingGatewayInterface gateway = gatewayRegistry.get(sub.getGateway() != null ? sub.getGateway() : "stripe");
        if (gateway == null) {
            Inertia.flashError(session, "Billing gateway not configured.");
            return Inertia.redirect("/subscription");
        }

        Map<String, Object> result = gateway.changePlan(sub, newPlan, billingCycle);
        if (!Boolean.TRUE.equals(result.get("ok"))) {
            Inertia.flashError(session, (String) result.getOrDefault("error", "Could not change plan."));
            return Inertia.redirect("/subscription");
        }

        Inertia.flashSuccess(session, "Your plan has been updated.");
        return Inertia.redirect("/subscription");
    }

    @PostMapping("/coupon/check")
    public ResponseEntity<Map<String, Object>> couponCheck(@RequestBody Map<String, Object> payload) {
        String code = (String) payload.get("code");
        Object planIdObj = payload.get("plan_id");

        if (code == null || code.isBlank()) {
            return ResponseEntity.ok(Map.of("valid", false, "message", "Coupon code is required."));
        }

        Optional<Coupon> couponOpt = couponRepository.findByCode(code.trim());
        if (couponOpt.isEmpty() || !couponOpt.get().isValid()) {
            return ResponseEntity.ok(Map.of("valid", false, "message", "Invalid or expired coupon code."));
        }

        Coupon coupon = couponOpt.get();
        if (coupon.getAppliesToPlanIds() != null && !coupon.getAppliesToPlanIds().isEmpty() && planIdObj != null) {
            Long planId = Long.valueOf(planIdObj.toString());
            boolean matches = coupon.getAppliesToPlanIds().stream()
                    .anyMatch(p -> p != null && Long.valueOf(p.toString()).equals(planId));
            if (!matches) {
                return ResponseEntity.ok(Map.of("valid", false, "message", "This coupon does not apply to the selected plan."));
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("valid", true);
        res.put("kind", coupon.getKind());
        res.put("amount", coupon.getAmount());
        res.put("duration", coupon.getDuration());
        return ResponseEntity.ok(res);
    }

    @GetMapping("/subscription/invoice/{transactionId}")
    public ResponseEntity<?> invoiceDownload(@PathVariable Long transactionId, Principal principal) {
        User user = getAuthenticatedUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PaymentTransaction transaction = paymentTransactionRepository.findById(transactionId).orElse(null);
        if (transaction == null || !user.getId().equals(transaction.getUserId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        byte[] pdf = invoiceService.generate(transaction);
        if (pdf == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "invoice-" + transaction.getId() + ".pdf");
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @DeleteMapping("/subscription")
    public Object destroy(Principal principal, HttpSession session) {
        User user = getAuthenticatedUser(principal);
        if (user == null) {
            return Inertia.redirect("/login");
        }

        List<Subscription> subs = subscriptionRepository.findByUserIdAndStatusIn(user.getId(), List.of("active", "trialing"));
        if (subs.isEmpty()) {
            Inertia.flashError(session, "No active subscription to cancel.");
            return Inertia.redirect("/subscription");
        }

        Subscription sub = subs.get(0);
        BillingGatewayInterface gateway = gatewayRegistry.get(sub.getGateway() != null ? sub.getGateway() : "stripe");
        if (gateway == null || !gateway.cancel(sub)) {
            Inertia.flashError(session, "Could not cancel subscription. Please contact support.");
            return Inertia.redirect("/subscription");
        }

        Inertia.flashSuccess(session, "Your subscription has been cancelled.");
        return Inertia.redirect("/subscription");
    }
}
