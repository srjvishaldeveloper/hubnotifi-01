package com.whatsmine.controller.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.Contact;
import com.whatsmine.model.Segment;
import com.whatsmine.model.SegmentContact;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.SegmentContactRepository;
import com.whatsmine.repository.SegmentRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.segments.SegmentMaterializerService;
import com.whatsmine.service.segments.SegmentRuleEvaluator;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java port of PHP's SegmentController — the piece that was missing to make
 * the campaign audience targeting built in CampaignAudienceService actually
 * usable: without this, nothing could create a Segment for it to resolve.
 * Dynamic segments are re-materialised (segment_contact pivot + contact_count
 * refreshed) synchronously on every save, matching PHP. Static segments get
 * their members via the separate attach/detach "manage contacts" endpoints.
 */
@RestController
@RequestMapping("/segments")
public class SegmentController {

    private final SegmentRepository segmentRepository;
    private final SegmentContactRepository segmentContactRepository;
    private final ContactRepository contactRepository;
    private final SegmentMaterializerService materializerService;
    private final ObjectMapper objectMapper;

    public SegmentController(
            SegmentRepository segmentRepository,
            SegmentContactRepository segmentContactRepository,
            ContactRepository contactRepository,
            SegmentMaterializerService materializerService,
            ObjectMapper objectMapper) {
        this.segmentRepository = segmentRepository;
        this.segmentContactRepository = segmentContactRepository;
        this.contactRepository = contactRepository;
        this.materializerService = materializerService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public Object index(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = userDetails.getWorkspaceId();
        List<Map<String, Object>> segments = segmentRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .map(this::segmentMap).toList();

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("segments", segments);
        props.put("fields", SegmentRuleEvaluator.ALLOWED_FIELDS);
        props.put("operators", SegmentRuleEvaluator.ALLOWED_OPERATORS);

        return Inertia.render("Contacts/Segments", props);
    }

    @PostMapping
    public Object store(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody Map<String, Object> payload, HttpSession session) {
        Long workspaceId = userDetails.getWorkspaceId();

        Segment segment = new Segment();
        segment.setWorkspaceId(workspaceId);
        applyPayload(segment, payload);
        segment = segmentRepository.save(segment);
        materializerService.materialise(segment);

        Inertia.flashSuccess(session, "Segment created.");
        return Inertia.redirect("/segments");
    }

    @PutMapping("/{id}")
    public Object update(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id,
                          @RequestBody Map<String, Object> payload, HttpSession session) {
        Segment segment = segmentRepository.findByIdAndWorkspaceId(id, userDetails.getWorkspaceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        applyPayload(segment, payload);
        segment = segmentRepository.save(segment);
        materializerService.materialise(segment);

        Inertia.flashSuccess(session, "Segment updated.");
        return Inertia.redirect("/segments");
    }

    @DeleteMapping("/{id}")
    public Object destroy(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, HttpSession session) {
        Segment segment = segmentRepository.findByIdAndWorkspaceId(id, userDetails.getWorkspaceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        segmentContactRepository.deleteBySegmentId(segment.getId());
        segmentRepository.delete(segment);

        Inertia.flashSuccess(session, "Segment deleted.");
        return Inertia.redirect("/segments");
    }

    @GetMapping("/{id}/contacts")
    public Object manageContacts(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id,
                                  @RequestParam(required = false) String search) {
        Long workspaceId = userDetails.getWorkspaceId();
        Segment segment = segmentRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Set<Long> memberIds = segmentContactRepository.findBySegmentId(segment.getId()).stream()
                .map(SegmentContact::getContactId).collect(java.util.stream.Collectors.toSet());

        List<Contact> allContacts = (search != null && !search.isBlank())
                ? contactRepository.searchContacts(workspaceId, search)
                : contactRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId);

        List<Map<String, Object>> members = allContacts.stream()
                .filter(c -> memberIds.contains(c.getId()))
                .map(this::contactSummary).toList();
        List<Map<String, Object>> available = allContacts.stream()
                .filter(c -> !memberIds.contains(c.getId()))
                .map(this::contactSummary).toList();

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("segment", segmentMap(segment));
        props.put("members", members);
        props.put("available", available);

        return Inertia.render("Contacts/SegmentContacts", props);
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/{id}/contacts")
    public Object attachContacts(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id,
                                  @RequestBody Map<String, Object> payload, HttpSession session) {
        Segment segment = segmentRepository.findByIdAndWorkspaceId(id, userDetails.getWorkspaceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!"static".equalsIgnoreCase(segment.getType())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Only static segments manage members directly.");
        }

        List<Object> rawIds = payload.get("contact_ids") instanceof List ? (List<Object>) payload.get("contact_ids") : List.of();
        Set<Long> existing = segmentContactRepository.findBySegmentId(segment.getId()).stream()
                .map(SegmentContact::getContactId).collect(java.util.stream.Collectors.toSet());

        for (Object raw : rawIds) {
            Long contactId = Long.valueOf(raw.toString());
            if (existing.contains(contactId)) continue;
            SegmentContact sc = new SegmentContact();
            sc.setSegmentId(segment.getId());
            sc.setContactId(contactId);
            segmentContactRepository.save(sc);
        }

        segment.setContactCount((int) segmentContactRepository.countBySegmentId(segment.getId()));
        segmentRepository.save(segment);

        Inertia.flashSuccess(session, "Contacts added to segment.");
        return Inertia.redirect("/segments/" + id + "/contacts");
    }

    @DeleteMapping("/{id}/contacts/{contactId}")
    public Object detachContact(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id,
                                 @PathVariable Long contactId, HttpSession session) {
        Segment segment = segmentRepository.findByIdAndWorkspaceId(id, userDetails.getWorkspaceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        segmentContactRepository.deleteBySegmentIdAndContactId(segment.getId(), contactId);
        segment.setContactCount((int) segmentContactRepository.countBySegmentId(segment.getId()));
        segmentRepository.save(segment);

        Inertia.flashSuccess(session, "Contact removed from segment.");
        return Inertia.redirect("/segments/" + id + "/contacts");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void applyPayload(Segment segment, Map<String, Object> payload) {
        if (payload.get("name") != null) segment.setName(payload.get("name").toString());
        if (payload.get("type") != null) {
            String type = payload.get("type").toString();
            segment.setType("dynamic".equalsIgnoreCase(type) ? "dynamic" : "static");
        }
        if (payload.containsKey("rules_json")) {
            Object rules = payload.get("rules_json");
            try {
                segment.setRulesJson(rules != null ? objectMapper.writeValueAsString(rules) : null);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid rules.");
            }
        }
    }

    private Map<String, Object> segmentMap(Segment s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("type", s.getType());
        m.put("contact_count", s.getContactCount());
        Object rules = null;
        try {
            rules = s.getRulesJson() != null ? objectMapper.readValue(s.getRulesJson(), Map.class) : null;
        } catch (Exception ignored) {}
        m.put("rules_json", rules);
        m.put("created_at", s.getCreatedAt());
        return m;
    }

    private Map<String, Object> contactSummary(Contact c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("uuid", c.getUuid());
        m.put("first_name", c.getFirstName());
        m.put("last_name", c.getLastName());
        m.put("phone_e164", c.getPhoneE164());
        m.put("email", c.getEmail());
        return m;
    }
}
