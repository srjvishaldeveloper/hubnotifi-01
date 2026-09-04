package com.whatsmine.repository;

import com.whatsmine.model.Lead;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {

    List<Lead> findByWorkspaceIdOrderByIdDesc(Long workspaceId);

    Page<Lead> findByWorkspaceIdOrderByIdDesc(Long workspaceId, Pageable pageable);

    Optional<Lead> findByIdAndWorkspaceId(Long id, Long workspaceId);

    Optional<Lead> findByGooglePlaceId(String googlePlaceId);

    @Query("SELECT l FROM Lead l WHERE l.workspaceId = :workspaceId " +
           "AND (:query IS NULL OR LOWER(l.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(l.category) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(l.city) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Lead> searchLeads(@Param("workspaceId") Long workspaceId, @Param("query") String query, Pageable pageable);

    List<Lead> findByWorkspaceIdAndIdIn(Long workspaceId, List<Long> ids);
}
