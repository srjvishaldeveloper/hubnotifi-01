package com.whatsmine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "leads")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    @JsonProperty("workspace_id")
    private Long workspaceId;

    @Column(name = "name", length = 256)
    private String name;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "email", length = 256)
    private String email;

    @Column(name = "website", length = 512)
    private String website;

    @Column(name = "address", length = 512)
    private String address;

    @Column(name = "city", length = 128)
    private String city;

    @Column(name = "country", length = 64)
    private String country;

    @Column(name = "lat", precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(name = "lng", precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(name = "category", length = 128)
    private String category;

    @Column(name = "rating", precision = 3, scale = 1)
    private BigDecimal rating;

    @Column(name = "review_count", nullable = false)
    @JsonProperty("review_count")
    private Integer reviewCount = 0;

    @Column(name = "google_place_id", length = 128, unique = true)
    @JsonProperty("google_place_id")
    private String googlePlaceId;

    @Column(name = "whatsapp_status", nullable = false, length = 32)
    @JsonProperty("whatsapp_status")
    private String whatsappStatus = "unknown";

    @Column(name = "pushed_to_contacts", nullable = false)
    @JsonProperty("pushed_to_contacts")
    private boolean pushedToContacts = false;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public void setLat(BigDecimal lat) {
        this.lat = lat;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public void setLng(BigDecimal lng) {
        this.lng = lng;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }

    public String getGooglePlaceId() {
        return googlePlaceId;
    }

    public void setGooglePlaceId(String googlePlaceId) {
        this.googlePlaceId = googlePlaceId;
    }

    public String getWhatsappStatus() {
        return whatsappStatus;
    }

    public void setWhatsappStatus(String whatsappStatus) {
        this.whatsappStatus = whatsappStatus;
    }

    public boolean isPushedToContacts() {
        return pushedToContacts;
    }

    public void setPushedToContacts(boolean pushedToContacts) {
        this.pushedToContacts = pushedToContacts;
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
