package com.whatsmine.repository;

import com.whatsmine.model.Media;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MediaRepository extends JpaRepository<Media, Long> {
    Page<Media> findByMediableTypeAndMediableIdOrderByCreatedAtDesc(String mediableType, Long mediableId, Pageable pageable);
    List<Media> findByMediableTypeAndMediableId(String mediableType, Long mediableId);
    
    @Query("SELECT COALESCE(SUM(m.sizeBytes), 0) FROM Media m WHERE m.mediableType = :mediableType AND m.mediableId = :mediableId")
    Long sumSizeBytesByMediable(String mediableType, Long mediableId);
}
