package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FinalizeCampaignJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(FinalizeCampaignJobHandler.class);

    @Override
    public String getJobType() {
        return "FinalizeCampaignJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null) return;
        Number campaignId = (Number) data.get("campaignId");
        log.info("Executing FinalizeCampaignJob for campaignId={}", campaignId != null ? campaignId.longValue() : null);
    }

    @Override
    public int getMaxTries() {
        return 60;
    }
}
