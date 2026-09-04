package com.whatsmine.controller.api;

import com.whatsmine.model.AuditLog;
import com.whatsmine.model.User;
import com.whatsmine.repository.AuditLogRepository;
import com.whatsmine.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-log")
public class AuditLogApiController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogApiController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<Page<AuditLog>> index(@AuthenticationPrincipal CustomUserDetails userDetails,
                                               @RequestParam(defaultValue = "1") int page) {
        User user = userDetails.getUser();
        return ResponseEntity.ok(auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(user.getWorkspaceId(), PageRequest.of(page - 1, 20)));
    }
}
