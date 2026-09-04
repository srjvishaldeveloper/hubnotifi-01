package com.whatsmine.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class InertiaAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {

        boolean isInertia = "true".equalsIgnoreCase(request.getHeader("X-Inertia"));
        String uri = request.getRequestURI();

        if (isInertia) {
            if (uri.startsWith("/admin")) {
                response.setHeader("X-Inertia-Location", "/admin/login");
            } else {
                response.setHeader("X-Inertia-Location", "/login");
            }
            response.setStatus(HttpServletResponse.SC_CONFLICT); // 409 Conflict triggers full page redirect in Inertia
        } else if (uri.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Unauthenticated.\"}");
        } else {
            if (uri.startsWith("/admin")) {
                response.sendRedirect("/admin/login");
            } else {
                response.sendRedirect("/login");
            }
        }
    }
}
