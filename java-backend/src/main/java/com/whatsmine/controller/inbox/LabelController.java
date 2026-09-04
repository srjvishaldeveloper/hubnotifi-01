package com.whatsmine.controller.inbox;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaRenderer;

import com.whatsmine.model.Conversation;
import com.whatsmine.model.InboxLabel;

import com.whatsmine.repository.ConversationRepository;
import com.whatsmine.repository.InboxLabelRepository;

import com.whatsmine.security.CustomUserDetails;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/app/inbox")
public class LabelController {

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private InboxLabelRepository inboxLabelRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        return userDetails.getWorkspaceId();
    }

    @GetMapping("/labels")
    public Object index(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<InboxLabel> labels = inboxLabelRepository.findByWorkspaceIdOrderByNameAsc(workspaceId);
        return inertiaRenderer.render("Inbox/Labels/Index", Map.of("labels", labels), request);
    }

    @PostMapping("/labels")
    public Object store(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody Map<String, String> body) {
        Long workspaceId = getWorkspaceId(userDetails);
        String name = body.get("name");
        String color = body.get("color");

        if (name == null || name.isBlank() || color == null || color.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Name and color are required");
        }

        InboxLabel label = new InboxLabel();
        label.setWorkspaceId(workspaceId);
        label.setName(name);
        label.setColor(color);
        inboxLabelRepository.save(label);

        return Inertia.redirect("/app/inbox/labels");
    }

    @PutMapping("/labels/{id}")
    public Object update(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        InboxLabel label = inboxLabelRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        label.setName(body.get("name"));
        label.setColor(body.get("color"));
        inboxLabelRepository.save(label);

        return Inertia.redirect("/app/inbox/labels");
    }

    @DeleteMapping("/labels/{id}")
    public Object destroy(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
        Long workspaceId = getWorkspaceId(userDetails);
        InboxLabel label = inboxLabelRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        inboxLabelRepository.delete(label);

        return Inertia.redirect("/app/inbox/labels");
    }

    @PostMapping("/conversations/{uuid}/labels")
    public ResponseEntity<Map<String, Object>> attach(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestBody Map<String, Long> body
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Conversation conversation = conversationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        Long labelId = body.get("label_id");
        InboxLabel label = inboxLabelRepository.findByIdAndWorkspaceId(labelId, workspaceId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!conversation.getLabels().contains(label)) {
            conversation.getLabels().add(label);
            conversationRepository.save(conversation);
        }

        return ResponseEntity.ok(Map.of("ok", true, "label", label));
    }

    @DeleteMapping("/conversations/{uuid}/labels/{labelId}")
    public ResponseEntity<Map<String, Boolean>> detach(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @PathVariable Long labelId
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Conversation conversation = conversationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        InboxLabel label = inboxLabelRepository.findByIdAndWorkspaceId(labelId, workspaceId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        conversation.getLabels().remove(label);
        conversationRepository.save(conversation);

        return ResponseEntity.ok(Map.of("ok", true));
    }
}
