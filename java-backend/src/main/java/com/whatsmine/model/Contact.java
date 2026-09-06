package com.whatsmine.model;

import com.whatsmine.model.converter.JsonMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "contacts")
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36, unique = true)
    private String uuid;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "phone_e164", length = 32)
    private String phoneE164;

    @Column(name = "email")
    private String email;

    @Column(name = "first_name", length = 128)
    private String firstName;

    @Column(name = "last_name", length = 128)
    private String lastName;

    @Column(name = "avatar", length = 512)
    private String avatar;

    @Column(name = "country", length = 8)
    private String country;

    @Column(name = "language", length = 8)
    private String language;

    @Column(name = "opt_in_whatsapp")
    private Boolean optInWhatsapp = true;

    @Column(name = "opt_in_sms")
    private Boolean optInSms = false;

    @Column(name = "opt_in_email")
    private Boolean optInEmail = false;

    @Column(name = "custom_fields", length = 65535)
    @Convert(converter = JsonMapConverter.class)
    private Map<String, Object> customFields;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "source", length = 64)
    private String source;

    @Column(name = "lead_id")
    private Long leadId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    public String getFullName() {
        String fn = firstName != null ? firstName : "";
        String ln = lastName != null ? lastName : "";
        return (fn + " " + ln).trim();
    }

    @Transient
    public String getAvatarUrl() {
        if (avatar == null || avatar.isBlank()) {
            return null;
        }
        if (avatar.startsWith("http")) {
            return avatar;
        }
        return "/storage/" + avatar;
    }

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public String getPhoneE164() { return phoneE164; }
    public void setPhoneE164(String phoneE164) { this.phoneE164 = phoneE164; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Boolean getOptInWhatsapp() { return optInWhatsapp; }
    public void setOptInWhatsapp(Boolean optInWhatsapp) { this.optInWhatsapp = optInWhatsapp; }
    public Boolean getOptInSms() { return optInSms; }
    public void setOptInSms(Boolean optInSms) { this.optInSms = optInSms; }
    public Boolean getOptInEmail() { return optInEmail; }
    public void setOptInEmail(Boolean optInEmail) { this.optInEmail = optInEmail; }
    public Map<String, Object> getCustomFields() { return customFields; }
    public void setCustomFields(Map<String, Object> customFields) { this.customFields = customFields; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getLeadId() { return leadId; }
    public void setLeadId(Long leadId) { this.leadId = leadId; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
