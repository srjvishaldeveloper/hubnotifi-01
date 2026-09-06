package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import com.whatsmine.queue.QueueDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Stage 2 of the campaign send pipeline, porting PHP's DispatchCampaignChunkJob:
 * dispatches one SendCampaignMessageJob per contact in the chunk. PHP staggers
 * these 100ms apart (a 10 msg/sec rate cap); this queue's dispatch API only
 * supports whole-second delays, so the same intent is approximated at
 * 1-second granularity (10 contacts per delay step) rather than claiming
 * exact sub-second timing it can't provide.
 */
@Component
public class DispatchCampaignChunkJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(DispatchCampaignChunkJobHandler.class);
    private static final int PER_SECOND_RATE_CAP = 10;

    private final QueueDispatcher queueDispatcher;

    public DispatchCampaignChunkJobHandler(QueueDispatcher queueDispatcher) {
        this.queueDispatcher = queueDispatcher;
    }

    @Override
    public String getJobType() {
        return "DispatchCampaignChunkJob";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null || data.get("campaignId") == null) return;
        Long campaignId = ((Number) data.get("campaignId")).longValue();
        List<?> contactIds = data.get("contactIds") instanceof List ? (List<?>) data.get("contactIds") : List.of();

        int i = 0;
        for (Object contactIdObj : contactIds) {
            Long contactId = ((Number) contactIdObj).longValue();
            int delaySeconds = i / PER_SECOND_RATE_CAP;
            queueDispatcher.dispatchDelayed("broadcast", "SendCampaignMessageJob",
                    Map.of("campaignId", campaignId, "contactId", contactId), delaySeconds, 3, new int[]{60});
            i++;
        }

        log.info("DispatchCampaignChunkJob: campaign {} dispatched {} SendCampaignMessageJobs.", campaignId, contactIds.size());
    }

    @Override
    public int getMaxTries() {
        return 2;
    }
}
