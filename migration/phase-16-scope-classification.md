# Phase 16 — Scope Classification

## Overview
This document classifies all realtime migration responsibilities across Phase 16 through Phase 20.

## Phase Scope Matrix

### Phase 16: Inventory, Audit & Design (Current Phase)
- Realtime event inventory & channel audit.
- React Echo consumer mapping.
- Protocol contract definition (Pusher v7 WebSocket framing & `/broadcasting/auth` signature format).
- Gap analysis of Java WebSocket capability.
- Queue & broadcast interaction analysis.
- Realtime security audit & multi-tenant isolation rules.
- Implementation plan creation.

### Phase 16 Implementation Phase (Next Task)
- Addition of `spring-boot-starter-websocket` to `java-backend/build.gradle`.
- Implementation of `BroadcastingAuthController` (`/broadcasting/auth`).
- Implementation of `PusherWebSocketHandler` & `PusherChannelRegistry`.
- Implementation of `RealtimeEventPublisher` & event DTO mappers.
- Integration with Phase 15 queue workers (`BroadcastEventJobHandler`).
- Backend unit and integration tests.

### Phase 17: End-to-End Realtime & UI Parity Verification
- React frontend + Java backend live WebSocket connection verification.
- Browser-level integration testing (inbox live chat, presence indicators, notification toasts, typing indicators).
- Verification of multi-tenant security boundary with multiple active browser sessions.

### Phase 18: Render Staging Realtime Deployment
- Deployment of Spring Boot WebSocket server to Render staging.
- WSS / TLS configuration verification on Render reverse proxy.
- Connection resilience, ping/pong heartbeat, and reconnect verification under network latency.

### Phase 19: Production Cutover
- Zero-downtime cutover of WebSocket environment variables (`VITE_PUSHER_HOST`, `VITE_PUSHER_PORT`) to Spring backend.

### Phase 20: Laravel Removal
- Decommissioning of legacy Laravel Reverb/Pusher server.
