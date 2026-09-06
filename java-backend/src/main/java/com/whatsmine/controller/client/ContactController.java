package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.model.Contact;
import com.whatsmine.model.ContactTag;
import com.whatsmine.model.Segment;
import com.whatsmine.model.SegmentContact;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.ContactTagRepository;
import com.whatsmine.repository.SegmentContactRepository;
import com.whatsmine.repository.SegmentRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.automation.AutomationTriggerService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
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

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Core Contact CRM — list/search, view, create, update, soft-delete, bulk
 * delete, CSV export. Segments are now wired to the real SegmentController;
 * tag *association* on a contact (the checkbox pickers in Index.jsx/Show.jsx)
 * is still an empty list — there's no contact&lt;-&gt;tag join table modeled in
 * Java yet, a separate follow-up.
 */
@RestController
@RequestMapping("/contacts")
public class ContactController {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int PER_PAGE = 50;

    private final ContactRepository contactRepository;
    private final ContactTagRepository contactTagRepository;
    private final SegmentRepository segmentRepository;
    private final SegmentContactRepository segmentContactRepository;
    private final AutomationTriggerService automationTriggerService;

    public ContactController(
            ContactRepository contactRepository,
            ContactTagRepository contactTagRepository,
            SegmentRepository segmentRepository,
            SegmentContactRepository segmentContactRepository,
            AutomationTriggerService automationTriggerService) {
        this.contactRepository = contactRepository;
        this.contactTagRepository = contactTagRepository;
        this.segmentRepository = segmentRepository;
        this.segmentContactRepository = segmentContactRepository;
        this.automationTriggerService = automationTriggerService;
    }

    @GetMapping
    public InertiaResponse index(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") int page) {

        Long workspaceId = userDetails.getWorkspaceId();
        PageRequest pageable = PageRequest.of(Math.max(page - 1, 0), PER_PAGE);

        Page<Contact> result = (search != null && !search.isBlank())
                ? contactRepository.searchContactsPage(workspaceId, search, pageable)
                : contactRepository.findByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(workspaceId, pageable);

        List<ContactTag> tags = contactTagRepository.findByWorkspaceIdOrderByNameAsc(workspaceId);
        List<Segment> segments = segmentRepository.findByWorkspaceIdOrderByNameAsc(workspaceId);

        Map<String, Object> filters = new LinkedHashMap<>();
        if (search != null) filters.put("search", search);
        if (tag != null) filters.put("tag", tag);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("contacts", paginate(result, "/contacts", search));
        props.put("tags", tags.stream().map(this::tagMap).toList());
        props.put("segments", segments.stream().map(this::segmentSummary).toList());
        props.put("filters", filters);

        return Inertia.render("Contacts/Index", props);
    }

    @GetMapping("/{uuid}")
    public Object show(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String uuid) {
        Contact contact = contactRepository.findByUuidAndWorkspaceId(uuid, userDetails.getWorkspaceId()).orElse(null);
        if (contact == null) {
            return Inertia.redirect("/contacts");
        }

        List<Segment> staticSegments = segmentRepository.findByWorkspaceIdOrderByNameAsc(userDetails.getWorkspaceId()).stream()
                .filter(s -> "static".equalsIgnoreCase(s.getType())).toList();

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("contact", contactMap(contact));
        props.put("staticSegments", staticSegments.stream().map(this::segmentSummary).toList());
        return Inertia.render("Contacts/Show", props);
    }

    @PostMapping
    public Object store(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody Map<String, Object> payload, HttpSession session) {
        Long workspaceId = userDetails.getWorkspaceId();

        Contact contact = new Contact();
        contact.setWorkspaceId(workspaceId);
        contact.setPhoneE164(str(payload.get("phone_e164")));
        contact.setEmail(str(payload.get("email")));
        contact.setFirstName(str(payload.get("first_name")));
        contact.setLastName(str(payload.get("last_name")));
        contact.setCountry(str(payload.get("country")));
        contact.setLanguage(str(payload.get("language")));
        contact.setOptInWhatsapp(bool(payload.get("opt_in_whatsapp"), true));
        contact.setOptInSms(bool(payload.get("opt_in_sms"), false));
        contact.setOptInEmail(bool(payload.get("opt_in_email"), false));
        contact.setSource("manual");

        contact = contactRepository.save(contact);
        syncStaticSegments(workspaceId, contact.getId(), payload.get("segment_ids"));

        try {
            automationTriggerService.fireForContact(workspaceId, "contact.created", contact.getId(), Map.of());
        } catch (Exception e) {
            // Never let an automation misconfiguration block saving the contact.
        }

        Inertia.flashSuccess(session, "Contact saved.");
        return Inertia.redirect("/contacts");
    }

