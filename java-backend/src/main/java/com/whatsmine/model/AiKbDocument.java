package com.whatsmine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_kb_documents")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiKbDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "kb_id", nullable = false)
    @JsonProperty("kb_id")
    private Long kbId;

    @Column(name = "source_type", nullable = false, length = 32)
    @JsonProperty("source_type")
    private String sourceType;

    @Column(name = "source_ref", length = 512)
    @JsonProperty("source_ref")
    private String sourceRef;

    @Column(name = "title", length = 256)
    private String title;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "pending";

    @Column(name = "tokens", nullable = false)
    private Integer tokens = 0;

    @Column(name = "last_indexed_at")
    @JsonProperty("last_indexed_at")
    private LocalDateTime lastIndexedAt;

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

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTokens() {
        return tokens;
    }

    public void setTokens(Integer tokens) {
        this.tokens = tokens;
    }

    public LocalDateTime getLastIndexedAt() {
        return lastIndexedAt;
    }

    public void setLastIndexedAt(LocalDateTime lastIndexedAt) {
        this.lastIndexedAt = lastIndexedAt;
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
