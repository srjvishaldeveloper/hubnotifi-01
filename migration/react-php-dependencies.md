# React to PHP Dependencies & Java Replacement Analysis

## 1. Summary of React-PHP Touchpoints

The React frontend interacts with PHP through **5 primary mechanisms**:
1. **Inertia.js Page Props & Visits** (`@inertiajs/react` package)
2. **Ziggy Route Helper** (`tightenco/ziggy` via `route()`)
3. **Axios Raw HTTP Requests** (`window.axios` with `X-CSRF-TOKEN`)
4. **Real-time WebSockets** (`laravel-echo` + `pusher-js` connected to Laravel Reverb/Pusher)
5. **Global Shared State & Media Assets** (HTML `<meta>` tags, global JS variables, static storage URLs)

---

## 2. Exhaustive Dependency Catalog & Java Replacements

### Dependency 1: Inertia.js Protocol (`@inertiajs/react`)

* **How React Uses It**:
  - `createInertiaApp()` initializes the SPA in `resources/js/app.jsx`.
  - `<Link href={route('...')} />` and `router.visit()`, `router.post()`, `router.put()`, `router.delete()` navigate between pages.
  - `usePage().props` accesses data passed from the backend (`auth`, `flash`, `csrf_token`, `currentWorkspace`, `i18n`, `branding`, etc.).
  - `useForm()` handles client-side form submissions with automatic validation error binding and processing states.

* **Current PHP Implementation**:
  - `Inertia\Middleware` (`HandleInertiaRequests.php`) intercepts HTTP requests.
  - `Inertia::render('PageName', $data)` returns Inertia HTTP responses.
  - Returns HTML template with `<div id="app" data-page="...">` on first visit, or JSON `{ component, props, url, version }` with `X-Inertia: true` header on AJAX visits.

* **Java Replacement Architecture**:
  - **Option A (Recommended)**: Use `inertia-spring-boot` library or implement a light **Spring MVC `HandlerInterceptor` & `ResponseBodyAdvice`**.
  - **Inertia Controller in Spring Boot**:
    ```java
    @GetMapping("/app/dashboard")
    public Object dashboard(HttpServletRequest request, Model model) {
        Map<String, Object> props = Map.of("stats", dashboardService.getStats());
        return Inertia.render("Client/Dashboard", props);
    }
    ```
  - **Inertia Interceptor / Handler**:
    - Detects `X-Inertia: true` header.
    - If present, sets response content type to `application/json`, header `X-Inertia: true`, and outputs `{ "component": name, "props": combinedProps, "url": req.getRequestURI(), "version": assetVersion }`.
    - If absent (first page load), renders `index.html` (Thymeleaf or raw HTML) embedding the JSON payload into `<div id="app" data-page='...'></div>`.
  - **Result for React**: **ZERO CHANGES to React code**. `<Link>`, `router`, `usePage()`, `useForm()` continue working seamlessly.

---

### Dependency 2: Ziggy Route Helper (`route('name', params)`)

* **How React Uses It**:
  - React components call `route('client.inbox.index')`, `route('admin.users.edit', { user: 5 })`, `route('api.v1.contacts.store')`.
  - Provides type-safe dynamic URL resolution directly inside JSX.

* **Current PHP Implementation**:
  - `@routes` blade directive outputs a window JS object `window.Ziggy = { url: "...", port: 8000, routes: { ... } }`.
  - `tightenco/ziggy` package formats all registered Laravel named routes into JSON.

* **Java Replacement Architecture**:
  - Create a Spring Boot endpoint `@GetMapping("/api/ziggy.js")` or `@GetMapping("/js/routes.json")` or inject `window.Ziggy` into the initial HTML template.
  - **Spring Route Extractor**:
    - Scans Spring MVC `@RequestMapping`, `@GetMapping`, `@PostMapping` annotations.
    - Exports a JSON object matching Ziggy's schema:
      ```json
      {
        "url": "https://your-domain.com",
        "port": null,
        "routes": {
          "admin.dashboard": { "uri": "admin/dashboard", "methods": ["GET"] },
          "client.inbox.show": { "uri": "app/inbox/{id}", "methods": ["GET"] }
        }
      }
      ```
  - **Result for React**: React continues to call `route('route.name')` without any modification.

---

### Dependency 3: Axios & Direct AJAX Requests

* **How React Uses It**:
  - Direct HTTP calls in `resources/js/push.js`, media upload components, async webhooks tester, etc.
  - Automatically attaches `X-CSRF-TOKEN` and `X-Requested-With: XMLHttpRequest`.

