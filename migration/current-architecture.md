# Current Architecture Analysis

## 1. Executive Summary & Architecture Overview

The application is a single, integrated, monolithic SaaS platform built with **Laravel 12 (PHP 8.2+)** on the backend and **React 19** on the frontend, bridged together using **Inertia.js 2.x**.

Unlike decoupled SPA + REST API architectures, this codebase operates as an **Inertia-driven monolith**:
- There is **no separate React build running on an independent frontend server**.
- PHP renders the initial single-page application (SPA) HTML container (`resources/views/app.blade.php`), into which Inertia injects JSON page props.
- Subsequent user interactions and page transitions are handled client-side by React via Inertia AJAX visits (`X-Inertia: true`), which return JSON payloads matching exact React component props.
- React routes are defined by Laravel's routing system (`routes/web.php`, `routes/admin.php`, `routes/client.php`, `routes/auth.php`, `app/Modules/*/routes/web.php`) and mapped dynamically to React page components using **Ziggy** (`route('name', params)`).

```
+-------------------------------------------------------------------------------+
|                                 BROWSER                                       |
|  +-------------------------------------------------------------------------+  |
|  |                       React 19 Single Page Application                    |  |
|  |   (@inertiajs/react, Ziggy route(), Axios, Echo WebSocket, i18next)     |  |
|  +-------------------------------------------------------------------------+  |
+--------------------------------------|----------------------------------------+
                                       | HTTP / Inertia / WebSockets
                                       v
+-------------------------------------------------------------------------------+
|                         SERVER (Render Deployment)                            |
|  +-------------------------------------------------------------------------+  |
|  |                       Laravel 12 Monolithic Core                        |  |
|  |   - HTTP Controllers & Inertia Middleware (HandleInertiaRequests)       |  |
|  |   - Multi-Guard Auth (Users, AdminUsers, Sanctum API Tokens)             |  |
|  |   - Feature Modules (Whatsapp, AI, Automation, Broadcasting, Inbox)     |  |
|  |   - Eloquent ORM & Query Builder (MySQL / PostgreSQL)                   |  |
|  |   - Queue Workers & Scheduled Tasks (Supervisor / Artisan)               |  |
|  |   - Real-time Broadcasting (Reverb / Pusher)                            |  |
|  +-------------------------------------------------------------------------+  |
+-------------------------------------------------------------------------------+
```

---

## 2. Complete Folder Structure Map

```
hubnotification/
├── php/                                # Main Application Root
│   ├── app/                            # PHP Application Core
│   │   ├── Console/                    # Artisan Commands & Scheduling
│   │   ├── Contracts/                  # Interface Definitions
│   │   ├── Events/                     # Event Classes
│   │   ├── Http/                       # HTTP Layer
│   │   │   ├── Controllers/            # Web, Admin, Client, Api, Auth, Webhook Controllers
│   │   │   │   ├── Admin/              # Admin Panel Controllers (30+ controllers)
│   │   │   │   ├── Api/                # Sanctum REST API Controllers
│   │   │   │   ├── Auth/               # Auth Controllers (Login, Register, 2FA, Social)
│   │   │   │   ├── Client/             # Client Portal Controllers (15+ controllers)
│   │   │   │   ├── Install/            # Setup Wizard Controller
│   │   │   │   └── Webhooks/           # External Webhook Handlers
│   │   │   ├── Middleware/             # Middleware (HandleInertiaRequests, EnsureInstalled, etc.)
│   │   │   ├── Requests/               # Form Validation Requests
│   │   │   └── Resources/              # API Resources / Serializers
│   │   ├── Jobs/                       # Background Queue Jobs
│   │   ├── Listeners/                  # Event Listeners
│   │   ├── Mail/                       # Mailable Classes
│   │   ├── Models/                     # Eloquent Models (User, Client, Workspace, Plan, etc.)
│   │   ├── Modules/                    # Modular Architecture (Domain-Driven Modules)
│   │   │   ├── AI/                     # OpenAI/Claude AI Chatbot & Knowledge Base Module
│   │   │   ├── Automation/             # Workflow & Auto-Reply Builder Module
│   │   │   ├── Broadcasting/           # Bulk Campaign & Rate-Limited Messaging Module
│   │   │   ├── Ecommerce/              # Product Catalog & Order Notifications Module
│   │   │   ├── Inbox/                  # Multi-Agent Shared Live Chat Inbox Module
│   │   │   ├── Integrations/           # Third-party credentials & API resolvers
│   │   │   ├── Leads/                  # Contact & Lead Scoring Module
│   │   │   ├── Shared/                 # Shared Contact Management & Utilities
│   │   │   ├── Social/                 # Facebook & Instagram Messaging Integration
│   │   │   └── Whatsapp/               # Meta WhatsApp Cloud API Client & Webhooks
│   │   ├── Notifications/              # System & Web Push Notifications
│   │   ├── Policies/                   # Authorization Policies
│   │   ├── Providers/                  # Service Providers
│   │   ├── Services/                   # Core Domain Services (Storage, Onboarding, I18n)
│   │   └── Support/                    # Helper Classes & Trait Utilities
│   ├── bootstrap/                      # Application Lifecycle Bootstrap (`app.php`, `providers.php`)
│   ├── config/                         # Configuration Files (app, auth, database, saas, services, etc.)
│   ├── database/                       # Database Resources
│   │   ├── factories/                  # Model Factories for Testing
│   │   ├── migrations/                 # 53+ Migration Files defining database tables
│   │   └── seeders/                    # Database Seeders
│   ├── docker/                         # Docker & Supervisor Process Configs
│   │   └── supervisor/whatsmine.conf   # Supervisor config for Queue Workers & Scheduler
│   ├── public/                         # Web Server Document Root
│   │   ├── build/                      # Compiled Vite React assets (JS, CSS, fonts)
│   │   ├── index.php                   # Single Entry Point for HTTP Requests
│   │   └── .htaccess                   # Apache URL Rewriting Rules
│   ├── resources/                      # Frontend Sources
│   │   ├── css/                        # Tailwind & Custom CSS
│   │   ├── js/                         # React Application Source
│   │   │   ├── app.jsx                 # Inertia React Application Entry Point
│   │   │   ├── bootstrap.js            # Axios & CSRF Setup
│   │   │   ├── echo.js                 # Echo WebSocket Client Setup
│   │   │   ├── Components/             # Shared React UI Components
│   │   │   ├── Layouts/                # App Layouts (Admin, Client, Guest, Inbox)
│   │   │   ├── Pages/                  # Inertia Page Components (Admin, Client, Auth, Public)
│   │   │   ├── Utils/                  # JavaScript Helper Utilities
│   │   │   ├── context/                # React Contexts (ThemeContext, etc.)
│   │   │   ├── hooks/                  # Custom React Hooks
│   │   │   ├── i18n.js                 # Client-side Translation Engine
│   │   │   ├── lib/                    # Shared Libraries
│   │   │   └── push.js                 # Web Push / OneSignal Client Handlers
│   │   └── views/                      # Blade Templates (`app.blade.php`)
│   ├── routes/                         # Laravel Route Definitions
│   │   ├── admin.php                   # Admin Panel Routes
│   │   ├── api.php                     # REST API Endpoints (Sanctum)
│   │   ├── auth.php                    # Authentication Routes
│   │   ├── channels.php                # WebSocket Channel Authorization
│   │   ├── client.php                  # Client Dashboard Routes
│   │   ├── console.php                 # Artisan Scheduled Tasks
│   │   ├── reports.php                 # Report Export Download Routes
│   │   ├── web.php                     # Public / Landing / Utility Routes
│   │   └── webhooks.php                # Webhook Handlers (Stripe, WhatsApp, SMS)
│   ├── storage/                        # Application Storage (Logs, Framework Cache, Uploads)
│   ├── tests/                          # Feature & Unit Tests (PHPUnit / Vitest)
│   ├── .env.example                    # Environment Template
│   ├── artisan                         # Laravel CLI Script
│   ├── composer.json                   # PHP Dependencies
│   ├── package.json                    # Node/Frontend Dependencies
│   ├── tailwind.config.js              # Tailwind CSS Configuration
│   └── vite.config.js                  # Vite Build Configuration
└── report_md/                          # Report Storage Directory
```

