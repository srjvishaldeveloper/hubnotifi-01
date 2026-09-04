package com.whatsmine.repository;

import com.whatsmine.model.WhatsappBusinessAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WhatsappBusinessAccountRepository extends JpaRepository<WhatsappBusinessAccount, Long> {

    Optional<WhatsappBusinessAccount> findByWabaId(String wabaId);

    Optional<WhatsappBusinessAccount> findByWebhookVerifyToken(String token);

    Optional<WhatsappBusinessAccount> findByWebhookVerifyTokenHash(String tokenHash);

    List<WhatsappBusinessAccount> findByWorkspaceId(Long workspaceId);

    Optional<WhatsappBusinessAccount> findByWorkspaceIdAndWabaId(Long workspaceId, String wabaId);

    Optional<WhatsappBusinessAccount> findFirstByWorkspaceIdAndStatus(Long workspaceId, String status);
}
