package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.model.Client;
import com.whatsmine.model.ClientSubscription;
import com.whatsmine.model.Plan;
import com.whatsmine.model.User;
import com.whatsmine.repository.ClientRepository;
import com.whatsmine.repository.ClientSubscriptionRepository;
import com.whatsmine.repository.PlanRepository;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.security.AdminUserDetails;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/clients")
public class AdminClientController {

    private static final String CLIENT_ROLE_ADMINISTRATOR = "administrator";

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final ClientSubscriptionRepository clientSubscriptionRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminClientController(ClientRepository clientRepository,
                                  UserRepository userRepository,
                                  PlanRepository planRepository,
                                  ClientSubscriptionRepository clientSubscriptionRepository,
                                  PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.clientSubscriptionRepository = clientSubscriptionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public InertiaResponse index(@RequestParam(required = false) String search,
                                  @RequestParam(defaultValue = "1") int page) {
        Specification<Client> spec = (root, query, cb) -> {
            if (search == null || search.isBlank()) return null;
            String like = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("email")), like)
            );
        };
        Page<Client> pageResult = clientRepository.findAll(spec, PageRequest.of(Math.max(0, page - 1), 20, Sort.by("name")));

        List<Map<String, Object>> rows = pageResult.getContent().stream().map(this::clientRow).toList();

        Map<String, Object> paginated = new LinkedHashMap<>();
        paginated.put("data", rows);
        paginated.put("current_page", pageResult.getNumber() + 1);
        paginated.put("last_page", pageResult.getTotalPages());
        paginated.put("total", pageResult.getTotalElements());

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("clients", paginated);
        props.put("plans", planRepository.findByEnabledTrueOrderBySortOrderAsc().stream().map(this::planSummary).toList());
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("search", search);
        props.put("filters", filters);

        return Inertia.render("Admin/Clients/Index", props);
    }

    private Map<String, Object> clientRow(Client c) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", c.getId());
        row.put("name", c.getName());
        row.put("email", c.getEmail());
        row.put("phone", c.getPhone());
        row.put("address", c.getAddress());
        row.put("status", c.getStatus());
        row.put("base_currency", c.getBaseCurrency());
        row.put("currency_symbol", c.getCurrencySymbol());
        row.put("currency_position", c.getCurrencyPosition());
        row.put("subscription", effectivePlanSummary(c.getId()));
        return row;
    }

    private Map<String, Object> effectivePlanSummary(Long clientId) {
        return clientSubscriptionRepository.findFirstByClientIdAndStatusOrderByCreatedAtDesc(clientId, "active")
                .flatMap(sub -> planRepository.findById(sub.getPlanId()))
                .map(plan -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", plan.getName());
                    return m;
                })
                .orElse(null);
    }

    private Map<String, Object> planSummary(Plan p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("slug", p.getSlug());
        m.put("currency_code", p.getCurrencyCode());
        m.put("monthly_price_cents", p.getMonthlyPriceCents());
        m.put("yearly_price_cents", p.getYearlyPriceCents());
        return m;
    }

    @PostMapping
    public Object store(@RequestBody ClientRequest request, HttpSession session) {
        Client client = new Client();
        applyRequest(client, request);
        clientRepository.save(client);

        Inertia.flashSuccess(session, "Client created.");
        return Inertia.redirect("/admin/clients");
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @RequestBody ClientRequest request, HttpSession session) {
        Client client = clientRepository.findById(id).orElseThrow();
        applyRequest(client, request);
        clientRepository.save(client);

        Inertia.flashSuccess(session, "Client updated.");
        return Inertia.redirect("/admin/clients");
    }

    private void applyRequest(Client client, ClientRequest request) {
        client.setName(request.getName());
        client.setEmail(request.getEmail());
        client.setPhone(request.getPhone());
        client.setAddress(request.getAddress());
        client.setStatus(request.getStatus() != null ? request.getStatus() : "active");
        client.setBaseCurrency(request.getBaseCurrency() != null ? request.getBaseCurrency() : "USD");
        client.setCurrencySymbol(request.getCurrencySymbol() != null ? request.getCurrencySymbol() : "$");
        client.setCurrencyPosition(request.getCurrencyPosition() != null ? request.getCurrencyPosition() : "before");
    }

    @DeleteMapping("/{id}")
    public Object destroy(@PathVariable Long id, HttpSession session) {
        clientRepository.deleteById(id);
        Inertia.flashSuccess(session, "Client deleted.");
        return Inertia.redirect("/admin/clients");
    }

    @GetMapping("/{id}/users")
    public ResponseEntity<Map<String, Object>> users(@PathVariable("id") Long clientId) {
        Client client = clientRepository.findById(clientId).orElseThrow();
        List<Map<String, Object>> users = userRepository.findByClientId(clientId).stream()
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::userRow)
                .toList();

        Map<String, Object> clientMap = new LinkedHashMap<>();
        clientMap.put("id", client.getId());
        clientMap.put("name", client.getName());

        return ResponseEntity.ok(Map.of("users", users, "client", clientMap));
    }

    private Map<String, Object> userRow(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("client_role", u.getClientRole() != null ? u.getClientRole() : "staff");
        m.put("status", u.getStatus() != null ? u.getStatus() : "active");
        m.put("created_at", u.getCreatedAt() != null ? u.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
        return m;
    }

    @PostMapping("/{id}/users")
    public ResponseEntity<Map<String, Object>> storeUser(@PathVariable("id") Long clientId, @RequestBody StoreUserRequest request) {
        Client client = clientRepository.findById(clientId).orElseThrow();

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", "That email address is already in use."));
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("client");
        user.setClientId(client.getId());
        user.setClientRole(request.getClientRole() != null ? request.getClientRole() : "staff");
        user.setStatus(request.getStatus() != null ? request.getStatus() : "active");
        user = userRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("user", userRow(user)));
    }

    @PutMapping("/{id}/users/{userId}")
    public ResponseEntity<Map<String, Object>> updateUser(@PathVariable("id") Long clientId,
                                                           @PathVariable Long userId,
                                                           @RequestBody UpdateUserRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        if (!clientId.equals(user.getClientId())) {
            return ResponseEntity.notFound().build();
        }

        userRepository.findByEmail(request.getEmail()).ifPresent(existing -> {
            if (!existing.getId().equals(userId)) {
                throw new IllegalArgumentException("That email address is already in use.");
            }
        });

        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setClientRole(request.getClientRole() != null ? request.getClientRole() : "staff");
        user.setStatus(request.getStatus() != null ? request.getStatus() : "active");
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user = userRepository.save(user);

        return ResponseEntity.ok(Map.of("user", userRow(user)));
    }

    @DeleteMapping("/{id}/users/{userId}")
    public ResponseEntity<Object> destroyUser(@PathVariable("id") Long clientId, @PathVariable Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        if (!clientId.equals(user.getClientId())) {
            return ResponseEntity.notFound().build();
        }

        long adminCount = userRepository.findByClientId(clientId).stream()
                .filter(u -> CLIENT_ROLE_ADMINISTRATOR.equals(u.getClientRole()))
                .count();
        if (CLIENT_ROLE_ADMINISTRATOR.equals(user.getClientRole()) && adminCount <= 1) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", "Cannot delete the last client administrator."));
        }

        userRepository.delete(user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/assign-plan")
    public Object assignPlan(@PathVariable("id") Long clientId, @RequestBody AssignPlanRequest request, HttpSession session) {
        Client client = clientRepository.findById(clientId).orElseThrow();
        Plan plan = planRepository.findById(request.getPlanId()).orElseThrow();

        for (ClientSubscription existing : clientSubscriptionRepository.findByClientId(clientId)) {
            if ("active".equals(existing.getStatus())) {
                existing.setStatus("cancelled");
                existing.setEndsAt(LocalDateTime.now());
                clientSubscriptionRepository.save(existing);
            }
        }

        ClientSubscription sub = new ClientSubscription();
        sub.setClientId(client.getId());
        sub.setPlanId(plan.getId());
        sub.setBillingCycle(request.getBillingCycle());
        sub.setStartsAt(LocalDateTime.now());
        sub.setStatus("active");
        clientSubscriptionRepository.save(sub);

        Inertia.flashSuccess(session, "Plan assigned.");
        return Inertia.redirect("/admin/clients");
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(@RequestParam(required = false) String search) {
        Specification<Client> spec = (root, query, cb) -> {
            if (search == null || search.isBlank()) return null;
            String like = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("email")), like)
            );
        };
        List<Client> clients = clientRepository.findAll(spec, Sort.by("name"));

        StringBuilder csv = new StringBuilder("ID,Name,Email,Phone,Status,Plan,Created At\r\n");
        for (Client c : clients) {
            Map<String, Object> plan = effectivePlanSummary(c.getId());
            csv.append(csvField(c.getId())).append(',')
                    .append(csvField(c.getName())).append(',')
                    .append(csvField(c.getEmail())).append(',')
                    .append(csvField(c.getPhone())).append(',')
                    .append(csvField(c.getStatus())).append(',')
                    .append(csvField(plan != null ? plan.get("name") : "")).append(',')
                    .append(csvField(c.getCreatedAt() != null ? c.getCreatedAt().toLocalDate().toString() : ""))
                    .append("\r\n");
        }

        String filename = "clients_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(csv.toString());
    }

    private String csvField(Object value) {
        String s = value != null ? value.toString() : "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    @PostMapping("/{id}/impersonate")
    public Object impersonate(@PathVariable("id") Long clientId,
                               @AuthenticationPrincipal AdminUserDetails adminDetails,
                               HttpServletRequest httpRequest,
                               HttpSession session) {
        Client client = clientRepository.findById(clientId).orElseThrow();

        if (Boolean.TRUE.equals(session.getAttribute("impersonating"))) {
            Inertia.flashError(session, "Already impersonating.");
            return Inertia.redirect("/admin/clients");
        }

        User target = userRepository.findByClientId(clientId).stream()
                .filter(u -> "active".equalsIgnoreCase(u.getStatus()))
                .sorted(Comparator
                        .<User, Integer>comparing(u -> CLIENT_ROLE_ADMINISTRATOR.equals(u.getClientRole()) ? 0 : 1)
                        .thenComparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .findFirst()
                .orElse(null);

        if (target == null) {
            Inertia.flashError(session, "Client has no active users. Add a user first.");
            return Inertia.redirect("/admin/clients");
        }

        session.setAttribute("impersonator_admin_id", adminDetails.getId());
        session.setAttribute("impersonating", true);
        session.setAttribute("impersonated_client_id", client.getId());
        session.setAttribute("impersonated_client_name", client.getName());

        CustomUserDetails targetDetails = new CustomUserDetails(target);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(targetDetails, null, targetDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());

        return Inertia.redirect("/dashboard");
    }

    public static class ClientRequest {
        @NotBlank
        private String name;
        private String email;
        private String phone;
        private String address;
        private String status;
        private String baseCurrency;
        private String currencySymbol;
        private String currencyPosition;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getBaseCurrency() { return baseCurrency; }
        public void setBaseCurrency(String baseCurrency) { this.baseCurrency = baseCurrency; }
        public String getCurrencySymbol() { return currencySymbol; }
        public void setCurrencySymbol(String currencySymbol) { this.currencySymbol = currencySymbol; }
        public String getCurrencyPosition() { return currencyPosition; }
        public void setCurrencyPosition(String currencyPosition) { this.currencyPosition = currencyPosition; }
    }

    public static class StoreUserRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String email;
        @NotBlank
        private String password;
        private String clientRole;
        private String status;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getClientRole() { return clientRole; }
        public void setClientRole(String clientRole) { this.clientRole = clientRole; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class UpdateUserRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String email;
        private String password;
        private String clientRole;
        private String status;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getClientRole() { return clientRole; }
        public void setClientRole(String clientRole) { this.clientRole = clientRole; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class AssignPlanRequest {
        private Long planId;
        private String billingCycle;

        public Long getPlanId() { return planId; }
        public void setPlanId(Long planId) { this.planId = planId; }
        public String getBillingCycle() { return billingCycle; }
        public void setBillingCycle(String billingCycle) { this.billingCycle = billingCycle; }
    }
}
