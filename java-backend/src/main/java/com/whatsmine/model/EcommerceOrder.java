package com.whatsmine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatsmine.model.converter.JsonListMapConverter;
import com.whatsmine.model.converter.JsonMapConverter;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "ecommerce_orders")
@JsonIgnoreProperties(ignoreUnknown = true)
public class EcommerceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    @JsonProperty("workspace_id")
    private Long workspaceId;

    @Column(name = "store_id", nullable = false)
    @JsonProperty("store_id")
    private Long storeId;

    @Column(name = "contact_id")
    @JsonProperty("contact_id")
    private Long contactId;

    @Column(name = "external_order_id", nullable = false)
    @JsonProperty("external_order_id")
    private String externalOrderId;

    @Column(name = "platform", nullable = false, length = 20)
    private String platform;

    @Column(name = "number")
    private String number;

    @Column(name = "status", length = 40)
    private String status;

    @Column(name = "financial_status", length = 40)
    @JsonProperty("financial_status")
    private String financialStatus;

    @Column(name = "fulfillment_status", length = 40)
    @JsonProperty("fulfillment_status")
    private String fulfillmentStatus;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Convert(converter = JsonListMapConverter.class)
    @Column(name = "line_items")
    @JsonProperty("line_items")
    private List<Map<String, Object>> lineItems;

    @Column(name = "tracking_url", length = 512)
    @JsonProperty("tracking_url")
    private String trackingUrl;

    @Column(name = "tracking_number")
    @JsonProperty("tracking_number")
    private String trackingNumber;

    @Column(name = "placed_at")
    @JsonProperty("placed_at")
    private LocalDateTime placedAt;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "raw")
    private Map<String, Object> raw;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", insertable = false, updatable = false)
    @JsonProperty("contact")
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", insertable = false, updatable = false)
    @JsonProperty("store")
    private EcommerceStore store;

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

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public Long getContactId() {
        return contactId;
    }

    public void setContactId(Long contactId) {
        this.contactId = contactId;
    }

    public String getExternalOrderId() {
        return externalOrderId;
    }

    public void setExternalOrderId(String externalOrderId) {
        this.externalOrderId = externalOrderId;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFinancialStatus() {
        return financialStatus;
    }

    public void setFinancialStatus(String financialStatus) {
        this.financialStatus = financialStatus;
    }

    public String getFulfillmentStatus() {
        return fulfillmentStatus;
    }

    public void setFulfillmentStatus(String fulfillmentStatus) {
        this.fulfillmentStatus = fulfillmentStatus;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public List<Map<String, Object>> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<Map<String, Object>> lineItems) {
        this.lineItems = lineItems;
    }

    public String getTrackingUrl() {
        return trackingUrl;
    }

    public void setTrackingUrl(String trackingUrl) {
        this.trackingUrl = trackingUrl;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public LocalDateTime getPlacedAt() {
        return placedAt;
    }

    public void setPlacedAt(LocalDateTime placedAt) {
        this.placedAt = placedAt;
    }

    public Map<String, Object> getRaw() {
        return raw;
    }

    public void setRaw(Map<String, Object> raw) {
        this.raw = raw;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public EcommerceStore getStore() {
        return store;
    }

    public void setStore(EcommerceStore store) {
        this.store = store;
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
