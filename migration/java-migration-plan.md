# Master Java Migration Plan & Roadmap

## 1. Objectives & Guiding Constraints

The goal of this migration is to transition the backend infrastructure from **PHP (Laravel 12)** to **Java (Spring Boot 3.4+ / Java 21)** while satisfying the following mandatory constraints:

1. **Zero React Frontend Rewrites**: The React 19 source code (`resources/js/`) must remain **100% untouched** (or require zero architectural changes). Component props, Inertia visits, forms, and routes remain identical.
2. **Zero Database Schema Modifications**: MySQL/PostgreSQL schema, table names, column types, foreign keys, and indexes remain 100% unchanged. Existing data will be preserved.
3. **No Downtime / Incremental Migration Strategy**: Ensure the Java replacement passes end-to-end regression tests before cutover.
4. **Preserve All Features**: All WhatsApp messaging, AI Chatbots, Automations, Shared Inbox, Billing Gateways, Multi-tenancy, and System Administration features must be completely implemented in Java.

---

## 2. Technology Stack Selection

| Layer | PHP Technology | Java Replacement Technology | Rationale |
|---|---|---|---|
| **Runtime & JDK** | PHP 8.2 / 8.3 | **Java 21 LTS** (Eclipse Temurin) | High performance, Virtual Threads (Project Loom) for high-concurrency WhatsApp webhooks. |
| **Framework** | Laravel 12.x | **Spring Boot 3.4.x** | Enterprise standard, rich ecosystem for security, data, web, webhooks, and scheduling. |
| **Frontend Bridge** | Inertia Laravel (`inertiajs/inertia-laravel`) | **Spring Inertia Adapter** (Custom Spring Interceptor / `inertia-spring-boot`) | Emits identical Inertia JSON responses and HTML page containers. |
| **Routing Helper** | Tightenco Ziggy (`@routes`) | **Spring Ziggy Endpoint Controller** | Generates dynamic route JSON matching Laravel named routes. |
| **Security & Auth** | Laravel Guard (`web`, `admin`, `sanctum`) | **Spring Security 6.x** | Multi-chain authentication (Sessions, Bearer Sanctum Tokens, Admin Impersonation filter). |
| **ORM / Data Access** | Eloquent ORM | **Spring Data JPA / Hibernate 6.x** | Native mapping to existing database tables without schema changes. |
| **Real-time WebSockets** | Laravel Reverb / Pusher | **Spring WebSocket + STOMP / Pusher Java SDK** | Real-time push to React Echo client. |
| **Background Queues** | Laravel Queue (`artisan queue:work`) | **Spring `@Async` / Quartz / Spring Batch** | Asynchronous job execution with automatic retries and thread pools. |
| **Scheduling / Cron** | Laravel Scheduler (`routes/console.php`) | **Spring `@Scheduled` Tasks** | In-process cron scheduling for billing, campaign dispatch, template sync. |
| **AI Integration** | OpenAI PHP SDK | **Spring AI (OpenAI / Anthropic / Vector Stores)** | First-class AI model integration and RAG document embeddings. |
| **Billing Gateways** | Stripe PHP SDK | **Stripe Java SDK + Payment APIs** | Native Java client for Stripe, PayPal, Razorpay, Cashfree, Tap. |
| **File Storage** | Laravel Storage Facade | **Spring Cloud AWS S3 / Local File Storage Manager** | Unified storage abstraction for media & uploads. |

---

## 3. Phase-by-Phase Implementation Roadmap

```
+-----------------------------------------------------------------------------------+
| PHASE 1: Framework Foundation & Inertia-Spring Core Adapter                      |
| - Spring Boot 3.4 project setup with Java 21 & Gradle                            |
| - Custom Inertia Interceptor emitting Inertia JSON/HTML responses                 |
| - Ziggy Route Generator endpoint emitting window.Ziggy JS                         |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| PHASE 2: JPA Data Layer & Entity Mapping                                          |
| - Hibernate JPA entities for all 53+ database tables                             |
| - Multi-tenant workspace filter (`where workspace_id = ?`)                        |
| - Spring Data Repositories for all domain models                                  |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| PHASE 3: Spring Security, Auth & Multi-Guard Setup                                |
| - Multi-guard filter chains: Client Session, Admin Session, Sanctum Bearer Tokens |
| - Admin Client Impersonation Filter & CSRF Cookie Repository                      |
| - 2FA TOTP validator & Socialite OAuth login handlers                             |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| PHASE 4: Core Domain & Module Migration                                           |
| - WhatsApp Module (Meta Cloud API client, incoming webhook handlers, template sync)|
| - Shared Inbox Module (Conversations, Messages, Internal Notes, Echo push)         |
| - Automation & Broadcasting Modules (Campaign dispatch, queue execution)         |
| - AI Module (Spring AI, prompt templates, vector indexing)                        |
| - Billing Module (Stripe / Gateway webhooks, UsageMeter, subscriptions)           |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| PHASE 5: Build, Packaging & Render Deployment Cutover                             |
| - Multi-stage Dockerfile packaging Vite React assets inside Spring Boot JAR       |
| - Render deployment staging validation & cutover                                  |
+-----------------------------------------------------------------------------------+
```

---

## 4. Detailed Implementation Strategy for Core Components

### Component 1: Spring Boot Inertia Adapter

Implement a Spring MVC HandlerInterceptor that checks for incoming `X-Inertia` headers:

```java
@Component
public class InertiaInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String inertiaHeader = request.getHeader("X-Inertia");
        if (inertiaHeader != null && Boolean.parseBoolean(inertiaHeader)) {
            request.setAttribute("IS_INERTIA_REQUEST", true);
        }
        return true;
    }
}
```

Implement `Inertia.render(component, props)` helper:

```java
public class Inertia {
    public static ModelAndView render(String component, Map<String, Object> props) {
        ModelAndView mav = new ModelAndView("inertia-template");
        mav.addObject("component", component);
        mav.addObject("props", props);
        return mav;
    }
}
```

---

### Component 2: Spring Ziggy Route Endpoint

Create `@RestController` emitting registered Spring MVC routes in Ziggy JSON format:

```java
@RestController
public class ZiggyController {

    @GetMapping(value = "/api/ziggy.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getRoutes(HttpServletRequest request) {
        Map<String, Object> ziggy = new HashMap<>();
        ziggy.put("url", getBaseUrl(request));
        ziggy.put("port", request.getServerPort());
        ziggy.put("routes", getAllSpringRoutesMap());
        return ResponseEntity.ok(ziggy);
    }
}
```

---

### Component 3: Async Queue Execution with Spring `@Async`

Replace Laravel Artisan queue workers with Spring ThreadPoolTaskExecutor:

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "messageTaskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("WhatsAppWorker-");
        executor.initialize();
        return executor;
    }
}
```

---

## 5. Verification & Testing Plan

1. **Automated Integration Tests**:
   - Spring Boot `@SpringBootTest` with Testcontainers (MySQL & Redis) validating API contracts.
   - End-to-end Inertia response structure tests verifying required shared props (`auth`, `branding`, `csrf_token`, `i18n`).

2. **Frontend Parity Verification**:
   - Run Vite dev server connected to Spring Boot backend.
   - Test all user journeys: Login, Workspace Switching, Live Inbox messaging, WhatsApp Template sync, Campaign creation, Billing checkout, and Admin management.
   - Confirm 0 console errors and 0 layout glitches in React.

3. **Performance & Load Testing**:
   - Run JMeter / k6 stress testing on WhatsApp inbound webhook endpoints to measure throughput and response latency under high message loads.
