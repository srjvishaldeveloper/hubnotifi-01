# Phase 12 — Ecommerce & Leads Parity Contract

## Behavioral Parity Guarantees

### 1. Ecommerce Store Management
- **Identical Inertia Component**: `Ecommerce/Stores/Index`
- **Store Platform Support**: Shopify, WooCommerce, BigCommerce
- **Security & SSRF Guarding**: `StoreUrlGuard` validates store domains, ensuring bare `*.myshopify.com` for Shopify, alphanumeric store hash for BigCommerce, and public IP resolution for WooCommerce (blocking internal/private loopbacks).
- **Connection Testing**: Updates `last_tested_at`, `last_test_status`, `last_test_message`, `status`, and `external_meta` fields in `ecommerce_stores`.

### 2. Product Catalog & Picker API
- **Identical Inertia Component**: `Ecommerce/Products/Index`
- **Picker API Endpoint**: `GET /app/ecommerce/products/search` returns light JSON payloads for Shared Inbox product sharing.
- **Stock Thresholding**: Low stock threshold statically set to 5; out-of-stock and total statistics computed cleanly.

### 3. Order Management & Inbox Integration
- **Identical Inertia Components**: `Ecommerce/Orders/Index` and `Ecommerce/Orders/Show`
- **Fulfillment**: `POST /app/ecommerce/orders/{id}/fulfill` updates tracking URL and number, setting `fulfillment_status` to `fulfilled`.
- **Shared Inbox Integration**: `GET /app/ecommerce/contacts/{contactId}/orders` returns JSON list of contact orders for the inbox context sidebar.

### 4. Lead Generation & Contacts Push
- **Identical Inertia Component**: `Leads/Index`
- **Google Places Scraping**: `POST /app/leads/scrape` creates a `LeadScrapeJob`, populating the `leads` table with Google Place IDs, rating, review counts, and formatted addresses.
- **Contacts Import**: `POST /app/leads/push-to-contacts` converts scraped leads with valid phone or email into `Contact` records in `contacts` table and marks `pushed_to_contacts = true`.
- **Workspace Isolation**: Enforces workspace boundary checks on all endpoints; cross-workspace operations are forbidden (403/404).
