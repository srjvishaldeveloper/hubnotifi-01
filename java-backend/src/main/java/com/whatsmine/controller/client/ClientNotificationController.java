package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.NotificationPreference;
import com.whatsmine.model.User;
import com.whatsmine.repository.NotificationPreferenceRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
public class ClientNotificationController {

    private final NotificationPreferenceRepository notificationPreferenceRepository;

    public ClientNotificationController(NotificationPreferenceRepository notificationPreferenceRepository) {
        this.notificationPreferenceRepository = notificationPreferenceRepository;
    }

    @GetMapping
    public Object index(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        List<NotificationPreference> prefs = notificationPreferenceRepository.findByUserId(user.getId());

        Map<String, Map<String, Boolean>> prefMap = new LinkedHashMap<>();
        for (NotificationPreference p : prefs) {
            prefMap.computeIfAbsent(p.getEvent(), k -> new LinkedHashMap<>()).put(p.getChannel(), Boolean.TRUE.equals(p.getEnabled()));
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("notifications", List.of());
        props.put("preferences", prefMap);

        return Inertia.render("client/Notifications/Index", props);
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Map<String, Object>>> recent() {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> unreadCount() {
        return ResponseEntity.ok(Map.of("count", 0));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/mark-all-read")
    public Object markAllRead(HttpSession session) {
        Inertia.flashSuccess(session, "All notifications marked as read.");
        return Inertia.redirect("/notifications");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> destroy(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/preferences")
    public Object updatePreferences(@AuthenticationPrincipal CustomUserDetails userDetails,
                                    @RequestBody Map<String, Object> payload,
                                    HttpSession session) {
        User user = userDetails.getUser();
        if (payload.get("preferences") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) payload.get("preferences");
            for (Map<String, Object> item : list) {
                String event = (String) item.get("event");
                String channel = (String) item.get("channel");
                Boolean enabled = Boolean.TRUE.equals(item.get("enabled"));

                if (event != null && channel != null) {
                    NotificationPreference pref = notificationPreferenceRepository
                            .findByUserIdAndEventAndChannel(user.getId(), event, channel)
                            .orElseGet(() -> {
                                NotificationPreference np = new NotificationPreference();
                                np.setUserId(user.getId());
                                np.setEvent(event);
                                np.setChannel(channel);
                                return np;
                            });
                    pref.setEnabled(enabled);
                    notificationPreferenceRepository.save(pref);
                }
            }
        }

        Inertia.flashSuccess(session, "Notification preferences updated.");
        return Inertia.redirect("/notifications");
    }
}
