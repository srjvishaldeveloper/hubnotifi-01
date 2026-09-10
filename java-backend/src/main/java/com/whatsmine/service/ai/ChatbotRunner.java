package com.whatsmine.service.ai;

import com.whatsmine.model.AiChatbot;
import com.whatsmine.model.Message;
import com.whatsmine.model.Conversation;
import com.whatsmine.repository.ConversationRepository;
import com.whatsmine.repository.MessageRepository;
import com.whatsmine.service.ai.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ChatbotRunner {

    private static final Logger log = LoggerFactory.getLogger(ChatbotRunner.class);

    @Autowired
    private LlmGateway llmGateway;

    @Autowired
    private EmbeddingStore embeddingStore;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    public String run(AiChatbot bot, Message inboundMessage) {
        if (bot == null || !bot.isEnabled()) return null;

        Conversation conversation = inboundMessage.getConversationId() != null
                ? conversationRepository.findById(inboundMessage.getConversationId()).orElse(null) : null;
        String body = inboundMessage.getBody() != null ? inboundMessage.getBody() : "";
        Long workspaceId = conversation != null ? conversation.getWorkspaceId() : bot.getWorkspaceId();

        // 1. Embed query if KB attached
        List<Double> queryEmbedding = List.of();
        if (bot.getAiKbId() != null) {
            try {
                List<List<Double>> embeddings = llmGateway.embed(workspaceId, List.of(body));
                if (!embeddings.isEmpty()) queryEmbedding = embeddings.get(0);
            } catch (Exception e) {
                log.warn("KB query embedding failed: {}", e.getMessage());
            }
        }

        // 2. Retrieve top-k chunks
        List<EmbeddingStore.SearchResult> searchResults = List.of();
        if (bot.getAiKbId() != null && !queryEmbedding.isEmpty()) {
            int topK = bot.getMaxContextChunks() != null ? bot.getMaxContextChunks() : 5;
            searchResults = embeddingStore.search(bot.getAiKbId(), queryEmbedding, topK);
        }

        // 3. Build system prompt
        String systemPrompt = bot.getSystemPrompt() != null ? bot.getSystemPrompt() : "You are a helpful assistant.";
        if (!searchResults.isEmpty()) {
            StringBuilder sb = new StringBuilder(systemPrompt);
            sb.append("\n\nRelevant context:\n");
            for (int i = 0; i < searchResults.size(); i++) {
                if (i > 0) sb.append("\n\n---\n\n");
                sb.append(searchResults.get(i).chunk().getContent());
            }
            systemPrompt = sb.toString();
        }

        // Load conversation history (last 20 text messages)
        List<Map<String, String>> history = new ArrayList<>();
        if (conversation != null && conversation.getId() != null && conversation.getId() > 0) {
            List<Message> recentMessages = messageRepository.findTop20ByConversationIdOrderByIdDesc(conversation.getId());
            Collections.reverse(recentMessages);
            for (Message m : recentMessages) {
                if (m.getBody() == null || m.getBody().isBlank()) continue;
                if (inboundMessage.getId() != null && inboundMessage.getId().equals(m.getId())) continue;
                String role = "out".equalsIgnoreCase(m.getDirection()) ? "assistant" : "user";
                history.add(Map.of("role", role, "content", m.getBody()));
            }
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.addAll(history);
        messages.add(Map.of("role", "user", "content", body));

        // 4. Call LLM
        try {
            LlmResponse response = llmGateway.chat(
                    workspaceId,
                    messages,
                    Map.of("max_tokens", 512),
                    bot.getId(),
                    conversation != null ? conversation.getId() : null
            );
            return response.content();
        } catch (Exception e) {
            log.error("ChatbotRunner execution error: {}", e.getMessage());
            return bot.getFallbackReply();
        }
    }

    public Map<String, Object> runForApi(AiChatbot bot, String message, Long workspaceId, List<Map<String, String>> history) {
        if (bot == null) return Map.of("reply", "Chatbot not found", "tokens_used", 0);

        List<Double> queryEmbedding = List.of();
        if (bot.getAiKbId() != null) {
            try {
                List<List<Double>> embeddings = llmGateway.embed(workspaceId, List.of(message));
                if (!embeddings.isEmpty()) queryEmbedding = embeddings.get(0);
            } catch (Exception ignored) {}
        }

        List<EmbeddingStore.SearchResult> searchResults = List.of();
        if (bot.getAiKbId() != null && !queryEmbedding.isEmpty()) {
            int topK = bot.getMaxContextChunks() != null ? bot.getMaxContextChunks() : 5;
            searchResults = embeddingStore.search(bot.getAiKbId(), queryEmbedding, topK);
        }

        String systemPrompt = bot.getSystemPrompt() != null ? bot.getSystemPrompt() : "You are a helpful assistant.";
        if (!searchResults.isEmpty()) {
            StringBuilder sb = new StringBuilder(systemPrompt);
            sb.append("\n\nRelevant context:\n");
            for (int i = 0; i < searchResults.size(); i++) {
                if (i > 0) sb.append("\n\n---\n\n");
                sb.append(searchResults.get(i).chunk().getContent());
            }
            systemPrompt = sb.toString();
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        if (history != null) messages.addAll(history);
        messages.add(Map.of("role", "user", "content", message));

        try {
            LlmResponse response = llmGateway.chat(
                    workspaceId,
                    messages,
                    Map.of("max_tokens", 512),
                    bot.getId(),
                    null
            );
            return Map.of(
                    "reply", response.content(),
                    "tokens_used", response.promptTokens() + response.completionTokens()
            );
        } catch (Exception e) {
            log.error("ChatbotRunner.runForApi (Playground) failed for chatbot {}: {}", bot.getId(), e.getMessage(), e);
            return Map.of(
                    "reply", bot.getFallbackReply() != null ? bot.getFallbackReply() : "No response.",
                    "tokens_used", 0
            );
        }
    }
}
