# Hub Notification

**Hub Notification** is a multi-tenant messaging and customer-engagement platform built on **Spring Boot**. It centralizes WhatsApp, SMS, email, and social-channel messaging into a single inbox, with campaign broadcasting, contact segmentation, automation, billing, and an admin console for platform operators.

[![Java Backend CI](https://github.com/srjvishaldeveloper/hubnotifi-01/actions/workflows/java-backend-ci.yml/badge.svg)](https://github.com/srjvishaldeveloper/hubnotifi-01/actions/workflows/java-backend-ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen)
![Build](https://img.shields.io/badge/Build-Gradle-blue)

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Security Model](#security-model)
- [API Surface](#api-surface)
- [Testing](#testing)
- [Deployment](#deployment)
- [Contributing](#contributing)

---

## Overview

Hub Notification gives businesses one place to manage every customer conversation and outbound campaign:

- A unified **inbox** across WhatsApp, social, and other channels
- **Broadcast campaigns** with scheduling, chunking, and delivery tracking
- **Contact segmentation** for targeted messaging
- **Automation** workflows triggered by customer events
- **Billing** via Stripe and Razorpay, with invoicing
- **E-commerce integrations** for order/customer sync
- **AI-assisted** knowledge-base ingestion and reply suggestions
- A full **admin console** for managing clients, integrations, and platform health
- **Audit logging** and **license management** for self-hosted/reseller deployments

The entire application — server-rendered pages, JSON APIs, WebSocket channels, and static assets — is served from a single Spring Boot process.

---

## Key Features

### Messaging & Inbox
- Unified inbox aggregating conversations across channels
- WhatsApp Business (Meta Graph API) integration — messaging, templates, webhooks
- Social account connections
- Real-time updates over WebSocket

### Campaigns & Broadcasting
- Scheduled and immediate broadcast campaigns
- Chunked, staggered send-out to respect provider rate limits
- Background job handlers for launching and finalizing campaigns
- Delivery/status polling with configurable retry limits

### Contacts & Segmentation
- Contact database with bulk import
- Dynamic segments for targeted campaigns
- Lead capture and management

### Automation & AI
- Event-driven automation workflows
- AI-assisted document ingestion (URL/HTML and PDF parsing) for knowledge bases
- AI provider integrations for smart replies

### Billing & Commerce
- Stripe and Razorpay payment gateway integrations
- Invoice generation and billing history
- E-commerce platform OAuth integrations for order sync

### Notifications & Communication Channels
- Email delivery (Spring Mail)
- SMS provider integrations
- In-app notification feed with an audit trail

### Platform Administration
- Dedicated admin security domain with role-based access (`/admin/**`)
- Client management, impersonation support for support workflows
- Outbound webhook management and delivery
- Cron/scheduled job configuration
- Audit log viewer
- Self-hosted license verification (opt-in, disabled by default)
- Provider/integration catalog with branded logos

### Internationalization
- 17 bundled locales (English, Arabic, Bengali, Chinese, German, Spanish, French, Hindi, Indonesian, Italian, Japanese, Korean, Portuguese, Russian, Turkish, and more)

### Operations
- Spring Boot Actuator health/metrics endpoints
- Structured logging via Logback
- Dockerized, multi-stage container build with a built-in health check

---

## Architecture

```
                       ┌─────────────────────────────────────────┐
                       │              Spring Boot App              │
                       │                                           │
   HTTP/HTTPS  ───────▶│  Security Filter Chains (admin/api/web)  │
                       │            │         │         │          │
                       │      Controllers  WebSocket   Webhooks    │
                       │            │                              │
                       │        Services  ─────────────┐           │
                       │      (billing, whatsapp, ai,   │           │
                       │   automation, segments, sms,   │           │
                       │        social, ecommerce)      │           │
                       │            │                   │           │
                       │      Repositories (JPA)   Background Jobs │
                       │            │              (queue handlers,│
                       │            │               schedulers)    │
                       └────────────┼───────────────────┼──────────┘
                                    ▼                   ▼
                              MySQL / H2         External APIs
                          (contacts, campaigns,  (WhatsApp/Meta,
                           billing, audit, etc.)  Stripe, Razorpay,
                                                   SMS, e-commerce)
```

Three independent security filter chains isolate concerns by URL prefix:

| Chain | Scope | Session model | Notes |
|---|---|---|---|
| Admin | `/admin/**` | Session-based, CSRF-protected | Role `ADMIN`, gated by an optional license filter |
| API | `/api/**` | Stateless | Token-based auth, CSRF disabled |
| Client web app | Everything else | Session-based, CSRF-protected | Role `CLIENT`, workspace-scoped access |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language / Runtime | Java 21 |
| Framework | Spring Boot 3.4.3 |
| Web | Spring Web MVC, Spring WebSocket |
| Security | Spring Security (session + stateless token auth) |
| Persistence | Spring Data JPA / Hibernate |
| Databases | MySQL (dev/prod), H2 (local/in-memory) |
| Validation | Spring Validation (Jakarta Bean Validation) |
| Monitoring | Spring Boot Actuator |
| Email | Spring Mail |
| Payments | Stripe Java SDK, Razorpay |
| Document Processing | Apache PDFBox, jsoup |
| JSON | Jackson (with JSR-310 and Hibernate6 modules) |
| Build Tool | Gradle (via wrapper) |
| Containerization | Docker (multi-stage build) |
| CI | GitHub Actions |

---

## Project Structure

```
java-backend/
├── src/main/java/com/whatsmine/
│   ├── config/          # Security, app-wide Spring configuration
│   ├── controller/      # REST & Inertia-style controllers (admin, api, client, inbox, social, webhook, whatsapp, broadcasting)
│   ├── dto/              # Request/response data transfer objects
│   ├── exception/       # Centralized exception handling
│   ├── inertia/         # Server-driven page rendering + global props
│   ├── model/            # JPA entities (74+ domain models)
│   ├── notification/    # In-app notification delivery
│   ├── queue/            # Background job handlers (campaign launch/finalize, template sync)
│   ├── realtime/        # WebSocket messaging
│   ├── repository/      # Spring Data JPA repositories
│   ├── scheduler/        # Scheduled/cron tasks
│   ├── security/         # Authentication filters, user detail services
│   └── service/           # Business logic (admin, ai, automation, billing, broadcasting, ecommerce, email, leads, license, segments, sms, social, whatsapp)
├── src/main/resources/
│   ├── application*.yml  # Profile-specific configuration (dev, local, prod, whatsapp)
│   ├── locales/           # i18n translation bundles
│   └── static/            # Bundled front-end assets served by the app
├── src/test/               # Unit and integration tests
├── Dockerfile              # Multi-stage container build
└── build.gradle             # Build & dependency configuration
```

---

## Getting Started

Full setup, environment variables, and troubleshooting live in **[RUN.md](RUN.md)**.

Fastest path (no external database required):

```powershell
cd java-backend
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Then open **http://localhost:8080**.

---

## Configuration

Configuration is fully externalized through environment variables and Spring profiles:

- `application.yml` — shared defaults (app metadata, server port, Jackson, Actuator, licensing)
- `application-local.yml` — H2 in-memory database, auto-created schema
- `application-dev.yml` — MySQL, verbose logging, schema validation
- `application-prod.yml` — MySQL with SSL, minimal logging, strict schema handling
- `application-whatsapp.yml` — WhatsApp/Meta Graph API integration settings

See **[RUN.md](RUN.md#6-environment-variables-reference)** for the complete variable reference.

---

## Security Model

- **BCrypt** password hashing (strength 12)
- Separate authentication managers for admin vs. client principals
- CSRF protection on session-based routes (cookie-based token repository)
- Stateless, token-authenticated `/api/**` surface
- Workspace-scoped access enforcement via a dedicated security filter
- Optional license-gating filter for self-hosted/reseller deployments
- Custom authentication entry points and access-denied handlers for consistent error responses

---

## API Surface

Representative API controllers (`/api/**`, stateless, token-authenticated):

| Controller | Purpose |
|---|---|
| `MeApiController` | Current authenticated user/session info |
| `NotificationApiController` | In-app notification feed |
| `AuditLogApiController` | Audit trail retrieval |
| `OutboundWebhookApiController` | Outbound webhook configuration |
| `TokenApiController` | API token issuance/management |

Health and monitoring:

- `GET /actuator/health`
- `GET /actuator/info`
- `GET /actuator/metrics`

---

## Testing

```powershell
cd java-backend
.\gradlew.bat test
```

Tests run under the `test` Spring profile (`src/test/resources/application-test.yml`) using JUnit 5.

Continuous integration (`.github/workflows/java-backend-ci.yml`) builds the jar, runs the test suite, publishes test reports, and builds the Docker image on every push and pull request to `main`.

---

## Deployment

The service is packaged as a single executable JAR and ships with a production-ready, multi-stage `Dockerfile`:

```powershell
docker build -t hub-notification-backend ./java-backend
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod hub-notification-backend
```

The image runs as a non-root user and includes a built-in `HEALTHCHECK` against `/actuator/health`. See **[RUN.md](RUN.md#8-running-with-docker)** for full deployment instructions.

---

## Contributing

1. Create a feature branch from `main`
2. Make your changes under `java-backend/`
3. Run `./gradlew test` and ensure the build passes
4. Open a pull request — CI will build, test, and produce a Docker image automatically
