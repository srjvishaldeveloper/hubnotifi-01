package com.whatsmine.service.admin;

import com.whatsmine.model.AiChatbot;
import com.whatsmine.model.AiRun;
import com.whatsmine.model.Client;
import com.whatsmine.model.Message;
import com.whatsmine.model.PaymentTransaction;
import com.whatsmine.model.Plan;
import com.whatsmine.model.Subscription;
import com.whatsmine.model.User;
import com.whatsmine.model.Workspace;
import com.whatsmine.repository.AiChatbotRepository;
import com.whatsmine.repository.AiRunRepository;
import com.whatsmine.repository.ClientRepository;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.ConversationRepository;
import com.whatsmine.repository.MessageRepository;
import com.whatsmine.repository.PaymentTransactionRepository;
import com.whatsmine.repository.PlanRepository;
import com.whatsmine.repository.SubscriptionRepository;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.repository.WorkspaceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Java port of PHP's AnalyticsService — admin-dashboard slice only (the
 * per-workspace client/campaign/AI/inbox analytics methods stay unported
 * until those report pages themselves get built; see readiness board
 * "rep-pages"). Replaces InertiaDemoController's hardcoded
 * {users: 120, revenue: 5400} with real queries.
 */
@Service
public class AdminAnalyticsService {

    private static final List<String> ACTIVE_STATUSES = List.of("active", "trialing");
    private static final List<Integer> ALLOWED_RANGES = List.of(7, 30, 90);

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final MessageRepository messageRepository;
    private final ContactRepository contactRepository;
    private final ConversationRepository conversationRepository;
    private final AiRunRepository aiRunRepository;
    private final AiChatbotRepository aiChatbotRepository;
    private final WorkspaceRepository workspaceRepository;

    public AdminAnalyticsService(
            SubscriptionRepository subscriptionRepository, PlanRepository planRepository,
            ClientRepository clientRepository, UserRepository userRepository,
            PaymentTransactionRepository paymentTransactionRepository, MessageRepository messageRepository,
            ContactRepository contactRepository, ConversationRepository conversationRepository,
            AiRunRepository aiRunRepository, AiChatbotRepository aiChatbotRepository,
            WorkspaceRepository workspaceRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.messageRepository = messageRepository;
        this.contactRepository = contactRepository;
        this.conversationRepository = conversationRepository;
        this.aiRunRepository = aiRunRepository;
        this.aiChatbotRepository = aiChatbotRepository;
        this.workspaceRepository = workspaceRepository;
    }

    /** Monthly-equivalent price for one subscription (yearly plans normalised /12), matching PHP's computeMrr(). */
    private double monthlyEquivalentCents(Subscription sub, Map<Long, Plan> plansById) {
        Plan plan = plansById.get(sub.getPlanId());
        if (plan == null) return 0.0;
        long cents = plan.getMonthlyPriceCents() != null ? plan.getMonthlyPriceCents()
                : plan.getPriceCents() != null ? plan.getPriceCents() : 0L;
        if (sub.getRenewsAt() != null && sub.getStartsAt() != null
                && java.time.temporal.ChronoUnit.MONTHS.between(sub.getStartsAt(), sub.getRenewsAt()) >= 12) {
            long yearly = plan.getYearlyPriceCents() != null ? plan.getYearlyPriceCents()
                    : plan.getPriceCents() != null ? plan.getPriceCents() : 0L;
            cents = yearly / 12;
        }
        return cents / 100.0;
    }

    private Map<Long, Plan> plansById() {
        return planRepository.findAll().stream().collect(Collectors.toMap(Plan::getId, p -> p));
    }

    public double computeMrr() {
        Map<Long, Plan> plans = plansById();
        return subscriptionRepository.findAll().stream()
                .filter(s -> ACTIVE_STATUSES.contains(s.getStatus()))
                .mapToDouble(s -> monthlyEquivalentCents(s, plans))
                .sum();
    }

