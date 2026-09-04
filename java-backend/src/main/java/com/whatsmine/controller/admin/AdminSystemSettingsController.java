package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.SystemSetting;
import com.whatsmine.repository.SystemSettingRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/system-settings")
public class AdminSystemSettingsController {

    private final SystemSettingRepository systemSettingRepository;

    public AdminSystemSettingsController(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    @GetMapping
    public Object index() {
        Map<String, String> settings = new LinkedHashMap<>();
        systemSettingRepository.findAll().forEach(s -> settings.put(s.getKey(), s.getValue()));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("settings", settings);

        return Inertia.render("Admin/SystemSettings/Index", props);
    }

    @PostMapping
    public Object update(@RequestBody Map<String, Object> payload, HttpSession session) {
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (entry.getValue() != null) {
                SystemSetting setting = systemSettingRepository.findByKey(entry.getKey())
                        .orElseGet(() -> {
                            SystemSetting s = new SystemSetting();
                            s.setKey(entry.getKey());
                            s.setGroup("general");
                            return s;
                        });
                setting.setValue(entry.getValue().toString());
                systemSettingRepository.save(setting);
            }
        }

        Inertia.flashSuccess(session, "System settings updated.");
        return Inertia.redirect("/admin/system-settings");
    }
}
