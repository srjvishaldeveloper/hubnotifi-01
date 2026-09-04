# Local Runtime Guide — Spring Boot + React (PHP-Off)

This guide documents how to run the full migrated **WhatsMine Application** locally **WITHOUT PHP, Laravel, Artisan, or Reverb**.

---

## 1. Process & Port Architecture

```text
Browser (User)
    │
    ├─────► React Frontend (Vite Dev Server)
    │       URL: http://localhost:5173
    │
    └─────► Java 21 Spring Boot Backend
            URL: http://localhost:8080
            │
            ├─► Inertia SSR / HTTP Controllers
            ├─► Session Auth & CSRF Guard
            ├─► Embedded Queue Worker (`QueueWorker`)
            ├─► Embedded Cron Scheduler (`SystemSchedulerTasks`)
            ├─► Embedded Pusher WebSocket (`PusherWebSocketHandler`)
            │   WS URL: ws://localhost:8080/app/{appKey}
            │   Auth URL: http://localhost:8080/broadcasting/auth
            │
            └─► Database (MySQL / PostgreSQL / H2)
```

---

## 2. Environment Variables & Setup

### Java Backend (`java-backend`)
- `SPRING_PROFILES_ACTIVE`: `dev`
- `PORT`: `8080`
- `APP_URL`: `http://localhost:8080`
- `DB_HOST`: `127.0.0.1` (or local DB container)
- `DB_PORT`: `3306`
- `DB_DATABASE`: `whatsmine`
- `DB_USERNAME`: `root`
- `DB_PASSWORD`: `""`

### React Frontend (`php`)
- `VITE_APP_NAME`: `WhatsMine`
- `VITE_PUSHER_APP_KEY`: `whatsmine-key`
- `VITE_PUSHER_HOST`: `127.0.0.1`
- `VITE_PUSHER_PORT`: `8080`
- `VITE_PUSHER_SCHEME`: `http`

---

## 3. Starting the Application (Without PHP)

### Terminal 1: Java Backend Server & Worker Engine
```powershell
cd java-backend
$env:JAVA_TOOL_OPTIONS=""
& "..\gradle-dist\bin\gradle.bat" bootRun --args='--spring.profiles.active=dev'
```
*(Or if using standard Gradle wrapper / system installation: `./gradlew bootRun`)*

### Terminal 2: React Frontend Development Server
```powershell
cd php
npm run dev
```

---

## 4. Operational Verification

1. **Web App Interface**: Open `http://localhost:5173` or `http://localhost:8080` in your browser.
2. **Login**: Enter user credentials (`admin@example.com` / `password`).
3. **Queue Processing**: Background tasks (campaigns, automation, email, media) are automatically polled and processed by `QueueWorker.java` within the Java backend process. No `php artisan queue:work` required.
4. **Cron Scheduler**: Scheduled tasks (campaign execution, trial expiration, reset meters) are automatically invoked by `SystemSchedulerTasks.java`. No `php artisan schedule:run` required.
5. **Realtime Broadcasting**: React connects to `ws://localhost:8080/app/{appKey}` via `laravel-echo` and `pusher-js`. Channel auth is validated at `http://localhost:8080/broadcasting/auth`. No Laravel Reverb server required.

---

## 5. Developer Convenience Script

A PowerShell runner script is provided at `scripts/start-local.ps1` for launching the full PHP-free local runtime.
