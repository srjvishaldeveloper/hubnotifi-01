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
@Table(name = "channel_accounts")
public class ChannelAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel = "whatsapp";

    @Column(name = "provider", length = 32)
    private String provider;

    @Column(name = "credentials", length = 65535)
    private String credentials;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "phone_number_id")
    private String phoneNumberId;

    @Column(name = "business_account_id")
    private String businessAccountId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "active";

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
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getCredentials() { return credentials; }
    public void setCredentials(String credentials) { this.credentials = credentials; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPhoneNumberId() { return phoneNumberId; }
    public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }
    public String getBusinessAccountId() { return businessAccountId; }
    public void setBusinessAccountId(String businessAccountId) { this.businessAccountId = businessAccountId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMetaJson() { return metaJson; }
    public void setMetaJson(String metaJson) { this.metaJson = metaJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
