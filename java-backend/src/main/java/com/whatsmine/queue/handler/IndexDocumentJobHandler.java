package com.whatsmine.queue.handler;

import com.whatsmine.queue.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class IndexDocumentJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(IndexDocumentJobHandler.class);

    @Override
    public String getJobType() {
        return "IndexDocumentJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        if (data == null) return;
        Number docId = (Number) data.get("documentId");
        log.info("Executing IndexDocumentJob for documentId={}", docId != null ? docId.longValue() : null);
    }

    @Override
    public int getMaxTries() {
        return 3;
    }
}
