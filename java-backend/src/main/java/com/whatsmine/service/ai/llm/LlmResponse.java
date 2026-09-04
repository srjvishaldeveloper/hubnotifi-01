package com.whatsmine.service.ai.llm;

public record LlmResponse(
        String content,
        int promptTokens,
        int completionTokens,
        String model,
        long latencyMs
) {}
