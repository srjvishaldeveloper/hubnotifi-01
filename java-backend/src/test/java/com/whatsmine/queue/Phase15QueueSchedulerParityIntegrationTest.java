package com.whatsmine.queue;

import com.whatsmine.model.FailedJob;
import com.whatsmine.model.Job;
import com.whatsmine.repository.FailedJobRepository;
import com.whatsmine.repository.JobRepository;
import com.whatsmine.scheduler.SystemSchedulerTasks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class Phase15QueueSchedulerParityIntegrationTest {

    @Autowired
    private QueueDispatcher queueDispatcher;

    @Autowired
    private QueueWorker queueWorker;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private FailedJobRepository failedJobRepository;

    @Autowired
    private SystemSchedulerTasks systemSchedulerTasks;

    @Autowired
    private JobRegistry jobRegistry;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        failedJobRepository.deleteAll();
    }

    @Test
    void testQueueDispatchAndWorkerProcessing() {
        String uuid = queueDispatcher.dispatch("default", "DispatchWebhookJob", Map.of("endpoint_id", 1, "event", "user.created"));
        assertThat(uuid).isNotNull();

        assertThat(jobRepository.count()).isEqualTo(1);

        boolean processed = queueWorker.processNextAvailableJob(null);
        assertThat(processed).isTrue();
        assertThat(jobRepository.count()).isEqualTo(0);
    }

    @Test
    void testDelayedJobIsHeldUntilAvailable() {
        queueDispatcher.dispatchDelayed("default", "GenerateWorkspaceExportJob", Map.of("userId", 42), 3600, 1, new int[]{60});
        assertThat(jobRepository.count()).isEqualTo(1);

        boolean processed = queueWorker.processNextAvailableJob(null);
        assertThat(processed).isFalse();
        assertThat(jobRepository.count()).isEqualTo(1);
    }

    @Test
    void testUnknownJobTypeMovedToFailedJobsTable() {
        queueDispatcher.dispatch("default", "NonExistentUnknownJob", Map.of("foo", "bar"), 1, new int[]{60});
        assertThat(jobRepository.count()).isEqualTo(1);

        boolean processed = queueWorker.processNextAvailableJob(null);
        assertThat(processed).isTrue();
        assertThat(jobRepository.count()).isEqualTo(0);

        List<FailedJob> failedJobs = failedJobRepository.findAll();
        assertThat(failedJobs).hasSize(1);
        assertThat(failedJobs.get(0).getException()).contains("No handler registered for job type: NonExistentUnknownJob");
    }

    @Test
    void testJobRetryAndBackoff() {
        jobRegistry.register(new JobHandler() {
            @Override
            public String getJobType() {
                return "TestFailingJob";
            }

            @Override
            public void handle(Map<String, Object> data) throws Exception {
                throw new RuntimeException("Simulated execution failure");
            }
        });

        queueDispatcher.dispatch("default", "TestFailingJob", Map.of(), 3, new int[]{120});

        boolean processed = queueWorker.processNextAvailableJob(null);
        assertThat(processed).isTrue();

        // Should not be in failed_jobs yet because maxTries is 3 and attempt 1 failed
        assertThat(failedJobRepository.count()).isEqualTo(0);
        assertThat(jobRepository.count()).isEqualTo(1);

        Optional<Job> jobOpt = jobRepository.findAll().stream().findFirst();
        assertThat(jobOpt).isPresent();
        Job job = jobOpt.get();
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getReservedAt()).isNull();
    }

    @Test
    void testSchedulerCronTaskInvocations() {
        systemSchedulerTasks.schedulerHeartbeat();
        systemSchedulerTasks.launchScheduledCampaigns();
        systemSchedulerTasks.syncWhatsappTemplates();
        systemSchedulerTasks.dispatchScheduledSocialPosts();
        systemSchedulerTasks.refreshSocialTokens();
        systemSchedulerTasks.resetMonthlyUsageMeters();
        systemSchedulerTasks.pruneWebhookEvents();
        systemSchedulerTasks.billingSync();
        systemSchedulerTasks.billingExpireTrials();
        systemSchedulerTasks.billingChargeRecurring();
        systemSchedulerTasks.notificationsTrialEnding();
        systemSchedulerTasks.reportsWeeklyDigest();

        // 4 cron tasks dispatch jobs into queues
        assertThat(jobRepository.count()).isEqualTo(4);
    }
}
