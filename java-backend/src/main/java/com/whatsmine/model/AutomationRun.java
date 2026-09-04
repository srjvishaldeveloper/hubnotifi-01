package com.whatsmine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatsmine.model.converter.JsonMapConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "automation_runs")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AutomationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "automation_id", nullable = false)
    @JsonProperty("automation_id")
    private Long automationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "automation_id", insertable = false, updatable = false)
    private Automation automation;

    @Column(name = "contact_id")
    @JsonProperty("contact_id")
    private Long contactId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "running";

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "context")
    private Map<String, Object> context;

    @Column(name = "current_node_id", length = 64)
    @JsonProperty("current_node_id")
    private String currentNodeId;

    @Column(name = "resume_node_id", length = 64)
    @JsonProperty("resume_node_id")
    private String resumeNodeId;

    @Lob
    @Column(name = "error")
    private String error;

    @Column(name = "started_at")
    @JsonProperty("started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    @JsonProperty("completed_at")
    private LocalDateTime completedAt;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAutomationId() { return automationId; }
    public void setAutomationId(Long automationId) { this.automationId = automationId; }

    public Automation getAutomation() { return automation; }
    public void setAutomation(Automation automation) { this.automation = automation; }

    public Long getContactId() { return contactId; }
    public void setContactId(Long contactId) { this.contactId = contactId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }

    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = currentNodeId; }

    public String getResumeNodeId() { return resumeNodeId; }
    public void setResumeNodeId(String resumeNodeId) { this.resumeNodeId = resumeNodeId; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
