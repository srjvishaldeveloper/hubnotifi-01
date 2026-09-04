# Phase 16 — Implementation Walkthrough

## Realtime Sequence Walkthrough

```text
User opens React App in Browser
        ↓
`echo.js` initializes `window.Echo = new Echo({ broadcaster: 'pusher', ... })`
        ↓
`pusher-js` opens WebSocket connection to `wss://domain/app/{appKey}?protocol=7`
        ↓
Spring Boot `PusherWebSocketHandler` accepts connection
        ↓
Java server sends `{ "event": "pusher:connection_established", "data": "{\"socket_id\":\"1700000.123456\"}" }`
        ↓
React Inbox/Layout executes `window.Echo.private('workspace.5')`
        ↓
Pusher JS sends HTTP `POST /broadcasting/auth` with `socket_id` & `channel_name`
        ↓
Spring `BroadcastingAuthController` validates user session & workspace access
        ↓
Java calculates HMAC SHA-256 signature and returns `{ "auth": "pusher_key:hash..." }`
        ↓
Pusher JS sends WebSocket frame `{ "event": "pusher:subscribe", "data": { "channel": "private-workspace.5", "auth": "..." } }`
        ↓
Java `PusherWebSocketHandler` validates signature & registers session in `ChannelRegistry`
        ↓
Java sends `{ "event": "pusher:subscription_succeeded", "channel": "private-workspace.5", "data": "{}" }`
        ↓
Agent sends message in Inbox -> `InboxController.reply()` executes
        ↓
Java `RealtimeBroadcaster.broadcast("conversation.42", ".MessageSent", msgPayload)` sends frame
        ↓
Pusher JS receives frame -> `laravel-echo` `.listen('.MessageSent', cb)` triggers React state update
        ↓
React Inbox UI updates chat thread live with 0 page refresh!
```
