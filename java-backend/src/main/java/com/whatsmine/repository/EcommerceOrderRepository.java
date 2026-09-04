package com.whatsmine.repository;

import com.whatsmine.model.EcommerceOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EcommerceOrderRepository extends JpaRepository<EcommerceOrder, Long> {

    List<EcommerceOrder> findByWorkspaceIdOrderByIdDesc(Long workspaceId);

    Page<EcommerceOrder> findByWorkspaceIdOrderByIdDesc(Long workspaceId, Pageable pageable);

    Optional<EcommerceOrder> findByIdAndWorkspaceId(Long id, Long workspaceId);

    Optional<EcommerceOrder> findByStoreIdAndExternalOrderId(Long storeId, String externalOrderId);

    List<EcommerceOrder> findByContactIdAndWorkspaceIdOrderByIdDesc(Long contactId, Long workspaceId);

    @Query("SELECT o FROM EcommerceOrder o WHERE o.workspaceId = :workspaceId " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:query IS NULL OR LOWER(o.number) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<EcommerceOrder> searchOrders(@Param("workspaceId") Long workspaceId, 
                                       @Param("status") String status, 
                                       @Param("query") String query, 
                                       Pageable pageable);
}