* **Current PHP Implementation**:
  - Standard Laravel HTTP API endpoints / web controller actions returning JSON responses.
  - Middleware validates CSRF token from header `X-CSRF-TOKEN` or cookie `XSRF-TOKEN`.

* **Java Replacement Architecture**:
  - Standard **Spring MVC `@RestController`** endpoints.
  - Configure **Spring Security CSRF Filter** to automatically output `XSRF-TOKEN` cookie (using `CookieCsrfTokenRepository.withHttpOnlyFalse()`).
  - Axios automatically reads `XSRF-TOKEN` cookie and attaches `X-XSRF-TOKEN` header on POST/PUT/DELETE requests.
  - **Result for React**: 100% compatible.

---

### Dependency 4: Real-time WebSockets (`Laravel Echo` + `Pusher-JS`)

* **How React Uses It**:
  - `resources/js/echo.js` initializes `window.Echo = new Echo({ broadcaster: 'reverb', key: '...', wsHost: '...' })`.
  - Subscribes to channels: `Echo.private('workspace.' + workspaceId)`.
  - Listens to events: `.listen('.MessageSent', (e) => { ... })`.

* **Current PHP Implementation**:
  - Laravel Broadcasting with Laravel Reverb or Pusher.
  - Channel authorization route: `POST /broadcasting/auth` (`routes/channels.php`).
  - Broadcast Events (`MessageSent`, `ConversationUpdated`).

* **Java Replacement Architecture**:
  - **Option A (Pusher-compatible WebSocket Server in Java)**: Use `java-pusher-server` or implement a lightweight Spring WebSocket endpoint mimicking the Pusher protocol on `/app/{app_key}` and auth on `/broadcasting/auth`.
  - **Option B (Managed Pusher/Sentry Cloud)**: Spring Boot uses `pusher-http-java` library to push events to hosted Pusher service, matching the key configured in React environment variables.
  - **Result for React**: React Echo client requires zero code changes.

---

### Dependency 5: Shared Global Props (`HandleInertiaRequests`)

* **How React Uses It**:
  - Components call `const { auth, flash, currentWorkspace, branding, i18n, pusher, onesignal, firebase } = usePage().props`.

* **Current PHP Implementation**:
  - `HandleInertiaRequests::share()` queries database and session on every request to compose this dictionary.

* **Java Replacement Architecture**:
  - Implement a Spring MVC `GlobalModelAttributes` component or Inertia `GlobalPropsProvider`.
  - On every request, populate the exact same JSON key hierarchy:
    ```json
    {
      "csrf_token": "...",
      "flash": { "success": null, "error": null },
      "auth": {
        "user": { "id": 1, "name": "John", "email": "john@example.com", ... },
        "adminUser": null,
        "permissions": [...]
      },
      "currentWorkspace": { "id": 10, "name": "Default Workspace" },
      "workspaces": [...],
      "locale": "en",
      "dir": "ltr",
      "i18n": { "locale": "en", "isRtl": false, "translations": { ... } },
      "branding": { "app_name": "WhatsMine", "primary_color": "#467235", ... },
      "pusher": { "key": "...", "cluster": "mt1", "enabled": true },
      "onesignal": { "app_id": "...", "enabled": true },
      "firebase": { "enabled": false }
    }
    ```
  - **Result for React**: 100% compatible.

---

### Dependency 6: Static Assets & Media URLs

* **How React Uses It**:
  - React renders images with src like `/storage/avatars/user1.png` or `branding.logo_url` or `/whatsmine-icon.svg`.

* **Current PHP Implementation**:
  - `Storage::disk('public')->url(...)` or `asset(...)`.

* **Java Replacement Architecture**:
  - Configure Spring MVC static resource handler:
    ```java
    @Configuration
    public class WebConfig implements WebMvcConfigurer {
        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/storage/**")
                    .addResourceLocations("file:/var/app/storage/app/public/");
            registry.addResourceHandler("/**")
                    .addResourceLocations("classpath:/static/");
        }
    }
    ```
  - **Result for React**: Asset paths resolve identically.

---

## 3. Summary of Compatibility Guarantee

By replicating:
1. **Inertia JSON Response Format & Headers**
2. **Ziggy Route JSON Format**
3. **Shared Prop Structure in `usePage().props`**
4. **WebSocket Channel Auth Endpoint (`/broadcasting/auth`)**
5. **CSRF Cookie Name (`XSRF-TOKEN`)**

**The React frontend will function 100% identically with ZERO changes to React source code.**
