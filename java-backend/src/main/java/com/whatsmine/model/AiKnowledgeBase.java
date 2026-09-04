package com.whatsmine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ai_knowledge_bases")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiKnowledgeBase {

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

    @Column(name = "embedding_model", length = 64)
    @JsonProperty("embedding_model")
    private String embeddingModel = "text-embedding-3-small";

    @Column(name = "dimensions")
    private Integer dimensions = 1536;

    @Column(name = "status", length = 32)
    private String status = "active";

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "kb_id")
    private List<AiKbDocument> documents = new ArrayList<>();

    @Transient
    @JsonProperty("documents_count")
    private Integer documentsCount;

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

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Integer getDimensions() {
        return dimensions;
    }

    public void setDimensions(Integer dimensions) {
        this.dimensions = dimensions;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<AiKbDocument> getDocuments() {
        return documents;
    }

    public void setDocuments(List<AiKbDocument> documents) {
        this.documents = documents;
    }

    public Integer getDocumentsCount() {
        if (documentsCount != null) return documentsCount;
        return documents != null ? documents.size() : 0;
    }

    public void setDocumentsCount(Integer documentsCount) {
        this.documentsCount = documentsCount;
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
