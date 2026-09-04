# Shared Inbox Database Mapping — Phase 8 Migration

This document records the exact JPA entity mapping for the 8 database tables involved in the Shared Inbox module.

---

## 1. Table Mappings

### A. `conversations`
- **Table**: `conversations`
- **Columns**:
  - `id` (BIGINT, Primary Key, Auto Increment)
  - `uuid` (VARCHAR 36, Unique)
  - `workspace_id` (BIGINT, Indexed)
  - `channel_account_id` (BIGINT, Indexed)
  - `contact_id` (BIGINT, Indexed)
  - `external_thread_id` (VARCHAR 255)
  - `status` (VARCHAR 32, Default: `open`) — ENUM values: `open`, `pending`, `snoozed`, `resolved`
  - `assigned_user_id` (BIGINT, Nullable)
  - `assigned_to` (VARCHAR 32, Default: `human`) — `human` / `bot`
  - `unread_count` (INT, Default: 0)
  - `last_message_at` (DATETIME)
  - `first_response_at` (DATETIME)
  - `resolved_at` (DATETIME)
  - `last_inbound_at` (DATETIME)
  - `handover_at` (DATETIME)
  - `created_at` / `updated_at` (DATETIME)
- **JPA Entity**: `com.whatsmine.model.Conversation`

### B. `messages`
- **Table**: `messages`
- **Columns**:
  - `id` (BIGINT, Primary Key, Auto Increment)
  - `conversation_id` (BIGINT, Foreign Key -> `conversations.id`)
  - `direction` (VARCHAR 8) — `in` / `out`
  - `channel` (VARCHAR 32) — `whatsapp`, `instagram`, `messenger`
  - `type` (VARCHAR 32) — `text`, `template`, `image`, `video`, `audio`, `document`, `location`, `interactive`, `reaction`, etc.
  - `body` (TEXT)
  - `payload` (LONGTEXT / JSON)
  - `media_id` (VARCHAR 255)
  - `status` (VARCHAR 32) — `queued`, `sent`, `delivered`, `read`, `failed`
  - `provider_message_id` (VARCHAR 255, Indexed)
  - `error_json` (LONGTEXT / JSON)
  - `sent_by` (VARCHAR 32, Default: `human`) — `human` / `system` / `bot`
  - `user_id` (BIGINT, Nullable)
  - `sent_at` (DATETIME)
  - `created_at` / `updated_at` (DATETIME)
- **JPA Entity**: `com.whatsmine.model.Message`

### C. `contacts`
- **Table**: `contacts`
- **Columns**:
  - `id` (BIGINT, Primary Key, Auto Increment)
  - `uuid` (VARCHAR 36, Unique)
  - `workspace_id` (BIGINT, Indexed)
  - `phone_e164` (VARCHAR 32, Indexed)
  - `email` (VARCHAR 255)
  - `first_name` (VARCHAR 128)
  - `last_name` (VARCHAR 128)
  - `avatar` (VARCHAR 512)
  - `country` (VARCHAR 8)
  - `language` (VARCHAR 8)
  - `opt_in_whatsapp` (BOOLEAN, Default: true)
  - `opt_in_sms` (BOOLEAN, Default: false)
  - `opt_in_email` (BOOLEAN, Default: false)
  - `custom_fields` (LONGTEXT / JSON)
  - `last_seen_at` (DATETIME)
  - `source` (VARCHAR 64)
  - `lead_id` (BIGINT, Nullable)
  - `deleted_at` (DATETIME, Nullable — Soft Delete)
  - `created_at` / `updated_at` (DATETIME)
- **JPA Entity**: `com.whatsmine.model.Contact`

### D. `channel_accounts`
- **Table**: `channel_accounts`
- **Columns**:
  - `id` (BIGINT, Primary Key, Auto Increment)
  - `workspace_id` (BIGINT, Indexed)
  - `channel` (VARCHAR 32) — `whatsapp`, `instagram`, `messenger`
  - `provider` (VARCHAR 32)
  - `credentials` (TEXT)
  - `display_name` (VARCHAR 255)
  - `phone_number_id` (VARCHAR 255, Indexed)
  - `business_account_id` (VARCHAR 255)
  - `status` (VARCHAR 32, Default: `active`)
  - `meta_json` (LONGTEXT / JSON)
  - `created_at` / `updated_at` (DATETIME)
- **JPA Entity**: `com.whatsmine.model.ChannelAccount`

### E. `internal_notes`
- **Table**: `internal_notes`
- **Columns**:
  - `id` (BIGINT, Primary Key, Auto Increment)
  - `conversation_id` (BIGINT, Foreign Key -> `conversations.id`)
  - `user_id` (BIGINT, Foreign Key -> `users.id`)
  - `body` (TEXT)
  - `mentioned_user_ids` (LONGTEXT / JSON)
  - `created_at` / `updated_at` (DATETIME)
- **JPA Entity**: `com.whatsmine.model.InternalNote`

### F. `inbox_labels`
- **Table**: `inbox_labels`
- **Columns**:
  - `id` (BIGINT, Primary Key, Auto Increment)
  - `workspace_id` (BIGINT, Indexed)
  - `name` (VARCHAR 64)
  - `color` (VARCHAR 16)
  - `created_at` / `updated_at` (DATETIME)
- **JPA Entity**: `com.whatsmine.model.InboxLabel`

### G. `inbox_canned_replies`
- **Table**: `inbox_canned_replies`
- **Columns**:
  - `id` (BIGINT, Primary Key, Auto Increment)
  - `workspace_id` (BIGINT, Indexed)
  - `shortcut` (VARCHAR 64)
  - `body` (TEXT)
  - `created_at` / `updated_at` (DATETIME)
- **JPA Entity**: `com.whatsmine.model.CannedReply`
