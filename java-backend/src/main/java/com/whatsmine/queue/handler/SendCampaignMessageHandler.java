package com.whatsmine.queue.handler;

import com.whatsmine.model.Campaign;
import com.whatsmine.model.CampaignRecipient;
import com.whatsmine.model.ChannelAccount;
import com.whatsmine.model.Contact;
import com.whatsmine.queue.JobHandler;
import com.whatsmine.repository.CampaignRecipientRepository;
import com.whatsmine.repository.CampaignRepository;
import com.whatsmine.repository.ChannelAccountRepository;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.service.email.EmailApiClient;
import com.whatsmine.service.sms.SmsApiClient;
import com.whatsmine.service.whatsapp.WhatsAppApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Stage 3 of the campaign send pipeline, porting PHP's SendCampaignMessageJob:
 * the actual per-recipient channel send. This is the exact send logic that
 * used to run synchronously inside CampaignController.launch() — lifted
 * unchanged (same placeholder "Broadcast: <name>" body — the "campaign
 * message body is a placeholder" gap is a separate, already-tracked item)
 * so real WhatsApp/SMS/Email sending isn't lost, just moved off the request
 * thread and onto the real queue.
 */
@Component
public class SendCampaignMessageHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(SendCampaignMessageHandler.class);

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final ContactRepository contactRepository;
    private final ChannelAccountRepository channelAccountRepository;
    private final WhatsAppApiClient whatsAppApiClient;
    private final SmsApiClient smsApiClient;
    private final EmailApiClient emailApiClient;

    public SendCampaignMessageHandler(CampaignRepository campaignRepository,
                                       CampaignRecipientRepository campaignRecipientRepository,
                                       ContactRepository contactRepository,
                                       ChannelAccountRepository channelAccountRepository,
                                       WhatsAppApiClient whatsAppApiClient,
                                       SmsApiClient smsApiClient,
                                       EmailApiClient emailApiClient) {
        this.campaignRepository = campaignRepository;
        this.campaignRecipientRepository = campaignRecipientRepository;
        this.contactRepository = contactRepository;
        this.channelAccountRepository = channelAccountRepository;
        this.whatsAppApiClient = whatsAppApiClient;
        this.smsApiClient = smsApiClient;
        this.emailApiClient = emailApiClient;
    }

    @Override
    public String getJobType() {
        return "SendCampaignMessageJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null || data.get("campaignId") == null || data.get("contactId") == null) return;
        Long campaignId = ((Number) data.get("campaignId")).longValue();
        Long contactId = ((Number) data.get("contactId")).longValue();

        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) return;

        // Skip sends for a paused/failed/completed campaign — defense in depth
        // against a chunk that was already dispatched before a pause/cancel.
        if (!"sending".equals(campaign.getStatus())) {
            log.info("SendCampaignMessageJob: campaign {} is {} (not sending), skipping contact {}.", campaignId, campaign.getStatus(), contactId);
            return;
        }

        CampaignRecipient recipient = campaignRecipientRepository.findByCampaignIdAndContactId(campaignId, contactId).orElse(null);
        if (recipient == null) return;

        Contact contact = contactRepository.findById(contactId).orElse(null);
        if (contact == null) {
            recipient.setStatus("failed");
            recipient.setFailedReason("Contact no longer exists.");
            campaignRecipientRepository.save(recipient);
            return;
        }

        recipient.setSentAt(LocalDateTime.now());

        try {
            String channel = campaign.getChannel();
            if ("whatsapp".equalsIgnoreCase(channel)) {
                ChannelAccount waChannelAccount = channelAccountRepository.findByWorkspaceIdAndStatus(campaign.getWorkspaceId(), "active").stream()
                        .filter(ca -> "whatsapp".equalsIgnoreCase(ca.getChannel()))
                        .findFirst().orElse(null);
                if (waChannelAccount == null) {
                    throw new IllegalStateException("No active WhatsApp channel connected for this workspace.");
                }
                String providerId = whatsAppApiClient.sendText(waChannelAccount, contact.getPhoneE164(), "Broadcast: " + campaign.getName());
                recipient.setStatus("sent");
                recipient.setProviderMessageId(providerId);
            } else if ("sms".equalsIgnoreCase(channel)) {
                String sid = smsApiClient.sendText(campaign.getWorkspaceId(), contact.getPhoneE164(), "Broadcast: " + campaign.getName());
                recipient.setStatus("sent");
                recipient.setProviderMessageId(sid);
            } else if ("email".equalsIgnoreCase(channel)) {
                emailApiClient.send(campaign.getWorkspaceId(), contact.getEmail(), campaign.getName(), "Broadcast: " + campaign.getName());
                recipient.setStatus("sent");
                recipient.setProviderMessageId("email-" + UUID.randomUUID());
            } else {
                throw new IllegalStateException("Unsupported campaign channel: " + channel);
            }
        } catch (Exception e) {
            recipient.setStatus("failed");
            recipient.setFailedReason(e.getMessage());
            log.warn("SendCampaignMessageJob: campaign {} contact {} send failed: {}", campaignId, contactId, e.getMessage());
        }

        campaignRecipientRepository.save(recipient);
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
