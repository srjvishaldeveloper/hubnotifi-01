package com.whatsmine.service.automation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.service.ai.LlmGateway;
import com.whatsmine.service.ai.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Turns a natural-language description into a complete automation graph
 * (trigger + nodes + edges) using the workspace's configured LLM provider.
 */
@Service
public class WorkflowGenerator {

    private static final Logger log = LoggerFactory.getLogger(WorkflowGenerator.class);

    private static final List<String> NODE_TYPES = List.of(
            "send_whatsapp", "send_template", "send_media", "send_sequence", "quick_replies", "list_message",
            "send_sms", "send_email", "ask_question", "condition", "wait", "webhook", "run_subflow", "ai_reply",
            "add_tag", "remove_tag", "update_contact", "assign_agent", "add_to_campaign", "cta_button",
            "send_location", "send_poll", "run_chatbot", "book_appointment", "google_meet", "whatsapp_form",
            "whatsapp_catalog", "woocommerce_product", "shopify_product", "google_sheets", "google_docs"
    );

    private static final List<String> TRIGGER_TYPES = List.of(
            "contact.created", "contact.tag_added", "message.received", "campaign.sent", "form.submitted",
            "webhook.received", "order.placed", "order.fulfilled", "order.cancelled", "cart.abandoned", "customer.created"
    );

