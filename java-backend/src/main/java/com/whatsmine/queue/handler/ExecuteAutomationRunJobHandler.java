package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ExecuteAutomationRunJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(ExecuteAutomationRunJobHandler.class);

    @Override
    public String getJobType() {
        return "ExecuteAutomationRunJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null) return;
        Number runId = (Number) data.get("runId");
        log.info("Executing ExecuteAutomationRunJob for runId={}", runId != null ? runId.longValue() : null);
    }

    @Override
    public int getMaxTries() {
        return 3;
    }
}
