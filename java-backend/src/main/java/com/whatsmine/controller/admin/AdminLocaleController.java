package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.Locale;
import com.whatsmine.repository.LocaleRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/locales")
public class AdminLocaleController {

    private final LocaleRepository localeRepository;

    public AdminLocaleController(LocaleRepository localeRepository) {
        this.localeRepository = localeRepository;
    }

    @GetMapping
    public Object index() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("locales", localeRepository.findAll());

        return Inertia.render("Admin/Locales/Index", props);
    }

    @PostMapping
    public Object store(@RequestBody Map<String, Object> payload, HttpSession session) {
        String code = (String) payload.get("code");
        String name = (String) payload.get("name");

        if (code != null && name != null) {
            Locale locale = localeRepository.findByCode(code).orElseGet(Locale::new);
            locale.setCode(code);
            locale.setName(name);
            locale.setFlag((String) payload.get("flag"));
            locale.setIsDefault(Boolean.TRUE.equals(payload.get("is_default")));
            locale.setEnabled(payload.get("enabled") == null || Boolean.TRUE.equals(payload.get("enabled")));
            localeRepository.save(locale);
        }

        Inertia.flashSuccess(session, "Locale saved.");
        return Inertia.redirect("/admin/locales");
    }
}
