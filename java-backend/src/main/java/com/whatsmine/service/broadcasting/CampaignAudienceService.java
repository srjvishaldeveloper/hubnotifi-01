package com.whatsmine.service.broadcasting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.Contact;
import com.whatsmine.model.ContactTagPivot;
import com.whatsmine.model.Segment;
import com.whatsmine.model.SegmentContact;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.ContactTagPivotRepository;
import com.whatsmine.repository.SegmentContactRepository;
import com.whatsmine.repository.SegmentRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** Matches PHP SegmentResolver::ALLOWED_FIELDS — whitelisted to block arbitrary field/SQL injection via rules_json. */
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "phone_e164", "email", "first_name", "last_name", "country", "language",
            "source", "opt_in_whatsapp", "opt_in_sms", "opt_in_email", "created_at", "last_seen_at");

    private static final Set<String> ALLOWED_OPERATORS = Set.of(
            "=", "!=", "like", "not_like", "<", ">", "<=", ">=", "is_null", "is_not_null");

    private final ContactRepository contactRepository;
    private final SegmentRepository segmentRepository;
    private final ContactTagPivotRepository contactTagPivotRepository;
    private final SegmentContactRepository segmentContactRepository;
    private final ObjectMapper objectMapper;

    public CampaignAudienceService(ContactRepository contactRepository, SegmentRepository segmentRepository,
                                    ContactTagPivotRepository contactTagPivotRepository, SegmentContactRepository segmentContactRepository,
                                    ObjectMapper objectMapper) {
        this.contactRepository = contactRepository;
        this.segmentRepository = segmentRepository;
        this.contactTagPivotRepository = contactTagPivotRepository;
        this.segmentContactRepository = segmentContactRepository;
        this.objectMapper = objectMapper;
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
            return resolveDynamicSegment(workspaceId, segment);
        }

        List<Long> contactIds = segmentContactRepository.findBySegmentId(segmentId).stream().map(SegmentContact::getContactId).toList();
        if (contactIds.isEmpty()) return List.of();
        return contactRepository.findAllById(contactIds).stream()
                .filter(c -> c.getWorkspaceId().equals(workspaceId) && c.getDeletedAt() == null)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Contact> resolveDynamicSegment(Long workspaceId, Segment segment) {
        if (segment.getRulesJson() == null || segment.getRulesJson().isBlank()) {
            return contactRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId);
        }

        Map<String, Object> rules;
        try {
            rules = objectMapper.readValue(segment.getRulesJson(), Map.class);
        } catch (Exception e) {
            log.warn("Segment {} has unparseable rules_json — treating as no filter", segment.getId());
            return contactRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId);
        }

        String combinator = String.valueOf(rules.getOrDefault("combinator", "AND")).toUpperCase();
        Object rawConditions = rules.get("conditions");
        List<Map<String, Object>> conditions = rawConditions instanceof List ? (List<Map<String, Object>>) rawConditions : List.of();

        Specification<Contact> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("workspaceId"), workspaceId), cb.isNull(root.get("deletedAt")));

        if (!conditions.isEmpty()) {
            Specification<Contact> conditionSpec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                for (Map<String, Object> condition : conditions) {
                    Predicate p = buildPredicate(root, cb, condition);
                    if (p != null) predicates.add(p);
                }
                if (predicates.isEmpty()) return cb.conjunction();
                return "OR".equals(combinator) ? cb.or(predicates.toArray(new Predicate[0])) : cb.and(predicates.toArray(new Predicate[0]));
            };
            spec = spec.and(conditionSpec);
        }

        return contactRepository.findAll(spec);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate buildPredicate(jakarta.persistence.criteria.Root<Contact> root, CriteriaBuilder cb, Map<String, Object> condition) {
        String field = String.valueOf(condition.get("field"));
        String operator = String.valueOf(condition.get("operator"));
        Object value = condition.get("value");

        if (!ALLOWED_FIELDS.contains(field) || !ALLOWED_OPERATORS.contains(operator)) return null;

        String property = toCamelCase(field);
        Path path;
        try {
            path = root.get(property);
        } catch (Exception e) {
            return null;
        }

        return switch (operator) {
            case "=" -> cb.equal(path, coerce(path, value));
            case "!=" -> cb.notEqual(path, coerce(path, value));
            case "like" -> cb.like(cb.lower(path.as(String.class)), "%" + String.valueOf(value).toLowerCase() + "%");
            case "not_like" -> cb.notLike(cb.lower(path.as(String.class)), "%" + String.valueOf(value).toLowerCase() + "%");
            case "<" -> cb.lessThan(path, (Comparable) coerce(path, value));
            case ">" -> cb.greaterThan(path, (Comparable) coerce(path, value));
            case "<=" -> cb.lessThanOrEqualTo(path, (Comparable) coerce(path, value));
            case ">=" -> cb.greaterThanOrEqualTo(path, (Comparable) coerce(path, value));
            case "is_null" -> cb.isNull(path);
            case "is_not_null" -> cb.isNotNull(path);
            default -> null;
        };
    }

    private Object coerce(Path<?> path, Object value) {
        Class<?> type = path.getJavaType();
        if (value == null) return null;
        if (type == Boolean.class || type == boolean.class) {
            if (value instanceof Boolean b) return b;
            return Boolean.parseBoolean(String.valueOf(value));
        }
        if (type == LocalDateTime.class) {
            return LocalDateTime.parse(String.valueOf(value));
        }
        return String.valueOf(value);
    }

    private String toCamelCase(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else {
                sb.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            }
        }
        return sb.toString();
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
