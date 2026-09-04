package com.whatsmine.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RealtimeBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RealtimeBroadcaster.class);

    private final ChannelRegistry channelRegistry;
    private final ObjectMapper objectMapper;

    public RealtimeBroadcaster(ChannelRegistry channelRegistry, ObjectMapper objectMapper) {
        this.channelRegistry = channelRegistry;
        this.objectMapper = objectMapper;
    }

    public void broadcast(String channel, String eventName, Object payload) {
        try {
            Set<WebSocketSession> sessions = channelRegistry.getSubscribers(channel);
            if (sessions.isEmpty()) {
                log.debug("No subscribers registered for channel {}, event {}", channel, eventName);
                return;
            }

            String payloadJson = payload instanceof String ? (String) payload : objectMapper.writeValueAsString(payload);

            Map<String, Object> frame = Map.of(
                    "event", eventName,
                    "channel", channel,
                    "data", payloadJson
            );

            String frameJson = objectMapper.writeValueAsString(frame);
            TextMessage textMessage = new TextMessage(frameJson);

            int sentCount = 0;
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                        sentCount++;
                    } catch (IOException e) {
                        log.warn("Failed to send broadcast frame to session {}", session.getId(), e);
                    }
                }
            }
            log.debug("Broadcast event '{}' to channel '{}' sent to {}/{} sessions", eventName, channel, sentCount, sessions.size());

        } catch (Exception e) {
            log.error("Failed to broadcast event '{}' to channel '{}'", eventName, channel, e);
        }
    }

    public void broadcastNotification(Long userId, String type, String title, String snippet, String url) {
        String channel = "App.Models.User." + userId;
        Map<String, Object> payload = Map.of(
                "id", UUID.randomUUID().toString(),
                "type", type,
                "title", title,
                "snippet", snippet != null ? snippet : "",
                "name", title,
                "url", url != null ? url : ""
        );
        broadcast(channel, ".Illuminate\\Notifications\\Events\\NotificationReceived", payload);
    }
}
