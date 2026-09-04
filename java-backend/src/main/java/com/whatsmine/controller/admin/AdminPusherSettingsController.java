package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.SystemSetting;
import com.whatsmine.repository.SystemSettingRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/pusher-settings")
public class AdminPusherSettingsController {

    private static final List<String> KEYS = List.of(
            "pusher_app_id", "pusher_app_key", "pusher_app_secret", "pusher_app_cluster");

    private final SystemSettingRepository systemSettingRepository;

    public AdminPusherSettingsController(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    @GetMapping
    public Object index() {
        Map<String, Object> settings = new LinkedHashMap<>();
        for (String key : KEYS) {
            settings.put(key, systemSettingRepository.findByKey(key).map(SystemSetting::getValue).orElse(""));
        }
        settings.put("pusher_enabled", systemSettingRepository.findByKey("pusher_enabled").map(SystemSetting::getValue).orElse("false"));

        boolean configured = !isBlank((String) settings.get("pusher_app_key")) && !isBlank((String) settings.get("pusher_app_secret"));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("settings", settings);
        props.put("configured", configured);
        return Inertia.render("Admin/PusherSettings/Index", props);
    }

    @PutMapping
    public Object update(@RequestBody Map<String, Object> payload, HttpSession session) {
        List<String> updatable = List.of("pusher_app_id", "pusher_app_key", "pusher_app_secret", "pusher_app_cluster", "pusher_enabled");
        for (String key : updatable) {
            Object value = payload.get(key);
            if (value == null) {
                continue;
            }
            SystemSetting setting = systemSettingRepository.findByKey(key).orElseGet(() -> {
                SystemSetting s = new SystemSetting();
                s.setKey(key);
                s.setGroup("pusher");
                return s;
            });
            setting.setValue(value.toString());
            systemSettingRepository.save(setting);
        }

        Inertia.flashSuccess(session, "Pusher settings saved.");
        return Inertia.redirect("/admin/pusher-settings");
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        String key = systemSettingRepository.findByKey("pusher_app_key").map(SystemSetting::getValue).orElse(null);
        String secret = systemSettingRepository.findByKey("pusher_app_secret").map(SystemSetting::getValue).orElse(null);
        String appId = systemSettingRepository.findByKey("pusher_app_id").map(SystemSetting::getValue).orElse(null);

        if (isBlank(key) || isBlank(secret) || isBlank(appId)) {
            return ResponseEntity.status(422).body(Map.of("success", false, "message", "Pusher credentials not configured."));
        }

        // No Pusher SDK wired into the Java backend yet — this only confirms
        // credentials are present, it doesn't make a live connection test.
        return ResponseEntity.ok(Map.of("success", true, "message", "Pusher credentials are present. Live connection test isn't wired up yet."));
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
