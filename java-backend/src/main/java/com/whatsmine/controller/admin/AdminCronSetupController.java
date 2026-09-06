package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.scheduler.SystemSchedulerTasks;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unlike PHP/Laravel, this Spring Boot backend needs no OS crontab entry and no separate
 * queue-worker process: {@link SystemSchedulerTasks} runs its {@code @Scheduled} jobs and
 * {@link com.whatsmine.queue.QueueWorker} polls the jobs table, both in-process, for as
 * long as the application is running. This page reports that truthfully instead of
 * reusing PHP's artisan/Supervisor instructions, which don't apply here.
 */
@RestController
@RequestMapping("/admin/cron-setup")
public class AdminCronSetupController {

    private final SystemSchedulerTasks schedulerTasks;

    public AdminCronSetupController(SystemSchedulerTasks schedulerTasks) {
        this.schedulerTasks = schedulerTasks;
    }

    @GetMapping
    public Object index() {
        Instant lastHeartbeat = schedulerTasks.getLastHeartbeat();

        List<Map<String, Object>> tasks = schedulerTasks.describeScheduledTasks();
        Map<String, Object> queueTask = new LinkedHashMap<>();
        queueTask.put("description", "Background job queue worker (polls the jobs table)");
        queueTask.put("expression", "every 1s");
        tasks.add(queueTask);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("runtime", "java");
        props.put("basePath", System.getProperty("user.dir"));
        props.put("tasks", tasks);
        props.put("schedulerLastRun", lastHeartbeat != null ? DateTimeFormatter.ISO_INSTANT.format(lastHeartbeat) : null);
        props.put("schedulerStatus", status(lastHeartbeat));

        return Inertia.render("Admin/CronSetup/Index", props);
    }

    private String status(Instant lastHeartbeat) {
        if (lastHeartbeat == null) return "inactive";
        long secondsAgo = Duration.between(lastHeartbeat, Instant.now()).getSeconds();
        if (secondsAgo <= 120) return "active";
        if (secondsAgo <= 3600) return "stale";
        return "inactive";
    }
}
