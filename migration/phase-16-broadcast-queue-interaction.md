# Phase 16 — Broadcast & Queue Interaction

## Overview
This document details how Laravel broadcasting interacts with queue workers and how the Java backend will execute queued broadcasts cleanly using Phase 15 queue infrastructure.

## Laravel Broadcasting Execution Models

### 1. Queued Broadcasts (`ShouldBroadcast`)
- **Laravel Behavior**: When `event(new MessageReceived($message))` is called, Laravel creates a `BroadcastEvent` job and places it on the `broadcast` queue.
- **Queue Connection**: Database queue (`jobs` table).
- **Queue Name**: `broadcast`.
- **Payload**: Contains serialized event object and channel list.
- **Java Equivalent**: `QueueDispatcher.dispatch("broadcast", "BroadcastEventJob", payloadMap)` or direct asynchronous dispatch using `TaskExecutor`.

### 2. Synchronous Broadcasts (`ShouldBroadcastNow`)
- **Laravel Behavior**: When `event(new TypingChanged(...))` is called, Laravel broadcasts immediately on the active HTTP request thread without queueing.
- **Events Using Sync**: `TypingChanged`.
- **Java Equivalent**: Direct invocation of `RealtimeEventPublisher.publishSync(event)`.

## Queued Events Matrix

| Event Class | Dispatch Model | Target Queue | Retry Count | Delay / Backoff | Transaction / DB Commit Requirement |
|---|---|---|---|---|---|
| `MessageReceived` | Queued (`ShouldBroadcast`) | `broadcast` | 3 | 0s | Must broadcast after DB transaction commits (`Message` saved). |
| `MessageSent` | Queued (`ShouldBroadcast`) | `broadcast` | 3 | 0s | Must broadcast after DB transaction commits (`Message` saved). |
| `MessageStatusUpdated` | Queued (`ShouldBroadcast`) | `broadcast` | 3 | 0s | Must broadcast after DB status update commits. |
| `ConversationAssigned` | Queued (`ShouldBroadcast`) | `broadcast` | 3 | 0s | Must broadcast after conversation assignment updates. |
| `ContactCreated` | Queued (`ShouldBroadcast`) | `broadcast` | 3 | 0s | Must broadcast after contact entity persists. |
| `CampaignCompleted` | Queued (`ShouldBroadcast`) | `broadcast` | 3 | 0s | Broadcast upon campaign completion job finalizing. |
| `AutomationFailed` | Queued (`ShouldBroadcast`) | `broadcast` | 3 | 0s | Broadcast upon automation run failure handling. |
| `TypingChanged` | Synchronous (`ShouldBroadcastNow`) | N/A (Direct) | N/A | Instant | Instant dispatch (no DB persistence required). |
| `NotificationReceived` | Queued (`toBroadcast`) | `default` | 3 | 0s | Dispatched alongside notification record persistence. |

## Transaction Safety Requirements
In Spring Boot, database operations wrapped in `@Transactional` must defer broadcasting until after transaction commit (`TransactionSynchronizationManager.registerSynchronization(...)`) to ensure websocket subscribers querying the REST API for updated details receive committed database state.
