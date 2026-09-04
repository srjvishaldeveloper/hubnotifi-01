# Phase 14 — Scope Classification Matrix

## 1. Classification Overview

All discovered features from the Laravel repository (`d:\hubnotification\php`) are categorized into strict migration phases to enforce system boundary control.

---

## 2. Comprehensive Scope Classification

| Feature / Subsystem | Category / Phase | Component Description | Rationale |
|---|---|---|---|
| Authentication & Sanctum | **Phase 1–5 (Already Migrated)** | User/Admin auth, Sanctum tokens, CSRF, Workspaces | Completed in Phases 1–5 |
| Client & Workspace Core | **Phase 6 (Already Migrated)** | Workspaces, Users, Contacts, Tags, Segments | Completed in Phase 6 |
| WhatsApp Integration | **Phase 7 (Already Migrated)** | WhatsApp Cloud API, Phone Numbers, WABAs, Webhooks | Completed in Phase 7 |
| Shared Inbox & Conversations | **Phase 8 (Already Migrated)** | Conversations, Messages, Notes, Labels, Canned Replies | Completed in Phase 8 |
| Broadcasting & Campaigns | **Phase 9 (Already Migrated)** | Campaigns, Recipients, Email/SMS providers | Completed in Phase 9 |
| Automation Engine | **Phase 10 (Already Migrated)** | Automation flow builder, trigger nodes, run logs | Completed in Phase 10 |
| AI Module | **Phase 11 (Already Migrated)** | AI Chatbots, Knowledge Bases, Documents, Chunks, Runs | Completed in Phase 11 |
| Ecommerce & Leads | **Phase 12 (Already Migrated)** | Stores, Products, Orders, Carts, Scraper | Completed in Phase 12 |
| Billing & Subscriptions | **Phase 13 (Already Migrated)** | Plans, Subscriptions, Payments, Gateways, Coupons | Completed in Phase 13 |
| **Support / Helpdesk Module** | **Phase 14 (Current)** | `SupportTicket`, `SupportReply`, Client & Admin Controllers | Core synchronous support ticketing functionality |
| **Notifications & Preferences** | **Phase 14 (Current)** | `NotificationPreference`, `PushSubscription`, bell notifications | Bell notifications, unread counts, preferences, WebPush APIs |
| **Media & File Storage** | **Phase 14 (Current)** | `Media` entity, `StorageManager`, S3/Local/DO/Wasabi drivers | File uploads, mime/size validations, storage quota logic |
| **Custom Outgoing Webhooks** | **Phase 14 (Current)** | `WebhookEndpoint`, `WebhookDelivery`, dispatcher, test ping | Outbound webhook endpoint management & delivery auditing |
| **Integrations & Social Config** | **Phase 14 (Current)** | `IntegrationConfig`, `SocialAccount`, `SocialPost`, API tokens | Social account linking, social post drafting, API tokens |
| **Audit Logs & System Settings** | **Phase 14 (Current)** | `AuditLog`, `SystemSetting`, `ClientSetting`, SMTP configs | Audit log index, client branding, SMTP settings |
| **CMS, Pages & i18n** | **Phase 14 (Current)** | `CmsPage`, `Locale`, `Translation`, landing page builder | Custom pages, multi-language support, onboarding wizard |
| **Public API v1 Endpoints** | **Phase 14 (Current)** | `/api/v1/me`, `/api/v1/tokens`, `/api/v1/audit-log` etc. | Synchronous Sanctum-guarded REST API endpoints |
| **Queue & Worker Engine** | **Phase 15 (Deferred)** | Background job processing, retry delays, queue tables | Queue infrastructure & scheduler owned by Phase 15 |
| **Scheduled Jobs / Cron** | **Phase 15 (Deferred)** | `GenerateWorkspaceExportJob`, campaign background cron | Scheduled tasks & async heavy background tasks |
| **Realtime WebSockets & Pusher** | **Phase 16 (Deferred)** | Pusher settings, Echo broadcasting, typing indicators | Realtime messaging & live Echo presence owned by Phase 16 |
| **Full E2E Regression** | **Phase 17 (Deferred)** | Complete React + Java backend E2E suite | End-to-end integration testing owned by Phase 17 |

---

## 3. Phase 14 Mandatory Exclusions (No Cross-Phase Leakage)
1. **DO NOT** implement background queue workers or Redis/DB queue processing (Deferred to Phase 15).
2. **DO NOT** implement WebSocket push / Pusher server broadcasting (Deferred to Phase 16).
3. **DO NOT** alter existing React frontend code or database schema (Strict Migration Boundary).
