# Render Deployment & Infrastructure Analysis

## 1. Current Render Environment Analysis

The application is currently deployed on **Render** (render.com) as a single containerized web service / worker setup.

### A. Current Process Architecture (`docker/supervisor/whatsmine.conf`)
The production container runs **Supervisor** (`supervisord`) to manage 3 distinct processes simultaneously inside the container:

```ini
[supervisord]
nodaemon=true
logfile=/dev/stdout

[program:web]
command=php artisan serve --host=0.0.0.0 --port=8080
autostart=true
autorestart=true

[program:queue-worker]
command=php artisan queue:work --tries=3 --timeout=90 --max-jobs=1000
autostart=true
autorestart=true

[program:scheduler]
command=bash -c "while [ true ]; do php artisan schedule:run --no-interaction; sleep 60; done"
autostart=true
autorestart=true
```

---

## 2. Build & Runtime Specifications

### Build Command:
```bash
composer install --no-dev --optimize-autoloader && npm ci && npm run build
```
1. Installs PHP composer dependencies.
2. Installs npm packages.
3. Compiles Vite React frontend (`npm run build`) producing production JS/CSS assets into `public/build/`.

### Start Command:
```bash
php artisan migrate --force && supervisord -c docker/supervisor/whatsmine.conf
```
1. Executes pending database migrations.
2. Starts Supervisor daemon which boots web server, queue worker, and scheduler loop.

---

## 3. Environment Variables Catalog

| Environment Variable | Description / Purpose | Recommended Java Equivalent |
|---|---|---|
| `APP_NAME` | Application display name | `spring.application.name` / `app.name` |
| `APP_ENV` | Environment (`production`, `local`) | `spring.profiles.active` (`prod`, `dev`) |
| `APP_KEY` | AES encryption key for sessions | `app.security.encryption-key` |
| `APP_URL` | Base public URL | `app.url` |
| `APP_DEMO_MODE` | Read-only demo mode toggle | `app.demo-mode` |
| `APP_INSTALLED` | Web setup wizard completion flag | `app.installed` |
| `DB_CONNECTION` | RDBMS driver (`mysql`, `postgresql`) | `spring.datasource.url` |
| `DB_HOST`, `DB_PORT`, `DB_DATABASE` | Database connection host & credentials | JDBC connection string |
| `DB_USERNAME`, `DB_PASSWORD` | DB user & password | `spring.datasource.username/password` |
| `SESSION_DRIVER` | Session store (`database`, `redis`) | `spring.session.store-type` |
| `QUEUE_CONNECTION` | Queue store (`database`, `redis`) | Internal Spring Executor / RabbitMQ / Redis |
| `BROADCAST_CONNECTION` | Realtime driver (`reverb`, `pusher`) | `app.broadcasting.driver` |
| `STRIPE_SECRET`, `STRIPE_WEBHOOK_SECRET` | Stripe API credentials | `stripe.api-key`, `stripe.webhook-secret` |
| `OPENAI_API_KEY`, `AI_PROVIDER` | AI Chatbot LLM API key | `spring.ai.openai.api-key` |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_BUCKET` | S3 Media Storage | `cloud.aws.credentials.*`, `cloud.aws.s3.bucket` |

---

## 4. Java Deployment Strategy on Render

### Target Container Architecture (Single Jar Docker Deployment)

```
+-------------------------------------------------------------------------------+
|                            RENDER DOCKER CONTAINER                            |
|                                                                               |
|   +-----------------------------------------------------------------------+   |
|   |                       Spring Boot Standalone Jar                      |   |
|   |                                                                       |   |
|   |   - Embedded Tomcat / Jetty Web Server (Port 8080)                     |   |
|   |   - Embedded React Static Assets (Built Vite JS/CSS in static/)        |   |
|   |   - Spring `@Scheduled` Tasks (Replacing Artisan Scheduler)           |   |
|   |   - Spring `@Async` Thread Pool / Quartz (Replacing Artisan Worker)   |   |
|   +-----------------------------------------------------------------------+   |
+-------------------------------------------------------------------------------+
```

### Multi-Stage Dockerfile for Java Deployment:
```dockerfile
# Stage 1: Build React Frontend with Node.js
FROM node:20-alpine AS frontend-builder
WORKDIR /app
COPY php/package*.json php/vite.config.js ./
RUN npm ci
COPY php/resources ./resources
RUN npm run build

# Stage 2: Build Spring Boot Application with Maven / Gradle
FROM eclipse-temurin:21-jdk-alpine AS backend-builder
WORKDIR /app
COPY java-backend/ ./
COPY --from=frontend-builder /app/public/build src/main/resources/static/build
RUN ./gradlew bootJar --no-daemon

# Stage 3: Runtime Container
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend-builder /app/build/libs/whatsmine-java.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Key Render Deployment Advantages with Java:
1. **Simplified Process Management**: No need for `supervisord`. Spring Boot manages web HTTP requests, scheduled cron tasks (`@Scheduled`), and background async queues (`ThreadPoolTaskExecutor`) natively within a single JVM process.
2. **Lower Memory Footprint**: One JVM instance running Spring Boot + embedded Tomcat requires significantly less RAM than running PHP-FPM/Artisan + multiple Queue Worker processes + Cron loops + Supervisor.
3. **Zero Frontend Rebuild on Host**: React assets are compiled during the multi-stage Docker build and packaged directly inside the Spring Boot JAR file (`src/main/resources/static/`).
