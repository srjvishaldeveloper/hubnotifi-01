package com.whatsmine.controller;

import com.whatsmine.dto.LoginRequest;
import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @GetMapping("/login")
    public InertiaResponse showLogin() {
        return Inertia.render("Admin/Auth/Login");
    }

    @PostMapping("/login")
    public Object login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        try {
            adminAuthService.login(loginRequest, request);
            return Inertia.redirect("/admin/dashboard");
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public Object logout(HttpServletRequest request) {
        adminAuthService.logout(request);
        return Inertia.redirect("/admin/login");
    }
}
