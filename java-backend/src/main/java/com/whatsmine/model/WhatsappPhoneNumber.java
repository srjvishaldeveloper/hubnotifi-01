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
@Table(name = "whatsapp_phone_numbers")
public class WhatsappPhoneNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "waba_id_fk", nullable = false)
    private Long wabaIdFk;

    @Column(name = "phone_number_id", nullable = false, length = 64, unique = true)
    private String phoneNumberId;

    @Column(name = "display_phone", length = 32)
    private String displayPhone;

    @Column(name = "verified_name", length = 128)
    private String verifiedName;

    @Column(name = "quality_rating", length = 32)
    private String qualityRating;

    @Column(name = "messaging_limit_tier", length = 64)
    private String messagingLimitTier;

    @Column(name = "code_verification_status")
    private String codeVerificationStatus;

    @Column(name = "name_status", length = 64)
    private String nameStatus;

    @Column(name = "requested_verified_name", length = 128)
    private String requestedVerifiedName;

    @Column(name = "account_mode", length = 32)
    private String accountMode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWabaIdFk() { return wabaIdFk; }
    public void setWabaIdFk(Long wabaIdFk) { this.wabaIdFk = wabaIdFk; }
    public String getPhoneNumberId() { return phoneNumberId; }
    public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }
    public String getDisplayPhone() { return displayPhone; }
    public void setDisplayPhone(String displayPhone) { this.displayPhone = displayPhone; }
    public String getVerifiedName() { return verifiedName; }
    public void setVerifiedName(String verifiedName) { this.verifiedName = verifiedName; }
    public String getQualityRating() { return qualityRating; }
    public void setQualityRating(String qualityRating) { this.qualityRating = qualityRating; }
    public String getMessagingLimitTier() { return messagingLimitTier; }
    public void setMessagingLimitTier(String messagingLimitTier) { this.messagingLimitTier = messagingLimitTier; }
    public String getCodeVerificationStatus() { return codeVerificationStatus; }
    public void setCodeVerificationStatus(String codeVerificationStatus) { this.codeVerificationStatus = codeVerificationStatus; }
    public String getNameStatus() { return nameStatus; }
    public void setNameStatus(String nameStatus) { this.nameStatus = nameStatus; }
    public String getRequestedVerifiedName() { return requestedVerifiedName; }
    public void setRequestedVerifiedName(String requestedVerifiedName) { this.requestedVerifiedName = requestedVerifiedName; }
    public String getAccountMode() { return accountMode; }
    public void setAccountMode(String accountMode) { this.accountMode = accountMode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
