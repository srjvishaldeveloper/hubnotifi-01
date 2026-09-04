# Phase 16 — Realtime Dependency Map

## Overview
This document maps the end-to-end realtime event lifecycle for both the legacy Laravel architecture and the target Spring Boot architecture.

## Legacy Architecture Flow (Laravel)

```text
Business Trigger (Webhook / Agent Action / Scheduler)
        ↓
Laravel Controller / Handler
        ↓
event(new MessageReceived($message))
        ↓
`broadcast` Queue (Jobs table) or Synchronous
        ↓
Laravel Queue Worker (queue:work)
        ↓
Broadcaster Driver (Pusher / Reverb HTTP API)
        ↓
WebSocket Server (Pusher / Reverb)
        ↓
WebSocket Frame Transmission over TCP/TLS
        ↓
`pusher-js` WebSocket client
        ↓
`laravel-echo` (.listen / .notification / .join)
        ↓
React State Update (Inertia Page / Hooks)
        ↓
UI Update (DOM Render / Sound Chime / Toast)
```

---

## Target Migration Flow (Spring Boot)

```text
Spring Business Action (Inbound Webhook / Controller / Job Handler)
        ↓
Spring Service Layer (MessageService / AutomationEngine / CampaignEngine)
        ↓
`RealtimeEventPublisher.publish(event)`
        ↓
Phase 15 Queue (`QueueDispatcher.dispatch("broadcast", ...)` if async)
        ↓
Spring WebSocket Handler (`PusherWebSocketHandler`)
        ↓
Socket Frame Delivery to Active Channel Subscriptions
        ↓
Existing Pusher-Compatible Transport
        ↓
Existing `laravel-echo` + `pusher-js` in React (0 changes required)
        ↓
Existing React Component / Hook
        ↓
Existing UI Update (DOM Render / Sound Chime / Toast)
```

## Key Parity Verification Points
1. **Endpoint Compatibility**: `/broadcasting/auth` must accept `socket_id` and `channel_name` POST parameters and validate user session.
2. **Protocol Framing**: WebSocket connection handshake must return `pusher:connection_established` frame with valid `socket_id`.
3. **Payload Parity**: All JSON string fields inside broadcast frames must match Laravel field names and date formats (`ISO-8601`).
