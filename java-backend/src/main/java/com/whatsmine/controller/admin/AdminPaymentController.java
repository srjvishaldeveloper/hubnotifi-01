package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.PaymentTransaction;
import com.whatsmine.model.Plan;
import com.whatsmine.model.Subscription;
import com.whatsmine.model.User;
import com.whatsmine.repository.PaymentTransactionRepository;
import com.whatsmine.repository.PlanRepository;
import com.whatsmine.repository.SubscriptionRepository;
import com.whatsmine.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/payments")
public class AdminPaymentController {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    public AdminPaymentController(PaymentTransactionRepository paymentTransactionRepository,
                                  UserRepository userRepository,
                                  SubscriptionRepository subscriptionRepository,
                                  PlanRepository planRepository) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
    }

    @GetMapping
    public Object index(@RequestParam(value = "page", defaultValue = "1") int page,
                        @RequestParam(value = "status", required = false) String status,
                        @RequestParam(value = "gateway", required = false) String gateway) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page - 1), 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PaymentTransaction> pageResult;

        if (status != null && !status.isBlank() && gateway != null && !gateway.isBlank()) {
            pageResult = paymentTransactionRepository.findByStatusAndGateway(status, gateway, pageRequest);
        } else if (status != null && !status.isBlank()) {
            pageResult = paymentTransactionRepository.findByStatus(status, pageRequest);
        } else if (gateway != null && !gateway.isBlank()) {
            pageResult = paymentTransactionRepository.findByGateway(gateway, pageRequest);
        } else {
            pageResult = paymentTransactionRepository.findAll(pageRequest);
        }

        List<Map<String, Object>> payments = new ArrayList<>();
        for (PaymentTransaction t : pageResult.getContent()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", t.getId());

            User user = userRepository.findById(t.getUserId()).orElse(null);
            item.put("user", user != null ? Map.of("id", user.getId(), "name", user.getName(), "email", user.getEmail()) : null);

            item.put("gateway", t.getGateway());
            item.put("amount_cents", t.getAmountCents());
            item.put("currency_code", t.getCurrencyCode());
            item.put("status", t.getStatus());
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
            payments.add(item);
        }

        Map<String, Object> paginated = new LinkedHashMap<>();
        paginated.put("data", payments);
        paginated.put("current_page", pageResult.getNumber() + 1);
        paginated.put("last_page", pageResult.getTotalPages());
        paginated.put("total", pageResult.getTotalElements());

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("status", status);
        filters.put("gateway", gateway);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("payments", paginated);
        props.put("filters", filters);

        return Inertia.render("Admin/Payments/Index", props);
    }
}
