package com.whatsmine.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    private Boolean remember = false;

    public LoginRequest() {}

    public LoginRequest(String email, String password, Boolean remember) {
        this.email = email;
        this.password = password;
        this.remember = remember;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Boolean getRemember() {
        return remember;
    }

    public void setRemember(Boolean remember) {
        this.remember = remember;
    }
}
