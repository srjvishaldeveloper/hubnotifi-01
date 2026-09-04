# Database Schema & Data Layer Analysis

## 1. Engine & Driver Compatibility

- **Supported RDBMS**: MySQL (Primary production target), PostgreSQL, SQLite (Development/Testing).
- **Configuration**: Managed via standard environment variables (`DB_CONNECTION`, `DB_HOST`, `DB_PORT`, `DB_DATABASE`, `DB_USERNAME`, `DB_PASSWORD`).
- **Migration Strategy**: The database schema **MUST REMAIN 100% UNCHANGED** during the Java migration. Spring Data JPA or MyBatis will map directly to the existing table names, column names, foreign keys, and indexes created by Laravel migrations.

---

## 2. Table Inventory & Domain Mapping

### A. Core System & Administration Tables
1. **`system_settings`**: `(key [PK], value, created_at, updated_at)` - Global key-value store (app branding, active gateways, Pusher config, Firebase keys).
2. **`locales`**: `(id, code, name, native_name, is_rtl, flag, is_default, enabled, created_at, updated_at)` - System supported languages.
3. **`translations`**: `(id, locale_code, group, key, value, created_at, updated_at)` - Multilingual dictionary entries.
4. **`currencies`**: `(id, code, symbol, decimals, exchange_rate, is_default, enabled, created_at, updated_at)` - Supported currencies & FX rates.
5. **`smtp_configurations`**: `(id, host, port, username, password, encryption, from_address, from_name, is_default, created_at, updated_at)` - Global SMTP email configs.
6. **`admin_users`**: `(id, name, email, password, status, remember_token, created_at, updated_at)` - Super admins & support staff.
7. **`roles`**, **`permissions`**, **`role_permission`**, **`admin_role`**: System administrator RBAC tables.
8. **`audit_logs`**: `(id, admin_user_id, action, target_type, target_id, ip_address, user_agent, payload, created_at)` - Action audit history.

### B. Client Accounts & Multi-Tenant Workspace Tables
1. **`clients`**: `(id, name, email, phone, status, company_name, vat_number, address, created_at, updated_at)` - Client SaaS tenant accounts.
2. **`workspaces`**: `(id, client_id, name, slug, timezone, currency_code, created_at, updated_at)` - Multi-tenant isolated workspace spaces.
3. **`users`**: `(id, workspace_id, name, email, password, avatar, theme, display_currency, status, 2fa_enabled, 2fa_secret, remember_token, created_at, updated_at)` - User accounts associated with workspaces.
4. **`workspace_user`**: `(workspace_id, user_id, role)` - Join table for multi-workspace access.
5. **`invitations`**: `(id, workspace_id, email, role, token, expires_at, created_at)` - Workspace user invitations.
6. **`personal_access_tokens`**: `(id, tokenable_type, tokenable_id, name, token, abilities, last_used_at, expires_at)` - Laravel Sanctum API tokens.
7. **`client_settings`**: `(id, client_id, key, value)` - Workspace/Client specific configuration overrides.

### C. Subscriptions & Payment Tables
1. **`plans`**: `(id, name, slug, description, price_monthly, price_yearly, currency, limits [JSON], features [JSON], is_active, created_at, updated_at)` - Subscription tier definitions.
2. **`client_subscriptions`**: `(id, client_id, plan_id, status, gateway, gateway_subscription_id, current_period_starts_at, current_period_ends_at, cancels_at, created_at, updated_at)` - Active client plan subscriptions.
3. **`subscriptions`**: `(id, workspace_id, plan_id, status, trial_ends_at, ends_at, created_at, updated_at)` - Workspace plan allocations.
4. **`payment_transactions`**: `(id, client_id, subscription_id, gateway, transaction_id, amount, currency, status, invoice_pdf, created_at)` - Transaction ledger.
5. **`payment_gateway_configs`**: `(id, gateway_name, credentials [JSON], is_enabled, mode)` - System payment gateway secrets.
6. **`coupons`**, **`tax_rates`**, **`billing_events`**: Subscription discounts, regional tax rates, and billing webhook event logs.

### D. WhatsApp & Messaging Modules Tables
1. **`channel_accounts`**: `(id, workspace_id, type, name, credentials [JSON], status, created_at, updated_at)` - Connected WABA / Social channels.
2. **`contacts`**: `(id, workspace_id, name, phone, email, avatar, custom_fields [JSON], tags [JSON], lead_score, created_at, updated_at)` - Unified CRM contacts.
3. **`conversations`**: `(id, workspace_id, contact_id, channel_account_id, assigned_user_id, status, unread_count, last_message_at, created_at, updated_at)` - Live chat threads.
4. **`messages`**: `(id, conversation_id, sender_type, sender_id, provider_message_id, type, content, media_url, status, metadata [JSON], created_at, updated_at)` - Individual messages (text, image, voice, PDF).
5. **`internal_notes`**: `(id, conversation_id, user_id, note, created_at)` - Agent collaboration notes on chat threads.
6. **`templates`**: `(id, workspace_id, waba_id, name, language, category, components [JSON], status, created_at)` - Sync'd Meta WhatsApp templates.

### E. Automation, AI & Broadcasting Tables
1. **`automations`**, **`automation_rules`**, **`automation_logs`**: Trigger-action workflow definitions and execution logs.
2. **`campaigns`**, **`campaign_recipients`**: Scheduled bulk messaging campaigns and individual dispatch statuses.
3. **`ai_assistants`**, **`ai_documents`**, **`ai_prompts`**: AI chatbot agent settings, RAG indexed document chunks, and generation logs.
4. **`usage_meters`**: `(id, workspace_id, metric, current_value, period_start, period_end)` - Real-time quota usage tracking.

### F. Notifications, Media & Support Tables
1. **`notifications`**, **`notification_preferences`**, **`push_subscriptions`**: In-app notifications, push tokens (VAPID/OneSignal).
2. **`media`**: `(id, workspace_id, filename, disk, path, mime_type, size_bytes)` - Uploaded media metadata.
3. **`support_tickets`**, **`support_ticket_replies`**: System support tickets and attachments.
4. **`webhook_endpoints`**, **`webhook_deliveries`**: Client registered outgoing webhooks and delivery logs.

---

## 3. Java JPA Mapping Strategy

To maintain complete compatibility without modifying a single column:

1. **JPA Entity Naming**: Use `@Table(name = "exact_table_name")` and `@Column(name = "exact_column_name")`.
2. **JSON Columns**: Map JSON/JSONB columns (e.g. `limits`, `features`, `credentials`, `custom_fields`, `metadata`) using `@Type(JsonType.class)` (Hibernate Types / Hypersistence Utils) or Jackson converters.
3. **Timestamps**: Map `created_at` and `updated_at` to `LocalDateTime` or `Instant` with `@CreationTimestamp` and `@UpdateTimestamp`.
4. **Foreign Keys**: Model relations with `@ManyToOne`, `@OneToMany`, `@ManyToMany` preserving foreign key names.
5. **Multi-Tenant Workspace Filters**: Implement Spring Data JPA `@Filter` or Hibernate Tenant Discriminator to automatically inject `WHERE workspace_id = ?` into queries for client endpoints.
