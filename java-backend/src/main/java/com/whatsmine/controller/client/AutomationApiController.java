package com.whatsmine.controller.client;

import com.whatsmine.model.Automation;
import com.whatsmine.repository.AutomationRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.automation.AutomationEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/automations")
public class AutomationApiController {

    @Autowired
    private AutomationRepository automationRepository;

    @Autowired
    private AutomationEngine automationEngine;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return userDetails.getWorkspaceId();
    }

    @GetMapping
    public ResponseEntity<List<Automation>> list(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<Automation> automations = automationRepository.findByWorkspaceIdOrderByIdDesc(workspaceId);
        return ResponseEntity.ok(automations);
    }

    @PostMapping("/{id}/trigger")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> trigger(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Automation automation = null;
        try {
            Long numericId = Long.parseLong(id);
            automation = automationRepository.findById(numericId).orElse(null);
        } catch (NumberFormatException ignored) {}

        if (automation == null) {
            automation = automationRepository.findByWorkspaceIdAndUuid(workspaceId, id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Automation not found."));
        }

        if (!automation.getWorkspaceId().equals(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
        }

        Long contactId = null;
        Map<String, Object> context = Map.of();

        if (body != null) {
            if (body.get("contact_id") != null) {
                try {
                    contactId = Long.parseLong(String.valueOf(body.get("contact_id")));
                } catch (Exception ignored) {}
            }
            if (body.get("context") instanceof Map) {
                context = (Map<String, Object>) body.get("context");
            }
        }

        automationEngine.triggerForContact(automation, contactId, context);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("status", "triggered", "automation_id", automation.getId()));
    }
}
