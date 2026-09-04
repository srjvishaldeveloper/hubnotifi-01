# Phase 14 — Support + Notifications + Media + Remaining Integrations — Parity Contract

## Status: COMPLETE ✅
- 129/129 tests passing
- 0 failures
- BUILD SUCCESSFUL

---

## Route Parity

### Client Routes (Authenticated)

| Laravel Route | Java Controller | Method | Inertia Component |
|---|---|---|---|
| `GET /app/support` | `ClientSupportTicketController.index()` | GET | `client/Support/Index` |
| `POST /app/support` | `ClientSupportTicketController.store()` | POST | redirect `/app/support` |
| `GET /app/support/{id}` | `ClientSupportTicketController.show()` | GET | `client/Support/Show` |
| `POST /app/support/{id}/reply` | `ClientSupportTicketController.reply()` | POST | redirect back |
| `GET /app/notifications` | `ClientNotificationController.index()` | GET | `client/Notifications/Index` |
| `POST /app/notifications/mark-read` | `ClientNotificationController.markAllRead()` | POST | 303 redirect |
| `GET /app/notification-preferences` | `ClientNotificationController.preferences()` | GET | `client/Notifications/Preferences` |
| `POST /app/notification-preferences` | `ClientNotificationController.updatePreferences()` | POST | 303 redirect |
| `POST /app/web-push/subscribe` | `ClientWebPushController.subscribe()` | POST | JSON |
| `DELETE /app/web-push/unsubscribe` | `ClientWebPushController.unsubscribe()` | DELETE | JSON |
| `POST /app/media/upload` | `ClientMediaController.upload()` | POST | JSON |
| `GET /app/media` | `ClientMediaController.index()` | GET | `client/Media/Index` |
| `DELETE /app/media/{id}` | `ClientMediaController.destroy()` | DELETE | 303 redirect |
| `GET /webhooks` | `ClientWebhookEndpointController.index()` | GET | `client/Webhooks/Index` |
| `POST /webhooks` | `ClientWebhookEndpointController.store()` | POST | 303 redirect |
| `PUT /webhooks/{id}` | `ClientWebhookEndpointController.update()` | PUT | 303 redirect |
| `POST /webhooks/{id}/rotate-secret` | `ClientWebhookEndpointController.rotateSecret()` | POST | JSON |
| `POST /webhooks/{id}/test` | `ClientWebhookEndpointController.testDelivery()` | POST | 303 redirect |
| `GET /webhooks/{id}/deliveries` | `ClientWebhookEndpointController.deliveries()` | GET | `client/Webhooks/Deliveries` |
| `DELETE /webhooks/{id}` | `ClientWebhookEndpointController.destroy()` | DELETE | 303 redirect |
| `GET /app/audit-logs` | `ClientAuditLogController.index()` | GET | `client/AuditLogs/Index` |
| `GET /app/onboarding` | `ClientOnboardingController.index()` | GET | `client/Onboarding/Index` |
| `POST /app/onboarding/complete-step` | `ClientOnboardingController.completeStep()` | POST | JSON |

### Admin Routes (Admin Role)

| Laravel Route | Java Controller | Method | Inertia Component |
|---|---|---|---|
| `GET /admin/support` | `AdminSupportTicketController.index()` | GET | `Admin/Support/Index` |
| `GET /admin/support/{id}` | `AdminSupportTicketController.show()` | GET | `Admin/Support/Show` |
| `POST /admin/support/{id}/reply` | `AdminSupportTicketController.reply()` | POST | redirect back |
| `POST /admin/support/{id}/status` | `AdminSupportTicketController.updateStatus()` | POST | redirect back |
| `GET /admin/audit-logs` | `AdminAuditLogController.index()` | GET | `Admin/AuditLogs/Index` |
| `GET /admin/client-branding` | `AdminClientBrandingController.index()` | GET | `Admin/ClientBranding/Index` |
| `POST /admin/client-branding` | `AdminClientBrandingController.update()` | POST | 303 redirect |
| `GET /admin/email-system` | `AdminEmailSystemController.index()` | GET | `Admin/EmailSystem/Index` |
| `POST /admin/email-system` | `AdminEmailSystemController.update()` | POST | 303 redirect |
| `GET /admin/landing-page` | `AdminLandingPageController.index()` | GET | `Admin/LandingPage/Index` |
| `POST /admin/landing-page` | `AdminLandingPageController.update()` | POST | 303 redirect |
| `GET /admin/cms-pages` | `AdminCmsPageController.index()` | GET | `Admin/CmsPages/Index` |
| `POST /admin/cms-pages` | `AdminCmsPageController.store()` | POST | 303 redirect |
| `PUT /admin/cms-pages/{id}` | `AdminCmsPageController.update()` | PUT | 303 redirect |
| `DELETE /admin/cms-pages/{id}` | `AdminCmsPageController.destroy()` | DELETE | 303 redirect |
| `GET /admin/locales` | `AdminLocaleController.index()` | GET | `Admin/Locales/Index` |
| `POST /admin/locales` | `AdminLocaleController.store()` | POST | 303 redirect |
| `GET /admin/translations/{locale}` | `AdminTranslationController.index()` | GET | `Admin/Translations/Index` |
| `POST /admin/translations/{locale}` | `AdminTranslationController.update()` | POST | 303 redirect |
| `GET /admin/system-settings` | `AdminSystemSettingsController.index()` | GET | `Admin/SystemSettings/Index` |
| `POST /admin/system-settings` | `AdminSystemSettingsController.update()` | POST | 303 redirect |
| `POST /admin/impersonate/{userId}` | `AdminImpersonationController.start()` | POST | 303 redirect |
| `POST /admin/stop-impersonation` | `AdminImpersonationController.stop()` | POST | 303 redirect |
| `GET /admin/cron-setup` | `AdminCronSetupController.index()` | GET | `Admin/CronSetup/Index` |

