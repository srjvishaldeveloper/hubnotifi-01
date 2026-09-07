package com.whatsmine.config;

import com.whatsmine.security.AdminUserDetailsService;
import com.whatsmine.security.CustomUserDetailsService;
import com.whatsmine.security.InertiaAccessDeniedHandler;
import com.whatsmine.security.InertiaAuthenticationEntryPoint;
import com.whatsmine.security.LicenseGateFilter;
import com.whatsmine.security.SanctumAuthenticationFilter;
import com.whatsmine.security.WorkspaceSecurityFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final AdminUserDetailsService adminUserDetailsService;
    private final SanctumAuthenticationFilter sanctumFilter;
    private final WorkspaceSecurityFilter workspaceSecurityFilter;
    private final LicenseGateFilter licenseGateFilter;
    private final InertiaAuthenticationEntryPoint authenticationEntryPoint;
    private final InertiaAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            CustomUserDetailsService userDetailsService,
            AdminUserDetailsService adminUserDetailsService,
            SanctumAuthenticationFilter sanctumFilter,
            WorkspaceSecurityFilter workspaceSecurityFilter,
            LicenseGateFilter licenseGateFilter,
            InertiaAuthenticationEntryPoint authenticationEntryPoint,
            InertiaAccessDeniedHandler accessDeniedHandler) {
        this.userDetailsService = userDetailsService;
        this.adminUserDetailsService = adminUserDetailsService;
        this.sanctumFilter = sanctumFilter;
        this.workspaceSecurityFilter = workspaceSecurityFilter;
        this.licenseGateFilter = licenseGateFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    // Backs the "Active Sessions" profile page (list/revoke other devices).
    // Client login is performed manually (AuthService / TwoFactorChallengeController set
    // the SecurityContext directly rather than going through the standard authentication
    // filter), so those call sites register the session with this registry themselves.
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    // Required for SessionRegistry to be notified when a session is invalidated
    // (logout, expiry, or a revoke-other-sessions request) so it stops listing it.
    // Spring Boot auto-registers HttpSessionListener beans with the servlet container.
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * LicenseGateFilter is a @Component OncePerRequestFilter, which Spring
     * Boot would otherwise ALSO auto-register as a generic servlet filter
     * running on every request — bypassing the admin chain's securityMatcher
     * entirely and blocking unrelated routes (like /license itself) whenever
     * licensing is enabled but unverified. Disabling that generic
     * registration here so it only runs where addFilterAfter wires it below,
     * scoped to /admin/**.
     */
    @Bean
    public FilterRegistrationBean<LicenseGateFilter> disableLicenseGateFilterAutoRegistration(LicenseGateFilter filter) {
        FilterRegistrationBean<LicenseGateFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
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
                        // Callable by the impersonated user (client role) to return to their
                        // own admin session — deliberately not admin-gated, same as PHP's
                        // ImpersonationController::stop route (['web', 'auth'], no auth:admin).
                        .requestMatchers("/admin/impersonation/stop").authenticated()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterAfter(licenseGateFilter, UsernamePasswordAuthenticationFilter.class)
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
                .securityContext(sc -> sc
                        .securityContextRepository(new HttpSessionSecurityContextRepository())
                )
                .sessionManagement(session -> session
                        .maximumSessions(-1) // no cap — just track sessions so they can be listed/revoked
                        .sessionRegistry(sessionRegistry())
                )
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
                                "/i18n/**",
                                "/whatsmine-logo.png",
                                "/whatsmine-icon.svg",
                                "/*.png",
                                "/*.svg",
                                "/*.ico",
                                "/api/ziggy.js",
                                "/favicon.ico",
                                "/webhooks/**",
                                "/widgets/**",
                                "/license",
                                "/license/**",
                                "/pages/**",
                                "/p/**",
                                "/pricing",
                                "/faq",
                                "/use-cases",
                                "/integrations",
                                "/about",
                                "/contact",
                                "/actuator/health"
                        ).permitAll()
                        .requestMatchers("/app/**", "/client/**", "/dashboard", "/dashboard/**", "/contacts/**", "/inbox/**", "/profile/**", "/billing/**", "/social/**", "/whatsapp/**", "/broadcasting/**", "/ecommerce/**", "/reports/exports/**").hasRole("CLIENT")
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
