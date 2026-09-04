package com.whatsmine.controller;

import com.whatsmine.dto.LoginRequest;
import com.whatsmine.dto.RegisterRequest;
import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.model.User;
import com.whatsmine.service.AuthService;
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
@RequestMapping
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public InertiaResponse showLogin() {
        return Inertia.render("Auth/Login");
    }

    @PostMapping("/login")
    public Object login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        try {
            boolean fullyAuthenticated = authService.login(loginRequest, request);
            if (fullyAuthenticated) {
                return Inertia.redirect("/app/dashboard");
            } else {
                return Inertia.redirect("/two-factor-challenge");
            }
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public Object logout(HttpServletRequest request) {
        authService.logout(request);
        return Inertia.redirect("/login");
    }

    @GetMapping("/register")
    public InertiaResponse showRegister() {
        return Inertia.render("Auth/Register");
    }

    @PostMapping("/register")
    public Object register(@Valid @RequestBody RegisterRequest registerRequest, HttpServletRequest request) {
        User user = authService.register(registerRequest);
        authService.login(new LoginRequest(registerRequest.getEmail(), registerRequest.getPassword(), false), request);
        return Inertia.redirect("/app/dashboard");
    }
}
