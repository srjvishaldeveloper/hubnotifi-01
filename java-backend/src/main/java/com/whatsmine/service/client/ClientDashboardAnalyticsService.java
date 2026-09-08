package com.whatsmine.service.client;

import com.whatsmine.model.*;
import com.whatsmine.repository.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Java port of PHP's AnalyticsService, scoped to what the client Dashboard needs.
 * Follows the same in-memory grouping + dense day-series style already established
 * by AdminAnalyticsService and the workspace-scoped report controllers (Inbox/Campaign/AI)
 * rather than raw SQL aggregation.
 */
@Service
public class ClientDashboardAnalyticsService {

    private final ContactRepository contactRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final AutomationRepository automationRepository;
    private final AutomationRunRepository automationRunRepository;
    private final AiChatbotRepository aiChatbotRepository;
    private final AiRunRepository aiRunRepository;
    private final SocialPostRepository socialPostRepository;
    private final UserRepository userRepository;

    public ClientDashboardAnalyticsService(ContactRepository contactRepository,
                                            ConversationRepository conversationRepository,
                                            MessageRepository messageRepository,
                                            CampaignRepository campaignRepository,
                                            CampaignRecipientRepository campaignRecipientRepository,
                                            AutomationRepository automationRepository,
                                            AutomationRunRepository automationRunRepository,
                                            AiChatbotRepository aiChatbotRepository,
                                            AiRunRepository aiRunRepository,
                                            SocialPostRepository socialPostRepository,
                                            UserRepository userRepository) {
        this.contactRepository = contactRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.campaignRepository = campaignRepository;
        this.campaignRecipientRepository = campaignRecipientRepository;
        this.automationRepository = automationRepository;
        this.automationRunRepository = automationRunRepository;
        this.aiChatbotRepository = aiChatbotRepository;
        this.aiRunRepository = aiRunRepository;
        this.socialPostRepository = socialPostRepository;
        this.userRepository = userRepository;
    }

    public Map<String, Object> buildStats(Long wsId, LocalDateTime from, LocalDateTime to,
                                           LocalDateTime prevFrom, LocalDateTime prevTo) {
        long messagesOut = messageRepository.countByWorkspaceAndDirectionAndCreatedAtBetween(wsId, "out", from, to);
        long messagesOutPrev = messageRepository.countByWorkspaceAndDirectionAndCreatedAtBetween(wsId, "out", prevFrom, prevTo);
        long messagesIn = messageRepository.countByWorkspaceAndDirectionAndCreatedAtBetween(wsId, "in", from, to);
        long messagesInPrev = messageRepository.countByWorkspaceAndDirectionAndCreatedAtBetween(wsId, "in", prevFrom, prevTo);

        long contactsNew = contactRepository.countByWorkspaceIdAndCreatedAtBetween(wsId, from, to);
        long contactsNewPrev = contactRepository.countByWorkspaceIdAndCreatedAtBetween(wsId, prevFrom, prevTo);

        long convNew = conversationRepository.countByWorkspaceIdAndCreatedAtBetween(wsId, from, to);
        long convNewPrev = conversationRepository.countByWorkspaceIdAndCreatedAtBetween(wsId, prevFrom, prevTo);

        Map<String, Object> ai = aiKpis(wsId, from, to);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("messages_out", messagesOut);
        stats.put("messages_out_delta", pctDelta(messagesOut, messagesOutPrev));
        stats.put("messages_in", messagesIn);
        stats.put("messages_in_delta", pctDelta(messagesIn, messagesInPrev));
        stats.put("contacts_total", contactRepository.countByWorkspaceId(wsId));
        stats.put("contacts_new", contactsNew);
        stats.put("contacts_new_delta", pctDelta(contactsNew, contactsNewPrev));
        stats.put("conversations_open", conversationRepository.countByWorkspaceIdAndStatusIn(wsId, List.of("open", "pending")));
        stats.put("conversations_new", convNew);
        stats.put("conversations_new_delta", pctDelta(convNew, convNewPrev));
        stats.put("campaigns_total", campaignRepository.countByWorkspaceId(wsId));
        stats.put("automations_active", automationRepository.countByWorkspaceIdAndStatus(wsId, "active"));
        stats.put("ai_runs", ai.get("total_runs"));
        stats.put("ai_cost_cents", ai.get("total_cost_cents"));
        return stats;
    }

