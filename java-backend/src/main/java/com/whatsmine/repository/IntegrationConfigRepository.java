package com.whatsmine.repository;

import com.whatsmine.model.IntegrationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IntegrationConfigRepository extends JpaRepository<IntegrationConfig, Long> {
    Optional<IntegrationConfig> findByProvider(String provider);
    Optional<IntegrationConfig> findByWorkspaceIdAndProvider(Long workspaceId, String provider);
    Optional<IntegrationConfig> findByProviderAndMode(String provider, String mode);
    List<IntegrationConfig> findByEnabledTrue();
}
