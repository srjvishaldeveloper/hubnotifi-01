# Phase 12 — Ecommerce & Leads Inventory

## Overview
Phase 12 migrates the Ecommerce store integrations (Shopify, WooCommerce, BigCommerce), Product catalog, Order management, Shared Inbox order context, and Google Places Leads Scraping functionality from PHP/Laravel to Spring Boot Java 21 with 1:1 behavioral parity.

## Database Tables Mapped (6/6)
1. `ecommerce_stores` -> `EcommerceStore.java`
2. `ecommerce_products` -> `EcommerceProduct.java`
3. `ecommerce_orders` -> `EcommerceOrder.java`
4. `ecommerce_carts` -> `EcommerceCart.java`
5. `leads` -> `Lead.java`
6. `lead_scrape_jobs` -> `LeadScrapeJob.java`

## Web & Webhook Routes Mapped (14/14)
- `GET /app/ecommerce/stores` -> `StoreController::index` (`Ecommerce/Stores/Index`)
- `POST /app/ecommerce/stores` -> `StoreController::store`
- `POST /app/ecommerce/stores/{id}/test` -> `StoreController::test`
- `POST /app/ecommerce/stores/{id}/sync` -> `StoreController::sync`
- `DELETE /app/ecommerce/stores/{id}` -> `StoreController::destroy`
- `GET /app/ecommerce/oauth/{platform}/connect` -> `EcommerceOAuthController::connect`
- `GET /app/ecommerce/oauth/shopify/callback` -> `EcommerceOAuthController::shopifyCallback`
- `GET /app/ecommerce/oauth/bigcommerce/callback` -> `EcommerceOAuthController::bigcommerceCallback`
- `GET /app/ecommerce/oauth/woocommerce/return` -> `EcommerceOAuthController::woocommerceReturn`
- `POST /webhooks/ecommerce/woo-auth` -> `EcommerceOAuthController::woocommerceCallback`
- `GET /app/ecommerce/products` -> `ProductController::index` (`Ecommerce/Products/Index`)
- `GET /app/ecommerce/products/search` -> `ProductController::search` (JSON)
- `GET /app/ecommerce/orders` -> `OrderController::index` (`Ecommerce/Orders/Index`)
- `GET /app/ecommerce/orders/{id}` -> `OrderController::show` (`Ecommerce/Orders/Show`)
- `POST /app/ecommerce/orders/{id}/refresh` -> `OrderController::refresh`
- `POST /app/ecommerce/orders/{id}/fulfill` -> `OrderController::fulfill`
- `GET /app/ecommerce/contacts/{contactId}/orders` -> `OrderContextController::getOrdersForContact` (JSON)
- `POST /webhooks/ecommerce/shopify/{storeId}` -> `EcommerceWebhookController::handleShopify`
- `POST /webhooks/ecommerce/woocommerce/{storeId}` -> `EcommerceWebhookController::handleWooCommerce`
- `POST /webhooks/ecommerce/bigcommerce/{storeId}` -> `EcommerceWebhookController::handleBigCommerce`
- `GET /app/leads` -> `LeadController::index` (`Leads/Index`)
- `POST /app/leads/scrape` -> `LeadController::scrape`
- `POST /app/leads/push-to-contacts` -> `LeadController::pushToContacts`
- `DELETE /app/leads/{id}` -> `LeadController::destroy`
