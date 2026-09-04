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
@RequestMapping("/admin/landing-page")
public class AdminLandingPageController {

    private final SystemSettingRepository systemSettingRepository;

    public AdminLandingPageController(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    @GetMapping
    public Object edit() {
        Map<String, String> landing = new LinkedHashMap<>();
        systemSettingRepository.findByGroup("landing").forEach(s -> landing.put(s.getKey(), s.getValue()));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("landing", landing);

        return Inertia.render("Admin/LandingPage/Edit", props);
    }

    @PostMapping
    public Object update(@RequestBody Map<String, Object> payload, HttpSession session) {
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (entry.getValue() != null) {
                SystemSetting setting = systemSettingRepository.findByKey(entry.getKey())
                        .orElseGet(() -> {
                            SystemSetting s = new SystemSetting();
                            s.setKey(entry.getKey());
                            s.setGroup("landing");
                            return s;
                        });
                setting.setValue(entry.getValue().toString());
                systemSettingRepository.save(setting);
            }
        }

        Inertia.flashSuccess(session, "Landing page settings updated.");
        return Inertia.redirect("/admin/landing-page");
    }
}
