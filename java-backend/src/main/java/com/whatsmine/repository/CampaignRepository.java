package com.whatsmine.repository;

import com.whatsmine.model.Campaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    Optional<Campaign> findByWorkspaceIdAndUuid(Long workspaceId, String uuid);

    Optional<Campaign> findByWorkspaceIdAndUuidAndStatus(Long workspaceId, String uuid, String status);

    @Query("SELECT c FROM Campaign c WHERE c.workspaceId = :workspaceId " +
           "AND (:channel IS NULL OR c.channel = :channel) " +
           "AND (:status IS NULL OR c.status = :status) " +
           "ORDER BY c.id DESC")
    Page<Campaign> findFilteredCampaigns(
            @Param("workspaceId") Long workspaceId,
            @Param("channel") String channel,
            @Param("status") String status,
            Pageable pageable
    );
}
