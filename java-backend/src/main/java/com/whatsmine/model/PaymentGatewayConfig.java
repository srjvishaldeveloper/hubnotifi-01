package com.whatsmine.model;

import com.whatsmine.model.converter.JsonAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "payment_gateway_configs")
public class PaymentGatewayConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gateway", length = 50, nullable = false, unique = true)
    private String gateway;

    @Column(name = "test_mode", nullable = false)
    private Boolean testMode = true;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    @Convert(converter = JsonAttributeConverter.class)
    @Column(name = "credentials", length = 65535)
    private Map<String, Object> credentials;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public PaymentGatewayConfig() {}

    @SuppressWarnings("unchecked")
    public Map<String, Object> getActiveCredentials() {
        if (credentials == null) {
            return Map.of();
        }
        String mode = Boolean.TRUE.equals(testMode) ? "test" : "live";
        Object val = credentials.get(mode);
        if (val instanceof Map) {
            return (Map<String, Object>) val;
        }
        return Map.of();
    }

    public boolean hasActiveCredentials() {
        Map<String, Object> active = getActiveCredentials();
        Object secret = active.get("secret_key");
        return secret != null && !secret.toString().isBlank();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public Boolean getTestMode() {
        return testMode;
    }

    public void setTestMode(Boolean testMode) {
        this.testMode = testMode;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Object> getCredentials() {
        return credentials;
    }

    public void setCredentials(Map<String, Object> credentials) {
        this.credentials = credentials;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
