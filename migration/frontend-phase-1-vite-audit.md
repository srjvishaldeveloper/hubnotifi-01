# Frontend Phase 1 — Vite Audit

## 1. Vite Configuration Audit (`php/vite.config.js`)

```javascript
import { defineConfig } from 'vite';
import laravel from 'laravel-vite-plugin';
import react from '@vitejs/plugin-react';

export default defineConfig({
    server: {
        host: '127.0.0.1', // Avoid IPv6 [::1] so CSP script-src matches without parsing issues
    },
    plugins: [
        laravel({
            input: 'resources/js/app.jsx',
            refresh: true,
        }),
        react(),
    ],
});
```

---

## 2. Key Plugin Functions & Behavioral Analysis

### A. `laravel-vite-plugin`
1. **HMR Development Mode (`npm run dev`)**:
   - Spawns Vite development server on `http://127.0.0.1:<port>` (e.g. `5173` or `5174`).
   - Writes a single line containing `http://127.0.0.1:<port>` into `php/public/hot`.
2. **Production Build Mode (`npm run build`)**:
   - Bundles `resources/js/app.jsx` and its dependencies.
   - Outputs compiled assets to `php/public/build/assets/` (e.g., `app-[hash].js` and `app-[hash].css`).
   - Generates `php/public/build/manifest.json` mapping input paths (`resources/js/app.jsx`) to build filenames.
3. **Inertia Page Resolver Helper**:
   - `resources/js/app.jsx` imports `resolvePageComponent` from `laravel-vite-plugin/inertia-helpers`.
   - `resolvePageComponent` converts glob imports `import.meta.glob('./Pages/**/*.jsx')` into lazy-loaded dynamic imports.

---

## 3. Vite Build Output Audit

- **Dev Mode Artefact**: `php/public/hot` containing dev server URL.
- **Production Build Directory**: `php/public/build/`
  - `manifest.json`: Asset mapping catalog
  - `assets/app-[hash].js`: Compiled React JavaScript bundle
  - `assets/app-[hash].css`: Compiled Tailwind CSS stylesheet bundle
