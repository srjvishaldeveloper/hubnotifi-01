package com.whatsmine.model;

import jakarta.persistence.*;

@Entity
@Table(name = "jobs", indexes = {
    @Index(name = "jobs_queue_index", columnList = "queue")
})
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String queue;

    @Column(length = 65535, nullable = false)
    private String payload;

    @Column(nullable = false)
    private Integer attempts;

    @Column(name = "reserved_at")
    private Long reservedAt;

    @Column(name = "available_at", nullable = false)
    private Long availableAt;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    public Job() {}

    public Job(Long id, String queue, String payload, Integer attempts, Long reservedAt, Long availableAt, Long createdAt) {
        this.id = id;
        this.queue = queue;
        this.payload = payload;
        this.attempts = attempts;
        this.reservedAt = reservedAt;
        this.availableAt = availableAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public Long getReservedAt() {
        return reservedAt;
    }

    public void setReservedAt(Long reservedAt) {
        this.reservedAt = reservedAt;
    }

    public Long getAvailableAt() {
        return availableAt;
    }

    public void setAvailableAt(Long availableAt) {
        this.availableAt = availableAt;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
