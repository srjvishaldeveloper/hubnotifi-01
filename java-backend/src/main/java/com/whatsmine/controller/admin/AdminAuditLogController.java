package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.AuditLog;
import com.whatsmine.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/audit-logs")
public class AdminAuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AdminAuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public Object index(@RequestParam(defaultValue = "1") int page) {
        Page<AuditLog> pageResult = auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page - 1, 20));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("logs", pageResult.getContent());
        props.put("total", pageResult.getTotalElements());

        return Inertia.render("Admin/AuditLogs/Index", props);
    }
}
