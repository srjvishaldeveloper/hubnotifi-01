package com.whatsmine.controller.whatsapp;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.WhatsappAutoReply;
import com.whatsmine.repository.WhatsappAutoReplyRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CRUD for rule-based (non-AI) WhatsApp auto-replies, porting PHP's
 * WhatsappAutoReplyController. Renders the existing Whatsapp/AutoReplies/Index
 * page (single-page list + inline form).
 */
@RestController
@RequestMapping("/app/whatsapp/auto-replies")
public class WhatsAppAutoReplyController {

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private WhatsappAutoReplyRepository autoReplyRepository;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return userDetails.getWorkspaceId();
    }

    @GetMapping
    public Object index(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<WhatsappAutoReply> rules = autoReplyRepository.findByWorkspaceIdOrderByPriorityAsc(workspaceId);
        return inertiaRenderer.render("Whatsapp/AutoReplies/Index", Map.of("rules", rules), request);
    }

    @PostMapping
    public Object store(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> body,
            HttpSession session
    ) {
        Long workspaceId = getWorkspaceId(userDetails);

        WhatsappAutoReply rule = new WhatsappAutoReply();
        rule.setWorkspaceId(workspaceId);
        applyBody(rule, body);

        autoReplyRepository.save(rule);
        Inertia.flashSuccess(session, "Auto-reply rule created.");
        return Inertia.redirect("/app/whatsapp/auto-replies");
    }

    @PutMapping("/{id}")
    public Object update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpSession session
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        WhatsappAutoReply rule = autoReplyRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auto-reply rule not found."));

        applyBody(rule, body);
        autoReplyRepository.save(rule);
        Inertia.flashSuccess(session, "Auto-reply rule updated.");
        return Inertia.redirect("/app/whatsapp/auto-replies");
    }

    @DeleteMapping("/{id}")
    public Object destroy(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            HttpSession session
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        WhatsappAutoReply rule = autoReplyRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auto-reply rule not found."));

        autoReplyRepository.delete(rule);
        Inertia.flashSuccess(session, "Auto-reply rule deleted.");
        return Inertia.redirect("/app/whatsapp/auto-replies");
    }

    @SuppressWarnings("unchecked")
    private void applyBody(WhatsappAutoReply rule, Map<String, Object> body) {
        if (body.containsKey("trigger_type")) rule.setTriggerType(str(body.get("trigger_type"), "keyword"));
        if (body.containsKey("match_mode")) rule.setMatchMode(str(body.get("match_mode"), "contains"));
        if (body.containsKey("channel_account_id")) {
            Object v = body.get("channel_account_id");
            rule.setChannelAccountId(v != null ? Long.valueOf(String.valueOf(v)) : null);
        }
        if (body.get("keywords") instanceof List) {
            rule.setKeywords((List<String>) body.get("keywords"));
        }
        if (body.get("schedule_json") instanceof Map) {
            rule.setScheduleJson(new LinkedHashMap<>((Map<String, Object>) body.get("schedule_json")));
        }
        if (body.containsKey("response_kind")) rule.setResponseKind(str(body.get("response_kind"), "text"));
        if (body.get("payload_json") instanceof Map) {
            rule.setPayloadJson(new LinkedHashMap<>((Map<String, Object>) body.get("payload_json")));
        }
        if (body.containsKey("enabled")) rule.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        if (body.containsKey("priority")) {
            try {
                rule.setPriority(Integer.parseInt(String.valueOf(body.get("priority"))));
            } catch (Exception ignored) { }
        }
    }

    private String str(Object o, String fallback) {
        return o != null ? String.valueOf(o) : fallback;
    }
}
