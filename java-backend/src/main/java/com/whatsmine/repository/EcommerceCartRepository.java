package com.whatsmine.repository;

import com.whatsmine.model.EcommerceCart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EcommerceCartRepository extends JpaRepository<EcommerceCart, Long> {

    List<EcommerceCart> findByWorkspaceIdOrderByIdDesc(Long workspaceId);

    Optional<EcommerceCart> findByIdAndWorkspaceId(Long id, Long workspaceId);

    Optional<EcommerceCart> findByStoreIdAndExternalId(Long storeId, String externalId);

    List<EcommerceCart> findByContactIdAndWorkspaceId(Long contactId, Long workspaceId);
}
