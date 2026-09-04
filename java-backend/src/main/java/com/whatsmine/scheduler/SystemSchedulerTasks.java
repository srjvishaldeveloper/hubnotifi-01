package com.whatsmine.scheduler;

import com.whatsmine.queue.QueueDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class SystemSchedulerTasks {

    private static final Logger log = LoggerFactory.getLogger(SystemSchedulerTasks.class);

    private final QueueDispatcher queueDispatcher;

    public SystemSchedulerTasks(QueueDispatcher queueDispatcher) {
        this.queueDispatcher = queueDispatcher;
    }

    // Heartbeat every minute
    @Scheduled(fixedRate = 60000)
    public void schedulerHeartbeat() {
        log.debug("Scheduler heartbeat executed at {}", Instant.now());
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
        log.info("Cron: Resetting monthly usage meters");
    }

    // Prune webhook events daily at 03:00
    @Scheduled(cron = "0 0 3 * * *")
    public void pruneWebhookEvents() {
        log.info("Cron: Pruning old webhook events");
    }

    // Billing sync hourly
    @Scheduled(cron = "0 0 * * * *")
    public void billingSync() {
        log.info("Cron: Syncing billing status");
    }

    // Expire trials daily at 01:00
    @Scheduled(cron = "0 0 1 * * *")
    public void billingExpireTrials() {
        log.info("Cron: Expiring billing trials");
    }

    // Charge recurring subscriptions daily at 01:30
    @Scheduled(cron = "0 30 1 * * *")
    public void billingChargeRecurring() {
        log.info("Cron: Charging recurring subscriptions");
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
