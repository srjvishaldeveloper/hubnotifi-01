package com.whatsmine.controller.client;

import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.AiKbDocument;
import com.whatsmine.model.AiKnowledgeBase;
import com.whatsmine.repository.AiKbChunkRepository;
import com.whatsmine.repository.AiKbDocumentRepository;
import com.whatsmine.repository.AiKnowledgeBaseRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.ai.DocumentIndexer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/app/ai")
public class AiKnowledgeBaseController {

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private AiKnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private AiKbDocumentRepository documentRepository;

    @Autowired
    private AiKbChunkRepository chunkRepository;

    @Autowired
    private DocumentIndexer documentIndexer;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return userDetails.getWorkspaceId();
    }

    @GetMapping("/knowledge-bases")
    public Object index(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<AiKnowledgeBase> kbs = knowledgeBaseRepository.findByWorkspaceIdOrderByIdDesc(workspaceId);
        for (AiKnowledgeBase kb : kbs) {
            List<AiKbDocument> docs = documentRepository.findByKbId(kb.getId());
            kb.setDocumentsCount(docs.size());
        }

        Map<String, Object> props = Map.of("knowledgeBases", kbs);
        return inertiaRenderer.render("AI/KnowledgeBases/Index", props, request);
    }

    @PostMapping("/knowledge-bases")
    public Object store(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody Map<String, Object> body) {
        Long workspaceId = getWorkspaceId(userDetails);
        String name = body != null ? (String) body.get("name") : null;
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Knowledge base name is required.");
        }

        AiKnowledgeBase kb = new AiKnowledgeBase();
        kb.setWorkspaceId(workspaceId);
        kb.setName(name.trim());
        kb = knowledgeBaseRepository.save(kb);

        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header("Location", "/app/ai/knowledge-bases")
                .body(Map.of("message", "Knowledge base created.", "uuid", kb.getUuid()));
    }

    @GetMapping("/knowledge-bases/{uuid}")
    public Object show(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String uuid) {
        Long workspaceId = getWorkspaceId(userDetails);
        AiKnowledgeBase kb = knowledgeBaseRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge base not found."));

        List<AiKbDocument> docs = documentRepository.findByKbId(kb.getId());
        kb.setDocuments(docs);

        Map<String, Object> props = Map.of("kb", kb);
        return inertiaRenderer.render("AI/KnowledgeBases/Show", props, request);
    }

    @PostMapping("/knowledge-bases/{uuid}/documents")
    public Object addDocument(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestBody Map<String, Object> body
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        AiKnowledgeBase kb = knowledgeBaseRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge base not found."));

        String sourceType = body != null ? (String) body.get("source_type") : "text";
        String sourceRef = body != null ? (String) body.get("source_ref") : "";
        String title = body != null && body.get("title") != null ? (String) body.get("title") : "Document";

        AiKbDocument doc = new AiKbDocument();
        doc.setKbId(kb.getId());
        doc.setSourceType(sourceType);
        doc.setSourceRef(sourceRef);
        doc.setTitle(title);
        doc.setStatus("pending");
        doc = documentRepository.save(doc);

        // Index document synchronously for Phase 11
        documentIndexer.indexDocument(doc.getId(), workspaceId);

        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header("Location", "/app/ai/knowledge-bases/" + uuid)
                .body(Map.of("message", "Document queued for indexing."));
    }

    @PostMapping("/documents/{uuid}/reindex")
    public Object reindex(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String uuid) {
        Long workspaceId = getWorkspaceId(userDetails);
        AiKbDocument doc = documentRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found."));

        AiKnowledgeBase kb = knowledgeBaseRepository.findById(doc.getKbId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge base not found."));

        if (!kb.getWorkspaceId().equals(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
        }

        doc.setStatus("pending");
        documentRepository.save(doc);

        documentIndexer.indexDocument(doc.getId(), workspaceId);

        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header("Location", "/app/ai/knowledge-bases/" + kb.getUuid())
                .body(Map.of("message", "Re-indexing queued."));
    }

    @DeleteMapping("/documents/{uuid}")
    public Object destroyDocument(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String uuid) {
        Long workspaceId = getWorkspaceId(userDetails);
        AiKbDocument doc = documentRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found."));

        AiKnowledgeBase kb = knowledgeBaseRepository.findById(doc.getKbId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge base not found."));

        if (!kb.getWorkspaceId().equals(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
        }

        chunkRepository.deleteByDocumentId(doc.getId());
        documentRepository.delete(doc);

        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header("Location", "/app/ai/knowledge-bases/" + kb.getUuid())
                .body(Map.of("message", "Document removed."));
    }
}