---

## 3. Request Execution Flow

```
User Click / Navigation in React
       |
       v
Inertia Router Intercepts (`router.visit` or `<Link>`)
       |
       +---> Sends AJAX Request with Header `X-Inertia: true` and `X-CSRF-TOKEN`
       |
       v
Laravel HTTP Engine (`public/index.php`)
       |
       +---> Route Resolution (`routes/*.php` and `app/Modules/*/routes/*.php`)
       |
       +---> Middleware Execution (`EnsureInstalled`, `StartSession`, `HandleInertiaRequests`)
       |
       +---> Controller Execution -> Returns `Inertia::render('Page/Name', $props)`
       |
       v
`HandleInertiaRequests` Middleware Merges Global Props:
       - auth (user, adminUser, permissions)
       - currentWorkspace & workspaces list
       - csrf_token & flash messages
       - i18n dictionary & locale configuration
       - branding, pusher, onesignal, firebase, current_workspace_usage
       |
       v
PHP Serializes Data to JSON Response:
       {
         "component": "Client/Dashboard",
         "props": { ... },
         "url": "/app/dashboard",
         "version": "b47c9e..."
       }
       |
       v
React Inertia Engine Receives JSON
       |
       +---> Dynamically resolves page component from `./Pages/Client/Dashboard.jsx`
       |
       +---> Re-renders page component inside Active Layout with updated props without full page reload
```

---

## 4. Key Architectural Patterns & Characteristics

1. **Inertia.js Protocol**:
   - Eliminates the need for a separate REST API for the web frontend.
   - Server returns complete prop objects per route; React components render purely based on incoming props.
   - CSRF protection relies on standard Laravel web session cookies (`laravel_session`, `XSRF-TOKEN`).

2. **Ziggy Route Resolver**:
   - `@routes` blade directive exposes Laravel named routes as JavaScript data structures.
   - React components construct URLs using `route('admin.clients.show', { id: 1 })`.

3. **Modular Monolith (`app/Modules/`)**:
   - Business features (WhatsApp, AI, Automation, Inbox, Ecommerce, Leads) are decoupled into self-contained modules.
   - Each module contains its own Controllers, Services, Models, Jobs, Migrations, and Route files.

4. **Multi-Tenancy & Workspace Scoping**:
   - Clients own Workspaces.
   - Session stores `current_workspace_id`.
   - Controllers apply global scopes or explicit `where('workspace_id', $id)` filters to isolate workspace data.

5. **Multi-Guard Authentication**:
   - Guard `web`: Client users (User model).
   - Guard `admin`: System Administrators (AdminUser model).
   - Guard `sanctum`: External API integration tokens.
