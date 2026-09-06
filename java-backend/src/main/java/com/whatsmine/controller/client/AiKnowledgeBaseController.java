package com.whatsmine.controller.client;

import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.AiKbDocument;
import com.whatsmine.model.AiKnowledgeBase;
import com.whatsmine.queue.QueueDispatcher;
import com.whatsmine.repository.AiKbChunkRepository;
import com.whatsmine.repository.AiKbDocumentRepository;
import com.whatsmine.repository.AiKnowledgeBaseRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private QueueDispatcher queueDispatcher;

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

    /**
     * The real Show.jsx page always submits this as multipart/form-data
     * (it needs to support an optional file upload alongside url/text/faq/
     * sitemap source refs), so this must bind form fields + an optional
     * MultipartFile — a @RequestBody Map here would never actually
     * deserialize the real request the frontend sends.
     */
    @PostMapping("/knowledge-bases/{uuid}/documents")
    public Object addDocument(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestParam(value = "source_type", defaultValue = "text") String sourceType,
            @RequestParam(value = "source_ref", required = false) String sourceRef,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) throws java.io.IOException {
        Long workspaceId = getWorkspaceId(userDetails);
        AiKnowledgeBase kb = knowledgeBaseRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge base not found."));

        String resolvedSourceRef = sourceRef != null ? sourceRef : "";
        String resolvedTitle = (title != null && !title.isBlank()) ? title : "Document";

        if ("file".equals(sourceType)) {
            if (file == null || file.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A file is required for the file source type.");
            }
            resolvedSourceRef = storeUploadedFile(file);
            if (title == null || title.isBlank()) {
                resolvedTitle = file.getOriginalFilename() != null ? file.getOriginalFilename() : "Document";
            }
        }

        AiKbDocument doc = new AiKbDocument();
        doc.setKbId(kb.getId());
        doc.setSourceType(sourceType);
        doc.setSourceRef(resolvedSourceRef);
        doc.setTitle(resolvedTitle);
        doc.setStatus("pending");
        doc = documentRepository.save(doc);

        Map<String, Object> jobData = new LinkedHashMap<>();
        jobData.put("documentId", doc.getId());
        jobData.put("workspaceId", workspaceId);
        queueDispatcher.dispatch("ai", "IndexDocumentJob", jobData);

        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header("Location", "/app/ai/knowledge-bases/" + uuid)
                .body(Map.of("message", "Document queued for indexing."));
    }

    /** Stores an uploaded KB document (e.g. PDF) under storage/app/public/kb-docs/, the same local disk MediaService writes to. */
    private String storeUploadedFile(MultipartFile file) throws java.io.IOException {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String ext = "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot > 0) ext = originalFilename.substring(dot + 1);

        String relativePath = "kb-docs/" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        // MultipartFile#transferTo(File) resolves a RELATIVE destination against
        // the servlet container's own temp work directory, not this process's
        // working directory — must pass an absolute path or the write silently
        // lands (or fails) somewhere under Tomcat's temp dir instead of storage/.
        Path targetPath = Paths.get("storage/app/public", relativePath).toAbsolutePath();
        Files.createDirectories(targetPath.getParent());
        file.transferTo(targetPath.toFile());
        return relativePath;
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

        Map<String, Object> jobData = new LinkedHashMap<>();
        jobData.put("documentId", doc.getId());
        jobData.put("workspaceId", workspaceId);
        queueDispatcher.dispatch("ai", "IndexDocumentJob", jobData);

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
