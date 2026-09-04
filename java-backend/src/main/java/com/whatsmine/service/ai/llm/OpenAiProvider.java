package com.whatsmine.service.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class OpenAiProvider implements LlmProviderInterface {

    private static final String BASE = "https://api.openai.com/v1";

    private final String apiKey;
    private final String chatModel;
    private final String embedModel;
    private final String organization;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiProvider(String apiKey, String chatModel, String embedModel, String organization) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.chatModel = chatModel != null && !chatModel.isEmpty() ? chatModel : "gpt-4o-mini";
        this.embedModel = embedModel != null && !embedModel.isEmpty() ? embedModel : "text-embedding-3-small";
        this.organization = organization;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @SuppressWarnings("unchecked")
    public LlmResponse chat(List<Map<String, String>> messages, Map<String, Object> opts) {
        long start = System.currentTimeMillis();

        String model = opts != null && opts.get("model") != null ? String.valueOf(opts.get("model")) : this.chatModel;
        int maxTokens = opts != null && opts.get("max_tokens") != null ? Integer.parseInt(String.valueOf(opts.get("max_tokens"))) : 1024;
        double temperature = opts != null && opts.get("temperature") != null ? Double.parseDouble(String.valueOf(opts.get("temperature"))) : 0.7;

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("model", model);
        bodyMap.put("messages", messages);
        bodyMap.put("max_tokens", maxTokens);
        bodyMap.put("temperature", temperature);

        try {
            String jsonBody = objectMapper.writeValueAsString(bodyMap);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            if (organization != null && !organization.isEmpty()) {
                builder.header("OpenAI-Organization", organization);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("OpenAI chat failed: " + response.body());
            }

            Map<String, Object> resMap = objectMapper.readValue(response.body(), Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resMap.get("choices");
            String content = "";
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                if (msg != null && msg.get("content") != null) {
                    content = String.valueOf(msg.get("content"));
                }
            }

            Map<String, Object> usage = (Map<String, Object>) resMap.get("usage");
            int promptTokens = usage != null && usage.get("prompt_tokens") != null ? ((Number) usage.get("prompt_tokens")).intValue() : 0;
            int completionTokens = usage != null && usage.get("completion_tokens") != null ? ((Number) usage.get("completion_tokens")).intValue() : 0;
            String resModel = resMap.get("model") != null ? String.valueOf(resMap.get("model")) : model;
            long latency = System.currentTimeMillis() - start;

            return new LlmResponse(content, promptTokens, completionTokens, resModel, latency);

        } catch (Exception e) {
            throw new RuntimeException("OpenAI chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<List<Double>> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) return Collections.emptyList();

        Map<String, Object> bodyMap = Map.of(
                "model", embedModel,
                "input", texts
        );

        try {
            String jsonBody = objectMapper.writeValueAsString(bodyMap);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/embeddings"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("OpenAI embed failed: " + response.body());
            }

            Map<String, Object> resMap = objectMapper.readValue(response.body(), Map.class);
            List<Map<String, Object>> data = (List<Map<String, Object>>) resMap.get("data");

            List<List<Double>> embeddings = new ArrayList<>();
            if (data != null) {
                for (Map<String, Object> item : data) {
                    List<Number> vec = (List<Number>) item.get("embedding");
                    List<Double> doubles = vec != null ? vec.stream().map(Number::doubleValue).toList() : Collections.emptyList();
                    embeddings.add(doubles);
                }
            }
            return embeddings;
        } catch (Exception e) {
            throw new RuntimeException("OpenAI embed failed: " + e.getMessage(), e);
        }
    }
}
