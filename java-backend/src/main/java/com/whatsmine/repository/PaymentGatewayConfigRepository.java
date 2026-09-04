package com.whatsmine.repository;

import com.whatsmine.model.PaymentGatewayConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentGatewayConfigRepository extends JpaRepository<PaymentGatewayConfig, Long> {
    Optional<PaymentGatewayConfig> findByGateway(String gateway);
    List<PaymentGatewayConfig> findByEnabledTrue();
    List<PaymentGatewayConfig> findByGatewayIn(List<String> gateways);
}
