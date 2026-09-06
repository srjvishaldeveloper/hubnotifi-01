package com.whatsmine.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatsmine.model.converter.EncryptedJsonMapConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Per-workspace SMS gateway override, porting PHP's SmsProviderConfig /
 * sms_provider_configs table. One row per (workspace, provider); the row
 * with isDefault=true is the one actually used to send. Falls back to the
 * system-wide "sms_twilio" IntegrationConfig (Admin > Integrations) when a
 * workspace has no default row of its own.
 */
@Entity
@Table(name = "sms_provider_configs")
public class SmsProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    @JsonProperty("workspace_id")
    private Long workspaceId;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Convert(converter = EncryptedJsonMapConverter.class)
    @Column(name = "credentials", length = 65535)
    private Map<String, Object> credentials;

    @Column(name = "sender_id")
    @JsonProperty("sender_id")
    private String senderId;

    @Column(name = "is_default", nullable = false)
    @JsonProperty("is_default")
    private Boolean isDefault = false;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public Map<String, Object> getCredentials() { return credentials; }
    public void setCredentials(Map<String, Object> credentials) { this.credentials = credentials; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
