package com.whatsmine.repository;

import com.whatsmine.model.AiKbChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiKbChunkRepository extends JpaRepository<AiKbChunk, Long> {

    List<AiKbChunk> findByKbId(Long kbId);

    List<AiKbChunk> findByKbIdAndEmbeddingIsNotNull(Long kbId);

    List<AiKbChunk> findByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);

    void deleteByKbId(Long kbId);
}
