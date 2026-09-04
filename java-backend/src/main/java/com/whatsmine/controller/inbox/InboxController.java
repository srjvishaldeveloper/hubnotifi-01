package com.whatsmine.controller.inbox;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaRenderer;

import com.whatsmine.model.ChannelAccount;
import com.whatsmine.model.Contact;
import com.whatsmine.model.Conversation;
import com.whatsmine.model.InboxLabel;
import com.whatsmine.model.Message;
import com.whatsmine.model.User;
import com.whatsmine.model.WhatsappTemplate;

import com.whatsmine.repository.ChannelAccountRepository;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.ConversationRepository;
import com.whatsmine.repository.InboxLabelRepository;
import com.whatsmine.repository.MessageRepository;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.repository.WhatsappTemplateRepository;

import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.whatsapp.WhatsAppApiClient;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

import java.util.*;

@RestController
@RequestMapping("/app/inbox")
public class InboxController {

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private ChannelAccountRepository channelAccountRepository;

    @Autowired
    private InboxLabelRepository inboxLabelRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WhatsappTemplateRepository whatsappTemplateRepository;

    @Autowired
    private WhatsAppApiClient whatsAppApiClient;

    @Autowired
    private com.whatsmine.realtime.RealtimeBroadcaster realtimeBroadcaster;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        return userDetails.getWorkspaceId();
    }

    @GetMapping
    public Object index(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String folder,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Long label,
            @RequestParam(name = "account_id", required = false) Long accountId
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Long userId = userDetails.getId();

        Page<Conversation> conversations = conversationRepository.findFilteredConversations(
                workspaceId, userId, folder, PageRequest.of(0, 30)
        );

        for (Conversation conv : conversations.getContent()) {
            Optional<Message> lastMsg = messageRepository.findFirstByConversationIdOrderBySentAtDesc(conv.getId());
            lastMsg.ifPresent(conv::setLastMessage);
        }

        List<InboxLabel> labels = inboxLabelRepository.findByWorkspaceIdOrderByNameAsc(workspaceId);
        List<ChannelAccount> channelAccounts = channelAccountRepository.findByWorkspaceIdAndStatus(workspaceId, "active");

        Map<String, Object> filters = new HashMap<>();
        filters.put("folder", folder);
        filters.put("channel", channel);
        filters.put("label", label);
        filters.put("account_id", accountId);

        Map<String, Object> props = new HashMap<>();
        props.put("conversations", conversations);
        props.put("filters", filters);
        props.put("labels", labels);
        props.put("channelAccounts", channelAccounts);

        return inertiaRenderer.render("Inbox/Index", props, request);
    }

    @GetMapping("/conversations/{uuid}")
    public Object show(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestParam(required = false) String folder,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Long label,
            @RequestParam(name = "account_id", required = false) Long accountId
    ) {
        Long workspaceId = getWorkspaceId(userDetails);

        Conversation conversation = conversationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        conversation.setUnreadCount(0);
        conversationRepository.save(conversation);

        conversation.setIsWhatsappWindowOpen(conversation.checkWhatsappWindowOpen());

        List<Message> messages = messageRepository.findByConversationIdOrderBySentAtAsc(conversation.getId());
        List<InboxLabel> allLabels = inboxLabelRepository.findByWorkspaceIdOrderByNameAsc(workspaceId);

        List<Map<String, Object>> teamMembers = new ArrayList<>();
        for (User u : userRepository.findByWorkspaceId(workspaceId)) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("name", u.getName());
            m.put("email", u.getEmail());
            teamMembers.add(m);
        }

        List<WhatsappTemplate> whatsappTemplates = whatsappTemplateRepository.findByWorkspaceIdAndStatusOrderByNameAsc(workspaceId, "APPROVED");
        List<ChannelAccount> channelAccounts = channelAccountRepository.findByWorkspaceIdAndStatus(workspaceId, "active");

        Page<Conversation> conversations = conversationRepository.findFilteredConversations(
                workspaceId, userDetails.getId(), folder, PageRequest.of(0, 30)
        );

        Map<String, Object> filters = new HashMap<>();
        filters.put("folder", folder);
        filters.put("channel", channel);
        filters.put("label", label);
        filters.put("account_id", accountId);

        Map<String, Object> props = new HashMap<>();
        props.put("conversation", conversation);
        props.put("messages", messages);
        props.put("allLabels", allLabels);
        props.put("conversations", conversations);
        props.put("filters", filters);
        props.put("teamMembers", teamMembers);
        props.put("whatsappTemplates", whatsappTemplates);
        props.put("channelAccounts", channelAccounts);
        props.put("hasEcommerceStore", false);

        return inertiaRenderer.render("Inbox/Show", props, request);
    }

    @PostMapping("/conversations/start")
    public Object startConversation(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> body
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Long contactId = Long.valueOf(body.get("contact_id").toString());
        Long channelAccountId = Long.valueOf(body.get("channel_account_id").toString());
        String initialMsg = (String) body.get("body");

        Contact contact = contactRepository.findById(contactId)
                .filter(c -> c.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        ChannelAccount channelAccount = channelAccountRepository.findById(channelAccountId)
                .filter(c -> c.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        Conversation conversation = conversationRepository
                .findFirstByWorkspaceIdAndContactIdAndChannelAccountIdAndStatusOrderByCreatedAtDesc(
                        workspaceId, contact.getId(), channelAccount.getId(), "open"
                )
                .orElseGet(() -> {
                    Conversation c = new Conversation();
                    c.setWorkspaceId(workspaceId);
                    c.setContactId(contact.getId());
                    c.setChannelAccountId(channelAccount.getId());
                    c.setStatus("open");
                    c.setAssignedTo("human");
                    c.setAssignedUserId(userDetails.getId());
                    c.setLastMessageAt(LocalDateTime.now());
                    return conversationRepository.save(c);
                });

        if (initialMsg != null && !initialMsg.isBlank()) {
            Message msg = new Message();
            msg.setConversationId(conversation.getId());
            msg.setDirection("out");
            msg.setChannel(channelAccount.getChannel());
            msg.setType("text");
            msg.setBody(initialMsg);
            msg.setStatus("queued");
            msg.setSentBy("human");
            msg.setUserId(userDetails.getId());
            msg.setSentAt(LocalDateTime.now());
            msg = messageRepository.save(msg);

            try {
                if ("whatsapp".equalsIgnoreCase(channelAccount.getChannel())) {
                    String msgId = whatsAppApiClient.sendTextMessage(contact.getPhoneE164(), initialMsg);
                    msg.setStatus("sent");
                    msg.setProviderMessageId(msgId);
                }
            } catch (Exception e) {
                msg.setStatus("failed");
                msg.setErrorJson("{\"message\":\"" + e.getMessage() + "\"}");
            }
            messageRepository.save(msg);

            conversation.setLastMessageAt(LocalDateTime.now());
            conversationRepository.save(conversation);
        }

        return Inertia.redirect("/app/inbox/conversations/" + conversation.getUuid());
    }

    @PostMapping("/conversations/{uuid}/reply")
    public Object reply(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestBody Map<String, Object> bodyPayload
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Conversation conversation = conversationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        String body = (String) bodyPayload.get("body");
        String type = bodyPayload.containsKey("type") ? (String) bodyPayload.get("type") : "text";

        if ("text".equalsIgnoreCase(type) && (body == null || body.isBlank())) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("errors", Map.of("body", "Message body is required.")));
        }

        Message msg = new Message();
        msg.setConversationId(conversation.getId());
        msg.setDirection("out");
        msg.setChannel(conversation.getChannelAccount() != null ? conversation.getChannelAccount().getChannel() : "whatsapp");
        msg.setType(type);
        msg.setBody(body);
        msg.setStatus("queued");
        msg.setSentBy("human");
        msg.setUserId(userDetails.getId());
        msg.setSentAt(LocalDateTime.now());
        msg = messageRepository.save(msg);

        String sendError = null;
        try {
            if ("whatsapp".equalsIgnoreCase(msg.getChannel())) {
                Contact contact = contactRepository.findById(conversation.getContactId()).orElseThrow();
                String providerMsgId = whatsAppApiClient.sendTextMessage(contact.getPhoneE164(), body);
                msg.setStatus("sent");
                msg.setProviderMessageId(providerMsgId);
            }
        } catch (Exception e) {
            sendError = e.getMessage();
            msg.setStatus("failed");
            msg.setErrorJson("{\"message\":\"" + sendError + "\"}");
        }
        messageRepository.save(msg);

        conversation.setLastMessageAt(LocalDateTime.now());
        if (conversation.getLastInboundAt() != null && conversation.getFirstResponseAt() == null) {
            conversation.setFirstResponseAt(LocalDateTime.now());
        }
        conversationRepository.save(conversation);

        if (realtimeBroadcaster != null) {
            Map<String, Object> msgPayload = Map.of(
                    "id", msg.getId(),
                    "conversation_id", msg.getConversationId(),
                    "direction", msg.getDirection() != null ? msg.getDirection() : "outbound",
                    "channel", msg.getChannel() != null ? msg.getChannel() : "whatsapp",
                    "type", msg.getType() != null ? msg.getType() : "text",
                    "body", msg.getBody() != null ? msg.getBody() : "",
                    "status", msg.getStatus() != null ? msg.getStatus() : "sent",
                    "created_at", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : LocalDateTime.now().toString()
            );
            realtimeBroadcaster.broadcast("conversation." + conversation.getId(), ".MessageSent", msgPayload);
            realtimeBroadcaster.broadcast("workspace." + workspaceId, ".MessageSent", msgPayload);
        }

        if ("application/json".equalsIgnoreCase(request.getHeader("Accept"))) {
            return ResponseEntity.ok(Map.of("message", msg, "error", sendError != null ? sendError : ""));
        }

        return Inertia.redirect("/app/inbox/conversations/" + conversation.getUuid());
    }

    @PostMapping("/conversations/{uuid}/assign")
    public Object assign(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestBody Map<String, Object> body
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Conversation conversation = conversationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        Long userId = body.get("user_id") != null ? Long.valueOf(body.get("user_id").toString()) : null;
        String userName = "Agent";
        if (userId != null) {
            var assignedUserOpt = userRepository.findById(userId)
                    .filter(u -> u.getWorkspaceId().equals(workspaceId));
            if (assignedUserOpt.isEmpty()) {
                throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
            }
            userName = assignedUserOpt.get().getName() != null ? assignedUserOpt.get().getName() : assignedUserOpt.get().getEmail();
        }

        conversation.setAssignedUserId(userId);
        conversationRepository.save(conversation);

        if (realtimeBroadcaster != null) {
            Map<String, Object> assignedPayload = Map.of(
                    "conversation_id", conversation.getId(),
                    "assigned_to", userId != null ? Map.of("id", userId, "name", userName) : null
            );
            realtimeBroadcaster.broadcast("workspace." + workspaceId, ".ConversationAssigned", assignedPayload);
            realtimeBroadcaster.broadcast("conversation." + conversation.getId(), ".ConversationAssigned", assignedPayload);
        }

        return Inertia.redirect("/app/inbox/conversations/" + conversation.getUuid());
    }

    @PostMapping("/conversations/{uuid}/status")
    public Object updateStatus(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestBody Map<String, String> body
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Conversation conversation = conversationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        String status = body.get("status");
        if (status == null || !List.of("open", "pending", "resolved", "snoozed").contains(status)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid status");
        }

        conversation.setStatus(status);
        if ("resolved".equalsIgnoreCase(status) && conversation.getResolvedAt() == null) {
            conversation.setResolvedAt(LocalDateTime.now());
        }
        conversationRepository.save(conversation);

        return Inertia.redirect("/app/inbox/conversations/" + conversation.getUuid());
    }

    @PostMapping("/conversations/{uuid}/handover")
    public ResponseEntity<Map<String, Object>> handover(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestBody Map<String, String> body
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Conversation conversation = conversationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        String mode = body.getOrDefault("mode", "human");
        conversation.setAssignedTo(mode);
        if ("human".equalsIgnoreCase(mode) && conversation.getHandoverAt() == null) {
            conversation.setHandoverAt(LocalDateTime.now());
        }
        conversationRepository.save(conversation);

        return ResponseEntity.ok(Map.of("ok", true, "assigned_to", mode));
    }

    @PostMapping("/conversations/{uuid}/typing")
    public ResponseEntity<Map<String, Boolean>> typing(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestBody(required = false) Map<String, Object> bodyPayload
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Conversation conversation = conversationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        boolean isTyping = bodyPayload != null && Boolean.TRUE.equals(bodyPayload.get("is_typing"));

        if (realtimeBroadcaster != null) {
            String uName = userDetails.getUser() != null && userDetails.getUser().getName() != null
                    ? userDetails.getUser().getName()
                    : userDetails.getUsername();
            Map<String, Object> typingPayload = Map.of(
                    "user_id", userDetails.getId(),
                    "user_name", uName,
                    "is_typing", isTyping
            );
            realtimeBroadcaster.broadcast("conversation." + conversation.getId(), ".TypingChanged", typingPayload);
        }

        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/contacts/search")
    public ResponseEntity<List<Contact>> contactSearch(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(name = "q", defaultValue = "") String q
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<Contact> contacts = q.isBlank()
                ? contactRepository.findTop30ByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                : contactRepository.searchContacts(workspaceId, q);

        return ResponseEntity.ok(contacts);
    }

    @GetMapping("/channel-accounts")
    public ResponseEntity<List<ChannelAccount>> channelAccounts(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<ChannelAccount> accounts = channelAccountRepository.findByWorkspaceIdAndStatus(workspaceId, "active");
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/templates")
    public ResponseEntity<List<WhatsappTemplate>> templates(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<WhatsappTemplate> list = whatsappTemplateRepository.findByWorkspaceIdAndStatusOrderByNameAsc(workspaceId, "APPROVED");
        return ResponseEntity.ok(list);
    }
}
