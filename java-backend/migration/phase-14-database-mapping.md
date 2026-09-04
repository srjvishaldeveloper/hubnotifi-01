# Phase 14 — Database Mapping Document

## Overview
This document maps the exact database tables required for Phase 14 in MySQL.

> [!CRITICAL]
> DO NOT create migrations.
> DO NOT modify existing tables.
> DO NOT add columns, indexes, or rename fields.
> Hibernate JPA entity mappings must strictly reflect the existing MySQL database schema.

---

## 1. Table Schema Mapping Matrix

### 1. `support_tickets`
- `id` (BIGINT, PK, Auto-Increment)
- `user_id` (BIGINT, Nullable, FK `users.id`)
- `name` (VARCHAR 255, NOT NULL)
- `email` (VARCHAR 255, NOT NULL)
- `subject` (VARCHAR 255, NOT NULL)
- `message` (TEXT, NOT NULL)
- `status` (VARCHAR 32, Default 'open')
- `priority` (VARCHAR 32, Default 'medium')
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 2. `support_replies`
- `id` (BIGINT, PK, Auto-Increment)
- `ticket_id` (BIGINT, NOT NULL, FK `support_tickets.id`)
- `user_id` (BIGINT, Nullable, FK `users.id`)
- `author_name` (VARCHAR 255, NOT NULL)
- `is_staff` (BOOLEAN, Default false)
- `message` (TEXT, NOT NULL)
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 3. `notification_preferences`
- `id` (BIGINT, PK, Auto-Increment)
- `user_id` (BIGINT, NOT NULL, FK `users.id`)
- `event` (VARCHAR 100, NOT NULL)
- `channel` (VARCHAR 32, NOT NULL)
- `enabled` (BOOLEAN, Default true)
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 4. `push_subscriptions`
- `id` (BIGINT, PK, Auto-Increment)
- `user_id` (BIGINT, NOT NULL, FK `users.id`)
- `endpoint` (TEXT, NOT NULL)
- `p256dh_key` (TEXT, NOT NULL)
- `auth_key` (TEXT, NOT NULL)
- `ua` (VARCHAR 512, Nullable)
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 5. `media`
- `id` (BIGINT, PK, Auto-Increment)
- `mediable_type` (VARCHAR 255, NOT NULL)
- `mediable_id` (BIGINT, NOT NULL)
- `disk` (VARCHAR 64, NOT NULL)
- `path` (VARCHAR 1000, NOT NULL)
- `filename` (VARCHAR 255, NOT NULL)
- `mime_type` (VARCHAR 128, NOT NULL)
- `size_bytes` (BIGINT, NOT NULL)
- `collection` (VARCHAR 64, Default 'default')
- `meta` (JSON / TEXT, Nullable)
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 6. `webhook_endpoints`
- `id` (BIGINT, PK, Auto-Increment)
- `user_id` (BIGINT, NOT NULL, FK `users.id`)
- `url` (VARCHAR 500, NOT NULL)
- `secret` (VARCHAR 128, NOT NULL)
- `description` (VARCHAR 255, Nullable)
- `events` (JSON / TEXT, Nullable)
- `enabled` (BOOLEAN, Default true)
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 7. `webhook_deliveries`
- `id` (BIGINT, PK, Auto-Increment)
- `webhook_endpoint_id` (BIGINT, NOT NULL, FK `webhook_endpoints.id`)
- `event` (VARCHAR 100, NOT NULL)
- `payload` (JSON / TEXT, Nullable)
- `response_status` (INT, Nullable)
- `response_body` (TEXT, Nullable)
- `delivered_at` (TIMESTAMP, Nullable)
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 8. `integration_configs`
- `id` (BIGINT, PK, Auto-Increment)
- `workspace_id` (BIGINT, Nullable)
- `provider` (VARCHAR 64, NOT NULL)
- `mode` (VARCHAR 32, Default 'live')
- `enabled` (BOOLEAN, Default false)
- `credentials` (JSON / TEXT, Nullable)
- `settings` (JSON / TEXT, Nullable)
- `is_default` (BOOLEAN, Default false)
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 9. `integration_audit_logs`
- `id` (BIGINT, PK, Auto-Increment)
- `workspace_id` (BIGINT, Nullable)
- `provider` (VARCHAR 64, NOT NULL)
- `action` (VARCHAR 128, NOT NULL)
- `details` (JSON / TEXT, Nullable)
- `created_at` (TIMESTAMP)

