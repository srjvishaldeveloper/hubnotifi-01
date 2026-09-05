package com.whatsmine.service.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.AiChatbot;
import com.whatsmine.model.CampaignRecipient;
import com.whatsmine.model.ChannelAccount;
import com.whatsmine.model.Contact;
import com.whatsmine.model.Conversation;
import com.whatsmine.model.Message;
import com.whatsmine.realtime.RealtimeBroadcaster;
import com.whatsmine.repository.AiChatbotRepository;
import com.whatsmine.repository.CampaignRecipientRepository;
import com.whatsmine.repository.ChannelAccountRepository;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.ConversationRepository;
import com.whatsmine.repository.MessageRepository;
import com.whatsmine.service.ai.ChatbotRunner;
import com.whatsmine.service.automation.AutomationTriggerService;
import com.whatsmine.service.whatsapp.WhatsAppApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java port of PHP's WhatsappDriver::processWebhookPayload() (the part that
 * matters for a live product: turning a Meta Cloud API webhook into real
 * Contact/Conversation/Message rows). Entry point is {@link #process}, called
 * by WhatsAppWebhookController once the HMAC signature has been verified.
 *
 * Deliberately NOT ported yet (kept as a follow-up, not silently dropped):
 * message_template_status_update and phone_number/account metadata updates
 * (cosmetic sync, not on the critical path for messages to work).
 */
@Service
public class WhatsappInboundProcessor {

    private static final Logger log = LoggerFactory.getLogger(WhatsappInboundProcessor.class);

    private static final List<String> ALLOWED_TYPES = List.of(
            "text", "template", "media", "interactive", "reaction", "image", "video",
            "document", "audio", "location", "contacts", "sticker", "order", "poll", "event", "unsupported");

    private static final Map<String, String> STATUS_MAP = Map.of(
            "sent", "sent", "delivered", "delivered", "read", "read", "failed", "failed");

    private static final Map<String, Integer> STATUS_PRIORITY = Map.of(
            "queued", 0, "sent", 1, "delivered", 2, "read", 3, "failed", 4);

    /** Case-insensitive substrings that hand a conversation off to a human agent. */
    private static final List<String> HANDOVER_PHRASES = List.of(
            "talk to human", "talk to agent", "speak to agent", "speak to human",
            "human please", "real person", "live agent", "live support",
            "need a human", "connect me to", "transfer me");

    private final ChannelAccountRepository channelAccountRepository;
    private final ContactRepository contactRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final AutomationTriggerService automationTriggerService;
    private final AiChatbotRepository aiChatbotRepository;
    private final ChatbotRunner chatbotRunner;
    private final WhatsAppApiClient whatsAppApiClient;
    private final RealtimeBroadcaster realtimeBroadcaster;
    private final ObjectMapper objectMapper;

    public WhatsappInboundProcessor(
            ChannelAccountRepository channelAccountRepository,
            ContactRepository contactRepository,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            CampaignRecipientRepository campaignRecipientRepository,
            AutomationTriggerService automationTriggerService,
            AiChatbotRepository aiChatbotRepository,
            ChatbotRunner chatbotRunner,
            WhatsAppApiClient whatsAppApiClient,
            RealtimeBroadcaster realtimeBroadcaster,
            ObjectMapper objectMapper) {
        this.channelAccountRepository = channelAccountRepository;
        this.contactRepository = contactRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.campaignRecipientRepository = campaignRecipientRepository;
        this.automationTriggerService = automationTriggerService;
        this.aiChatbotRepository = aiChatbotRepository;
        this.chatbotRunner = chatbotRunner;
        this.whatsAppApiClient = whatsAppApiClient;
        this.realtimeBroadcaster = realtimeBroadcaster;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public void process(Map<String, Object> payload) {
        List<Map<String, Object>> entries = (List<Map<String, Object>>) payload.getOrDefault("entry", List.of());

        for (Map<String, Object> entry : entries) {
            List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.getOrDefault("changes", List.of());

            for (Map<String, Object> change : changes) {
                Map<String, Object> value = (Map<String, Object>) change.getOrDefault("value", Map.of());

                List<Map<String, Object>> messages = (List<Map<String, Object>>) value.getOrDefault("messages", List.of());
                for (Map<String, Object> msg : messages) {
                    try {
                        processInboundMessage(value, msg);
                    } catch (Exception e) {
                        log.error("WhatsApp webhook message processing failed: {}", e.getMessage(), e);
                    }
                }

                List<Map<String, Object>> statuses = (List<Map<String, Object>>) value.getOrDefault("statuses", List.of());
                for (Map<String, Object> status : statuses) {
                    try {
                        processStatusUpdate(status);
                    } catch (Exception e) {
                        log.error("WhatsApp webhook status processing failed: {}", e.getMessage(), e);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void processInboundMessage(Map<String, Object> value, Map<String, Object> msg) {
        String msgId = str(msg.get("id"));
        if (msgId != null && messageRepository.findByProviderMessageId(msgId).isPresent()) {
            return; // already processed — Meta redelivers on any non-2xx or timeout
        }

        Map<String, Object> metadata = (Map<String, Object>) value.getOrDefault("metadata", Map.of());
        String phoneNumberId = str(metadata.get("phone_number_id"));
        String fromPhone = str(msg.get("from"));

        ChannelAccount channelAccount = channelAccountRepository
                .findByPhoneNumberIdAndChannel(phoneNumberId, "whatsapp")
                .orElse(null);
        if (channelAccount == null) {
            log.warn("WhatsApp inbound dropped — no channel_account for phone_number_id={} from={} msg_id={}", phoneNumberId, fromPhone, msgId);
            return;
        }

        Long workspaceId = channelAccount.getWorkspaceId();
        String phoneE164 = "+" + (fromPhone != null ? fromPhone : "");

        Contact contact = contactRepository.findByWorkspaceIdAndPhoneE164(workspaceId, phoneE164).orElse(null);
        boolean isNewContact = contact == null;
        if (contact == null) {
            contact = new Contact();
            contact.setWorkspaceId(workspaceId);
            contact.setPhoneE164(phoneE164);
            contact.setOptInWhatsapp(true);
            contact.setSource("whatsapp_inbound");
            contact = contactRepository.save(contact);
        } else if (!Boolean.TRUE.equals(contact.getOptInWhatsapp())) {
            contact.setOptInWhatsapp(true);
            contact = contactRepository.save(contact);
        }

        if (isNewContact) {
            try {
                automationTriggerService.fireForContact(workspaceId, "contact.created", contact.getId(), Map.of());
            } catch (Exception e) {
                log.error("contact.created automation trigger failed for contact {}: {}", contact.getId(), e.getMessage());
            }
        }

        Conversation conversation = conversationRepository
                .findFirstByWorkspaceIdAndContactIdAndChannelAccountIdAndStatusOrderByCreatedAtDesc(
                        workspaceId, contact.getId(), channelAccount.getId(), "open")
                .orElse(null);
        if (conversation == null) {
            conversation = new Conversation();
            conversation.setWorkspaceId(workspaceId);
            conversation.setContactId(contact.getId());
            conversation.setChannelAccountId(channelAccount.getId());
            conversation.setStatus("open");
            conversation.setExternalThreadId(fromPhone);
            // A brand-new inbound conversation has no human on it yet — start it
            // bot-handled (matches PHP's `assigned_to ?? 'bot'` fallback) so
            // maybeAutoReply() below can actually respond. The entity's own
            // default of "human" is meant for agent-initiated conversations.
            conversation.setAssignedTo("bot");
            conversation = conversationRepository.save(conversation);
        }

        String type = str(msg.get("type"));
        if (type == null) type = "text";
        String body = extractBody(type, msg);

        LocalDateTime sentAt;
        try {
            long ts = Long.parseLong(String.valueOf(msg.getOrDefault("timestamp", Instant.now().getEpochSecond())));
            sentAt = LocalDateTime.ofInstant(Instant.ofEpochSecond(ts), ZoneOffset.UTC);
        } catch (NumberFormatException e) {
            sentAt = LocalDateTime.now();
        }

        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setDirection("in");
        message.setChannel("whatsapp");
        message.setType(ALLOWED_TYPES.contains(type) ? type : "unsupported");
        message.setBody(body);
        message.setStatus("delivered");
        message.setProviderMessageId(msgId);
        message.setSentBy("customer");
        message.setSentAt(sentAt);
        try {
            message.setPayload(objectMapper.writeValueAsString(msg));
        } catch (Exception ignored) {
            message.setPayload(null);
        }
        message = messageRepository.save(message);

        conversation.setLastMessageAt(sentAt);
        conversation.setStatus("open");
        conversation.setUnreadCount((conversation.getUnreadCount() != null ? conversation.getUnreadCount() : 0) + 1);
        conversation.setLastInboundAt(sentAt);
        conversationRepository.save(conversation);

        broadcastInbound(workspaceId, conversation, message);

        Map<String, Object> triggerContext = new LinkedHashMap<>();
        triggerContext.put("message_body", body != null ? body : "");
        triggerContext.put("conversation_id", conversation.getId());
        triggerContext.put("channel", "whatsapp");
        try {
            automationTriggerService.fireForContact(workspaceId, "message.received", contact.getId(), triggerContext);
        } catch (Exception e) {
            log.error("Automation trigger failed for contact {}: {}", contact.getId(), e.getMessage());
        }

        try {
            maybeAutoReply(channelAccount, conversation, message, contact);
        } catch (Exception e) {
            log.error("AI auto-reply failed for conversation {}: {}", conversation.getId(), e.getMessage(), e);
        }
    }

    /**
     * Java port of the AI-chatbot branch of PHP's AutoReplyListener::process().
     * Keyword/welcome/schedule auto-reply RULES (WhatsappAutoReply) are a
     * separate, still-unported feature — this only covers: skip if a human is
     * already on the conversation, hand over on request phrases, otherwise run
     * whichever chatbot is linked to this channel account (if any) and send
     * its reply back over WhatsApp.
     */
    private void maybeAutoReply(ChannelAccount channelAccount, Conversation conversation, Message inboundMessage, Contact contact) {
        if ("human".equalsIgnoreCase(conversation.getAssignedTo())) {
            return;
        }

        String body = inboundMessage.getBody() != null ? inboundMessage.getBody().toLowerCase() : "";
        for (String phrase : HANDOVER_PHRASES) {
            if (body.contains(phrase)) {
                conversation.setAssignedTo("human");
                conversation.setHandoverAt(LocalDateTime.now());
                conversationRepository.save(conversation);
                log.info("Conversation {} handed over to human (requested)", conversation.getId());
                return;
            }
        }

        Long chatbotId = extractChatbotId(channelAccount.getMetaJson());
        if (chatbotId == null) {
            return;
        }

        AiChatbot bot = aiChatbotRepository.findByWorkspaceIdAndId(channelAccount.getWorkspaceId(), chatbotId).orElse(null);
        if (bot == null || !bot.isEnabled()) {
            return;
        }

        String reply = chatbotRunner.run(bot, inboundMessage);
        if (reply == null || reply.isBlank()) {
            return;
        }

        Message botMessage = new Message();
        botMessage.setConversationId(conversation.getId());
        botMessage.setDirection("out");
        botMessage.setChannel("whatsapp");
        botMessage.setType("text");
        botMessage.setBody(reply);
        botMessage.setStatus("queued");
        botMessage.setSentBy("bot");
        botMessage.setSentAt(LocalDateTime.now());
        botMessage = messageRepository.save(botMessage);

        try {
            String providerMsgId = whatsAppApiClient.sendTextMessage(contact.getPhoneE164(), reply);
            botMessage.setStatus("sent");
            botMessage.setProviderMessageId(providerMsgId);
        } catch (Exception sendErr) {
            botMessage.setStatus("failed");
            botMessage.setErrorJson("{\"message\":\"" + sendErr.getMessage() + "\"}");
            log.warn("AI chatbot reply send failed for conversation {}: {}", conversation.getId(), sendErr.getMessage());
        }
        botMessage = messageRepository.save(botMessage);

        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        broadcastOutbound(channelAccount.getWorkspaceId(), conversation, botMessage);
    }

    @SuppressWarnings("unchecked")
    private Long extractChatbotId(String metaJson) {
        if (metaJson == null || metaJson.isBlank()) return null;
        try {
            Map<String, Object> meta = objectMapper.readValue(metaJson, Map.class);
            Object id = meta.get("ai_chatbot_id");
            if (id == null) return null;
            return Long.valueOf(id.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private void broadcastOutbound(Long workspaceId, Conversation conversation, Message message) {
        if (realtimeBroadcaster == null) return;
        Map<String, Object> msgPayload = new LinkedHashMap<>();
        msgPayload.put("id", message.getId());
        msgPayload.put("conversation_id", message.getConversationId());
        msgPayload.put("direction", "out");
        msgPayload.put("channel", "whatsapp");
        msgPayload.put("type", message.getType());
        msgPayload.put("body", message.getBody() != null ? message.getBody() : "");
        msgPayload.put("status", message.getStatus());
        msgPayload.put("created_at", message.getCreatedAt() != null ? message.getCreatedAt().toString() : LocalDateTime.now().toString());

        realtimeBroadcaster.broadcast("conversation." + conversation.getId(), ".MessageSent", msgPayload);
        realtimeBroadcaster.broadcast("workspace." + workspaceId, ".MessageSent", msgPayload);
    }

    private void broadcastInbound(Long workspaceId, Conversation conversation, Message message) {
        if (realtimeBroadcaster == null) return;
        Map<String, Object> msgPayload = new LinkedHashMap<>();
        msgPayload.put("id", message.getId());
        msgPayload.put("conversation_id", message.getConversationId());
        msgPayload.put("direction", "in");
        msgPayload.put("channel", "whatsapp");
        msgPayload.put("type", message.getType());
        msgPayload.put("body", message.getBody() != null ? message.getBody() : "");
        msgPayload.put("status", message.getStatus());
        msgPayload.put("created_at", message.getCreatedAt() != null ? message.getCreatedAt().toString() : LocalDateTime.now().toString());

        realtimeBroadcaster.broadcast("conversation." + conversation.getId(), ".MessageReceived", msgPayload);
        realtimeBroadcaster.broadcast("workspace." + workspaceId, ".MessageReceived", msgPayload);
    }

    @SuppressWarnings("unchecked")
    private String extractBody(String type, Map<String, Object> msg) {
        Map<String, Object> textBlock = msg.get("text") instanceof Map ? (Map<String, Object>) msg.get("text") : Map.of();
        Map<String, Object> button = msg.get("button") instanceof Map ? (Map<String, Object>) msg.get("button") : Map.of();
        Map<String, Object> interactive = msg.get("interactive") instanceof Map ? (Map<String, Object>) msg.get("interactive") : Map.of();
        Map<String, Object> buttonReply = interactive.get("button_reply") instanceof Map ? (Map<String, Object>) interactive.get("button_reply") : Map.of();
        Map<String, Object> listReply = interactive.get("list_reply") instanceof Map ? (Map<String, Object>) interactive.get("list_reply") : Map.of();

        String body = firstNonBlank(
                str(textBlock.get("body")),
                str(button.get("text")),
                str(buttonReply.get("title")),
                str(listReply.get("title")),
                str(msg.get("caption")));

        if (body != null && !body.isBlank()) return body;

        return switch (type) {
            case "location" -> {
                Map<String, Object> loc = msg.get("location") instanceof Map ? (Map<String, Object>) msg.get("location") : Map.of();
                String name = str(loc.get("name"));
                yield name != null ? name : "📍 Location";
            }
            case "image" -> "🖼 Image";
            case "video" -> "🎬 Video";
            case "audio" -> "🎤 Audio";
            case "document" -> {
                Map<String, Object> doc = msg.get("document") instanceof Map ? (Map<String, Object>) msg.get("document") : Map.of();
                String filename = str(doc.get("filename"));
                yield "📄 " + (filename != null ? filename : "Document");
            }
            case "sticker" -> "😊 Sticker";
            case "reaction" -> {
                Map<String, Object> reaction = msg.get("reaction") instanceof Map ? (Map<String, Object>) msg.get("reaction") : Map.of();
                String emoji = str(reaction.get("emoji"));
                yield emoji != null ? emoji : "👍";
            }
            default -> "";
        };
    }

    private void processStatusUpdate(Map<String, Object> status) {
        String providerId = str(status.get("id"));
        String newStatus = str(status.get("status"));
        if (providerId == null || newStatus == null) return;

        String mapped = STATUS_MAP.get(newStatus);
        if (mapped == null) return;
        int newPriority = STATUS_PRIORITY.getOrDefault(mapped, 0);

        messageRepository.findByProviderMessageId(providerId).ifPresent(message -> {
            int current = STATUS_PRIORITY.getOrDefault(message.getStatus(), 0);
            if (newPriority >= current) {
                message.setStatus(mapped);
                messageRepository.save(message);
            }
        });

        campaignRecipientRepository.findByProviderMessageId(providerId).ifPresent(recipient -> {
            int current = STATUS_PRIORITY.getOrDefault(recipient.getStatus(), 0);
            if (newPriority < current) return;

            LocalDateTime now = LocalDateTime.now();
            recipient.setStatus(mapped);
            if ("sent".equals(mapped) && recipient.getSentAt() == null) recipient.setSentAt(now);
            if ("delivered".equals(mapped)) {
                if (recipient.getSentAt() == null) recipient.setSentAt(now);
                if (recipient.getDeliveredAt() == null) recipient.setDeliveredAt(now);
            }
            if ("read".equals(mapped)) {
                if (recipient.getSentAt() == null) recipient.setSentAt(now);
                if (recipient.getDeliveredAt() == null) recipient.setDeliveredAt(now);
                if (recipient.getReadAt() == null) recipient.setReadAt(now);
            }
            if ("failed".equals(mapped)) {
                recipient.setFailedReason(extractFailureReason(status));
            }
            campaignRecipientRepository.save(recipient);
        });
    }

    @SuppressWarnings("unchecked")
    private String extractFailureReason(Map<String, Object> status) {
        List<Map<String, Object>> errors = status.get("errors") instanceof List ? (List<Map<String, Object>>) status.get("errors") : List.of();
        if (errors.isEmpty()) return "unknown";
        Map<String, Object> first = errors.get(0);
        String reason = firstNonBlank(str(first.get("title")), str(first.get("message")));
        if (reason == null) reason = "unknown";
        return reason.length() > 512 ? reason.substring(0, 512) : reason;
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
