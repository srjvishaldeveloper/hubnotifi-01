package com.whatsmine.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatsmine.model.converter.JsonListConverter;
import com.whatsmine.model.converter.JsonMapConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Rule-based (non-AI) WhatsApp auto-reply, porting PHP's WhatsappAutoReply /
 * whatsapp_auto_replies table. Distinct from the AI-chatbot auto-reply
 * branch already ported this session in WhatsappInboundProcessor.
 */
@Entity
@Table(name = "whatsapp_auto_replies")
public class WhatsappAutoReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    @JsonProperty("workspace_id")
    private Long workspaceId;

    /** Null = applies to every WhatsApp channel account in the workspace. */
    @Column(name = "channel_account_id")
    @JsonProperty("channel_account_id")
    private Long channelAccountId;

    /** keyword | welcome | away | out_of_hours */
    @Column(name = "trigger_type", nullable = false, length = 32)
    @JsonProperty("trigger_type")
    private String triggerType = "keyword";

    /** exact | contains | regex — only meaningful for trigger_type=keyword */
    @Column(name = "match_mode", nullable = false, length = 16)
    @JsonProperty("match_mode")
    private String matchMode = "contains";

    @Convert(converter = JsonListConverter.class)
    @Column(name = "keywords", length = 4096)
    private List<String> keywords;

    /** {days:[1-7], start:"HH:MM", end:"HH:MM", timezone:"TZ"} — used by away/out_of_hours */
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "schedule_json", length = 4096)
    @JsonProperty("schedule_json")
    private Map<String, Object> scheduleJson;

    /** text | template | media | flow */
    @Column(name = "response_kind", nullable = false, length = 16)
    @JsonProperty("response_kind")
    private String responseKind = "text";

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "payload_json", length = 65535)
    @JsonProperty("payload_json")
    private Map<String, Object> payloadJson;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }

    public Long getChannelAccountId() { return channelAccountId; }
    public void setChannelAccountId(Long channelAccountId) { this.channelAccountId = channelAccountId; }

    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }

    public String getMatchMode() { return matchMode; }
    public void setMatchMode(String matchMode) { this.matchMode = matchMode; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }

    public Map<String, Object> getScheduleJson() { return scheduleJson; }
    public void setScheduleJson(Map<String, Object> scheduleJson) { this.scheduleJson = scheduleJson; }

    public String getResponseKind() { return responseKind; }
    public void setResponseKind(String responseKind) { this.responseKind = responseKind; }

    public Map<String, Object> getPayloadJson() { return payloadJson; }
    public void setPayloadJson(Map<String, Object> payloadJson) { this.payloadJson = payloadJson; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