    public Map<String, Object> buildCharts(Long wsId, LocalDateTime from, LocalDateTime to) {
        List<Message> messages = messageRepository.findByWorkspaceAndCreatedAtBetween(wsId, from, to);
        List<Conversation> activity = conversationRepository.findByWorkspaceIdAndActivityBetween(wsId, from, to);
        List<AutomationRun> runs = automationRunRepository.findByWorkspaceAndCreatedAtBetween(wsId, from, to);
        List<AiRun> aiRuns = aiRunsInRange(wsId, from, to);

        Map<String, Object> charts = new LinkedHashMap<>();
        charts.put("messages", messageVolumeByChannel(messages, from, to));
        charts.put("ai_tokens", aiUsageByDay(aiRuns, from, to));
        charts.put("conversations", conversationsResolvedOverTime(activity, from, to));
        charts.put("contacts_growth", contactsGrowthByDay(wsId, from, to));
        charts.put("channel_mix", conversationChannelMix(messages));
        charts.put("automation_runs", automationRunsByStatus(runs, from, to));
        charts.put("social_posts", socialPostsByStatus(wsId, from, to));
        return charts;
    }

    public Map<String, Object> buildTables(Long wsId, LocalDateTime from, LocalDateTime to) {
        List<Conversation> activity = conversationRepository.findByWorkspaceIdAndActivityBetween(wsId, from, to);

        Map<String, Object> tables = new LinkedHashMap<>();
        tables.put("agent_leaderboard", agentLeaderboard(activity));
        tables.put("recent_conversations", recentConversations(wsId, 6));
        tables.put("recent_campaigns", recentCampaigns(wsId, 6));
        return tables;
    }

    // ─── AI KPIs ──────────────────────────────────────────────────────────────

    private List<AiRun> aiRunsInRange(Long wsId, LocalDateTime from, LocalDateTime to) {
        List<Long> chatbotIds = aiChatbotRepository.findByWorkspaceIdOrderByIdDesc(wsId).stream().map(AiChatbot::getId).toList();
        return chatbotIds.isEmpty() ? List.of() : aiRunRepository.findByChatbotIdInAndCreatedAtBetween(chatbotIds, from, to);
    }

    private Map<String, Object> aiKpis(Long wsId, LocalDateTime from, LocalDateTime to) {
        List<AiRun> runs = aiRunsInRange(wsId, from, to);
        long totalRuns = runs.size();
        long totalTokens = runs.stream().mapToLong(r -> nz(r.getPromptTokens()) + nz(r.getCompletionTokens())).sum();
        long totalCostCents = runs.stream().mapToLong(r -> nz(r.getCostCents())).sum();
        double avgLatency = runs.stream().mapToInt(r -> nz(r.getLatencyMs())).average().orElse(0);
        long failedRuns = runs.stream().filter(r -> "failed".equals(r.getStatus())).count();
        double errorRate = totalRuns > 0 ? Math.round((failedRuns * 100.0 / totalRuns) * 10) / 10.0 : 0.0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total_runs", totalRuns);
        out.put("total_tokens", totalTokens);
        out.put("total_cost_cents", totalCostCents);
        out.put("avg_latency_ms", Math.round(avgLatency));
        out.put("error_rate", errorRate);
        return out;
    }

    // ─── Day-series charts ────────────────────────────────────────────────────

