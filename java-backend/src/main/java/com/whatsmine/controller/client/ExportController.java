package com.whatsmine.controller.client;

import com.whatsmine.model.AiChatbot;
import com.whatsmine.model.AiRun;
import com.whatsmine.model.AuditLog;
import com.whatsmine.model.Campaign;
import com.whatsmine.model.CampaignRecipient;
import com.whatsmine.model.ChannelAccount;
import com.whatsmine.model.Contact;
import com.whatsmine.model.ContactTag;
import com.whatsmine.model.ContactTagPivot;
import com.whatsmine.model.Conversation;
import com.whatsmine.repository.AiChatbotRepository;
import com.whatsmine.repository.AiRunRepository;
import com.whatsmine.repository.AuditLogRepository;
import com.whatsmine.repository.CampaignRecipientRepository;
import com.whatsmine.repository.CampaignRepository;
import com.whatsmine.repository.ChannelAccountRepository;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.ContactTagPivotRepository;
import com.whatsmine.repository.ContactTagRepository;
import com.whatsmine.repository.ConversationRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Ports PHP's Client\Reports\ExportController exactly: 5 CSV export
 * endpoints for a workspace's contacts, a campaign's recipients,
 * conversations, AI runs, and (client-admin only) the audit log.
 */
@RestController
@RequestMapping("/reports/exports")
public class ExportController {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ContactRepository contactRepository;
    private final ContactTagRepository contactTagRepository;
    private final ContactTagPivotRepository contactTagPivotRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final ConversationRepository conversationRepository;
    private final ChannelAccountRepository channelAccountRepository;
    private final AiRunRepository aiRunRepository;
    private final AiChatbotRepository aiChatbotRepository;
    private final AuditLogRepository auditLogRepository;

    public ExportController(
            ContactRepository contactRepository,
            ContactTagRepository contactTagRepository,
            ContactTagPivotRepository contactTagPivotRepository,
            CampaignRepository campaignRepository,
            CampaignRecipientRepository campaignRecipientRepository,
            ConversationRepository conversationRepository,
            ChannelAccountRepository channelAccountRepository,
            AiRunRepository aiRunRepository,
            AiChatbotRepository aiChatbotRepository,
            AuditLogRepository auditLogRepository) {
        this.contactRepository = contactRepository;
        this.contactTagRepository = contactTagRepository;
        this.contactTagPivotRepository = contactTagPivotRepository;
        this.campaignRepository = campaignRepository;
        this.campaignRecipientRepository = campaignRecipientRepository;
        this.conversationRepository = conversationRepository;
        this.channelAccountRepository = channelAccountRepository;
        this.aiRunRepository = aiRunRepository;
        this.aiChatbotRepository = aiChatbotRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/contacts")
    public void contacts(@AuthenticationPrincipal CustomUserDetails userDetails, HttpServletResponse response) throws IOException {
        Long workspaceId = userDetails.getWorkspaceId();
        stream(response, "contacts.csv", writer -> {
            writer.println(row("ID", "First Name", "Last Name", "Phone", "Email", "Tags", "Created At"));
            for (Contact c : contactRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId)) {
                List<Long> tagIds = contactTagPivotRepository.findByContactId(c.getId()).stream().map(ContactTagPivot::getTagId).toList();
                String tags = tagIds.isEmpty() ? "" : String.join(", ", contactTagRepository.findAllById(tagIds).stream().map(ContactTag::getName).toList());
                writer.println(row(
                        str(c.getId()), c.getFirstName(), c.getLastName(), c.getPhoneE164(), c.getEmail(), tags,
                        c.getCreatedAt() != null ? c.getCreatedAt().format(ISO) : ""));
            }
        });
    }

