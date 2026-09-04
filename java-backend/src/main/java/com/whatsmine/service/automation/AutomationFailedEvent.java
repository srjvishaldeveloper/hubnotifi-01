package com.whatsmine.service.automation;

import org.springframework.context.ApplicationEvent;

public class AutomationFailedEvent extends ApplicationEvent {

    private final Long automationRunId;
    private final String errorMessage;

    public AutomationFailedEvent(Object source, Long automationRunId, String errorMessage) {
        super(source);
        this.automationRunId = automationRunId;
        this.errorMessage = errorMessage;
    }

    public Long getAutomationRunId() {
        return automationRunId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
