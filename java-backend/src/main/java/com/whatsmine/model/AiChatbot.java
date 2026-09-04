package com.whatsmine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatsmine.model.converter.JsonMapConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ai_chatbots")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiChatbot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "workspace_id", nullable = false)
    @JsonProperty("workspace_id")
    private Long workspaceId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "ai_kb_id")
    @JsonProperty("ai_kb_id")
    private Long aiKbId;

    @Lob
    @Column(name = "system_prompt")
    @JsonProperty("system_prompt")
    private String systemPrompt = "You are a helpful assistant.";

    @Column(name = "tone", length = 64)
    private String tone = "professional";

    @Column(name = "max_context_chunks")
    @JsonProperty("max_context_chunks")
    private Integer maxContextChunks = 5;

    @Column(name = "fallback_reply", length = 512)
    @JsonProperty("fallback_reply")
    private String fallbackReply;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "channels")
    private Map<String, Object> channels;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_kb_id", insertable = false, updatable = false)
    @JsonProperty("knowledge_base")
    private AiKnowledgeBase knowledgeBase;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getAiKbId() {
        return aiKbId;
    }

    public void setAiKbId(Long aiKbId) {
        this.aiKbId = aiKbId;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getTone() {
        return tone;
    }

    public void setTone(String tone) {
        this.tone = tone;
    }

    public Integer getMaxContextChunks() {
        return maxContextChunks;
    }

    public void setMaxContextChunks(Integer maxContextChunks) {
        this.maxContextChunks = maxContextChunks;
    }

    public String getFallbackReply() {
        return fallbackReply;
    }

    public void setFallbackReply(String fallbackReply) {
        this.fallbackReply = fallbackReply;
    }

    public Map<String, Object> getChannels() {
        return channels;
    }

    public void setChannels(Map<String, Object> channels) {
        this.channels = channels;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public AiKnowledgeBase getKnowledgeBase() {
        return knowledgeBase;
    }

    public void setKnowledgeBase(AiKnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
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
