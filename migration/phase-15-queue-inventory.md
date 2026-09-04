# Phase 15 — Queue Inventory

## Queue Driver

**Active driver:** `database` (QUEUE_CONNECTION=database in .env)  
**Production deployment:** Docker Compose overrides to `redis` (`queue:work redis`) — see `docker-compose.queues.yml`  
**Retry_after (reservation timeout):** 90 seconds (default DB driver config)  
**Failed job driver:** `database-uuids` → table `failed_jobs`

---

## Job Inventory (22 Queue Jobs)

| # | Job | File | Trigger | Queue | Tries | MaxExceptions | Backoff (s) | Timeout (s) | Payload | Side Effects |
|---|-----|------|---------|-------|-------|---------------|-------------|-------------|---------|--------------|
| 1 | `DispatchWebhookJob` | `app/Jobs/DispatchWebhookJob.php` | `WebhookDispatchService::dispatch()` (Phase 14) | `default` | 5 | 5 | [60,300,3600,86400,86400] | — | endpoint_id, event, payload[] | Creates WebhookDelivery record; HTTP POST to endpoint; updates delivery status; releases with backoff |
| 2 | `GenerateWorkspaceExportJob` | `app/Jobs/GenerateWorkspaceExportJob.php` | `DataExportController` | `default` | — (1) | — | — | 300 | userId | Calls WorkspaceExportService; stores file; creates 72h signed URL; notifies user via WorkspaceExportReadyNotification |
| 3 | `IndexDocumentJob` | `app/Modules/AI/Jobs/IndexDocumentJob.php` | `AiKnowledgeBaseController`, `AiKnowledgeBaseApiController`, self-dispatches for sitemap children | `ai` | 3 | — | — | 120 | documentId | Extracts text, chunks, creates AiKbChunk records, calls LlmGateway.embed(), stores embeddings, updates document status |
| 4 | `ExecuteAutomationRunJob` | `app/Modules/Automation/Jobs/ExecuteAutomationRunJob.php` | `AutomationEngine`, `AutomationTriggerListener`, `AutomationApiController` | `automation` | 3 | — | — | 120 | runId | Calls AutomationEngine.executeRun(); on failure updates run status + dispatches AutomationFailed event |
| 5 | `LaunchScheduledCampaignsJob` | `app/Modules/Broadcasting/Jobs/LaunchScheduledCampaignsJob.php` | Scheduler (every minute) | `broadcast` | — (1) | — | — | — | — | Finds queued campaigns with schedule_at ≤ now(); dispatches LaunchCampaignJob for each |
| 6 | `LaunchCampaignJob` | `app/Modules/Broadcasting/Jobs/LaunchCampaignJob.php` | `CampaignController`, `CampaignApiController`, `LaunchScheduledCampaignsJob` | `broadcast` | 2 | — | — | — | campaignId | Updates campaign status to sending; resolves audience; chunks into DispatchCampaignChunkJobs |
| 7 | `DispatchCampaignChunkJob` | `app/Modules/Broadcasting/Jobs/DispatchCampaignChunkJob.php` | `LaunchCampaignJob` | `broadcast` | 2 | — | — | — | campaignId, contactIds[] | Dispatches SendCampaignMessageJob per contact with 100ms delay throttle (10 msg/s) |
| 8 | `SendCampaignMessageJob` | `app/Modules/Broadcasting/Jobs/SendCampaignMessageJob.php` | `DispatchCampaignChunkJob` | `broadcast` | 3 | — | 60 | — | campaignId, contactId | Personalizes and sends WhatsApp/SMS/Email message; updates CampaignRecipient; syncs to Inbox |
| 9 | `FinalizeCampaignJob` | `app/Modules/Broadcasting/Jobs/FinalizeCampaignJob.php` | `LaunchCampaignJob` | `broadcast` | 60 | — | — | — | campaignId, attempt | Polls for all recipients to settle; self-reschedules +1 min if pending; marks campaign completed; fires CampaignCompleted |
| 10 | `BackfillStoreOrdersJob` | `app/Modules/Ecommerce/Jobs/BackfillStoreOrdersJob.php` | `SyncStoreCustomersJob` (after customers done) | `default` | 3 | — | [30,120,300] | 120 | storeId, cursor? | Fetches orders page by page; creates EcommerceOrder records; links/creates Contacts; calls ContactEnricher; re-dispatches next page |
| 11 | `CheckAbandonedCartJob` | `app/Modules/Ecommerce/Jobs/CheckAbandonedCartJob.php` | `ProcessEcommerceWebhookJob` with `delay(30 min)` | `automation` | 2 | — | — | — | cartId | Guards idempotency via recovered_at / recovery_triggered_at; dispatches CommerceEventReceived (cart.abandoned) |
| 12 | `ProcessEcommerceWebhookJob` | `app/Modules/Ecommerce/Jobs/ProcessEcommerceWebhookJob.php` | `EcommerceWebhookController` | `automation` | 3 | — | — | 60 | storeId, topic, payload[] | Normalizes ecommerce webhook; creates/updates EcommerceOrder, EcommerceProduct, Contact; dispatches CheckAbandonedCartJob with 30-min delay for checkout events; fires CommerceEventReceived |
| 13 | `RegisterStoreWebhooksJob` | `app/Modules/Ecommerce/Jobs/RegisterStoreWebhooksJob.php` | `EcommerceStoreController.store()` | `default` | 2 | — | — | — | storeId | Calls StoreClientFactory.registerWebhooks(); updates store external_meta.webhooks_registered |
| 14 | `SyncStoreCustomersJob` | `app/Modules/Ecommerce/Jobs/SyncStoreCustomersJob.php` | `EcommerceStoreController` | `default` | 3 | — | [30,120,300] | 120 | storeId, cursor? | Fetches customer pages; upserts Contacts; calls ContactEnricher; re-dispatches next page; kicks BackfillStoreOrdersJob on completion |
| 15 | `SyncStoreProductsJob` | `app/Modules/Ecommerce/Jobs/SyncStoreProductsJob.php` | `EcommerceStoreController` | `default` | 3 | — | [30,120,300] | 120 | storeId, cursor? | Fetches product pages; upserts EcommerceProduct records; re-dispatches next page; sets products_synced_at |
| 16 | `ProcessInboundInboxMessageJob` | `app/Modules/Inbox/Jobs/ProcessInboundInboxMessageJob.php` | `MetaWebhookController` (Instagram/Messenger) | `whatsapp` | 5 | 3 | [30,60,120,240,300] | 120 | payload[], object | Routes to InstagramDriver or MessengerDriver.processWebhookPayload() |
| 17 | `ScrapeLeadsJob` | `app/Modules/Leads/Jobs/ScrapeLeadsJob.php` | `LeadController.createScrapeJob()` | `leads` | 2 | — | — | 300 | scrapeJobId | Calls GooglePlacesScraper.run(); updates LeadScrapeJob status |
| 18 | `DispatchScheduledPostsJob` | `app/Modules/Social/Jobs/DispatchScheduledPostsJob.php` | Scheduler (every minute) | `social` | — (1) | — | — | — | — | Atomically flips social_posts.status scheduled→publishing; dispatches PublishSocialPostJob per post |
| 19 | `PublishSocialPostJob` | `app/Modules/Social/Jobs/PublishSocialPostJob.php` | `DispatchScheduledPostsJob`, `SocialPostController`, `SocialPostApiController` | `social` | 3 | — | — | 120 | postId | Calls SocialPublisher.publish(); updates SocialPost status |
| 20 | `RefreshSocialTokensJob` | `app/Modules/Social/Jobs/RefreshSocialTokensJob.php` | Scheduler (daily at 02:00) | `social` | — (1) | — | — | — | — | Chunks active social accounts expiring within 24h; calls OAuthManager.refresh(); updates tokens or marks inactive |
| 21 | `ProcessInboundMessageJob` | `app/Modules/Whatsapp/Jobs/ProcessInboundMessageJob.php` | `WhatsappWebhookController` | `whatsapp` | 5 | 3 | [30,60,120,240,300] | 120 | payload[], verifyToken | Calls WhatsappDriver.processWebhookPayload(); logs on permanent failure |
| 22 | `TemplateSyncJob` | `app/Modules/Whatsapp/Jobs/TemplateSyncJob.php` | Scheduler (daily), `WhatsappEmbeddedSignupController` | `whatsapp` | 3 | — | — | — | wabaDbId | Calls CloudApiClient.fetchTemplates(); upserts WhatsappTemplate records |

