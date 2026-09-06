package com.whatsmine.queue.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.Campaign;
import com.whatsmine.queue.JobHandler;
import com.whatsmine.queue.QueueDispatcher;
import com.whatsmine.repository.CampaignRecipientRepository;
import com.whatsmine.repository.CampaignRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Stage 4 of the campaign send pipeline, porting PHP's FinalizeCampaignJob:
 * polls until no recipients remain "queued" (self-rescheduling every 60s, up
 * to 1440 attempts / ~24h), then tallies sent/delivered/read vs failed and
 * marks the campaign completed (or failed if nothing sent).
 */
@Component
public class FinalizeCampaignJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(FinalizeCampaignJobHandler.class);
    private static final int POLL_DELAY_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 1440;

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final QueueDispatcher queueDispatcher;
    private final ObjectMapper objectMapper;

    public FinalizeCampaignJobHandler(CampaignRepository campaignRepository,
                                       CampaignRecipientRepository campaignRecipientRepository,
                                       QueueDispatcher queueDispatcher,
                                       ObjectMapper objectMapper) {
        this.campaignRepository = campaignRepository;
        this.campaignRecipientRepository = campaignRecipientRepository;
        this.queueDispatcher = queueDispatcher;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getJobType() {
        return "FinalizeCampaignJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null || data.get("campaignId") == null) return;
        Long campaignId = ((Number) data.get("campaignId")).longValue();
        int attempt = data.get("attempt") != null ? ((Number) data.get("attempt")).intValue() : 1;

        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null || !"sending".equals(campaign.getStatus())) {
            return; // already finalized (or never launched) — nothing to do
        }

        long stillQueued = campaignRecipientRepository.countByCampaignIdAndStatus(campaignId, "queued");
        if (stillQueued > 0 && attempt < MAX_ATTEMPTS) {
            queueDispatcher.dispatchDelayed("broadcast", "FinalizeCampaignJob",
                    Map.of("campaignId", campaignId, "attempt", attempt + 1), POLL_DELAY_SECONDS, 60, new int[]{60});
            return;
        }

        List<Object[]> statusCounts = campaignRecipientRepository.countGroupByStatus(campaignId);
        long total = 0, sent = 0, delivered = 0, read = 0, failed = 0;
        for (Object[] row : statusCounts) {
            String status = (String) row[0];
            long count = ((Number) row[1]).longValue();
            total += count;
            switch (status) {
                case "sent" -> sent += count;
                case "delivered" -> delivered += count;
                case "read" -> read += count;
                case "failed" -> failed += count;
                default -> { }
            }
        }
        // "sent" status recipients are also delivered-or-better in this
        // pipeline's simple send-then-status-update model.
        long sentOrBetter = sent + delivered + read;

        campaign.setStatus(sentOrBetter == 0 && failed > 0 ? "failed" : "completed");
        try {
            Map<String, Object> totals = Map.of(
                    "total", total,
                    "queued", 0,
                    "sent", sentOrBetter,
                    "delivered", delivered + read,
                    "read", read,
                    "failed", failed
            );
            campaign.setTotalsJson(objectMapper.writeValueAsString(totals));
        } catch (Exception ignored) { }

        campaignRepository.save(campaign);
        log.info("FinalizeCampaignJob: campaign {} finalized as {} (sent={}, failed={}, total={}).",
                campaignId, campaign.getStatus(), sentOrBetter, failed, total);
    }

    @Override
    public int getMaxTries() {
        return 60;
    }
}