    public List<Map<String, Object>> mrrTrend(int months) {
        Map<Long, Plan> plans = plansById();
        List<Subscription> all = subscriptionRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (int i = months - 1; i >= 0; i--) {
            LocalDateTime month = now.minusMonths(i);
            LocalDateTime monthStart = month.withDayOfMonth(1).toLocalDate().atStartOfDay();
            LocalDateTime monthEnd = monthStart.plusMonths(1).minusSeconds(1);
            String label = String.format("%04d-%02d", month.getYear(), month.getMonthValue());

            double mrr = all.stream()
                    .filter(s -> ACTIVE_STATUSES.contains(s.getStatus()))
                    .filter(s -> s.getStartsAt() != null && !s.getStartsAt().isAfter(monthEnd))
                    .filter(s -> s.getRenewsAt() == null || !s.getRenewsAt().isBefore(monthStart))
                    .mapToDouble(s -> monthlyEquivalentCents(s, plans))
                    .sum();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", label);
            row.put("mrr", Math.round(mrr * 100.0) / 100.0);
            result.add(row);
        }
        return result;
    }

    public List<Map<String, Object>> revenueByDay(LocalDateTime from, LocalDateTime to) {
        List<PaymentTransaction> txns = paymentTransactionRepository.findByStatusAndCreatedAtBetween("succeeded", from, to);
        Map<String, Long> byDate = new LinkedHashMap<>();
        for (PaymentTransaction t : txns) {
            String date = t.getCreatedAt().toLocalDate().toString();
            byDate.merge(date, (long) t.getAmountCents(), Long::sum);
        }
        return fillDateSeries(from, to, byDate, "revenue");
    }

    public List<Map<String, Object>> newClientsByDay(LocalDateTime from, LocalDateTime to) {
        List<Client> clients = clientRepository.findByCreatedAtBetween(from, to);
        Map<String, Long> byDate = new LinkedHashMap<>();
        for (Client c : clients) {
            byDate.merge(c.getCreatedAt().toLocalDate().toString(), 1L, Long::sum);
        }
        return fillDateSeries(from, to, byDate, "clients");
    }

