package com.whatsmine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "social_post_accounts")
public class SocialPostAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "social_post_id", nullable = false)
    private Long socialPostId;

    @Column(name = "social_account_id", nullable = false)
    private Long socialAccountId;

    @Column(name = "status", length = 32)
    private String status = "pending";

    @Column(name = "post_id_external")
    private String postIdExternal;

    @Column(name = "error", length = 65535)
    private String error;

    public SocialPostAccount() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSocialPostId() { return socialPostId; }
    public void setSocialPostId(Long socialPostId) { this.socialPostId = socialPostId; }

    public Long getSocialAccountId() { return socialAccountId; }
    public void setSocialAccountId(Long socialAccountId) { this.socialAccountId = socialAccountId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPostIdExternal() { return postIdExternal; }
    public void setPostIdExternal(String postIdExternal) { this.postIdExternal = postIdExternal; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
