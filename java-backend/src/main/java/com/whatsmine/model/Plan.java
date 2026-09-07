package com.whatsmine.model;

import com.whatsmine.model.converter.JsonAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "description", length = 65535)
    private String description;

    @Column(name = "price_cents", nullable = false)
    private Long priceCents;

    @Column(name = "currency_code", length = 10, nullable = false)
    private String currencyCode;

    @Column(name = "\"interval\"", length = 20)
    private String interval = "month";

    @Column(name = "monthly_price_cents")
    private Long monthlyPriceCents;

    @Column(name = "yearly_price_cents")
    private Long yearlyPriceCents;

    @Column(name = "trial_days")
    private Integer trialDays = 0;

    @Column(name = "stripe_monthly_id")
    private String stripeMonthlyId;

    @Column(name = "stripe_yearly_id")
    private String stripeYearlyId;

    @Column(name = "paddle_monthly_id")
    private String paddleMonthlyId;

    @Column(name = "paddle_yearly_id")
    private String paddleYearlyId;

    @Convert(converter = JsonAttributeConverter.class)
    @Column(name = "features", length = 65535)
    private Map<String, Object> features;

    @Convert(converter = JsonAttributeConverter.class)
    @Column(name = "limits", length = 65535)
    private Map<String, Object> limits;

    @Column(name = "featured")
    private Boolean featured = false;

    @Column(name = "popular")
    private Boolean popular = false;

    @Column(name = "white_label_enabled")
    private Boolean whiteLabelEnabled = false;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "enabled")
    private Boolean enabled = true;

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

    public Plan() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(Long priceCents) {
        this.priceCents = priceCents;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public Long getMonthlyPriceCents() {
        return monthlyPriceCents;
    }

    public void setMonthlyPriceCents(Long monthlyPriceCents) {
        this.monthlyPriceCents = monthlyPriceCents;
    }

    public Long getYearlyPriceCents() {
        return yearlyPriceCents;
    }

    public void setYearlyPriceCents(Long yearlyPriceCents) {
        this.yearlyPriceCents = yearlyPriceCents;
    }

    public Integer getTrialDays() {
        return trialDays;
    }

    public void setTrialDays(Integer trialDays) {
        this.trialDays = trialDays;
    }

    public String getStripeMonthlyId() {
        return stripeMonthlyId;
    }

    public void setStripeMonthlyId(String stripeMonthlyId) {
        this.stripeMonthlyId = stripeMonthlyId;
    }

    public String getStripeYearlyId() {
        return stripeYearlyId;
    }

    public void setStripeYearlyId(String stripeYearlyId) {
        this.stripeYearlyId = stripeYearlyId;
    }

    public String getPaddleMonthlyId() {
        return paddleMonthlyId;
    }

    public void setPaddleMonthlyId(String paddleMonthlyId) {
        this.paddleMonthlyId = paddleMonthlyId;
    }

    public String getPaddleYearlyId() {
        return paddleYearlyId;
    }

    public void setPaddleYearlyId(String paddleYearlyId) {
        this.paddleYearlyId = paddleYearlyId;
    }

    public Map<String, Object> getFeatures() {
        return features;
    }

    public void setFeatures(Map<String, Object> features) {
        this.features = features;
    }

    /**
     * Feature names whose value is truthy, in insertion order — the display
     * list expected by the marketing/pricing pages (they render a bullet per
     * entry), as opposed to {@link #getFeatures()} which is the raw
     * name-&gt;enabled map used by the admin plan editor.
     */
    public List<String> getFeatureList() {
        if (features == null || features.isEmpty()) {
            return List.of();
        }
        return features.entrySet().stream()
                .filter(e -> isTruthy(e.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return !s.isBlank() && !s.equalsIgnoreCase("false") && !s.equals("0");
        }
        return true;
    }

    public Map<String, Object> getLimits() {
        return limits;
    }

    public void setLimits(Map<String, Object> limits) {
        this.limits = limits;
    }

    public Boolean getFeatured() {
        return featured;
    }

    public void setFeatured(Boolean featured) {
        this.featured = featured;
    }

    public Boolean getPopular() {
        return popular;
    }

    public void setPopular(Boolean popular) {
        this.popular = popular;
    }

    public Boolean getWhiteLabelEnabled() {
        return whiteLabelEnabled;
    }

    public void setWhiteLabelEnabled(Boolean whiteLabelEnabled) {
        this.whiteLabelEnabled = whiteLabelEnabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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
