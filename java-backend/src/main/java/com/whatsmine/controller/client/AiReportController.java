package com.whatsmine.controller.client;

import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.AiChatbot;
import com.whatsmine.model.AiRun;
import com.whatsmine.repository.AiChatbotRepository;
import com.whatsmine.repository.AiRunRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI usage/cost report — porting PHP's Reports\AiReportController against
 * AiRun (promptTokens/completionTokens/costCents/latencyMs/model/status),
 * the same real, already-ported LLM usage data AiRun capture uses.
 * Renders the existing client/Reports/Ai/Index page.
 */
@RestController
@RequestMapping("/app/reports/ai")
public class AiReportController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private AiChatbotRepository chatbotRepository;

    @Autowired
    private AiRunRepository aiRunRepository;

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

        List<AiChatbot> chatbots = chatbotRepository.findByWorkspaceIdOrderByIdDesc(workspaceId);
        Map<Long, String> nameById = chatbots.stream().collect(Collectors.toMap(AiChatbot::getId, AiChatbot::getName));

        List<AiRun> runs = chatbots.isEmpty() ? List.of() : aiRunRepository.findByChatbotIdIn(chatbots.stream().map(AiChatbot::getId).toList());
        List<AiRun> inRange = runs.stream()
                .filter(r -> r.getCreatedAt() != null && !r.getCreatedAt().isBefore(fromDt) && !r.getCreatedAt().isAfter(toDt))
                .toList();

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("kpis", kpis(inRange));
        props.put("tokensByDay", tokensByDay(inRange, fromDate, toDate));
        props.put("tokensByModel", tokensByModel(inRange));
        props.put("topChatbots", topChatbots(inRange, nameById));
        props.put("dateRange", Map.of("from", fromDate.format(DAY), "to", toDate.format(DAY)));

        return inertiaRenderer.render("client/Reports/Ai/Index", props, request);
    }

    private Map<String, Object> kpis(List<AiRun> runs) {
        long totalRuns = runs.size();
        long totalTokens = runs.stream().mapToLong(r -> nz(r.getPromptTokens()) + nz(r.getCompletionTokens())).sum();
        long totalCostCents = runs.stream().mapToLong(r -> nz(r.getCostCents())).sum();
        double avgLatency = runs.isEmpty() ? 0 : runs.stream().mapToInt(r -> nz(r.getLatencyMs())).average().orElse(0);
        long errors = runs.stream().filter(r -> r.getStatus() != null && !"ok".equals(r.getStatus())).count();
        double errorRate = totalRuns == 0 ? 0 : Math.round((errors * 1000.0) / totalRuns) / 10.0;

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("total_runs", totalRuns);
        kpis.put("total_tokens", totalTokens);
        kpis.put("total_cost_cents", totalCostCents);
        kpis.put("avg_latency_ms", Math.round(avgLatency));
        kpis.put("error_rate", errorRate);
        return kpis;
    }

    private List<Map<String, Object>> tokensByDay(List<AiRun> runs, LocalDate from, LocalDate to) {
        Map<String, long[]> byDay = new HashMap<>();
        for (AiRun r : runs) {
            String key = r.getCreatedAt().format(DAY);
            long[] pc = byDay.computeIfAbsent(key, k -> new long[2]);
            pc[0] += nz(r.getPromptTokens());
            pc[1] += nz(r.getCompletionTokens());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            String key = d.format(DAY);
            long[] pc = byDay.getOrDefault(key, new long[2]);
            result.add(Map.of("date", key, "prompt", pc[0], "completion", pc[1]));
        }
        return result;
    }

    private List<Map<String, Object>> tokensByModel(List<AiRun> runs) {
        Map<String, Long> byModel = runs.stream()
                .collect(Collectors.groupingBy(r -> r.getModel() != null ? r.getModel() : "unknown",
                        Collectors.summingLong(r -> nz(r.getPromptTokens()) + nz(r.getCompletionTokens()))));
        return byModel.entrySet().stream()
                .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue()))
                .toList();
    }

    private List<Map<String, Object>> topChatbots(List<AiRun> runs, Map<Long, String> nameById) {
        Map<Long, List<AiRun>> byChatbot = runs.stream().collect(Collectors.groupingBy(AiRun::getChatbotId));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Long, List<AiRun>> e : byChatbot.entrySet()) {
            List<AiRun> chatbotRuns = e.getValue();
            long tokens = chatbotRuns.stream().mapToLong(r -> nz(r.getPromptTokens()) + nz(r.getCompletionTokens())).sum();
            double avgLatency = chatbotRuns.stream().mapToInt(r -> nz(r.getLatencyMs())).average().orElse(0);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("chatbot_id", e.getKey());
            row.put("name", nameById.getOrDefault(e.getKey(), "Unknown"));
            row.put("runs", chatbotRuns.size());
            row.put("tokens", tokens);
            row.put("avg_latency_ms", Math.round(avgLatency));
            rows.add(row);
        }
        rows.sort((a, b) -> Integer.compare((int) b.get("runs"), (int) a.get("runs")));
        return rows;
    }

    private int nz(Integer v) {
        return v != null ? v : 0;
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
