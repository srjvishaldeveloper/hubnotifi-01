# Shared Inbox Realtime Inventory — Phase 8 Migration

This document records the Pusher/Echo realtime events used by the Shared Inbox React frontend.

---

## Realtime Event Inventory

| Event Class | Channel Name | Type | Payload Structure | React Listener Component |
|---|---|---|---|---|
| `MessageSent` | `private-workspace.{workspaceId}` | Private | `{ message: Message }` | `Inbox/Show.jsx`, `Inbox/Index.jsx` |
| `MessageReceived` | `private-workspace.{workspaceId}` | Private | `{ message: Message }` | `Inbox/Show.jsx`, `Inbox/Index.jsx` |
| `MessageStatusUpdated` | `private-workspace.{workspaceId}` | Private | `{ message: Message }` | `Inbox/Show.jsx` |
| `ConversationAssigned` | `private-workspace.{workspaceId}` | Private | `{ conversation: Conversation, assigned_to: User }` | `Inbox/Index.jsx` |
| `TypingChanged` | `private-conversation.{conversationUuid}` | Private | `{ user_id: int, user_name: string, is_typing: bool }` | `Inbox/Show.jsx` |
