package com.whatsmine.service.broadcasting;

import com.whatsmine.model.Contact;
import com.whatsmine.model.ContactTagPivot;
import com.whatsmine.model.Segment;
import com.whatsmine.model.SegmentContact;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.ContactTagPivotRepository;
import com.whatsmine.repository.SegmentContactRepository;
import com.whatsmine.repository.SegmentRepository;
import com.whatsmine.service.segments.SegmentRuleEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Java port of PHP's LaunchCampaignJob::resolveAudience() /
 * CampaignController::resolveAudienceForPreview(). Resolves a campaign's
 * audience_type + audience_ref into real Contact rows, then applies the
 * same per-channel opt-in + reachability filter PHP applies before sending.
 *
 * audience_type values (matching PHP's validation list):
 *  - "segment": audience_ref is a Segment id. Static segments read the
 *    materialized segment_contact pivot; dynamic segments evaluate
 *    rules_json against Contact directly (PHP re-materializes dynamic
 *    segments on a schedule — Java always evaluates live instead, which is
 *    simpler and never stale, at the cost of a slightly heavier query).
 *  - "tag": audience_ref is a ContactTag id, resolved via contact_tag_pivot.
 *  - "contact_list" (and legacy/unset "all"/"segment" with no ref): every
 *    contact in the workspace.
 *  - "csv": no CSV upload/import endpoint exists in the Java broadcasting
 *    UI yet, so this intentionally returns an empty audience rather than
 *    silently sending to the wrong people.
 */
@Service
public class CampaignAudienceService {

    private static final Logger log = LoggerFactory.getLogger(CampaignAudienceService.class);

    private final ContactRepository contactRepository;
    private final SegmentRepository segmentRepository;
    private final ContactTagPivotRepository contactTagPivotRepository;
    private final SegmentContactRepository segmentContactRepository;
    private final SegmentRuleEvaluator segmentRuleEvaluator;

    public CampaignAudienceService(ContactRepository contactRepository, SegmentRepository segmentRepository,
                                    ContactTagPivotRepository contactTagPivotRepository, SegmentContactRepository segmentContactRepository,
                                    SegmentRuleEvaluator segmentRuleEvaluator) {
        this.contactRepository = contactRepository;
        this.segmentRepository = segmentRepository;
        this.contactTagPivotRepository = contactTagPivotRepository;
        this.segmentContactRepository = segmentContactRepository;
        this.segmentRuleEvaluator = segmentRuleEvaluator;
    }

    public List<Contact> resolve(Long workspaceId, String channel, String audienceType, String audienceRef) {
        List<Contact> contacts = resolveRaw(workspaceId, audienceType, audienceRef);
        return applyChannelFilter(contacts, channel);
    }

    private List<Contact> resolveRaw(Long workspaceId, String audienceType, String audienceRef) {
        String type = audienceType != null ? audienceType : "contact_list";

        return switch (type) {
            case "segment" -> resolveSegment(workspaceId, audienceRef);
            case "tag" -> resolveTag(workspaceId, audienceRef);
            case "csv" -> {
                log.warn("Campaign audience_type=csv has no import pipeline in Java yet — returning empty audience for workspace {}", workspaceId);
                yield List.of();
            }
            default -> contactRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId);
        };
    }

    private List<Contact> resolveSegment(Long workspaceId, String audienceRef) {
        Long segmentId = parseLong(audienceRef);
        if (segmentId == null) return List.of();
        Segment segment = segmentRepository.findById(segmentId).filter(s -> s.getWorkspaceId().equals(workspaceId)).orElse(null);
        if (segment == null) return List.of();

        if ("dynamic".equalsIgnoreCase(segment.getType())) {
            return segmentRuleEvaluator.evaluate(workspaceId, segment.getRulesJson());
        }

        List<Long> contactIds = segmentContactRepository.findBySegmentId(segmentId).stream().map(SegmentContact::getContactId).toList();
        if (contactIds.isEmpty()) return List.of();
        return contactRepository.findAllById(contactIds).stream()
                .filter(c -> c.getWorkspaceId().equals(workspaceId) && c.getDeletedAt() == null)
                .toList();
    }

    private List<Contact> resolveTag(Long workspaceId, String audienceRef) {
        Long tagId = parseLong(audienceRef);
        if (tagId == null) return List.of();
        List<Long> contactIds = contactTagPivotRepository.findByTagId(tagId).stream().map(ContactTagPivot::getContactId).toList();
        if (contactIds.isEmpty()) return List.of();
        return contactRepository.findAllById(contactIds).stream()
                .filter(c -> c.getWorkspaceId().equals(workspaceId) && c.getDeletedAt() == null)
                .toList();
    }

    /** Matches PHP's per-channel opt-in + reachability check applied after audience resolution. */
    private List<Contact> applyChannelFilter(List<Contact> contacts, String channel) {
        String ch = channel != null ? channel.toLowerCase() : "";
        return contacts.stream().filter(c -> switch (ch) {
            case "whatsapp" -> Boolean.TRUE.equals(c.getOptInWhatsapp()) && c.getPhoneE164() != null && !c.getPhoneE164().isBlank();
            case "sms" -> Boolean.TRUE.equals(c.getOptInSms()) && c.getPhoneE164() != null && !c.getPhoneE164().isBlank();
            case "email" -> Boolean.TRUE.equals(c.getOptInEmail()) && c.getEmail() != null && !c.getEmail().isBlank();
            default -> true;
        }).toList();
    }

    private Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
