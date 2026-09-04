package com.whatsmine.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.AiKbChunk;
import com.whatsmine.repository.AiKbChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class EmbeddingStore {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingStore.class);

    @Autowired
    private AiKbChunkRepository chunkRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record SearchResult(AiKbChunk chunk, double score) {}

    public void storeEmbedding(AiKbChunk chunk, List<Double> embedding) {
        try {
            chunk.setEmbedding(objectMapper.writeValueAsString(embedding));
            chunkRepository.save(chunk);
        } catch (Exception e) {
            log.error("Failed to store chunk embedding: {}", e.getMessage());
        }
    }

    public List<SearchResult> search(Long kbId, List<Double> queryEmbedding, int topK) {
        if (queryEmbedding == null || queryEmbedding.isEmpty()) return List.of();

        List<AiKbChunk> chunks = chunkRepository.findByKbIdAndEmbeddingIsNotNull(kbId);
        if (chunks.isEmpty()) {
            chunks = chunkRepository.findByKbId(kbId).stream().filter(c -> c.getEmbedding() != null).toList();
        }

        List<SearchResult> results = new ArrayList<>();
        for (AiKbChunk chunk : chunks) {
            List<Double> chunkVec = unpackEmbedding(chunk.getEmbedding());
            double score = cosine(queryEmbedding, chunkVec);
            results.add(new SearchResult(chunk, score));
        }

        results.sort((a, b) -> Double.compare(b.score(), a.score()));
        return results.stream().limit(topK).toList();
    }

    private List<Double> unpackEmbedding(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Double>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private double cosine(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty() || a.size() != b.size()) {
            return 0.0;
        }
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < a.size(); i++) {
            double va = a.get(i);
            double vb = b.get(i);
            dot += va * vb;
            na += va * va;
            nb += vb * vb;
        }
        double denom = Math.sqrt(na) * Math.sqrt(nb);
        return denom > 0 ? dot / denom : 0.0;
    }
}
