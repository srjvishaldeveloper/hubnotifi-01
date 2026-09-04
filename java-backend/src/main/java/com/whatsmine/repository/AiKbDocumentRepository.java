package com.whatsmine.repository;

import com.whatsmine.model.AiKbDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiKbDocumentRepository extends JpaRepository<AiKbDocument, Long> {

    List<AiKbDocument> findByKbId(Long kbId);

    Optional<AiKbDocument> findByUuid(String uuid);

    void deleteByKbId(Long kbId);
}
