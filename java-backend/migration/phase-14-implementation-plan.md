# Phase 14 — Implementation Plan: Support, Notifications, Media & Remaining Integrations

## Scope
Migrate all remaining synchronous business functionality from Laravel/PHP to Java 21 + Spring Boot, focusing on:
1. Support / Helpdesk tickets & replies.
2. Notifications, unread counts, preferences, and WebPush subscriptions.
3. Media / File Storage manager, quota enforcement, and upload/delete endpoints.
4. Custom outgoing webhook endpoint management, HMAC-SHA256 signature dispatch, and delivery auditing.
5. Integrations & Social posting configurations.
6. Audit logging, client branding, system settings, SMTP setup, CMS pages, i18n/locales, and onboarding wizard.
7. Public REST API v1 endpoints for Sanctum authenticated access.

---

## Features to Migrate
- **Support Module:** `SupportTicket`, `SupportReply`, Client & Admin controllers for ticket CRUD, status updates, and replies.
- **Notifications Module:** `NotificationPreference`, `PushSubscription`, Bell notification dropdown API, unread count API, mark read/all-read, WebPush subscription APIs.
- **Media Module:** `Media` model, `StorageManager` (Local, S3, DigitalOcean Spaces, Wasabi driver resolution), `MediaService`, file upload validations, user storage quota calculation.
- **Outbound Webhooks:** `WebhookEndpoint`, `WebhookDelivery`, HMAC-SHA256 dispatch, secret rotation, delivery log listing.
- **Integrations & Social:** `IntegrationConfig`, `IntegrationAuditLog`, `SocialAccount`, `SocialPost`, `SocialPostAccount`, API tokens.
- **System Settings & CMS:** `SystemSetting`, `ClientSetting`, `CmsPage`, `Locale`, `Translation`, `OnboardingStep`, `AuditLog`, SMTP config & test email sending, impersonation.
- **Public API v1:** Sanctum guarded REST controllers for account, tokens, audit logs, notifications, and webhooks.

---

## Features Deferred
- **Queue Workers & Background Processing:** `DispatchWebhookJob`, `GenerateWorkspaceExportJob`, queue retry backoff, scheduler (Deferred to **Phase 15**).
- **Realtime Broadcasting:** WebSockets, Pusher settings, Echo presence, `TypingChanged` events (Deferred to **Phase 16**).
- **End-to-End Test Suite:** Complete React UI + Java E2E test execution (Deferred to **Phase 17**).

---

## Existing Java Infrastructure to Reuse
- `WorkspaceContext`: Current workspace resolution.
- `Spring Security` & `Sanctum`: `CustomUserDetails`, `AdminUserDetails`, Bearer token authentication.
- `InertiaRenderer` & `Inertia`: `Inertia.render()`, `Inertia.redirect()`, flash messages.
- JPA repositories & converters (`JsonAttributeConverter`).

---

## Entities to Implement
- `SupportTicket.java`
- `SupportReply.java`
- `NotificationPreference.java`
- `PushSubscription.java`
- `Media.java`
- `WebhookEndpoint.java`
- `WebhookDelivery.java`
- `IntegrationConfig.java`
- `IntegrationAuditLog.java`
- `SocialAccount.java`
- `SocialPost.java`
- `SocialPostAccount.java`
- `AuditLog.java`
- `SystemSetting.java`
- `ClientSetting.java`
- `CmsPage.java`
- `Locale.java`
- `Translation.java`
- `OnboardingStep.java`

---

## Repositories to Implement
- `SupportTicketRepository`
- `SupportReplyRepository`
- `NotificationPreferenceRepository`
- `PushSubscriptionRepository`
- `MediaRepository`
- `WebhookEndpointRepository`
- `WebhookDeliveryRepository`
- `IntegrationConfigRepository`
- `IntegrationAuditLogRepository`
- `SocialAccountRepository`
- `SocialPostRepository`
- `SocialPostAccountRepository`
- `AuditLogRepository`
- `SystemSettingRepository`
- `ClientSettingRepository`
- `CmsPageRepository`
- `LocaleRepository`
- `TranslationRepository`
- `OnboardingStepRepository`

