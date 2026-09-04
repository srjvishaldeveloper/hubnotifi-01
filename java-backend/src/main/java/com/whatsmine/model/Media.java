package com.whatsmine.model;

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

@Entity
@Table(name = "media")
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mediable_type", nullable = false)
    private String mediableType;

    @Column(name = "mediable_id", nullable = false)
    private Long mediableId;

    @Column(name = "disk", length = 64, nullable = false)
    private String disk;

    @Column(name = "path", length = 1000, nullable = false)
    private String path;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "mime_type", length = 128, nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "collection", length = 64)
    private String collection = "default";

    @Convert(converter = JsonAttributeConverter.class)
    @Column(name = "meta", length = 65535)
    private Map<String, Object> meta;

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

    public Media() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMediableType() { return mediableType; }
    public void setMediableType(String mediableType) { this.mediableType = mediableType; }

    public Long getMediableId() { return mediableId; }
    public void setMediableId(Long mediableId) { this.mediableId = mediableId; }

    public String getDisk() { return disk; }
    public void setDisk(String disk) { this.disk = disk; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getCollection() { return collection; }
    public void setCollection(String collection) { this.collection = collection; }

    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