    private List<Map<String, Object>> messageVolumeByChannel(List<Message> messages, LocalDateTime from, LocalDateTime to) {
        Set<String> channels = new LinkedHashSet<>();
        Map<String, Map<String, Long>> byDate = new HashMap<>();
        for (Message m : messages) {
            String channel = m.getChannel() != null ? m.getChannel() : "unknown";
            channels.add(channel);
            String date = m.getCreatedAt().toLocalDate().toString();
            byDate.computeIfAbsent(date, k -> new HashMap<>()).merge(channel, 1L, Long::sum);
        }
        return fillDateSeriesMultiKey(from, to, byDate, channels);
    }

    private List<Map<String, Object>> aiUsageByDay(List<AiRun> runs, LocalDateTime from, LocalDateTime to) {
        Map<String, long[]> byDate = new HashMap<>();
        for (AiRun r : runs) {
            String date = r.getCreatedAt().toLocalDate().toString();
            long[] arr = byDate.computeIfAbsent(date, k -> new long[3]);
            arr[0] += nz(r.getPromptTokens());
            arr[1] += nz(r.getCompletionTokens());
            arr[2] += nz(r.getCostCents());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = from.toLocalDate(); !d.isAfter(to.toLocalDate()); d = d.plusDays(1)) {
            long[] arr = byDate.getOrDefault(d.toString(), new long[3]);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.toString());
            row.put("prompt", arr[0]);
            row.put("completion", arr[1]);
            row.put("cost_cents", arr[2]);
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> conversationsResolvedOverTime(List<Conversation> activity, LocalDateTime from, LocalDateTime to) {
        Map<String, Long> opened = activity.stream()
                .filter(c -> c.getCreatedAt() != null && !c.getCreatedAt().isBefore(from) && !c.getCreatedAt().isAfter(to))
                .collect(Collectors.groupingBy(c -> c.getCreatedAt().toLocalDate().toString(), Collectors.counting()));
        Map<String, Long> resolved = activity.stream()
                .filter(c -> c.getResolvedAt() != null && !c.getResolvedAt().isBefore(from) && !c.getResolvedAt().isAfter(to))
                .collect(Collectors.groupingBy(c -> c.getResolvedAt().toLocalDate().toString(), Collectors.counting()));

        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = from.toLocalDate(); !d.isAfter(to.toLocalDate()); d = d.plusDays(1)) {
            String key = d.toString();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", key);
            row.put("opened", opened.getOrDefault(key, 0L));
            row.put("resolved", resolved.getOrDefault(key, 0L));
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> contactsGrowthByDay(Long wsId, LocalDateTime from, LocalDateTime to) {
        List<Contact> contacts = contactRepository.findByWorkspaceIdAndCreatedAtBetween(wsId, from, to);
        Map<String, Long> byDate = contacts.stream()
                .filter(c -> c.getCreatedAt() != null)
                .collect(Collectors.groupingBy(c -> c.getCreatedAt().toLocalDate().toString(), Collectors.counting()));

        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = from.toLocalDate(); !d.isAfter(to.toLocalDate()); d = d.plusDays(1)) {
            String key = d.toString();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", key);
            row.put("contacts", byDate.getOrDefault(key, 0L));
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> automationRunsByStatus(List<AutomationRun> runs, LocalDateTime from, LocalDateTime to) {
        Set<String> statuses = new LinkedHashSet<>();
        Map<String, Map<String, Long>> byDate = new HashMap<>();
        for (AutomationRun r : runs) {
            String status = r.getStatus() != null ? r.getStatus() : "unknown";
            statuses.add(status);
            String date = r.getCreatedAt().toLocalDate().toString();
            byDate.computeIfAbsent(date, k -> new HashMap<>()).merge(status, 1L, Long::sum);
        }
        return fillDateSeriesMultiKey(from, to, byDate, statuses);
    }

    // ─── Non-day-series charts ────────────────────────────────────────────────

    private List<Map<String, Object>> conversationChannelMix(List<Message> messages) {
        Map<String, Set<Long>> conversationsByChannel = new LinkedHashMap<>();
        for (Message m : messages) {
            String channel = m.getChannel() != null ? m.getChannel() : "unknown";
            conversationsByChannel.computeIfAbsent(channel, k -> new HashSet<>()).add(m.getConversationId());
        }
        return conversationsByChannel.entrySet().stream()
                .map(e -> Map.<String, Object>of("name", e.getKey(), "value", (long) e.getValue().size()))
                .sorted((a, b) -> Long.compare((long) b.get("value"), (long) a.get("value")))
                .toList();
    }

    private List<Map<String, Object>> socialPostsByStatus(Long wsId, LocalDateTime from, LocalDateTime to) {
        List<SocialPost> posts = socialPostRepository.findByWorkspaceIdAndCreatedAtBetween(wsId, from, to);
        Map<String, Long> byStatus = posts.stream()
                .collect(Collectors.groupingBy(p -> p.getStatus() != null ? p.getStatus() : "unknown", Collectors.counting()));
        return byStatus.entrySet().stream()
                .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue()))
                .toList();
    }

    // ─── Tables ───────────────────────────────────────────────────────────────

    private List<Map<String, Object>> agentLeaderboard(List<Conversation> activity) {
        Map<Long, List<Conversation>> byAgent = activity.stream()
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
            long handled = e.getValue().stream()
                    .filter(c -> c.getResolvedAt() != null || "resolved".equals(c.getStatus()))
                    .count();

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
            row.put("handled", handled);
            row.put("avg_first_response_min", avgFirstResponse);
            rows.add(row);
        }
        rows.sort((a, b) -> Long.compare((long) b.get("handled"), (long) a.get("handled")));
        return rows;
    }

