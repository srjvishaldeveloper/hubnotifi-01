# Phase 15 — Scope Classification

## Classification Legend

- **A — Phase 15 (Queue Infrastructure):** Core queue/worker/scheduler infrastructure
- **B — Phase 16 (Realtime):** Jobs that exist purely for Pusher/Echo/WebSocket behavior
- **C — Existing Module (Reuse):** Business logic already migrated in Phases 7–14; only async wiring needed
- **D — Phase 14 connector:** Sync behavior migrated in Phase 14, but async execution channel not yet wired
- **E — Obsolete/Unused:** Not needed in Java migration

---

## Job Classification

| Job | Class | Reason |
|---|---|---|
| `DispatchWebhookJob` | **A + D** | Core Phase 15 queue infra; business logic in `WebhookDispatchService` (Phase 14). Phase 15 provides async dispatch; service already exists |
| `GenerateWorkspaceExportJob` | **A + C** | Queue infra. `WorkspaceExportService` must be created/verified — belongs to Phase 15 as it was deferred |
| `IndexDocumentJob` | **A + C** | Queue infra. Business logic: `DocumentIndexer`/`LlmGateway`/`EmbeddingStore` exist (Phase 11). Phase 15 provides async dispatch |
| `ExecuteAutomationRunJob` | **A + C** | Queue infra. `AutomationEngine` exists (Phase 10). Phase 15 wires async dispatch |
| `LaunchScheduledCampaignsJob` | **A + C** | Queue infra (scheduler-driven). Business logic: `CampaignService` exists (Phase 9) |
| `LaunchCampaignJob` | **A + C** | Queue infra. Uses `CampaignService` (Phase 9). Phase 15 provides async dispatch |
| `DispatchCampaignChunkJob` | **A + C** | Queue infra (fan-out). Dispatches `SendCampaignMessageJob` with delay for rate limiting |
| `SendCampaignMessageJob` | **A + C** | Queue infra. Uses `CampaignService`/`WhatsAppApiClient`/`MailService` (Phases 7,9). Actual send logic already exists |
| `FinalizeCampaignJob` | **A + C** | Queue infra (polling loop). Uses `CampaignService` (Phase 9) |
| `BackfillStoreOrdersJob` | **A + C** | Queue infra. Uses ecommerce services (Phase 12). Paginated self-chaining pattern is Phase 15 infrastructure concern |
| `CheckAbandonedCartJob` | **A + C** | Queue infra (delayed job). Uses cart/order services (Phase 12). Phase 15 provides 30-min delayed dispatch |
| `ProcessEcommerceWebhookJob` | **A + C** | Queue infra. Uses `PayloadNormalizer`, `ContactEnricher` (Phase 12) |
| `RegisterStoreWebhooksJob` | **A + C** | Queue infra. Uses `StoreClientFactory` (Phase 12) |
| `SyncStoreCustomersJob` | **A + C** | Queue infra. Uses `ContactService`, `ContactEnricher` (Phase 12). Paginated self-chaining |
| `SyncStoreProductsJob` | **A + C** | Queue infra. Uses `PayloadNormalizer` (Phase 12). Paginated self-chaining |
| `ProcessInboundInboxMessageJob` | **A + C** | Queue infra. Uses `InstagramDriver`, `MessengerDriver` (Phase 8). Phase 15 provides async dispatch |
| `ScrapeLeadsJob` | **A + C** | Queue infra. Uses `GooglePlacesScraper` (Phase 12). Phase 15 provides async dispatch |
| `DispatchScheduledPostsJob` | **A** | Scheduler job. Business logic inline (atomic DB flip). Self-contained; belongs in Phase 15 |
| `PublishSocialPostJob` | **A + C** | Queue infra. Uses `SocialPublisher` (Phase 14 Social) |
| `RefreshSocialTokensJob` | **A + C** | Queue infra. Uses `OAuthManager` (Phase 14 Social) |
| `ProcessInboundMessageJob` | **A + C** | Queue infra. Uses `WhatsappDriver` (Phase 7). Phase 15 provides async dispatch |
| `TemplateSyncJob` | **A + C** | Queue infra. Uses `CloudApiClient` (Phase 7). Phase 15 provides async dispatch |

---

## Queued Notifications Classification

All 13 Laravel Notifications that implement `ShouldQueue` are **D — Phase 14 connectors**.

