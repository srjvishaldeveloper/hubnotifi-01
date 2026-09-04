package com.whatsmine.service.automation;

import com.whatsmine.model.*;
import com.whatsmine.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Event-driven automation execution engine — Java port of Laravel AutomationEngine.
 * Executes automation nodes sequentially. Each node has a type and data.
 * Edges define the flow between nodes.
 */
@Service
public class AutomationEngine {

    private static final Logger log = LoggerFactory.getLogger(AutomationEngine.class);
    private static final int MAX_STEPS = 100;
    private static final int MAX_TEST_STEPS = 60;

    @Autowired private AutomationRunRepository runRepository;
    @Autowired private AutomationRunLogRepository logRepository;
    @Autowired private AutomationRepository automationRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private ContactTagRepository contactTagRepository;
    @Autowired private com.whatsmine.service.ai.LlmGateway llmGateway;
    @Autowired private com.whatsmine.service.ai.ChatbotRunner chatbotRunner;
    @Autowired private com.whatsmine.repository.AiChatbotRepository chatbotRepository;

    private Map<String, Object> executeAiReply(Map<String, Object> data, AutomationRun run, Map<String, Object> context) {
        Automation auto = automationRepository.findById(run.getAutomationId()).orElse(null);
        if (auto == null) return Map.of("status", "error", "message", "Automation not found.");

        Contact contact = run.getContactId() != null ? contactRepository.findById(run.getContactId()).orElse(null) : null;
        String prompt = str(data.getOrDefault("prompt", "Reply to the user politely."));
        prompt = renderTokens(prompt, contact, context);

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "You are an automated assistant. " + prompt),
                Map.of("role", "user", "content", str(context.getOrDefault("message_body", "Hello")))
        );

        try {
            com.whatsmine.service.ai.llm.LlmResponse response = llmGateway.chat(auto.getWorkspaceId(), messages, Map.of("max_tokens", 512));
            String reply = response.content();
            return Map.of("status", "ok", "message", "AI generated reply.", "context_update", Map.of("last_ai_reply", reply));
        } catch (Exception e) {
            return Map.of("status", "error", "message", "AI reply failed: " + e.getMessage());
        }
    }

    private Map<String, Object> executeRunChatbot(Map<String, Object> data, AutomationRun run, Map<String, Object> context) {
        Automation auto = automationRepository.findById(run.getAutomationId()).orElse(null);
        if (auto == null) return Map.of("status", "error", "message", "Automation not found.");

        String botRef = str(data.getOrDefault("chatbot_id", data.get("chatbot_uuid")));
        if (botRef.isEmpty()) return Map.of("status", "skipped", "message", "No chatbot selected.");

        com.whatsmine.model.AiChatbot bot = null;
        try {
            Long botId = Long.parseLong(botRef);
            bot = chatbotRepository.findByWorkspaceIdAndId(auto.getWorkspaceId(), botId).orElse(null);
        } catch (NumberFormatException e) {
            bot = chatbotRepository.findByWorkspaceIdAndUuid(auto.getWorkspaceId(), botRef).orElse(null);
        }

        if (bot == null) return Map.of("status", "error", "message", "Chatbot not found.");

        String messageText = str(context.getOrDefault("message_body", "Hello"));
        Map<String, Object> apiRes = chatbotRunner.runForApi(bot, messageText, auto.getWorkspaceId(), List.of());
        String reply = str(apiRes.getOrDefault("reply", ""));

        return Map.of("status", "ok", "message", "Executed chatbot '" + bot.getName() + "'.", "context_update", Map.of("last_ai_reply", reply));
    }


    // ─── Entry Points ─────────────────────────────────────────────────────────

    public void triggerForContact(Automation automation, Long contactId, Map<String, Object> context) {
        if (!automation.isActive()) return;

        AutomationRun run = new AutomationRun();
        run.setAutomationId(automation.getId());
        run.setContactId(contactId);
        run.setStatus("pending");
        run.setContext(context != null ? context : new HashMap<>());
        run.setStartedAt(LocalDateTime.now());
        run = runRepository.save(run);

        // Execute synchronously for now; Phase 15 will add async queue
        executeRun(run);
    }

    @Transactional
    public void executeRun(AutomationRun run) {
        run.setStatus("running");
        run = runRepository.save(run);

        Automation automation = automationRepository.findById(run.getAutomationId()).orElse(null);
        if (automation == null) {
            run.setStatus("failed");
            run.setError("Automation not found.");
            run.setCompletedAt(LocalDateTime.now());
            runRepository.save(run);
            return;
        }

        List<Map<String, Object>> nodes = automation.getNodes() != null ? automation.getNodes() : new ArrayList<>();
        List<Map<String, Object>> edges = automation.getEdges() != null ? automation.getEdges() : new ArrayList<>();
        Map<String, Object> context = run.getContext() != null ? new HashMap<>(run.getContext()) : new HashMap<>();

        String currentId;
        if (run.getResumeNodeId() != null) {
            currentId = run.getResumeNodeId();
            run.setResumeNodeId(null);
            runRepository.save(run);
        } else {
            Map<String, Object> triggerNode = findNodeByType(nodes, "trigger");
            if (triggerNode == null) {
                run.setStatus("failed");
                run.setError("No trigger node.");
                run.setCompletedAt(LocalDateTime.now());
                runRepository.save(run);
                return;
            }
            String triggerId = (String) triggerNode.get("id");
            Map<String, Object> firstEdge = findEdgeBySource(edges, triggerId, null);
            currentId = firstEdge != null ? (String) firstEdge.get("target") : null;
        }

        Set<String> visited = new HashSet<>();
        int stepsRemaining = MAX_STEPS;

        while (currentId != null && stepsRemaining-- > 0) {
            if (visited.contains(currentId)) break; // cycle guard
            visited.add(currentId);

            Map<String, Object> node = findNodeById(nodes, currentId);
            if (node == null) break;

            run.setCurrentNodeId(currentId);
            runRepository.save(run);

            Map<String, Object> result = executeNode(node, run, context);
            Map<String, Object> contextUpdate = asMap(result.get("context_update"));
            if (contextUpdate != null) context.putAll(contextUpdate);
            run.setContext(context);
            runRepository.save(run);

            // Log the step
            AutomationRunLog logEntry = new AutomationRunLog();
            logEntry.setRunId(run.getId());
            logEntry.setNodeId(currentId);
            logEntry.setNodeType((String) node.getOrDefault("type", "unknown"));
            String status = (String) result.getOrDefault("status", "ok");
            logEntry.setResult(switch (status) {
                case "error" -> "error";
                case "skipped" -> "skipped";
                default -> "ok";
            });
            logEntry.setMessage((String) result.get("message"));
            logEntry.setOutput(asMap(result.get("output")));
            logRepository.save(logEntry);

            if ("error".equals(status)) {
                run.setStatus("failed");
                run.setError((String) result.get("message"));
                run.setCompletedAt(LocalDateTime.now());
                runRepository.save(run);
                return;
            }

            if ("waiting".equals(status)) {
                return; // Wait node suspends the run
            }

            // Condition branching
            String branch = (String) result.get("branch");
            Map<String, Object> nextEdge = findEdgeBySource(edges, currentId, branch);
            currentId = nextEdge != null ? (String) nextEdge.get("target") : null;
        }

        run.setStatus("completed");
        run.setCompletedAt(LocalDateTime.now());
        runRepository.save(run);

        automation.setRunCount(automation.getRunCount() + 1);
        automationRepository.save(automation);
    }

    // ─── Test Run (dry-run simulation) ────────────────────────────────────────

    public Map<String, Object> testRun(Automation automation, List<Map<String, Object>> nodes,
                                        List<Map<String, Object>> edges, Map<String, Object> extraContext) {
        Map<String, Object> context = new HashMap<>(defaultTestContext());
        if (extraContext != null) context.putAll(extraContext);

        Map<String, Object> trigger = nodes.stream()
                .filter(n -> {
                    String type = (String) n.getOrDefault("type", "");
                    Map<String, Object> data = asMap(n.get("data"));
                    return "trigger".equals(type) || "triggerNode".equals(type)
                            || (data != null && data.containsKey("triggerType"));
                })
                .findFirst().orElse(null);

        if (trigger == null) {
            return Map.of("ok", false, "error", "Add a trigger to start the automation.", "steps", List.of());
        }

        String triggerType = automation.getTriggerType();
        if (triggerType == null || triggerType.isEmpty()) {
            Map<String, Object> triggerData = asMap(trigger.get("data"));
            triggerType = triggerData != null ? (String) triggerData.get("triggerType") : null;
        }
        if (triggerType == null || triggerType.isEmpty()) {
            return Map.of("ok", false, "error", "Pick a trigger type before testing.", "steps", List.of());
        }

        String triggerId = (String) trigger.get("id");
        Map<String, Object> firstEdge = findEdgeBySource(edges, triggerId, null);
        String currentId = firstEdge != null ? (String) firstEdge.get("target") : null;

        if (currentId == null) {
            return Map.of("ok", false, "error", "Connect the trigger to at least one step.", "steps", List.of());
        }

        // Sample contact for test
        Contact sampleContact = sampleContact(automation.getWorkspaceId());

        List<Map<String, Object>> steps = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        int stepsRemaining = MAX_TEST_STEPS;

        while (currentId != null && stepsRemaining-- > 0) {
            if (visited.contains(currentId)) {
                steps.add(Map.of("node_id", currentId, "node_type", "loop", "result", "skipped",
                        "message", "Loop detected — stopping here."));
                break;
            }
            visited.add(currentId);

            String nodeId = currentId;
            Map<String, Object> node = nodes.stream()
                    .filter(n -> nodeId.equals(n.get("id")))
                    .findFirst().orElse(null);
            if (node == null) break;

            Map<String, Object> data = asMap(node.get("data"));
            if (data == null) data = new HashMap<>();
            String type = (String) data.getOrDefault("nodeType", node.getOrDefault("type", "unknown"));

            String branch = null;
            Map<String, Object> result;

            if ("condition".equals(type)) {
                boolean passed = evaluateCondition(data, sampleContact, context);
                branch = passed ? "true" : "false";
                result = conditionResult(passed, (String) data.get("field"),
                        (String) data.getOrDefault("operator", "equals"), data.get("value"));
            } else {
                result = previewNode(type, data, sampleContact, context);
            }

            Map<String, Object> ctxUpdate = asMap(result.get("context_update"));
            if (ctxUpdate != null) context.putAll(ctxUpdate);

            Map<String, Object> step = new HashMap<>();
            step.put("node_id", currentId);
            step.put("node_type", type);
            step.put("label", data.get("label"));
            step.put("result", result.getOrDefault("status", "ok"));
            step.put("message", result.getOrDefault("message", ""));
            step.put("output", result.get("output"));
            step.put("branch", branch);
            steps.add(step);

            if ("error".equals(result.get("status"))) break;

            String finalBranch = branch;
            Map<String, Object> nextEdge = findEdgeBySource(edges, currentId, finalBranch);
            currentId = nextEdge != null ? (String) nextEdge.get("target") : null;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("steps", steps);
        response.put("context", context);
        response.put("contact", Map.of("name", "Test Contact", "email", "test.contact@example.com", "phone", "+15555550123"));
        return response;
    }

    // ─── Node Execution ───────────────────────────────────────────────────────

    private Map<String, Object> executeNode(Map<String, Object> node, AutomationRun run, Map<String, Object> context) {
        String type = (String) node.getOrDefault("type", "unknown");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) node.getOrDefault("data", new HashMap<>());

        try {
            return switch (type) {
                case "wait" -> executeWait(data, run);
                case "add_tag" -> executeTagAction(data, run, "add");
                case "remove_tag" -> executeTagAction(data, run, "remove");
                case "update_contact" -> executeUpdateContact(data, run, context);
                case "webhook" -> executeWebhook(data, run, context);
                case "condition" -> executeCondition(data, run, context);
                case "add_to_campaign" -> Map.of("status", "skipped", "message", "Campaign actions not available in automation test mode.");
                case "assign_agent" -> executeAssignAgent(data, run);
                case "run_subflow" -> executeRunSubflow(data, run, context);
                // Messaging nodes — stubbed for now, full implementation uses WhatsApp client
                case "send_whatsapp", "send_template", "send_media", "send_sequence",
                     "quick_replies", "list_message", "cta_button", "send_location",
                     "send_poll", "whatsapp_form", "whatsapp_catalog" ->
                        executeSendMessage(type, data, run, context);
                case "send_sms" -> Map.of("status", "skipped", "message", "SMS driver not available in Phase 10.");
                case "send_email" -> Map.of("status", "skipped", "message", "Email not available in Phase 10.");
                case "ask_question" -> executeAskQuestion(data, run, context);
                case "ai_reply" -> executeAiReply(data, run, context);
                case "run_chatbot" -> executeRunChatbot(data, run, context);
                // Integration nodes — stubbed
                case "book_appointment", "google_meet", "google_sheets", "google_docs", "google_forms" ->
                        Map.of("status", "skipped", "message", "Google integration not available in Phase 10.");
                case "woocommerce_product", "shopify_product" ->
                        Map.of("status", "skipped", "message", "E-commerce integration not available in Phase 10.");
                default -> Map.of("status", "skipped", "message", "Unknown node type: " + type);
            };
        } catch (Exception e) {
            log.error("AutomationEngine node error [{}]: {}", type, e.getMessage());
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    // ─── Wait ─────────────────────────────────────────────────────────────────

    private Map<String, Object> executeWait(Map<String, Object> data, AutomationRun run) {
        int amount = toInt(data.get("amount"), 1);
        String unit = (String) data.getOrDefault("unit", "minutes");

        // Find next node after wait
        Automation automation = automationRepository.findById(run.getAutomationId()).orElse(null);
        if (automation == null) return Map.of("status", "error", "message", "Automation not found.");

        List<Map<String, Object>> edges = automation.getEdges() != null ? automation.getEdges() : new ArrayList<>();
        Map<String, Object> nextEdge = findEdgeBySource(edges, run.getCurrentNodeId(), null);
        String nextNodeId = nextEdge != null ? (String) nextEdge.get("target") : null;

        run.setStatus("waiting");
        run.setResumeNodeId(nextNodeId);
        runRepository.save(run);

        // In a full implementation, a delayed job would wake this up.
        // For Phase 10, we mark it as waiting. Phase 15 adds the scheduler.
        return Map.of("status", "waiting", "message", "Waiting " + amount + " " + unit + ".");
    }

    // ─── Tag Actions ──────────────────────────────────────────────────────────

    private Map<String, Object> executeTagAction(Map<String, Object> data, AutomationRun run, String action) {
        String tagName = (String) data.get("tag");
        if (tagName == null || tagName.isEmpty() || run.getContactId() == null) {
            return Map.of("status", "skipped", "message", "No tag or contact.");
        }
        Contact contact = contactRepository.findById(run.getContactId()).orElse(null);
        if (contact == null) return Map.of("status", "skipped", "message", "Contact not found.");

        // Find or create tag
        ContactTag tag = contactTagRepository
                .findByWorkspaceIdAndName(contact.getWorkspaceId(), tagName)
                .orElseGet(() -> {
                    ContactTag newTag = new ContactTag();
                    newTag.setWorkspaceId(contact.getWorkspaceId());
                    newTag.setName(tagName);
                    return contactTagRepository.save(newTag);
                });

        String actionLabel = action.substring(0, 1).toUpperCase() + action.substring(1);
        return Map.of("status", "ok", "message", actionLabel + " tag '" + tagName + "'.");
    }

    // ─── Update Contact ───────────────────────────────────────────────────────

    private Map<String, Object> executeUpdateContact(Map<String, Object> data, AutomationRun run, Map<String, Object> context) {
        if (run.getContactId() == null) return Map.of("status", "skipped", "message", "No contact.");
        String field = (String) data.get("field");
        if (field == null || field.isEmpty()) return Map.of("status", "skipped", "message", "No field specified.");

        Contact contact = contactRepository.findById(run.getContactId()).orElse(null);
        if (contact == null) return Map.of("status", "skipped", "message", "Contact not found.");

        String value = renderTokens(str(data.get("value")), contact, context);

        switch (field) {
            case "name" -> {
                String[] parts = value.trim().split("\\s+", 2);
                contact.setFirstName(parts[0]);
                contact.setLastName(parts.length > 1 ? parts[1] : "");
            }
            case "first_name" -> contact.setFirstName(value);
            case "last_name" -> contact.setLastName(value);
            case "phone", "phone_e164" -> contact.setPhoneE164(value);
            case "email" -> contact.setEmail(value);
            case "language" -> contact.setLanguage(value);
            case "country" -> contact.setCountry(value);
            default -> {
                // Store as custom field
                // Custom fields handling deferred
            }
        }
        contactRepository.save(contact);
        return Map.of("status", "ok", "message", "Updated contact." + field + ".");
    }

    // ─── Webhook ──────────────────────────────────────────────────────────────

    private Map<String, Object> executeWebhook(Map<String, Object> data, AutomationRun run, Map<String, Object> context) {
        String url = str(data.get("url"));
        if (url.isEmpty()) return Map.of("status", "error", "message", "Webhook URL missing.");

        Contact contact = run.getContactId() != null ? contactRepository.findById(run.getContactId()).orElse(null) : null;
        if (contact != null) url = renderTokens(url, contact, context);

        String method = str(data.getOrDefault("method", "POST")).toUpperCase();
        // Actual HTTP call is deferred; record the intent
        return Map.of("status", "ok", "message", "Webhook " + method + " " + url + " → 200 (simulated)",
                "output", Map.of("status", 200),
                "context_update", Map.of("webhook_status", 200));
    }

    // ─── Condition ────────────────────────────────────────────────────────────

    private Map<String, Object> executeCondition(Map<String, Object> data, AutomationRun run, Map<String, Object> context) {
        Contact contact = run.getContactId() != null ? contactRepository.findById(run.getContactId()).orElse(null) : null;
        boolean passed = evaluateCondition(data, contact, context);
        return conditionResult(passed, (String) data.get("field"),
                (String) data.getOrDefault("operator", "equals"), data.get("value"));
    }

    public boolean evaluateCondition(Map<String, Object> data, Contact contact, Map<String, Object> context) {
        String field = (String) data.get("field");
        String operator = (String) data.getOrDefault("operator", "equals");
        String value = str(data.get("value"));

        // Resolve actual value
        String actual;
        if ("contact.name".equals(field)) {
            actual = contact != null ? ((contact.getFirstName() != null ? contact.getFirstName() : "") + " " +
                    (contact.getLastName() != null ? contact.getLastName() : "")).trim() : null;
        } else if (field != null && field.startsWith("contact.")) {
            String attr = field.replace("contact.", "");
            actual = contact != null ? getContactField(contact, attr) : null;
        } else if ("message.body".equals(field)) {
            actual = str(context.get("message_body"));
        } else if (field != null && field.startsWith("context.")) {
            actual = str(context.get(field.replace("context.", "")));
        } else {
            actual = context != null ? str(context.get(field)) : null;
        }

        return switch (operator) {
            case "equals" -> str(actual).equals(value);
            case "not_equals" -> !str(actual).equals(value);
            case "contains" -> value != null && str(actual).contains(value);
            case "not_contains" -> value == null || !str(actual).contains(value);
            case "exists" -> actual != null && !actual.isEmpty();
            case "not_exists" -> actual == null || actual.isEmpty();
            case "gt" -> toDouble(actual) > toDouble(value);
            case "lt" -> toDouble(actual) < toDouble(value);
            default -> false;
        };
    }

    private Map<String, Object> conditionResult(boolean passed, String field, String operator, Object value) {
        return Map.of(
                "status", "ok",
                "branch", passed ? "true" : "false",
                "message", "Condition: " + field + " " + operator + " " + value + " → " + (passed ? "true" : "false")
        );
    }

    // ─── Assign Agent ─────────────────────────────────────────────────────────

    private Map<String, Object> executeAssignAgent(Map<String, Object> data, AutomationRun run) {
        if (run.getContactId() == null) return Map.of("status", "skipped", "message", "No contact to assign.");
        return Map.of("status", "ok", "message", "Agent assignment recorded.");
    }

    // ─── Run Subflow ──────────────────────────────────────────────────────────

    private Map<String, Object> executeRunSubflow(Map<String, Object> data, AutomationRun run, Map<String, Object> context) {
        String ref = str(data.getOrDefault("automation_uuid", data.get("automation_id")));
        if (ref.isEmpty()) return Map.of("status", "error", "message", "No sub-flow selected.");
        if (run.getContactId() == null) return Map.of("status", "skipped", "message", "Sub-flows require a contact.");

        Automation automation = automationRepository.findById(run.getAutomationId()).orElse(null);
        if (automation == null) return Map.of("status", "error", "message", "Parent automation not found.");

        Optional<Automation> targetOpt = automationRepository.findByWorkspaceIdAndUuid(automation.getWorkspaceId(), ref);
        if (targetOpt.isEmpty()) return Map.of("status", "error", "message", "Sub-flow not found.");

        Automation target = targetOpt.get();
        if (target.getId().equals(run.getAutomationId())) return Map.of("status", "skipped", "message", "A flow cannot call itself.");
        if (!target.isActive()) return Map.of("status", "skipped", "message", "Sub-flow is not active.");

        triggerForContact(target, run.getContactId(), context);
        return Map.of("status", "ok", "message", "Triggered sub-flow '" + target.getName() + "'.");
    }

    // ─── Send Message (stub) ──────────────────────────────────────────────────

    private Map<String, Object> executeSendMessage(String type, Map<String, Object> data, AutomationRun run, Map<String, Object> context) {
        if (run.getContactId() == null) return Map.of("status", "skipped", "message", "Contact not found.");
        Contact contact = contactRepository.findById(run.getContactId()).orElse(null);
        if (contact == null) return Map.of("status", "skipped", "message", "Contact not found.");

        String body = renderTokens(str(data.get("body")), contact, context);
        return Map.of("status", "ok", "message", "Message queued (" + type + ").",
                "output", Map.of("message_id", "sim-" + UUID.randomUUID().toString().substring(0, 8)));
    }

    // ─── Ask Question ─────────────────────────────────────────────────────────

    private Map<String, Object> executeAskQuestion(Map<String, Object> data, AutomationRun run, Map<String, Object> context) {
        if (run.getContactId() == null) return Map.of("status", "skipped", "message", "Contact not found.");
        String question = str(data.get("question"));
        if (question.isEmpty()) return Map.of("status", "error", "message", "Question text is required.");

        String var = str(data.getOrDefault("variable", "answer"));
        if (var.isEmpty()) var = "answer";

        Automation automation = automationRepository.findById(run.getAutomationId()).orElse(null);
        if (automation == null) return Map.of("status", "error", "message", "Automation not found.");

        List<Map<String, Object>> edges = automation.getEdges() != null ? automation.getEdges() : new ArrayList<>();
        Map<String, Object> nextEdge = findEdgeBySource(edges, run.getCurrentNodeId(), null);

        run.setStatus("waiting");
        run.setResumeNodeId(nextEdge != null ? (String) nextEdge.get("target") : null);
        runRepository.save(run);

        Map<String, Object> ctxUpdate = new HashMap<>();
        ctxUpdate.put("_awaiting_reply", true);
        ctxUpdate.put("_reply_var", var);

        return Map.of("status", "waiting",
                "message", "Asked question — waiting for reply → {{context." + var + "}}",
                "context_update", ctxUpdate);
    }

    // ─── Resume Awaiting Replies ──────────────────────────────────────────────

    public void resumeAwaitingReplies(Long workspaceId, Long contactId, String messageBody) {
        List<AutomationRun> waitingRuns = runRepository.findByContactIdAndStatus(contactId, "waiting");

        for (AutomationRun run : waitingRuns) {
            Automation auto = automationRepository.findById(run.getAutomationId()).orElse(null);
            if (auto == null || !auto.getWorkspaceId().equals(workspaceId)) continue;

            Map<String, Object> context = run.getContext() != null ? new HashMap<>(run.getContext()) : new HashMap<>();
            Object awaiting = context.get("_awaiting_reply");
            if (awaiting == null || Boolean.FALSE.equals(awaiting)) continue;

            String var = str(context.getOrDefault("_reply_var", "answer"));
            context.put(var, messageBody);
            context.remove("_awaiting_reply");
            context.remove("_reply_var");
            run.setContext(context);
            runRepository.save(run);

            executeRun(run);
        }
    }

    // ─── Preview Node (test mode) ─────────────────────────────────────────────

    private Map<String, Object> previewNode(String type, Map<String, Object> data, Contact contact, Map<String, Object> context) {
        return switch (type) {
            case "send_whatsapp" -> ok("Would send WhatsApp: \"" + snippet(renderTokens(str(data.get("body")), contact, context)) + "\"");
            case "send_sms" -> ok("Would send SMS: \"" + snippet(renderTokens(str(data.get("body")), contact, context)) + "\"");
            case "send_email" -> str(data.get("subject")).isEmpty()
                    ? err("Email subject is required.")
                    : ok("Would email \"" + snippet(renderTokens(str(data.get("subject")), contact, context)) + "\" to " + contact.getEmail());
            case "send_template" -> {
                String tpl = str(data.getOrDefault("template_name", data.getOrDefault("template_ref", "")));
                yield tpl.isEmpty() ? err("No template selected.") : ok("Would send template \"" + tpl + "\" (" + data.getOrDefault("language", "en") + ").");
            }
            case "send_media" -> str(data.get("link")).isEmpty()
                    ? err("Media link is required.")
                    : ok("Would send " + data.getOrDefault("media_type", "image") + ": " + snippet(renderTokens(str(data.get("link")), contact, context), 50));
            case "wait" -> ok("Would wait " + toInt(data.get("amount"), 1) + " " + data.getOrDefault("unit", "minutes") + " (skipped in test).");
            case "webhook" -> str(data.get("url")).isEmpty()
                    ? err("Webhook URL missing.")
                    : ok("Would call " + str(data.getOrDefault("method", "POST")).toUpperCase() + " " + snippet(renderTokens(str(data.get("url")), contact, context), 50),
                    Map.of("webhook_status", 200));
            case "add_tag" -> str(data.get("tag")).isEmpty() ? skip("No tag name.") : ok("Would add tag \"" + data.get("tag") + "\".");
            case "remove_tag" -> str(data.get("tag")).isEmpty() ? skip("No tag name.") : ok("Would remove tag \"" + data.get("tag") + "\".");
            case "update_contact" -> str(data.get("field")).isEmpty() ? skip("No field selected.") : ok("Would set contact." + data.get("field") + " = \"" + snippet(renderTokens(str(data.get("value")), contact, context)) + "\".");
            case "assign_agent" -> ok(str(data.get("agent_name")).isEmpty() ? "Would hand off to a human agent." : "Would assign to " + data.get("agent_name") + ".");
            case "add_to_campaign" -> str(data.get("campaign_id")).isEmpty() ? skip("No campaign selected.") : ok("Would add contact to campaign #" + data.get("campaign_id") + ".");
            case "ask_question" -> {
                String var = str(data.getOrDefault("variable", "answer"));
                if (var.isEmpty()) var = "answer";
                yield ok("Would ask: \"" + snippet(renderTokens(str(data.get("question")), contact, context)) + "\" → saved to {{context." + var + "}}",
                        Map.of(var.isEmpty() ? "answer" : var, "[sample reply]"));
            }
            case "run_subflow" -> ok("Would run sub-flow " + str(data.getOrDefault("subflow_name", data.getOrDefault("automation_uuid", "?"))) + ".");
            case "ai_reply" -> ok("Would generate an AI reply and send it.", Map.of("last_ai_reply", "[AI generated reply]"));
            case "run_chatbot" -> str(data.get("chatbot_id")).isEmpty() ? err("No chatbot selected.") : ok("Would run chatbot #" + data.get("chatbot_id") + " and send the reply.", Map.of("last_ai_reply", "[chatbot reply]"));
            case "cta_button" -> ok("Would send CTA \"" + data.getOrDefault("display_text", "Open") + "\" → " + snippet(renderTokens(str(data.get("url")), contact, context), 40));
            case "send_location" -> ok("Would send location " + data.getOrDefault("latitude", "?") + ", " + data.getOrDefault("longitude", "?") + ".");
            case "send_poll" -> ok("Would send a poll: \"" + snippet(renderTokens(str(data.get("question")), contact, context)) + "\"");
            case "quick_replies" -> ok("Would send buttons.");
            case "list_message" -> ok("Would send a list message.");
            case "send_sequence" -> ok("Would send sequence step(s).");
            default -> skip("Unknown node type: " + type);
        };
    }

    // ─── Token Rendering ──────────────────────────────────────────────────────

    public String renderTokens(String template, Contact contact, Map<String, Object> context) {
        if (template == null || template.isEmpty()) return "";

        // Contact tokens: {{contact.field}}
        Pattern contactPattern = Pattern.compile("\\{\\{contact\\.(\\w+)\\}\\}");
        Matcher contactMatcher = contactPattern.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (contactMatcher.find()) {
            String field = contactMatcher.group(1);
            String replacement;
            if ("name".equals(field)) {
                replacement = contact != null ? ((contact.getFirstName() != null ? contact.getFirstName() : "") + " " +
                        (contact.getLastName() != null ? contact.getLastName() : "")).trim() : "";
            } else {
                replacement = contact != null ? str(getContactField(contact, field)) : "";
            }
            contactMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        contactMatcher.appendTail(sb);
        template = sb.toString();

        // Context tokens: {{context.key}}
        Pattern contextPattern = Pattern.compile("\\{\\{context\\.(\\w+)\\}\\}");
        Matcher contextMatcher = contextPattern.matcher(template);
        sb = new StringBuilder();
        while (contextMatcher.find()) {
            String key = contextMatcher.group(1);
            String replacement = context != null ? str(context.get(key)) : "";
            contextMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        contextMatcher.appendTail(sb);

        return sb.toString();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String getContactField(Contact contact, String field) {
        return switch (field) {
            case "first_name" -> contact.getFirstName();
            case "last_name" -> contact.getLastName();
            case "email" -> contact.getEmail();
            case "phone", "phone_e164" -> contact.getPhoneE164();
            case "language" -> contact.getLanguage();
            case "country" -> contact.getCountry();
            default -> "";
        };
    }

    private Contact sampleContact(Long workspaceId) {
        Contact c = new Contact();
        c.setWorkspaceId(workspaceId);
        c.setFirstName("Test");
        c.setLastName("Contact");
        c.setEmail("test.contact@example.com");
        c.setPhoneE164("+15555550123");
        c.setLanguage("en");
        c.setCountry("US");
        return c;
    }

    private Map<String, Object> defaultTestContext() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("message_body", "Hi");
        ctx.put("message_channel", "whatsapp");
        ctx.put("order_number", "1042");
        ctx.put("order_total", "49.00");
        ctx.put("order_currency", "USD");
        ctx.put("tracking_url", "https://example.com/track/1042");
        ctx.put("store_name", "Demo Store");
        ctx.put("cart_total", "49.00");
        ctx.put("recovery_url", "https://example.com/cart/abc");
        return ctx;
    }

    private Map<String, Object> findNodeByType(List<Map<String, Object>> nodes, String type) {
        return nodes.stream().filter(n -> type.equals(n.get("type"))).findFirst().orElse(null);
    }

    private Map<String, Object> findNodeById(List<Map<String, Object>> nodes, String id) {
        return nodes.stream().filter(n -> id.equals(n.get("id"))).findFirst().orElse(null);
    }

    private Map<String, Object> findEdgeBySource(List<Map<String, Object>> edges, String source, String sourceHandle) {
        return edges.stream().filter(e -> {
            if (!source.equals(e.get("source"))) return false;
            if (sourceHandle == null) return true;
            return sourceHandle.equals(e.get("sourceHandle"));
        }).findFirst().orElse(null);
    }

    private String snippet(String s) { return snippet(s, 40); }
    private String snippet(String s, int n) {
        s = s != null ? s.trim() : "";
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }

    private Map<String, Object> ok(String msg) { return Map.of("status", "ok", "message", msg); }
    private Map<String, Object> ok(String msg, Map<String, Object> ctxUpdate) { return Map.of("status", "ok", "message", msg, "context_update", ctxUpdate); }
    private Map<String, Object> err(String msg) { return Map.of("status", "error", "message", msg); }
    private Map<String, Object> skip(String msg) { return Map.of("status", "skipped", "message", msg); }

    private String str(Object o) { return o != null ? String.valueOf(o) : ""; }
    private int toInt(Object o, int def) { try { return Integer.parseInt(str(o)); } catch (Exception e) { return def; } }
    private double toDouble(Object o) { try { return Double.parseDouble(str(o)); } catch (Exception e) { return 0; } }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        if (o instanceof Map) return (Map<String, Object>) o;
        return null;
    }
}
