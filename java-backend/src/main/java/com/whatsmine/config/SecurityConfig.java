package com.whatsmine.config;

import com.whatsmine.security.AdminUserDetailsService;
import com.whatsmine.security.CustomUserDetailsService;
import com.whatsmine.security.InertiaAccessDeniedHandler;
import com.whatsmine.security.InertiaAuthenticationEntryPoint;
import com.whatsmine.security.SanctumAuthenticationFilter;
import com.whatsmine.security.WorkspaceSecurityFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final AdminUserDetailsService adminUserDetailsService;
    private final SanctumAuthenticationFilter sanctumFilter;
    private final WorkspaceSecurityFilter workspaceSecurityFilter;
    private final InertiaAuthenticationEntryPoint authenticationEntryPoint;
    private final InertiaAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            CustomUserDetailsService userDetailsService,
            AdminUserDetailsService adminUserDetailsService,
            SanctumAuthenticationFilter sanctumFilter,
            WorkspaceSecurityFilter workspaceSecurityFilter,
            InertiaAuthenticationEntryPoint authenticationEntryPoint,
            InertiaAccessDeniedHandler accessDeniedHandler) {
        this.userDetailsService = userDetailsService;
        this.adminUserDetailsService = adminUserDetailsService;
        this.sanctumFilter = sanctumFilter;
        this.workspaceSecurityFilter = workspaceSecurityFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    @Primary
    public AuthenticationManager clientAuthenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(provider);
    }

    @Bean(name = "adminAuthenticationManager")
    public AuthenticationManager adminAuthenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(adminUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(provider);
    }

    // 1. Admin Security Chain (/admin/**)
    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http
                .securityMatcher("/admin/**")
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(requestHandler)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login", "/admin/forgot-password", "/admin/reset-password/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                );

        return http.build();
    }

    // 2. Sanctum API Security Chain (/api/**)
    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health", "/actuator/health", "/api/ziggy.js").permitAll()
                        .requestMatchers("/api/**").authenticated()
                )
                .addFilterBefore(sanctumFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 3. Client Web Application Security Chain (/**)
    @Bean
    @Order(3)
    public SecurityFilterChain clientSecurityFilterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(requestHandler)
                        .ignoringRequestMatchers("/api/**", "/auth/firebase", "/webhooks/**", "/widgets/**")
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login",
                                "/register",
                                "/forgot-password",
                                "/reset-password/**",
                                "/magic-link/**",
                                "/two-factor-challenge",
                                "/auth/**",
                                "/invitations/**",
                                "/build/**",
                                "/storage/**",
                                "/images/**",
                                "/whatsmine-icon.svg",
                                "/api/ziggy.js",
                                "/favicon.ico",
                                "/webhooks/**",
                                "/widgets/**",
                                "/pages/**"
                        ).permitAll()
                        .requestMatchers("/app/**", "/client/**").hasRole("CLIENT")
                        .anyRequest().authenticated()
                )
                .addFilterAfter(workspaceSecurityFilter, UsernamePasswordAuthenticationFilter.class)
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                );

        return http.build();
    }
}
