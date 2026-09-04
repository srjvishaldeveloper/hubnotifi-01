package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.OnboardingStep;
import com.whatsmine.model.User;
import com.whatsmine.repository.OnboardingStepRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/app/onboarding")
public class ClientOnboardingController {

    private final OnboardingStepRepository onboardingStepRepository;

    public ClientOnboardingController(OnboardingStepRepository onboardingStepRepository) {
        this.onboardingStepRepository = onboardingStepRepository;
    }

    @GetMapping
    public Object index(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        List<OnboardingStep> steps = onboardingStepRepository.findByUserId(user.getId());

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("steps", steps);

        return Inertia.render("client/Onboarding/Index", props);
    }

    @PostMapping("/complete-step")
    public Object completeStep(@AuthenticationPrincipal CustomUserDetails userDetails,
                                 @RequestBody Map<String, Object> payload,
                                 HttpSession session) {
        User user = userDetails.getUser();
        String stepKey = (String) payload.get("step_key");

        if (stepKey != null) {
            OnboardingStep step = onboardingStepRepository.findByUserIdAndStepKey(user.getId(), stepKey)
                    .orElseGet(() -> {
                        OnboardingStep s = new OnboardingStep();
                        s.setUserId(user.getId());
                        s.setStepKey(stepKey);
                        return s;
                    });
            step.setCompleted(true);
            step.setCompletedAt(LocalDateTime.now());
            onboardingStepRepository.save(step);
        }

        Inertia.flashSuccess(session, "Onboarding step completed.");
        return Inertia.redirect("/app/onboarding");
    }
}
