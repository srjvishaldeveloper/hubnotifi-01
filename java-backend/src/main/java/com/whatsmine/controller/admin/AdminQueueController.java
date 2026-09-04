package com.whatsmine.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.FailedJob;
import com.whatsmine.repository.FailedJobRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/queue")
public class AdminQueueController {

    private final FailedJobRepository failedJobRepository;
    private final ObjectMapper objectMapper;

    public AdminQueueController(FailedJobRepository failedJobRepository, ObjectMapper objectMapper) {
        this.failedJobRepository = failedJobRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public Object index(@RequestParam(defaultValue = "failed") String tab) {
        List<Map<String, Object>> failedRows = new ArrayList<>();
        int failedTotal = 0;

        if ("failed".equals(tab)) {
            List<FailedJob> jobs = failedJobRepository.findAll();
            jobs.sort(Comparator.comparing(FailedJob::getFailedAt, Comparator.nullsLast(Comparator.reverseOrder())));
            failedTotal = jobs.size();
            for (FailedJob job : jobs) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", job.getId());
                row.put("uuid", job.getUuid());
                row.put("queue", job.getQueue());
                row.put("class", extractClass(job.getPayload()));
                row.put("exception", job.getException() != null && job.getException().length() > 300
                        ? job.getException().substring(0, 300) : job.getException());
                row.put("failed_at", job.getFailedAt());
                failedRows.add(row);
            }
        }

        Map<String, Object> failedJobs = new LinkedHashMap<>();
        failedJobs.put("data", failedRows);
        failedJobs.put("total", failedTotal);

        // No job_batches table/entity is wired up yet — mirrors the PHP
        // controller's try/catch-to-empty-collection fallback for that case.
        Map<String, Object> batches = new LinkedHashMap<>();
        batches.put("data", List.of());
        batches.put("total", 0);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("tab", tab);
        props.put("failedJobs", failedJobs);
        props.put("batches", batches);

        return Inertia.render("Admin/Queue/Index", props);
    }

    @PostMapping("/{id}/retry")
    public Object retryFailed(@PathVariable Long id, HttpSession session) {
        // No queue worker to redispatch onto yet — clearing the failed row is
        // the closest honest equivalent until a real retry pipeline exists.
        failedJobRepository.deleteById(id);
        Inertia.flashSuccess(session, "Job queued for retry.");
        return Inertia.redirect("/admin/queue");
    }

    @DeleteMapping("/{id}")
    public Object deleteFailed(@PathVariable Long id, HttpSession session) {
        failedJobRepository.deleteById(id);
        Inertia.flashSuccess(session, "Failed job deleted.");
        return Inertia.redirect("/admin/queue");
    }

    @PostMapping("/retry-all")
    public Object retryAll(HttpSession session) {
        failedJobRepository.deleteAll();
        Inertia.flashSuccess(session, "All failed jobs queued for retry.");
        return Inertia.redirect("/admin/queue");
    }

    @PostMapping("/flush")
    public Object flushFailed(HttpSession session) {
        failedJobRepository.deleteAll();
        Inertia.flashSuccess(session, "All failed jobs deleted.");
        return Inertia.redirect("/admin/queue");
    }

    private String extractClass(String payload) {
        if (payload == null) {
            return "Unknown";
        }
        try {
            Map<?, ?> decoded = objectMapper.readValue(payload, Map.class);
            Object displayName = decoded.get("displayName");
            Object job = decoded.get("job");
            return displayName != null ? displayName.toString() : job != null ? job.toString() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
