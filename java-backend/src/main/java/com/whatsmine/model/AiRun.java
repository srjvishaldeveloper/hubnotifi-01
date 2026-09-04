package com.whatsmine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_runs")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chatbot_id")
    @JsonProperty("chatbot_id")
    private Long chatbotId;

    @Column(name = "conversation_id")
    @JsonProperty("conversation_id")
    private Long conversationId;

    @Column(name = "prompt_tokens", nullable = false)
    @JsonProperty("prompt_tokens")
    private Integer promptTokens = 0;

    @Column(name = "completion_tokens", nullable = false)
    @JsonProperty("completion_tokens")
    private Integer completionTokens = 0;

    @Column(name = "cost_cents", nullable = false)
    @JsonProperty("cost_cents")
    private Integer costCents = 0;

    @Column(name = "latency_ms", nullable = false)
    @JsonProperty("latency_ms")
    private Integer latencyMs = 0;

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ok";

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

    public Long getChatbotId() {
        return chatbotId;
    }

    public void setChatbotId(Long chatbotId) {
        this.chatbotId = chatbotId;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getCostCents() {
        return costCents;
    }

    public void setCostCents(Integer costCents) {
        this.costCents = costCents;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