The notification *content* and *channels* were mapped in Phase 14. Phase 15 provides the queue infrastructure that enables them to execute asynchronously. In Java, these translate to `@Async` service calls or queue-backed Spring events.

| Notification | Classification | Java Mechanism |
|---|---|---|
| `UserWelcomeNotification` | D | `@Async` email via `MailService` |
| `TrialEndingNotification` | D | `@Async` email via `MailService` + DB notification |
| `SubscriptionStartedNotification` | D | `@Async` email + DB notification |
| `SubscriptionRenewedNotification` | D | `@Async` email + DB notification |
| `SubscriptionExpiredNotification` | D | `@Async` email + DB notification |
| `SubscriptionCancelledNotification` | D | `@Async` email + DB notification |
| `PlanChangedNotification` | D | `@Async` email + DB notification |
| `BillingPaymentFailedNotification` | D | `@Async` email + DB notification |
| `CampaignCompletedNotification` | D | `@Async` email + DB notification |
| `ConversationAssignedNotification` | D | `@Async` DB notification + push |
| `MentionedInNoteNotification` | D | `@Async` DB notification + push |
| `NewMessageNotification` | D | `@Async` DB notification + push |
| `AutomationFailedNotification` | D | `@Async` email + DB notification |

---

## Scheduled Tasks Classification

| Task | Classification | Notes |
|---|---|---|
| Scheduler heartbeat | **A** | Pure infra; cache write |
| Launch scheduled campaigns | **A + C** | Scheduler wiring; reuses Phase 9 services |
| Sync WhatsApp templates | **A + C** | Scheduler wiring; reuses Phase 7 services |
| Dispatch scheduled social posts | **A + C** | Scheduler wiring; reuses Phase 14 social services |
| Refresh expiring social tokens | **A + C** | Scheduler wiring; reuses Phase 14 social services |
| Reset monthly usage meters | **A** | Inline DB cleanup; Phase 15 scheduler |
| Prune webhook idempotency | **A** | Inline DB cleanup; Phase 15 scheduler |
| billing:sync | **A + C** | Scheduler wiring; reuses Phase 13 billing services |
| billing:expire-trials | **A + C** | Scheduler wiring; reuses Phase 13 billing services |
| billing:charge-recurring (Tap) | **A + C** | Scheduler wiring; reuses Phase 13 billing services |
| billing:charge-recurring (Paymob) | **A + C** | Scheduler wiring; reuses Phase 13 billing services |
| billing:charge-recurring (MyFatoorah) | **A + C** | Scheduler wiring; reuses Phase 13 billing services |
| notifications:trial-ending | **A + C** | Scheduler wiring; reuses Phase 13 billing + Phase 14 notification services |
| reports:weekly-digest | **A + C** | Scheduler wiring; builds stats and queues digest email |

---

## Phase 16 Reservations

The following are explicitly deferred to Phase 16:

| Item | Reason |
|---|---|
| `reverb` Docker service (`php artisan reverb:start`) | Laravel Reverb = WebSocket server. Phase 16 scope. |
| Any broadcast events using `ShouldBroadcast` | Realtime channel delivery. Phase 16 scope. |
| Laravel Echo / Pusher event delivery | Phase 16 scope. |

---

## Not Duplicated (Existing Services Reused)

| Existing Java Class | Reused By |
|---|---|
| `WhatsAppApiClient` | `ProcessInboundMessageJob`, `TemplateSyncJob`, `SendCampaignMessageJob` |
| `WhatsappDriver` | `ProcessInboundMessageJob` |
| `AutomationEngine` | `ExecuteAutomationRunJob` |
| `LlmGateway`, `DocumentIndexer` | `IndexDocumentJob` |
| `CampaignService` | `LaunchCampaignJob`, `FinalizeCampaignJob`, `SendCampaignMessageJob` |
| `BillingService`, `BillingGatewayRegistry` | Billing scheduler commands |
| `WebhookDispatchService` | `DispatchWebhookJob` |
| `GooglePlacesScraper` | `ScrapeLeadsJob` |
| `SocialPublisher`, `OAuthManager` | `PublishSocialPostJob`, `RefreshSocialTokensJob` |
| `InstagramDriver`, `MessengerDriver` | `ProcessInboundInboxMessageJob` |
| `StoreClientFactory`, `PayloadNormalizer`, `ContactEnricher` | Ecommerce jobs |
