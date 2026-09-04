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
@Table(name = "whatsapp_templates")
public class WhatsappTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "waba_id", nullable = false, length = 64)
    private String wabaId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "language", nullable = false, length = 8)
    private String language = "en";

    @Column(name = "category", nullable = false, length = 32)
    private String category = "MARKETING";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "components", length = 65535)
    private String components;

    @Column(name = "rejection_reason", length = 65535)
    private String rejectionReason;

    @Column(name = "meta_template_id", length = 64)
    private String metaTemplateId;

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
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getComponents() { return components; }
    public void setComponents(String components) { this.components = components; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getMetaTemplateId() { return metaTemplateId; }
    public void setMetaTemplateId(String metaTemplateId) { this.metaTemplateId = metaTemplateId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
