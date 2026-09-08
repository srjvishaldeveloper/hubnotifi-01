package com.whatsmine.model;

import com.whatsmine.model.converter.JsonListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 64, nullable = false, unique = true)
    private String code;

    @Column(name = "kind", length = 20, nullable = false)
    private String kind = "percent";

    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "duration", length = 20, nullable = false)
    private String duration = "once";

    @Column(name = "duration_in_months")
    private Integer durationInMonths;

    @Convert(converter = JsonListConverter.class)
    @Column(name = "applies_to_plan_ids", length = 65535)
    private List<String> appliesToPlanIds;

    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(name = "times_redeemed", nullable = false)
    private Integer timesRedeemed = 0;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "stripe_coupon_id")
    private String stripeCouponId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Coupon() {}

    public boolean isValid() {
        if (Boolean.FALSE.equals(enabled)) {
            return false;
        }
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            return false;
        }
        if (maxRedemptions != null && timesRedeemed >= maxRedemptions) {
            return false;
        }
        return true;
    }

    public int discountCents(int priceCents) {
        if ("percent".equalsIgnoreCase(kind)) {
            double percent = amount != null ? amount.doubleValue() : 0.0;
            return (int) Math.round(priceCents * (percent / 100.0));
        }
        int fixedCents = amount != null ? amount.multiply(BigDecimal.valueOf(100)).intValue() : 0;
        return Math.min(fixedCents, priceCents);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public Integer getDurationInMonths() {
        return durationInMonths;
    }

    public void setDurationInMonths(Integer durationInMonths) {
        this.durationInMonths = durationInMonths;
    }

    public List<String> getAppliesToPlanIds() {
        return appliesToPlanIds;
    }

    public void setAppliesToPlanIds(List<String> appliesToPlanIds) {
        this.appliesToPlanIds = appliesToPlanIds;
    }

    public Integer getMaxRedemptions() {
        return maxRedemptions;
    }

    public void setMaxRedemptions(Integer maxRedemptions) {
        this.maxRedemptions = maxRedemptions;
    }

    public Integer getTimesRedeemed() {
        return timesRedeemed;
    }

    public void setTimesRedeemed(Integer timesRedeemed) {
        this.timesRedeemed = timesRedeemed;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getStripeCouponId() {
        return stripeCouponId;
    }

    public void setStripeCouponId(String stripeCouponId) {
        this.stripeCouponId = stripeCouponId;
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
