package com.whatsmine.repository;

import com.whatsmine.model.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);
    Page<PaymentTransaction> findByUserId(Long userId, Pageable pageable);
    Page<PaymentTransaction> findByStatus(String status, Pageable pageable);
    Page<PaymentTransaction> findByGateway(String gateway, Pageable pageable);
    Page<PaymentTransaction> findByStatusAndGateway(String status, String gateway, Pageable pageable);
}