---

## Queued Notifications (13 — Phase 15 Infrastructure Required)

All extend `Notification implements ShouldQueue`. They use the `default` queue unless overridden.

| Notification | Trigger (Event) | Queue | Channel |
|---|---|---|---|
| `AutomationFailedNotification` | `AutomationFailed` event | default | mail + database |
| `BillingPaymentFailedNotification` | payment failure webhook | default | mail + database |
| `CampaignCompletedNotification` | `CampaignCompleted` event | default | mail + database |
| `ConversationAssignedNotification` | conversation assignment | default | mail + database |
| `MentionedInNoteNotification` | internal note mention | default | mail + database |
| `NewMessageNotification` | inbound message | default | mail + database |
| `PlanChangedNotification` | plan change | default | mail + database |
| `SubscriptionCancelledNotification` | subscription cancelled | default | mail + database |
| `SubscriptionExpiredNotification` | trial/subscription expired | default | mail + database |
| `SubscriptionRenewedNotification` | subscription renewed | default | mail + database |
| `SubscriptionStartedNotification` | subscription started | default | mail + database |
| `TrialEndingNotification` | `TrialEnding` event | default | mail + database |
| `UserWelcomeNotification` | user registration | default | mail + database |

---

## Queue Names Summary

| Queue | Workers (Docker) | Replicas | Primary Jobs |
|---|---|---|---|
| `default` | queue-default | 2 | DispatchWebhookJob, GenerateWorkspaceExportJob, RegisterStoreWebhooksJob, SyncStoreCustomersJob, SyncStoreProductsJob, BackfillStoreOrdersJob, all Notifications |
| `whatsapp` | queue-whatsapp | 3 | ProcessInboundMessageJob, ProcessInboundInboxMessageJob, TemplateSyncJob |
| `broadcast` | queue-broadcast | 2 | LaunchScheduledCampaignsJob, LaunchCampaignJob, DispatchCampaignChunkJob, SendCampaignMessageJob, FinalizeCampaignJob |
| `ai` | queue-ai | 2 | IndexDocumentJob |
| `social` | queue-social | 2 | DispatchScheduledPostsJob, PublishSocialPostJob, RefreshSocialTokensJob |
| `leads` | queue-leads | 1 | ScrapeLeadsJob |
| `automation` | queue-automation | 2 | ExecuteAutomationRunJob, ProcessEcommerceWebhookJob, CheckAbandonedCartJob |

