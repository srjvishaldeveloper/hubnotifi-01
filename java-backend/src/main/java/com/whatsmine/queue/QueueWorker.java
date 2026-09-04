package com.whatsmine.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.FailedJob;
import com.whatsmine.model.Job;
import com.whatsmine.repository.FailedJobRepository;
import com.whatsmine.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;

@Service
public class QueueWorker {

    private static final Logger log = LoggerFactory.getLogger(QueueWorker.class);

    private final JobRepository jobRepository;
    private final FailedJobRepository failedJobRepository;
    private final JobRegistry jobRegistry;
    private final ObjectMapper objectMapper;

    private static final long RETRY_AFTER_SECONDS = 90;

    public QueueWorker(JobRepository jobRepository,
                       FailedJobRepository failedJobRepository,
                       JobRegistry jobRegistry,
                       ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.failedJobRepository = failedJobRepository;
        this.jobRegistry = jobRegistry;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    public void pollAndProcessJobs() {
        try {
            boolean hasMore = true;
            while (hasMore) {
                hasMore = processNextAvailableJob(null);
            }
        } catch (Exception e) {
            log.error("Error in queue worker polling loop", e);
        }
    }

    @Transactional
    public boolean processNextAvailableJob(String queue) {
        long now = Instant.now().getEpochSecond();
        long reservedBefore = now - RETRY_AFTER_SECONDS;

        List<Job> jobs = (queue != null && !queue.isBlank())
                ? jobRepository.findNextAvailableJobInQueue(queue, now, reservedBefore, PageRequest.of(0, 1))
                : jobRepository.findNextAvailableJob(now, reservedBefore, PageRequest.of(0, 1));

        if (jobs.isEmpty()) {
            return false;
        }

        Job job = jobs.get(0);
        job.setReservedAt(now);
        job.setAttempts(job.getAttempts() + 1);
        jobRepository.save(job);

        try {
            JobPayload payload = objectMapper.readValue(job.getPayload(), JobPayload.class);
            var handlerOpt = jobRegistry.getHandler(payload.getJobType());

            if (handlerOpt.isEmpty()) {
                log.warn("No handler registered for job type: {}. Failing job.", payload.getJobType());
                markJobFailed(job, payload, new IllegalArgumentException("No handler registered for job type: " + payload.getJobType()));
                return true;
            }

            JobHandler handler = handlerOpt.get();
            handler.handle(payload.getData());

            // Success -> delete job
            jobRepository.delete(job);
            log.info("Job {} [{}] completed successfully", job.getId(), payload.getJobType());

        } catch (Exception e) {
            log.error("Job {} execution failed", job.getId(), e);
            try {
                JobPayload payload = objectMapper.readValue(job.getPayload(), JobPayload.class);
                int maxTries = payload.getMaxTries() > 0 ? payload.getMaxTries() : 3;

                if (job.getAttempts() < maxTries) {
                    int[] backoff = payload.getBackoff();
                    int backoffSeconds = 60;
                    if (backoff != null && backoff.length > 0) {
                        int index = Math.min(job.getAttempts() - 1, backoff.length - 1);
                        backoffSeconds = backoff[index];
                    }
                    job.setReservedAt(null);
                    job.setAvailableAt(now + backoffSeconds);
                    jobRepository.save(job);
                    log.info("Job {} released with {}s backoff (attempt {}/{})", job.getId(), backoffSeconds, job.getAttempts(), maxTries);
                } else {
                    markJobFailed(job, payload, e);
                }
            } catch (Exception parseEx) {
                markJobFailed(job, null, e);
            }
        }

        return true;
    }

    private void markJobFailed(Job job, JobPayload payload, Exception exception) {
        String uuid = payload != null && payload.getUuid() != null ? payload.getUuid() : java.util.UUID.randomUUID().toString();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);

        FailedJob failedJob = new FailedJob(null, uuid, "database", job.getQueue(), job.getPayload(), sw.toString(), null);

        failedJobRepository.save(failedJob);
        jobRepository.delete(job);
        log.error("Job {} marked as failed permanently in failed_jobs table", job.getId());
    }
}
