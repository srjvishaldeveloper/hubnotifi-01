package com.whatsmine.security;

import com.whatsmine.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    public Long getId() {
        return user.getId();
    }

    public Long getClientId() {
        return user.getClientId();
    }

    public Long getWorkspaceId() {
        return user.getWorkspaceId();
    }

    public String getClientRole() {
        return user.getClientRole();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String roleName = user.getRole() != null ? user.getRole().toUpperCase() : "CLIENT";
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + roleName));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !"suspended".equalsIgnoreCase(user.getStatus()) && !"banned".equalsIgnoreCase(user.getStatus());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "active".equalsIgnoreCase(user.getStatus());
    }
}
