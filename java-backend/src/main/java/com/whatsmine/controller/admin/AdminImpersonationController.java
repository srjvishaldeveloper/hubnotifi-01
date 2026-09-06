package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.AdminUser;
import com.whatsmine.repository.AdminUserRepository;
import com.whatsmine.security.AdminUserDetails;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns an impersonated session back to the original admin. Deliberately NOT
 * gated to ROLE_ADMIN (see SecurityConfig's carve-out for this exact path) since
 * the caller here is authenticated as the impersonated client user, not the admin —
 * matches PHP's ImpersonationController::stop, which runs on the plain 'auth'
 * middleware rather than 'auth:admin'.
 */
@RestController
@RequestMapping("/admin/impersonation")
public class AdminImpersonationController {

    private final AdminUserRepository adminUserRepository;

    public AdminImpersonationController(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    @PostMapping("/stop")
    public Object stop(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("impersonating"))) {
            return Inertia.redirect("/admin/dashboard");
        }

        Object adminIdRaw = session.getAttribute("impersonator_admin_id");
        session.removeAttribute("impersonator_admin_id");
        session.removeAttribute("impersonating");
        session.removeAttribute("impersonated_client_id");
        session.removeAttribute("impersonated_client_name");

        AdminUser adminUser = adminIdRaw instanceof Long id ? adminUserRepository.findById(id).orElse(null) : null;
        if (adminUser == null) {
            SecurityContextHolder.clearContext();
            session.invalidate();
            return Inertia.redirect("/admin/login");
        }

        AdminUserDetails adminDetails = new AdminUserDetails(adminUser);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());

        Inertia.flashSuccess(session, "Returned to admin.");
        return Inertia.redirect("/admin/clients");
    }
}
