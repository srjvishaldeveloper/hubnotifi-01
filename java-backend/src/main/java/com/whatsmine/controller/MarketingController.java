package com.whatsmine.controller;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.model.ContactMessage;
import com.whatsmine.repository.ContactMessageRepository;
import com.whatsmine.repository.PlanRepository;
import com.whatsmine.service.LandingPageSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public marketing pages (no auth required) — the Java port of PHP's
 * routes/web.php public group + LandingController's pricing/faq/useCases/
 * about/integrations methods and ContactController.
 */
@RestController
public class MarketingController {

    private final LandingPageSettingsService landingPageSettingsService;
    private final PlanRepository planRepository;
    private final ContactMessageRepository contactMessageRepository;

    public MarketingController(
            LandingPageSettingsService landingPageSettingsService,
            PlanRepository planRepository,
            ContactMessageRepository contactMessageRepository) {
        this.landingPageSettingsService = landingPageSettingsService;
        this.planRepository = planRepository;
        this.contactMessageRepository = contactMessageRepository;
    }

    @GetMapping("/pricing")
    public InertiaResponse pricing() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("canRegister", true);
        props.put("landing", landingPageSettingsService.getPublicSettings());
        props.put("plans", publicPlans());
        return Inertia.render("marketing/Pricing", props);
    }

    @GetMapping("/faq")
    public InertiaResponse faq() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("canRegister", true);
        props.put("landing", landingPageSettingsService.getPublicSettings());
        return Inertia.render("marketing/Faq", props);
    }

    @GetMapping("/use-cases")
    public InertiaResponse useCases() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("canRegister", true);
        props.put("landing", landingPageSettingsService.getPublicSettings());
        return Inertia.render("marketing/UseCases", props);
    }

    @GetMapping("/integrations")
    public InertiaResponse integrations() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("canRegister", true);
        props.put("landing", landingPageSettingsService.getPublicSettings());
        return Inertia.render("marketing/Integrations", props);
    }

    @GetMapping("/about")
    public InertiaResponse about() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("canRegister", true);
        props.put("landing", landingPageSettingsService.getPublicSettings());
        return Inertia.render("marketing/About", props);
    }

    @GetMapping("/contact")
    public InertiaResponse contactShow() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("landing", landingPageSettingsService.getPublicSettings());
        return Inertia.render("marketing/Contact", props);
    }

    @PostMapping("/contact")
    public Object contactStore(@Valid @RequestBody ContactRequest request, HttpServletRequest httpRequest, HttpSession session) {
        ContactMessage message = new ContactMessage();
        message.setName(request.name());
        message.setEmail(request.email());
        message.setSubject(request.subject());
        message.setMessage(request.message());
        message.setIpAddress(httpRequest.getRemoteAddr());
        contactMessageRepository.save(message);

        Inertia.flashSuccess(session, "Your message has been received. We'll get back to you soon!");
        return Inertia.redirect("/contact");
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

    public record ContactRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 255) String subject,
            @NotBlank @Size(max = 5000) String message) {
    }
}
