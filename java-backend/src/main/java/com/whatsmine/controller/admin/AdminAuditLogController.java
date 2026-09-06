package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.AuditLog;
import com.whatsmine.model.User;
import com.whatsmine.repository.AuditLogRepository;
import com.whatsmine.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/audit-logs")
public class AdminAuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AdminAuditLogController(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public Object index(@RequestParam(required = false) Long userId,
                         @RequestParam(required = false) String action,
                         @RequestParam(value = "page", defaultValue = "1") int page) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page - 1), 30, Sort.by(Sort.Direction.DESC, "createdAt"));
        boolean hasUser = userId != null;
        boolean hasAction = action != null && !action.isBlank();

        Page<AuditLog> pageResult;
        if (hasUser && hasAction) {
            pageResult = auditLogRepository.findByUserIdAndEventOrderByCreatedAtDesc(userId, action, pageRequest);
        } else if (hasUser) {
            pageResult = auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);
        } else if (hasAction) {
            pageResult = auditLogRepository.findByEventOrderByCreatedAtDesc(action, pageRequest);
        } else {
            pageResult = auditLogRepository.findAllByOrderByCreatedAtDesc(pageRequest);
        }

        List<Long> userIds = pageResult.getContent().stream()
                .map(AuditLog::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> rows = pageResult.getContent().stream().map(log -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", log.getId());
            User user = log.getUserId() != null ? usersById.get(log.getUserId()) : null;
            row.put("user", user != null ? Map.of("id", user.getId(), "name", user.getName(), "email", user.getEmail()) : null);
            row.put("actor_admin", null);
            row.put("action", log.getEvent());
            row.put("meta", null);
            row.put("auditable_type", log.getAuditableType());
            row.put("auditable_id", log.getAuditableId());
            row.put("old_values", log.getOldValues());
            row.put("new_values", log.getNewValues());
            row.put("ip", log.getIpAddress());
            row.put("url", log.getUrl());
            row.put("created_at", log.getCreatedAt() != null
                    ? log.getCreatedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    : null);
            return row;
        }).toList();

        Map<String, Object> paginated = new LinkedHashMap<>();
        paginated.put("data", rows);
        paginated.put("current_page", pageResult.getNumber() + 1);
        paginated.put("last_page", pageResult.getTotalPages());
        paginated.put("total", pageResult.getTotalElements());

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("user_id", userId);
        filters.put("action", action);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("logs", paginated);
        props.put("filters", filters);

        return Inertia.render("Admin/AuditLog/Index", props);
    }
}
