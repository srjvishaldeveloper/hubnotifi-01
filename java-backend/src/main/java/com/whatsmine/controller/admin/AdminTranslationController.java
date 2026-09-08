package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.Translation;
import com.whatsmine.repository.TranslationRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/translations")
public class AdminTranslationController {

    private final TranslationRepository translationRepository;

    public AdminTranslationController(TranslationRepository translationRepository) {
        this.translationRepository = translationRepository;
    }

    @GetMapping
    public Object index(@RequestParam(defaultValue = "en") String locale,
                        @RequestParam(defaultValue = "messages") String group) {
        List<Translation> translations = translationRepository.findByLocaleCodeAndGroup(locale, group);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("translations", translations);
        props.put("locale", locale);
        props.put("group", group);

        return Inertia.render("Admin/Translations/Index", props);
    }

    @RequestMapping(method = { RequestMethod.PUT, RequestMethod.POST })
    public Object update(@RequestBody Map<String, Object> payload, HttpSession session) {
        String locale = (String) payload.get("locale");
        String group = payload.get("group") != null ? (String) payload.get("group") : "messages";

        if (locale != null && payload.get("values") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> values = (Map<String, Object>) payload.get("values");
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                Translation tr = translationRepository.findByLocaleCodeAndGroup(locale, group).stream()
                        .filter(t -> t.getKey().equals(entry.getKey()))
                        .findFirst()
                        .orElseGet(() -> {
                            Translation t = new Translation();
                            t.setLocaleCode(locale);
                            t.setGroup(group);
                            t.setKey(entry.getKey());
                            return t;
                        });
                tr.setValue(entry.getValue() != null ? entry.getValue().toString() : "");
                translationRepository.save(tr);
            }
        }

        Inertia.flashSuccess(session, "Translations saved.");
        return Inertia.redirect("/admin/translations?locale=" + (locale != null ? locale : "en"));
    }
}
