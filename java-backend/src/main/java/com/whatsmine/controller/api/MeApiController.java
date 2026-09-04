package com.whatsmine.controller.api;

import com.whatsmine.model.User;
import com.whatsmine.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/me")
public class MeApiController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> show(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("name", user.getName());
        map.put("email", user.getEmail());
        map.put("role", user.getRole());
        map.put("workspace_id", user.getWorkspaceId());
        return ResponseEntity.ok(map);
    }

    @PatchMapping
    public ResponseEntity<Map<String, Object>> update(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                      @RequestBody Map<String, Object> payload) {
        User user = userDetails.getUser();
        if (payload.get("name") != null) user.setName((String) payload.get("name"));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("name", user.getName());
        map.put("email", user.getEmail());
        return ResponseEntity.ok(map);
    }
}
