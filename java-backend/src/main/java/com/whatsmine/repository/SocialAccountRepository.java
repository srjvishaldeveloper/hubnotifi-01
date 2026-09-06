package com.whatsmine.repository;

import com.whatsmine.model.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    List<SocialAccount> findByWorkspaceId(Long workspaceId);

    List<SocialAccount> findByWorkspaceIdAndActiveTrue(Long workspaceId);

    Optional<SocialAccount> findByIdAndWorkspaceId(Long id, Long workspaceId);

    Optional<SocialAccount> findByWorkspaceIdAndProviderAndProviderUserId(Long workspaceId, String provider, String providerUserId);

    List<SocialAccount> findByRefreshTokenIsNotNullAndExpiresAtBefore(java.time.LocalDateTime before);
}