    @GetMapping("/campaign-recipients/{uuid}")
    public void campaignRecipients(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String uuid, HttpServletResponse response) throws IOException {
        Campaign campaign = campaignRepository.findByWorkspaceIdAndUuid(userDetails.getWorkspaceId(), uuid)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));

        stream(response, "campaign-" + campaign.getId() + "-recipients.csv", writer -> {
            writer.println(row("Recipient ID", "Contact ID", "Status", "Sent At", "Delivered At", "Read At", "Failed Reason"));
            for (CampaignRecipient r : campaignRecipientRepository.findByCampaignId(campaign.getId())) {
                writer.println(row(
                        str(r.getId()), str(r.getContactId()), r.getStatus(),
                        r.getSentAt() != null ? r.getSentAt().format(ISO) : "",
                        r.getDeliveredAt() != null ? r.getDeliveredAt().format(ISO) : "",
                        r.getReadAt() != null ? r.getReadAt().format(ISO) : "",
                        r.getFailedReason()));
            }
        });
    }

    @GetMapping("/conversations")
    public void conversations(@AuthenticationPrincipal CustomUserDetails userDetails, HttpServletResponse response) throws IOException {
        Long workspaceId = userDetails.getWorkspaceId();
        stream(response, "conversations.csv", writer -> {
            writer.println(row("ID", "Channel", "Contact ID", "Status", "Assigned User ID", "Last Message At", "Created At"));
            for (Conversation c : conversationRepository.findByWorkspaceIdOrderByLastMessageAtDesc(workspaceId)) {
                String channel = "";
                if (c.getChannelAccountId() != null) {
                    ChannelAccount ca = channelAccountRepository.findById(c.getChannelAccountId()).orElse(null);
                    channel = ca != null ? ca.getChannel() : "";
                }
                writer.println(row(
                        str(c.getId()), channel, str(c.getContactId()), c.getStatus(), str(c.getAssignedUserId()),
                        c.getLastMessageAt() != null ? c.getLastMessageAt().format(ISO) : "",
                        c.getCreatedAt() != null ? c.getCreatedAt().format(ISO) : ""));
            }
        });
    }

    @GetMapping("/ai-runs")
    public void aiRuns(@AuthenticationPrincipal CustomUserDetails userDetails, HttpServletResponse response) throws IOException {
        Long workspaceId = userDetails.getWorkspaceId();
        List<Long> chatbotIds = aiChatbotRepository.findByWorkspaceIdOrderByIdDesc(workspaceId).stream().map(AiChatbot::getId).toList();
        List<AiRun> runs = chatbotIds.isEmpty() ? List.of() : aiRunRepository.findByChatbotIdIn(chatbotIds);

        stream(response, "ai-runs.csv", writer -> {
            writer.println(row("ID", "Chatbot ID", "Model", "Prompt Tokens", "Completion Tokens", "Cost Cents", "Latency ms", "Status", "Created At"));
            for (AiRun r : runs) {
                writer.println(row(
                        str(r.getId()), str(r.getChatbotId()), r.getModel(),
                        str(r.getPromptTokens()), str(r.getCompletionTokens()), str(r.getCostCents()), str(r.getLatencyMs()),
                        r.getStatus(), r.getCreatedAt() != null ? r.getCreatedAt().format(ISO) : ""));
            }
        });
    }

    @GetMapping("/audit-log")
    public void auditLog(@AuthenticationPrincipal CustomUserDetails userDetails, HttpServletResponse response) throws IOException {
        if (!"ADMINISTRATOR".equalsIgnoreCase(userDetails.getClientRole())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        }
        Long workspaceId = userDetails.getWorkspaceId();
        List<AuditLog> rows = auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, PageRequest.of(0, 10000, Sort.by(Sort.Direction.ASC, "id"))).getContent();

        stream(response, "audit-log.csv", writer -> {
            writer.println(row("ID", "Actor User ID", "Workspace ID", "Event", "Auditable Type", "Auditable ID", "IP", "Created At"));
            for (AuditLog r : rows) {
                writer.println(row(
                        str(r.getId()), str(r.getUserId()), str(r.getWorkspaceId()), r.getEvent(),
                        r.getAuditableType(), str(r.getAuditableId()), r.getIpAddress(),
                        r.getCreatedAt() != null ? r.getCreatedAt().format(ISO) : ""));
            }
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private interface CsvBody {
        void write(PrintWriter writer);
    }

    private void stream(HttpServletResponse response, String filename, CsvBody body) throws IOException {
        response.setContentType(MediaType.TEXT_PLAIN_VALUE + "; charset=UTF-8");
        response.setHeader("Content-Type", "text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setHeader("X-Accel-Buffering", "no");
        try (PrintWriter writer = response.getWriter()) {
            body.write(writer);
        }
    }

    private String row(String... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(values[i] != null ? values[i].replace("\"", "\"\"") : "").append('"');
        }
        return sb.toString();
    }

    private String str(Object o) {
        return o != null ? o.toString() : "";
    }
}
