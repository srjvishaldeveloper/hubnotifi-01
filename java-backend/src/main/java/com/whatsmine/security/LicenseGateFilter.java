package com.whatsmine.security;

import com.whatsmine.service.license.LicenseManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Blocks the admin panel when the copy's license is missing or invalid,
 * redirecting to the standalone /license re-activation page — porting PHP's
 * EnsureLicensed middleware. Verification is cached inside LicenseManager
 * (with a grace window on server outage), so this adds no per-request
 * network cost once a license is confirmed. A no-op whenever licensing
 * isn't configured (the default for a self-hosted deployment).
 */
@Component
public class LicenseGateFilter extends OncePerRequestFilter {

    private final LicenseManager licenseManager;

    public LicenseGateFilter(LicenseManager licenseManager) {
        this.licenseManager = licenseManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        boolean exempt = uri.startsWith("/admin/login") || uri.startsWith("/admin/logout");

        if (exempt || !licenseManager.enabled() || Boolean.TRUE.equals(licenseManager.verify(true).get("ok"))) {
            filterChain.doFilter(request, response);
            return;
        }

        String accept = request.getHeader("Accept");
        boolean wantsJson = accept != null && accept.contains("application/json");

        if (wantsJson) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"A valid license is required.\"}");
            return;
        }

        response.sendRedirect("/license");
    }
}
