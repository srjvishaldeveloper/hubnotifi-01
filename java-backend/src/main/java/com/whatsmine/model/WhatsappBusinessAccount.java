package com.whatsmine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_business_accounts")
public class WhatsappBusinessAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "waba_id", nullable = false, length = 64, unique = true)
    private String wabaId;

    @Column(name = "credentials", length = 65535)
    private String credentials;

    @Column(name = "webhook_verify_token", length = 512)
    private String webhookVerifyToken;

    @Column(name = "webhook_verify_token_hash", length = 64)
    private String webhookVerifyTokenHash;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "inactive";

    @Column(name = "meta_json", length = 65535)
    private String metaJson;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public String getWabaId() { return wabaId; }
    public void setWabaId(String wabaId) { this.wabaId = wabaId; }
    public String getCredentials() { return credentials; }
    public void setCredentials(String credentials) { this.credentials = credentials; }
    public String getWebhookVerifyToken() { return webhookVerifyToken; }
    public void setWebhookVerifyToken(String webhookVerifyToken) { this.webhookVerifyToken = webhookVerifyToken; }
    public String getWebhookVerifyTokenHash() { return webhookVerifyTokenHash; }
    public void setWebhookVerifyTokenHash(String webhookVerifyTokenHash) { this.webhookVerifyTokenHash = webhookVerifyTokenHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMetaJson() { return metaJson; }
    public void setMetaJson(String metaJson) { this.metaJson = metaJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
