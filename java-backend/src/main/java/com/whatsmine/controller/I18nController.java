package com.whatsmine.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/i18n")
public class I18nController {

    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();

    public I18nController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{locale}")
    public ResponseEntity<Map<String, Object>> show(@PathVariable String locale) {
        String safeLocale = locale.replaceAll("[^a-zA-Z0-9_-]", "");
        Map<String, String> dictionary = cache.computeIfAbsent(safeLocale, this::loadAndFlatten);
        if (dictionary.isEmpty() && !"en".equals(safeLocale)) {
            dictionary = cache.computeIfAbsent("en", this::loadAndFlatten);
        }
        return ResponseEntity.ok(Map.of("translation", dictionary));
    }

    private Map<String, String> loadAndFlatten(String loc) {
        ClassPathResource resource = new ClassPathResource("locales/" + loc + ".json");
        if (!resource.exists()) {
            return Map.of();
        }
        try (InputStream is = resource.getInputStream()) {
            Map<String, Object> nested = objectMapper.readValue(is, new TypeReference<>() {});
            Map<String, String> flat = new HashMap<>();
            flatten(nested, "", flat);
            return flat;
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private void flatten(Map<String, Object> source, String prefix, Map<String, String> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object val = entry.getValue();
            if (val instanceof Map<?, ?> nestedMap) {
                flatten((Map<String, Object>) nestedMap, key, target);
            } else if (val != null) {
                target.put(key, val.toString());
            }
        }
    }
}
