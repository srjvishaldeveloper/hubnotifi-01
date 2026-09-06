package com.whatsmine.service.segments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.Contact;
import com.whatsmine.repository.ContactRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
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
 * Java port of PHP's SegmentResolver — evaluates a dynamic segment's
 * rules_json ({combinator: 'AND'|'OR', conditions: [{field, operator,
 * value}]}) against real Contact rows via a whitelisted JPA Specification.
 * Shared by CampaignAudienceService (live audience resolution) and
 * SegmentMaterializerService (persisting segment_contact + contact_count
 * on save, matching PHP's SegmentController::store/update calling
 * SegmentResolver::materialise()).
 */
@Service
public class SegmentRuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SegmentRuleEvaluator.class);

    /** Matches PHP SegmentResolver::ALLOWED_FIELDS. */
    public static final Set<String> ALLOWED_FIELDS = Set.of(
            "phone_e164", "email", "first_name", "last_name", "country", "language",
            "source", "opt_in_whatsapp", "opt_in_sms", "opt_in_email", "created_at", "last_seen_at");

    public static final Set<String> ALLOWED_OPERATORS = Set.of(
            "=", "!=", "like", "not_like", "<", ">", "<=", ">=", "is_null", "is_not_null");

    private final ContactRepository contactRepository;
    private final ObjectMapper objectMapper;

    public SegmentRuleEvaluator(ContactRepository contactRepository, ObjectMapper objectMapper) {
        this.contactRepository = contactRepository;
        this.objectMapper = objectMapper;
    }

    /** Evaluates rules_json against a workspace's contacts. Null/blank/unparseable rules = every contact (matches PHP's "no filter" fallback). */
    @SuppressWarnings("unchecked")
    public List<Contact> evaluate(Long workspaceId, String rulesJson) {
        if (rulesJson == null || rulesJson.isBlank()) {
            return contactRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId);
        }

        Map<String, Object> rules;
        try {
            rules = objectMapper.readValue(rulesJson, Map.class);
        } catch (Exception e) {
            log.warn("Unparseable segment rules_json for workspace {} — treating as no filter", workspaceId);
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
    private Predicate buildPredicate(Root<Contact> root, CriteriaBuilder cb, Map<String, Object> condition) {
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
}
