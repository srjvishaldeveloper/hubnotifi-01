package com.whatsmine.repository;

import com.whatsmine.model.EcommerceStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EcommerceStoreRepository extends JpaRepository<EcommerceStore, Long> {

    List<EcommerceStore> findByWorkspaceIdOrderByIdDesc(Long workspaceId);

    Optional<EcommerceStore> findByIdAndWorkspaceId(Long id, Long workspaceId);

    Optional<EcommerceStore> findByWorkspaceIdAndUuid(Long workspaceId, String uuid);

    Optional<EcommerceStore> findByUuid(String uuid);

    Optional<EcommerceStore> findByWorkspaceIdAndPlatformAndDomain(Long workspaceId, String platform, String domain);
}