    @Autowired
    private LlmGateway llmGateway;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> generate(Long workspaceId, String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new RuntimeException("Prompt cannot be empty.");
        }

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt()),
                Map.of("role", "user", "content", "Build an automation for this request:\n\n" + prompt.trim())
        );

        LlmResponse response = llmGateway.chat(workspaceId, messages, Map.of("max_tokens", 3000));
        Map<String, Object> spec = decode(response.content());
        if (spec == null) {
            throw new RuntimeException("The AI did not return a valid workflow. Try rephrasing your request.");
        }

        return normalise(spec);
    }

    private String systemPrompt() {
        String nodeTypes = String.join(", ", NODE_TYPES);
        String triggerTypes = String.join(", ", TRIGGER_TYPES);

        return """
                You are an automation workflow architect for a WhatsApp / omnichannel messaging platform.
                Convert the user's request into ONE automation expressed as strict JSON. Output ONLY the JSON
                object — no prose, no markdown, no code fences.

                Shape:
                {
                  "name": "<short title, max 60 chars>",
                  "trigger_type": "<one of: %s>",
                  "trigger_config": { },
                  "nodes": [ { "id": "n1", "type": "<node type>", "data": { ... } } ],
                  "edges": [ { "source": "trigger-1", "target": "n1" }, { "source": "n1", "target": "n2" } ]
                }

                Rules:
                - The trigger is implicit: its node id is always "trigger-1". Do NOT list it in "nodes"; only
                  reference it as an edge source. Every "nodes" entry needs a unique "id" and a valid "type".
                - Connect nodes with "edges". The flow must start with an edge whose source is "trigger-1".
                - Allowed node types: %s.
                """.formatted(triggerTypes, nodeTypes);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decode(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.trim();
        cleaned = cleaned.replaceAll("(?i)^```(?:json)?\\s*", "");
        cleaned = cleaned.replaceAll("\\s*```$", "");

        try {
            return objectMapper.readValue(cleaned.trim(), Map.class);
        } catch (Exception e) {
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start != -1 && end != -1 && end > start) {
                try {
                    return objectMapper.readValue(cleaned.substring(start, end + 1), Map.class);
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalise(Map<String, Object> spec) {
        String triggerType = (String) spec.get("trigger_type");
        if (triggerType == null || !TRIGGER_TYPES.contains(triggerType)) {
            triggerType = "contact.created";
        }

        Map<String, Object> triggerConfig = spec.get("trigger_config") instanceof Map
                ? (Map<String, Object>) spec.get("trigger_config") : new HashMap<>();

        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, String> idMap = new HashMap<>();
        Set<String> used = new HashSet<>();
        int i = 0;

        List<Map<String, Object>> rawNodes = (List<Map<String, Object>>) spec.getOrDefault("nodes", List.of());
        for (Map<String, Object> raw : rawNodes) {
            String type = (String) raw.get("type");
            if (type == null || !NODE_TYPES.contains(type)) continue;

            String origId = raw.get("id") != null ? String.valueOf(raw.get("id")) : "";
            String id = uniqueId(!origId.isEmpty() && !"trigger-1".equals(origId) ? origId : type + "-" + i, used);
            used.add(id);
            if (!origId.isEmpty()) idMap.put(origId, id);

            Map<String, Object> data = raw.get("data") instanceof Map ? new HashMap<>((Map<String, Object>) raw.get("data")) : new HashMap<>();
            data.put("nodeType", type);
            data.put("configured", true);

            Map<String, Object> node = new HashMap<>();
            node.put("id", id);
            node.put("type", type);
            node.put("data", data);
            node.put("position", Map.of("x", 250, "y", 50));
            nodes.add(node);
            i++;
        }

        idMap.put("trigger-1", "trigger-1");
        Map<String, Object> triggerNode = new HashMap<>();
        triggerNode.put("id", "trigger-1");
        triggerNode.put("type", "trigger");
        triggerNode.put("data", Map.of("triggerType", triggerType, "label", "Trigger"));
        triggerNode.put("position", Map.of("x", 250, "y", 50));
        nodes.add(0, triggerNode);

        List<String> validIds = nodes.stream().map(n -> (String) n.get("id")).toList();

        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int e = 0;

        List<Map<String, Object>> rawEdges = (List<Map<String, Object>>) spec.getOrDefault("edges", List.of());
        for (Map<String, Object> raw : rawEdges) {
            String source = idMap.getOrDefault(raw.get("source"), (String) raw.get("source"));
            String target = idMap.getOrDefault(raw.get("target"), (String) raw.get("target"));

            if (source == null || target == null || !validIds.contains(source) || !validIds.contains(target) || source.equals(target)) {
                continue;
            }

            String handle = (String) raw.get("sourceHandle");
            if (!"true".equals(handle) && !"false".equals(handle)) handle = null;

            String key = source + ">" + target + ">" + (handle != null ? handle : "");
            if (seen.contains(key)) continue;
            seen.add(key);

            Map<String, Object> edge = new HashMap<>();
            edge.put("id", "e" + e);
            edge.put("source", source);
            edge.put("target", target);
            edge.put("sourceHandle", handle);
            edges.add(edge);
            e++;
        }

        edges = ensureConnectivity(nodes, edges);
        layout(nodes, edges);

        String name = spec.get("name") != null ? String.valueOf(spec.get("name")).trim() : "AI Automation";
        if (name.length() > 120) name = name.substring(0, 120);

        Map<String, Object> result = new HashMap<>();
        result.put("name", name);
        result.put("trigger_type", triggerType);
        result.put("trigger_config", triggerConfig);
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    private String uniqueId(String candidate, Set<String> used) {
        String id = candidate;
        int n = 1;
        while (used.contains(id)) {
            id = candidate + "-" + n++;
        }
        return id;
    }

    private List<Map<String, Object>> ensureConnectivity(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        List<String> actionIds = nodes.stream().map(n -> (String) n.get("id")).filter(id -> !"trigger-1".equals(id)).toList();
        if (actionIds.isEmpty()) return edges;

        boolean hasTriggerEdge = edges.stream().anyMatch(e -> "trigger-1".equals(e.get("source")));
        if (!hasTriggerEdge) {
            List<Map<String, Object>> newEdges = new ArrayList<>(edges);
            Map<String, Object> triggerEdge = new HashMap<>();
            triggerEdge.put("id", "e-trigger");
            triggerEdge.put("source", "trigger-1");
            triggerEdge.put("target", actionIds.get(0));
            triggerEdge.put("sourceHandle", null);
            newEdges.add(0, triggerEdge);
            return newEdges;
        }

        return edges;
    }

    private void layout(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        Map<String, List<String>> adj = new HashMap<>();
        for (Map<String, Object> edge : edges) {
            String src = (String) edge.get("source");
            String tgt = (String) edge.get("target");
            adj.computeIfAbsent(src, k -> new ArrayList<>()).add(tgt);
        }

        Map<String, Integer> level = new HashMap<>();
        level.put("trigger-1", 0);
        Queue<String> queue = new LinkedList<>();
        queue.add("trigger-1");

        while (!queue.isEmpty()) {
            String cur = queue.poll();
            int curLvl = level.get(cur);
            for (String next : adj.getOrDefault(cur, List.of())) {
                if (!level.containsKey(next)) {
                    level.put(next, curLvl + 1);
                    queue.add(next);
                }
            }
        }

        int maxLvl = level.values().stream().mapToInt(v -> v).max().orElse(0);
        for (Map<String, Object> node : nodes) {
            String id = (String) node.get("id");
            if (!level.containsKey(id)) {
                level.put(id, ++maxLvl);
            }
        }

        Map<Integer, List<String>> byLevel = new HashMap<>();
        for (Map.Entry<String, Integer> entry : level.entrySet()) {
            byLevel.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        Map<String, Map<String, Integer>> pos = new HashMap<>();
        for (Map.Entry<Integer, List<String>> entry : byLevel.entrySet()) {
            int lvl = entry.getKey();
            List<String> ids = entry.getValue();
            int count = ids.size();
            for (int idx = 0; idx < count; idx++) {
                int x = (int) (250 + (idx - (count - 1) / 2.0) * 280);
                int y = 50 + lvl * 150;
                pos.put(ids.get(idx), Map.of("x", x, "y", y));
            }
        }

        for (Map<String, Object> node : nodes) {
            String id = (String) node.get("id");
            if (pos.containsKey(id)) {
                node.put("position", pos.get(id));
            }
        }
    }
}
