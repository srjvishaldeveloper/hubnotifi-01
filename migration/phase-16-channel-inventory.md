# Phase 16 — Channel Inventory

## Overview
This document inventories all WebSocket/Pusher broadcast channels in the application, including channel type, scope, authorization rules, events broadcast, React consumers, and security requirements.

## Realtime Channels Table

| Channel Pattern | Type | Scope | Authorization Rule (Laravel Source of Truth) | Events Broadcast | React Consumer File | Security & Multi-Tenant Isolation |
|---|---|---|---|---|---|---|
| `App.Models.User.{id}` | Private | Per-User | `authenticated_user.id === requested_id` | System Notifications (`toBroadcast`) | `Layouts/ClientLayout.jsx`, `Layouts/InboxLayout.jsx` | Strict user-isolation. Prevents user A from receiving notifications for user B. |
| `workspace.{workspaceId}` | Private | Workspace-Wide | `userCanAccessWorkspace(user, workspaceId)` (Checks primary workspace, `current_workspace_id`, pivot membership, or client ownership) | `.MessageReceived`, `.MessageSent`, `.MessageStatusUpdated`, `.ConversationAssigned`, `.ContactCreated`, `.CampaignCompleted`, `.AutomationFailed` | `Pages/Inbox/Index.jsx`, `Pages/Inbox/Show.jsx` | Workspace tenant isolation. Prevents Workspace A members from seeing Workspace B activity. |
| `conversation.{conversationId}` | Private | Per-Conversation | `conversation.exists && userCanAccessWorkspace(user, conversation.workspace_id)` | `.MessageReceived`, `.MessageSent`, `.MessageStatusUpdated`, `.ConversationAssigned`, `.TypingChanged` | `Pages/Inbox/Show.jsx` | Conversation-level security tied to workspace access control. |
| `presence-conversation.{conversationId}` | Presence | Per-Conversation | `conversation.exists && userCanAccessWorkspace(user, conversation.workspace_id)`. Returns member data: `{ id, name, avatar: null }` | Presence join/leave events (`here`, `joining`, `leaving`) | `Pages/Inbox/Show.jsx` (`Echo.join`) | Presence tracking for active conversation viewers in shared inbox. |

## Detailed Authorization Rules (`BroadcastChannelsServiceProvider.php`)

### 1. User Channel: `App.Models.User.{id}`
```php
Broadcast::channel('App.Models.User.{id}', function (User $user, int $id) {
    return (int) $user->id === $id;
});
```

### 2. Workspace Channel: `workspace.{workspaceId}`
```php
Broadcast::channel('workspace.{workspaceId}', function (User $user, int $workspaceId) {
    return userCanAccessWorkspace($user, $workspaceId);
});
```
Authorization logic checks:
1. `$user->workspace_id === $workspaceId`
2. `$user->current_workspace_id === $workspaceId`
3. `$user->accessibleWorkspaces()->contains('id', $workspaceId)`
4. `$workspace->client_id === $user->client_id` (Same client organisation)

### 3. Conversation Channel: `conversation.{conversationId}`
```php
Broadcast::channel('conversation.{conversationId}', function (User $user, int $conversationId) {
    $conversation = Conversation::find($conversationId);
    return $conversation && userCanAccessWorkspace($user, (int) $conversation->workspace_id);
});
```

### 4. Presence Channel: `presence-conversation.{conversationId}`
```php
Broadcast::channel('presence-conversation.{conversationId}', function (User $user, int $conversationId) {
    $conversation = Conversation::find($conversationId);
    if (! $conversation || ! userCanAccessWorkspace($user, (int) $conversation->workspace_id)) {
        return false;
    }
    return [
        'id' => $user->id,
        'name' => $user->name,
        'avatar' => null,
    ];
});
```
