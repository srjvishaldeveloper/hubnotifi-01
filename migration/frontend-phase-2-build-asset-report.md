# FRONTEND MIGRATION PHASE 2 REPORT — BUILD & ASSET INTEGRATION PIPELINE

## 1. Executive Summary

Phase 2 establishes the production build and static asset integration pipeline.
The compiled React frontend is built via Vite (`npm run build`) directly into the Spring Boot static resource directory (`java-backend/src/main/resources/static/build/`).
Spring Boot serves Inertia HTML, dynamic Ziggy routes (`/api/ziggy.js`), and pre-compiled static bundles (`/build/assets/*.js`, `/build/assets/*.css`) natively on port `8080`.

- **PHP Process**: **0 (OFF)**
- **Laravel Server Process**: **0 (OFF)**
- **Vite Dev Server Process (Production)**: **0 (OFF)**
- **Standalone Spring Boot Process**: **1 (ON - Port 8080)**

---

## 2. Build Pipeline Configuration

- **npm Build Command**: `npm run build` (executed in `php/`)
- **Vite Input**: `resources/js/app.jsx`
- **Vite Output Directory**: `../java-backend/src/main/resources/static/build`
- **Manifest Location**: `java-backend/src/main/resources/static/build/manifest.json`
- **Compiled Assets**:
  - `java-backend/src/main/resources/static/build/assets/app-*.js`
  - `java-backend/src/main/resources/static/build/assets/app-*.css`

---

## 3. Spring Boot Static Asset Serving

- **Resource Handler**: `WebConfig.java` registers `/build/**` mapping to `classpath:/static/build/` and `file:src/main/resources/static/build/`.
- **Manifest Resolution**: `InertiaRenderer.java` parses `manifest.json` dynamically when `public/hot` is absent, automatically resolving target hashes.
- **Security Permissions**: `SecurityConfig.java` permits `/build/**`, `/images/**`, `/whatsmine-icon.svg`, and `/api/ziggy.js`.

---

## 4. Local Runtime Architectures

### Development Mode (HMR)
```text
Browser -> Spring Boot (http://localhost:8080) -> Reads public/hot -> Vite Dev Server (http://127.0.0.1:5174)
```

### Production-Like Standalone Mode
```text
Browser -> Spring Boot (http://localhost:8080) -> Reads manifest.json -> /build/assets/*.js & *.css (Spring Boot)
```

---

## 5. Verification Results

| Verification Check | Status | Note |
| :--- | :--- | :--- |
| `npm run build` compilation | **PASS** | Successfully built into `static/build/` |
| `manifest.json` generation | **PASS** | Valid manifest mapping `resources/js/app.jsx` |
| Spring Boot `/build/assets/*.js` serving | **PASS** | HTTP 200 OK served by Spring Boot |
| Spring Boot `/build/assets/*.css` serving | **PASS** | HTTP 200 OK served by Spring Boot |
| Inertia HTML bootstrap output | **PASS** | Clean HTML referring ONLY to `localhost:8080` assets |
| `5173 / 5174 / 5175` dev server calls | **NONE** | 0 requests to Vite dev ports in production mode |
| PHP / Laravel dependency | **NONE** | 0 PHP processes running |
| React source code changes | **0** | React source and business logic 100% untouched |
| Database schema changes | **0** | Database schema 100% untouched |
