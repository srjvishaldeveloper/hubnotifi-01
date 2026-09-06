package com.whatsmine.controller.client;

import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.SocialAccount;
import com.whatsmine.model.SocialPost;
import com.whatsmine.model.SocialPostAccount;
import com.whatsmine.repository.SocialAccountRepository;
import com.whatsmine.repository.SocialPostAccountRepository;
import com.whatsmine.repository.SocialPostRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Social scheduling report — porting PHP's Reports\SocialReportController.
 * Java's SocialPost/SocialPostAccount schema is normalized differently from
 * PHP's (a join table + SocialAccount.provider rather than a target_accounts
 * JSON column, and no title/post_url fields), so "posts by network" joins
 * across both tables instead of reading a JSON array. Renders the existing
 * client/Reports/Social/Index page. Since the Social Media Scheduling
 * feature itself has no create/publish backend yet (separate, already
 * tracked gap), this report is real but will show near-empty data until
 * that's built.
 */
@RestController
@RequestMapping("/app/reports/social")
public class SocialReportController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private SocialPostRepository socialPostRepository;

    @Autowired
    private SocialPostAccountRepository socialPostAccountRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @GetMapping
    public Object index(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        Long workspaceId = userDetails.getWorkspaceId();

        LocalDate fromDate = parseOrDefault(from, LocalDate.now().minusDays(29));
        LocalDate toDate = parseOrDefault(to, LocalDate.now());
        LocalDateTime fromDt = fromDate.atStartOfDay();
        LocalDateTime toDt = toDate.plusDays(1).atStartOfDay().minusNanos(1);

        List<SocialPost> posts = socialPostRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, PageRequest.of(0, 5000))
                .stream()
                .filter(p -> p.getCreatedAt() != null && !p.getCreatedAt().isBefore(fromDt) && !p.getCreatedAt().isAfter(toDt))
                .toList();

        Map<Long, String> providerByAccountId = socialAccountRepository.findByWorkspaceId(workspaceId).stream()
                .collect(Collectors.toMap(SocialAccount::getId, SocialAccount::getProvider));

        List<Long> postIds = posts.stream().map(SocialPost::getId).toList();
        List<SocialPostAccount> targets = postIds.isEmpty() ? List.of() : socialPostAccountRepository.findBySocialPostIdIn(postIds);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("postsByNetwork", postsByNetwork(targets, providerByAccountId));
        props.put("postsByStatus", postsByStatus(posts));
        props.put("recentPosts", recentPosts(posts));
        props.put("dateRange", Map.of("from", fromDate.format(DAY), "to", toDate.format(DAY)));

        return inertiaRenderer.render("client/Reports/Social/Index", props, request);
    }

    private List<Map<String, Object>> postsByNetwork(List<SocialPostAccount> targets, Map<Long, String> providerByAccountId) {
        Map<String, Long> byNetwork = targets.stream()
                .collect(Collectors.groupingBy(t -> providerByAccountId.getOrDefault(t.getSocialAccountId(), "unknown"), Collectors.counting()));
        return byNetwork.entrySet().stream()
                .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue()))
                .toList();
    }

    private List<Map<String, Object>> postsByStatus(List<SocialPost> posts) {
        Map<String, Long> byStatus = posts.stream()
                .collect(Collectors.groupingBy(p -> p.getStatus() != null ? p.getStatus() : "draft", Collectors.counting()));
        return byStatus.entrySet().stream()
                .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue()))
                .toList();
    }

    private List<Map<String, Object>> recentPosts(List<SocialPost> posts) {
        return posts.stream().limit(20).map(p -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("title", null);
            row.put("status", p.getStatus());
            row.put("scheduled_at", p.getScheduledAt());
            row.put("published_at", p.getPublishedAt());
            row.put("post_url", null);
            return row;
        }).toList();
    }

    private LocalDate parseOrDefault(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return fallback;
        }
    }
}
