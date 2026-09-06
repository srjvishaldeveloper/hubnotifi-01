package com.whatsmine.service.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.AiChatbot;
import com.whatsmine.model.ChannelAccount;
import com.whatsmine.model.Contact;
import com.whatsmine.model.Conversation;
import com.whatsmine.model.Message;
import com.whatsmine.realtime.RealtimeBroadcaster;
import com.whatsmine.repository.AiChatbotRepository;
import com.whatsmine.repository.ChannelAccountRepository;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.ConversationRepository;
import com.whatsmine.repository.MessageRepository;
import com.whatsmine.service.ai.ChatbotRunner;
import com.whatsmine.service.automation.AutomationTriggerService;
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
 * Java port of PHP's MessengerDriver/InstagramDriver inbound processing —
 * the two are near-identical in PHP too (separate files there), so this
 * collapses them into one class parameterized by channel ("messenger" or
 * "instagram") rather than duplicating the logic.
 *
 * Contact identification differs from WhatsApp: there's no phone number,
 * just a Page-Scoped ID (PSID) or Instagram-Scoped ID (IGSID), stored in
 * Contact.customFields (JSON key "messenger_psid"/"instagram_psid") exactly
 * as PHP does — matched here with an in-memory scan of the workspace's
 * contacts rather than a DB-side JSON query, to stay portable across H2
 * (tests) and MySQL (prod) without native-SQL dialect branching.
 *
 * Deliberately NOT handled yet: postbacks, read/delivery receipts, and
 * fetching the sender's real name/profile picture from Meta — contacts are
 * created with a generic placeholder name. These don't block messages from
 * actually sending and receiving.
 */
@Service
public class MetaMessagingInboundProcessor {

    private static final Logger log = LoggerFactory.getLogger(MetaMessagingInboundProcessor.class);

    private static final List<String> HANDOVER_PHRASES = List.of(
            "talk to human", "talk to agent", "speak to agent", "speak to human",
            "human please", "real person", "live agent", "live support",
            "need a human", "connect me to", "transfer me");

    private final ChannelAccountRepository channelAccountRepository;
    private final ContactRepository contactRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AutomationTriggerService automationTriggerService;
    private final AiChatbotRepository aiChatbotRepository;
    private final ChatbotRunner chatbotRunner;
    private final MetaMessagingApiClient metaMessagingApiClient;
    private final RealtimeBroadcaster realtimeBroadcaster;
    private final ObjectMapper objectMapper;

    public MetaMessagingInboundProcessor(
            ChannelAccountRepository channelAccountRepository,
            ContactRepository contactRepository,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            AutomationTriggerService automationTriggerService,
            AiChatbotRepository aiChatbotRepository,
            ChatbotRunner chatbotRunner,
            MetaMessagingApiClient metaMessagingApiClient,
            RealtimeBroadcaster realtimeBroadcaster,
            ObjectMapper objectMapper) {
        this.channelAccountRepository = channelAccountRepository;
        this.contactRepository = contactRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.automationTriggerService = automationTriggerService;
        this.aiChatbotRepository = aiChatbotRepository;
        this.chatbotRunner = chatbotRunner;
        this.metaMessagingApiClient = metaMessagingApiClient;
        this.realtimeBroadcaster = realtimeBroadcaster;
        this.objectMapper = objectMapper;
    }

    public void processMessenger(Map<String, Object> payload) {
        processEntries(payload, "messenger");
    }

    public void processInstagram(Map<String, Object> payload) {
        processEntries(payload, "instagram");
    }

