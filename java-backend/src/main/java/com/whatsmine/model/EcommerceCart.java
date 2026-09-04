package com.whatsmine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatsmine.model.converter.JsonListMapConverter;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "ecommerce_carts")
@JsonIgnoreProperties(ignoreUnknown = true)
public class EcommerceCart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    @JsonProperty("workspace_id")
    private Long workspaceId;

    @Column(name = "store_id", nullable = false)
    @JsonProperty("store_id")
    private Long storeId;

    @Column(name = "contact_id")
    @JsonProperty("contact_id")
    private Long contactId;

    @Column(name = "external_id", nullable = false)
    @JsonProperty("external_id")
    private String externalId;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "currency", length = 8)
    private String currency;

    @Convert(converter = JsonListMapConverter.class)
    @Column(name = "line_items")
    @JsonProperty("line_items")
    private List<Map<String, Object>> lineItems;

    @Column(name = "recovery_url", length = 1024)
    @JsonProperty("recovery_url")
    private String recoveryUrl;

    @Column(name = "abandoned_at")
    @JsonProperty("abandoned_at")
    private LocalDateTime abandonedAt;

    @Column(name = "recovered_at")
    @JsonProperty("recovered_at")
    private LocalDateTime recoveredAt;

    @Column(name = "recovery_triggered_at")
    @JsonProperty("recovery_triggered_at")
    private LocalDateTime recoveryTriggeredAt;

    @Column(name = "created_at", updatable = false)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public Long getContactId() {
        return contactId;
    }

    public void setContactId(Long contactId) {
        this.contactId = contactId;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public List<Map<String, Object>> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<Map<String, Object>> lineItems) {
        this.lineItems = lineItems;
    }

    public String getRecoveryUrl() {
        return recoveryUrl;
    }

    public void setRecoveryUrl(String recoveryUrl) {
        this.recoveryUrl = recoveryUrl;
    }

    public LocalDateTime getAbandonedAt() {
        return abandonedAt;
    }

    public void setAbandonedAt(LocalDateTime abandonedAt) {
        this.abandonedAt = abandonedAt;
    }

    public LocalDateTime getRecoveredAt() {
        return recoveredAt;
    }

    public void setRecoveredAt(LocalDateTime recoveredAt) {
        this.recoveredAt = recoveredAt;
    }

    public LocalDateTime getRecoveryTriggeredAt() {
        return recoveryTriggeredAt;
    }

    public void setRecoveryTriggeredAt(LocalDateTime recoveryTriggeredAt) {
        this.recoveryTriggeredAt = recoveryTriggeredAt;
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
