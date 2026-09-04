package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ProcessInboundInboxMessageJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(ProcessInboundInboxMessageJobHandler.class);

    @Override
    public String getJobType() {
        return "ProcessInboundInboxMessageJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        log.info("Executing ProcessInboundInboxMessageJob");
    }

    @Override
    public int getMaxTries() {
        return 5;
    }

    @Override
    public int[] getBackoff() {
        return new int[]{30, 60, 120, 240, 300};
    }
}