    @SuppressWarnings("unchecked")
    private void processEntries(Map<String, Object> payload, String channel) {
        List<Map<String, Object>> entries = (List<Map<String, Object>>) payload.getOrDefault("entry", List.of());
        for (Map<String, Object> entry : entries) {
            String pageOrIgId = str(entry.get("id"));
            List<Map<String, Object>> messaging = (List<Map<String, Object>>) entry.getOrDefault("messaging", List.of());
            for (Map<String, Object> event : messaging) {
                try {
                    processInboundEvent(channel, pageOrIgId, event);
                } catch (Exception e) {
                    log.error("{} webhook message processing failed: {}", channel, e.getMessage(), e);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void processInboundEvent(String channel, String pageOrIgId, Map<String, Object> event) {
        Map<String, Object> message = event.get("message") instanceof Map ? (Map<String, Object>) event.get("message") : null;
        if (message == null) {
            return; // postback/read/delivery events — not handled yet
        }
        if (Boolean.TRUE.equals(message.get("is_echo"))) {
            return; // our own outbound message reflected back
        }

        String mid = str(message.get("mid"));
        if (mid != null && messageRepository.findByProviderMessageId(mid).isPresent()) {
            return; // idempotency — Meta redelivers on any non-2xx or timeout
        }

        Map<String, Object> sender = event.get("sender") instanceof Map ? (Map<String, Object>) event.get("sender") : Map.of();
        String senderId = str(sender.get("id"));
        if (senderId == null) return;

        ChannelAccount channelAccount = resolveChannelAccount(channel, pageOrIgId);
        if (channelAccount == null) {
            log.warn("{} inbound dropped — no channel_account for page/ig id={} sender={} mid={}", channel, pageOrIgId, senderId, mid);
            return;
        }

        Long workspaceId = channelAccount.getWorkspaceId();
        String psidKey = psidKey(channel);

        Contact contact = findContactByPsid(workspaceId, psidKey, senderId);
        boolean isNewContact = contact == null;
        if (contact == null) {
            contact = new Contact();
            contact.setWorkspaceId(workspaceId);
            contact.setFirstName("messenger".equals(channel) ? "Messenger User" : "Instagram User");
            contact.setSource(channel + "_inbound");
            contact.setCustomFields(Map.of(psidKey, senderId));
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
            conversation.setExternalThreadId(senderId);
            conversation.setAssignedTo("bot");
            conversation = conversationRepository.save(conversation);
        }

        String body = extractBody(message);

        LocalDateTime sentAt;
        try {
            long ts = Long.parseLong(String.valueOf(event.getOrDefault("timestamp", Instant.now().toEpochMilli())));
            sentAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneOffset.UTC);
        } catch (NumberFormatException e) {
            sentAt = LocalDateTime.now();
        }

        Message msgEntity = new Message();
        msgEntity.setConversationId(conversation.getId());
        msgEntity.setDirection("in");
        msgEntity.setChannel(channel);
        msgEntity.setType(message.get("attachments") != null && message.get("text") == null ? "attachment" : "text");
        msgEntity.setBody(body);
        msgEntity.setStatus("delivered");
        msgEntity.setProviderMessageId(mid);
        msgEntity.setSentBy("customer");
        msgEntity.setSentAt(sentAt);
        try {
            msgEntity.setPayload(objectMapper.writeValueAsString(event));
        } catch (Exception ignored) {
            msgEntity.setPayload(null);
        }
        msgEntity = messageRepository.save(msgEntity);

        conversation.setLastMessageAt(sentAt);
        conversation.setStatus("open");
        conversation.setUnreadCount((conversation.getUnreadCount() != null ? conversation.getUnreadCount() : 0) + 1);
        conversation.setLastInboundAt(sentAt);
        conversationRepository.save(conversation);

        broadcastInbound(workspaceId, conversation, msgEntity, channel);

        Map<String, Object> triggerContext = new LinkedHashMap<>();
        triggerContext.put("message_body", body != null ? body : "");
        triggerContext.put("conversation_id", conversation.getId());
        triggerContext.put("channel", channel);
        try {
            automationTriggerService.fireForContact(workspaceId, "message.received", contact.getId(), triggerContext);
        } catch (Exception e) {
            log.error("Automation trigger failed for contact {}: {}", contact.getId(), e.getMessage());
        }

        try {
            maybeAutoReply(channel, channelAccount, conversation, msgEntity, contact, senderId);
        } catch (Exception e) {
            log.error("AI auto-reply failed for conversation {}: {}", conversation.getId(), e.getMessage(), e);
        }
    }

    private void maybeAutoReply(String channel, ChannelAccount channelAccount, Conversation conversation, Message inboundMessage, Contact contact, String recipientId) {
        if ("human".equalsIgnoreCase(conversation.getAssignedTo())) {
            return;
        }

        String body = inboundMessage.getBody() != null ? inboundMessage.getBody().toLowerCase() : "";
        for (String phrase : HANDOVER_PHRASES) {
            if (body.contains(phrase)) {
                conversation.setAssignedTo("human");
                conversation.setHandoverAt(LocalDateTime.now());
                conversationRepository.save(conversation);
                return;
            }
        }

        Long chatbotId = extractChatbotId(channelAccount.getMetaJson());
        if (chatbotId == null) return;

        AiChatbot bot = aiChatbotRepository.findByWorkspaceIdAndId(channelAccount.getWorkspaceId(), chatbotId).orElse(null);
        if (bot == null || !bot.isEnabled()) return;

        String reply = chatbotRunner.run(bot, inboundMessage);
        if (reply == null || reply.isBlank()) return;

        Message botMessage = new Message();
        botMessage.setConversationId(conversation.getId());
        botMessage.setDirection("out");
        botMessage.setChannel(channel);
        botMessage.setType("text");
        botMessage.setBody(reply);
        botMessage.setStatus("queued");
        botMessage.setSentBy("bot");
        botMessage.setSentAt(LocalDateTime.now());
        botMessage = messageRepository.save(botMessage);

        try {
            String providerMsgId = "messenger".equals(channel)
                    ? metaMessagingApiClient.sendMessengerText(channelAccount, recipientId, reply)
                    : metaMessagingApiClient.sendInstagramText(channelAccount, recipientId, reply);
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

        broadcastOutbound(channelAccount.getWorkspaceId(), conversation, botMessage, channel);
    }

    @SuppressWarnings("unchecked")
    private ChannelAccount resolveChannelAccount(String channel, String pageOrIgId) {
        if (pageOrIgId == null) return null;
        for (ChannelAccount ca : channelAccountRepository.findByChannelAndStatus(channel, "active")) {
            Map<String, Object> meta = parseJson(ca.getMetaJson());
            if ("messenger".equals(channel)) {
                if (pageOrIgId.equals(str(meta.get("page_id")))) return ca;
            } else {
                if (pageOrIgId.equals(str(meta.get("instagram_page_id"))) || pageOrIgId.equals(str(meta.get("instagram_account_id")))) return ca;
            }
        }
        return null;
    }

    private Contact findContactByPsid(Long workspaceId, String psidKey, String psid) {
        for (Contact c : contactRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId)) {
            Map<String, Object> customFields = c.getCustomFields();
            if (customFields != null && psid.equals(str(customFields.get(psidKey)))) return c;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(raw, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String psidKey(String channel) {
        return "messenger".equals(channel) ? "messenger_psid" : "instagram_psid";
    }

    private Long extractChatbotId(String metaJson) {
        Object id = parseJson(metaJson).get("ai_chatbot_id");
        if (id == null) return null;
        try {
            return Long.valueOf(id.toString());
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractBody(Map<String, Object> message) {
        String text = str(message.get("text"));
        if (text != null && !text.isBlank()) return text;

        List<Map<String, Object>> attachments = message.get("attachments") instanceof List ? (List<Map<String, Object>>) message.get("attachments") : List.of();
        if (!attachments.isEmpty()) {
            String type = str(attachments.get(0).get("type"));
            return switch (type != null ? type : "") {
                case "image" -> "🖼 Image";
                case "video" -> "🎬 Video";
                case "audio" -> "🎤 Audio";
                case "file" -> "📄 File";
                default -> "Attachment";
            };
        }
        return "";
    }

    private void broadcastInbound(Long workspaceId, Conversation conversation, Message message, String channel) {
        if (realtimeBroadcaster == null) return;
        Map<String, Object> msgPayload = messagePayload(message, "in", channel);
        realtimeBroadcaster.broadcast("conversation." + conversation.getId(), ".MessageReceived", msgPayload);
        realtimeBroadcaster.broadcast("workspace." + workspaceId, ".MessageReceived", msgPayload);
    }

    private void broadcastOutbound(Long workspaceId, Conversation conversation, Message message, String channel) {
        if (realtimeBroadcaster == null) return;
        Map<String, Object> msgPayload = messagePayload(message, "out", channel);
        realtimeBroadcaster.broadcast("conversation." + conversation.getId(), ".MessageSent", msgPayload);
        realtimeBroadcaster.broadcast("workspace." + workspaceId, ".MessageSent", msgPayload);
    }

    private Map<String, Object> messagePayload(Message message, String direction, String channel) {
        Map<String, Object> msgPayload = new LinkedHashMap<>();
        msgPayload.put("id", message.getId());
        msgPayload.put("conversation_id", message.getConversationId());
        msgPayload.put("direction", direction);
        msgPayload.put("channel", channel);
        msgPayload.put("type", message.getType());
        msgPayload.put("body", message.getBody() != null ? message.getBody() : "");
        msgPayload.put("status", message.getStatus());
        msgPayload.put("created_at", message.getCreatedAt() != null ? message.getCreatedAt().toString() : LocalDateTime.now().toString());
        return msgPayload;
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
