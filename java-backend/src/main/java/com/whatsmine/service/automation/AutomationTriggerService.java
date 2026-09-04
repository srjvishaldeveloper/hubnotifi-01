package com.whatsmine.service.automation;

import com.whatsmine.model.Automation;
import com.whatsmine.repository.AutomationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Listens for system events and triggers active automations matching the event types.
 */
@Service
public class AutomationTriggerService {

    private static final Logger log = LoggerFactory.getLogger(AutomationTriggerService.class);

    @Autowired
    private AutomationRepository automationRepository;

    @Autowired
    private AutomationEngine automationEngine;

    public void fireForContact(Long workspaceId, String triggerType, Long contactId, Map<String, Object> context) {
        List<Automation> automations = automationRepository.findByWorkspaceIdAndTriggerTypeAndStatus(workspaceId, triggerType, "active");

        for (Automation auto : automations) {
            // Keyword filtering for message.received
            if ("message.received".equals(triggerType)) {
                Map<String, Object> config = auto.getTriggerConfig();
                if (config != null && config.containsKey("keywords")) {
                    @SuppressWarnings("unchecked")
                    List<String> keywords = (List<String>) config.get("keywords");
                    if (keywords != null && !keywords.isEmpty()) {
                        String body = context != null ? String.valueOf(context.getOrDefault("message_body", "")).toLowerCase() : "";
                        boolean matched = keywords.stream().anyMatch(k -> body.contains(k.toLowerCase()));
                        if (!matched) continue;
                    }
                }
            }

            try {
                automationEngine.triggerForContact(auto, contactId, context);
            } catch (Exception e) {
                log.error("Failed to trigger automation [ID: {}]: {}", auto.getId(), e.getMessage());
            }
        }
    }

    @EventListener
    public void handleWebhookReceived(AutomationWebhookReceivedEvent event) {
        Automation auto = automationRepository.findById(event.getAutomationId()).orElse(null);
        if (auto == null || !auto.isActive()) return;

        Map<String, Object> context = new HashMap<>();
        if (event.getPayload() != null) {
            context.putAll(event.getPayload());
        }

        try {
            automationEngine.triggerForContact(auto, event.getContactId(), context);
        } catch (Exception e) {
            log.error("Failed to trigger webhook automation [ID: {}]: {}", auto.getId(), e.getMessage());
        }
    }
}
