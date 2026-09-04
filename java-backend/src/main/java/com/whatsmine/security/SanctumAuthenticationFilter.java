package com.whatsmine.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.AdminUser;
import com.whatsmine.model.PersonalAccessToken;
import com.whatsmine.model.User;
import com.whatsmine.repository.AdminUserRepository;
import com.whatsmine.repository.PersonalAccessTokenRepository;
import com.whatsmine.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class SanctumAuthenticationFilter extends OncePerRequestFilter {

    private final PersonalAccessTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final AdminUserRepository adminUserRepository;
    private final ObjectMapper objectMapper;

    public SanctumAuthenticationFilter(
            PersonalAccessTokenRepository tokenRepository,
            UserRepository userRepository,
            AdminUserRepository adminUserRepository,
            ObjectMapper objectMapper) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.adminUserRepository = adminUserRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String tokenValue = authHeader.substring(7).trim();

            if (!tokenValue.isEmpty()) {
                Optional<PersonalAccessToken> tokenOpt = tokenRepository.findByToken(tokenValue);

                if (tokenOpt.isPresent()) {
                    PersonalAccessToken tokenEntity = tokenOpt.get();

                    // 1. Check expiration
                    if (tokenEntity.getExpiresAt() == null || tokenEntity.getExpiresAt().isAfter(LocalDateTime.now())) {

                        UserDetails userDetails = null;

                        // 2. Resolve tokenable entity (User or AdminUser)
                        if ("App\\Models\\User".equalsIgnoreCase(tokenEntity.getTokenableType())
                                || "User".equalsIgnoreCase(tokenEntity.getTokenableType())) {
                            Optional<User> userOpt = userRepository.findById(tokenEntity.getTokenableId());
                            if (userOpt.isPresent() && "active".equalsIgnoreCase(userOpt.get().getStatus())) {
                                userDetails = new CustomUserDetails(userOpt.get());
                            }
                        } else if ("App\\Models\\AdminUser".equalsIgnoreCase(tokenEntity.getTokenableType())
                                || "AdminUser".equalsIgnoreCase(tokenEntity.getTokenableType())) {
                            Optional<AdminUser> adminOpt = adminUserRepository.findById(tokenEntity.getTokenableId());
                            if (adminOpt.isPresent() && "ACTIVE".equalsIgnoreCase(adminOpt.get().getStatus())) {
                                userDetails = new AdminUserDetails(adminOpt.get());
                            }
                        }

                        if (userDetails != null) {
                            // 3. Parse abilities
                            List<String> abilities = parseAbilities(tokenEntity.getAbilities());

                            // 4. Update last_used_at
                            tokenEntity.setLastUsedAt(LocalDateTime.now());
                            tokenRepository.save(tokenEntity);

                            // 5. Create SanctumAuthenticationToken and set SecurityContextHolder
                            SanctumAuthenticationToken authToken = new SanctumAuthenticationToken(
                                    userDetails, tokenValue, abilities, userDetails.getAuthorities()
                            );
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                        }
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private List<String> parseAbilities(String abilitiesJson) {
        if (abilitiesJson == null || abilitiesJson.trim().isEmpty()) {
            return List.of("*");
        }
        try {
            return objectMapper.readValue(abilitiesJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of("*");
        }
    }
}
