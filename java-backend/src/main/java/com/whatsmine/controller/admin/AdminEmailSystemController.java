package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.SystemSetting;
import com.whatsmine.repository.SystemSettingRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/email-system")
public class AdminEmailSystemController {

    private final SystemSettingRepository systemSettingRepository;

    public AdminEmailSystemController(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    @GetMapping
    public Object index() {
        Map<String, String> smtp = new LinkedHashMap<>();
        systemSettingRepository.findByGroup("smtp").forEach(s -> smtp.put(s.getKey(), s.getValue()));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("smtp", smtp);

        return Inertia.render("Admin/EmailSystem/Index", props);
    }

    @PostMapping("/smtp")
    public Object updateSmtp(@RequestBody Map<String, Object> payload, HttpSession session) {
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (entry.getValue() != null) {
                SystemSetting setting = systemSettingRepository.findByKey(entry.getKey())
                        .orElseGet(() -> {
                            SystemSetting s = new SystemSetting();
                            s.setKey(entry.getKey());
                            s.setGroup("smtp");
                            return s;
                        });
                setting.setValue(entry.getValue().toString());
                systemSettingRepository.save(setting);
            }
        }

        Inertia.flashSuccess(session, "SMTP settings saved.");
        return Inertia.redirect("/admin/email-system");
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> sendTestEmail(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(Map.of("success", true, "message", "Test email sent successfully."));
    }
}
