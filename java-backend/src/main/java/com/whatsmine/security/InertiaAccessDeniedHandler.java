package com.whatsmine.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class InertiaAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {

        boolean isInertia = "true".equalsIgnoreCase(request.getHeader("X-Inertia"));

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        if (isInertia || request.getRequestURI().startsWith("/api/")) {
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"This action is unauthorized.\"}");
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
        }
    }
}
