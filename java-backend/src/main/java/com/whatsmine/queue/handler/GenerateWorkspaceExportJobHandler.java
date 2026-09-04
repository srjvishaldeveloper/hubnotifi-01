package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GenerateWorkspaceExportJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(GenerateWorkspaceExportJobHandler.class);

    @Override
    public String getJobType() {
        return "GenerateWorkspaceExportJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null) return;
        Number userIdNum = (Number) data.get("userId");
        log.info("Executing GenerateWorkspaceExportJob for userId={}", userIdNum != null ? userIdNum.longValue() : null);
    }

    @Override
    public int getMaxTries() {
        return 1;
    }
}
