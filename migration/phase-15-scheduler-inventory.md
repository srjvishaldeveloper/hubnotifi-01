# Phase 15 — Scheduler Inventory

## Timezone

**Application timezone:** `UTC` (config/app.php: `'timezone' => 'UTC'`)  
**Scheduler timezone:** UTC (no per-task timezone override found)  
**Scheduler file:** `routes/console.php` (Laravel 11 style — no Kernel.php)

---

## Scheduled Tasks (11 tasks)

| # | Task | Laravel Source | Frequency | Cron Equivalent | Queue/Job | withoutOverlapping | onOneServer | Side Effects |
|---|------|---------------|-----------|-----------------|-----------|-------------------|-------------|--------------|
| 1 | Scheduler heartbeat | `Schedule::call(fn)` | Every minute | `* * * * *` | Inline (no queue) | No | No | Sets `scheduler_heartbeat` cache key with current ISO timestamp; TTL 24h |
| 2 | Launch scheduled campaigns | `Schedule::job(LaunchScheduledCampaignsJob)` | Every minute | `* * * * *` | `broadcast` queue | **Yes** | No | Finds queued campaigns with schedule_at ≤ now(); dispatches LaunchCampaignJob per campaign |
| 3 | Sync WhatsApp templates | `Schedule::call(fn)` | Daily | `0 0 * * *` | `whatsapp` queue (dispatches TemplateSyncJob per WABA) | No | No | Iterates all WhatsappBusinessAccounts; dispatches TemplateSyncJob for each |
| 4 | Dispatch scheduled social posts | `Schedule::job(DispatchScheduledPostsJob)` | Every minute | `* * * * *` | `social` queue | **Yes** | No | Finds social_posts with status=scheduled, scheduled_at ≤ now(); atomically flips to publishing; dispatches PublishSocialPostJob |
| 5 | Refresh expiring social tokens | `Schedule::job(RefreshSocialTokensJob)` | Daily at 02:00 | `0 2 * * *` | `social` queue | No | No | Chunks social accounts with token_expires_at < now+24h; calls OAuthManager.refresh(); updates or deactivates |
| 6 | Reset monthly usage meters | `Schedule::call(fn)` | Monthly on 1st at 00:05 | `5 0 1 * *` | Inline (no queue) | No | No | Deletes UsageMeter rows with period < now-2months |
| 7 | Prune inbound webhook idempotency | `Schedule::call(fn)` | Weekly | `0 0 * * 0` | Inline (no queue) | No | No | Calls WebhookIdempotencyService.prune(30); deletes records older than 30 days |
| 8 | Billing sync | `Schedule::command('billing:sync')` | Hourly | `0 * * * *` | Artisan command | **Yes** | **Yes** | Syncs all non-cancelled subscriptions with gateways (Stripe/PayPal/Paddle/Razorpay/Cashfree) |
| 9 | Billing expire trials | `Schedule::command('billing:expire-trials')` | Hourly | `0 * * * *` | Artisan command | **Yes** | **Yes** | Expires subscriptions past trial_ends_at; dispatches SubscriptionExpired event |
| 10 | Charge recurring (Tap) | `Schedule::command('billing:charge-recurring')` | Hourly | `0 * * * *` | Artisan command | **Yes** | **Yes** | Finds active Tap subscriptions with renews_at ≤ now(); merchant-initiated card charge |
| 11 | Charge recurring (Paymob) | `Schedule::command('billing:charge-recurring-paymob')` | Hourly | `0 * * * *` | Artisan command | **Yes** | **Yes** | Finds active Paymob subscriptions with renews_at ≤ now(); merchant-initiated card token charge |
| 12 | Charge recurring (MyFatoorah) | `Schedule::command('billing:charge-recurring-myfatoorah')` | Hourly | `0 * * * *` | Artisan command | **Yes** | **Yes** | Finds active MyFatoorah subscriptions with renews_at ≤ now(); merchant-initiated card token charge |
| 13 | Notify trial ending (3 days) | `Schedule::command('notifications:trial-ending --days=3')` | Daily at 09:00 | `0 9 * * *` | Artisan command | **Yes** | **Yes** | Finds trialing subscriptions ending in 3 days (not yet notified); dispatches TrialEnding event; sets trial_reminder_sent_at |
| 14 | Weekly digest emails | `Schedule::command('reports:weekly-digest')` | Mondays at 09:00 | `0 9 * * 1` | Artisan command | **Yes** | **Yes** | Chunks all Workspaces; builds weekly stats; queues WeeklyDigestMail per workspace owner (respects weekly_digest_enabled setting) |

**Total scheduled tasks: 14**

---

## withoutOverlapping Details

`withoutOverlapping()` in Laravel acquires an atomic lock before running. These tasks must NOT run concurrently:

| Task | Critical Reason |
|---|---|
| launch-scheduled-campaigns | Concurrent runs would double-dispatch campaigns |
| dispatch-social-posts | Concurrent runs would double-publish posts (mitigated partially by atomic DB update) |
| billing-sync | Concurrent subscription sync would cause race conditions on gateway calls |
| billing-expire-trials | Concurrent expiry would fire duplicate SubscriptionExpired events |
| billing-charge-recurring-* | Concurrent charges would result in double billing |
| notify-trial-ending-3d | Guarded by `trial_reminder_sent_at` but overlap still undesirable |
| weekly-digest-emails | Duplicate emails to workspace owners |

---

## onOneServer Details

Tasks marked `onOneServer()` use a distributed lock (Redis/Cache) so only one server in a cluster executes them. All billing-related and report tasks use this.

---

## Artisan Commands Used by Scheduler (5 commands)

| Command | Signature | Description |
|---|---|---|
| `billing:sync` | `billing:sync {--gateway=} {--dry-run}` | Syncs subscriptions with payment gateways |
| `billing:expire-trials` | `billing:expire-trials` | Expires past-due trial subscriptions |
| `billing:charge-recurring` | `billing:charge-recurring {--dry-run}` | MIT recurring charge for Tap |
| `billing:charge-recurring-paymob` | `billing:charge-recurring-paymob {--dry-run}` | MIT recurring charge for Paymob |
| `billing:charge-recurring-myfatoorah` | `billing:charge-recurring-myfatoorah {--dry-run}` | MIT recurring charge for MyFatoorah |
| `notifications:trial-ending` | `notifications:trial-ending {--days=3}` | Sends trial-ending reminder notifications |
| `reports:weekly-digest` | `reports:weekly-digest` | Sends weekly performance digest emails |

---

## Non-Scheduled Artisan Commands (operational, not recurring)

| Command | Signature | Purpose |
|---|---|---|
| `i18n:scan` | `i18n:scan {--sync} {--locale=}` | Scans translation keys; syncs JSON files |
| `i18n:seed-defaults` | `i18n:seed-defaults` | Seeds default locale translations |
| `inbox:duplicate-report` | `inbox:duplicate-report {--workspace=}` | Diagnostic report for duplicate conversations |
| `saas:install` | `saas:install {--fresh} {--seed}` | Full application installer |
| `webpush:vapid` | `webpush:vapid` | Generates VAPID key pair for Web Push |
| `whatsapp:register-webhook` | `whatsapp:register-webhook {--waba=} {--dry-run}` | Registers Meta webhook callback URL |
| `messenger:test-profile` | `messenger:test-profile {--account=} {--psid=}` | Diagnostic for Messenger profile |
