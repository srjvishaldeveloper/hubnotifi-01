# Phase 12 — Database Schema Mapping

## Mapped Tables

### 1. `ecommerce_stores`
- **Table Name**: `ecommerce_stores`
- **Entity**: `com.whatsmine.model.EcommerceStore`
- **Columns Mapped**:
  - `id`: `Long` (Primary Key)
  - `workspace_id`: `Long`
  - `platform`: `String` ('shopify', 'woocommerce', 'bigcommerce')
  - `name`: `String`
  - `domain`: `String`
  - `credentials`: `Map<String, Object>` (JSON column)
  - `status`: `String`
  - `uuid`: `String`
  - `external_meta`: `Map<String, Object>` (JSON column)
  - `webhook_secret`: `String`
  - `last_tested_at`, `last_test_status`, `last_test_message`
  - `customers_synced_at`, `orders_synced_at`, `products_synced_at`

### 2. `ecommerce_products`
- **Table Name**: `ecommerce_products`
- **Entity**: `com.whatsmine.model.EcommerceProduct`
- **Columns Mapped**:
  - `id`: `Long` (Primary Key)
  - `workspace_id`: `Long`
  - `store_id`: `Long`
  - `external_id`: `String`
  - `platform`: `String`
  - `name`: `String`
  - `sku`: `String`
  - `price`: `BigDecimal`
  - `inventory_quantity`: `Integer`
  - `status`: `String`
  - `image_url`: `String`
  - `raw`: `Map<String, Object>` (JSON column)

### 3. `ecommerce_orders`
- **Table Name**: `ecommerce_orders`
- **Entity**: `com.whatsmine.model.EcommerceOrder`
- **Columns Mapped**:
  - `id`: `Long` (Primary Key)
  - `workspace_id`: `Long`
  - `store_id`: `Long`
  - `contact_id`: `Long`
  - `external_order_id`: `String`
  - `platform`: `String`
  - `number`: `String`
  - `status`: `String`
  - `financial_status`: `String`
  - `fulfillment_status`: `String`
  - `currency`: `String`
  - `total`: `BigDecimal`
  - `line_items`: `List<Map<String, Object>>` (JSON column)
  - `tracking_url`, `tracking_number`, `placed_at`

### 4. `ecommerce_carts`
- **Table Name**: `ecommerce_carts`
- **Entity**: `com.whatsmine.model.EcommerceCart`
- **Columns Mapped**:
  - `id`: `Long` (Primary Key)
  - `workspace_id`: `Long`
  - `store_id`: `Long`
  - `contact_id`: `Long`
  - `external_id`: `String`
  - `total`: `BigDecimal`
  - `currency`: `String`
  - `line_items`: `List<Map<String, Object>>` (JSON column)
  - `recovery_url`: `String`
  - `abandoned_at`, `recovered_at`, `recovery_triggered_at`

### 5. `leads`
- **Table Name**: `leads`
- **Entity**: `com.whatsmine.model.Lead`
- **Columns Mapped**:
  - `id`: `Long` (Primary Key)
  - `workspace_id`: `Long`
  - `name`: `String`
  - `phone`: `String`
  - `email`: `String`
  - `website`: `String`
  - `address`: `String`
  - `city`: `String`
  - `country`: `String`
  - `lat`, `lng`: `BigDecimal`
  - `category`: `String`
  - `rating`: `BigDecimal`
  - `review_count`: `Integer`
  - `google_place_id`: `String` (Unique constraint)
  - `whatsapp_status`: `String`
  - `pushed_to_contacts`: `boolean`

### 6. `lead_scrape_jobs`
- **Table Name**: `lead_scrape_jobs`
- **Entity**: `com.whatsmine.model.LeadScrapeJob`
- **Columns Mapped**:
  - `id`: `Long` (Primary Key)
  - `workspace_id`: `Long`
  - `keyword`: `String`
  - `location`: `String`
  - `radius_meters`: `Integer`
  - `status`: `String` ('pending', 'running', 'done', 'failed')
  - `leads_found`: `Integer`
  - `error`: `String`
  - `started_at`, `completed_at`
