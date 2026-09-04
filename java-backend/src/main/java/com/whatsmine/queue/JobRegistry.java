package com.whatsmine.queue;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JobRegistry {

    private final Map<String, JobHandler> handlers = new ConcurrentHashMap<>();

    public JobRegistry(List<JobHandler> handlerList) {
        if (handlerList != null) {
            for (JobHandler handler : handlerList) {
                handlers.put(handler.getJobType(), handler);
            }
        }
    }

    public void register(JobHandler handler) {
        handlers.put(handler.getJobType(), handler);
    }

    public Optional<JobHandler> getHandler(String jobType) {
        return Optional.ofNullable(handlers.get(jobType));
    }
}
