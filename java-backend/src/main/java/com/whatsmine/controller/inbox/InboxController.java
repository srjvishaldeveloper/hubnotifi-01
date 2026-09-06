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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

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
    private com.whatsmine.service.social.MetaMessagingApiClient metaMessagingApiClient;

    @Autowired
    private com.whatsmine.realtime.RealtimeBroadcaster realtimeBroadcaster;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private com.whatsmine.repository.EcommerceProductRepository ecommerceProductRepository;

    /** Reads Contact.customFields for the PSID/IGSID stored by MetaMessagingInboundProcessor. */
    private String contactPsid(Contact contact, String channel) {
        if (contact.getCustomFields() == null) return null;
        Object psid = contact.getCustomFields().get("messenger".equalsIgnoreCase(channel) ? "messenger_psid" : "instagram_psid");
        return psid != null ? psid.toString() : null;
    }

    private String sendOverChannel(ChannelAccount channelAccount, Contact contact, String body) {
        String channel = channelAccount.getChannel();
        if ("whatsapp".equalsIgnoreCase(channel)) {
            return whatsAppApiClient.sendText(channelAccount, contact.getPhoneE164(), body);
        }
        if ("messenger".equalsIgnoreCase(channel) || "instagram".equalsIgnoreCase(channel)) {
            String psid = contactPsid(contact, channel);
            if (psid == null || psid.isBlank()) {
                throw new IllegalStateException("This contact has no " + channel + " conversation id — they must message in first.");
            }
            return "messenger".equalsIgnoreCase(channel)
                    ? metaMessagingApiClient.sendMessengerText(channelAccount, psid, body)
                    : metaMessagingApiClient.sendInstagramText(channelAccount, psid, body);
        }
        throw new IllegalStateException("Unsupported channel: " + channel);
    }

    /** mediaType is image/video/document/audio; exactly one of mediaId or link is used. */
    private String sendMediaOverChannel(ChannelAccount channelAccount, Contact contact, String mediaType, String mediaId, String link, String caption, String filename) {
        String channel = channelAccount.getChannel();
        if ("whatsapp".equalsIgnoreCase(channel)) {
            return whatsAppApiClient.sendMedia(channelAccount, contact.getPhoneE164(), mediaType, link, mediaId, caption, filename);
        }
        throw new IllegalStateException("Sending media over " + channel + " is not yet supported — only WhatsApp media sends are wired up.");
    }

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        return userDetails.getWorkspaceId();
    }

    /**
     * Builds a Laravel-paginator-shaped payload (data/current_page/last_page/total)
     * since Inbox/Index.jsx and Inbox/Show.jsx were written against that shape —
     * a raw Spring Page serializes as {content, pageable, ...}, which left
     * conversations.data undefined and silently broke the page render.
     */
    private Map<String, Object> paginate(Page<Conversation> page) {
        Map<String, Object> out = new HashMap<>();
        out.put("data", page.getContent());
        out.put("current_page", page.getNumber() + 1);
        out.put("last_page", Math.max(page.getTotalPages(), 1));
        out.put("per_page", page.getSize());
        out.put("total", page.getTotalElements());
        return out;
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
        props.put("conversations", paginate(conversations));
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
        props.put("conversations", paginate(conversations));
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
                String msgId = sendOverChannel(channelAccount, contact, initialMsg);
                msg.setStatus("sent");
                msg.setProviderMessageId(msgId);
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

    @PostMapping(value = "/conversations/{uuid}/reply", consumes = MediaType.APPLICATION_JSON_VALUE)
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

        return finishReply(request, userDetails, conversation, type, body, null);
    }

    /**
     * Attachment path of PHP's InboxController::reply() — the same reply()
     * route, but the frontend posts multipart/form-data whenever the compose
     * bar has a file attached, so Spring routes it here by content type
     * instead of trying to bind one method to both shapes.
     */
    @PostMapping(value = "/conversations/{uuid}/reply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object replyWithAttachment(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestParam(value = "body", required = false) String body,
            @RequestParam(value = "type", required = false, defaultValue = "text") String type,
            @RequestParam("attachment") MultipartFile attachment
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Conversation conversation = conversationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        ChannelAccount sendChannelAccount = conversation.getChannelAccountId() != null
                ? channelAccountRepository.findById(conversation.getChannelAccountId()).orElseThrow()
                : conversation.getChannelAccount();
        if (sendChannelAccount == null || !"whatsapp".equalsIgnoreCase(sendChannelAccount.getChannel())) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "No active WhatsApp account."));
        }

        String mimeType = attachment.getContentType() != null ? attachment.getContentType() : "application/octet-stream";
        if ("text".equalsIgnoreCase(type)) {
            type = mimeType.startsWith("image/") ? "image" : mimeType.startsWith("video/") ? "video" : "document";
        }

        Map<String, Object> msgPayload;
        try {
            byte[] bytes = attachment.getBytes();
            String mediaId = whatsAppApiClient.uploadMedia(sendChannelAccount, bytes, attachment.getOriginalFilename(), mimeType);

            String storedName = UUID.randomUUID() + extensionFor(attachment.getOriginalFilename());
            Path targetPath = Paths.get("storage/app/public/message-media", storedName).toAbsolutePath();
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, bytes);
            String previewUrl = "/storage/message-media/" + storedName;

            msgPayload = new LinkedHashMap<>();
            msgPayload.put("media_id", mediaId);
            msgPayload.put("preview_url", previewUrl);
            msgPayload.put("caption", body);
            msgPayload.put("filename", attachment.getOriginalFilename());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
        }

        String effectiveBody = body != null && !body.isBlank() ? body : attachment.getOriginalFilename();
        return finishReply(request, userDetails, conversation, type, effectiveBody, msgPayload);
    }

    private String extensionFor(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    /** Shared tail of both reply() overloads: save, send over the real channel driver, broadcast, respond. */
    private Object finishReply(HttpServletRequest request, CustomUserDetails userDetails, Conversation conversation,
                                String type, String body, Map<String, Object> msgPayload) {
        Long workspaceId = getWorkspaceId(userDetails);

        Message msg = new Message();
        msg.setConversationId(conversation.getId());
        msg.setDirection("out");
        msg.setChannel(conversation.getChannelAccount() != null ? conversation.getChannelAccount().getChannel() : "whatsapp");
        msg.setType(type);
        msg.setBody(body);
        if (msgPayload != null) {
            try {
                msg.setPayload(objectMapper.writeValueAsString(msgPayload));
            } catch (Exception ignored) { }
        }
        msg.setStatus("queued");
        msg.setSentBy("human");
        msg.setUserId(userDetails.getId());
        msg.setSentAt(LocalDateTime.now());
        msg = messageRepository.save(msg);

        String sendError = null;
        try {
            Contact contact = contactRepository.findById(conversation.getContactId()).orElseThrow();
            ChannelAccount sendChannelAccount = conversation.getChannelAccountId() != null
                    ? channelAccountRepository.findById(conversation.getChannelAccountId()).orElseThrow()
                    : conversation.getChannelAccount();

            String providerMsgId = msgPayload != null
                    ? sendMediaOverChannel(sendChannelAccount, contact, type,
                            String.valueOf(msgPayload.get("media_id")), null, (String) msgPayload.get("caption"), (String) msgPayload.get("filename"))
                    : sendOverChannel(sendChannelAccount, contact, body);
            msg.setStatus("sent");
            msg.setProviderMessageId(providerMsgId);
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
            Map<String, Object> broadcastPayload = Map.of(
                    "id", msg.getId(),
                    "conversation_id", msg.getConversationId(),
                    "direction", msg.getDirection() != null ? msg.getDirection() : "outbound",
                    "channel", msg.getChannel() != null ? msg.getChannel() : "whatsapp",
                    "type", msg.getType() != null ? msg.getType() : "text",
                    "body", msg.getBody() != null ? msg.getBody() : "",
                    "status", msg.getStatus() != null ? msg.getStatus() : "sent",
                    "created_at", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : LocalDateTime.now().toString()
            );
            realtimeBroadcaster.broadcast("conversation." + conversation.getId(), ".MessageSent", broadcastPayload);
            realtimeBroadcaster.broadcast("workspace." + workspaceId, ".MessageSent", broadcastPayload);
        }

        if ("application/json".equalsIgnoreCase(request.getHeader("Accept"))) {
            return ResponseEntity.ok(Map.of("message", msg, "error", sendError != null ? sendError : ""));
        }

        return Inertia.redirect("/app/inbox/conversations/" + conversation.getUuid());
    }

    /** Upload a media file to WhatsApp and return the media_id, matching PHP's standalone uploadMedia() (used by the template/media pickers). */
    @PostMapping("/conversations/{uuid}/upload-media")
    public ResponseEntity<Map<String, Object>> uploadMedia(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestParam("file") MultipartFile file
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Conversation conversation = conversationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        ChannelAccount channelAccount = conversation.getChannelAccountId() != null
                ? channelAccountRepository.findById(conversation.getChannelAccountId()).orElse(null)
                : conversation.getChannelAccount();
        if (channelAccount == null || !"whatsapp".equalsIgnoreCase(channelAccount.getChannel())) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "No active WhatsApp account."));
        }

        String mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        try {
            byte[] bytes = file.getBytes();
            String mediaId = whatsAppApiClient.uploadMedia(channelAccount, bytes, file.getOriginalFilename(), mimeType);

            String storedName = UUID.randomUUID() + extensionFor(file.getOriginalFilename());
            Path targetPath = Paths.get("storage/app/public/template-media", storedName).toAbsolutePath();
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, bytes);
            String previewUrl = "/storage/template-media/" + storedName;

            return ResponseEntity.ok(Map.of("media_id", mediaId, "mime_type", mimeType, "preview_url", previewUrl));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Upload failed."));
        }
    }

    /**
     * Proxy/lazy-download inbound WhatsApp media: serves a cached local copy
     * if one exists, otherwise resolves the media id from the raw webhook
     * payload, downloads it from Meta, caches it, and redirects to it.
     */
    @GetMapping("/conversations/{uuid}/messages/{messageId}/media")
    @SuppressWarnings("unchecked")
    public Object serveMedia(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @PathVariable Long messageId
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Conversation conversation = conversationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!message.getConversationId().equals(conversation.getId())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        Map<String, Object> payload;
        try {
            payload = message.getPayload() != null ? objectMapper.readValue(message.getPayload(), Map.class) : new LinkedHashMap<>();
        } catch (Exception e) {
            payload = new LinkedHashMap<>();
        }

        Object cachedPreview = payload.get("preview_url");
        if (cachedPreview != null) {
            String relative = String.valueOf(cachedPreview).replaceFirst("^/storage/", "");
            Path cachedPath = Paths.get("storage/app/public", relative).toAbsolutePath();
            if (Files.exists(cachedPath)) {
                return Inertia.redirect(String.valueOf(cachedPreview));
            }
            payload.put("preview_url", null);
        }

        String type = message.getType() != null ? message.getType() : "image";
        Object typeObj = payload.get(type);
        Object mediaIdObj = typeObj instanceof Map ? ((Map<String, Object>) typeObj).get("id") : payload.get("media_id");
        if (mediaIdObj == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "No media available.");
        }

        ChannelAccount channelAccount = conversation.getChannelAccountId() != null
                ? channelAccountRepository.findById(conversation.getChannelAccountId()).orElse(null)
                : conversation.getChannelAccount();
        if (channelAccount == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "WhatsApp account not configured.");
        }

        try {
            Map<String, String> resolved = whatsAppApiClient.getMediaUrl(channelAccount, String.valueOf(mediaIdObj));
            byte[] bytes = whatsAppApiClient.downloadMedia(channelAccount, resolved.get("url"));
            String mimeType = resolved.get("mime_type");
            String ext = mimeType != null && mimeType.contains("/") ? mimeType.substring(mimeType.indexOf('/') + 1) : "bin";
            if ("jpeg".equals(ext)) ext = "jpg";

            String storedName = message.getId() + "." + ext;
            Path targetPath = Paths.get("storage/app/public/message-media", storedName).toAbsolutePath();
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, bytes);
            String previewUrl = "/storage/message-media/" + storedName;

            payload.put("preview_url", previewUrl);
            payload.put("mime_type", mimeType);
            message.setPayload(objectMapper.writeValueAsString(payload));
            messageRepository.save(message);

            return Inertia.redirect(previewUrl);
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not fetch media: " + e.getMessage());
        }
    }

    /**
     * Shares a connected-store product into the conversation as a rich image
     * card (or a plain text card when the product has no photo) — ports
     * PHP's InboxController::shareProduct().
     */
    @PostMapping("/conversations/{uuid}/share-product")
    public ResponseEntity<Map<String, Object>> shareProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestBody Map<String, Object> body
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Conversation conversation = conversationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        Object productIdObj = body.get("product_id");
        if (productIdObj == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "product_id is required."));
        }
        com.whatsmine.model.EcommerceProduct product = ecommerceProductRepository.findByIdAndWorkspaceId(
                Long.valueOf(productIdObj.toString()), workspaceId).orElse(null);
        if (product == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
        }

        String channel = conversation.getChannelAccount() != null ? conversation.getChannelAccount().getChannel() : "whatsapp";
        if ("whatsapp".equalsIgnoreCase(channel) && !conversation.checkWhatsappWindowOpen()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "WhatsApp 24-hour session is closed. Use an approved template to re-engage this contact."));
        }

        String currency = "";
        if (product.getStore() != null && product.getStore().getExternalMeta() != null) {
            Object c = product.getStore().getExternalMeta().get("currency");
            if (c != null) currency = String.valueOf(c);
        }
        String url = productShareUrl(product);
        String caption = formatProductMessage(product, currency, url, "whatsapp".equalsIgnoreCase(channel));
        String image = product.getImageUrl();
        boolean useImage = image != null && !image.isBlank();

        Message msg = new Message();
        msg.setConversationId(conversation.getId());
        msg.setDirection("out");
        msg.setChannel(channel);
        msg.setType(useImage ? "image" : "text");
        msg.setBody(caption);
        if (useImage) {
            try {
                msg.setPayload(objectMapper.writeValueAsString(Map.of("link", image, "preview_url", image, "caption", caption)));
            } catch (Exception ignored) { }
        }
        msg.setStatus("queued");
        msg.setSentBy("human");
        msg.setUserId(userDetails.getId());
        msg.setSentAt(LocalDateTime.now());
        msg = messageRepository.save(msg);

        String sendError = null;
        try {
            Contact contact = contactRepository.findById(conversation.getContactId()).orElseThrow();
            ChannelAccount sendChannelAccount = conversation.getChannelAccountId() != null
                    ? channelAccountRepository.findById(conversation.getChannelAccountId()).orElseThrow()
                    : conversation.getChannelAccount();
            String providerMsgId = useImage
                    ? sendMediaOverChannel(sendChannelAccount, contact, "image", null, image, caption, null)
                    : sendOverChannel(sendChannelAccount, contact, caption);
            msg.setStatus("sent");
            msg.setProviderMessageId(providerMsgId);
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

        return ResponseEntity.ok(Map.of("message", msg, "error", sendError != null ? sendError : ""));
    }

    private String formatProductMessage(com.whatsmine.model.EcommerceProduct product, String currency, String url, boolean bold) {
        String name = product.getName() != null ? product.getName().trim() : "";
        List<String> lines = new ArrayList<>();
        lines.add(bold ? "🛍️ *" + name + "*" : "🛍️ " + name);

        if (product.getPrice() != null) {
            String price = product.getPrice().stripTrailingZeros().toPlainString();
            lines.add("Price: " + currencyPrefix(currency) + price);
        }
        if (product.getSku() != null && !product.getSku().isBlank()) {
            lines.add("SKU: " + product.getSku());
        }
        if (url != null && !url.isBlank()) {
            lines.add(url);
        }
        return String.join("\n", lines);
    }

    private String currencyPrefix(String currency) {
        if (currency == null || currency.isBlank()) return "";
        String c = currency.trim().toUpperCase();
        Map<String, String> symbols = Map.of(
                "USD", "$", "EUR", "€", "GBP", "£", "JPY", "¥", "INR", "₹",
                "AUD", "A$", "CAD", "C$", "NZD", "NZ$", "BRL", "R$");
        return symbols.getOrDefault(c, c + " ");
    }

    @SuppressWarnings("unchecked")
    private String productShareUrl(com.whatsmine.model.EcommerceProduct product) {
        Map<String, Object> raw = product.getRaw();
        if (raw == null) return null;
        String domain = product.getStore() != null ? product.getStore().getDomain() : null;

        if ("shopify".equalsIgnoreCase(product.getPlatform())) {
            Object onlineUrl = raw.get("online_store_url");
            if (onlineUrl != null) return String.valueOf(onlineUrl);
            Object handle = raw.get("handle");
            if (handle != null && !String.valueOf(handle).isBlank() && domain != null) {
                return "https://" + domain + "/products/" + handle;
            }
            return null;
        }
        if ("woocommerce".equalsIgnoreCase(product.getPlatform())) {
            Object permalink = raw.get("permalink");
            if (permalink != null && String.valueOf(permalink).startsWith("http")) {
                return String.valueOf(permalink);
            }
            return null;
        }
        return null;
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