---

## Services to Implement
- `StorageManagerService.java`: Resolves storage disk driver (`public`, `s3`, `storage_do`, `storage_wasabi`) from `integration_configs`.
- `MediaService.java`: File upload processing, UUID pathing, storage quota checks (`storage_gb`).
- `WebhookDispatchService.java`: HMAC-SHA256 request signing & HTTP dispatch.
- `AuditLogService.java`: Event audit logging.
- `OneSignalService.java` & `WebPushService.java`: Push dispatches.

---

## Controllers to Implement
- **Client Controllers:**
  - `ClientSupportTicketController` (`/support`)
  - `ClientNotificationController` (`/notifications`, `/notifications/recent`, `/notifications/unread-count`, `/notifications/mark-all-read`, `/notifications/preferences`)
  - `ClientWebPushController` (`/webpush/subscribe`, `/webpush/unsubscribe`)
  - `ClientMediaController` (`/media`)
  - `ClientWebhookEndpointController` (`/webhooks`)
  - `ClientAuditLogController` (`/app/audit-logs`)
  - `ClientOnboardingController` (`/app/onboarding`)
- **Admin Controllers:**
  - `AdminSupportTicketController` (`/admin/support`)
  - `AdminAuditLogController` (`/admin/audit-logs`)
  - `AdminClientBrandingController` (`/admin/client-branding`)
  - `AdminEmailSystemController` (`/admin/email-system`)
  - `AdminLandingPageController` (`/admin/landing-page`)
  - `AdminCmsPageController` (`/admin/cms-pages`)
  - `AdminLocaleController` (`/admin/locales`)
  - `AdminTranslationController` (`/admin/translations`)
  - `AdminSystemSettingsController` (`/admin/system-settings`)
  - `AdminImpersonationController` (`/admin/impersonate`)
  - `AdminCronSetupController` (`/admin/cron-setup`)
- **Public & API Controllers:**
  - `PublicCmsPageController` (`/pages/{slug}`)
  - `MeApiController`, `TokenApiController`, `NotificationApiController`, `OutboundWebhookApiController`, `AuditLogApiController` (`/api/v1/...`)

---

## Testing Strategy
Create [`Phase14SupportNotificationsMediaParityIntegrationTest.java`](file:///d:/hubnotification/java-backend/src/test/java/com/whatsmine/support/Phase14SupportNotificationsMediaParityIntegrationTest.java) with integration test cases covering:
1. Support ticket lifecycle (Client create ticket, Admin view ticket & reply, status change).
2. Notification endpoints (bell dropdown 10 recent, unread count, mark as read, preference update).
3. File upload & quota checks (Media upload, quota limit enforcement, file listing, deletion).
4. Outbound webhook endpoint creation, secret rotation, HMAC delivery simulation.
5. Audit log generation & search.
6. Settings & i18n locale loading.
7. Public REST API v1 endpoints guarded by Sanctum tokens.

---

## Risks & Mitigation
- **Risk:** Java `Map.of()` with null values throwing `NullPointerException`.
  - **Mitigation:** Use `LinkedHashMap` or null-safe builders for optional settings/limits.
- **Risk:** File upload mime type / XSS vulnerabilities.
  - **Mitigation:** Enforce explicit MIME allow-list (`jpg,jpeg,png,gif,webp,pdf,doc,docx,xls,xlsx,ppt,pptx,csv,txt,mp3,wav,ogg,m4a,mp4,webm,mov`) and 50MB max file size.
- **Risk:** Storage driver credentials missing.
  - **Mitigation:** Gracefully fall back to local `public` disk if S3 / DigitalOcean credentials are not configured in `integration_configs`.

---

## Files Expected to Change / Create
- **JPA Models:** 19 new files in `com.whatsmine.model`
- **Repositories:** 19 new files in `com.whatsmine.repository`
- **Services:** 5 new files in `com.whatsmine.service`
- **Controllers:** 19 new files in `com.whatsmine.controller`
- **Tests:** 1 new integration test file `Phase14SupportNotificationsMediaParityIntegrationTest.java`
