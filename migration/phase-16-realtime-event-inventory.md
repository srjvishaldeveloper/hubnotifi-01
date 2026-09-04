# Phase 16 — Realtime Event Inventory

## Overview
This document contains the complete inventory of all broadcast events in the application, including their trigger conditions, target channels, event names, payload structures, authorization rules, queue behavior, consuming React components, and migration status.

## Realtime Event Inventory Table

| Laravel Event Class | Trigger Condition | Target Channel(s) | Channel Type | Event Name (`broadcastAs`) | Payload Summary | Auth Requirement | Queue / Sync | React Consumer Component | Parity Status |
|---|---|---|---|---|---|---|---|---|---|
| `MessageReceived` | Incoming WhatsApp/SMS/Email webhook processed | `conversation.{convId}`, `workspace.{wsId}` | Private | `.MessageReceived` | `{ id, conversation_id, direction, channel, type, body, payload, status, sent_at, created_at }` | Workspace access (`userCanAccessWorkspace`) | Queued (`ShouldBroadcast` via `broadcast` queue) | `Pages/Inbox/Show.jsx`, `Pages/Inbox/Index.jsx` | Inventory Complete |
| `MessageSent` | Outbound message sent by agent or system | `conversation.{convId}`, `workspace.{wsId}` | Private | `.MessageSent` | `{ id, conversation_id, direction, channel, type, body, payload, status, sent_at, created_at }` | Workspace access (`userCanAccessWorkspace`) | Queued (`ShouldBroadcast` via `broadcast` queue) | `Pages/Inbox/Show.jsx` | Inventory Complete |
| `MessageStatusUpdated` | Message delivery status callback (sent/delivered/read/failed) | `conversation.{convId}`, `workspace.{wsId}` | Private | `.MessageStatusUpdated` | `{ id, conversation_id, status, provider_message_id }` | Workspace access (`userCanAccessWorkspace`) | Queued (`ShouldBroadcast` via `broadcast` queue) | `Pages/Inbox/Show.jsx` | Inventory Complete |
| `TypingChanged` | User starts/stops typing in inbox conversation | `conversation.{convId}` | Private | `.TypingChanged` | `{ user_id, user_name, is_typing }` | Workspace access (`userCanAccessWorkspace`) | Synchronous (`ShouldBroadcastNow`) | `Pages/Inbox/Show.jsx` | Inventory Complete |
| `ConversationAssigned` | Agent assigned to/unassigned from conversation | `workspace.{wsId}`, `conversation.{convId}` | Private | `.ConversationAssigned` | `{ conversation_id, assigned_to: { id, name } }` | Workspace access (`userCanAccessWorkspace`) | Queued (`ShouldBroadcast` via `broadcast` queue) | `Pages/Inbox/Show.jsx`, `Pages/Inbox/Index.jsx` | Inventory Complete |
| `ContactCreated` | New contact created manually or via webhook | `workspace.{wsId}` | Private | `.ContactCreated` | `{ id, name, phone, email, created_at }` | Workspace access (`userCanAccessWorkspace`) | Queued (`ShouldBroadcast` via `broadcast` queue) | Workspace real-time listener | Inventory Complete |
| `CampaignCompleted` | Broadcast campaign finish / all messages sent | `workspace.{wsId}` | Private | `.CampaignCompleted` | `{ id, name, status, sent_count, failed }` | Workspace access (`userCanAccessWorkspace`) | Queued (`ShouldBroadcast` via `broadcast` queue) | `Layouts/ClientLayout.jsx` notification | Inventory Complete |
| `AutomationFailed` | Automation run encounters execution error | `workspace.{wsId}` | Private | `.AutomationFailed` | `{ run_id, automation, error }` | Workspace access (`userCanAccessWorkspace`) | Queued (`ShouldBroadcast` via `broadcast` queue) | `Layouts/ClientLayout.jsx` notification | Inventory Complete |
| `NotificationReceived` (System Notifications) | Laravel Notification dispatched via `broadcast` driver (`toBroadcast`) | `App.Models.User.{id}` | Private | `.Illuminate\Notifications\Events\NotificationReceived` | `{ id, type, title, snippet, url, ... }` | User ID match (`user.id === requested_id`) | Queued (`toBroadcast` channel) | `Layouts/ClientLayout.jsx`, `Layouts/InboxLayout.jsx` (`.notification()`) | Inventory Complete |

## Notification Broadcasting Inventory (`toBroadcast`)

The following system notifications send realtime broadcast events to `App.Models.User.{userId}`:

1. `UserWelcomeNotification` — User onboarding welcome notification.
2. `TrialEndingNotification` — Plan trial expiration notification.
3. `SubscriptionStartedNotification` — Subscription activation.
4. `SubscriptionRenewedNotification` — Subscription renewal confirmation.
5. `SubscriptionExpiredNotification` — Subscription expiration warning/notice.
6. `SubscriptionCancelledNotification` — Subscription cancellation confirmation.
7. `PlanChangedNotification` — Workspace subscription plan change notice.
8. `NewMessageNotification` — Inbound unassigned/assigned message notification.
9. `MentionedInNoteNotification` — `@user` tag mention in internal conversation note.
10. `ConversationHandoverNotification` — Bot to human agent handover alert.
11. `ConversationAssignedNotification` — Direct conversation assignment alert.
12. `CampaignCompletedNotification` — Campaign completion notice.
13. `BillingPaymentFailedNotification` — Recurring charge / invoice failure alert.
14. `AutomationFailedNotification` — Workflow automation error notice.

## Presence Events Inventory (`presence-conversation.{conversationId}`)
- **Channel**: `presence-conversation.{conversationId}`
- **Subscribed by**: `Pages/Inbox/Show.jsx` via `Echo.join()`
- **Member Object**: `{ id: user.id, name: user.name, avatar: null }`
- **Events**:
  - `pusher:member_added` (`joining`)
  - `pusher:member_removed` (`leaving`)
  - `pusher:subscription_succeeded` (`here`)
