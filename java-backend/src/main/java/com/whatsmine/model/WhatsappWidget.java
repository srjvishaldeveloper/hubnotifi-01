package com.whatsmine.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatsmine.model.converter.JsonListConverter;
import com.whatsmine.model.converter.JsonMapConverter;
import jakarta.persistence.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Embeddable "click to WhatsApp" website widget, porting PHP's
 * WhatsappWidget / whatsapp_widgets table. widgetKey is the public id
 * embedded in the third-party site's <script> tag.
 */
@Entity
@Table(name = "whatsapp_widgets")
public class WhatsappWidget {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    @JsonProperty("workspace_id")
    private Long workspaceId;

    @Column(name = "name")
    private String name;

    @Column(name = "widget_key", nullable = false, unique = true, length = 64)
    @JsonProperty("widget_key")
    private String widgetKey;

    @Column(name = "phone_number_id")
    @JsonProperty("phone_number_id")
    private String phoneNumberId;

    @Column(name = "display_phone")
    @JsonProperty("display_phone")
    private String displayPhone;

    @Column(name = "prefilled_message", length = 1024)
    @JsonProperty("prefilled_message")
    private String prefilledMessage;

    @Column(name = "greeting_message", length = 1024)
    @JsonProperty("greeting_message")
    private String greetingMessage;

    @Column(name = "agent_name")
    @JsonProperty("agent_name")
    private String agentName = "Support";

    @Column(name = "agent_avatar_color", length = 16)
    @JsonProperty("agent_avatar_color")
    private String agentAvatarColor = "#25D366";

    @Column(name = "button_color", length = 16)
    @JsonProperty("button_color")
    private String buttonColor = "#25D366";

    /** bottom_right | bottom_left */
    @Column(name = "position", length = 16)
    private String position = "bottom_right";

    /** Domain whitelist; empty = allow all. */
    @Convert(converter = JsonListConverter.class)
    @Column(name = "allowed_domains", length = 2048)
    @JsonProperty("allowed_domains")
    private List<String> allowedDomains;

    /** {enabled, timezone, schedule:{sun..sat:{enabled,open,close}}} */
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "working_hours_json", length = 4096)
    @JsonProperty("working_hours_json")
    private Map<String, Object> workingHoursJson;

    @Column(name = "created_at", updatable = false)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (widgetKey == null || widgetKey.isBlank()) {
            widgetKey = generateKey();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private static String generateKey() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.substring(0, 32);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getWidgetKey() { return widgetKey; }
    public void setWidgetKey(String widgetKey) { this.widgetKey = widgetKey; }

    public String getPhoneNumberId() { return phoneNumberId; }
    public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }

    public String getDisplayPhone() { return displayPhone; }
    public void setDisplayPhone(String displayPhone) { this.displayPhone = displayPhone; }

    public String getPrefilledMessage() { return prefilledMessage; }
    public void setPrefilledMessage(String prefilledMessage) { this.prefilledMessage = prefilledMessage; }

    public String getGreetingMessage() { return greetingMessage; }
    public void setGreetingMessage(String greetingMessage) { this.greetingMessage = greetingMessage; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getAgentAvatarColor() { return agentAvatarColor; }
    public void setAgentAvatarColor(String agentAvatarColor) { this.agentAvatarColor = agentAvatarColor; }

    public String getButtonColor() { return buttonColor; }
    public void setButtonColor(String buttonColor) { this.buttonColor = buttonColor; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public List<String> getAllowedDomains() { return allowedDomains; }
    public void setAllowedDomains(List<String> allowedDomains) { this.allowedDomains = allowedDomains; }

    public Map<String, Object> getWorkingHoursJson() { return workingHoursJson; }
    public void setWorkingHoursJson(Map<String, Object> workingHoursJson) { this.workingHoursJson = workingHoursJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
