package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SendCampaignMessageHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(SendCampaignMessageHandler.class);

    @Override
    public String getJobType() {
        return "SendCampaignMessageJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null) return;
        Number campaignId = (Number) data.get("campaignId");
        Number contactId = (Number) data.get("contactId");
        log.info("Executing SendCampaignMessageJob for campaignId={}, contactId={}",
                campaignId != null ? campaignId.longValue() : null,
                contactId != null ? contactId.longValue() : null);
    }

    @Override
    public int getMaxTries() {
        return 3;
    }

    @Override
    public int[] getBackoff() {
        return new int[]{60};
    }
}
