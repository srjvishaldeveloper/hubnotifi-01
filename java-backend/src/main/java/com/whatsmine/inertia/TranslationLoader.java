package com.whatsmine.inertia;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Shared by I18nController (client-side async fetch) and DefaultGlobalPropsProvider
// (synchronous seed on every Inertia response) so both read the same flattened,
// cached dictionary instead of duplicating the load-and-flatten logic.
@Component
public class TranslationLoader {

    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();

    public TranslationLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, String> load(String locale) {
        String safeLocale = locale == null ? "en" : locale.replaceAll("[^a-zA-Z0-9_-]", "");
        Map<String, String> dictionary = cache.computeIfAbsent(safeLocale, this::loadAndFlatten);
        if (dictionary.isEmpty() && !"en".equals(safeLocale)) {
            dictionary = cache.computeIfAbsent("en", this::loadAndFlatten);
        }
        return dictionary;
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
