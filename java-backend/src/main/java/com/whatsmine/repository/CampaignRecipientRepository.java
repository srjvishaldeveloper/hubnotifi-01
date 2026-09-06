package com.whatsmine.repository;

import com.whatsmine.model.CampaignRecipient;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, Long> {

    Optional<CampaignRecipient> findByProviderMessageId(String providerMessageId);

    List<CampaignRecipient> findByCampaignId(Long campaignId);

    @Query("SELECT cr FROM CampaignRecipient cr LEFT JOIN FETCH cr.contact WHERE cr.campaignId = :campaignId ORDER BY cr.updatedAt DESC")
    List<CampaignRecipient> findByCampaignIdOrderByUpdatedAtDesc(@Param("campaignId") Long campaignId, Pageable pageable);

    long countByCampaignId(Long campaignId);

    @Query("SELECT cr.status, COUNT(cr) FROM CampaignRecipient cr WHERE cr.campaignId = :campaignId GROUP BY cr.status")
    List<Object[]> countGroupByStatus(@Param("campaignId") Long campaignId);
}
