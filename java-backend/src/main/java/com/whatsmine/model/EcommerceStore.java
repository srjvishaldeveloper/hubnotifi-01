package com.whatsmine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatsmine.model.converter.JsonMapConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ecommerce_stores")
@JsonIgnoreProperties(ignoreUnknown = true)
public class EcommerceStore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "workspace_id", nullable = false)
    @JsonProperty("workspace_id")
    private Long workspaceId;

    @Column(name = "platform", nullable = false, length = 20)
    private String platform;

    @Column(name = "name")
    private String name;

    @Column(name = "domain", nullable = false)
    private String domain;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "credentials")
    private Map<String, Object> credentials;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "external_meta")
    @JsonProperty("external_meta")
    private Map<String, Object> externalMeta;

    @Column(name = "webhook_secret", length = 64)
    @JsonProperty("webhook_secret")
    private String webhookSecret;

    @Column(name = "last_tested_at")
    @JsonProperty("last_tested_at")
    private LocalDateTime lastTestedAt;

    @Column(name = "last_test_status", length = 20)
    @JsonProperty("last_test_status")
    private String lastTestStatus = "untested";

    @Column(name = "last_test_message", length = 512)
    @JsonProperty("last_test_message")
    private String lastTestMessage;

    @Column(name = "customers_synced_at")
    @JsonProperty("customers_synced_at")
    private LocalDateTime customersSyncedAt;

    @Column(name = "orders_synced_at")
    @JsonProperty("orders_synced_at")
    private LocalDateTime ordersSyncedAt;

    @Column(name = "products_synced_at")
    @JsonProperty("products_synced_at")
    private LocalDateTime productsSyncedAt;

    @Column(name = "created_at", updatable = false)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (uuid == null || uuid.isBlank()) {
            uuid = UUID.randomUUID().toString();
        }
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

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Map<String, Object> getCredentials() {
        return credentials;
    }

    public void setCredentials(Map<String, Object> credentials) {
        this.credentials = credentials;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getExternalMeta() {
        return externalMeta;
    }

    public void setExternalMeta(Map<String, Object> externalMeta) {
        this.externalMeta = externalMeta;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public LocalDateTime getLastTestedAt() {
        return lastTestedAt;
    }

    public void setLastTestedAt(LocalDateTime lastTestedAt) {
        this.lastTestedAt = lastTestedAt;
    }

    public String getLastTestStatus() {
        return lastTestStatus;
    }

    public void setLastTestStatus(String lastTestStatus) {
        this.lastTestStatus = lastTestStatus;
    }

    public String getLastTestMessage() {
        return lastTestMessage;
    }

    public void setLastTestMessage(String lastTestMessage) {
        this.lastTestMessage = lastTestMessage;
    }

    public LocalDateTime getCustomersSyncedAt() {
        return customersSyncedAt;
    }

    public void setCustomersSyncedAt(LocalDateTime customersSyncedAt) {
        this.customersSyncedAt = customersSyncedAt;
    }

    public LocalDateTime getOrdersSyncedAt() {
        return ordersSyncedAt;
    }

    public void setOrdersSyncedAt(LocalDateTime ordersSyncedAt) {
        this.ordersSyncedAt = ordersSyncedAt;
    }

    public LocalDateTime getProductsSyncedAt() {
        return productsSyncedAt;
    }

    public void setProductsSyncedAt(LocalDateTime productsSyncedAt) {
        this.productsSyncedAt = productsSyncedAt;
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
