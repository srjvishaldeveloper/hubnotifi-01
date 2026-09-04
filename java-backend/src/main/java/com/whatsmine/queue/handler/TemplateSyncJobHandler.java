package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TemplateSyncJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(TemplateSyncJobHandler.class);

    @Override
    public String getJobType() {
        return "TemplateSyncJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null) return;
        Number wabaDbId = (Number) data.get("wabaDbId");
        log.info("Executing TemplateSyncJob for wabaDbId={}", wabaDbId != null ? wabaDbId.longValue() : null);
    }

    @Override
    public int getMaxTries() {
        return 3;
    }
}
