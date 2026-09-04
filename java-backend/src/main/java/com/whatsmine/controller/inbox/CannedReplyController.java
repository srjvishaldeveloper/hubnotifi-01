package com.whatsmine.controller.inbox;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.CannedReply;

import com.whatsmine.repository.CannedReplyRepository;

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
@RequestMapping("/app/inbox/canned-replies")
public class CannedReplyController {

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private CannedReplyRepository cannedReplyRepository;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        return userDetails.getWorkspaceId();
    }

    @GetMapping
    public Object index(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<CannedReply> replies = cannedReplyRepository.findByWorkspaceIdOrderByShortcutAsc(workspaceId);
        return inertiaRenderer.render("Inbox/CannedReplies/Index", Map.of("cannedReplies", replies), request);
    }

    @GetMapping("/list")
    public ResponseEntity<List<CannedReply>> list(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<CannedReply> replies = cannedReplyRepository.findByWorkspaceIdOrderByShortcutAsc(workspaceId);
        return ResponseEntity.ok(replies);
    }

    @PostMapping
    public Object store(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody Map<String, String> body) {
        Long workspaceId = getWorkspaceId(userDetails);
        String shortcut = body.get("shortcut");
        String text = body.get("body");

        if (shortcut == null || shortcut.isBlank() || text == null || text.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Shortcut and body are required");
        }

        CannedReply reply = new CannedReply();
        reply.setWorkspaceId(workspaceId);
        reply.setShortcut(shortcut);
        reply.setBody(text);
        cannedReplyRepository.save(reply);

        return Inertia.redirect("/app/inbox/canned-replies");
    }

    @PutMapping("/{id}")
    public Object update(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        CannedReply reply = cannedReplyRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        reply.setShortcut(body.get("shortcut"));
        reply.setBody(body.get("body"));
        cannedReplyRepository.save(reply);

        return Inertia.redirect("/app/inbox/canned-replies");
    }

    @DeleteMapping("/{id}")
    public Object destroy(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
        Long workspaceId = getWorkspaceId(userDetails);
        CannedReply reply = cannedReplyRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        cannedReplyRepository.delete(reply);

        return Inertia.redirect("/app/inbox/canned-replies");
    }
}
