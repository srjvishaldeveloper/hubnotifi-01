# FRONTEND MIGRATION PHASE 1 — AUDIT REPORT

## A. Current Architecture

- **Laravel**: 11.x (Source retained under `php/` for reference only; **0 runtime processes active**)
- **React**: 19.2.4 (Located in `php/resources/js/`)
- **Inertia**: `@inertiajs/react` 2.3.16
- **Vite**: 6.0.11 + `@vitejs/plugin-react` 5.1.4
- **Java**: Java 21 + Spring Boot 3.4.3 (Located in `java-backend/`)
- **Database**: H2 / PostgreSQL (Schema unchanged)
- **Realtime**: Laravel Echo 2.3.4 + Pusher JS 8.5.0 + Spring Boot WebSocket Server (`/app/**`)

---

## B. React Entry Point

- **Exact file**: `php/resources/js/app.jsx`

---

## C. Vite Entry Point

- **Exact file**: `php/vite.config.js` (pointing to `resources/js/app.jsx`)

---

## D. Laravel Vite Dependency

- **Package**: `laravel-vite-plugin`
- **Version**: `^1.2.0`
- **Used where**: `php/vite.config.js` (`plugins: [laravel(...)]`) and `php/resources/js/app.jsx` (`import { resolvePageComponent } from 'laravel-vite-plugin/inertia-helpers'`)
- **Purpose**: HMR `public/hot` file generation and Inertia dynamic page resolution
- **Runtime/build/dev**: BUILD-TIME / DEV-TIME (Not required during production runtime by Spring Boot)

---

## E. Current Asset Flow

```text
Browser
 │
 ▼
Spring Boot (http://localhost:8080)
 │
 ▼
InertiaRenderer (Reads php/public/hot)
 │
 ▼
HTML (<script src="http://127.0.0.1:5174/resources/js/app.jsx">)
 │
 ▼
Vite Dev Server (http://127.0.0.1:5174)
 │
 ▼
React (Browser)
```

---

## F. Current Java Asset Flow

```text
Spring Boot (InertiaRenderer)
 │
 ▼
Checks for presence of php/public/hot
 │
 ├── IF hot EXISTS ──► Outputs Vite HMR script tags pointing to http://127.0.0.1:5174
 └── IF hot MISSING ─► Outputs static asset tags pointing to /build/assets/app-*.js
```

---

## G. PHP Dependencies

- **Runtime**: **NONE** (0 PHP processes required or running)
- **Build**: **NONE** (Node.js/Vite handles build without PHP)
- **Development**: **NONE** (Spring Boot + Node.js/Vite dev server run independently)
- **Documentation**: `php/app/`, `php/routes/`, `php/composer.json` (Retained purely for reference)

---

## H. Laravel Dependencies

- **Runtime**: `laravel-echo` (Client library connecting to Java WebSocket), `@inertiajs/react` (Inertia client)
- **Build**: `laravel-vite-plugin` (Used in `vite.config.js` and `app.jsx`)
- **Development**: `public/hot` file indicator
- **Documentation**: Laravel route files and Eloquent model definitions

---

## I. Target Architecture

```text
Browser
 │
 ▼
Spring Boot (Java 21)
 │
 ▼
Inertia HTML Bootstrap
 │
 ▼
React Compiled Production Assets (/build/assets/app-*.js served directly by Spring Boot)
```

---

## J. Required Next Phases

1. **Phase 2 — Frontend Build & Asset Integration**
   - Configure npm production build pipeline (`npm run build`) to output directly into Spring Boot static resources (`java-backend/src/main/resources/static/build`).
2. **Phase 3 — Standalone React / Vite Configuration Decoupling**
   - Decouple Inertia page resolver in `app.jsx` from `laravel-vite-plugin/inertia-helpers` to standard Vite `import.meta.glob`.
3. **Phase 4 — Production Build Static Serving Verification**
   - Verify Spring Boot serving pre-compiled React bundles with `public/hot` removed, ensuring 0 Node/Vite processes needed at runtime.
4. **Phase 5 — Full Standalone Java + React End-to-End Verification**
   - Full regression test suite run without Vite dev server or PHP running.

---

========================================
FRONTEND MIGRATION PHASE 1
ARCHITECTURE AUDIT
========================================

React source changed: 0
Java source changed: 0
Database changed: 0

React architecture: AUDITED
Inertia architecture: AUDITED
Vite architecture: AUDITED
Laravel dependencies: AUDITED
PHP dependencies: AUDITED
Ziggy: AUDITED
Axios: AUDITED
Echo/Pusher: AUDITED
Environment variables: AUDITED
Assets: AUDITED
Production build: AUDITED
Spring Boot asset integration: AUDITED

Implementation changes: 0

Phase 1 Status: COMPLETE

Next Phase:
Phase 2 — Frontend Build & Asset Integration Pipeline
========================================
