# Phase 16 — Event Payload Contract

## Overview
This document specifies the exact JSON payload contracts for all 8 broadcast events and system notifications.
The Java backend MUST produce these exact field names and types.

---

## 1. `MessageReceived` (`.MessageReceived`)
```json
{
  "id": 101,
  "conversation_id": 42,
  "direction": "inbound",
  "channel": "whatsapp",
  "type": "text",
  "body": "Hello support, I need help with my order.",
  "payload": {
    "wa_id": "15551234567"
  },
  "status": "delivered",
  "sent_at": "2026-09-04T10:00:00Z",
  "created_at": "2026-09-04T10:00:00Z"
}
```

## 2. `MessageSent` (`.MessageSent`)
```json
{
  "id": 102,
  "conversation_id": 42,
  "direction": "outbound",
  "channel": "whatsapp",
  "type": "text",
  "body": "Hi there! How can I assist you today?",
  "payload": null,
  "status": "sent",
  "sent_at": "2026-09-04T10:01:00Z",
  "created_at": "2026-09-04T10:01:00Z"
}
```

## 3. `MessageStatusUpdated` (`.MessageStatusUpdated`)
```json
{
  "id": 102,
  "conversation_id": 42,
  "status": "read",
  "provider_message_id": "wamid.HBgLMTU1NTEyMzQ1NjcVAgARGBI1RkUzRj..."
}
```

## 4. `TypingChanged` (`.TypingChanged`)
```json
{
  "user_id": 5,
  "user_name": "Agent Sarah",
  "is_typing": true
}
```

## 5. `ConversationAssigned` (`.ConversationAssigned`)
```json
{
  "conversation_id": 42,
  "assigned_to": {
    "id": 5,
    "name": "Agent Sarah"
  }
}
```

## 6. `ContactCreated` (`.ContactCreated`)
```json
{
  "id": 77,
  "name": "John Doe",
  "phone": "+15551234567",
  "email": "john@example.com",
  "created_at": "2026-09-04T10:02:00Z"
}
```

## 7. `CampaignCompleted` (`.CampaignCompleted`)
```json
{
  "id": 12,
  "name": "Black Friday Promo",
  "status": "completed",
  "sent_count": 1500,
  "failed": 3
}
```

## 8. `AutomationFailed` (`.AutomationFailed`)
```json
{
  "run_id": 88,
  "automation": "Lead Qualification Flow",
  "error": "Failed to invoke OpenAI API: rate limit exceeded"
}
```

---

## 9. Notification Broadcast Contract (`App.Models.User.{id}`)

Laravel Echo `.notification()` listener receives notifications wrapped in Laravel's standard broadcast format:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "type": "new_message",
  "title": "New message",
  "snippet": "Hello support, I need help...",
  "name": "John Doe",
  "automation": null,
  "error": null,
  "url": "/app/inbox/conversations/42"
}
```
