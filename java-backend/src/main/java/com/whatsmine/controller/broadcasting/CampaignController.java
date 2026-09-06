package com.whatsmine.controller.broadcasting;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaRenderer;

import com.whatsmine.model.Campaign;
import com.whatsmine.model.CampaignRecipient;
import com.whatsmine.model.Contact;
import com.whatsmine.model.ContactTag;
import com.whatsmine.model.Segment;
import com.whatsmine.model.WhatsappBusinessAccount;
import com.whatsmine.model.WhatsappPhoneNumber;
import com.whatsmine.model.WhatsappTemplate;

import com.whatsmine.repository.CampaignRecipientRepository;
import com.whatsmine.repository.CampaignRepository;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.ContactTagRepository;
import com.whatsmine.repository.SegmentRepository;
import com.whatsmine.repository.WhatsappBusinessAccountRepository;
import com.whatsmine.repository.WhatsappPhoneNumberRepository;
import com.whatsmine.repository.WhatsappTemplateRepository;

import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.broadcasting.CampaignPersonalizer;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/app/broadcasts")
public class CampaignController {

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CampaignRecipientRepository campaignRecipientRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private SegmentRepository segmentRepository;

    @Autowired
    private ContactTagRepository contactTagRepository;

    @Autowired
    private WhatsappTemplateRepository whatsappTemplateRepository;

    @Autowired
    private WhatsappBusinessAccountRepository whatsappBusinessAccountRepository;

    @Autowired
    private WhatsappPhoneNumberRepository whatsappPhoneNumberRepository;

    @Autowired
    private CampaignPersonalizer campaignPersonalizer;

    @Autowired
    private com.whatsmine.service.broadcasting.CampaignAudienceService campaignAudienceService;

    @Autowired
    private com.whatsmine.queue.QueueDispatcher queueDispatcher;

    @Autowired
    private com.whatsmine.repository.ChannelAccountRepository channelAccountRepository;

    @Autowired
    private com.whatsmine.service.whatsapp.WhatsAppApiClient whatsAppApiClient;

    @Autowired
    private com.whatsmine.service.sms.SmsApiClient smsApiClient;

