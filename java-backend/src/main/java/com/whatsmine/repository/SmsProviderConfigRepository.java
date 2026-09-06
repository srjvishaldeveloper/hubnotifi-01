package com.whatsmine.repository;

import com.whatsmine.model.SmsProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SmsProviderConfigRepository extends JpaRepository<SmsProviderConfig, Long> {

    List<SmsProviderConfig> findByWorkspaceId(Long workspaceId);

    Optional<SmsProviderConfig> findByWorkspaceIdAndProvider(Long workspaceId, String provider);

    Optional<SmsProviderConfig> findByWorkspaceIdAndIsDefaultTrue(Long workspaceId);
}
