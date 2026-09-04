package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.AiKbDocument;
import com.whatsmine.model.AiProviderConfig;
import com.whatsmine.model.AiRun;
import com.whatsmine.repository.AiChatbotRepository;
import com.whatsmine.repository.AiKbDocumentRepository;
import com.whatsmine.repository.AiKnowledgeBaseRepository;
import com.whatsmine.repository.AiProviderConfigRepository;
import com.whatsmine.repository.AiRunRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/ai")
public class AdminAiDashboardController {

    private final AiProviderConfigRepository providerConfigRepository;
    private final AiRunRepository aiRunRepository;
    private final AiKnowledgeBaseRepository knowledgeBaseRepository;
    private final AiKbDocumentRepository kbDocumentRepository;
    private final AiChatbotRepository chatbotRepository;

    public AdminAiDashboardController(
            AiProviderConfigRepository providerConfigRepository,
            AiRunRepository aiRunRepository,
            AiKnowledgeBaseRepository knowledgeBaseRepository,
            AiKbDocumentRepository kbDocumentRepository,
            AiChatbotRepository chatbotRepository) {
        this.providerConfigRepository = providerConfigRepository;
        this.aiRunRepository = aiRunRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.kbDocumentRepository = kbDocumentRepository;
        this.chatbotRepository = chatbotRepository;
    }

    @GetMapping
    public Object index() {
        List<AiProviderConfig> enabledConfigs = providerConfigRepository.findAll().stream()
                .filter(AiProviderConfig::isEnabled)
                .collect(Collectors.toList());

        Map<String, Long> providerStats = enabledConfigs.stream()
                .collect(Collectors.groupingBy(AiProviderConfig::getProvider, Collectors.counting()));

        long configuredWorkspaces = enabledConfigs.stream()
                .map(AiProviderConfig::getWorkspaceId)
                .distinct()
                .count();

        LocalDateTime since30 = LocalDateTime.now().minusDays(30);
        List<AiRun> recentRuns = aiRunRepository.findAll().stream()
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(since30))
                .collect(Collectors.toList());

        long totalTokens = recentRuns.stream()
                .mapToLong(r -> (r.getPromptTokens() != null ? r.getPromptTokens() : 0) + (r.getCompletionTokens() != null ? r.getCompletionTokens() : 0))
                .sum();
        long errorRuns = recentRuns.stream().filter(r -> "error".equals(r.getStatus())).count();
        double avgLatency = recentRuns.stream()
                .mapToInt(r -> r.getLatencyMs() != null ? r.getLatencyMs() : 0)
                .average().orElse(0);

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("total_tokens", totalTokens);
        usage.put("total_runs", recentRuns.size());
        usage.put("error_runs", errorRuns);
        usage.put("avg_latency_ms", (int) avgLatency);

        Map<String, long[]> byModel = new LinkedHashMap<>();
        for (AiRun r : recentRuns) {
            if (r.getModel() == null) continue;
            long tokens = (r.getPromptTokens() != null ? r.getPromptTokens() : 0) + (r.getCompletionTokens() != null ? r.getCompletionTokens() : 0);
            byModel.computeIfAbsent(r.getModel(), k -> new long[2]);
            byModel.get(r.getModel())[0] += 1;
            byModel.get(r.getModel())[1] += tokens;
        }
        List<Map<String, Object>> topModels = byModel.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("model", e.getKey());
                    m.put("runs", e.getValue()[0]);
                    m.put("tokens", e.getValue()[1]);
                    return m;
                })
                .collect(Collectors.toList());

        LocalDateTime since14 = LocalDateTime.now().minusDays(14);
        DateTimeFormatter dayFmt = DateTimeFormatter.ISO_LOCAL_DATE;
        Map<String, long[]> byDay = new LinkedHashMap<>();
        aiRunRepository.findAll().stream()
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(since14))
                .forEach(r -> {
                    String day = r.getCreatedAt().toLocalDate().format(dayFmt);
                    long tokens = (r.getPromptTokens() != null ? r.getPromptTokens() : 0) + (r.getCompletionTokens() != null ? r.getCompletionTokens() : 0);
                    byDay.computeIfAbsent(day, k -> new long[2]);
                    byDay.get(day)[0] += tokens;
                    byDay.get(day)[1] += 1;
                });
        List<Map<String, Object>> dailyUsage = byDay.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", e.getKey());
                    m.put("tokens", e.getValue()[0]);
                    m.put("runs", e.getValue()[1]);
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Long> documentStats = kbDocumentRepository.findAll().stream()
                .collect(Collectors.groupingBy(AiKbDocument::getStatus, Collectors.counting()));

        Map<String, Object> qdrant = new LinkedHashMap<>();
        qdrant.put("configured", false);
        qdrant.put("healthy", false);
        qdrant.put("url", null);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("providerStats", providerStats);
        props.put("configuredWorkspaces", configuredWorkspaces);
        props.put("qdrant", qdrant);
        props.put("usage", usage);
        props.put("topModels", topModels);
        props.put("dailyUsage", dailyUsage);
        props.put("kbCount", knowledgeBaseRepository.count());
        props.put("documentStats", documentStats);
        props.put("chatbotCount", chatbotRepository.count());
        props.put("activeChatbotCount", chatbotRepository.findAll().stream().filter(c -> c.isEnabled()).count());

        return Inertia.render("Admin/AI/Dashboard", props);
    }
}
