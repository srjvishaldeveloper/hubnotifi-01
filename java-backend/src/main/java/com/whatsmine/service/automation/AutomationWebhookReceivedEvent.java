package com.whatsmine.service.automation;

import org.springframework.context.ApplicationEvent;

import java.util.Map;

public class AutomationWebhookReceivedEvent extends ApplicationEvent {

    private final Long automationId;
    private final Map<String, Object> payload;
    private final Long contactId;

    public AutomationWebhookReceivedEvent(Object source, Long automationId, Map<String, Object> payload, Long contactId) {
        super(source);
        this.automationId = automationId;
        this.payload = payload;
        this.contactId = contactId;
    }

    public Long getAutomationId() {
        return automationId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Long getContactId() {
        return contactId;
    }
}
