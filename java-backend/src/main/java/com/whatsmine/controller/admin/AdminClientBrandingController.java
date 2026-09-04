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
@RequestMapping("/admin/client-branding")
public class AdminClientBrandingController {

    private final SystemSettingRepository systemSettingRepository;

    public AdminClientBrandingController(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    @GetMapping
    public Object index() {
        Map<String, String> branding = new LinkedHashMap<>();
        systemSettingRepository.findByGroup("branding").forEach(s -> branding.put(s.getKey(), s.getValue()));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("branding", branding);

        return Inertia.render("Admin/ClientBranding/Index", props);
    }

    @PostMapping
    public Object update(@RequestBody Map<String, Object> payload, HttpSession session) {
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (entry.getValue() != null) {
                SystemSetting setting = systemSettingRepository.findByKey(entry.getKey())
                        .orElseGet(() -> {
                            SystemSetting s = new SystemSetting();
                            s.setKey(entry.getKey());
                            s.setGroup("branding");
                            return s;
                        });
                setting.setValue(entry.getValue().toString());
                systemSettingRepository.save(setting);
            }
        }

        Inertia.flashSuccess(session, "Branding settings saved.");
        return Inertia.redirect("/admin/client-branding");
    }
}
