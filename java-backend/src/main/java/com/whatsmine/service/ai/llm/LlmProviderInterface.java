package com.whatsmine.service.ai.llm;

import java.util.List;
import java.util.Map;

public interface LlmProviderInterface {

    LlmResponse chat(List<Map<String, String>> messages, Map<String, Object> opts);

    List<List<Double>> embed(List<String> texts);
}
