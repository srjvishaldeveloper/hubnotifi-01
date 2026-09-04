# Phase 16 — Implementation Plan

## Overview
This implementation plan outlines the exact technical execution steps required to build the Spring Boot WebSocket/Pusher realtime broadcasting server to replace Laravel Reverb/Pusher while leaving the React client completely untouched.

---

## Technical Components to Implement in Java

### Component 1: `build.gradle` Dependency
Add standard Spring WebSocket starter:
```groovy
implementation 'org.springframework.boot:spring-boot-starter-websocket'
```

### Component 2: `/broadcasting/auth` Controller (`BroadcastingAuthController.java`)
- Endpoint: `@PostMapping(value = "/broadcasting/auth", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)`
- Inputs: `socket_id`, `channel_name`
- Auth Check: Inspect `SecurityContextHolder` to get authenticated `UserPrincipal`.
- Access Check: Call `BroadcastChannelsServiceProvider.userCanAccessWorkspace()` parity helper.
- Signing: Compute HMAC SHA-256 using configured Pusher App Secret:
  - Private: `{ "auth": "APP_KEY:" + HMAC(socket_id + ":" + channel_name) }`
  - Presence: `{ "auth": "...", "channel_data": "..." }`

### Component 3: Pusher Protocol WebSocket Handler (`PusherWebSocketHandler.java`)
- Extends `TextWebSocketHandler`.
- `afterConnectionEstablished`: Generate unique `socket_id` (e.g. `System.currentTimeMillis() + "." + random`), store session, send `pusher:connection_established` JSON frame.
- `handleTextMessage`:
  - `pusher:ping` -> reply `pusher:pong`.
  - `pusher:subscribe` -> validate signature & register socket to `ChannelRegistry`. Send `pusher:subscription_succeeded`.
  - `pusher:unsubscribe` -> unregister socket from channel.
- `afterConnectionClosed`: Remove socket from all channels, trigger `pusher:member_removed` for presence channels.

### Component 4: Channel Subscription Registry (`ChannelRegistry.java`)
- Thread-safe memory map: `Map<String, Set<WebSocketSession>> channelSessions`.
- Presence map: `Map<String, Map<String, UserPresenceDto>> presenceMembers`.

### Component 5: Realtime Event Publisher (`RealtimeEventPublisher.java`)
- Service providing `publish(String channel, String eventName, Object payload)`.
- Serializes payload to JSON, constructs Pusher event frame `{ "event": eventName, "channel": channel, "data": jsonPayloadString }`, sends to all active websocket sessions subscribed to `channel`.

---

## Implementation Sequence

1. **Step 1: Dependency & WebSockets Config**:
   Configure `WebSocketConfigurer` registering `/app/{appKey}` WebSocket endpoint.
2. **Step 2: Channel Authorization Controller**:
   Build `/broadcasting/auth` controller with HMAC signing & workspace isolation rules.
3. **Step 3: Pusher Framing & Session Registry**:
   Implement `PusherWebSocketHandler` handling handshake, ping/pong, subscribe, unsubscribe, and presence tracking.
4. **Step 4: Realtime Publisher & Job Handler**:
   Build `RealtimeEventPublisher` and `BroadcastEventJobHandler` for Phase 15 queue worker integration.
5. **Step 5: Event Payload DTOs**:
   Map all 8 broadcast events to exact JSON shapes.
6. **Step 6: Integration Testing**:
   Write `Phase16RealtimeParityIntegrationTest` verifying `/broadcasting/auth` 200/403 responses, WebSocket connection handshake, channel subscription, and broadcast frame transmission.

---

## Test & Verification Strategy
- **Unit Tests**: Test HMAC signature generation for private & presence channels.
- **Integration Tests**: MockMvc test `/broadcasting/auth` with valid/invalid workspace sessions.
- **WebSocket Protocol Test**: `StandardWebSocketClient` test subscribing to `private-workspace.1` and receiving broadcast frames.
