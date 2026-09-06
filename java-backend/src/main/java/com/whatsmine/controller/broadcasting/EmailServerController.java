package com.whatsmine.controller.broadcasting;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.WorkspaceSmtpConfig;
import com.whatsmine.repository.WorkspaceSmtpConfigRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.email.EmailApiClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-workspace SMTP configuration, porting PHP's EmailServerController /
 * workspace_smtp_configs. Distinct from the admin's platform-wide SMTP
 * settings (Admin > Email System), which EmailApiClient falls back to when
 * a workspace has none configured or has deactivated its own.
 */
@RestController
@RequestMapping("/app/broadcasts/email-server")
public class EmailServerController {

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private WorkspaceSmtpConfigRepository workspaceSmtpConfigRepository;

    @Autowired
    private EmailApiClient emailApiClient;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return userDetails.getWorkspaceId();
    }

    @GetMapping
    public Object index(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = getWorkspaceId(userDetails);
        WorkspaceSmtpConfig config = workspaceSmtpConfigRepository.findByWorkspaceId(workspaceId).orElse(null);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("config", configProp(config));
        return inertiaRenderer.render("Broadcasting/EmailServer/Index", props, request);
    }

    @PostMapping
    public Object store(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> body,
            HttpSession session
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        if (workspaceSmtpConfigRepository.findByWorkspaceId(workspaceId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "An SMTP configuration already exists for this workspace — use update instead.");
        }

        WorkspaceSmtpConfig config = new WorkspaceSmtpConfig();
        config.setWorkspaceId(workspaceId);
        applyBody(config, body, true);

        workspaceSmtpConfigRepository.save(config);
        Inertia.flashSuccess(session, "SMTP server saved.");
        return Inertia.redirect("/app/broadcasts/email-server");
    }

    @PutMapping
    public Object update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> body,
            HttpSession session
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        WorkspaceSmtpConfig config = workspaceSmtpConfigRepository.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No SMTP configuration to update."));

        applyBody(config, body, false);
        workspaceSmtpConfigRepository.save(config);
        Inertia.flashSuccess(session, "SMTP server updated.");
        return Inertia.redirect("/app/broadcasts/email-server");
    }

    @DeleteMapping
    public Object destroy(@AuthenticationPrincipal CustomUserDetails userDetails, HttpSession session) {
        Long workspaceId = getWorkspaceId(userDetails);
        workspaceSmtpConfigRepository.findByWorkspaceId(workspaceId).ifPresent(workspaceSmtpConfigRepository::delete);

        Inertia.flashSuccess(session, "SMTP server removed.");
        return Inertia.redirect("/app/broadcasts/email-server");
    }

    /** Deliberately does NOT fall back to the platform-wide SMTP — this tests the workspace's own setup specifically. */
    @PostMapping("/test")
    public ResponseEntity<Map<String, String>> testEmail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, String> body
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        WorkspaceSmtpConfig config = workspaceSmtpConfigRepository.findByWorkspaceId(workspaceId).orElse(null);
        if (config == null || !Boolean.TRUE.equals(config.getIsActive())) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("message", "Save and activate your workspace SMTP settings before sending a test email."));
        }

        String to = body.get("email");
        if (to == null || to.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", "Enter an email address to test."));
        }

        try {
            emailApiClient.send(workspaceId, to, "Test email from Hub Notification", "This is a test email confirming your workspace SMTP configuration works.");
            return ResponseEntity.ok(Map.of("message", "Test email sent to " + to + "."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private void applyBody(WorkspaceSmtpConfig config, Map<String, Object> body, boolean isCreate) {
        if (body.containsKey("host")) config.setHost(str(body.get("host")));
        if (body.containsKey("port")) {
            try {
                config.setPort(Integer.parseInt(String.valueOf(body.get("port"))));
            } catch (Exception ignored) { }
        }
        if (body.containsKey("username")) config.setUsername(str(body.get("username")));
        if (body.containsKey("encryption")) config.setEncryption(str(body.getOrDefault("encryption", "tls")));
        if (body.containsKey("from_email")) config.setFromEmail(str(body.get("from_email")));
        if (body.containsKey("from_name")) config.setFromName(str(body.get("from_name")));
        if (body.containsKey("is_active")) config.setIsActive(Boolean.TRUE.equals(body.get("is_active")));
        else if (isCreate) config.setIsActive(true);

        // Merge-save: a blank password on an update leaves the existing secret untouched.
        String password = str(body.get("password"));
        if (password != null && !password.isBlank()) {
            Map<String, Object> secrets = config.getSecrets() != null ? new LinkedHashMap<>(config.getSecrets()) : new LinkedHashMap<>();
            secrets.put("password", password);
            config.setSecrets(secrets);
        }
    }

    private Map<String, Object> configProp(WorkspaceSmtpConfig config) {
        if (config == null) return null;
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("id", config.getId());
        prop.put("host", config.getHost());
        prop.put("port", config.getPort());
        prop.put("username", config.getUsername());
        prop.put("password", "");
        prop.put("encryption", config.getEncryption());
        prop.put("from_email", config.getFromEmail());
        prop.put("from_name", config.getFromName());
        prop.put("is_active", config.getIsActive());
        prop.put("summary", config.getHost() + ":" + config.getPort() + (config.getUsername() != null ? " (via " + config.getUsername() + ")" : ""));
        return prop;
    }

    private String str(Object o) {
        return o != null ? String.valueOf(o) : null;
    }
}
