package com.whatsmine.controller.client;

import com.whatsmine.model.Automation;
import com.whatsmine.model.Contact;
import com.whatsmine.repository.AutomationRepository;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.service.automation.AutomationEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/webhooks/automation")
public class AutomationWebhookController {

    @Autowired
    private AutomationRepository automationRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private AutomationEngine automationEngine;

    @PostMapping("/{triggerToken}")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @PathVariable String triggerToken,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        Automation automation = automationRepository.findByTriggerToken(triggerToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Automation not found for trigger token."));

        if (!automation.isActive()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of("status", "skipped", "message", "Automation is inactive."));
        }

        Map<String, Object> context = payload != null ? payload : Map.of();
        Long contactId = resolveContactId(automation.getWorkspaceId(), context);

        automationEngine.triggerForContact(automation, contactId, context);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("status", "accepted", "automation_uuid", automation.getUuid()));
    }

    private Long resolveContactId(Long workspaceId, Map<String, Object> context) {
        String email = str(context.get("email"));
        if (email.isEmpty() && context.get("customer") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> customer = (Map<String, Object>) context.get("customer");
            email = str(customer.get("email"));
        }

        String phone = str(context.get("phone"));
        if (phone.isEmpty()) phone = str(context.get("phone_e164"));
        if (phone.isEmpty() && context.get("customer") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> customer = (Map<String, Object>) context.get("customer");
            phone = str(customer.get("phone"));
        }

        if (!phone.isEmpty()) {
            Optional<Contact> byPhone = contactRepository.findByWorkspaceIdAndPhoneE164(workspaceId, phone);
            if (byPhone.isPresent()) return byPhone.get().getId();
        }

        if (!email.isEmpty()) {
            Optional<Contact> byEmail = contactRepository.findByWorkspaceIdAndEmail(workspaceId, email);
            if (byEmail.isPresent()) return byEmail.get().getId();
        }

        return null;
    }

    private String str(Object o) {
        return o != null ? String.valueOf(o).trim() : "";
    }
}
