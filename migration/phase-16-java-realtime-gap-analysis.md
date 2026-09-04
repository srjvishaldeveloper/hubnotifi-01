# Phase 16 — Java Realtime Gap Analysis

## Overview
This document evaluates the existing Java codebase regarding WebSocket/realtime implementation, identifies missing components, assesses protocol transport options, and details what can be reused versus what must be built.

## Current State of Java Codebase
- **WebSocket Dependencies**: Not present in `build.gradle` (needs `spring-boot-starter-websocket` or Pusher Server Java SDK).
- **Controllers**: No `/broadcasting/auth` controller endpoints exist yet.
- **WebSocket Handlers**: No `WebSocketHandler` or Pusher protocol handlers exist in `src/main/java`.
- **Event Bus / Publisher**: No Java realtime event publisher exists to broadcast events when database messages or automations finish.

## Protocol Transport Options Evaluation

### Option A: STOMP over WebSocket (`spring-messaging`)
- **Pros**: Native Spring Boot abstraction.
- **Cons**: **INCOMPATIBLE WITH REACT CLIENT**. Standard Laravel Echo uses Pusher protocol or Pusher HTTP/WS driver. STOMP framing (`CONNECT`, `SUBSCRIBE`, `SEND`) differs completely from Pusher framing (`pusher:subscribe`, `pusher:connection_established`).
- **Verdict**: **REJECTED**. Fails rule #1 ("React client remains unchanged").

### Option B: Spring WebSocket (`WebSocketHandler` implementing Pusher v7 Protocol)
- **Pros**:
  1. Full control over WebSocket lifecycle, authentication, session maps, and channel routing.
  2. Uses native Spring Boot WebSockets (`spring-boot-starter-websocket`) without external external server dependencies.
  3. Implements exact Pusher protocol v7 JSON frames.
  4. Fully compatible with `laravel-echo` + `pusher-js`.
  5. Simple to support `/broadcasting/auth` endpoint using Spring MVC `@PostMapping("/broadcasting/auth")`.
- **Cons**: Needs clean implementation of socket frame router & channel subscription manager (~200-300 lines of code).
- **Verdict**: **RECOMMENDED / SELECTED**.

### Option C: External Reverb / Pusher HTTP API client in Java
- **Pros**: Java backend uses `pusher-http-java` SDK to send broadcasts to an external Reverb or Pusher WebSocket server.
- **Cons**: Requires running external Reverb daemon alongside Java backend.
- **Verdict**: Optional supporting mode for multi-node clusters.

## Gap Analysis Matrix

| Component Required | Existing Java Code | Status | Required Action |
|---|---|---|---|
| `/broadcasting/auth` Endpoint | None | Missing | Build `BroadcastingAuthController` in Java using Spring Security auth context & `userCanAccessWorkspace` check. |
| WebSocket Handler | None | Missing | Add `spring-boot-starter-websocket` to `build.gradle`, implement `PusherWebSocketHandler` matching Pusher v7 frames. |
| Channel Subscription Manager | None | Missing | Build `ChannelSubscriptionRegistry` in Java to manage socket-to-channel mappings and presence lists. |
| Realtime Event Publisher | None | Missing | Build `RealtimeEventPublisher` service in Java to emit events to channel subscribers. |
| Event Payload Serialization | None | Missing | Build DTO mappers matching exact Laravel `broadcastWith()` JSON structures. |
| Spring Security Integration | Spring Security session auth active | Exists | Wire session auth & CSRF validation to `/broadcasting/auth`. |
