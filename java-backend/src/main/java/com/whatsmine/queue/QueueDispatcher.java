package com.whatsmine.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.Job;
import com.whatsmine.repository.JobRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class QueueDispatcher {

    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;

    public QueueDispatcher(JobRepository jobRepository, ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
    }

    public String dispatch(String queue, String jobType, Map<String, Object> data) {
        return dispatchDelayed(queue, jobType, data, 0, 3, new int[]{60});
    }

    public String dispatch(String queue, String jobType, Map<String, Object> data, int maxTries, int[] backoff) {
        return dispatchDelayed(queue, jobType, data, 0, maxTries, backoff);
    }

    public String dispatchDelayed(String queue, String jobType, Map<String, Object> data, int delaySeconds, int maxTries, int[] backoff) {
        try {
            String uuid = UUID.randomUUID().toString();
            long now = Instant.now().getEpochSecond();
            long availableAt = now + Math.max(0, delaySeconds);

            JobPayload payload = new JobPayload(uuid, jobType, maxTries, backoff != null ? backoff : new int[]{60}, data);
            String payloadJson = objectMapper.writeValueAsString(payload);

            Job job = new Job(null, queue != null ? queue : "default", payloadJson, 0, null, availableAt, now);
            jobRepository.save(job);
            return uuid;
        } catch (Exception e) {
            throw new RuntimeException("Failed to dispatch job to queue", e);
        }
    }
}