    private List<Map<String, Object>> recentConversations(Long wsId, int limit) {
        List<Conversation> conversations = conversationRepository.findTop6ByWorkspaceIdOrderByLastMessageAtDesc(wsId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Conversation c : conversations) {
            Contact contact = c.getContact();
            String name = "Unknown";
            if (contact != null) {
                String full = ((contact.getFirstName() != null ? contact.getFirstName() : "") + " " +
                        (contact.getLastName() != null ? contact.getLastName() : "")).trim();
                if (!full.isEmpty()) name = full;
                else if (contact.getPhoneE164() != null && !contact.getPhoneE164().isBlank()) name = contact.getPhoneE164();
                else if (contact.getEmail() != null && !contact.getEmail().isBlank()) name = contact.getEmail();
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("uuid", c.getUuid());
            row.put("contact", name);
            row.put("status", c.getStatus());
            row.put("unread", c.getUnreadCount() != null ? c.getUnreadCount() : 0);
            row.put("last_message_at", c.getLastMessageAt() != null ? c.getLastMessageAt().toString() : null);
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> recentCampaigns(Long wsId, int limit) {
        List<Campaign> campaigns = campaignRepository.findTop6ByWorkspaceIdOrderByCreatedAtDesc(wsId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Campaign c : campaigns) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("name", c.getName());
            row.put("channel", c.getChannel());
            row.put("status", c.getStatus());
            row.put("recipients", campaignRecipientRepository.countByCampaignId(c.getId()));
            row.put("created_at", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
            out.add(row);
        }
        return out;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private List<Map<String, Object>> fillDateSeriesMultiKey(LocalDateTime from, LocalDateTime to,
                                                               Map<String, Map<String, Long>> byDate, Set<String> keys) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate d = from.toLocalDate(); !d.isAfter(to.toLocalDate()); d = d.plusDays(1)) {
            String dateKey = d.toString();
            Map<String, Long> dayValues = byDate.getOrDefault(dateKey, Map.of());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", dateKey);
            for (String key : keys) {
                row.put(key, dayValues.getOrDefault(key, 0L));
            }
            result.add(row);
        }
        return result;
    }

    private Double pctDelta(double current, double previous) {
        if (previous <= 0.0) {
            return current > 0.0 ? 100.0 : null;
        }
        return Math.round(((current - previous) / previous) * 1000.0) / 10.0;
    }

    private int nz(Integer v) { return v != null ? v : 0; }
}
