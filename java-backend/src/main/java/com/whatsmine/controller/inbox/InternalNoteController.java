package com.whatsmine.controller.inbox;

import com.whatsmine.model.Conversation;
import com.whatsmine.model.InternalNote;
import com.whatsmine.repository.ConversationRepository;
import com.whatsmine.repository.InternalNoteRepository;
import com.whatsmine.repository.UserRepository;

import com.whatsmine.security.CustomUserDetails;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/app/inbox/conversations/{uuid}/notes")
public class InternalNoteController {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private InternalNoteRepository internalNoteRepository;

    @Autowired
    private UserRepository userRepository;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        return userDetails.getWorkspaceId();
    }

    @GetMapping
    public ResponseEntity<List<InternalNote>> index(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Conversation conversation = conversationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        List<InternalNote> notes = internalNoteRepository.findByConversationIdOrderByCreatedAtDesc(conversation.getId());
        return ResponseEntity.ok(notes);
    }

    @PostMapping
    public ResponseEntity<InternalNote> store(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestBody Map<String, String> bodyPayload
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        Conversation conversation = conversationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        String body = bodyPayload.get("body");
        if (body == null || body.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Body is required");
        }

        List<Long> mentionedUserIds = new ArrayList<>();
        Pattern pattern = Pattern.compile("@(\\w+)");
        Matcher matcher = pattern.matcher(body);
        while (matcher.find()) {
            String username = matcher.group(1);
            userRepository.findByWorkspaceId(workspaceId).stream()
                    .filter(u -> u.getName().equalsIgnoreCase(username))
                    .findFirst()
                    .ifPresent(u -> mentionedUserIds.add(u.getId()));
        }

        InternalNote note = new InternalNote();
        note.setConversationId(conversation.getId());
        note.setUserId(userDetails.getId());
        note.setBody(body);
        note.setMentionedUserIds(mentionedUserIds.toString());
        note.setUser(userDetails.getUser());
        note = internalNoteRepository.save(note);

        return ResponseEntity.status(HttpStatus.CREATED).body(note);
    }
}
