# Frontend Phase 1 — Architecture Audit

## 1. Repository Architecture Map

```text
hubnotifi-01/
├── php/                                # Original Laravel / Frontend Root
│   ├── app/                            # PHP Application Logic (Reference only)
│   ├── bootstrap/                      # PHP Bootstrap
│   ├── config/                         # PHP Config
│   ├── database/                       # Migrations & Seeders (Reference only)
│   ├── public/                         # Public Assets & Vite hot/build outputs
│   │   ├── build/                      # Compiled Vite output (assets, manifest.json)
│   │   ├── hot                         # Vite HMR dev server URL indicator file
│   │   └── images/                     # Static media images
│   ├── resources/                      # Frontend Source Code
│   │   ├── css/                        # Stylesheets (app.css)
│   │   └── js/                         # React Application Source
│   │       ├── app.jsx                 # React Entry Point (Inertia Bootstrap)
│   │       ├── bootstrap.js            # Axios & CSRF Setup
│   │       ├── echo.js                 # Laravel Echo / Pusher Setup
│   │       ├── Components/             # Shared UI Components
│   │       ├── Layouts/                # Page Layouts (ClientLayout, AdminLayout, etc.)
│   │       ├── Pages/                  # Inertia Page Components
│   │       ├── context/                # React Contexts (ThemeContext, etc.)
│   │       └── i18n/                   # Internationalization Resources
│   ├── jsconfig.json                   # JS Path Aliases (@/*)
│   ├── package.json                    # Node dependencies & scripts
│   ├── postcss.config.js               # PostCSS Configuration
│   ├── tailwind.config.js              # Tailwind CSS Configuration
│   └── vite.config.js                  # Vite Build & Dev Server Configuration
│
└── java-backend/                       # Spring Boot Backend (Java 21)
    ├── src/
    │   ├── main/
    │   │   ├── java/com/whatsmine/
    │   │   │   ├── config/             # Spring Security, Web, WebSocket Config
    │   │   │   ├── controller/         # API & Ziggy Controllers
    │   │   │   └── inertia/            # InertiaRenderer & Shared Props
    │   │   └── resources/
    │   │       ├── application.yml     # Application Properties
    │   │       └── static/             # Target Production Static Assets Location
    └── build.gradle                    # Gradle Build Configuration
```

---

## 2. React Application Specifications Audit

| Metric / Dimension | Value / Implementation |
| :--- | :--- |
| **React Entry Point** | `php/resources/js/app.jsx` |
| **React Version** | `19.2.4` |
| **Inertia React Version** | `@inertiajs/react ^2.3.16` |
| **Build Tool & Dev Server** | Vite `^6.0.11` + `@vitejs/plugin-react ^5.1.4` |
| **CSS Framework** | TailwindCSS `^3.2.1` via PostCSS `autoprefixer ^10.4.12` |
| **State Management** | React Context (`ThemeContext`), local component state, `@inertiajs/react` page state |
| **HTTP Client** | Axios `^1.7.4` with automatic CSRF token sync (`X-CSRF-TOKEN`) |
| **Realtime Client** | Laravel Echo `^2.3.4` + Pusher JS `^8.5.0` |
| **Route Resolution (Ziggy)** | Dynamic `/api/ziggy.js` served by Spring Boot `ZiggyController`, injecting `window.route` |
| **Icons & UI Utilities** | Lucide React `^0.575.0`, Sonner `^2.0.7`, Headless UI `^2.0.0` |

---

## 3. Inertia Protocol Audit

1. **Bootstrap Mechanism**: `createInertiaApp` in `app.jsx` resolves components matching `./Pages/${name}.jsx`.
2. **Page Component Wrapping**: `wrappedPages` WeakMap wraps page components with `ThemeProvider`, `LocaleSync`, `BrandingFavicon`, and `ErrorBoundary`.
3. **Shared Props**: Spring Boot `InertiaRenderer` & `DefaultGlobalPropsProvider` inject:
   - `auth`: `user`, `role`, `client_id`, `workspace_id`
   - `csrf_token`: Fresh CSRF UUID token
   - `flash`: Flash notifications map
   - `branding`: `app_name`, `logo_url`
   - `pusher`: `{ key: "whatsmine-key", cluster: "mt1", enabled: false }`
   - `app_version`: System version string
4. **CSRF Synchronization**: `syncCsrfToken(page)` updates `window.axios.defaults.headers.common['X-CSRF-TOKEN']` and `<meta name="csrf-token">` on every Inertia navigation success.
