package com.whatsmine.service.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class AnthropicProvider implements LlmProviderInterface {

    private static final String BASE = "https://api.anthropic.com/v1";

    private final String apiKey;
    private final String chatModel;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AnthropicProvider(String apiKey, String chatModel) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.chatModel = chatModel != null && !chatModel.isEmpty() ? chatModel : "claude-3-haiku-20240307";
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @SuppressWarnings("unchecked")
    public LlmResponse chat(List<Map<String, String>> messages, Map<String, Object> opts) {
        long start = System.currentTimeMillis();

        String model = opts != null && opts.get("model") != null ? String.valueOf(opts.get("model")) : this.chatModel;
        int maxTokens = opts != null && opts.get("max_tokens") != null ? Integer.parseInt(String.valueOf(opts.get("max_tokens"))) : 1024;

        String systemPrompt = null;
        List<Map<String, String>> turns = new ArrayList<>();

        for (Map<String, String> m : messages) {
            String role = m.get("role");
            String content = m.get("content");
            if ("system".equalsIgnoreCase(role)) {
                systemPrompt = content;
            } else {
                turns.add(Map.of("role", role != null ? role : "user", "content", content != null ? content : ""));
            }
        }

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("model", model);
        bodyMap.put("max_tokens", maxTokens);
        bodyMap.put("messages", turns);
        if (systemPrompt != null) {
            bodyMap.put("system", systemPrompt);
        }

        try {
            String jsonBody = objectMapper.writeValueAsString(bodyMap);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Anthropic chat failed: " + response.body());
            }

            Map<String, Object> resMap = objectMapper.readValue(response.body(), Map.class);
            List<Map<String, Object>> contentList = (List<Map<String, Object>>) resMap.get("content");
            String content = "";
            if (contentList != null && !contentList.isEmpty()) {
                content = String.valueOf(contentList.get(0).getOrDefault("text", ""));
            }

            Map<String, Object> usage = (Map<String, Object>) resMap.get("usage");
            int promptTokens = usage != null && usage.get("input_tokens") != null ? ((Number) usage.get("input_tokens")).intValue() : 0;
            int completionTokens = usage != null && usage.get("output_tokens") != null ? ((Number) usage.get("output_tokens")).intValue() : 0;
            String resModel = resMap.get("model") != null ? String.valueOf(resMap.get("model")) : model;
            long latency = System.currentTimeMillis() - start;

            return new LlmResponse(content, promptTokens, completionTokens, resModel, latency);

        } catch (Exception e) {
            throw new RuntimeException("Anthropic chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<List<Double>> embed(List<String> texts) {
        throw new RuntimeException("Anthropic does not support embeddings natively. Use OpenAI or Gemini.");
    }
}
