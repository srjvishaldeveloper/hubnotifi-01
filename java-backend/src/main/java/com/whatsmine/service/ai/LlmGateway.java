package com.whatsmine.service.ai;

import com.whatsmine.model.AiRun;
import com.whatsmine.repository.AiRunRepository;
import com.whatsmine.service.ai.llm.LlmManager;
import com.whatsmine.service.ai.llm.LlmProviderInterface;
import com.whatsmine.service.ai.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

    @Autowired
    private LlmManager llmManager;

    @Autowired
    private AiRunRepository aiRunRepository;

    public LlmResponse chat(
            Long workspaceId,
            List<Map<String, String>> messages,
            Map<String, Object> opts,
            Long chatbotId,
            Long conversationId
    ) {
        LlmProviderInterface provider = llmManager.forWorkspace(workspaceId);
        LlmResponse response = provider.chat(messages, opts);

        try {
            AiRun run = new AiRun();
            run.setChatbotId(chatbotId);
            run.setConversationId(conversationId);
            run.setPromptTokens(response.promptTokens());
            run.setCompletionTokens(response.completionTokens());
            run.setCostCents(0);
            run.setLatencyMs((int) response.latencyMs());
            run.setModel(response.model());
            run.setStatus("ok");
            aiRunRepository.save(run);
        } catch (Exception e) {
            log.warn("Failed to save AiRun: {}", e.getMessage());
        }

        log.info("llm.chat workspace_id={} chatbot_id={} model={} prompt_tokens={} completion_tokens={} latency_ms={}",
                workspaceId, chatbotId, response.model(), response.promptTokens(), response.completionTokens(), response.latencyMs());

        return response;
    }

    public LlmResponse chat(Long workspaceId, List<Map<String, String>> messages, Map<String, Object> opts) {
        return chat(workspaceId, messages, opts, null, null);
    }

    public List<List<Double>> embed(Long workspaceId, List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();

        LlmProviderInterface provider = llmManager.forWorkspaceEmbed(workspaceId);
        List<List<Double>> embeddings = provider.embed(texts);

        int tokenEstimate = (int) texts.stream().mapToLong(t -> (long) Math.ceil(t.length() / 4.0)).sum();

        try {
            AiRun run = new AiRun();
            run.setPromptTokens(tokenEstimate);
            run.setCompletionTokens(0);
            run.setCostCents(0);
            run.setLatencyMs(0);
            run.setModel("embed");
            run.setStatus("ok");
            aiRunRepository.save(run);
        } catch (Exception e) {
            log.warn("Failed to save AiRun for embed: {}", e.getMessage());
        }

        return embeddings;
    }
}
