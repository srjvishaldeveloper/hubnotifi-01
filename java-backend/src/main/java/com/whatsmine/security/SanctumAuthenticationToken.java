package com.whatsmine.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class SanctumAuthenticationToken extends AbstractAuthenticationToken {

    private final UserDetails principal;
    private final String token;
    private final List<String> abilities;

    public SanctumAuthenticationToken(UserDetails principal, String token, List<String> abilities, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.token = token;
        this.abilities = abilities;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    public List<String> getAbilities() {
        return abilities;
    }

    public boolean hasAbility(String ability) {
        if (abilities == null || abilities.isEmpty() || abilities.contains("*")) {
            return true;
        }
        return abilities.contains(ability);
    }
}
