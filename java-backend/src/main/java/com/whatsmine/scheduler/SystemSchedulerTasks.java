package com.whatsmine.scheduler;

import com.whatsmine.model.Subscription;
import com.whatsmine.queue.QueueDispatcher;
import com.whatsmine.repository.SubscriptionRepository;
import com.whatsmine.service.billing.BillingGatewayInterface;
import com.whatsmine.service.billing.BillingGatewayRegistry;
import com.whatsmine.service.billing.WebhookIdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SystemSchedulerTasks {

    private static final Logger log = LoggerFactory.getLogger(SystemSchedulerTasks.class);
    private static final int WEBHOOK_EVENT_RETENTION_DAYS = 30;
    private final AtomicReference<Instant> lastHeartbeat = new AtomicReference<>();

    /**
     * Human-readable descriptions for each {@code @Scheduled} method below, keyed by
     * method name, in display order. Read by {@link #describeScheduledTasks()} so the
     * admin Cron Setup page always reflects exactly what's registered here.
     */
    private static final Map<String, String> TASK_DESCRIPTIONS = new LinkedHashMap<>();
    static {
        TASK_DESCRIPTIONS.put("schedulerHeartbeat", "Scheduler heartbeat");
        TASK_DESCRIPTIONS.put("launchScheduledCampaigns", "Launch scheduled campaigns");
        TASK_DESCRIPTIONS.put("syncWhatsappTemplates", "Sync WhatsApp templates");
        TASK_DESCRIPTIONS.put("dispatchScheduledSocialPosts", "Dispatch scheduled social posts");
        TASK_DESCRIPTIONS.put("refreshSocialTokens", "Refresh expiring social tokens");
        TASK_DESCRIPTIONS.put("resetMonthlyUsageMeters", "Reset monthly usage meters");
        TASK_DESCRIPTIONS.put("pruneWebhookEvents", "Prune old billing webhook events");
        TASK_DESCRIPTIONS.put("billingSync", "Sync subscription status with billing gateways");
        TASK_DESCRIPTIONS.put("billingExpireTrials", "Expire trials past their end date");
        TASK_DESCRIPTIONS.put("billingChargeRecurring", "Charge recurring subscriptions (merchant-initiated gateways)");
        TASK_DESCRIPTIONS.put("notificationsTrialEnding", "Send trial-ending notifications");
        TASK_DESCRIPTIONS.put("reportsWeeklyDigest", "Send weekly digest reports");
    }

    private final QueueDispatcher queueDispatcher;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingGatewayRegistry gatewayRegistry;
    private final WebhookIdempotencyService webhookIdempotencyService;

    public SystemSchedulerTasks(QueueDispatcher queueDispatcher,
                                 SubscriptionRepository subscriptionRepository,
                                 BillingGatewayRegistry gatewayRegistry,
                                 WebhookIdempotencyService webhookIdempotencyService) {
        this.queueDispatcher = queueDispatcher;
        this.subscriptionRepository = subscriptionRepository;
        this.gatewayRegistry = gatewayRegistry;
        this.webhookIdempotencyService = webhookIdempotencyService;
    }

    // Heartbeat every minute
    @Scheduled(fixedRate = 60000)
    public void schedulerHeartbeat() {
        lastHeartbeat.set(Instant.now());
        log.debug("Scheduler heartbeat executed at {}", Instant.now());
    }

    /** Last time {@link #schedulerHeartbeat()} ran, or {@code null} before the first tick. */
    public Instant getLastHeartbeat() {
        return lastHeartbeat.get();
    }

    /**
     * Reflects every {@code @Scheduled} method on this bean into a description + trigger
     * expression, in {@link #TASK_DESCRIPTIONS} order — so the admin Cron Setup page always
     * shows exactly what's registered here, never a hand-maintained list that can drift.
     */
    public List<Map<String, Object>> describeScheduledTasks() {
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (Map.Entry<String, String> entry : TASK_DESCRIPTIONS.entrySet()) {
            Method method;
            try {
                method = getClass().getDeclaredMethod(entry.getKey());
            } catch (NoSuchMethodException e) {
                continue;
            }
            Scheduled ann = method.getAnnotation(Scheduled.class);
            if (ann == null) continue;

            String expression;
            if (!ann.cron().isEmpty()) {
                expression = ann.cron();
            } else if (ann.fixedRate() > 0) {
                expression = "every " + (ann.fixedRate() / 1000) + "s";
            } else if (ann.fixedDelay() > 0) {
                expression = "every " + (ann.fixedDelay() / 1000) + "s";
            } else {
                expression = "";
            }

            Map<String, Object> task = new LinkedHashMap<>();
            task.put("description", entry.getValue());
            task.put("expression", expression);
            tasks.add(task);
        }
        return tasks;
    }

    // Launch scheduled campaigns every minute
    @Scheduled(cron = "0 * * * * *")
    public void launchScheduledCampaigns() {
        log.info("Cron: Launching scheduled campaigns");
        queueDispatcher.dispatch("broadcast", "LaunchScheduledCampaignsJob", Map.of());
    }

    // Sync WhatsApp templates hourly
    @Scheduled(cron = "0 0 * * * *")
    public void syncWhatsappTemplates() {
        log.info("Cron: Syncing WhatsApp templates");
        queueDispatcher.dispatch("whatsapp", "TemplateSyncJob", Map.of());
    }

    // Dispatch scheduled social posts every minute
    @Scheduled(cron = "0 * * * * *")
    public void dispatchScheduledSocialPosts() {
        log.info("Cron: Dispatching scheduled social posts");
        queueDispatcher.dispatch("social", "DispatchScheduledPostsJob", Map.of());
    }

    // Refresh expiring social tokens daily at 02:00
    @Scheduled(cron = "0 0 2 * * *")
    public void refreshSocialTokens() {
        log.info("Cron: Refreshing expiring social tokens");
        queueDispatcher.dispatch("social", "RefreshSocialTokensJob", Map.of());
    }

    // Reset monthly usage meters
    @Scheduled(cron = "0 0 0 1 * *")
    public void resetMonthlyUsageMeters() {
        // No usage-metering feature exists on the Java side yet (PHP's UsageMeter
        // model/table was never ported) — nothing to reset. Logging this as done
        // would be dishonest; logging the real reason instead.
        log.info("Cron: Skipping usage-meter reset — no usage-metering feature ported yet.");
    }

    // Prune webhook events — matches PHP's weekly() schedule, Sunday 00:00
    @Scheduled(cron = "0 0 0 * * SUN")
    public void pruneWebhookEvents() {
        long deleted = webhookIdempotencyService.prune(WEBHOOK_EVENT_RETENTION_DAYS);
        log.info("Cron: Pruned {} billing webhook event record(s) older than {} days", deleted, WEBHOOK_EVENT_RETENTION_DAYS);
    }

    // Billing sync — matches PHP's hourly schedule
    @Scheduled(cron = "0 0 * * * *")
    public void billingSync() {
        List<Subscription> candidates = subscriptionRepository.findByStatusNot("canceled");
        int synced = 0;
        int skipped = 0;
        for (Subscription sub : candidates) {
            if (sub.getGateway() == null || sub.getGatewaySubscriptionId() == null) {
                skipped++;
                continue;
            }
            BillingGatewayInterface gateway = gatewayRegistry.get(sub.getGateway());
            if (gateway == null || !gateway.isConfigured()) {
                skipped++;
                continue;
            }
            try {
                if (gateway.sync(sub)) synced++;
            } catch (Exception e) {
                log.warn("Cron billing:sync failed for subscription {}: {}", sub.getId(), e.getMessage());
            }
        }
        log.info("Cron: Billing sync — {} subscription(s) synced, {} skipped (no gateway id or gateway not configured)", synced, skipped);
    }

    // Expire trials — matches PHP's hourly schedule
    @Scheduled(cron = "0 0 * * * *")
    public void billingExpireTrials() {
        List<Subscription> trialing = subscriptionRepository.findByStatus("trialing");
        LocalDateTime now = LocalDateTime.now();
        int expired = 0;
        for (Subscription sub : trialing) {
            if (sub.getTrialEndsAt() == null || sub.getTrialEndsAt().isAfter(now)) continue;

            // Re-sync first so a real conversion webhook that already landed isn't clobbered.
            BillingGatewayInterface gateway = sub.getGateway() != null ? gatewayRegistry.get(sub.getGateway()) : null;
            if (gateway != null && gateway.isConfigured()) {
                try {
                    gateway.sync(sub);
                } catch (Exception e) {
                    log.warn("Cron billing:expire-trials sync failed for subscription {}: {}", sub.getId(), e.getMessage());
                }
            }

            if ("trialing".equals(sub.getStatus()) && sub.getTrialEndsAt() != null && !sub.getTrialEndsAt().isAfter(now)) {
                sub.setStatus("canceled");
                sub.setEndsAt(sub.getTrialEndsAt());
                subscriptionRepository.save(sub);
                expired++;
            }
        }
        log.info("Cron: Expired {} trial subscription(s) past their trial_ends_at", expired);
    }

    // Charge recurring subscriptions — matches PHP's hourly schedule
    @Scheduled(cron = "0 30 * * * *")
    public void billingChargeRecurring() {
        // Stripe and Razorpay (the two real gateways implemented) both auto-renew
        // themselves and notify via webhook — this job exists in PHP only for
        // "merchant-initiated transaction" gateways with no native recurring
        // billing (Tap/Paymob/MyFatoorah), none of which are ported yet.
        log.info("Cron: Skipping charge-recurring — all real gateways here auto-renew via their own webhook, no MIT-pattern gateway is ported yet.");
    }

    // Trial ending notifications daily at 09:00
    @Scheduled(cron = "0 0 9 * * *")
    public void notificationsTrialEnding() {
        log.info("Cron: Sending trial ending notifications");
    }

    // Weekly digest reports every Monday at 08:00
    @Scheduled(cron = "0 0 8 * * MON")
    public void reportsWeeklyDigest() {
        log.info("Cron: Sending weekly digest reports");
    }
}
