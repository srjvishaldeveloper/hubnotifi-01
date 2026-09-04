package com.whatsmine.repository;

import com.whatsmine.model.SocialPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SocialPostRepository extends JpaRepository<SocialPost, Long> {
    Page<SocialPost> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId, Pageable pageable);
}
