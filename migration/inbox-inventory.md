# Shared Inbox / Conversations Inventory — Phase 8 Migration

## 1. Overview
The Shared Inbox module manages omnichannel conversation threads (`whatsapp`, `instagram`, `messenger`), contact matching, message history, agent assignment, conversation status state transitions (`open`, `pending`, `snoozed`, `resolved`), internal notes with `@mentions`, canned replies, conversation labels, and media proxying.

---

## 2. Routes & Controller Endpoints

| HTTP Method | Route URL | Laravel Controller Method | Middleware | Description |
|---|---|---|---|---|
| GET | `/app/inbox` | `InboxController@index` | `auth:web`, `client-app` | Render `Inbox/Index` Inertia page (conversation thread list, filters, labels, channel accounts) |
| GET | `/app/inbox/conversations/{id}` | `InboxController@show` | `auth:web`, `client-app` | Render `Inbox/Show` Inertia page (conversation thread detail, messages, notes, team members, templates) |
| POST | `/app/inbox/conversations/start` | `InboxController@startConversation` | `auth:web`, `client-app` | Find or create open conversation with contact and optional opening message |
| POST | `/app/inbox/conversations/{id}/reply` | `InboxController@reply` | `auth:web`, `client-app` | Send outbound reply (text, image, document, video, audio, template) via channel driver |
| POST | `/app/inbox/conversations/{id}/share-product` | `InboxController@shareProduct` | `auth:web`, `client-app` | Share product card into conversation |
| POST | `/app/inbox/conversations/{id}/assign` | `InboxController@assign` | `auth:web`, `client-app` | Assign conversation to workspace team user |
| POST | `/app/inbox/conversations/{id}/status` | `InboxController@updateStatus` | `auth:web`, `client-app` | Update conversation status (`open`, `pending`, `snoozed`, `resolved`) |
| POST | `/app/inbox/conversations/{id}/typing` | `InboxController@typing` | `auth:web`, `client-app` | Broadcast typing indicator event |
| POST | `/app/inbox/conversations/{id}/handover` | `InboxController@handover` | `auth:web`, `client-app` | Switch assignment mode (`human` vs `bot`) |
| GET | `/app/inbox/contacts/search` | `InboxController@contactSearch` | `auth:web`, `client-app` | Search contacts for new conversation modal |
| GET | `/app/inbox/channel-accounts` | `InboxController@channelAccounts` | `auth:web`, `client-app` | Fetch active channel accounts for workspace |
| GET | `/app/inbox/templates` | `InboxController@templates` | `auth:web`, `client-app` | Fetch approved WhatsApp templates for workspace |
| POST | `/app/inbox/conversations/{id}/upload-media` | `InboxController@uploadMedia` | `auth:web`, `client-app` | Upload file to WhatsApp Cloud API |
| GET | `/app/inbox/conversations/{id}/messages/{msg}/media` | `InboxController@serveMedia` | `auth:web`, `client-app` | Proxy/cache inbound WhatsApp media attachment |
| GET | `/app/inbox/conversations/{id}/notes` | `InternalNoteController@index` | `auth:web`, `client-app` | Fetch internal notes for conversation |
| POST | `/app/inbox/conversations/{id}/notes` | `InternalNoteController@store` | `auth:web`, `client-app` | Create internal note with `@user` mentions |
| GET | `/app/inbox/canned-replies` | `CannedReplyController@index` | `auth:web`, `client-app` | Render `Inbox/CannedReplies/Index` |
| GET | `/app/inbox/canned-replies/list` | `CannedReplyController@list` | `auth:web`, `client-app` | Fetch canned reply list for slash command picker |
| POST | `/app/inbox/canned-replies` | `CannedReplyController@store` | `auth:web`, `client-app` | Create canned reply |
| PUT | `/app/inbox/canned-replies/{id}` | `CannedReplyController@update` | `auth:web`, `client-app` | Update canned reply |
| DELETE | `/app/inbox/canned-replies/{id}` | `CannedReplyController@destroy` | `auth:web`, `client-app` | Delete canned reply |
| GET | `/app/inbox/labels` | `LabelController@index` | `auth:web`, `client-app` | Render `Inbox/Labels/Index` |
| POST | `/app/inbox/labels` | `LabelController@store` | `auth:web`, `client-app` | Create label |
| PUT | `/app/inbox/labels/{id}` | `LabelController@update` | `auth:web`, `client-app` | Update label |
| DELETE | `/app/inbox/labels/{id}` | `LabelController@destroy` | `auth:web`, `client-app` | Delete label |
| POST | `/app/inbox/conversations/{id}/labels` | `LabelController@attach` | `auth:web`, `client-app` | Attach label to conversation |
| DELETE | `/app/inbox/conversations/{id}/labels/{labelId}` | `LabelController@detach` | `auth:web`, `client-app` | Detach label from conversation |

---

## 3. Database Tables
- `conversations`
- `messages`
- `contacts`
- `channel_accounts`
- `internal_notes`
- `inbox_labels`
- `inbox_canned_replies`
- `inbox_label_conversation` (pivot)

---

## 4. Realtime & Broadcast Events
- `MessageSent`: Broadcast when outbound reply is dispatched.
- `MessageReceived`: Broadcast when inbound message arrives.
- `MessageStatusUpdated`: Broadcast when delivery/read status updates (`sent` → `delivered` → `read`).
- `ConversationAssigned`: Broadcast when conversation is assigned to an agent.
- `TypingChanged`: Broadcast agent typing status.
