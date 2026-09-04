package com.whatsmine.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class ChannelRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChannelRegistry.class);

    // Channel name -> Set of active WebSocket Sessions
    private final Map<String, Set<WebSocketSession>> channelSessions = new ConcurrentHashMap<>();

    // Session ID -> Set of subscribed channel names
    private final Map<String, Set<String>> sessionChannels = new ConcurrentHashMap<>();

    // Session ID -> Socket Metadata
    private final Map<String, SocketMetadata> sessionMetadata = new ConcurrentHashMap<>();

    // Presence Channel Name -> Map<UserId, PresenceUser>
    private final Map<String, Map<Long, PresenceUser>> presenceMembers = new ConcurrentHashMap<>();

    public static class SocketMetadata {
        private final String socketId;
        private final Long userId;
        private final String userName;

        public SocketMetadata(String socketId, Long userId, String userName) {
            this.socketId = socketId;
            this.userId = userId;
            this.userName = userName;
        }

        public String getSocketId() {
            return socketId;
        }

        public Long getUserId() {
            return userId;
        }

        public String getUserName() {
            return userName;
        }
    }

    public static class PresenceUser {
        private final Long id;
        private final String name;
        private final String avatar;

        public PresenceUser(Long id, String name, String avatar) {
            this.id = id;
            this.name = name;
            this.avatar = avatar;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getAvatar() {
            return avatar;
        }
    }

    public void registerSession(WebSocketSession session, String socketId, Long userId, String userName) {
        sessionMetadata.put(session.getId(), new SocketMetadata(socketId, userId, userName));
        sessionChannels.put(session.getId(), new CopyOnWriteArraySet<>());
        log.debug("Registered socket connection session {} with socket_id {}", session.getId(), socketId);
    }

    public SocketMetadata getMetadata(WebSocketSession session) {
        return sessionMetadata.get(session.getId());
    }

    public void subscribe(WebSocketSession session, String channelName, PresenceUser presenceUser) {
        channelSessions.computeIfAbsent(channelName, k -> new CopyOnWriteArraySet<>()).add(session);
        sessionChannels.computeIfAbsent(session.getId(), k -> new CopyOnWriteArraySet<>()).add(channelName);

        if (channelName.startsWith("presence-") && presenceUser != null) {
            presenceMembers.computeIfAbsent(channelName, k -> new ConcurrentHashMap<>())
                    .put(presenceUser.getId(), presenceUser);
        }

        log.debug("Session {} subscribed to channel {}", session.getId(), channelName);
    }

    public void unsubscribe(WebSocketSession session, String channelName) {
        Set<WebSocketSession> sessions = channelSessions.get(channelName);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                channelSessions.remove(channelName);
            }
        }

        Set<String> channels = sessionChannels.get(session.getId());
        if (channels != null) {
            channels.remove(channelName);
        }

        if (channelName.startsWith("presence-")) {
            SocketMetadata meta = getMetadata(session);
            if (meta != null && meta.getUserId() != null) {
                Map<Long, PresenceUser> members = presenceMembers.get(channelName);
                if (members != null) {
                    members.remove(meta.getUserId());
                    if (members.isEmpty()) {
                        presenceMembers.remove(channelName);
                    }
                }
            }
        }

        log.debug("Session {} unsubscribed from channel {}", session.getId(), channelName);
    }

    public Set<WebSocketSession> getSubscribers(String channelName) {
        Set<WebSocketSession> sessions = channelSessions.get(channelName);
        return sessions != null ? Collections.unmodifiableSet(sessions) : Collections.emptySet();
    }

    public Map<Long, PresenceUser> getPresenceMembers(String channelName) {
        Map<Long, PresenceUser> members = presenceMembers.get(channelName);
        return members != null ? Collections.unmodifiableMap(members) : Collections.emptyMap();
    }

    public List<String> removeSession(WebSocketSession session) {
        SocketMetadata meta = sessionMetadata.remove(session.getId());
        Set<String> channels = sessionChannels.remove(session.getId());

        List<String> presenceChannelsLeft = new ArrayList<>();
        if (channels != null) {
            for (String channelName : channels) {
                Set<WebSocketSession> sessions = channelSessions.get(channelName);
                if (sessions != null) {
                    sessions.remove(session);
                    if (sessions.isEmpty()) {
                        channelSessions.remove(channelName);
                    }
                }

                if (channelName.startsWith("presence-") && meta != null && meta.getUserId() != null) {
                    Map<Long, PresenceUser> members = presenceMembers.get(channelName);
                    if (members != null) {
                        members.remove(meta.getUserId());
                        if (members.isEmpty()) {
                            presenceMembers.remove(channelName);
                        }
                    }
                    presenceChannelsLeft.add(channelName);
                }
            }
        }

        log.debug("Removed session {} from all channel subscriptions", session.getId());
        return presenceChannelsLeft;
    }
}