    public List<Map<String, Object>> platformMessageVolumeByChannel(LocalDateTime from, LocalDateTime to) {
        List<Message> messages = messageRepository.findByCreatedAtBetween(from, to);
        Map<String, Map<String, Long>> byDateAndChannel = new LinkedHashMap<>();
        Set<String> channels = new java.util.LinkedHashSet<>();
        for (Message m : messages) {
            if (m.getCreatedAt() == null) continue;
            String date = m.getCreatedAt().toLocalDate().toString();
            String channel = m.getChannel() != null ? m.getChannel() : "unknown";
            channels.add(channel);
            byDateAndChannel.computeIfAbsent(date, d -> new LinkedHashMap<>()).merge(channel, 1L, Long::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (java.time.LocalDate d = from.toLocalDate(); !d.isAfter(to.toLocalDate()); d = d.plusDays(1)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.toString());
            Map<String, Long> counts = byDateAndChannel.getOrDefault(d.toString(), Map.of());
            for (String ch : channels) {
                row.put(ch, counts.getOrDefault(ch, 0L));
            }
            result.add(row);
        }
        return result;
    }

    public List<Map<String, Object>> platformChannelMix(LocalDateTime from, LocalDateTime to) {
        List<Message> messages = messageRepository.findByCreatedAtBetween(from, to);
        Map<String, Long> byChannel = new LinkedHashMap<>();
        for (Message m : messages) {
            byChannel.merge(m.getChannel() != null ? m.getChannel() : "unknown", 1L, Long::sum);
        }
        return byChannel.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", e.getKey());
                    row.put("value", e.getValue());
                    return row;
                }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> planDistribution() {
        Map<Long, Plan> plans = plansById();
        Map<Long, Long> byPlan = new LinkedHashMap<>();
        for (Subscription s : subscriptionRepository.findAll()) {
            if (ACTIVE_STATUSES.contains(s.getStatus())) {
                byPlan.merge(s.getPlanId(), 1L, Long::sum);
            }
        }
        return byPlan.entrySet().stream().map(e -> {
            Plan plan = plans.get(e.getKey());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", plan != null ? plan.getName() : "Unknown");
            row.put("value", e.getValue());
            return row;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> subscriptionStatusBreakdown() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Subscription s : subscriptionRepository.findAll()) {
            String status = s.getStatus() != null ? s.getStatus() : "unknown";
            byStatus.merge(status, 1L, Long::sum);
        }
        return byStatus.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    String name = e.getKey().isEmpty() ? "Unknown" : Character.toUpperCase(e.getKey().charAt(0)) + e.getKey().substring(1);
                    row.put("name", name);
                    row.put("value", e.getValue());
                    return row;
                }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> topWorkspacesByAiCost(int limit) {
        Map<Long, AiChatbot> chatbots = aiChatbotRepository.findAll().stream()
                .collect(Collectors.toMap(AiChatbot::getId, c -> c));
        Map<Long, Long> costByWorkspace = new LinkedHashMap<>();
        for (AiRun run : aiRunRepository.findAll()) {
            AiChatbot bot = chatbots.get(run.getChatbotId());
            if (bot == null) continue;
            costByWorkspace.merge(bot.getWorkspaceId(), (long) run.getCostCents(), Long::sum);
        }
        Map<Long, Workspace> workspaces = workspaceRepository.findAllById(costByWorkspace.keySet()).stream()
                .collect(Collectors.toMap(Workspace::getId, w -> w));

        return costByWorkspace.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> {
                    Workspace ws = workspaces.get(e.getKey());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("workspace_id", e.getKey());
                    row.put("name", ws != null ? ws.getName() : "Unknown");
                    row.put("total_cost_cents", e.getValue());
                    return row;
                }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> recentClients(int limit) {
        List<Client> clients = clientRepository.findTop6ByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Client c : clients.stream().limit(limit).toList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("name", c.getName());
            row.put("email", c.getEmail());
            row.put("status", c.getStatus());
            row.put("users_count", userRepository.findByClientId(c.getId()).size());
            row.put("created_at", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
            result.add(row);
        }
        return result;
    }

    public List<Map<String, Object>> recentPayments(int limit) {
        List<PaymentTransaction> txns = paymentTransactionRepository.findTop6ByOrderByCreatedAtDesc();
        Map<Long, User> users = userRepository.findAllById(
                txns.stream().map(PaymentTransaction::getUserId).filter(java.util.Objects::nonNull).toList()
        ).stream().collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> result = new ArrayList<>();
        for (PaymentTransaction t : txns.stream().limit(limit).toList()) {
            User u = users.get(t.getUserId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", t.getId());
            row.put("amount_cents", t.getAmountCents());
            row.put("currency", t.getCurrencyCode() != null ? t.getCurrencyCode() : "USD");
            row.put("status", t.getStatus());
            row.put("gateway", t.getGateway());
            row.put("user", u != null ? (u.getName() != null ? u.getName() : u.getEmail()) : null);
            row.put("created_at", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
            result.add(row);
        }
        return result;
    }

    /** Full Admin/Dashboard.jsx prop payload: {range, stats, charts, tables, warnings}. */
    public Map<String, Object> dashboardProps(int requestedRange) {
        int range = ALLOWED_RANGES.contains(requestedRange) ? requestedRange : 30;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime to = now.toLocalDate().atTime(23, 59, 59);
        LocalDateTime from = to.toLocalDate().minusDays(range - 1L).atStartOfDay();
        LocalDateTime prevTo = from.minusSeconds(1);
        LocalDateTime prevFrom = prevTo.toLocalDate().minusDays(range - 1L).atStartOfDay();
        LocalDateTime startOfMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay();

        double mrr = computeMrr();
        long subscriptionsActive = subscriptionRepository.findAll().stream()
                .filter(s -> ACTIVE_STATUSES.contains(s.getStatus())).count();
        long trialing = subscriptionRepository.findAll().stream()
                .filter(s -> "trialing".equals(s.getStatus())).count();

        long clientsCount = clientRepository.count();
        long newClients = clientRepository.countByCreatedAtBetween(from, to);
        long newClientsPrev = clientRepository.countByCreatedAtBetween(prevFrom, prevTo);

        long usersCount = userRepository.count();
        long newUsers = userRepository.countByCreatedAtBetween(from, to);

        long revenuePeriod = paymentTransactionRepository.findByStatusAndCreatedAtBetween("succeeded", from, to)
                .stream().mapToLong(PaymentTransaction::getAmountCents).sum();
        long revenuePrev = paymentTransactionRepository.findByStatusAndCreatedAtBetween("succeeded", prevFrom, prevTo)
                .stream().mapToLong(PaymentTransaction::getAmountCents).sum();
        long paymentsThisMonth = paymentTransactionRepository.findByStatusAndCreatedAtGreaterThanEqual("succeeded", startOfMonth)
                .stream().mapToLong(PaymentTransaction::getAmountCents).sum();

        List<Message> messagesInRange = messageRepository.findByCreatedAtBetween(from, to);
        long messagesPeriod = messagesInRange.size();
        long messagesPrev = messageRepository.findByCreatedAtBetween(prevFrom, prevTo).size();

        double arpu = subscriptionsActive > 0 ? Math.round((mrr / subscriptionsActive) * 100.0) / 100.0 : 0.0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("mrr", Math.round(mrr * 100.0) / 100.0);
        stats.put("arpu", arpu);
        stats.put("subscriptions_active", subscriptionsActive);
        stats.put("subscriptions_trialing", trialing);
        stats.put("clients_count", clientsCount);
        stats.put("new_clients", newClients);
        stats.put("new_clients_delta", pctDelta(newClients, newClientsPrev));
        stats.put("users_count", usersCount);
        stats.put("new_users", newUsers);
        stats.put("revenue_period_cents", revenuePeriod);
        stats.put("revenue_delta", pctDelta(revenuePeriod, revenuePrev));
        stats.put("payments_this_month_cents", paymentsThisMonth);
        stats.put("messages_period", messagesPeriod);
        stats.put("messages_delta", pctDelta(messagesPeriod, messagesPrev));
        stats.put("contacts_total", contactRepository.count());
        stats.put("conversations_total", conversationRepository.count());

        Map<String, Object> charts = new LinkedHashMap<>();
        charts.put("mrr_trend", mrrTrend(12));
        charts.put("revenue_by_day", revenueByDay(from, to));
        charts.put("new_clients_by_day", newClientsByDay(from, to));
        charts.put("plan_distribution", planDistribution());
        charts.put("subscription_status", subscriptionStatusBreakdown());
        charts.put("messages_by_day", platformMessageVolumeByChannel(from, to));
        charts.put("channel_mix", platformChannelMix(from, to));
        charts.put("top_ai_workspaces", topWorkspacesByAiCost(10));

        Map<String, Object> tables = new LinkedHashMap<>();
        tables.put("recent_clients", recentClients(6));
        tables.put("recent_payments", recentPayments(6));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("range", range);
        props.put("stats", stats);
        props.put("charts", charts);
        props.put("tables", tables);
        props.put("warnings", List.of());
        return props;
    }

    /** Percentage change between two periods; null when there's no comparable baseline (matches PHP). */
    private Double pctDelta(double current, double previous) {
        if (previous <= 0.0) {
            return current > 0.0 ? 100.0 : null;
        }
        return Math.round(((current - previous) / previous) * 1000.0) / 10.0;
    }

    private List<Map<String, Object>> fillDateSeries(LocalDateTime from, LocalDateTime to, Map<String, Long> byDate, String key) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (java.time.LocalDate d = from.toLocalDate(); !d.isAfter(to.toLocalDate()); d = d.plusDays(1)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.toString());
            row.put(key, byDate.getOrDefault(d.toString(), 0L));
            result.add(row);
        }
        return result;
    }
}
