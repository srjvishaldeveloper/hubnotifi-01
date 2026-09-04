package com.whatsmine.service;

import com.whatsmine.dto.LoginRequest;
import com.whatsmine.model.AdminUser;
import com.whatsmine.security.AdminUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthService {

    private final AuthenticationManager adminAuthenticationManager;

    public AdminAuthService(@Qualifier("adminAuthenticationManager") AuthenticationManager adminAuthenticationManager) {
        this.adminAuthenticationManager = adminAuthenticationManager;
    }

    public AdminUser login(LoginRequest request, HttpServletRequest httpRequest) {
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword());

        Authentication auth = adminAuthenticationManager.authenticate(token);
        AdminUserDetails adminDetails = (AdminUserDetails) auth.getPrincipal();
        AdminUser adminUser = adminDetails.getAdminUser();

        if (!"ACTIVE".equalsIgnoreCase(adminUser.getStatus())) {
            throw new DisabledException("Admin account is disabled or suspended");
        }

        // Establish admin security session
        SecurityContextHolder.getContext().setAuthentication(auth);
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());

        return adminUser;
    }

    public void logout(HttpServletRequest httpRequest) {
        SecurityContextHolder.clearContext();
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
