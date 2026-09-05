package com.whatsmine.model;

import com.whatsmine.model.converter.EncryptedJsonMapConverter;
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

/**
 * Integration credentials (Meta app, LLM providers, storage, etc).
 * `workspaceId` is null for a system-wide config (set by a master admin —
 * e.g. the one Meta app the whole platform sends WhatsApp through) and set
 * for a per-workspace override. `credentials` is AES-GCM encrypted at rest
 * (see CredentialsCipher) since it holds real secrets; `settings` is plain
 * JSON for non-secret config.
 */
@Entity
@Table(name = "integration_configs")
public class IntegrationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(name = "provider", length = 64, nullable = false)
    private String provider;

    @Column(name = "label")
    private String label;

    @Column(name = "mode", length = 32)
    private String mode = "live";

    @Column(name = "enabled")
    private Boolean enabled = false;

    @Convert(converter = EncryptedJsonMapConverter.class)
    @Column(name = "credentials", length = 65535)
    private Map<String, Object> credentials;

    @Convert(converter = JsonAttributeConverter.class)
    @Column(name = "settings", length = 65535)
    private Map<String, Object> settings;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    @Column(name = "last_tested_at")
    private LocalDateTime lastTestedAt;

    @Column(name = "last_test_status", length = 32)
    private String lastTestStatus;

    @Column(name = "last_test_message", length = 65535)
    private String lastTestMessage;

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

    public IntegrationConfig() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Map<String, Object> getCredentials() { return credentials; }
    public void setCredentials(Map<String, Object> credentials) { this.credentials = credentials; }

    public Map<String, Object> getSettings() { return settings; }
    public void setSettings(Map<String, Object> settings) { this.settings = settings; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public LocalDateTime getLastTestedAt() { return lastTestedAt; }
    public void setLastTestedAt(LocalDateTime lastTestedAt) { this.lastTestedAt = lastTestedAt; }

    public String getLastTestStatus() { return lastTestStatus; }
    public void setLastTestStatus(String lastTestStatus) { this.lastTestStatus = lastTestStatus; }

    public String getLastTestMessage() { return lastTestMessage; }
    public void setLastTestMessage(String lastTestMessage) { this.lastTestMessage = lastTestMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
