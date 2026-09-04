package com.whatsmine.queue.handler;

import com.whatsmine.model.AutomationRun;
import com.whatsmine.queue.JobHandler;
import com.whatsmine.repository.AutomationRunRepository;
import com.whatsmine.service.automation.AutomationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Handles ExecuteAutomationRunJob — mirrors Laravel ExecuteAutomationRunJob.
 * Invokes AutomationEngine.executeRun() for the given runId.
 * Idempotent: skips if run is already cancelled or failed.
 * Tries: 3 | Timeout: 120s | Queue: automation
 */
@Component
public class ExecuteAutomationRunJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(ExecuteAutomationRunJobHandler.class);

    private static final List<String> TERMINAL_STATUSES = Arrays.asList("cancelled", "failed");

    private final AutomationRunRepository automationRunRepository;
    private final AutomationEngine automationEngine;

    public ExecuteAutomationRunJobHandler(AutomationRunRepository automationRunRepository,
                                          AutomationEngine automationEngine) {
        this.automationRunRepository = automationRunRepository;
        this.automationEngine = automationEngine;
    }

    @Override
    public String getJobType() {
        return "ExecuteAutomationRunJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null) return;
        Number runIdNum = (Number) data.get("runId");
        if (runIdNum == null) {
            log.warn("ExecuteAutomationRunJob: missing runId in payload, skipping");
            return;
        }
        long runId = runIdNum.longValue();
        log.info("ExecuteAutomationRunJob started: runId={}", runId);

        AutomationRun run = automationRunRepository.findById(runId).orElse(null);
        if (run == null) {
            log.info("ExecuteAutomationRunJob: run {} not found, skipping", runId);
            return;
        }
        if (TERMINAL_STATUSES.contains(run.getStatus())) {
            log.info("ExecuteAutomationRunJob: run {} is already {}, skipping", runId, run.getStatus());
            return;
        }

        try {
            automationEngine.executeRun(run);
            log.info("ExecuteAutomationRunJob completed: runId={}", runId);
        } catch (Exception e) {
            log.error("ExecuteAutomationRunJob failed: runId={}, error={}", runId, e.getMessage());
            throw e; // Let queue retry / fail logic handle it
        }
    }

    @Override
    public int getMaxTries() {
        return 3;
    }

    @Override
    public int[] getBackoff() {
        return new int[]{60, 120, 300};
    }
}