---

## Retry Semantics Summary

| Job | Tries | Backoff | MaxExceptions | Notes |
|---|---|---|---|---|
| `DispatchWebhookJob` | 5 | [60,300,3600,86400,86400] | 5 | Custom scheduleRetry writes next_retry_at, then releases |
| `GenerateWorkspaceExportJob` | 1 (default) | — | — | Timeout 300s |
| `IndexDocumentJob` | 3 | default | — | Timeout 120s; transient embed errors propagate for retry |
| `ExecuteAutomationRunJob` | 3 | default | — | Timeout 120s |
| `LaunchScheduledCampaignsJob` | 1 | — | — | Scheduler-driven, no retry |
| `LaunchCampaignJob` | 2 | default | — | |
| `DispatchCampaignChunkJob` | 2 | default | — | |
| `SendCampaignMessageJob` | 3 | 60s | — | |
| `FinalizeCampaignJob` | 60 | default | — | Self-reschedules +1 min until settled |
| `BackfillStoreOrdersJob` | 3 | [30,120,300] | — | Timeout 120s |
| `CheckAbandonedCartJob` | 2 | default | — | Delayed 30 min from cart creation |
| `ProcessEcommerceWebhookJob` | 3 | default | — | Timeout 60s |
| `RegisterStoreWebhooksJob` | 2 | default | — | |
| `SyncStoreCustomersJob` | 3 | [30,120,300] | — | Timeout 120s; self-chains for pagination |
| `SyncStoreProductsJob` | 3 | [30,120,300] | — | Timeout 120s; self-chains for pagination |
| `ProcessInboundInboxMessageJob` | 5 | [30,60,120,240,300] | 3 | Timeout 120s |
| `ScrapeLeadsJob` | 2 | default | — | Timeout 300s |
| `DispatchScheduledPostsJob` | 1 | — | — | Scheduler-driven |
| `PublishSocialPostJob` | 3 | default | — | Timeout 120s |
| `RefreshSocialTokensJob` | 1 | — | — | Scheduler-driven |
| `ProcessInboundMessageJob` | 5 | [30,60,120,240,300] | 3 | Timeout 120s; logs on permanent failure |
| `TemplateSyncJob` | 3 | default | — | |

---

## Delayed Jobs

| Job | Delay Mechanism | Delay Amount | Purpose |
|---|---|---|---|
| `CheckAbandonedCartJob` | `dispatch()->delay(now()->addMinutes(30))` | 30 minutes | Allow time for cart conversion before firing abandoned trigger |
| `DispatchCampaignChunkJob` → `SendCampaignMessageJob` | `delay(now()->addMilliseconds($i * 100))` | 0–N*100ms per contact | Rate limit campaign sends to ~10 msg/s |
| `FinalizeCampaignJob` (self) | `dispatch()->delay(now()->addMinute())` | 1 minute | Poll until all recipients settle |

---

## Idempotency Controls

| Job | Guard | Mechanism |
|---|---|---|
| `CheckAbandonedCartJob` | Double-dispatch | `recovered_at` + `recovery_triggered_at` null checks |
| `DispatchScheduledPostsJob` | Concurrent dispatch | Atomic UPDATE WHERE status='scheduled' before dispatch |
| `SyncStoreCustomersJob` | Pagination loop | Guards cursor == previous cursor |
| `SyncStoreProductsJob` | Pagination loop | Guards cursor == previous cursor |
| `DispatchWebhookJob` | Inbound events | `WebhookIdempotencyService` prunes records weekly |
| `ProcessInboundMessageJob` | — | WhatsApp verifyToken scopes payload |
| `FinalizeCampaignJob` | — | Campaign status checks prevent double-completion |
| `ExecuteAutomationRunJob` | — | `run.status in [cancelled, failed]` guard |
