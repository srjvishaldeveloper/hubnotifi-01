package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.AuditLog;
import com.whatsmine.model.User;
import com.whatsmine.repository.AuditLogRepository;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
public class ClientAuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public ClientAuditLogController(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/app/audit-logs")
    public Object index(@AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestParam(required = false) String action,
                        @RequestParam(defaultValue = "1") int page) {
        User currentUser = userDetails.getUser();
        Long workspaceId = currentUser.getWorkspaceId();
        boolean hasAction = action != null && !action.isBlank();
        PageRequest pageRequest = PageRequest.of(Math.max(0, page - 1), 20, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<AuditLog> pageResult = hasAction
                ? auditLogRepository.findByWorkspaceIdAndEventOrderByCreatedAtDesc(workspaceId, action, pageRequest)
                : auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageRequest);

        List<Long> userIds = pageResult.getContent().stream()
                .map(AuditLog::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> rows = pageResult.getContent().stream().map(log -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", log.getId());
            User user = log.getUserId() != null ? usersById.get(log.getUserId()) : null;
            row.put("user", user != null ? Map.of("id", user.getId(), "name", user.getName(), "email", user.getEmail()) : null);
            row.put("action", log.getEvent());
            row.put("meta", null);
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
        paginated.put("links", pageResult.getTotalPages() > 1 ? List.of(Map.of("url", "#")) : List.of());
        paginated.put("prev_page_url", pageResult.hasPrevious() ? pageUrl(page - 1, action) : null);
        paginated.put("next_page_url", pageResult.hasNext() ? pageUrl(page + 1, action) : null);

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("action", action);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("logs", paginated);
        props.put("filters", filters);

        return Inertia.render("client/AuditLog/Index", props);
    }

    private String pageUrl(int page, String action) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/app/audit-logs").queryParam("page", page);
        if (action != null && !action.isBlank()) {
            builder.queryParam("action", URLEncoder.encode(action, StandardCharsets.UTF_8));
        }
        return builder.toUriString();
    }
}