### Public Routes (No Auth)

| Laravel Route | Java Controller | Method | Inertia Component |
|---|---|---|---|
| `GET /pages/{slug}` | `PublicCmsPageController.show()` | GET | `Public/Page` |

### Public API v1 Routes

| Laravel Route | Java Controller | Method | Response |
|---|---|---|---|
| `GET /api/v1/me` | `MeApiController.me()` | GET | JSON |
| `GET /api/v1/tokens` | `TokenApiController.index()` | GET | JSON |
| `POST /api/v1/tokens` | `TokenApiController.create()` | POST | JSON |
| `DELETE /api/v1/tokens/{id}` | `TokenApiController.revoke()` | DELETE | JSON |
| `GET /api/v1/notifications` | `NotificationApiController.index()` | GET | JSON |
| `GET /api/v1/webhooks` | `OutboundWebhookApiController.index()` | GET | JSON |
| `GET /api/v1/audit-logs` | `AuditLogApiController.index()` | GET | JSON |

---

## Entity Parity (19 JPA Entities)

| Laravel Model | JPA Entity | Table |
|---|---|---|
| `SupportTicket` | `SupportTicket.java` | `support_tickets` |
| `SupportReply` | `SupportReply.java` | `support_replies` |
| `NotificationPreference` | `NotificationPreference.java` | `notification_preferences` |
| `PushSubscription` | `PushSubscription.java` | `push_subscriptions` |
| `Media` | `Media.java` | `media` |
| `WebhookEndpoint` | `WebhookEndpoint.java` | `webhook_endpoints` |
| `WebhookDelivery` | `WebhookDelivery.java` | `webhook_deliveries` |
| `IntegrationConfig` | `IntegrationConfig.java` | `integration_configs` |
| `IntegrationAuditLog` | `IntegrationAuditLog.java` | `integration_audit_logs` |
| `SocialAccount` | `SocialAccount.java` | `social_accounts` |
| `SocialPost` | `SocialPost.java` | `social_posts` |
| `SocialPostAccount` | `SocialPostAccount.java` | `social_post_accounts` |
| `AuditLog` | `AuditLog.java` | `audit_logs` |
| `SystemSetting` | `SystemSetting.java` | `system_settings` |
| `ClientSetting` | `ClientSetting.java` | `client_settings` |
| `CmsPage` | `CmsPage.java` | `cms_pages` |
| `Locale` | `Locale.java` | `locales` |
| `Translation` | `Translation.java` | `translations` |
| `OnboardingStep` | `OnboardingStep.java` | `onboarding_steps` |

---

## Service Parity

| Laravel Service | Java Service | Key Behavior |
|---|---|---|
| `StorageManager` | `StorageManagerService.java` | Resolves disk configs (public, s3, digitalocean, wasabi) from DB |
| `MediaService` | `MediaService.java` | Upload to disk, UUID paths, user plan storage_gb quota |
| `WebhookDispatcher` | `WebhookDispatchService.java` | HMAC-SHA256 dispatch with delivery logging |
| `AuditLogger` | `AuditLogService.java` | Structured audit logging to `audit_logs` table |
| `OneSignalService` | `OneSignalService.java` | OneSignal push notification integration |
| `WebPushService` | `WebPushService.java` | Web Push subscription management |

---

## Security Parity

| Route Pattern | Access | Notes |
|---|---|---|
| `/pages/**` | Public (permitAll) | CMS pages accessible without auth |
| `/webhooks/**` | CSRF-exempt, authenticated | Client webhook management |
| `/app/**` | CLIENT role | Client application routes |
| `/admin/**` | ADMIN role | Admin panel routes |
| `/api/v1/**` | Token-authenticated | Sanctum-compatible API |

---

## Deferred to Later Phases

| Item | Deferred To |
|---|---|
| Queue Infrastructure (DispatchWebhookJob, GenerateWorkspaceExportJob) | Phase 15 |
| Background Workers / Scheduler cron | Phase 15 |
| Realtime WebSockets / Pusher / Echo broadcasting | Phase 16 |
