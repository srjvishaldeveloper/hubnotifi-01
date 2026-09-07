package com.whatsmine.controller.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.model.User;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.security.TotpService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enable/disable authenticator-app 2FA and manage recovery codes.
 * Pairs with TwoFactorChallengeController, which handles the login-time OTP prompt.
 */
@RestController
@RequestMapping("/profile/two-factor")
public class TwoFactorSettingsController {

    private static final String RECOVERY_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int RECOVERY_CODE_COUNT = 8;

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Value("${app.name:Hub Notification}")
    private String appName;

    public TwoFactorSettingsController(
            UserRepository userRepository,
            TotpService totpService,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public InertiaResponse edit(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        boolean enabled = user.getTwoFactorSecret() != null && user.getTwoFactorConfirmedAt() != null;

        Map<String, Object> props = new HashMap<>();
        props.put("enabled", enabled);

        if (enabled) {
            props.put("recoveryCodes", readRecoveryCodes(user));
        } else {
            String secret = user.getTwoFactorSecret();
            if (secret == null || secret.isBlank()) {
                secret = totpService.generateSecret();
                user.setTwoFactorSecret(secret);
                userRepository.save(user);
            }
            props.put("secretKey", secret);
            props.put("qrCode", buildOtpAuthUri(user.getEmail(), secret));
        }

        return Inertia.render("Profile/TwoFactor", props);
    }

    @PostMapping("/enable")
    public Object enable(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody EnableRequest request,
            HttpSession session) {

        User user = userRepository.findById(userDetails.getId()).orElseThrow();

        if (user.getTwoFactorSecret() == null || !totpService.verifyCode(user.getTwoFactorSecret(), request.getCode())) {
            throw new BadCredentialsException("The verification code is invalid.");
        }

        List<String> recoveryCodes = generateRecoveryCodes();
        user.setTwoFactorConfirmedAt(LocalDateTime.now());
        writeRecoveryCodes(user, recoveryCodes);
        userRepository.save(user);

        Inertia.flashSuccess(session, "Two-factor authentication has been enabled.");
        return Inertia.redirect("/profile/two-factor");
    }

    @DeleteMapping("/disable")
    public Object disable(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody PasswordConfirmationRequest request,
            HttpSession session) {

        User user = userRepository.findById(userDetails.getId()).orElseThrow();

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("The provided password does not match your current password.");
        }

        user.setTwoFactorSecret(null);
        user.setTwoFactorRecoveryCodes(null);
        user.setTwoFactorConfirmedAt(null);
        userRepository.save(user);

        Inertia.flashSuccess(session, "Two-factor authentication has been disabled.");
        return Inertia.redirect("/profile/two-factor");
    }

    @PostMapping("/recovery-codes")
    public Object regenerateRecoveryCodes(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody PasswordConfirmationRequest request,
            HttpSession session) {

        User user = userRepository.findById(userDetails.getId()).orElseThrow();

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("The provided password does not match your current password.");
        }
        if (user.getTwoFactorConfirmedAt() == null) {
            throw new BadCredentialsException("Two-factor authentication is not enabled.");
        }

        writeRecoveryCodes(user, generateRecoveryCodes());
        userRepository.save(user);

        Inertia.flashSuccess(session, "New recovery codes have been generated.");
        return Inertia.redirect("/profile/two-factor");
    }

    private String buildOtpAuthUri(String email, String secret) {
        String label = encode(appName + ":" + email);
        String issuer = encode(appName);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + issuer + "&algorithm=SHA1&digits=6&period=30";
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private List<String> generateRecoveryCodes() {
        SecureRandom random = new SecureRandom();
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            codes.add(randomChunk(random, 4) + "-" + randomChunk(random, 4));
        }
        return codes;
    }

    private String randomChunk(SecureRandom random, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RECOVERY_CODE_CHARS.charAt(random.nextInt(RECOVERY_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private void writeRecoveryCodes(User user, List<String> codes) {
        try {
            user.setTwoFactorRecoveryCodes(objectMapper.writeValueAsString(codes));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist recovery codes", e);
        }
    }

    private List<String> readRecoveryCodes(User user) {
        if (user.getTwoFactorRecoveryCodes() == null) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(user.getTwoFactorRecoveryCodes(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static class EnableRequest {
        @NotBlank
        private String code;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    public static class PasswordConfirmationRequest {
        @NotBlank
        private String password;

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
