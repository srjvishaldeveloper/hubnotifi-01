package com.whatsmine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaign_recipients")
@JsonIgnoreProperties(ignoreUnknown = true)
public class CampaignRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    @JsonProperty("campaign_id")
    private Long campaignId;

    @Column(name = "contact_id", nullable = false)
    @JsonProperty("contact_id")
    private Long contactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", insertable = false, updatable = false)
    private Contact contact;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "queued";

    @Column(name = "provider_message_id", length = 128)
    @JsonProperty("provider_message_id")
    private String providerMessageId;

    @Column(name = "tracking_token", length = 64)
    @JsonProperty("tracking_token")
    private String trackingToken;

    @Column(name = "unsubscribe_token", length = 64)
    @JsonProperty("unsubscribe_token")
    private String unsubscribeToken;

    @Column(name = "sent_at")
    @JsonProperty("sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    @JsonProperty("delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "read_at")
    @JsonProperty("read_at")
    private LocalDateTime readAt;

    @Column(name = "clicked_at")
    @JsonProperty("clicked_at")
    private LocalDateTime clickedAt;

    @Column(name = "opted_out_at")
    @JsonProperty("opted_out_at")
    private LocalDateTime optedOutAt;

    @Lob
    @Column(name = "failed_reason")
    @JsonProperty("failed_reason")
    private String failedReason;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(Long campaignId) {
        this.campaignId = campaignId;
    }

    public Long getContactId() {
        return contactId;
    }

    public void setContactId(Long contactId) {
        this.contactId = contactId;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    public String getTrackingToken() {
        return trackingToken;
    }

    public void setTrackingToken(String trackingToken) {
        this.trackingToken = trackingToken;
    }

    public String getUnsubscribeToken() {
        return unsubscribeToken;
    }

    public void setUnsubscribeToken(String unsubscribeToken) {
        this.unsubscribeToken = unsubscribeToken;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    public LocalDateTime getClickedAt() {
        return clickedAt;
    }

    public void setClickedAt(LocalDateTime clickedAt) {
        this.clickedAt = clickedAt;
    }

    public LocalDateTime getOptedOutAt() {
        return optedOutAt;
    }

    public void setOptedOutAt(LocalDateTime optedOutAt) {
        this.optedOutAt = optedOutAt;
    }

    public String getFailedReason() {
        return failedReason;
    }

    public void setFailedReason(String failedReason) {
        this.failedReason = failedReason;
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
