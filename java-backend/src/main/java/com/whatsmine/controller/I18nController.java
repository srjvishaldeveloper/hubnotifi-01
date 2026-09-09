package com.whatsmine.controller;

import com.whatsmine.inertia.TranslationLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/i18n")
public class I18nController {

    private final TranslationLoader translationLoader;

    public I18nController(TranslationLoader translationLoader) {
        this.translationLoader = translationLoader;
    }

    @GetMapping("/{locale}")
    public ResponseEntity<Map<String, Object>> show(@PathVariable String locale) {
        return ResponseEntity.ok(Map.of("translation", translationLoader.load(locale)));
    }
}
