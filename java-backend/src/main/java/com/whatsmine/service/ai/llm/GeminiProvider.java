package com.whatsmine.service.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class GeminiProvider implements LlmProviderInterface {

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta";

    private final String apiKey;
    private final String chatModel;
    private final String embedModel;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiProvider(String apiKey, String chatModel, String embedModel) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.chatModel = chatModel != null && !chatModel.isEmpty() ? chatModel : "gemini-3-flash-preview";
        this.embedModel = embedModel != null && !embedModel.isEmpty() ? embedModel : "gemini-embedding-001";
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @SuppressWarnings("unchecked")
    public LlmResponse chat(List<Map<String, String>> messages, Map<String, Object> opts) {
        long start = System.currentTimeMillis();
        String model = opts != null && opts.get("model") != null ? String.valueOf(opts.get("model")) : this.chatModel;
        int maxTokens = opts != null && opts.get("max_tokens") != null ? Integer.parseInt(String.valueOf(opts.get("max_tokens"))) : 1024;

        Map<String, Object> systemInstruction = null;
        List<Map<String, Object>> contents = new ArrayList<>();

        for (Map<String, String> m : messages) {
            String role = m.get("role");
            String text = m.get("content");
            if ("system".equalsIgnoreCase(role)) {
                systemInstruction = Map.of("parts", List.of(Map.of("text", text != null ? text : "")));
            } else {
                String geminiRole = "assistant".equalsIgnoreCase(role) ? "model" : "user";
                contents.add(Map.of("role", geminiRole, "parts", List.of(Map.of("text", text != null ? text : ""))));
            }
        }

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("contents", contents);
        bodyMap.put("generationConfig", Map.of("maxOutputTokens", maxTokens));
        if (systemInstruction != null) {
            bodyMap.put("systemInstruction", systemInstruction);
        }

        try {
            String jsonBody = objectMapper.writeValueAsString(bodyMap);
            String url = BASE + "/models/" + model + ":generateContent?key=" + apiKey;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Gemini chat failed: " + response.body());
            }

            Map<String, Object> resMap = objectMapper.readValue(response.body(), Map.class);
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) resMap.get("candidates");
            String content = "";
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> candidate = candidates.get(0);
                Map<String, Object> candContent = (Map<String, Object>) candidate.get("content");
                if (candContent != null) {
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) candContent.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        content = String.valueOf(parts.get(0).getOrDefault("text", ""));
                    }
                }
            }

            Map<String, Object> meta = (Map<String, Object>) resMap.get("usageMetadata");
            int promptTokens = meta != null && meta.get("promptTokenCount") != null ? ((Number) meta.get("promptTokenCount")).intValue() : 0;
            int completionTokens = meta != null && meta.get("candidatesTokenCount") != null ? ((Number) meta.get("candidatesTokenCount")).intValue() : 0;
            long latency = System.currentTimeMillis() - start;

            return new LlmResponse(content, promptTokens, completionTokens, model, latency);

        } catch (Exception e) {
            throw new RuntimeException("Gemini chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<List<Double>> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) return Collections.emptyList();

        List<Map<String, Object>> requests = texts.stream()
                .map(text -> (Map<String, Object>) Map.of(
                        "model", "models/" + embedModel,
                        "content", Map.of("parts", List.of(Map.of("text", text)))
                ))
                .toList();

        Map<String, Object> bodyMap = Map.of("requests", requests);

        try {
            String jsonBody = objectMapper.writeValueAsString(bodyMap);
            String url = BASE + "/models/" + embedModel + ":batchEmbedContents?key=" + apiKey;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Gemini batch embed failed: " + response.body());
            }

            Map<String, Object> resMap = objectMapper.readValue(response.body(), Map.class);
            List<Map<String, Object>> embeddingsList = (List<Map<String, Object>>) resMap.get("embeddings");

            List<List<Double>> result = new ArrayList<>();
            if (embeddingsList != null) {
                for (Map<String, Object> emb : embeddingsList) {
                    List<Number> values = (List<Number>) emb.get("values");
                    List<Double> doubles = values != null ? values.stream().map(Number::doubleValue).toList() : Collections.emptyList();
                    result.add(doubles);
                }
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Gemini batch embed failed: " + e.getMessage(), e);
        }
    }
}
