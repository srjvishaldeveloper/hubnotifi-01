package com.whatsmine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "direction", nullable = false, length = 8)
    private String direction;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel = "whatsapp";

    @Column(name = "type", nullable = false, length = 32)
    private String type = "text";

    @Column(name = "body", length = 65535)
    private String body;

    @Column(name = "payload", length = 65535)
    private String payload;

    @Column(name = "media_id")
    private String mediaId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "queued";

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "error_json", length = 65535)
    private String errorJson;

    @Column(name = "sent_by", length = 32)
    private String sentBy = "human";

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.sentAt == null) {
            this.sentAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getMediaId() { return mediaId; }
    public void setMediaId(String mediaId) { this.mediaId = mediaId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public String getErrorJson() { return errorJson; }
    public void setErrorJson(String errorJson) { this.errorJson = errorJson; }
    public String getSentBy() { return sentBy; }
    public void setSentBy(String sentBy) { this.sentBy = sentBy; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
