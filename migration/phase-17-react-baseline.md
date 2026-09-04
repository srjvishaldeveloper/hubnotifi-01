# Phase 17 — React Frontend Baseline

## Overview
This document establishes the official source-code and configuration baseline for the React frontend prior to Phase 17 End-to-End (E2E) regression testing.
**Mandatory Requirement**: React source modifications caused by Phase 17 = **0**.

## 1. Environment & Package Baseline
- **React Version**: ^18.2.0
- **Build Tool / Bundler**: Vite 5 (`vite.config.js`)
- **Package Manager**: `npm` (Lockfile: `package-lock.json`)
- **UI Framework / Rendering**: Inertia.js React Client (`@inertiajs/react` ^1.0.0)
- **Styling**: TailwindCSS 3 (`tailwind.config.js`, `postcss.config.js`)
- **HTTP Client**: Axios (`axios` ^1.6.0 with `withCredentials: true`, `X-Requested-With`, `X-CSRF-TOKEN` headers)
- **WebSocket / Realtime**: `laravel-echo` (^1.16.0) + `pusher-js` (^8.4.0)

## 2. Echo & Pusher Configuration (`resources/js/echo.js`)
```javascript
window.Echo = new Echo({
    broadcaster:       'pusher',
    key:               pusherConfig.key || import.meta.env.VITE_PUSHER_APP_KEY || '',
    cluster:           pusherConfig.cluster || import.meta.env.VITE_PUSHER_APP_CLUSTER || 'mt1',
    forceTLS:          true,
    disableStats:      true,
    enabledTransports: ['ws', 'wss'],
    authEndpoint:      '/broadcasting/auth',
    auth: {
        headers: {
            'X-CSRF-TOKEN': csrf,
            'X-Requested-With': 'XMLHttpRequest',
            'Accept': 'application/json',
        },
    },
    authTransport:     'ajax',
});
```

## 3. Frontend Execution Commands
- **Install Dependencies**: `npm install`
- **Frontend Development Server**: `npm run dev`
- **Production Asset Build**: `npm run build`
- **Linting**: `npx eslint resources/js`
- **Testing**: `npm test` / `vitest`

## 4. Inertia & Session Integration Contract
- **Root Element**: `<div id="app" data-page="{...}"></div>`
- **Shared Props Injected by Java Backend**:
  - `auth`: `{ user: { id, name, email, workspace_id, client_id, role, ... } }`
  - `current_workspace_usage`: `{ contacts_count, messages_this_month, ... }`
  - `unreadNotificationsCount`: `integer`
  - `pusher`: `{ key, cluster, enabled }`
  - `branding`: `{ logo_url, app_name }`
  - `errors`: `{ fieldName: "Error message" }`
  - `flash`: `{ success: "...", error: "..." }`

## 5. Source Integrity Verification
- **Baseline Git Hash / Diff Check**: Source files under `php/resources/js` must match original state with **0 diffs**.
- **Result**: `React source modifications = 0`.
