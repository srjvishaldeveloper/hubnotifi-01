# Phase 15 — Database Mapping

## Queue-Related Tables

The Laravel database queue driver uses 3 tables. All exist in the schema. Java must work with them exactly as-is — no migrations, no schema changes.

---

## Table: `jobs`

**Purpose:** Persistent job queue — pending and reserved jobs waiting for workers.

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | BIGINT UNSIGNED AUTO_INCREMENT | No | Primary key |
| `queue` | VARCHAR | No | Queue name (`default`, `whatsapp`, `broadcast`, `ai`, `social`, `leads`, `automation`) |
| `payload` | LONGTEXT | No | JSON-serialized job payload (class name + constructor data) |
| `attempts` | TINYINT UNSIGNED | No | Number of times this job has been attempted |
| `reserved_at` | INT UNSIGNED | Yes | Unix timestamp when worker reserved/locked the job (null = available) |
| `available_at` | INT UNSIGNED | No | Unix timestamp when job becomes available (future = delayed) |
| `created_at` | INT UNSIGNED | No | Unix timestamp of job creation |

**Indexes:**
- `jobs_queue_index` on `queue`

**Primary Key:** `id`

**Notes:**
- `available_at > now()` = delayed job (not yet visible to workers)
- `reserved_at IS NOT NULL AND reserved_at < now() - retry_after` = stuck/timed-out job (should be re-queued)
- `reserved_at IS NULL AND available_at <= now()` = available for pickup
- Payload JSON structure (Laravel format):
  ```json
  {
    "uuid": "...",
    "displayName": "App\\Jobs\\DispatchWebhookJob",
    "job": "Illuminate\\Queue\\CallQueuedHandler@call",
    "maxTries": 5,
    "maxExceptions": 5,
    "failOnTimeout": false,
    "backoff": [60,300,3600,86400,86400],
    "timeout": null,
    "retryUntil": null,
    "data": {
      "commandName": "App\\Jobs\\DispatchWebhookJob",
      "command": "...serialized PHP object..."
    }
  }
  ```

> **CRITICAL:** Java must NOT attempt to deserialize PHP-serialized objects. Java writes its own JSON payload format. The Java queue implementation uses this table but with a Java-native payload format (pure JSON). Existing PHP jobs already in the table during cutover are drained by the PHP worker before Java takes over.

---

## Table: `failed_jobs`

**Purpose:** Records jobs that have exhausted all retry attempts and failed permanently.

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | BIGINT UNSIGNED AUTO_INCREMENT | No | Primary key |
| `uuid` | VARCHAR | No | Unique UUID per failed job (UNIQUE index) |
| `connection` | TEXT | No | Queue connection name (`database`, `redis`) |
| `queue` | TEXT | No | Queue name where job failed |
| `payload` | LONGTEXT | No | Original serialized payload at time of failure |
| `exception` | LONGTEXT | No | Full exception stack trace (string) |
| `failed_at` | TIMESTAMP | No | When the job was marked as failed (DEFAULT CURRENT_TIMESTAMP) |

**Indexes:**
- UNIQUE on `uuid`

**Primary Key:** `id`

**Notes:**
- Java failed job records must write `uuid`, `connection`, `queue`, `payload` (JSON), `exception` (string), and `failed_at`
- The `uuid` must be globally unique — use `UUID.randomUUID().toString()`
- The `connection` value should be `"database"` for the database-backed queue implementation
- `failed_at` has `useCurrent()` — Java can omit it and let MySQL DEFAULT handle it, or write it explicitly

---

## Table: `job_batches`

**Purpose:** Tracks Laravel batch jobs (Bus::batch()). Not currently used by any discovered job.

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | VARCHAR (primary) | No | Batch UUID |
| `name` | VARCHAR | No | Human-readable batch name |
| `total_jobs` | INT | No | Total jobs in batch |
| `pending_jobs` | INT | No | Remaining jobs |
| `failed_jobs` | INT | No | Failed count |
| `failed_job_ids` | LONGTEXT | No | JSON array of failed job UUIDs |
| `options` | MEDIUMTEXT | Yes | Serialized batch options |
| `cancelled_at` | INT | Yes | Unix timestamp when batch was cancelled |
| `created_at` | INT | No | Unix timestamp |
| `finished_at` | INT | Yes | Unix timestamp when batch finished |

**Status:** No discovered job uses `Bus::batch()`. This table is **not used by Phase 15** but exists in schema and must not be dropped. Java does not need to interact with it in Phase 15.

---

## Queue Reservation Logic (for Java Worker)

The Java database queue worker must implement this polling loop:

```
LOOP:
  BEGIN TRANSACTION
    SELECT id, payload, attempts, queue
    FROM jobs
    WHERE queue = ?
      AND available_at <= UNIX_TIMESTAMP()
      AND (reserved_at IS NULL OR reserved_at <= UNIX_TIMESTAMP() - retry_after)
    ORDER BY id ASC
    LIMIT 1
    FOR UPDATE SKIP LOCKED  -- requires MySQL 8+
    
    UPDATE jobs SET reserved_at = UNIX_TIMESTAMP(), attempts = attempts + 1
    WHERE id = ?
  COMMIT
  
  → Execute job
  → On success: DELETE FROM jobs WHERE id = ?
  → On failure (retries remain): UPDATE jobs SET reserved_at = NULL, available_at = UNIX_TIMESTAMP() + backoff WHERE id = ?
  → On failure (no retries): INSERT INTO failed_jobs ...; DELETE FROM jobs WHERE id = ?
```

**`FOR UPDATE SKIP LOCKED`** — available in MySQL 8+. If MySQL version < 8, fall back to `SELECT ... FOR UPDATE` with optimistic locking.

---

## Schema Constraints Summary

| Constraint | Value |
|---|---|
| `retry_after` (reservation timeout) | 90 seconds (config/queue.php default) |
| `failed_jobs.uuid` | UNIQUE constraint — must not conflict |
| No `job_batches` usage | Phase 15 does not need batch support |
| Payload column types | LONGTEXT — Java ObjectMapper JSON payload will fit |
| Timestamps are Unix epoch integers | `available_at`, `reserved_at`, `created_at` in `jobs` — use `Instant.now().getEpochSecond()` |
| `failed_at` is TIMESTAMP | Use LocalDateTime or Instant; MySQL DEFAULT handles it |

---

## Non-Queue Database Tables Used by Scheduler Tasks

These tables are READ/WRITTEN by scheduler tasks but are not queue tables — they belong to existing modules:

| Table | Used By | Purpose |
|---|---|---|
| `subscriptions` | billing scheduler commands | Read to find due/trialing subscriptions |
| `whatsapp_business_accounts` | sync-whatsapp-templates | Read all accounts to dispatch TemplateSyncJob |
| `social_posts` | dispatch-scheduled-posts | Atomic status flip scheduled→publishing |
| `social_accounts` | refresh-social-tokens | Find expiring tokens |
| `usage_meters` | reset-usage-meters | Prune old period records |
| `webhook_events` (idempotency) | prune-webhook-events | Prune old idempotency records |
| `cache` (or file/Redis) | scheduler-heartbeat | Writes `scheduler_heartbeat` key with TTL |
