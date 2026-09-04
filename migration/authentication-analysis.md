# Authentication, Sessions & Security Analysis

## 1. Current PHP Authentication Architecture

The application uses **Laravel Breeze / Sanctum** architecture with multi-guard authentication:

```
                                +---------------------------+
                                |      HTTP REQUEST         |
                                +-------------+-------------+
                                              |
                                              v
                              +-------------------------------+
                              |    Spring Security Filter     |
                              +---------------+---------------+
                                              |
                     +------------------------+------------------------+
                     |                        |                        |
                     v                        v                        v
             Web Client Guard           Admin Panel Guard       API Bearer Guard
          (Session Cookie)          (Session Cookie)         (Sanctum Bearer)
                     |                        |                        |
                     v                        v                        v
             `users` Table           `admin_users` Table    `personal_access_tokens`
```

### A. Web Client Guard (`web`)
- **Authenticates**: Client portal users (`App\Models\User`).
- **Session Driver**: `database` (or `redis` / `file`). Stores session record in `sessions` table.
- **Cookies**: `laravel_session` (Encrypted session ID) & `XSRF-TOKEN` (Plaintext CSRF token).
- **Password Hashing**: Bcrypt with round cost 12.

### B. Admin Panel Guard (`admin`)
- **Authenticates**: System Administrators (`App\Models\AdminUser`).
- **Route Namespace**: Controlled by `routes/admin.php` protected by `auth:admin` middleware.
- **Permissions**: Checked via `AdminUser::permissionKeys()` matching role permissions stored in `admin_role` and `role_permission`.

### C. API Bearer Guard (`sanctum`)
- **Authenticates**: External API integration clients.
- **Header**: `Authorization: Bearer <token>`.
- **Database Table**: `personal_access_tokens` storing token hash, abilities, and expiration.

---

## 2. Special Security Features

### A. Multi-Tenant Workspace Session Context
- Upon login, the active workspace ID is stored in the session (`current_workspace_id`).
- When switching workspaces, `POST /app/workspaces/{id}/switch` updates `session(['current_workspace_id' => $id])`.
- `HandleInertiaRequests` reads this session key to inject `currentWorkspace` into React props.

### B. Admin Impersonation Mechanism
- Admin clicks "Impersonate" on a client in the Admin Panel (`POST /admin/impersonate/{client_id}`).
- Server sets session flag `session(['impersonating' => true, 'impersonated_client_id' => $clientId])` and logs in as the client's primary user.
- React receives `impersonation: { active: true, clientName: '...', returnUrl: '/admin/impersonate/stop' }` in `usePage().props` to render the floating yellow impersonation banner.
- Stopping impersonation (`POST /admin/impersonate/stop`) clears session flags and restores the original Admin session.

### C. CSRF Token Synchronization Flow
- Modern SPAs face a challenge when session tokens rotate (e.g. during login, impersonation, or 2FA).
- `HandleInertiaRequests` injects `csrf_token` on **EVERY** Inertia response.
- `resources/js/app.jsx` listens to `router.on('success')` and executes `syncCsrfToken()`:
  1. Updates `window.axios.defaults.headers.common['X-CSRF-TOKEN'] = token`.
  2. Updates `<meta name="csrf-token" content="...">` tag in DOM.
- This prevents `419 Page Expired` status errors without forcing full page reloads.

### D. Two-Factor Authentication (2FA)
- Implemented via `pragmarx/google2fa`.
- Users generate TOTP secret, scan QR code, and verify 6-digit code.
- Backup codes are hashed and stored in database.

### E. Social OAuth Authentication (Socialite)
- OAuth2 login flow with Google and Microsoft.
- Redirects to provider (`/auth/google/redirect`) and handles callback (`/auth/google/callback`).

---

## 3. Java Spring Security Migration Architecture

To replicate this exact authentication behavior in Java:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 1. Admin Security Filter Chain
    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/admin/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/login").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
            )
            .formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/login")
                .defaultSuccessUrl("/admin/dashboard", true)
            )
            .logout(logout -> logout
                .logoutUrl("/admin/logout")
                .logoutSuccessUrl("/admin/login")
            );
        return http.build();
    }

    // 2. Sanctum REST API Bearer Filter Chain
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v1/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new PersonalAccessTokenFilter(tokenRepository), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // 3. Web Client Security Filter Chain
    @Bean
    @Order(3)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/register", "/auth/**", "/public/**", "/install/**").permitAll()
                .requestMatchers("/app/**").authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/app/dashboard", true)
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            );
        return http.build();
    }
}
```

### Key Spring Security Components to Implement:
1. **Bcrypt Password Encoder**: Use `PasswordEncoderFactories.createDelegatingPasswordEncoder()` matching cost 12.
2. **Session Security**: Use Spring Session (`spring-session-jdbc` or `spring-session-data-redis`) targeting the exact same session storage.
3. **Cookie Names**: Configure `server.servlet.session.cookie.name=JSESSIONID` (or custom `laravel_session` alias) and `XSRF-TOKEN` cookie repository.
4. **Custom Impersonation Filter**: Implement `SwitchUserFilter` to handle Admin client impersonation.
5. **Sanctum Bearer Token Filter**: Custom Spring Security filter that extracts `Bearer <token>` from HTTP Header, hashes with SHA-256, looks up in `personal_access_tokens` table, and sets `SecurityContext`.
6. **2FA Integration**: Use `aerogear-otp-java` library for Google Authenticator TOTP verification.
