package com.whatsmine.controller;

import com.whatsmine.dto.LoginRequest;
import com.whatsmine.dto.RegisterRequest;
import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.model.User;
import com.whatsmine.repository.PlanRepository;
import com.whatsmine.service.AuthService;
import com.whatsmine.service.LandingPageSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class AuthController {

    private final AuthService authService;
    private final LandingPageSettingsService landingPageSettingsService;
    private final PlanRepository planRepository;

    public AuthController(AuthService authService, LandingPageSettingsService landingPageSettingsService, PlanRepository planRepository) {
        this.authService = authService;
        this.landingPageSettingsService = landingPageSettingsService;
        this.planRepository = planRepository;
    }

    @GetMapping("/")
    public InertiaResponse showRoot() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("canLogin", true);
        props.put("canRegister", true);
        props.put("landing", landingPageSettingsService.getPublicSettings());
        props.put("plans", publicPlans());
        return Inertia.render("Welcome", props);
    }

    private List<Map<String, Object>> publicPlans() {
        return planRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getId());
                    m.put("name", p.getName());
                    m.put("description", p.getDescription() != null ? p.getDescription() : "");
                    m.put("price_monthly", (p.getMonthlyPriceCents() != null ? p.getMonthlyPriceCents() : 0) / 100.0);
                    m.put("price_yearly", (p.getYearlyPriceCents() != null ? p.getYearlyPriceCents() : 0) / 100.0);
                    m.put("features", p.getFeatureList());
                    m.put("is_featured", Boolean.TRUE.equals(p.getFeatured()) || Boolean.TRUE.equals(p.getPopular()));
                    m.put("trial_days", p.getTrialDays() != null ? p.getTrialDays() : 0);
                    return m;
                })
                .toList();
    }

    @GetMapping("/login")
    public InertiaResponse showLogin() {
        return Inertia.render("Auth/Login");
    }

    @PostMapping("/login")
    public Object login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        try {
            boolean fullyAuthenticated = authService.login(loginRequest, request);
            if (fullyAuthenticated) {
                return Inertia.redirect("/app/dashboard");
            } else {
                return Inertia.redirect("/two-factor-challenge");
            }
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public Object logout(HttpServletRequest request) {
        authService.logout(request);
        return Inertia.redirect("/login");
    }

    @GetMapping("/register")
    public InertiaResponse showRegister() {
        return Inertia.render("Auth/Register");
    }

    @PostMapping("/register")
    public Object register(@Valid @RequestBody RegisterRequest registerRequest, HttpServletRequest request) {
        try {
            authService.register(registerRequest);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.unprocessableEntity().body(Map.of(
                    "message", "The given data was invalid.",
                    "errors", Map.of("email", e.getMessage())
            ));
        }
        authService.login(new LoginRequest(registerRequest.getEmail(), registerRequest.getPassword(), false), request);
        return Inertia.redirect("/app/dashboard");
    }
}
