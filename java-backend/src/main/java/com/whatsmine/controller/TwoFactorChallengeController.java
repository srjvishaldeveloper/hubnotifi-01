package com.whatsmine.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.model.User;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.security.SessionMetadataStore;
import com.whatsmine.security.TotpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
public class TwoFactorChallengeController {

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final ObjectMapper objectMapper;
    private final SessionRegistry sessionRegistry;
    private final SessionMetadataStore sessionMetadataStore;

    public TwoFactorChallengeController(
            UserRepository userRepository,
            TotpService totpService,
            ObjectMapper objectMapper,
            SessionRegistry sessionRegistry,
            SessionMetadataStore sessionMetadataStore) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
        this.sessionMetadataStore = sessionMetadataStore;
    }

    @GetMapping("/two-factor-challenge")
    public Object challenge(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("2fa_user_id") == null) {
            return Inertia.redirect("/login");
        }
        return Inertia.render("Auth/TwoFactorChallenge");
    }

    @PostMapping("/two-factor-challenge")
    public Object verify(@RequestParam("code") String code, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("2fa_user_id") == null) {
            return Inertia.redirect("/login");
        }

        Long userId = (Long) session.getAttribute("2fa_user_id");
        Optional<User> userOpt = userRepository.findById(userId);

        if (userOpt.isEmpty()) {
            return Inertia.redirect("/login");
        }

        User user = userOpt.get();
        boolean valid = false;

        if (code != null && code.trim().length() == 6 && code.trim().chars().allMatch(Character::isDigit)) {
            valid = totpService.verifyCode(user.getTwoFactorSecret(), code.trim());
        } else if (code != null && user.getTwoFactorRecoveryCodes() != null) {
            // Verify recovery code
            try {
                List<String> recoveryCodes = objectMapper.readValue(user.getTwoFactorRecoveryCodes(), new TypeReference<List<String>>() {});
                String trimmedCode = code.trim();
                if (recoveryCodes.contains(trimmedCode)) {
                    valid = true;
                    recoveryCodes.remove(trimmedCode);
                    user.setTwoFactorRecoveryCodes(objectMapper.writeValueAsString(recoveryCodes));
                    userRepository.save(user);
                }
            } catch (Exception ignored) {}
        }

        if (!valid) {
            throw new BadCredentialsException("Invalid 2FA verification code.");
        }

        // Authenticate into session
        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        session.removeAttribute("2fa_user_id");
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());

        sessionRegistry.registerNewSession(session.getId(), userDetails);
        sessionMetadataStore.record(session.getId(), request.getRemoteAddr(), request.getHeader("User-Agent"));

        return Inertia.redirect("/app/dashboard");
    }
}
