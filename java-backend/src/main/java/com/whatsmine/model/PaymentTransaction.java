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
import java.util.Map;

@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "gateway", length = 50, nullable = false)
    private String gateway;

    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents = 0;

    @Column(name = "currency_code", length = 10, nullable = false)
    private String currencyCode = "USD";

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "status", length = 50, nullable = false)
    private String status = "pending";

    @Convert(converter = JsonAttributeConverter.class)
    @Column(name = "payload", length = 65535)
    private Map<String, Object> payload;

    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "refund_reason")
    private String refundReason;

    @Column(name = "refunded_cents")
    private Integer refundedCents;

    @Column(name = "invoice_path")
    private String invoicePath;

    @Column(name = "tax_amount_cents")
    private Integer taxAmountCents = 0;

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

    public PaymentTransaction() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public void setGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public Integer getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(Integer amountCents) {
        this.amountCents = amountCents;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public Long getCouponId() {
        return couponId;
    }

    public void setCouponId(Long couponId) {
        this.couponId = couponId;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }

    public void setRefundedAt(LocalDateTime refundedAt) {
        this.refundedAt = refundedAt;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    public Integer getRefundedCents() {
        return refundedCents;
    }

    public void setRefundedCents(Integer refundedCents) {
        this.refundedCents = refundedCents;
    }

    public String getInvoicePath() {
        return invoicePath;
    }

    public void setInvoicePath(String invoicePath) {
        this.invoicePath = invoicePath;
    }

    public Integer getTaxAmountCents() {
        return taxAmountCents;
    }

    public void setTaxAmountCents(Integer taxAmountCents) {
        this.taxAmountCents = taxAmountCents;
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
