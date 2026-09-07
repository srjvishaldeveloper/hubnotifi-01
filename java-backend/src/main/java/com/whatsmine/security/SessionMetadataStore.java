package com.whatsmine.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the IP address / user agent a session was created with.
 *
 * Spring Security's SessionRegistry only knows session-id, principal and
 * last-request-time — it has no access to the underlying HttpSession's
 * request metadata, and other users' HttpSession objects aren't reachable
 * from a given request. This side-table fills that gap so the "Active
 * Sessions" screen can show a device/IP per entry.
 */
@Component
public class SessionMetadataStore {

    public record SessionMeta(String ipAddress, String userAgent) {}

    private final Map<String, SessionMeta> metadataBySessionId = new ConcurrentHashMap<>();

    public void record(String sessionId, String ipAddress, String userAgent) {
        metadataBySessionId.put(sessionId, new SessionMeta(ipAddress, userAgent));
    }

    public Optional<SessionMeta> get(String sessionId) {
        return Optional.ofNullable(metadataBySessionId.get(sessionId));
    }

    public void remove(String sessionId) {
        metadataBySessionId.remove(sessionId);
    }
}
