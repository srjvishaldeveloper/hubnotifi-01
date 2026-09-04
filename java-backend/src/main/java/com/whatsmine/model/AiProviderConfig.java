package com.whatsmine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatsmine.model.converter.JsonMapConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "ai_provider_configs")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    @JsonProperty("workspace_id")
    private Long workspaceId;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "credentials")
    private Map<String, Object> credentials;

    @Column(name = "default_model_chat", length = 64)
    @JsonProperty("default_model_chat")
    private String defaultModelChat;

    @Column(name = "default_model_embed", length = 64)
    @JsonProperty("default_model_embed")
    private String defaultModelEmbed;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Map<String, Object> getCredentials() {
        return credentials;
    }

    public void setCredentials(Map<String, Object> credentials) {
        this.credentials = credentials;
    }

    public String getDefaultModelChat() {
        return defaultModelChat;
    }

    public void setDefaultModelChat(String defaultModelChat) {
        this.defaultModelChat = defaultModelChat;
    }

    public String getDefaultModelEmbed() {
        return defaultModelEmbed;
    }

    public void setDefaultModelEmbed(String defaultModelEmbed) {
        this.defaultModelEmbed = defaultModelEmbed;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
