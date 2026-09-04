package com.whatsmine.repository;

import com.whatsmine.model.LeadScrapeJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeadScrapeJobRepository extends JpaRepository<LeadScrapeJob, Long> {

    List<LeadScrapeJob> findByWorkspaceIdOrderByIdDesc(Long workspaceId);

    Page<LeadScrapeJob> findByWorkspaceIdOrderByIdDesc(Long workspaceId, Pageable pageable);

    Optional<LeadScrapeJob> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
