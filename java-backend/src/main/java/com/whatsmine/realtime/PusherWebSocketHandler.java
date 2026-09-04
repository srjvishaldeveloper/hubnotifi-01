package com.whatsmine.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.controller.broadcasting.BroadcastingAuthController;
import com.whatsmine.model.SystemSetting;
import com.whatsmine.repository.SystemSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.*;

@Component
public class PusherWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PusherWebSocketHandler.class);
    private static final SecureRandom random = new SecureRandom();

    private final ChannelRegistry channelRegistry;
    private final SystemSettingRepository systemSettingRepository;
    private final ObjectMapper objectMapper;

    public PusherWebSocketHandler(
            ChannelRegistry channelRegistry,
            SystemSettingRepository systemSettingRepository,
            ObjectMapper objectMapper) {
        this.channelRegistry = channelRegistry;
        this.systemSettingRepository = systemSettingRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String socketId = System.currentTimeMillis() + "." + (100000 + random.nextInt(900000));

        Long userId = null;
        String userName = "Anonymous";

        channelRegistry.registerSession(session, socketId, userId, userName);

        Map<String, Object> dataMap = Map.of(
                "socket_id", socketId,
                "activity_timeout", 120
        );
        String dataJson = objectMapper.writeValueAsString(dataMap);

        Map<String, Object> frame = Map.of(
                "event", "pusher:connection_established",
                "data", dataJson
        );

        sendJsonFrame(session, frame);
        log.info("WebSocket connection established session {} socket_id {}", session.getId(), socketId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payloadStr = message.getPayload();
        if (payloadStr == null || payloadStr.trim().isEmpty()) {
            return;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(payloadStr);
        } catch (Exception e) {
            log.warn("Malformed WebSocket text frame from session {}: {}", session.getId(), payloadStr);
            return;
        }

        String event = root.path("event").asText("");
        JsonNode dataNode = root.path("data");

        switch (event) {
            case "pusher:ping":
                handlePing(session);
                break;
            case "pusher:subscribe":
                handleSubscribe(session, dataNode);
                break;
            case "pusher:unsubscribe":
                handleUnsubscribe(session, dataNode);
                break;
            default:
                log.debug("Received unhandled WebSocket client event '{}' from session {}", event, session.getId());
                break;
        }
    }

    private void handlePing(WebSocketSession session) throws IOException {
        Map<String, Object> frame = Map.of(
                "event", "pusher:pong",
                "data", "{}"
        );
        sendJsonFrame(session, frame);
    }

    private void handleSubscribe(WebSocketSession session, JsonNode dataNode) throws IOException {
        String channel = dataNode.path("channel").asText("");
        String authStr = dataNode.path("auth").asText("");
        String channelDataStr = dataNode.path("channel_data").asText(null);

        if (channel.isEmpty()) {
            sendError(session, "Subscription failed: missing channel_name");
            return;
        }

        ChannelRegistry.SocketMetadata meta = channelRegistry.getMetadata(session);
        if (meta == null) {
            sendError(session, "Subscription failed: invalid socket session");
            return;
        }

        // Verify signature if private or presence channel
        if (channel.startsWith("private-") || channel.startsWith("presence-")) {
            String secret = systemSettingRepository.findByKey("pusher_app_secret")
                    .map(SystemSetting::getValue)
                    .filter(v -> v != null && !v.isBlank())
                    .orElseGet(() -> System.getenv("PUSHER_APP_SECRET") != null ? System.getenv("PUSHER_APP_SECRET") : "pusher_secret");

            String stringToSign = channelDataStr != null
                    ? meta.getSocketId() + ":" + channel + ":" + channelDataStr
                    : meta.getSocketId() + ":" + channel;

            String expectedSig = BroadcastingAuthController.hmacSha256(stringToSign, secret);
            int colonIdx = authStr.indexOf(':');
            String presentedSig = colonIdx >= 0 ? authStr.substring(colonIdx + 1) : authStr;

            if (!expectedSig.equalsIgnoreCase(presentedSig)) {
                log.warn("Subscription signature mismatch for channel {} on session {}", channel, session.getId());
                sendError(session, "Invalid signature for channel " + channel);
                return;
            }
        }

        ChannelRegistry.PresenceUser presenceUser = null;
        if (channel.startsWith("presence-") && channelDataStr != null) {
            try {
                JsonNode channelDataJson = objectMapper.readTree(channelDataStr);
                Long uId = channelDataJson.path("user_id").asLong(0);
                JsonNode userInfo = channelDataJson.path("user_info");
                String name = userInfo.path("name").asText("User");
                String avatar = userInfo.path("avatar").asText(null);
                if (uId > 0) {
                    presenceUser = new ChannelRegistry.PresenceUser(uId, name, avatar);
                }
            } catch (Exception e) {
                log.warn("Failed to parse presence channel_data", e);
            }
        }

        channelRegistry.subscribe(session, channel, presenceUser);

        // Send subscription_succeeded
        if (channel.startsWith("presence-")) {
            Map<Long, ChannelRegistry.PresenceUser> members = channelRegistry.getPresenceMembers(channel);
            List<String> ids = new ArrayList<>();
            Map<String, Object> hash = new HashMap<>();

            for (ChannelRegistry.PresenceUser pUser : members.values()) {
                String idStr = String.valueOf(pUser.getId());
                ids.add(idStr);
                hash.put(idStr, Map.of(
                        "id", pUser.getId(),
                        "name", pUser.getName(),
                        "avatar", pUser.getAvatar() != null ? pUser.getAvatar() : ""
                ));
            }

            Map<String, Object> presenceData = Map.of(
                    "presence", Map.of(
                            "count", members.size(),
                            "ids", ids,
                            "hash", hash
                    )
            );
            String presenceDataJson = objectMapper.writeValueAsString(presenceData);

            Map<String, Object> frame = Map.of(
                    "event", "pusher:subscription_succeeded",
                    "channel", channel,
                    "data", presenceDataJson
            );
            sendJsonFrame(session, frame);

            // Broadcast pusher:member_added to other subscribers
            if (presenceUser != null) {
                Map<String, Object> memberAddedData = Map.of(
                        "user_id", presenceUser.getId(),
                        "user_info", Map.of(
                                "id", presenceUser.getId(),
                                "name", presenceUser.getName(),
                                "avatar", presenceUser.getAvatar() != null ? presenceUser.getAvatar() : ""
                        )
                );
                String memberAddedDataJson = objectMapper.writeValueAsString(memberAddedData);
                Map<String, Object> memberAddedFrame = Map.of(
                        "event", "pusher:member_added",
                        "channel", channel,
                        "data", memberAddedDataJson
                );

                for (WebSocketSession subSession : channelRegistry.getSubscribers(channel)) {
                    if (!subSession.getId().equals(session.getId())) {
                        sendJsonFrame(subSession, memberAddedFrame);
                    }
                }
            }

        } else {
            Map<String, Object> frame = Map.of(
                    "event", "pusher:subscription_succeeded",
                    "channel", channel,
                    "data", "{}"
            );
            sendJsonFrame(session, frame);
        }
    }

    private void handleUnsubscribe(WebSocketSession session, JsonNode dataNode) throws IOException {
        String channel = dataNode.path("channel").asText("");
        if (channel.isEmpty()) return;

        ChannelRegistry.SocketMetadata meta = channelRegistry.getMetadata(session);
        channelRegistry.unsubscribe(session, channel);

        if (channel.startsWith("presence-") && meta != null && meta.getUserId() != null) {
            broadcastMemberRemoved(channel, meta.getUserId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        ChannelRegistry.SocketMetadata meta = channelRegistry.getMetadata(session);
        List<String> presenceChannelsLeft = channelRegistry.removeSession(session);

        if (meta != null && meta.getUserId() != null) {
            for (String presenceChannel : presenceChannelsLeft) {
                broadcastMemberRemoved(presenceChannel, meta.getUserId());
            }
        }
        log.info("WebSocket connection closed session {}", session.getId());
    }

    private void broadcastMemberRemoved(String presenceChannel, Long userId) throws IOException {
        Map<String, Object> removedData = Map.of("user_id", userId);
        String removedDataJson = objectMapper.writeValueAsString(removedData);
        Map<String, Object> frame = Map.of(
                "event", "pusher:member_removed",
                "channel", presenceChannel,
                "data", removedDataJson
        );

        for (WebSocketSession subSession : channelRegistry.getSubscribers(presenceChannel)) {
            sendJsonFrame(subSession, frame);
        }
    }

    private void sendJsonFrame(WebSocketSession session, Object frameObj) throws IOException {
        if (session.isOpen()) {
            String json = objectMapper.writeValueAsString(frameObj);
            session.sendMessage(new TextMessage(json));
        }
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        Map<String, Object> frame = Map.of(
                "event", "pusher:error",
                "data", objectMapper.writeValueAsString(Map.of("message", message, "code", 4001))
        );
        sendJsonFrame(session, frame);
    }
}
