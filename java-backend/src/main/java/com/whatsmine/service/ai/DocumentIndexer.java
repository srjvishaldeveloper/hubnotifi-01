package com.whatsmine.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.AiKbChunk;
import com.whatsmine.model.AiKbDocument;
import com.whatsmine.repository.AiKbChunkRepository;
import com.whatsmine.repository.AiKbDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class DocumentIndexer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexer.class);

    @Autowired
    private AiKbDocumentRepository documentRepository;

    @Autowired
    private AiKbChunkRepository chunkRepository;

    @Autowired
    private LlmGateway llmGateway;

    @Autowired
    private EmbeddingStore embeddingStore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void indexDocument(Long documentId, Long workspaceId) {
        AiKbDocument doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) return;

        doc.setStatus("indexing");
        documentRepository.save(doc);

        try {
            String text = extractText(doc);
            List<String> chunksText = chunkText(text, 800, 100);

            // Delete old chunks
            chunkRepository.deleteByDocumentId(doc.getId());

            List<AiKbChunk> chunkModels = new ArrayList<>();
            for (int i = 0; i < chunksText.size(); i++) {
                String cText = chunksText.get(i);
                AiKbChunk chunk = new AiKbChunk();
                chunk.setKbId(doc.getKbId());
                chunk.setDocumentId(doc.getId());
                chunk.setOrd(i);
                chunk.setContent(cText);
                chunk.setTokens((int) Math.ceil(cText.length() / 4.0));
                chunkModels.add(chunkRepository.save(chunk));
            }

            // Embed chunks if workspace has embedding provider
            if (workspaceId != null && !chunkModels.isEmpty()) {
                try {
                    List<String> texts = chunkModels.stream().map(AiKbChunk::getContent).toList();
                    List<List<Double>> embeddings = llmGateway.embed(workspaceId, texts);
                    for (int i = 0; i < chunkModels.size(); i++) {
                        if (i < embeddings.size()) {
                            embeddingStore.storeEmbedding(chunkModels.get(i), embeddings.get(i));
                        }
                    }
                } catch (Exception e) {
                    log.warn("Embedding generation skipped for document {}: {}", doc.getId(), e.getMessage());
                }
            }

            int totalTokens = chunkModels.stream().mapToInt(AiKbChunk::getTokens).sum();
            doc.setStatus("indexed");
            doc.setTokens(totalTokens);
            doc.setLastIndexedAt(LocalDateTime.now());
            documentRepository.save(doc);

        } catch (Exception e) {
            log.error("Failed to index document {}: {}", doc.getId(), e.getMessage());
            doc.setStatus("error");
            documentRepository.save(doc);
        }
    }

    private String extractText(AiKbDocument doc) {
        if ("text".equalsIgnoreCase(doc.getSourceType())) {
            return doc.getSourceRef() != null ? doc.getSourceRef() : "";
        }
        if ("faq".equalsIgnoreCase(doc.getSourceType())) {
            return formatFaq(doc.getSourceRef());
        }
        return doc.getSourceRef() != null ? doc.getSourceRef() : "";
    }

    private String formatFaq(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            List<Map<String, String>> faqPairs = objectMapper.readValue(raw, new TypeReference<List<Map<String, String>>>() {});
            StringBuilder sb = new StringBuilder();
            for (Map<String, String> pair : faqPairs) {
                String q = pair.getOrDefault("question", "").trim();
                String a = pair.getOrDefault("answer", "").trim();
                if (!q.isEmpty() || !a.isEmpty()) {
                    if (!sb.isEmpty()) sb.append("\n\n");
                    sb.append("Q: ").append(q).append("\nA: ").append(a);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return raw;
        }
    }

    public List<String> chunkText(String text, int size, int overlap) {
        if (text == null || text.isBlank()) return List.of();
        String[] words = text.trim().split("\\s+");
        List<String> chunks = new ArrayList<>();
        int i = 0;
        while (i < words.length) {
            int end = Math.min(i + size, words.length);
            String[] slice = Arrays.copyOfRange(words, i, end);
            chunks.add(String.join(" ", slice));
            i += (size - overlap);
        }
        return chunks;
    }
}
