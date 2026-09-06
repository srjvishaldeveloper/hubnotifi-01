package com.whatsmine.queue.handler;

import com.whatsmine.model.Campaign;
import com.whatsmine.model.CampaignRecipient;
import com.whatsmine.model.Contact;
import com.whatsmine.queue.JobHandler;
import com.whatsmine.queue.QueueDispatcher;
import com.whatsmine.repository.CampaignRecipientRepository;
import com.whatsmine.repository.CampaignRepository;
import com.whatsmine.service.broadcasting.CampaignAudienceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stage 1 of the real campaign send pipeline, porting PHP's LaunchCampaignJob:
 * resolves the audience, inserts queued CampaignRecipient rows (idempotently —
 * skips contacts already inserted by a retried attempt), then dispatches one
 * DispatchCampaignChunkJob per 1000-contact chunk, staggered 5s apart, plus a
 * FinalizeCampaignJob to poll for completion.
 */
@Component
public class LaunchCampaignJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(LaunchCampaignJobHandler.class);
    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_DELAY_SECONDS = 5;

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final CampaignAudienceService campaignAudienceService;
    private final QueueDispatcher queueDispatcher;

    public LaunchCampaignJobHandler(CampaignRepository campaignRepository,
                                     CampaignRecipientRepository campaignRecipientRepository,
                                     CampaignAudienceService campaignAudienceService,
                                     QueueDispatcher queueDispatcher) {
        this.campaignRepository = campaignRepository;
        this.campaignRecipientRepository = campaignRecipientRepository;
        this.campaignAudienceService = campaignAudienceService;
        this.queueDispatcher = queueDispatcher;
    }

    @Override
    public String getJobType() {
        return "LaunchCampaignJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null || data.get("campaignId") == null) return;
        Long campaignId = ((Number) data.get("campaignId")).longValue();

        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) return;

        // Idempotent: a retried attempt (or a duplicate dispatch) is a no-op
        // once the campaign has moved past "queued".
        if (!"queued".equals(campaign.getStatus())) {
            log.info("LaunchCampaignJob: campaign {} is not queued (status={}), skipping.", campaignId, campaign.getStatus());
            return;
        }

        List<Contact> contacts = campaignAudienceService.resolve(
                campaign.getWorkspaceId(), campaign.getChannel(), campaign.getAudienceType(), campaign.getAudienceRef());

        if (contacts.isEmpty()) {
            campaign.setStatus("failed");
            campaignRepository.save(campaign);
            log.warn("LaunchCampaignJob: campaign {} has an empty audience, marking failed.", campaignId);
            return;
        }

        campaign.setStatus("sending");
        campaignRepository.save(campaign);

        Set<Long> alreadyQueued = new HashSet<>();
        for (CampaignRecipient existing : campaignRecipientRepository.findByCampaignId(campaignId)) {
            alreadyQueued.add(existing.getContactId());
        }

        List<Long> newContactIds = new ArrayList<>();
        for (Contact contact : contacts) {
            if (alreadyQueued.contains(contact.getId())) continue;

            CampaignRecipient recipient = new CampaignRecipient();
            recipient.setCampaignId(campaignId);
            recipient.setContactId(contact.getId());
            recipient.setStatus("queued");
            campaignRecipientRepository.save(recipient);
            newContactIds.add(contact.getId());
        }

        int totalChunks = 0;
        for (int i = 0; i * CHUNK_SIZE < newContactIds.size(); i++) {
            List<Long> chunk = newContactIds.subList(i * CHUNK_SIZE, Math.min((i + 1) * CHUNK_SIZE, newContactIds.size()));
            queueDispatcher.dispatchDelayed("broadcast", "DispatchCampaignChunkJob",
                    Map.of("campaignId", campaignId, "contactIds", new ArrayList<>(chunk)),
                    i * CHUNK_DELAY_SECONDS, 2, new int[]{60});
            totalChunks++;
        }

        int finalizeDelay = Math.max(60, totalChunks * CHUNK_DELAY_SECONDS + 60);
        queueDispatcher.dispatchDelayed("broadcast", "FinalizeCampaignJob",
                Map.of("campaignId", campaignId, "attempt", 1), finalizeDelay, 60, new int[]{60});

        log.info("LaunchCampaignJob: campaign {} queued {} recipients across {} chunks.", campaignId, newContactIds.size(), totalChunks);
    }

    @Override
    public int getMaxTries() {
        return 2;
    }
}
