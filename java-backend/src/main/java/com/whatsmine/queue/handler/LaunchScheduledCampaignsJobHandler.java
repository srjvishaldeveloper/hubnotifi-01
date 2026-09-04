package com.whatsmine.queue.handler;

import com.whatsmine.model.Campaign;
import com.whatsmine.queue.JobHandler;
import com.whatsmine.queue.QueueDispatcher;
import com.whatsmine.repository.CampaignRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Handles LaunchScheduledCampaignsJob — mirrors Laravel LaunchScheduledCampaignsJob.
 * Finds all campaigns with status='queued' and schedule_at <= now,
 * dispatches LaunchCampaignJob for each on the 'broadcast' queue.
 * withoutOverlapping guard is handled by the scheduler (one-at-a-time dispatch).
 * Tries: 1 | Queue: broadcast
 */
@Component
public class LaunchScheduledCampaignsJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(LaunchScheduledCampaignsJobHandler.class);

    private final CampaignRepository campaignRepository;
    private final QueueDispatcher queueDispatcher;

    public LaunchScheduledCampaignsJobHandler(CampaignRepository campaignRepository,
                                               QueueDispatcher queueDispatcher) {
        this.campaignRepository = campaignRepository;
        this.queueDispatcher = queueDispatcher;
    }

    @Override
    public String getJobType() {
        return "LaunchScheduledCampaignsJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        log.info("LaunchScheduledCampaignsJob: scanning for campaigns due for launch");
        LocalDateTime now = LocalDateTime.now();
        // Use findFilteredCampaigns with status=queued, then filter by schedule_at in memory
        // (avoids schema changes — only adds a JPQL query on existing fields)
        List<Campaign> due = campaignRepository.findAll().stream()
                .filter(c -> "queued".equals(c.getStatus())
                        && c.getScheduleAt() != null
                        && !c.getScheduleAt().isAfter(now))
                .toList();

        if (due.isEmpty()) {
            log.debug("LaunchScheduledCampaignsJob: no campaigns due");
            return;
        }

        log.info("LaunchScheduledCampaignsJob: launching {} campaign(s)", due.size());
        for (Campaign campaign : due) {
            queueDispatcher.dispatch("broadcast", "LaunchCampaignJob",
                    Map.of("campaignId", campaign.getId()),
                    2, new int[]{60});
            log.info("LaunchScheduledCampaignsJob: dispatched LaunchCampaignJob for campaignId={}", campaign.getId());
        }
    }

    @Override
    public int getMaxTries() {
        return 1;
    }

    @Override
    public int[] getBackoff() {
        return new int[]{60};
    }
}
