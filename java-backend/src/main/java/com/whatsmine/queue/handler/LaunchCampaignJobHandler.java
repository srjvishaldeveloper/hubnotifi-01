package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LaunchCampaignJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(LaunchCampaignJobHandler.class);

    @Override
    public String getJobType() {
        return "LaunchCampaignJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null) return;
        Number campaignId = (Number) data.get("campaignId");
        log.info("Executing LaunchCampaignJob for campaignId={}", campaignId != null ? campaignId.longValue() : null);
    }

    @Override
    public int getMaxTries() {
        return 2;
    }
}
