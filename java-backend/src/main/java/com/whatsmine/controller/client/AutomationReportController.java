package com.whatsmine.controller.client;

import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.Automation;
import com.whatsmine.model.AutomationRun;
import com.whatsmine.repository.AutomationRepository;
import com.whatsmine.repository.AutomationRunRepository;
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
 * Automation run report — mirrors the Inbox/AI report controllers' shape
 * (per-day series + dateRange query params) since the React page
 * (client/Reports/Automation/Index.jsx) expects runsByStatus/runsPerAutomation
 * as arrays and reads dateRange.from/to directly on mount.
 */
@RestController
@RequestMapping("/app/reports/automations")
public class AutomationReportController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private AutomationRepository automationRepository;

    @Autowired
    private AutomationRunRepository automationRunRepository;

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

        List<Automation> automations = automationRepository.findByWorkspaceIdOrderByIdDesc(workspaceId);
        Map<Long, String> nameById = automations.stream()
                .collect(Collectors.toMap(Automation::getId, Automation::getName));

        List<AutomationRun> runs = automationRunRepository.findByWorkspaceAndCreatedAtBetween(workspaceId, fromDt, toDt);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("runsByStatus", runsByStatus(runs, fromDate, toDate));
        props.put("runsPerAutomation", runsPerAutomation(runs, nameById));
        props.put("topErrors", topErrors(runs));
        props.put("dateRange", Map.of("from", fromDate.format(DAY), "to", toDate.format(DAY)));

        return inertiaRenderer.render("client/Reports/Automation/Index", props, request);
    }

    private List<Map<String, Object>> runsByStatus(List<AutomationRun> runs, LocalDate from, LocalDate to) {
        Map<String, Map<String, Long>> byDayStatus = new HashMap<>();
        for (AutomationRun r : runs) {
            if (r.getCreatedAt() == null) continue;
            String day = r.getCreatedAt().format(DAY);
            byDayStatus.computeIfAbsent(day, k -> new HashMap<>())
                    .merge(r.getStatus(), 1L, Long::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            String key = d.format(DAY);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", key);
            row.putAll(byDayStatus.getOrDefault(key, Map.of()));
            result.add(row);
        }
        return result;
    }

    private List<Map<String, Object>> runsPerAutomation(List<AutomationRun> runs, Map<Long, String> nameById) {
        Map<Long, List<AutomationRun>> byAutomation = runs.stream()
                .collect(Collectors.groupingBy(AutomationRun::getAutomationId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<AutomationRun>> e : byAutomation.entrySet()) {
            Long automationId = e.getKey();
            List<AutomationRun> list = e.getValue();
            long completed = list.stream().filter(r -> "completed".equals(r.getStatus())).count();
            long failed = list.stream().filter(r -> "failed".equals(r.getStatus())).count();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("automation_id", automationId);
            row.put("name", nameById.getOrDefault(automationId, "Unknown"));
            row.put("runs", (long) list.size());
            row.put("completed", completed);
            row.put("failed", failed);
            result.add(row);
        }
        result.sort((a, b) -> Long.compare((Long) b.get("runs"), (Long) a.get("runs")));
        return result;
    }

    private List<Map<String, Object>> topErrors(List<AutomationRun> runs) {
        Map<String, Long> byMessage = runs.stream()
                .filter(r -> r.getError() != null && !r.getError().isBlank())
                .collect(Collectors.groupingBy(AutomationRun::getError, Collectors.counting()));

        return byMessage.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(e -> Map.<String, Object>of("message", e.getKey(), "count", e.getValue()))
                .toList();
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
