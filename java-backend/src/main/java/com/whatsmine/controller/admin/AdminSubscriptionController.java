package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.Plan;
import com.whatsmine.model.Subscription;
import com.whatsmine.model.User;
import com.whatsmine.repository.PlanRepository;
import com.whatsmine.repository.SubscriptionRepository;
import com.whatsmine.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/subscriptions")
public class AdminSubscriptionController {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;

    public AdminSubscriptionController(SubscriptionRepository subscriptionRepository,
                                       PlanRepository planRepository,
                                       UserRepository userRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public Object index(@RequestParam(value = "page", defaultValue = "1") int page,
                        @RequestParam(value = "status", required = false) String status,
                        @RequestParam(value = "gateway", required = false) String gateway) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page - 1), 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Subscription> pageResult;

        if (status != null && !status.isBlank() && gateway != null && !gateway.isBlank()) {
            pageResult = subscriptionRepository.findByStatusAndGateway(status, gateway, pageRequest);
        } else if (status != null && !status.isBlank()) {
            pageResult = subscriptionRepository.findByStatus(status, pageRequest);
        } else if (gateway != null && !gateway.isBlank()) {
            pageResult = subscriptionRepository.findByGateway(gateway, pageRequest);
        } else {
            pageResult = subscriptionRepository.findAll(pageRequest);
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Subscription s : pageResult.getContent()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());

            User user = userRepository.findById(s.getUserId()).orElse(null);
            item.put("user", user != null ? Map.of("id", user.getId(), "name", user.getName(), "email", user.getEmail()) : null);

            Plan plan = planRepository.findById(s.getPlanId()).orElse(null);
            item.put("plan", plan != null ? Map.of("id", plan.getId(), "name", plan.getName(), "slug", plan.getSlug()) : null);

            item.put("status", s.getStatus());
            item.put("gateway", s.getGateway());
            item.put("gateway_subscription_id", s.getGatewaySubscriptionId());
            item.put("starts_at", s.getStartsAt() != null ? s.getStartsAt().format(DateTimeFormatter.ISO_DATE_TIME) : null);
            item.put("ends_at", s.getEndsAt() != null ? s.getEndsAt().format(DateTimeFormatter.ISO_DATE_TIME) : null);
            item.put("renews_at", s.getRenewsAt() != null ? s.getRenewsAt().format(DateTimeFormatter.ISO_DATE_TIME) : null);
            item.put("created_at", s.getCreatedAt() != null ? s.getCreatedAt().format(DateTimeFormatter.ISO_DATE_TIME) : null);

            list.add(item);
        }

        Map<String, Object> paginated = new LinkedHashMap<>();
        paginated.put("data", list);
        paginated.put("current_page", pageResult.getNumber() + 1);
        paginated.put("last_page", pageResult.getTotalPages());
        paginated.put("total", pageResult.getTotalElements());

        List<Plan> enabledPlans = planRepository.findByEnabledTrueOrderBySortOrderAsc();
        List<Map<String, Object>> planOptions = new ArrayList<>();
        for (Plan p : enabledPlans) {
            planOptions.add(Map.of("id", p.getId(), "name", p.getName()));
        }

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("status", status);
        filters.put("gateway", gateway);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("subscriptions", paginated);
        props.put("filters", filters);
        props.put("plans", planOptions);

        return Inertia.render("Admin/Subscriptions/Index", props);
    }

    @GetMapping("/user-search")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> userSearch(@RequestParam(value = "q", defaultValue = "") String q) {
        String query = q.trim();
        if (query.length() < 2) {
            return ResponseEntity.ok(Map.of("users", List.of()));
        }

        List<User> users = userRepository.findAll().stream()
                .filter(u -> (u.getName() != null && u.getName().toLowerCase().contains(query.toLowerCase()))
                        || (u.getEmail() != null && u.getEmail().toLowerCase().contains(query.toLowerCase())))
                .limit(8)
                .toList();

        List<Map<String, Object>> userList = new ArrayList<>();
        for (User u : users) {
            userList.add(Map.of("id", u.getId(), "name", u.getName(), "email", u.getEmail()));
        }

        return ResponseEntity.ok(Map.of("users", userList));
    }

    @PostMapping
    public Object store(@RequestBody Map<String, Object> payload, HttpSession session) {
        Long userId = Long.valueOf(payload.get("user_id").toString());
        Long planId = Long.valueOf(payload.get("plan_id").toString());
        String billingCycle = (String) payload.get("billing_cycle");
        String status = (String) payload.get("status");

        List<Subscription> existing = subscriptionRepository.findByUserIdAndStatusIn(userId, List.of("active", "trialing"));
        for (Subscription sub : existing) {
            sub.setStatus("canceled");
            sub.setEndsAt(LocalDateTime.now());
            subscriptionRepository.save(sub);
        }

        Subscription sub = new Subscription();
        sub.setUserId(userId);
        sub.setPlanId(planId);
        sub.setBillingCycle(billingCycle);
        sub.setStatus(status);
        sub.setGateway("manual");
        sub.setStartsAt(LocalDateTime.now());

        subscriptionRepository.save(sub);

        Inertia.flashSuccess(session, "Subscription created.");
        return Inertia.redirect("/admin/subscriptions");
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response) throws Exception {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"subscriptions_" + System.currentTimeMillis() + ".csv\"");

        PrintWriter writer = response.getWriter();
        writer.println("ID,User,Email,Plan,Status,Gateway,Starts At,Ends At,Created At");

        List<Subscription> subscriptions = subscriptionRepository.findAll();
        for (Subscription s : subscriptions) {
            User user = userRepository.findById(s.getUserId()).orElse(null);
            Plan plan = planRepository.findById(s.getPlanId()).orElse(null);

            writer.println(String.format("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"",
                    s.getId(),
                    user != null ? user.getName() : "",
                    user != null ? user.getEmail() : "",
                    plan != null ? plan.getName() : "",
                    s.getStatus(),
                    s.getGateway(),
                    s.getStartsAt() != null ? s.getStartsAt().toLocalDate().toString() : "",
                    s.getEndsAt() != null ? s.getEndsAt().toLocalDate().toString() : "",
                    s.getCreatedAt() != null ? s.getCreatedAt().toLocalDate().toString() : ""
            ));
        }
        writer.flush();
    }
}
