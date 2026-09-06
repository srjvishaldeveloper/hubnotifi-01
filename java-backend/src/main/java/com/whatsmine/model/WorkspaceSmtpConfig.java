package com.whatsmine.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatsmine.model.converter.EncryptedJsonMapConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Per-workspace SMTP override, porting PHP's WorkspaceSmtpConfig /
 * workspace_smtp_configs table. Falls back to the admin's platform-wide
 * SMTP settings (Admin > Email System / SystemSetting group "smtp") when a
 * workspace has none configured or has deactivated its own. The password is
 * stored inside an encrypted map (this codebase has no single-string
 * encrypted column converter) alongside plaintext connection fields.
 */
@Entity
@Table(name = "workspace_smtp_configs")
public class WorkspaceSmtpConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, unique = true)
    @JsonProperty("workspace_id")
    private Long workspaceId;

    @Column(name = "host", nullable = false)
    private String host;

    @Column(name = "port", nullable = false)
    private Integer port = 587;

    @Column(name = "username")
    private String username;

    @Convert(converter = EncryptedJsonMapConverter.class)
    @Column(name = "secrets", length = 65535)
    private Map<String, Object> secrets;

    /** tls | ssl | none */
    @Column(name = "encryption", length = 8)
    private String encryption = "tls";

    @Column(name = "from_email")
    @JsonProperty("from_email")
    private String fromEmail;

    @Column(name = "from_name")
    @JsonProperty("from_name")
    private String fromName;

    @Column(name = "is_active", nullable = false)
    @JsonProperty("is_active")
    private Boolean isActive = true;

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

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Map<String, Object> getSecrets() { return secrets; }
    public void setSecrets(Map<String, Object> secrets) { this.secrets = secrets; }

    public String getEncryption() { return encryption; }
    public void setEncryption(String encryption) { this.encryption = encryption; }

    public String getFromEmail() { return fromEmail; }
    public void setFromEmail(String fromEmail) { this.fromEmail = fromEmail; }

    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = fromName; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
