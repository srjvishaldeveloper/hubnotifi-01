package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/settings")
public class SystemSettingsController {

    private final Map<String, Object> systemSettings = new HashMap<>();

    public SystemSettingsController() {
        systemSettings.put("app_name", "WhatsMine");
        systemSettings.put("default_locale", "en");
        systemSettings.put("base_currency", "USD");
        systemSettings.put("allow_registration", true);
    }

    @GetMapping
    public InertiaResponse index() {
        Map<String, Object> props = new HashMap<>();
        props.put("settings", systemSettings);

        return Inertia.render("Admin/Settings/Index", props);
    }

    @PutMapping
    public Object update(@RequestBody Map<String, Object> request, HttpSession session) {
        systemSettings.putAll(request);
        Inertia.flashSuccess(session, "System settings updated successfully.");
        return Inertia.redirect("/admin/settings");
    }
}
