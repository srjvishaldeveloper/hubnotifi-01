package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.AuditLog;
import com.whatsmine.model.User;
import com.whatsmine.repository.AuditLogRepository;
import com.whatsmine.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ClientAuditLogController {

    private final AuditLogRepository auditLogRepository;

    public ClientAuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/app/audit-logs")
    public Object index(@AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestParam(defaultValue = "1") int page) {
        User user = userDetails.getUser();
        Page<AuditLog> pageResult = auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(user.getWorkspaceId(), PageRequest.of(page - 1, 20));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("logs", pageResult.getContent());
        props.put("total", pageResult.getTotalElements());

        return Inertia.render("client/AuditLogs/Index", props);
    }
}
