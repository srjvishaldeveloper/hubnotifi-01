# Frontend Phase 1 — Runtime Flow Analysis

## 1. Current Development Runtime Flow (Vite HMR Mode)

```text
Browser
   │
   ├─► GET http://localhost:8080/login
   │
Spring Boot (Java 21)
   │
   ├─► InertiaRenderer.java
   │   ├── Reads `php/public/hot` (contains `http://127.0.0.1:5174`)
   │   ├── Generates HTML bootstrap with:
   │   │   ├── <script src="/api/ziggy.js"> (Served by Spring Boot)
   │   │   ├── <script type="module" src="http://127.0.0.1:5174/@react-refresh">
   │   │   ├── <script type="module" src="http://127.0.0.1:5174/@vite/client">
   │   │   └── <script type="module" src="http://127.0.0.1:5174/resources/js/app.jsx">
   │   └── Injects data-page Inertia JSON prop payload
   │
   └─► Serves HTML to Browser
           │
           ├─► Browser fetches /api/ziggy.js from Spring Boot (Defines window.route)
           ├─► Browser fetches app.jsx & modules from Vite Dev Server (http://127.0.0.1:5174)
           └─► React renders Inertia Page (Auth/Login)
```

---

## 2. Target Production Runtime Flow (Spring Boot Standalone)

```text
Browser
   │
   ├─► GET http://localhost:8080/login (or Production Domain)
   │
Spring Boot (Java 21)
   │
   ├─► InertiaRenderer.java
   │   ├── Detects NO `public/hot` file
   │   ├── Reads production manifest (`build/manifest.json`)
   │   ├── Generates HTML bootstrap with:
   │   │   ├── <script src="/api/ziggy.js"> (Served by Spring Boot)
   │   │   ├── <link rel="stylesheet" href="/build/assets/app-[hash].css">
   │   │   └── <script type="module" src="/build/assets/app-[hash].js">
   │   └── Injects data-page Inertia JSON prop payload
   │
   └─► Serves HTML to Browser
           │
           ├─► Browser fetches static assets (/build/assets/*) directly from Spring Boot
           ├─► Browser fetches /api/ziggy.js from Spring Boot
           └─► React renders Inertia Page natively without any external asset server
```

---

## 3. Key Differences: Development vs Production

| Feature | Development (HMR Mode) | Production Target Mode |
| :--- | :--- | :--- |
| **Asset Server** | Vite Dev Server (`http://127.0.0.1:5174`) | Spring Boot Embedded Server (`http://localhost:8080`) |
| **PHP / Laravel Requirement** | **NONE** (0 PHP processes) | **NONE** (0 PHP processes) |
| **Vite Process Requirement** | Active background `vite` process for HMR | **NONE** (Pre-compiled bundle only) |
| **Asset Source** | Dynamic JS module imports from Vite | Compiled, minified static `.js` & `.css` files |
| **Ziggy Route Helper** | Spring Boot `/api/ziggy.js` | Spring Boot `/api/ziggy.js` |
