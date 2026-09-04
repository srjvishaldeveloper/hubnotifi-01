package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.PaymentTransaction;
import com.whatsmine.model.Plan;
import com.whatsmine.model.Subscription;
import com.whatsmine.model.User;
import com.whatsmine.repository.PaymentTransactionRepository;
import com.whatsmine.repository.PlanRepository;
import com.whatsmine.repository.SubscriptionRepository;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.service.billing.BillingGatewayRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ClientBillingController {

    private final UserRepository userRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final BillingGatewayRegistry gatewayRegistry;

    public ClientBillingController(UserRepository userRepository,
                                  PaymentTransactionRepository paymentTransactionRepository,
                                  SubscriptionRepository subscriptionRepository,
                                  PlanRepository planRepository,
                                  BillingGatewayRegistry gatewayRegistry) {
        this.userRepository = userRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.gatewayRegistry = gatewayRegistry;
    }

    private User getAuthenticatedUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userRepository.findByEmail(principal.getName()).orElse(null);
    }

    @GetMapping("/billing")
    public Object index(HttpServletRequest request,
                        Principal principal,
                        @RequestParam(value = "page", defaultValue = "1") int page) {
        User user = getAuthenticatedUser(principal);
        if (user == null) {
            return Inertia.redirect("/login");
        }

        String sessionId = request.getParameter("session_id");
        if (sessionId != null && !sessionId.isBlank()) {
            gatewayRegistry.get("stripe").fulfillCheckoutSession(sessionId);
        }

        PageRequest pageRequest = PageRequest.of(Math.max(0, page - 1), 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PaymentTransaction> pageResult = paymentTransactionRepository.findByUserId(user.getId(), pageRequest);

        List<Map<String, Object>> data = new ArrayList<>();
        for (PaymentTransaction t : pageResult.getContent()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", t.getId());
            item.put("amount_cents", t.getAmountCents());
            item.put("currency_code", t.getCurrencyCode());
            item.put("status", t.getStatus());
            item.put("gateway", t.getGateway());
            item.put("created_at", t.getCreatedAt() != null ? t.getCreatedAt().format(DateTimeFormatter.ISO_DATE_TIME) : null);

            Map<String, Object> planData = null;
            if (t.getSubscriptionId() != null) {
                Subscription sub = subscriptionRepository.findById(t.getSubscriptionId()).orElse(null);
                if (sub != null && sub.getPlanId() != null) {
                    Plan plan = planRepository.findById(sub.getPlanId()).orElse(null);
                    if (plan != null) {
                        planData = Map.of("name", plan.getName());
                    }
                }
            }
            item.put("plan", planData);
            data.add(item);
        }

        Map<String, Object> paginated = new LinkedHashMap<>();
        paginated.put("data", data);
        paginated.put("current_page", pageResult.getNumber() + 1);
        paginated.put("last_page", pageResult.getTotalPages());
        paginated.put("total", pageResult.getTotalElements());

        return Inertia.render("client/Billing/Index", Map.of("transactions", paginated));
    }
}
