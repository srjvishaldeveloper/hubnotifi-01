package com.whatsmine.repository;

import com.whatsmine.model.EcommerceProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EcommerceProductRepository extends JpaRepository<EcommerceProduct, Long> {

    List<EcommerceProduct> findByWorkspaceIdOrderByIdDesc(Long workspaceId);

    Page<EcommerceProduct> findByWorkspaceIdOrderByIdDesc(Long workspaceId, Pageable pageable);

    Optional<EcommerceProduct> findByIdAndWorkspaceId(Long id, Long workspaceId);

    Optional<EcommerceProduct> findByStoreIdAndExternalId(Long storeId, String externalId);

    @Query("SELECT p FROM EcommerceProduct p WHERE p.workspaceId = :workspaceId AND (:query IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<EcommerceProduct> searchProducts(@Param("workspaceId") Long workspaceId, @Param("query") String query, Pageable pageable);

    @Query("SELECT p FROM EcommerceProduct p WHERE p.workspaceId = :workspaceId AND (:query IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<EcommerceProduct> searchProductsList(@Param("workspaceId") Long workspaceId, @Param("query") String query);
}
