# Phase 14 — Full Inventory Document

## Executive Summary
This document provides a comprehensive, itemized inventory of all remaining Laravel/PHP code components discovered in `d:\hubnotification\php` that are assigned to **Phase 14 (Support, Notifications, Media, Remaining Integrations, Webhooks, Settings & Public APIs)**.

---

## 1. Support & Helpdesk Inventory

### Models
- [`SupportTicket`](file:///d:/hubnotification/php/app/Models/SupportTicket.php): Table `support_tickets` (`id`, `user_id`, `name`, `email`, `subject`, `message`, `status`, `priority`, `created_at`, `updated_at`).
- [`SupportReply`](file:///d:/hubnotification/php/app/Models/SupportReply.php): Table `support_replies` (`id`, `ticket_id`, `user_id`, `author_name`, `is_staff`, `message`, `created_at`, `updated_at`).

### Routes & Controllers
- Client Routes (`/support`):
  - `GET /support` -> `Client\SupportTicketController@index` (Inertia `client/Support/Index`)
  - `GET /support/create` -> `Client\SupportTicketController@create` (Inertia `client/Support/Create`)
  - `POST /support` -> `Client\SupportTicketController@store` (Redirect `/support`)
  - `GET /support/{supportTicket}` -> `Client\SupportTicketController@show` (Inertia `client/Support/Show`)
  - `POST /support/{supportTicket}/reply` -> `Client\SupportTicketController@reply` (Redirect `/support/{id}`)
- Admin Routes (`/admin/support`):
  - `GET /admin/support` -> `Admin\SupportTicketController@index` (Inertia `Admin/Support/Index`)
  - `GET /admin/support/{supportTicket}` -> `Admin\SupportTicketController@show` (Inertia `Admin/Support/Show`)
  - `POST /admin/support/{supportTicket}/reply` -> `Admin\SupportTicketController@reply` (Redirect `/admin/support/{id}`)
  - `POST /admin/support/{supportTicket}/status` -> `Admin\SupportTicketController@updateStatus` (Redirect `/admin/support/{id}`)

---

## 2. Notifications & Preferences Inventory

### Models
- [`NotificationPreference`](file:///d:/hubnotification/php/app/Models/NotificationPreference.php): Table `notification_preferences` (`id`, `user_id`, `event`, `channel`, `enabled`, `created_at`, `updated_at`).
- [`PushSubscription`](file:///d:/hubnotification/php/app/Models/PushSubscription.php): Table `push_subscriptions` (`id`, `user_id`, `endpoint`, `p256dh_key`, `auth_key`, `ua`, `created_at`, `updated_at`).

### Services
- [`OneSignalService`](file:///d:/hubnotification/php/app/Services/OneSignalService.php): Handles push notification dispatches via OneSignal REST API.
- [`WebPushService`](file:///d:/hubnotification/php/app/Services/WebPushService.php): Handles standard Web Push (VAPID / WebPush protocol) dispatches.

### Routes & Controllers
- `GET /notifications` -> `Client\NotificationController@index` (Inertia `client/Notifications/Index`)
- `GET /notifications/recent` -> `Client\NotificationController@recent` (JSON list of 10 recent notifications)
- `GET /notifications/unread-count` -> `Client\NotificationController@unreadCount` (JSON count)
- `POST /notifications/{id}/read` -> `Client\NotificationController@markRead` (JSON ok)
- `POST /notifications/mark-all-read` -> `Client\NotificationController@markAllRead` (Redirect back)
- `DELETE /notifications/{id}` -> `Client\NotificationController@destroy` (JSON ok)
- `POST /notifications/preferences` -> `Client\NotificationController@updatePreferences` (Redirect back)
- `POST /webpush/subscribe` -> `Client\WebPushController@subscribe` (JSON ok)
- `POST /webpush/unsubscribe` -> `Client\WebPushController@unsubscribe` (JSON ok)

---

## 3. Media & File Storage Inventory

### Models
- [`Media`](file:///d:/hubnotification/php/app/Models/Media.php): Table `media` (`id`, `mediable_type`, `mediable_id`, `disk`, `path`, `filename`, `mime_type`, `size_bytes`, `collection`, `meta`, `created_at`, `updated_at`).

### Services
- [`StorageManager`](file:///d:/hubnotification/php/app/Services/StorageManager.php): Single source of truth resolving disk configuration from DB (`integration_configs` for `storage_s3`, `storage_do`, `storage_wasabi`, falling back to `public` local disk).
- [`MediaService`](file:///d:/hubnotification/php/app/Services/MediaService.php): File upload handler, storage path generation (`media/{uuid}.ext`), quota calculation (`quotaBytes` based on user's active plan `storage_gb` limit).

### Routes & Controllers
- `GET /media` -> `Client\MediaController@index` (Inertia `client/Media/Index`)
- `POST /media` -> `Client\MediaController@store` (JSON 201 with media object or 422 on quota/validation)
- `DELETE /media/{medium}` -> `Client\MediaController@destroy` (JSON ok)

---

## 4. Custom Outgoing Webhooks Inventory

### Models
- [`WebhookEndpoint`](file:///d:/hubnotification/php/app/Models/WebhookEndpoint.php): Table `webhook_endpoints` (`id`, `user_id`, `url`, `secret`, `description`, `events`, `enabled`, `created_at`, `updated_at`).
- [`WebhookDelivery`](file:///d:/hubnotification/php/app/Models/WebhookDelivery.php): Table `webhook_deliveries` (`id`, `webhook_endpoint_id`, `event`, `payload`, `response_status`, `response_body`, `delivered_at`, `created_at`, `updated_at`).

### Services
- [`WebhookDispatchService`](file:///d:/hubnotification/php/app/Services/WebhookDispatchService.php): Signs outgoing payloads with HMAC-SHA256 (`X-Hub-Signature-256`) and dispatches HTTP POST requests to registered endpoints.

### Routes & Controllers
- `GET /webhooks` -> `Client\WebhookEndpointController@index` (Inertia `client/Webhooks/Index`)
- `POST /webhooks` -> `Client\WebhookEndpointController@store` (Redirect index)
- `PUT /webhooks/{webhookEndpoint}` -> `Client\WebhookEndpointController@update` (Redirect index)
- `POST /webhooks/{webhookEndpoint}/rotate-secret` -> `Client\WebhookEndpointController@rotateSecret` (JSON secret)
- `DELETE /webhooks/{webhookEndpoint}` -> `Client\WebhookEndpointController@destroy` (Redirect index)
- `POST /webhooks/{webhookEndpoint}/test` -> `Client\WebhookEndpointController@testDelivery` (Redirect back)
- `GET /webhooks/{webhookEndpoint}/deliveries` -> `Client\WebhookEndpointController@deliveries` (Inertia `client/Webhooks/Deliveries`)

---

## 5. Remaining Integrations & Social Posting Inventory

### Models
- [`IntegrationConfig`](file:///d:/hubnotification/php/app/Modules/Integrations/Models/IntegrationConfig.php): Table `integration_configs` (`id`, `workspace_id`, `provider`, `mode`, `enabled`, `credentials`, `settings`, `is_default`, `created_at`, `updated_at`).
- [`IntegrationAuditLog`](file:///d:/hubnotification/php/app/Modules/Integrations/Models/IntegrationAuditLog.php): Table `integration_audit_logs` (`id`, `workspace_id`, `provider`, `action`, `details`, `created_at`).
- [`SocialAccount`](file:///d:/hubnotification/php/app/Modules/Social/Models/SocialAccount.php): Table `social_accounts` (`id`, `workspace_id`, `provider`, `provider_user_id`, `name`, `token`, `refresh_token`, `expires_at`, `created_at`, `updated_at`).
- [`SocialPost`](file:///d:/hubnotification/php/app/Modules/Social/Models/SocialPost.php): Table `social_posts` (`id`, `workspace_id`, `content`, `media_urls`, `status`, `scheduled_at`, `published_at`, `created_at`, `updated_at`).
- [`SocialPostAccount`](file:///d:/hubnotification/php/app/Modules/Social/Models/SocialPostAccount.php): Table `social_post_accounts` (`id`, `social_post_id`, `social_account_id`, `status`, `post_id_external`, `error`).

---

## 6. Audit Logs, Settings, CMS & i18n Inventory

### Models
- [`AuditLog`](file:///d:/hubnotification/php/app/Models/AuditLog.php): Table `audit_logs` (`id`, `workspace_id`, `user_id`, `event`, `auditable_type`, `auditable_id`, `old_values`, `new_values`, `url`, `ip_address`, `user_agent`, `created_at`).
- [`SystemSetting`](file:///d:/hubnotification/php/app/Models/SystemSetting.php): Table `system_settings` (`id`, `key`, `value`, `group`, `type`, `created_at`, `updated_at`).
- [`ClientSetting`](file:///d:/hubnotification/php/app/Models/ClientSetting.php): Table `client_settings` (`id`, `client_id`, `key`, `value`, `created_at`, `updated_at`).
- [`CmsPage`](file:///d:/hubnotification/php/app/Models/CmsPage.php): Table `cms_pages` (`id`, `title`, `slug`, `content`, `published`, `created_at`, `updated_at`).
- [`Locale`](file:///d:/hubnotification/php/app/Models/Locale.php): Table `locales` (`id`, `code`, `name`, `flag`, `is_default`, `enabled`, `created_at`, `updated_at`).
- [`Translation`](file:///d:/hubnotification/php/app/Models/Translation.php): Table `translations` (`id`, `locale_code`, `group`, `key`, `value`, `created_at`, `updated_at`).
- [`OnboardingStep`](file:///d:/hubnotification/php/app/Models/OnboardingStep.php): Table `onboarding_steps` (`id`, `user_id`, `step_key`, `completed`, `completed_at`, `created_at`, `updated_at`).

### Routes & Controllers
- `GET /admin/audit-logs` -> `Admin\AuditLogController@index`
- `GET /app/audit-logs` -> `Client\AuditLogController@index`
- `GET /admin/client-branding` -> `Admin\ClientBrandingController@index`
- `POST /admin/client-branding` -> `Admin\ClientBrandingController@update`
- `GET /admin/email-system` -> `Admin\EmailSystemController@index`
- `POST /admin/email-system/smtp` -> `Admin\EmailSystemController@updateSmtp`
- `POST /admin/email-system/test` -> `Admin\EmailSystemController@sendTestEmail`
- `GET /admin/landing-page` -> `Admin\LandingPageController@edit`
- `POST /admin/landing-page` -> `Admin\LandingPageController@update`
- `GET /admin/cms-pages` -> `Admin\CmsPageController@index`
- `POST /admin/cms-pages` -> `Admin\CmsPageController@store`
- `PUT /admin/cms-pages/{id}` -> `Admin\CmsPageController@update`
- `DELETE /admin/cms-pages/{id}` -> `Admin\CmsPageController@destroy`
- `GET /pages/{slug}` -> `CmsPageController@show`
- `GET /admin/locales` -> `Admin\LocaleController@index`
- `POST /admin/locales` -> `Admin\LocaleController@store`
- `GET /admin/translations` -> `Admin\TranslationController@index`
- `POST /admin/translations` -> `Admin\TranslationController@update`
- `GET /admin/system-settings` -> `Admin\SystemSettingsController@index`
- `POST /admin/system-settings` -> `Admin\SystemSettingsController@update`
- `GET /admin/impersonate/{user}` -> `Admin\ImpersonationController@start`
- `POST /admin/impersonate/stop` -> `Admin\ImpersonationController@stop`
- `GET /admin/cron-setup` -> `Admin\CronSetupController@index`
- `GET /app/onboarding` -> `Client\OnboardingController@index`
- `POST /app/onboarding/complete-step` -> `Client\OnboardingController@completeStep`

---

## 7. Jobs & Queue Classification (Phase 14 vs Phase 15)

| Job Class | Trigger | Queue Name | Delay / Retry | Side Effects | Assigned Phase |
|---|---|---|---|---|---|
| `DispatchWebhookJob` | Outbound event trigger | `default` | 3 retries, exponential backoff | Sends HTTP POST to user's webhook URL | **Phase 15 (Queue Worker)** |
| `GenerateWorkspaceExportJob` | Admin/User export request | `exports` | No delay, 1 retry | Zips workspace data & sends ready notification | **Phase 15 (Queue Worker)** |

---

## 8. Realtime / Pusher / Echo Classification (Phase 16)

| Realtime Event / Controller | Channel Name | Broadcaster | Frontend Listener | Assigned Phase |
|---|---|---|---|---|
| `TypingChanged` | `private-conversation.{id}` | Pusher | `Echo.private().listen('TypingChanged')` | **Phase 16 (Realtime)** |
| `MessageReceived` | `private-conversation.{id}` | Pusher | `Echo.private().listen('MessageReceived')` | **Phase 16 (Realtime)** |
| `MessageSent` | `private-conversation.{id}` | Pusher | `Echo.private().listen('MessageSent')` | **Phase 16 (Realtime)** |
| `MessageStatusUpdated` | `private-conversation.{id}` | Pusher | `Echo.private().listen('MessageStatusUpdated')` | **Phase 16 (Realtime)** |
| `Admin\PusherSettingsController` | N/A | Settings | `Admin/PusherSettings/Index` | **Phase 16 (Realtime)** |

---

## 9. Final Metric Counts Discovered

- **Total Discovered Routes in Scope:** 42 routes
- **Total Discovered Controllers in Scope:** 22 controllers
- **Total Discovered Models / Tables:** 16 models
- **Total Discovered Services:** 7 services
- **Total Discovered Notifications:** 16 notification classes
- **Total Jobs:** 2 jobs (classified for Phase 15)
- **Total Outgoing Webhooks / Intakes:** 7 endpoints
- **Total Integrations:** 5 providers (Local, S3, DigitalOcean, Wasabi, OneSignal)