    @PutMapping("/{uuid}")
    public Object update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestBody Map<String, Object> payload,
            HttpSession session) {

        Contact contact = contactRepository.findByUuidAndWorkspaceId(uuid, userDetails.getWorkspaceId()).orElse(null);
        if (contact == null) {
            return Inertia.redirect("/contacts");
        }

        if (payload.containsKey("first_name")) contact.setFirstName(str(payload.get("first_name")));
        if (payload.containsKey("last_name")) contact.setLastName(str(payload.get("last_name")));
        if (payload.containsKey("email")) contact.setEmail(str(payload.get("email")));
        if (payload.containsKey("country")) contact.setCountry(str(payload.get("country")));
        if (payload.containsKey("language")) contact.setLanguage(str(payload.get("language")));
        if (payload.containsKey("opt_in_whatsapp")) contact.setOptInWhatsapp(bool(payload.get("opt_in_whatsapp"), contact.getOptInWhatsapp()));
        if (payload.containsKey("opt_in_sms")) contact.setOptInSms(bool(payload.get("opt_in_sms"), contact.getOptInSms()));
        if (payload.containsKey("opt_in_email")) contact.setOptInEmail(bool(payload.get("opt_in_email"), contact.getOptInEmail()));

        contactRepository.save(contact);
        if (payload.containsKey("segment_ids")) {
            syncStaticSegments(userDetails.getWorkspaceId(), contact.getId(), payload.get("segment_ids"));
        }

        Inertia.flashSuccess(session, "Contact updated.");
        return Inertia.redirect("/contacts/" + uuid);
    }

    @DeleteMapping("/bulk-destroy")
    public Object bulkDestroy(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody Map<String, Object> payload, HttpSession session) {
        Long workspaceId = userDetails.getWorkspaceId();
        @SuppressWarnings("unchecked")
        List<String> uuids = (List<String>) payload.getOrDefault("uuids", List.of());

        List<Contact> contacts = contactRepository.findByWorkspaceIdAndUuidIn(workspaceId, uuids);
        LocalDateTime now = LocalDateTime.now();
        contacts.forEach(c -> c.setDeletedAt(now));
        contactRepository.saveAll(contacts);

        Inertia.flashSuccess(session, contacts.size() + " contact(s) deleted.");
        return Inertia.redirect("/contacts");
    }

    @DeleteMapping("/{uuid}")
    public Object destroy(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String uuid, HttpSession session) {
        Contact contact = contactRepository.findByUuidAndWorkspaceId(uuid, userDetails.getWorkspaceId()).orElse(null);
        if (contact != null) {
            contact.setDeletedAt(LocalDateTime.now());
            contactRepository.save(contact);
        }

        Inertia.flashSuccess(session, "Contact deleted.");
        return Inertia.redirect("/contacts");
    }

    @GetMapping("/export")
    public void export(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String search,
            HttpServletResponse response) throws IOException {

        Long workspaceId = userDetails.getWorkspaceId();
        List<Contact> contacts = (search != null && !search.isBlank())
                ? contactRepository.searchContacts(workspaceId, search)
                : contactRepository.findByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(workspaceId, PageRequest.of(0, 10000)).getContent();

        response.setContentType(MediaType.TEXT_PLAIN_VALUE + "; charset=UTF-8");
        response.setHeader("Content-Type", "text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"contacts-" + LocalDateTime.now().toLocalDate() + ".csv\"");

        try (PrintWriter writer = response.getWriter()) {
            writer.println("\"First Name\",\"Last Name\",\"Phone\",\"Email\",\"Opt-in WhatsApp\",\"Opt-in SMS\",\"Opt-in Email\",\"Created At\"");
            for (Contact c : contacts) {
                writer.println(String.join(",",
                        csv(c.getFirstName()), csv(c.getLastName()), csv(c.getPhoneE164()), csv(c.getEmail()),
                        csv(Boolean.TRUE.equals(c.getOptInWhatsapp()) ? "yes" : "no"),
                        csv(Boolean.TRUE.equals(c.getOptInSms()) ? "yes" : "no"),
                        csv(Boolean.TRUE.equals(c.getOptInEmail()) ? "yes" : "no"),
                        csv(c.getCreatedAt() != null ? c.getCreatedAt().format(TIMESTAMP) : "")));
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Map<String, Object> contactMap(Contact c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uuid", c.getUuid());
        m.put("first_name", c.getFirstName());
        m.put("last_name", c.getLastName());
        m.put("phone_e164", c.getPhoneE164());
        m.put("email", c.getEmail());
        m.put("country", c.getCountry());
        m.put("language", c.getLanguage());
        m.put("avatar_url", c.getAvatarUrl());
        m.put("opt_in_whatsapp", c.getOptInWhatsapp());
        m.put("opt_in_sms", c.getOptInSms());
        m.put("opt_in_email", c.getOptInEmail());
        m.put("tags", List.of());
        List<Long> segmentIds = segmentContactRepository.findByContactId(c.getId()).stream()
                .map(SegmentContact::getSegmentId).toList();
        m.put("segments", segmentIds.isEmpty() ? List.of() : segmentRepository.findAllById(segmentIds).stream()
                .map(this::segmentSummary).toList());
        m.put("conversations", List.of());
        m.put("created_at", c.getCreatedAt() != null ? c.getCreatedAt().format(TIMESTAMP) : null);
        return m;
    }

    /** Syncs which static segments a contact belongs to, matching PHP's Create/Edit checkbox list (static segments only). */
    @SuppressWarnings("unchecked")
    private void syncStaticSegments(Long workspaceId, Long contactId, Object rawSegmentIds) {
        if (!(rawSegmentIds instanceof List)) return;
        java.util.Set<Long> requested = ((List<Object>) rawSegmentIds).stream()
                .map(o -> Long.valueOf(o.toString())).collect(java.util.stream.Collectors.toSet());

        java.util.Set<Long> staticSegmentIds = segmentRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .filter(s -> "static".equalsIgnoreCase(s.getType()))
                .map(com.whatsmine.model.Segment::getId).collect(java.util.stream.Collectors.toSet());

        java.util.Set<Long> current = segmentContactRepository.findByContactId(contactId).stream()
                .map(com.whatsmine.model.SegmentContact::getSegmentId)
                .filter(staticSegmentIds::contains)
                .collect(java.util.stream.Collectors.toSet());

        for (Long segmentId : staticSegmentIds) {
            boolean shouldBeMember = requested.contains(segmentId);
            boolean isMember = current.contains(segmentId);
            if (shouldBeMember && !isMember) {
                com.whatsmine.model.SegmentContact sc = new com.whatsmine.model.SegmentContact();
                sc.setSegmentId(segmentId);
                sc.setContactId(contactId);
                segmentContactRepository.save(sc);
            } else if (!shouldBeMember && isMember) {
                segmentContactRepository.deleteBySegmentIdAndContactId(segmentId, contactId);
            }
        }

        for (Long segmentId : staticSegmentIds) {
            segmentRepository.findById(segmentId).ifPresent(seg -> {
                seg.setContactCount((int) segmentContactRepository.countBySegmentId(segmentId));
                segmentRepository.save(seg);
            });
        }
    }

    private Map<String, Object> segmentSummary(Segment s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("type", s.getType());
        return m;
    }

    private Map<String, Object> tagMap(ContactTag t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("color", t.getColor());
        return m;
    }

    /** Builds a Laravel-paginator-shaped payload (data/current_page/last_page/links) since the frontend was written against that shape. */
    private Map<String, Object> paginate(Page<Contact> page, String basePath, String search) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", page.getContent().stream().map(this::contactMap).toList());
        out.put("current_page", page.getNumber() + 1);
        out.put("last_page", Math.max(page.getTotalPages(), 1));
        out.put("per_page", page.getSize());
        out.put("total", page.getTotalElements());

        List<Map<String, Object>> links = new ArrayList<>();
        int current = page.getNumber() + 1;
        int last = Math.max(page.getTotalPages(), 1);
        links.add(linkFor(current > 1 ? current - 1 : -1, "&laquo; Previous", false, basePath, search));
        for (int p = 1; p <= last; p++) {
            links.add(linkFor(p, String.valueOf(p), p == current, basePath, search));
        }
        links.add(linkFor(current < last ? current + 1 : -1, "Next &raquo;", false, basePath, search));
        out.put("links", links);
        return out;
    }

    private Map<String, Object> linkFor(int pageNum, String label, boolean active, String basePath, String search) {
        Map<String, Object> link = new LinkedHashMap<>();
        link.put("label", label);
        link.put("active", active);
        if (pageNum < 1) {
            link.put("url", null);
        } else {
            String qs = "page=" + pageNum + (search != null && !search.isBlank() ? "&search=" + search : "");
            link.put("url", basePath + "?" + qs);
        }
        return link;
    }

    private String str(Object o) {
        return o != null ? o.toString().trim() : null;
    }

    private Boolean bool(Object o, Boolean fallback) {
        return o != null ? Boolean.parseBoolean(o.toString()) : fallback;
    }

    private String csv(String value) {
        return "\"" + (value != null ? value.replace("\"", "\"\"") : "") + "\"";
    }
}
