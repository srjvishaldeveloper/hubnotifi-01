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
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "event", length = 128, nullable = false)
    private String event;

    @Column(name = "auditable_type")
    private String auditableType;

    @Column(name = "auditable_id")
    private Long auditableId;

    @Convert(converter = JsonAttributeConverter.class)
    @Column(name = "old_values", length = 65535)
    private Map<String, Object> oldValues;

    @Convert(converter = JsonAttributeConverter.class)
    @Column(name = "new_values", length = 65535)
    private Map<String, Object> newValues;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public AuditLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }

    public String getAuditableType() { return auditableType; }
    public void setAuditableType(String auditableType) { this.auditableType = auditableType; }

    public Long getAuditableId() { return auditableId; }
    public void setAuditableId(Long auditableId) { this.auditableId = auditableId; }

    public Map<String, Object> getOldValues() { return oldValues; }
    public void setOldValues(Map<String, Object> oldValues) { this.oldValues = oldValues; }

    public Map<String, Object> getNewValues() { return newValues; }
    public void setNewValues(Map<String, Object> newValues) { this.newValues = newValues; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
