package com.whatsmine.repository;

import com.whatsmine.model.AiProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiProviderConfigRepository extends JpaRepository<AiProviderConfig, Long> {

    List<AiProviderConfig> findByWorkspaceId(Long workspaceId);

    Optional<AiProviderConfig> findByWorkspaceIdAndProvider(Long workspaceId, String provider);

    List<AiProviderConfig> findByWorkspaceIdAndEnabledTrue(Long workspaceId);
}
