package com.whatsmine.repository;

import com.whatsmine.model.SocialPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SocialPostRepository extends JpaRepository<SocialPost, Long> {
    Page<SocialPost> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId, Pageable pageable);

    Page<SocialPost> findByWorkspaceIdAndStatusOrderByCreatedAtDesc(Long workspaceId, String status, Pageable pageable);

    Optional<SocialPost> findByIdAndWorkspaceId(Long id, Long workspaceId);

    List<SocialPost> findByWorkspaceIdAndScheduledAtBetween(Long workspaceId, LocalDateTime from, LocalDateTime to);

    List<SocialPost> findByWorkspaceIdAndCreatedAtBetween(Long workspaceId, LocalDateTime from, LocalDateTime to);

    List<SocialPost> findByStatusAndScheduledAtLessThanEqual(String status, LocalDateTime cutoff);
}
