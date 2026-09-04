package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import com.whatsmine.service.ai.DocumentIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handles IndexDocumentJob — mirrors Laravel IndexDocumentJob.
 * Invokes DocumentIndexer which resolves the AI embedding provider at runtime
 * via LlmManager (workspace-configured, no hardcoded model).
 * Tries: 3 | Timeout: 120s | Queue: ai
 */
@Component
public class IndexDocumentJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(IndexDocumentJobHandler.class);

    private final DocumentIndexer documentIndexer;

    public IndexDocumentJobHandler(DocumentIndexer documentIndexer) {
        this.documentIndexer = documentIndexer;
    }

    @Override
    public String getJobType() {
        return "IndexDocumentJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null) return;
        Number docId = (Number) data.get("documentId");
        Number workspaceId = (Number) data.get("workspaceId");
        if (docId == null) {
            log.warn("IndexDocumentJob: missing documentId in payload, skipping");
            return;
        }
        long documentId = docId.longValue();
        Long wsId = workspaceId != null ? workspaceId.longValue() : null;
        log.info("IndexDocumentJob started: documentId={}, workspaceId={}", documentId, wsId);
        documentIndexer.indexDocument(documentId, wsId);
        log.info("IndexDocumentJob completed: documentId={}", documentId);
    }

    @Override
    public int getMaxTries() {
        return 3;
    }

    @Override
    public int[] getBackoff() {
        return new int[]{60, 120, 300};
    }
}
