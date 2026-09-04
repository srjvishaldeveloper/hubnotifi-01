package com.whatsmine.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationApiController {

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> index() {
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
