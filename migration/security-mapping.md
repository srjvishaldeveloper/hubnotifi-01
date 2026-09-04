# Security Mapping Document (Laravel 12 → Spring Security 6)

## 1. Authentication Guards Mapping

| Laravel Guard | Authentication Source / Entity | Storage Mechanism | Spring Security Mechanism |
|---|---|---|---|
| `web` | `users` table (`User` entity) | HTTP Session | `DaoAuthenticationProvider` + Session Cookie (`JSESSIONID`) |
| `admin` | `admin_users` table (`AdminUser` entity) | HTTP Session | Separate `DaoAuthenticationProvider` + Admin Session Filter Chain |
| `sanctum` | `personal_access_tokens` table (`PersonalAccessToken` entity) | Bearer Token | `SanctumAuthenticationFilter` + Header `Authorization: Bearer <token>` |

---

## 2. Route Security Mapping

| Route Pattern | Laravel Middleware | Spring Security Rule | Purpose |
|---|---|---|---|
| `/login`, `/register`, `/forgot-password`, `/reset-password/*` | `guest` | `permitAll()` | Public client auth pages & submissions |
| `/admin/login` | `guest:admin` | `permitAll()` | Public admin login page & submissions |
| `/app/**`, `/client/**` | `auth:web` | `hasAuthority('ROLE_USER')` | Protected client application |
| `/admin/**` | `auth:admin` | `hasAuthority('ROLE_ADMIN')` | Protected admin management console |
| `/api/**` | `auth:sanctum` | `authenticated()` (Bearer Token) | Protected REST API |
| `/api/v1/health`, `/actuator/health` | None | `permitAll()` | System health checks |

---

## 3. Auth Endpoints Mapping

| Action | Laravel Route | Java Route | Laravel Controller | Java Controller / Security Bean |
|---|---|---|---|---|
| Client Login Page | `GET /login` | `GET /login` | `AuthenticatedSessionController@create` | `AuthController@showLogin` |
| Client Login Submission | `POST /login` | `POST /login` | `AuthenticatedSessionController@store` | `SecurityConfig` / `AuthService` |
| Client Logout Submission | `POST /logout` | `POST /logout` | `AuthenticatedSessionController@destroy` | `SecurityConfig` / `AuthService` |
| Client Registration Page | `GET /register` | `GET /register` | `RegisteredUserController@create` | `AuthController@showRegister` |
| Client Registration | `POST /register` | `POST /register` | `RegisteredUserController@store` | `AuthController@register` |
| Admin Login Page | `GET /admin/login` | `GET /admin/login` | `AdminUserController` / Admin Auth | `AdminAuthController@showLogin` |
| Admin Login Submission | `POST /admin/login` | `POST /admin/login` | Admin Auth | `AdminSecurityConfig` / `AdminAuthService` |
| Admin Logout | `POST /admin/logout` | `POST /admin/logout` | Admin Auth | `AdminSecurityConfig` / `AdminAuthService` |
| 2FA Challenge Page | `GET /two-factor-challenge` | `GET /two-factor-challenge` | `TwoFactorController@challenge` | `TwoFactorController@challenge` |
| 2FA Verify Submission | `POST /two-factor-challenge` | `POST /two-factor-challenge` | `TwoFactorController@verify` | `TwoFactorController@verify` |
| OAuth Redirect | `GET /auth/{provider}/redirect` | `GET /auth/{provider}/redirect` | `SocialLoginController@redirect` | `OAuth2Controller@redirect` |
| OAuth Callback | `GET /auth/{provider}/callback` | `GET /auth/{provider}/callback` | `SocialLoginController@callback` | `OAuth2Controller@callback` |

---

## 4. Password Hashing Compatibility

- **Laravel Engine**: BCrypt (via `Illuminate\Support\Facades\Hash`).
- **Spring Engine**: `org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12)`.
- **Compatibility Note**: Spring Security's `BCryptPasswordEncoder` natively parses `$2y$` and `$2a$` hash prefixes produced by Laravel. Existing passwords in `users` and `admin_users` require zero rehashing or modification.
