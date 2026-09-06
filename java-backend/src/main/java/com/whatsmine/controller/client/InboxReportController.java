package com.whatsmine.controller.client;

import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.ChannelAccount;
import com.whatsmine.model.Conversation;
import com.whatsmine.model.User;
import com.whatsmine.repository.ChannelAccountRepository;
import com.whatsmine.repository.ConversationRepository;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Inbox SLA/volume report — porting PHP's Reports\InboxReportController.
 * Uses Conversation.firstResponseAt/resolvedAt (already captured by the real
 * Inbox core this session confirmed working) and Message.channel for the
 * channel mix. Renders the existing client/Reports/Inbox/Index page.
 */
@RestController
@RequestMapping("/app/reports/inbox")
public class InboxReportController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ChannelAccountRepository channelAccountRepository;

    @Autowired
    private UserRepository userRepository;

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

        List<Conversation> conversations = conversationRepository.findByWorkspaceIdAndActivityBetween(workspaceId, fromDt, toDt);

        Map<Long, String> channelById = channelAccountRepository.findByWorkspaceId(workspaceId).stream()
                .collect(Collectors.toMap(ChannelAccount::getId, ChannelAccount::getChannel));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("conversationsOverTime", conversationsOverTime(conversations, fromDate, toDate));
        props.put("channelMix", channelMix(conversations, channelById));
        props.put("agentLeaderboard", agentLeaderboard(conversations));
        props.put("firstResponseTimes", slaSeries(conversations, fromDate, toDate, true));
        props.put("resolutionTimes", slaSeries(conversations, fromDate, toDate, false));
        props.put("dateRange", Map.of("from", fromDate.format(DAY), "to", toDate.format(DAY)));

        return inertiaRenderer.render("client/Reports/Inbox/Index", props, request);
    }

    private List<Map<String, Object>> conversationsOverTime(List<Conversation> conversations, LocalDate from, LocalDate to) {
        Map<String, Long> opened = conversations.stream()
                .filter(c -> c.getCreatedAt() != null)
                .collect(Collectors.groupingBy(c -> c.getCreatedAt().format(DAY), Collectors.counting()));
        Map<String, Long> resolved = conversations.stream()
                .filter(c -> c.getResolvedAt() != null)
                .collect(Collectors.groupingBy(c -> c.getResolvedAt().format(DAY), Collectors.counting()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            String key = d.format(DAY);
            result.add(Map.of("date", key, "opened", opened.getOrDefault(key, 0L), "resolved", resolved.getOrDefault(key, 0L)));
        }
        return result;
    }

    private List<Map<String, Object>> channelMix(List<Conversation> conversations, Map<Long, String> channelById) {
        Map<String, Long> byChannel = conversations.stream()
                .collect(Collectors.groupingBy(c -> channelById.getOrDefault(c.getChannelAccountId(), "unknown"), Collectors.counting()));
        return byChannel.entrySet().stream()
                .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue()))
                .toList();
    }

    private List<Map<String, Object>> agentLeaderboard(List<Conversation> conversations) {
        Map<Long, List<Conversation>> byAgent = conversations.stream()
                .filter(c -> c.getAssignedUserId() != null)
                .collect(Collectors.groupingBy(Conversation::getAssignedUserId));

        if (byAgent.isEmpty()) {
            return List.of();
        }

        Map<Long, User> users = userRepository.findAllById(byAgent.keySet()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Long, List<Conversation>> e : byAgent.entrySet()) {
            Long userId = e.getKey();
            List<Conversation> handled = e.getValue().stream()
                    .filter(c -> c.getResolvedAt() != null || "resolved".equals(c.getStatus()))
                    .toList();

            List<Double> firstResponseMinutes = e.getValue().stream()
                    .filter(c -> c.getCreatedAt() != null && c.getFirstResponseAt() != null && !c.getFirstResponseAt().isBefore(c.getCreatedAt()))
                    .map(c -> Duration.between(c.getCreatedAt(), c.getFirstResponseAt()).getSeconds() / 60.0)
                    .toList();

            Double avgFirstResponse = firstResponseMinutes.isEmpty() ? null
                    : Math.round(firstResponseMinutes.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10) / 10.0;

            User user = users.get(userId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("user_id", userId);
            row.put("name", user != null ? user.getName() : "Unknown");
            row.put("handled", handled.size());
            row.put("avg_first_response_min", avgFirstResponse);
            rows.add(row);
        }
        rows.sort((a, b) -> Integer.compare((int) b.get("handled"), (int) a.get("handled")));
        return rows;
    }

    private List<Map<String, Object>> slaSeries(List<Conversation> conversations, LocalDate from, LocalDate to, boolean firstResponse) {
        Map<String, List<Long>> secondsByDay = new HashMap<>();
        for (Conversation c : conversations) {
            LocalDateTime end = firstResponse ? c.getFirstResponseAt() : c.getResolvedAt();
            if (c.getCreatedAt() == null || end == null || end.isBefore(c.getCreatedAt())) continue;
            String key = end.format(DAY);
            secondsByDay.computeIfAbsent(key, k -> new ArrayList<>()).add(Duration.between(c.getCreatedAt(), end).getSeconds());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            String key = d.format(DAY);
            List<Long> values = secondsByDay.get(key);
            Long median = median(values);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", key);
            row.put("median_seconds", median);
            result.add(row);
        }
        return result;
    }

    private Long median(List<Long> values) {
        if (values == null || values.isEmpty()) return null;
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(mid - 1) + sorted.get(mid)) / 2 : sorted.get(mid);
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
