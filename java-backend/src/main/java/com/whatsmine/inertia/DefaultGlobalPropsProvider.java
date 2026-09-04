package com.whatsmine.inertia;

import com.whatsmine.security.AdminUserDetails;
import com.whatsmine.security.CustomUserDetails;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DefaultGlobalPropsProvider implements GlobalPropsProvider {

    public static final String FLASH_SESSION_KEY = "flash_message";

    // AdminUserDetails.getAuthorities() grants every admin user the same flat
    // ROLE_ADMIN (the roles/permissions tables aren't wired into authentication
    // yet), so every authenticated admin gets the full permission set here too —
    // this list is what actually decides which items AdminLayout's sidebar shows.
    private static final List<String> ALL_ADMIN_PERMISSIONS = List.of(
            "view_clients", "create_clients", "update_clients", "delete_clients",
            "view_subscriptions",
            "view_settings", "manage_settings",
            "view_payment_gateways",
            "view_plans",
            "view_email_settings",
            "view_currencies",
            "view_languages",
            "view_admin_roles", "manage_admin_roles",
            "view_admins", "create_admins", "update_admins", "delete_admins",
            "manage_integrations"
    );

    @Value("${app.name:Hub Notification}")
    private String appName;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${app.demo-mode:false}")
    private boolean demoMode;

    @Override
    public Map<String, Object> getSharedProps(HttpServletRequest request) {
        Map<String, Object> shared = new HashMap<>();

        // CSRF Token
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        shared.put("csrf_token", csrfToken != null ? csrfToken.getToken() : null);

        // Flash Messages
        HttpSession session = request.getSession(false);
        if (session != null) {
            FlashMessage flash = (FlashMessage) session.getAttribute(FLASH_SESSION_KEY);
            if (flash != null) {
                shared.put("flash", flash.toMap());
                session.removeAttribute(FLASH_SESSION_KEY);
            } else {
                shared.put("flash", new HashMap<>());
            }
        } else {
            shared.put("flash", new HashMap<>());
        }

        // Auth Prop
        Map<String, Object> authMap = new HashMap<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()) {
            if (auth.getPrincipal() instanceof CustomUserDetails clientDetails) {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", clientDetails.getId());
                userMap.put("name", clientDetails.getUser().getName());
                userMap.put("email", clientDetails.getUser().getEmail());
                userMap.put("avatar", clientDetails.getUser().getAvatar());
                userMap.put("role", clientDetails.getUser().getRole());
                userMap.put("client_id", clientDetails.getClientId());
                userMap.put("workspace_id", clientDetails.getWorkspaceId());
                authMap.put("user", userMap);
            } else if (auth.getPrincipal() instanceof AdminUserDetails adminDetails) {
                Map<String, Object> adminMap = new HashMap<>();
                adminMap.put("id", adminDetails.getId());
                adminMap.put("name", adminDetails.getAdminUser().getName());
                adminMap.put("email", adminDetails.getAdminUser().getEmail());
                adminMap.put("role", "admin");
                authMap.put("admin", adminMap);
                authMap.put("user", adminMap);
                authMap.put("permissions", ALL_ADMIN_PERMISSIONS);
            }
        } else {
            authMap.put("user", null);
        }
        shared.put("auth", authMap);

        // Branding
        Map<String, Object> branding = new HashMap<>();
        branding.put("app_name", appName);
        branding.put("logo_url", "/whatsmine-logo.png");
        shared.put("branding", branding);

        // Pusher Config for Realtime Echo
        Map<String, Object> pusher = new HashMap<>();
        pusher.put("key", "whatsmine-key");
        pusher.put("cluster", "mt1");
        pusher.put("enabled", true);
        shared.put("pusher", pusher);

        // App System Specs
        shared.put("app_version", appVersion);
        shared.put("demo_mode", demoMode);

        return shared;
    }
}
