package com.whatsmine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatsmine.model.converter.JsonMapConverter;
import com.whatsmine.model.converter.JsonListMapConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "automations")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Automation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "workspace_id", nullable = false)
    @JsonProperty("workspace_id")
    private Long workspaceId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "draft";

    @Column(name = "trigger_type", length = 64)
    @JsonProperty("trigger_type")
    private String triggerType;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "trigger_config")
    @JsonProperty("trigger_config")
    private Map<String, Object> triggerConfig;

    @Column(name = "trigger_token", length = 128)
    @JsonProperty("trigger_token")
    private String triggerToken;

    @Convert(converter = JsonListMapConverter.class)
    @Column(name = "nodes")
    private List<Map<String, Object>> nodes;

    @Convert(converter = JsonListMapConverter.class)
    @Column(name = "edges")
    private List<Map<String, Object>> edges;

    @Column(name = "run_count", nullable = false)
    @JsonProperty("run_count")
    private Integer runCount = 0;

    @Column(name = "created_at", updatable = false)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    // Transient field for runs_count (withCount equivalent)
    @Transient
    @JsonProperty("runs_count")
    private Long runsCount;

    @PrePersist
    protected void onCreate() {
        if (uuid == null || uuid.isEmpty()) {
            uuid = UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return "active".equals(status);
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }

    public Map<String, Object> getTriggerConfig() { return triggerConfig; }
    public void setTriggerConfig(Map<String, Object> triggerConfig) { this.triggerConfig = triggerConfig; }

    public String getTriggerToken() { return triggerToken; }
    public void setTriggerToken(String triggerToken) { this.triggerToken = triggerToken; }

    public List<Map<String, Object>> getNodes() { return nodes; }
    public void setNodes(List<Map<String, Object>> nodes) { this.nodes = nodes; }

    public List<Map<String, Object>> getEdges() { return edges; }
    public void setEdges(List<Map<String, Object>> edges) { this.edges = edges; }

    public Integer getRunCount() { return runCount; }
    public void setRunCount(Integer runCount) { this.runCount = runCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getRunsCount() { return runsCount; }
    public void setRunsCount(Long runsCount) { this.runsCount = runsCount; }
}
