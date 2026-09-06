package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.SystemSetting;
import com.whatsmine.repository.SystemSettingRepository;
import com.whatsmine.service.LandingPageSettingsService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin editor for the marketing site content. Reads/writes the same
 * {@code landing.*} SystemSetting rows that {@link com.whatsmine.controller.MarketingController}
 * renders publicly, using {@link LandingPageSettingsService#defaults()} as the single
 * source of truth for keys/defaults so the two never drift.
 */
@RestController
@RequestMapping("/admin/landing-page")
public class AdminLandingPageController {

    private final SystemSettingRepository systemSettingRepository;

    public AdminLandingPageController(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    @GetMapping
    public Object index() {
        Map<String, String> stored = new LinkedHashMap<>();
        systemSettingRepository.findByGroup("landing").forEach(s -> stored.put(s.getKey(), s.getValue()));

        Map<String, String> settings = new LinkedHashMap<>();
        LandingPageSettingsService.defaults().forEach((key, def) -> settings.put(key, stored.getOrDefault(key, def)));

        return Inertia.render("Admin/LandingPage/Index", Map.of("settings", settings));
    }

    @SuppressWarnings("unchecked")
    @PostMapping
    public Object update(@RequestBody Map<String, Object> body, HttpSession session) {
        Object rawSettings = body.get("settings");
        if (rawSettings instanceof Map) {
            ((Map<String, Object>) rawSettings).forEach((key, value) -> {
                if (!key.startsWith("landing.")) return;
                SystemSetting setting = systemSettingRepository.findByKey(key)
                        .orElseGet(() -> {
                            SystemSetting s = new SystemSetting();
                            s.setKey(key);
                            s.setGroup("landing");
                            return s;
                        });
                setting.setValue(value != null ? value.toString() : "");
                systemSettingRepository.save(setting);
            });
        }

        Inertia.flashSuccess(session, "Site content saved.");
        return Inertia.redirect("/admin/landing-page");
    }
}
