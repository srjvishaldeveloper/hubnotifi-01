package com.whatsmine.service;

import com.whatsmine.model.AuditLog;
import com.whatsmine.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public AuditLog log(Long workspaceId, Long userId, String event, String auditableType, Long auditableId,
                        Map<String, Object> oldValues, Map<String, Object> newValues, String url, String ipAddress, String userAgent) {
        AuditLog log = new AuditLog();
        log.setWorkspaceId(workspaceId);
        log.setUserId(userId);
        log.setEvent(event);
        log.setAuditableType(auditableType);
        log.setAuditableId(auditableId);
        log.setOldValues(oldValues);
        log.setNewValues(newValues);
        log.setUrl(url);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        return auditLogRepository.save(log);
    }
}
