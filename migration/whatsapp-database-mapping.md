# WhatsApp Database Schema Mapping — Phase 7 Migration

This document records the exact JPA mapping for existing WhatsApp tables without schema alterations.

---

## 1. Table Mappings

### A. `whatsapp_business_accounts`
- **Table**: `whatsapp_business_accounts`
- **Columns**:
  - `id` (BIGINT, Primary Key, Auto Increment)
  - `workspace_id` (BIGINT, Indexed)
  - `waba_id` (VARCHAR 64, Unique)
  - `credentials` (TEXT)
  - `webhook_verify_token` (VARCHAR 512)
  - `webhook_verify_token_hash` (VARCHAR 64, Indexed)
  - `status` (ENUM: `active`, `inactive`, `error`)
  - `meta_json` (LONGTEXT / JSON)
  - `created_at` / `updated_at` (DATETIME)
- **JPA Entity**: `com.whatsmine.model.WhatsappBusinessAccount`

### B. `whatsapp_phone_numbers`
- **Table**: `whatsapp_phone_numbers`
- **Columns**:
  - `id` (BIGINT, Primary Key, Auto Increment)
  - `waba_id_fk` (BIGINT, Foreign Key -> `whatsapp_business_accounts.id`)
  - `phone_number_id` (VARCHAR 64, Unique)
  - `display_phone` (VARCHAR 32)
  - `verified_name` (VARCHAR 128)
  - `quality_rating` (VARCHAR 32)
  - `messaging_limit_tier` (VARCHAR 64)
  - `code_verification_status` (VARCHAR 255)
  - `name_status` (VARCHAR 64)
  - `requested_verified_name` (VARCHAR 128)
  - `account_mode` (VARCHAR 32)
  - `created_at` / `updated_at` (DATETIME)
- **JPA Entity**: `com.whatsmine.model.WhatsappPhoneNumber`

### C. `whatsapp_templates`
- **Table**: `whatsapp_templates`
- **Columns**:
  - `id` (BIGINT, Primary Key, Auto Increment)
  - `workspace_id` (BIGINT, Indexed)
  - `waba_id` (VARCHAR 64, Indexed)
  - `name` (VARCHAR 128)
  - `language` (VARCHAR 8)
  - `category` (ENUM: `MARKETING`, `UTILITY`, `AUTHENTICATION`)
  - `status` (ENUM: `PENDING`, `APPROVED`, `REJECTED`, `PAUSED`)
  - `components` (LONGTEXT / JSON)
  - `rejection_reason` (TEXT)
  - `meta_template_id` (VARCHAR 64)
  - `created_at` / `updated_at` (DATETIME)
- **JPA Entity**: `com.whatsmine.model.WhatsappTemplate`

---

## 2. Workspace Scoping Requirements
- Every `WhatsappBusinessAccount` and `WhatsappTemplate` queries must be scoped by `workspace_id`.
- Foreign workspace data must return `403 Forbidden` if accessed across workspaces.
