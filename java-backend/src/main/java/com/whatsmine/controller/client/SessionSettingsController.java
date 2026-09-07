package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.model.User;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.security.SessionMetadataStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Active Sessions" profile page: lists every device currently signed in as this
 * user (via Spring Security's SessionRegistry) and lets them revoke all but the
 * current one. See SecurityConfig for how sessions get registered/tracked.
 */
@RestController
@RequestMapping("/profile/sessions")
public class SessionSettingsController {

    private static final DateTimeFormatter LAST_ACTIVE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a").withZone(ZoneId.systemDefault());

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionRegistry sessionRegistry;
    private final SessionMetadataStore sessionMetadataStore;

    public SessionSettingsController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SessionRegistry sessionRegistry,
            SessionMetadataStore sessionMetadataStore) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionRegistry = sessionRegistry;
        this.sessionMetadataStore = sessionMetadataStore;
    }

    @GetMapping
    public InertiaResponse index(@AuthenticationPrincipal CustomUserDetails userDetails, HttpServletRequest request) {
        String currentSessionId = request.getSession(true).getId();

        List<SessionInformation> infos = sessionRegistry.getAllSessions(userDetails, false);
        List<Map<String, Object>> sessions = infos.stream()
                .sorted(Comparator.comparing(SessionInformation::getLastRequest).reversed())
                .map(info -> toSessionMap(info, currentSessionId))
                .toList();

        Map<String, Object> props = new HashMap<>();
        props.put("sessions", sessions);
        return Inertia.render("Profile/Sessions", props);
    }

    @DeleteMapping
    public Object destroyOthers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody PasswordConfirmationRequest request,
            HttpServletRequest httpRequest,
            HttpSession session) {

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("The provided password does not match your current password.");
        }

        String currentSessionId = httpRequest.getSession(true).getId();
        for (SessionInformation info : sessionRegistry.getAllSessions(userDetails, false)) {
            if (!info.getSessionId().equals(currentSessionId)) {
                info.expireNow();
            }
        }

        Inertia.flashSuccess(session, "You have been logged out of all other sessions.");
        return Inertia.redirect("/profile/sessions");
    }

    private Map<String, Object> toSessionMap(SessionInformation info, String currentSessionId) {
        var meta = sessionMetadataStore.get(info.getSessionId());

        Map<String, Object> map = new HashMap<>();
        map.put("id", info.getSessionId());
        map.put("ip_address", meta.map(SessionMetadataStore.SessionMeta::ipAddress).orElse(null));
        map.put("user_agent", meta.map(SessionMetadataStore.SessionMeta::userAgent).orElse(null));
        map.put("last_active_at", LAST_ACTIVE_FORMAT.format(Instant.ofEpochMilli(info.getLastRequest().getTime())));
        map.put("is_current", info.getSessionId().equals(currentSessionId));
        return map;
    }

    public static class PasswordConfirmationRequest {
        @NotBlank
        private String password;

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
