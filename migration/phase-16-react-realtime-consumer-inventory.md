# Phase 16 — React / Echo Consumer Inventory

## Overview
This document inventories every single usage of Laravel Echo / Pusher in the React frontend codebase.
**Rule**: The React frontend MUST NOT be rewritten or modified.

## React Consumer Table

| React File Path | Channel Subscribed | Event Listened | Handler Callback Summary | Expected Payload Structure | UI Effect |
|---|---|---|---|---|---|
| `echo.js` | N/A (Global Setup) | N/A | Instantiates `window.Echo` using Pusher protocol (`authEndpoint: '/broadcasting/auth'`, headers with CSRF) | N/A | Initializes global `window.Echo` connection. |
| `Layouts/ClientLayout.jsx` | `private-App.Models.User.{auth.user.id}` | `.notification()` | Increments unread count, displays Sonner toast alert, triggers browser push notification if not on inbox | `{ id, type, snippet, name, automation, error, url, ... }` | Unread counter increment, toast popup with title & action link. |
| `Layouts/InboxLayout.jsx` | `private-App.Models.User.{auth.user.id}` | `.notification()` | Increments unread count, displays Sonner toast alert | `{ id, type, snippet, name, automation, error, url, ... }` | Unread badge update + toast alert. |
| `Pages/Inbox/Index.jsx` | `private-workspace.{workspaceId}` | `.MessageReceived` | Updates list state, increments unread count, updates `last_message` & `last_message_at`, flashes conversation item, reloads if new conv | `{ conversation_id, body, created_at, ... }` | Dynamic thread list re-ordering, flashing row animation, unread badge. |
| `Pages/Inbox/Show.jsx` | `private-conversation.{conversation.id}` | `.MessageReceived` | Appends message to thread if not present (`prev.some(m => m.id === e.id)`) | `{ id, conversation_id, direction, channel, type, body, payload, status, sent_at, created_at }` | Realtime chat thread updates without full page refresh. |
| `Pages/Inbox/Show.jsx` | `private-conversation.{conversation.id}` | `.MessageSent` | Appends or updates message status/payload in active thread | `{ id, conversation_id, direction, channel, type, body, payload, status, sent_at, created_at }` | Instant feedback for sent messages and server confirmation. |
| `Pages/Inbox/Show.jsx` | `private-conversation.{conversation.id}` | `.MessageStatusUpdated` | Updates checkmarks (`✓` / `✓✓`) for message status (`sent`, `delivered`, `read`, `failed`) | `{ id, conversation_id, status, provider_message_id }` | Checkmark indicator status transition in thread view. |
| `Pages/Inbox/Show.jsx` | `private-conversation.{conversation.id}` | `.ConversationAssigned` | Triggers Inertia reload for `conversation` prop | `{ conversation_id, assigned_to }` | Header avatar and assignment state update. |
| `Pages/Inbox/Show.jsx` | `private-conversation.{conversation.id}` | `.TypingChanged` | Adds/removes user typing indicator bubble (auto-expires after 3s) | `{ user_id, user_name, is_typing }` | "... is typing" banner beneath chat box. |
| `Pages/Inbox/Show.jsx` | `presence-conversation.{conversation.id}` | `Echo.join` (`here`, `joining`, `leaving`) | Updates list of active viewing agents (`viewers`) | Member objects: `{ id, name, avatar }` | Agent presence badges (who is looking at this conversation right now). |
| `Pages/Inbox/Show.jsx` | `private-workspace.{workspaceId}` | `.MessageReceived` | Plays inbound audio chime (`playInboundSound(e.channel)`), updates left sidebar conversation list | `{ conversation_id, channel, body, created_at }` | Notification chime sound + sidebar thread list reordering while inside a conversation view. |

## Contract Verification Summary
- **Channels Used**: `private-App.Models.User.{id}`, `private-workspace.{workspaceId}`, `private-conversation.{conversationId}`, `presence-conversation.{conversationId}`.
- **Event Name Prefixes**: React listens with leading dot (e.g. `.MessageReceived`), which matches standard Pusher/Echo event naming conventions.
- **Cleanup**: Every component cleans up with `window.Echo.leave(...)` on unmount.
