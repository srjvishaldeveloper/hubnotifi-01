package com.whatsmine.model;

import com.whatsmine.model.converter.JsonAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "integration_audit_logs")
public class IntegrationAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(name = "provider", length = 64, nullable = false)
    private String provider;

    @Column(name = "action", length = 128, nullable = false)
    private String action;

    @Convert(converter = JsonAttributeConverter.class)
    @Column(name = "details", length = 65535)
    private Map<String, Object> details;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public IntegrationAuditLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
