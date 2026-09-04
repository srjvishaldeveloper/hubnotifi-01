package com.whatsmine.controller;

import com.whatsmine.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthCheckController {

    @Value("${app.name:WhatsMine}")
    private String appName;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHealthStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("application", appName);
        status.put("version", appVersion);
        status.put("environment", activeProfile);
        status.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(ApiResponse.success("Application backend is healthy", status));
    }
}
