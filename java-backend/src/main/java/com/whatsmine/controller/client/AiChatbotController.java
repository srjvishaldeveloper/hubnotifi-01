package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.AiChatbot;
import com.whatsmine.model.AiKnowledgeBase;
import com.whatsmine.repository.AiChatbotRepository;
import com.whatsmine.repository.AiKnowledgeBaseRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.ai.ChatbotRunner;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/app/ai/chatbots")
public class AiChatbotController {

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private AiChatbotRepository chatbotRepository;

    @Autowired
    private AiKnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private ChatbotRunner chatbotRunner;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return userDetails.getWorkspaceId();
    }

    @GetMapping
    public Object index(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<AiChatbot> chatbots = chatbotRepository.findByWorkspaceIdOrderByIdDesc(workspaceId);
        List<AiKnowledgeBase> kbs = knowledgeBaseRepository.findByWorkspaceIdOrderByIdDesc(workspaceId);

        Map<Long, AiKnowledgeBase> kbMap = new HashMap<>();
        for (AiKnowledgeBase kb : kbs) {
            kbMap.put(kb.getId(), kb);
        }

        for (AiChatbot bot : chatbots) {
            if (bot.getAiKbId() != null) {
                bot.setKnowledgeBase(kbMap.get(bot.getAiKbId()));
            }
        }

        List<Map<String, Object>> knowledgeBasesProp = kbs.stream()
                .map(kb -> (Map<String, Object>) Map.of("id", (Object) kb.getId(), "name", kb.getName()))
                .toList();

        Map<String, Object> props = Map.of(
                "chatbots", chatbots,
                "knowledgeBases", knowledgeBasesProp
        );
        return inertiaRenderer.render("AI/Chatbots/Index", props, request);
    }

    @PostMapping
    public Object store(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody Map<String, Object> body) {
        Long workspaceId = getWorkspaceId(userDetails);
        String name = body != null ? (String) body.get("name") : null;
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Chatbot name is required.");
        }

        AiChatbot bot = new AiChatbot();
        bot.setWorkspaceId(workspaceId);
        bot.setName(name.trim());
        chatbotRepository.save(bot);

        // A raw ResponseEntity<Map> here fails content negotiation for
        // Inertia's actual POST requests: Inertia's client sends
        // "Accept: text/html, application/xhtml+xml" (never application/json),
        // and Jackson's converter can't satisfy that for a JSON body — Spring
        // rejects the whole response with 406 before the redirect ever
        // reaches the browser. Inertia.redirect() goes through our own
        // InertiaReturnValueHandler instead, which sets Location + 303
        // directly with no body to negotiate.
        return Inertia.redirect("/app/ai/chatbots");
    }

    @PutMapping("/{uuid}")
    @SuppressWarnings("unchecked")
    public Object update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestBody Map<String, Object> body,
            HttpSession session
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        AiChatbot bot = chatbotRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chatbot not found."));

        if (body != null) {
            if (body.containsKey("name") && body.get("name") != null) {
                bot.setName(String.valueOf(body.get("name")).trim());
            }
            if (body.containsKey("ai_kb_id")) {
                Object kbVal = body.get("ai_kb_id");
                if (kbVal == null) {
                    bot.setAiKbId(null);
                } else {
                    try {
                        Long kbId = Long.parseLong(String.valueOf(kbVal));
                        boolean exists = knowledgeBaseRepository.findByWorkspaceIdAndId(workspaceId, kbId).isPresent();
                        if (!exists) {
                            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid Knowledge Base ID.");
                        }
                        bot.setAiKbId(kbId);
                    } catch (NumberFormatException e) {
                        bot.setAiKbId(null);
                    }
                }
            }
            if (body.containsKey("system_prompt")) {
                bot.setSystemPrompt((String) body.get("system_prompt"));
            }
            if (body.containsKey("tone")) {
                bot.setTone((String) body.get("tone"));
            }
            if (body.containsKey("max_context_chunks")) {
                try {
                    bot.setMaxContextChunks(Integer.parseInt(String.valueOf(body.get("max_context_chunks"))));
                } catch (Exception ignored) {}
            }
            if (body.containsKey("fallback_reply")) {
                bot.setFallbackReply((String) body.get("fallback_reply"));
            }
            if (body.containsKey("channels") && body.get("channels") instanceof Map) {
                bot.setChannels((Map<String, Object>) body.get("channels"));
            }
            if (body.containsKey("enabled")) {
                bot.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
            }
        }

        chatbotRepository.save(bot);

        Inertia.flashSuccess(session, "Chatbot updated.");
        return Inertia.redirect("/app/ai/chatbots");
    }

    @DeleteMapping("/{uuid}")
    public Object destroy(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String uuid, HttpSession session) {
        Long workspaceId = getWorkspaceId(userDetails);
        AiChatbot bot = chatbotRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chatbot not found."));

        chatbotRepository.delete(bot);

        Inertia.flashSuccess(session, "Chatbot deleted.");
        return Inertia.redirect("/app/ai/chatbots");
    }

    @PostMapping("/{uuid}/playground")
    @SuppressWarnings("unchecked")
    public Object playground(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestBody Map<String, Object> body
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        AiChatbot bot = chatbotRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chatbot not found."));

        String message = body != null ? (String) body.get("message") : null;
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Message is required.");
        }

        List<Map<String, String>> history = body != null && body.get("history") instanceof List
                ? (List<Map<String, String>>) body.get("history") : List.of();

        Map<String, Object> result = chatbotRunner.runForApi(bot, message, workspaceId, history);

        return ResponseEntity.ok(Map.of("reply", result.getOrDefault("reply", "No response.")));
    }
}
