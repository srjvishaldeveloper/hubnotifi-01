# Phase 17.5 — Local Runtime & PHP Dependency Audit Report

## 1. Project Structure Inventory

* **React Frontend**: Located in `php/resources/js` (built via `php/package.json` and `php/vite.config.js`).
* **Java / Spring Boot Backend**: Located in `java-backend/` (built and executed via Gradle 8.9 / Java 21).
* **Database**: MySQL / PostgreSQL (Production) / H2 (Test Baseline).
* **Legacy PHP Backend Source**: Retained in `php/` as a static code reference until Phase 18 cutover.

---

## 2. Comprehensive PHP / Laravel Dependency Scan & Classification

| Dependency / Keyword | Category | Context / Findings | Status / Resolution |
| -------------------- | -------- | ------------------ | ------------------- |
| `php` process | Reference / Dead | Legacy PHP codebase in `php/` folder | **NOT REQUIRED**. Java 21 Spring Boot handles 100% of HTTP API requests. |
| `artisan` / `php artisan` | Documentation / UI Display | Hardcoded display string in `AdminCronSetupController` for admin UI instructions | **NOT REQUIRED**. Java `@Scheduled` handles 100% of cron execution. |
| `composer` / `composer.json` | Reference / Dead | Retained `php/composer.json` file for reference | **NOT REQUIRED**. `build.gradle` manages all backend dependencies. |
| `Laravel routes / controllers` | Reference / Dead | Retained PHP source files in `php/app/Http/Controllers` | **REPLACED**. Spring Boot `@RestController` annotations handle all endpoints. |
| `Laravel queues` | Reference / Dead | Legacy `jobs` table polling | **REPLACED**. `QueueWorker.java` & `QueueDispatcher.java` handle all background queues. |
| `Laravel scheduler` | Reference / Dead | Legacy `php artisan schedule:run` | **REPLACED**. `SystemSchedulerTasks.java` handles all cron frequencies. |
| `Reverb` / Pusher Server | Reference / Dead | Legacy Laravel Reverb WebSocket server | **REPLACED**. `PusherWebSocketHandler.java` on `/app/{appKey}` handles all WebSockets. |
| `Laravel Storage / Disks` | Reference / Dead | Legacy `storage/app/public` disk paths | **REPLACED**. Java `LocalStorageService` / `StorageProperties` handle file uploads. |
| `laravel-echo` / `pusher-js` | Frontend Consumer | Client libraries in `php/package.json` | **ACTIVE**. Consumed by React to connect to Java WebSocket without modification. |
| `laravel-vite-plugin` | Development-Only Build Tool | Plugin in `php/vite.config.js` | **ACTIVE**. Used by Vite to resolve React entrypoint `app.jsx`. |

---

## 3. Runtime Dependency Result

```text
PHP Runtime Required: NO (0%)
Laravel Required: NO (0%)
Artisan Required: NO (0%)
Reverb Server Required: NO (0%)
```

All application features (HTTP routing, authentication, Inertia rendering, queue workers, cron scheduler, WebSocket broadcasting) execute natively within the Java 21 Spring Boot runtime.
