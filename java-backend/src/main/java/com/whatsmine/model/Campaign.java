package com.whatsmine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Campaign {

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

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "whatsapp_phone_number_id", length = 64)
    @JsonProperty("whatsapp_phone_number_id")
    private String whatsappPhoneNumberId;

    @Column(name = "audience_type", nullable = false, length = 32)
    @JsonProperty("audience_type")
    private String audienceType;

    @Column(name = "audience_ref", length = 255)
    @JsonProperty("audience_ref")
    private String audienceRef;

    @Column(name = "template_ref", length = 65535)
    @JsonProperty("template_ref")
    private String templateRef;

    @Column(name = "payload_json", length = 65535)
    @JsonProperty("payload_json")
    private String payloadJson;

    @Column(name = "schedule_at")
    @JsonProperty("schedule_at")
    private LocalDateTime scheduleAt;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "draft";

    @Column(name = "totals_json", length = 65535)
    @JsonProperty("totals_json")
    private String totalsJson;

    @Column(name = "created_by")
    @JsonProperty("created_by")
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @Transient
    @JsonProperty("recipients_count")
    private Long recipientsCount;

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

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getWhatsappPhoneNumberId() {
        return whatsappPhoneNumberId;
    }

    public void setWhatsappPhoneNumberId(String whatsappPhoneNumberId) {
        this.whatsappPhoneNumberId = whatsappPhoneNumberId;
    }

    public String getAudienceType() {
        return audienceType;
    }

    public void setAudienceType(String audienceType) {
        this.audienceType = audienceType;
    }

    public String getAudienceRef() {
        return audienceRef;
    }

    public void setAudienceRef(String audienceRef) {
        this.audienceRef = audienceRef;
    }

    public String getTemplateRef() {
        return templateRef;
    }

    public void setTemplateRef(String templateRef) {
        this.templateRef = templateRef;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public LocalDateTime getScheduleAt() {
        return scheduleAt;
    }

    public void setScheduleAt(LocalDateTime scheduleAt) {
        this.scheduleAt = scheduleAt;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTotalsJson() {
        return totalsJson;
    }

    public void setTotalsJson(String totalsJson) {
        this.totalsJson = totalsJson;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
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

    public Long getRecipientsCount() {
        return recipientsCount;
    }

    public void setRecipientsCount(Long recipientsCount) {
        this.recipientsCount = recipientsCount;
    }
}
