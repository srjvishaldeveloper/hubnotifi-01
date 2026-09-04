package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.model.User;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public InertiaResponse edit(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> props = new HashMap<>();
        if (userDetails != null) {
            User user = userDetails.getUser();
            Map<String, Object> userProp = new HashMap<>();
            userProp.put("id", user.getId());
            userProp.put("name", user.getName());
            userProp.put("email", user.getEmail());
            userProp.put("avatar", user.getAvatar());
            userProp.put("locale", user.getLocale());
            userProp.put("timezone", user.getTimezone());
            userProp.put("two_factor_enabled", user.getTwoFactorSecret() != null && user.getTwoFactorConfirmedAt() != null);
            props.put("mustVerifyEmail", false);
            props.put("status", null);
            props.put("user", userProp);
        }
        return Inertia.render("Profile/Edit", props);
    }

    @PutMapping
    public Object update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody ProfileUpdateRequest request,
            HttpSession session) {

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        userRepository.save(user);

        Inertia.flashSuccess(session, "Profile updated successfully.");
        return Inertia.redirect("/profile");
    }

    @PutMapping("/password")
    public Object updatePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody PasswordUpdateRequest request,
            HttpSession session) {

        User user = userRepository.findById(userDetails.getId()).orElseThrow();

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadCredentialsException("The provided password does not match your current password.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        Inertia.flashSuccess(session, "Password updated successfully.");
        return Inertia.redirect("/profile");
    }

    public static class ProfileUpdateRequest {
        @NotBlank
        private String name;

        @NotBlank
        @Email
        private String email;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class PasswordUpdateRequest {
        @NotBlank
        private String currentPassword;

        @NotBlank
        @Size(min = 8)
        private String newPassword;

        public String getCurrentPassword() { return currentPassword; }
        public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}