    @Autowired
    private com.whatsmine.service.email.EmailApiClient emailApiClient;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        return userDetails.getWorkspaceId();
    }

    @GetMapping("/campaigns")
    public Object index(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Page<Campaign> campaigns = campaignRepository.findFilteredCampaigns(
                workspaceId, channel, status, PageRequest.of(0, 25)
        );

        Map<String, Object> filters = new HashMap<>();
        filters.put("channel", channel);
        filters.put("status", status);

        return inertiaRenderer.render("Broadcasting/Campaigns/Index", Map.of(
                "campaigns", paginate(campaigns),
                "filters", filters
        ), request);
    }

    /**
     * Builds a Laravel-paginator-shaped payload (data/current_page/last_page/total)
     * since Broadcasting/Campaigns/Index.jsx was written against that shape —
     * a raw Spring Page serializes as {content, pageable, ...}, which left
     * campaigns.data undefined and silently broke the page render.
     */
    private Map<String, Object> paginate(Page<Campaign> page) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", page.getContent());
        out.put("current_page", page.getNumber() + 1);
        out.put("last_page", Math.max(page.getTotalPages(), 1));
        out.put("per_page", page.getSize());
        out.put("total", page.getTotalElements());
        return out;
    }

    @GetMapping("/campaigns/create")
    public Object create(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return inertiaRenderer.render("Broadcasting/Campaigns/Wizard", wizardProps(getWorkspaceId(userDetails)), request);
    }

    @PostMapping("/campaigns")
    public Object store(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> body
    ) {
        Long workspaceId = getWorkspaceId(userDetails);

        Campaign campaign = new Campaign();
        campaign.setWorkspaceId(workspaceId);
        campaign.setName((String) body.get("name"));
        campaign.setChannel((String) body.get("channel"));
        campaign.setWhatsappPhoneNumberId((String) body.get("whatsapp_phone_number_id"));
        campaign.setAudienceType((String) body.getOrDefault("audience_type", "segment"));
        campaign.setAudienceRef((String) body.get("audience_ref"));
        campaign.setStatus("draft");
        campaign.setCreatedBy(userDetails.getId());

        try {
            if (body.get("template_ref") != null) {
                campaign.setTemplateRef(objectMapper.writeValueAsString(body.get("template_ref")));
            }
            if (body.get("payload_json") != null) {
                campaign.setPayloadJson(objectMapper.writeValueAsString(body.get("payload_json")));
            }
        } catch (Exception ignored) {}

        campaign = campaignRepository.save(campaign);

        return Inertia.redirect("/app/broadcasts/campaigns/" + campaign.getUuid());
    }

    @PostMapping("/campaigns/draft")
    public ResponseEntity<Map<String, String>> storeDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> body
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        String uuid = (String) body.get("uuid");

        Campaign campaign = null;
        if (uuid != null && !uuid.isBlank()) {
            campaign = campaignRepository.findByWorkspaceIdAndUuidAndStatus(workspaceId, uuid, "draft").orElse(null);
        }

        if (campaign == null) {
            campaign = new Campaign();
            campaign.setWorkspaceId(workspaceId);
            campaign.setStatus("draft");
            campaign.setCreatedBy(userDetails.getId());
        }

        if (body.get("name") != null) campaign.setName((String) body.get("name"));
        if (body.get("channel") != null) campaign.setChannel((String) body.get("channel"));
        if (body.get("whatsapp_phone_number_id") != null) campaign.setWhatsappPhoneNumberId((String) body.get("whatsapp_phone_number_id"));
        if (body.get("audience_type") != null) campaign.setAudienceType((String) body.get("audience_type"));
        if (body.get("audience_ref") != null) campaign.setAudienceRef((String) body.get("audience_ref"));

        try {
            if (body.get("template_ref") != null) campaign.setTemplateRef(objectMapper.writeValueAsString(body.get("template_ref")));
            if (body.get("payload_json") != null) campaign.setPayloadJson(objectMapper.writeValueAsString(body.get("payload_json")));
        } catch (Exception ignored) {}

        campaign = campaignRepository.save(campaign);

        return ResponseEntity.ok(Map.of("uuid", campaign.getUuid()));
    }

    @GetMapping("/campaigns/{uuid}")
    public Object show(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Campaign campaign = campaignRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        long recipientCount = campaignRecipientRepository.countByCampaignId(campaign.getId());
        campaign.setRecipientsCount(recipientCount);

        List<Object[]> rawStats = campaignRecipientRepository.countGroupByStatus(campaign.getId());
        Map<String, Long> stats = new HashMap<>();
        for (Object[] row : rawStats) {
            stats.put((String) row[0], (Long) row[1]);
        }

        List<CampaignRecipient> sample = campaignRecipientRepository.findByCampaignIdOrderByUpdatedAtDesc(
                campaign.getId(), PageRequest.of(0, 10)
        );

        Map<String, Object> props = new HashMap<>();
        props.put("campaign", campaign);
        props.put("stats", stats);
        props.put("sample", sample);
        props.put("reportUrl", "/app/reports/campaigns/" + campaign.getUuid());

        return inertiaRenderer.render("Broadcasting/Campaigns/Show", props, request);
    }

    @GetMapping("/campaigns/{uuid}/edit")
    public Object edit(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Campaign campaign = campaignRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!List.of("draft", "paused").contains(campaign.getStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Only drafts or paused campaigns can be edited.");
        }

        Map<String, Object> props = new HashMap<>(wizardProps(workspaceId));
        props.put("campaign", campaign);

        return inertiaRenderer.render("Broadcasting/Campaigns/Edit", props, request);
    }

    @PatchMapping("/campaigns/{uuid}")
    public Object update(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestBody Map<String, Object> body
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Campaign campaign = campaignRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!List.of("draft", "paused").contains(campaign.getStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Only drafts or paused campaigns can be edited.");
        }

        if (body.get("name") != null) campaign.setName((String) body.get("name"));
        if (body.get("channel") != null) campaign.setChannel((String) body.get("channel"));
        if (body.get("whatsapp_phone_number_id") != null) campaign.setWhatsappPhoneNumberId((String) body.get("whatsapp_phone_number_id"));
        if (body.get("audience_type") != null) campaign.setAudienceType((String) body.get("audience_type"));
        if (body.get("audience_ref") != null) campaign.setAudienceRef((String) body.get("audience_ref"));

        try {
            if (body.get("template_ref") != null) campaign.setTemplateRef(objectMapper.writeValueAsString(body.get("template_ref")));
            if (body.get("payload_json") != null) campaign.setPayloadJson(objectMapper.writeValueAsString(body.get("payload_json")));
        } catch (Exception ignored) {}

        campaignRepository.save(campaign);

        return Inertia.redirect("/app/broadcasts/campaigns/" + campaign.getUuid());
    }

    @PostMapping("/campaigns/audience-preview")
    public ResponseEntity<Map<String, Object>> audiencePreview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> body
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        String channel = (String) body.get("channel");
        String audienceType = (String) body.get("audience_type");
        String audienceRef = (String) body.get("audience_ref");

        List<Contact> matchedContacts = campaignAudienceService.resolve(workspaceId, channel, audienceType, audienceRef);
        int matched = matchedContacts.size();
        int deliverable = matched;

        List<Map<String, Object>> sample = new ArrayList<>();
        for (Contact c : matchedContacts.stream().limit(5).toList()) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("first_name", c.getFirstName());
            map.put("last_name", c.getLastName());
            map.put("phone_e164", c.getPhoneE164());
            map.put("email", c.getEmail());
            sample.add(map);
        }

        return ResponseEntity.ok(Map.of(
                "matched", matched,
                "deliverable", deliverable,
                "sample", sample
        ));
    }

    @PostMapping("/campaigns/{uuid}/test-send")
    public ResponseEntity<Map<String, Object>> testSend(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestBody Map<String, String> body
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Campaign campaign = campaignRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        String phone = body.get("phone_e164") != null ? body.get("phone_e164").trim() : null;
        String email = body.get("email") != null ? body.get("email").trim() : null;

        if ((phone == null || phone.isBlank()) && (email == null || email.isBlank())) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "Provide either a phone or email to test."));
        }

        // Synthetic contact carrying the tester's own identity, so personalization
        // tokens ({{contact.name}}, etc.) render meaningfully — mirrors PHP.
        Contact testContact = new Contact();
        testContact.setWorkspaceId(workspaceId);
        testContact.setPhoneE164(phone);
        testContact.setEmail(email);
        testContact.setFirstName("Test");
        testContact.setLastName("User");

        try {
            String messageId = switch (campaign.getChannel() == null ? "" : campaign.getChannel().toLowerCase()) {
                case "whatsapp" -> testSendWhatsApp(campaign, testContact);
                case "sms" -> testSendSms(campaign, testContact);
                case "email" -> testSendEmail(campaign, testContact);
                default -> throw new IllegalStateException("Unsupported campaign channel: " + campaign.getChannel());
            };
            return ResponseEntity.ok(Map.of("ok", true, "message_id", messageId, "channel", campaign.getChannel()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Send failed. Check your channel configuration and try again."));
        }
    }

    private String testSendWhatsApp(Campaign campaign, Contact contact) throws Exception {
        if (contact.getPhoneE164() == null || contact.getPhoneE164().isBlank()) {
            throw new IllegalStateException("Phone is required for a WhatsApp test send.");
        }
        var waChannelAccount = channelAccountRepository.findByWorkspaceIdAndStatus(campaign.getWorkspaceId(), "active").stream()
                .filter(ca -> "whatsapp".equalsIgnoreCase(ca.getChannel()))
                .findFirst().orElse(null);
        if (waChannelAccount == null) {
            throw new IllegalStateException("No active WhatsApp channel connected for this workspace.");
        }

        Map<String, Object> tpl = parseJsonObject(campaign.getTemplateRef());
        String name = tpl.get("name") != null ? tpl.get("name").toString() : "";
        String language = tpl.get("language") != null ? tpl.get("language").toString() : "en";
        if (name.isBlank()) {
            throw new IllegalStateException("Pick a WhatsApp template before sending a test.");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = tpl.get("components") instanceof List
                ? (List<Map<String, Object>>) tpl.get("components") : List.of();

        String phone = contact.getPhoneE164().startsWith("+") ? contact.getPhoneE164() : "+" + contact.getPhoneE164();
        return whatsAppApiClient.sendTemplate(waChannelAccount, phone, name, language, components);
    }

    private String testSendSms(Campaign campaign, Contact contact) {
        if (contact.getPhoneE164() == null || contact.getPhoneE164().isBlank()) {
            throw new IllegalStateException("Phone is required for an SMS test send.");
        }
        Map<String, Object> payload = parseJsonObject(campaign.getPayloadJson());
        String body = campaignPersonalizer.renderText(str(payload.get("body")), contact);
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("SMS body is empty after personalization.");
        }
        return smsApiClient.sendText(campaign.getWorkspaceId(), contact.getPhoneE164(), body);
    }

    private String testSendEmail(Campaign campaign, Contact contact) {
        if (contact.getEmail() == null || contact.getEmail().isBlank()) {
            throw new IllegalStateException("Email is required for an email test send.");
        }
        Map<String, Object> payload = parseJsonObject(campaign.getPayloadJson());
        String subject = campaignPersonalizer.renderText("[TEST] " + (str(payload.get("subject")) != null ? str(payload.get("subject")) : "No subject"), contact);
        String body = campaignPersonalizer.renderText(str(payload.get("body")), contact);
        emailApiClient.send(campaign.getWorkspaceId(), contact.getEmail(), subject, body != null ? body : "");
        return "email-test-" + UUID.randomUUID();
    }

    private Map<String, Object> parseJsonObject(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(raw, Map.class);
            return parsed;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }

    @PostMapping("/campaigns/{uuid}/launch")
    public Object launch(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Campaign campaign = campaignRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!List.of("draft", "paused").contains(campaign.getStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Cannot launch this campaign.");
        }

        // Audience resolution + sending now happens off-request via the real
        // job pipeline (LaunchCampaignJob -> DispatchCampaignChunkJob ->
        // SendCampaignMessageJob -> FinalizeCampaignJob), matching PHP and no
        // longer blocking this HTTP request or the WhatsApp/SMS rate limits.
        campaign.setStatus("queued");
        campaignRepository.save(campaign);

        queueDispatcher.dispatch("broadcast", "LaunchCampaignJob", Map.of("campaignId", campaign.getId()), 2, new int[]{60});

        return Inertia.redirect("/app/broadcasts/campaigns/" + campaign.getUuid());
    }

    @PostMapping("/campaigns/{uuid}/pause")
    public Object pause(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Campaign campaign = campaignRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!List.of("queued", "sending").contains(campaign.getStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Only queued or sending campaigns can be paused.");
        }

        campaign.setStatus("paused");
        campaignRepository.save(campaign);

        return Inertia.redirect("/app/broadcasts/campaigns/" + campaign.getUuid());
    }

    @DeleteMapping("/campaigns/{uuid}")
    public Object destroy(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Campaign campaign = campaignRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!"draft".equals(campaign.getStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Only draft campaigns can be deleted.");
        }

        campaignRepository.delete(campaign);

        return Inertia.redirect("/app/broadcasts/campaigns");
    }

    private Map<String, Object> wizardProps(Long workspaceId) {
        List<WhatsappTemplate> templates = whatsappTemplateRepository.findByWorkspaceIdAndStatusOrderByNameAsc(workspaceId, "APPROVED");
        List<Segment> segments = segmentRepository.findByWorkspaceIdOrderByNameAsc(workspaceId);
        List<ContactTag> tags = contactTagRepository.findByWorkspaceIdOrderByNameAsc(workspaceId);

        List<Map<String, Object>> phoneNumbers = new ArrayList<>();
        for (WhatsappBusinessAccount waba : whatsappBusinessAccountRepository.findByWorkspaceId(workspaceId)) {
            for (WhatsappPhoneNumber phone : whatsappPhoneNumberRepository.findByWabaIdFk(waba.getId())) {
                Map<String, Object> p = new HashMap<>();
                p.put("phone_number_id", phone.getPhoneNumberId());
                p.put("display_phone", phone.getDisplayPhone());
                p.put("verified_name", phone.getVerifiedName());
                p.put("waba_id", waba.getWabaId());
                phoneNumbers.add(p);
            }
        }

        Map<String, Object> props = new HashMap<>();
        props.put("whatsappTemplates", templates);
        props.put("whatsappPhoneNumbers", phoneNumbers);
        props.put("segments", segments);
        props.put("tags", tags);
        props.put("contactTokens", campaignPersonalizer.availableContactTokens());
        return props;
    }
}
