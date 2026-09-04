package com.whatsmine.repository;

import com.whatsmine.model.Segment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SegmentRepository extends JpaRepository<Segment, Long> {

    List<Segment> findByWorkspaceIdOrderByNameAsc(Long workspaceId);

    Optional<Segment> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
