package com.whatsmine.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "failed_jobs", indexes = {
    @Index(name = "failed_jobs_uuid_unique", columnList = "uuid", unique = true)
})
public class FailedJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String uuid;

    @Column(length = 65535, nullable = false)
    private String connection;

    @Column(length = 65535, nullable = false)
    private String queue;

    @Column(length = 65535, nullable = false)
    private String payload;

    @Column(length = 65535, nullable = false)
    private String exception;

    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    @PrePersist
    protected void onCreate() {
        if (failedAt == null) {
            failedAt = LocalDateTime.now();
        }
    }

    public FailedJob() {}

    public FailedJob(Long id, String uuid, String connection, String queue, String payload, String exception, LocalDateTime failedAt) {
        this.id = id;
        this.uuid = uuid;
        this.connection = connection;
        this.queue = queue;
        this.payload = payload;
        this.exception = exception;
        this.failedAt = failedAt != null ? failedAt : LocalDateTime.now();
    }

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

    public String getConnection() {
        return connection;
    }

    public void setConnection(String connection) {
        this.connection = connection;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getException() {
        return exception;
    }

    public void setException(String exception) {
        this.exception = exception;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(LocalDateTime failedAt) {
        this.failedAt = failedAt;
    }
}