### 10. `social_accounts`
- `id` (BIGINT, PK, Auto-Increment)
- `workspace_id` (BIGINT, NOT NULL)
- `provider` (VARCHAR 32, NOT NULL)
- `provider_user_id` (VARCHAR 255, Nullable)
- `name` (VARCHAR 255, Nullable)
- `token` (TEXT, Nullable)
- `refresh_token` (TEXT, Nullable)
- `expires_at` (TIMESTAMP, Nullable)
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 11. `social_posts`
- `id` (BIGINT, PK, Auto-Increment)
- `workspace_id` (BIGINT, NOT NULL)
- `content` (TEXT, NOT NULL)
- `media_urls` (JSON / TEXT, Nullable)
- `status` (VARCHAR 32, Default 'draft')
- `scheduled_at` (TIMESTAMP, Nullable)
- `published_at` (TIMESTAMP, Nullable)
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 12. `social_post_accounts`
- `id` (BIGINT, PK, Auto-Increment)
- `social_post_id` (BIGINT, NOT NULL, FK `social_posts.id`)
- `social_account_id` (BIGINT, NOT NULL, FK `social_accounts.id`)
- `status` (VARCHAR 32, Default 'pending')
- `post_id_external` (VARCHAR 255, Nullable)
- `error` (TEXT, Nullable)

### 13. `audit_logs`
- `id` (BIGINT, PK, Auto-Increment)
- `workspace_id` (BIGINT, Nullable)
- `user_id` (BIGINT, Nullable)
- `event` (VARCHAR 128, NOT NULL)
- `auditable_type` (VARCHAR 255, Nullable)
- `auditable_id` (BIGINT, Nullable)
- `old_values` (JSON / TEXT, Nullable)
- `new_values` (JSON / TEXT, Nullable)
- `url` (VARCHAR 500, Nullable)
- `ip_address` (VARCHAR 45, Nullable)
- `user_agent` (VARCHAR 512, Nullable)
- `created_at` (TIMESTAMP)

### 14. `system_settings`
- `id` (BIGINT, PK, Auto-Increment)
- `key` (VARCHAR 191, NOT NULL, UNIQUE)
- `value` (TEXT, Nullable)
- `group` (VARCHAR 64, Default 'general')
- `type` (VARCHAR 32, Default 'string')
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 15. `client_settings`
- `id` (BIGINT, PK, Auto-Increment)
- `client_id` (BIGINT, NOT NULL, FK `clients.id`)
- `key` (VARCHAR 191, NOT NULL)
- `value` (TEXT, Nullable)
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 16. `cms_pages`
- `id` (BIGINT, PK, Auto-Increment)
- `title` (VARCHAR 255, NOT NULL)
- `slug` (VARCHAR 191, NOT NULL, UNIQUE)
- `content` (LONGTEXT, Nullable)
- `published` (BOOLEAN, Default true)
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 17. `locales`
- `id` (BIGINT, PK, Auto-Increment)
- `code` (VARCHAR 10, NOT NULL, UNIQUE)
- `name` (VARCHAR 64, NOT NULL)
- `flag` (VARCHAR 10, Nullable)
- `is_default` (BOOLEAN, Default false)
- `enabled` (BOOLEAN, Default true)
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 18. `translations`
- `id` (BIGINT, PK, Auto-Increment)
- `locale_code` (VARCHAR 10, NOT NULL)
- `group` (VARCHAR 64, Default 'messages')
- `key` (VARCHAR 255, NOT NULL)
- `value` (TEXT, Nullable)
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 19. `onboarding_steps`
- `id` (BIGINT, PK, Auto-Increment)
- `user_id` (BIGINT, NOT NULL, FK `users.id`)
- `step_key` (VARCHAR 64, NOT NULL)
- `completed` (BOOLEAN, Default false)
- `completed_at` (TIMESTAMP, Nullable)
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)
