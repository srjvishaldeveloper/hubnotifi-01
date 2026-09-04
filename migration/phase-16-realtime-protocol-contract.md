# Phase 16 — Realtime Protocol Contract

## Overview
This document specifies the exact client-server WebSocket protocol contract expected by `laravel-echo` and `pusher-js` in the React frontend.
The Java backend MUST implement this exact protocol so React works transparently with 0 code changes.

## 1. Client Configuration (`echo.js`)
```javascript
window.Echo = new Echo({
    broadcaster:       'pusher',
    key:               PUSHER_APP_KEY,
    cluster:           PUSHER_APP_CLUSTER,
    forceTLS:          true,
    disableStats:      true,
    enabledTransports: ['ws', 'wss'],
    authEndpoint:      '/broadcasting/auth',
    auth: {
        headers: {
            'X-CSRF-TOKEN': csrfToken,
            'X-Requested-With': 'XMLHttpRequest',
            'Accept': 'application/json',
        },
    },
    authTransport:     'ajax',
});
```

## 2. Authentication Endpoint (`POST /broadcasting/auth`)

### Request Shape
- **Method**: `POST`
- **URL**: `/broadcasting/auth`
- **Content-Type**: `application/x-www-form-urlencoded`
- **Form Body**:
  - `socket_id`: `string` (e.g. `123456.789012`)
  - `channel_name`: `string` (e.g. `private-workspace.5` or `presence-conversation.42`)
- **Headers**:
  - `X-CSRF-TOKEN`: string
  - `Cookie`: session cookie (`JSESSIONID` or session cookie)

### Successful Authorization Responses

#### Private Channel (`private-*`)
- **Status**: `200 OK`
- **Content-Type**: `application/json`
- **Body**:
```json
{
  "auth": "PUSHER_APP_KEY:HMAC_SHA256_SIGNATURE"
}
```
*Signature calculation*: `HMAC_SHA256(socket_id + ":" + channel_name, PUSHER_APP_SECRET)`

#### Presence Channel (`presence-*`)
- **Status**: `200 OK`
- **Content-Type**: `application/json`
- **Body**:
```json
{
  "auth": "PUSHER_APP_KEY:HMAC_SHA256_SIGNATURE",
  "channel_data": "{\"user_id\":123,\"user_info\":{\"id\":123,\"name\":\"Agent Smith\",\"avatar\":null}}"
}
```
*Presence signature calculation*: `HMAC_SHA256(socket_id + ":" + channel_name + ":" + channel_data, PUSHER_APP_SECRET)`

### Unauthorized / Denied Response
- **Status**: `403 Forbidden`
- **Body**: `{ "error": "Unauthorized" }` or forbidden error.

---

## 3. WebSocket Handshake & Frame Protocol (Pusher Protocol v7)

### WebSocket Connection URL
`wss://domain/app/{app_key}?protocol=7&client=js&version=8.4.0&flash=false`

### Frame Formats (JSON Text Frames)

#### Connection Established (Server -> Client)
Sent immediately upon WebSocket connection opening:
```json
{
  "event": "pusher:connection_established",
  "data": "{\"socket_id\":\"123456.789012\",\"activity_timeout\":120}"
}
```

#### Ping / Pong Heartbeat
- **Client -> Server Ping**:
  ```json
  { "event": "pusher:ping", "data": {} }
  ```
- **Server -> Client Pong**:
  ```json
  { "event": "pusher:pong", "data": {} }
  ```

#### Channel Subscription (Client -> Server)
Sent by Pusher JS client after receiving `200 OK` from `/broadcasting/auth`:
```json
{
  "event": "pusher:subscribe",
  "data": {
    "channel": "private-workspace.5",
    "auth": "PUSHER_APP_KEY:SIGNATURE"
  }
}
```

#### Subscription Succeeded (Server -> Client)
Sent by server upon successful channel subscription validation:
```json
{
  "event": "pusher:subscription_succeeded",
  "channel": "private-workspace.5",
  "data": {}
}
```

For presence channels:
```json
{
  "event": "pusher:subscription_succeeded",
  "channel": "presence-conversation.42",
  "data": "{\"presence\":{\"count\":1,\"ids\":[\"123\"],\"hash\":{\"123\":{\"id\":123,\"name\":\"Agent Smith\",\"avatar\":null}}}}"
}
```

#### Unsubscribe (Client -> Server)
```json
{
  "event": "pusher:unsubscribe",
  "data": {
    "channel": "private-workspace.5"
  }
}
```

#### Server Broadcast Event Frame (Server -> Client)
Sent by server when a business event occurs:
```json
{
  "event": "MessageReceived",
  "channel": "private-conversation.42",
  "data": "{\"id\":99,\"conversation_id\":42,\"direction\":\"inbound\",\"channel\":\"whatsapp\",\"type\":\"text\",\"body\":\"Hello!\",\"status\":\"delivered\",\"created_at\":\"2026-09-04T10:00:00Z\"}"
}
```
*Note*: `data` value MUST be a serialized JSON string inside the JSON event object (or standard Pusher JSON object format).

#### Presence Join / Leave Events (Server -> Client)
- **Member Joined (`pusher:member_added`)**:
  ```json
  {
    "event": "pusher:member_added",
    "channel": "presence-conversation.42",
    "data": "{\"user_id\":124,\"user_info\":{\"id\":124,\"name\":\"Jane Doe\",\"avatar\":null}}"
  }
  ```
- **Member Left (`pusher:member_removed`)**:
  ```json
  {
    "event": "pusher:member_removed",
    "channel": "presence-conversation.42",
    "data": "{\"user_id\":124}"
  }
  ```
