package com.whatsmine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatsmine.model.converter.JsonMapConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "automation_run_logs")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AutomationRunLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    @JsonProperty("run_id")
    private Long runId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", insertable = false, updatable = false)
    private AutomationRun run;

    @Column(name = "node_id", length = 64)
    @JsonProperty("node_id")
    private String nodeId;

    @Column(name = "node_type", length = 64)
    @JsonProperty("node_type")
    private String nodeType;

    @Column(name = "result", length = 32)
    private String result;

    @Lob
    @Column(name = "message")
    private String message;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "output")
    private Map<String, Object> output;

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

    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }

    public AutomationRun getRun() { return run; }
    public void setRun(AutomationRun run) { this.run = run; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Map<String, Object> getOutput() { return output; }
    public void setOutput(Map<String, Object> output) { this.output = output; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
