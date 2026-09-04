package com.whatsmine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.Duration;
import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36, unique = true)
    private String uuid;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "channel_account_id")
    private Long channelAccountId;

    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    @Column(name = "external_thread_id")
    private String externalThreadId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "open";

    @Column(name = "assigned_user_id")
    private Long assignedUserId;

    @Column(name = "assigned_to", length = 32)
    private String assignedTo = "human";

    @Column(name = "unread_count")
    private Integer unreadCount = 0;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "first_response_at")
    private LocalDateTime firstResponseAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "last_inbound_at")
    private LocalDateTime lastInboundAt;

    @Column(name = "handover_at")
    private LocalDateTime handoverAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "contact_id", insertable = false, updatable = false)
    private Contact contact;

    @ManyToOne
    @JoinColumn(name = "channel_account_id", insertable = false, updatable = false)
    private ChannelAccount channelAccount;

    @ManyToOne
    @JoinColumn(name = "assigned_user_id", insertable = false, updatable = false)
    private User assignedUser;

    @ManyToMany
    @JoinTable(
            name = "inbox_label_conversation",
            joinColumns = @JoinColumn(name = "conversation_id"),
            inverseJoinColumns = @JoinColumn(name = "label_id")
    )
    private List<InboxLabel> labels = new ArrayList<>();

    @Transient
    private Message lastMessage;

    @Transient
    private Boolean isWhatsappWindowOpen;

    @PrePersist
    protected void onCreate() {
        if (this.uuid == null || this.uuid.isBlank()) {
            this.uuid = UUID.randomUUID().toString();
        }
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean checkWhatsappWindowOpen() {
        if (channelAccount != null && !"whatsapp".equalsIgnoreCase(channelAccount.getChannel())) {
            return true;
        }
        if (lastInboundAt == null) {
            return false;
        }
        return Duration.between(lastInboundAt, LocalDateTime.now()).toHours() < 24;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public Long getChannelAccountId() { return channelAccountId; }
    public void setChannelAccountId(Long channelAccountId) { this.channelAccountId = channelAccountId; }
    public Long getContactId() { return contactId; }
    public void setContactId(Long contactId) { this.contactId = contactId; }
    public String getExternalThreadId() { return externalThreadId; }
    public void setExternalThreadId(String externalThreadId) { this.externalThreadId = externalThreadId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getAssignedUserId() { return assignedUserId; }
    public void setAssignedUserId(Long assignedUserId) { this.assignedUserId = assignedUserId; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public Integer getUnreadCount() { return unreadCount; }
    public void setUnreadCount(Integer unreadCount) { this.unreadCount = unreadCount; }
    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }
    public LocalDateTime getFirstResponseAt() { return firstResponseAt; }
    public void setFirstResponseAt(LocalDateTime firstResponseAt) { this.firstResponseAt = firstResponseAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public LocalDateTime getLastInboundAt() { return lastInboundAt; }
    public void setLastInboundAt(LocalDateTime lastInboundAt) { this.lastInboundAt = lastInboundAt; }
    public LocalDateTime getHandoverAt() { return handoverAt; }
    public void setHandoverAt(LocalDateTime handoverAt) { this.handoverAt = handoverAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Contact getContact() { return contact; }
    public void setContact(Contact contact) { this.contact = contact; }
    public ChannelAccount getChannelAccount() { return channelAccount; }
    public void setChannelAccount(ChannelAccount channelAccount) { this.channelAccount = channelAccount; }
    public User getAssignedUser() { return assignedUser; }
    public void setAssignedUser(User assignedUser) { this.assignedUser = assignedUser; }
    public List<InboxLabel> getLabels() { return labels; }
    public void setLabels(List<InboxLabel> labels) { this.labels = labels; }
    public Message getLastMessage() { return lastMessage; }
    public void setLastMessage(Message lastMessage) { this.lastMessage = lastMessage; }
    public Boolean getIsWhatsappWindowOpen() { return isWhatsappWindowOpen; }
    public void setIsWhatsappWindowOpen(Boolean isWhatsappWindowOpen) { this.isWhatsappWindowOpen = isWhatsappWindowOpen; }
}
