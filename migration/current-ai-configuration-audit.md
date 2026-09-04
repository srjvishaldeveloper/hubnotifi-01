# Current AI Configuration Audit + Phase 15 Queue/Scheduler Compatibility Report

**Audit Date:** 2026-09-04
**Migration Status:** Phase 1-14 COMPLETE (129 tests passing) | Phase 15 PENDING
**Audit Scope:** AI model/provider configuration verification + Phase 15 queue/scheduler compatibility

---

## CRITICAL FINDING: AI Configuration Is UNCHANGED Since Phase 11

After thorough inspection of all Java source files, configuration files, environment variable
references, and migration documentation, the finding is:

> The AI provider/model configuration in the current Java implementation is IDENTICAL to
> the Phase 11 configuration. No model name change has been applied to the codebase.

The task description states the model/provider was changed after Phase 11, but no evidence
of that change exists anywhere in the current repository:

- No new model names in application.yml, application-dev.yml, application-prod.yml
- No new environment variable names added
- No new provider classes created
- No provider class constructor defaults updated
- The ai-provider-mapping.md documentation still reflects Phase 11 defaults
- The ai-inventory.md still references the original Phase 11 model names
- The AiParityIntegrationTest.java still uses "gpt-4o-mini" as the mock model label

**Conclusion:** Either (a) the model change has not yet been applied to this repository,
or (b) the new model is supplied at runtime via the ai_provider_configs database table
and/or environment variables - and the source code correctly does NOT hardcode it,
making it database/env-driven already.

**ACTION REQUIRED:** Please confirm the new AI provider and model name so Phase 15
can be verified correctly.

---

## 1. AI Configuration Comparison Table

| Item | Phase 11 Configuration | Current Configuration | Changed? | Source |
|------|------------------------|----------------------|----------|--------|
| Provider Architecture | Multi-provider: OpenAI, Anthropic, Gemini | Multi-provider: OpenAI, Anthropic, Gemini | No | LlmManager.java |
| Provider Priority | openai -> anthropic -> gemini | openai -> anthropic -> gemini | No | LlmManager.java:22 |
| Chat Model (OpenAI default) | gpt-4o-mini | gpt-4o-mini | No | OpenAiProvider.java:25 |
| Chat Model (Anthropic default) | claude-3-haiku-20240307 | claude-3-haiku-20240307 | No | AnthropicProvider.java:23 |
| Chat Model (Gemini default) | gemini-1.5-flash | gemini-1.5-flash | No | GeminiProvider.java:24 |
| Embedding Model (OpenAI default) | text-embedding-3-small | text-embedding-3-small | No | OpenAiProvider.java:26 |
| Embedding Model (Gemini default) | text-embedding-004 | text-embedding-004 | No | GeminiProvider.java:25 |
| Embedding Model (Anthropic) | Not supported | Not supported | No | AnthropicProvider.java:94 |
| OpenAI API Endpoint | https://api.openai.com/v1 | https://api.openai.com/v1 | No | OpenAiProvider.java:14 |
| Anthropic API Endpoint | https://api.anthropic.com/v1 | https://api.anthropic.com/v1 | No | AnthropicProvider.java:14 |
| Gemini API Endpoint | https://generativelanguage.googleapis.com/v1beta | https://generativelanguage.googleapis.com/v1beta | No | GeminiProvider.java:14 |
| OpenAI Authentication | Bearer token | Bearer token | No | OpenAiProvider.java:52 |
| Anthropic Authentication | x-api-key + anthropic-version: 2023-06-01 | Same | No | AnthropicProvider.java:62-63 |
| Gemini Authentication | key query parameter | key query parameter | No | GeminiProvider.java:60 |
| Default Max Tokens | 1024 | 1024 | No | All providers |
| Default Temperature | 0.7 | 0.7 | No | OpenAiProvider.java:39 |
| Context Limits | Not hardcoded; per-call via opts | Same | No | All providers |
| Embedding Dimensions | OpenAI: 1536, Gemini: 768 | Same (model unchanged) | No | Model defaults |
| Env Variables (OpenAI) | AI_OPENAI_API_KEY, OPENAI_API_KEY | Same | No | LlmManager.java:32 |
| Env Variables (Anthropic) | AI_ANTHROPIC_API_KEY, ANTHROPIC_API_KEY | Same | No | LlmManager.java:35 |
| Env Variables (Gemini) | AI_GEMINI_API_KEY, GEMINI_API_KEY | Same | No | LlmManager.java:38 |
| Config-driven model override | Via ai_provider_configs.default_model_chat / default_model_embed | Same | No | LlmManager.java:27,52 |

---

## 2. Current AI Architecture (Verified Correct)

```
ai_provider_configs table (per workspace)
  OR environment variables (system fallback)
        |
    LlmManager.forWorkspace(workspaceId)
        |
    LlmManager.build(provider, apiKey, chatModel, embedModel)
        |
    OpenAiProvider | AnthropicProvider | GeminiProvider
        |
    LlmGateway.chat() or LlmGateway.embed()
        |
    AI API (OpenAI / Anthropic / Google)
```

### Key Architecture Properties (All Verified)

| Property | Status |
|----------|--------|
| Model selection is configuration-driven (DB or env) | YES - Correct |
| No hardcoded model in controllers | YES - Correct |
| No hardcoded model in AutomationEngine | YES - Correct |
| No hardcoded model in WorkflowGenerator | YES - Correct |
| No hardcoded model in ChatbotRunner | YES - Correct |
| No hardcoded model in DocumentIndexer | YES - Correct |
| No hardcoded model in queue job handlers | YES - Correct |
| Provider fallback chain works | YES - Correct |
| Embedding-capable provider detection works | YES - Correct |

### Hardcoded Default Values (Fallback Only - NOT Call Sites)

These appear ONLY as constructor defaults when no model is specified in configuration.
They only activate if a workspace has no ai_provider_configs entry AND no environment variable.

| Location | Default Value | Risk |
|----------|---------------|------|
| OpenAiProvider.java:25 | "gpt-4o-mini" | Low - overridden by config/env |
| OpenAiProvider.java:26 | "text-embedding-3-small" | Low - overridden by config/env |
| AnthropicProvider.java:23 | "claude-3-haiku-20240307" | Low - overridden by config/env |
| GeminiProvider.java:24 | "gemini-1.5-flash" | Low - overridden by config/env |
| GeminiProvider.java:25 | "text-embedding-004" | Low - overridden by config/env |

Assessment: These are NOT stale hardcoded references. The LlmManager always passes model
names from ai_provider_configs when a workspace config exists.

### Model Reference in Tests

AiParityIntegrationTest.java uses "gpt-4o-mini" as the mock response label (lines 119, 121, 139, 246).
These are mock return values, NOT real API calls. LlmGateway is fully mocked via @MockBean.

---

## 3. Model Change: Repository Cannot Confirm

IMPORTANT: The task states a model change occurred after Phase 11. However, the repository
contains no evidence of a new model name anywhere - not in source code, not in configuration
files, not in migration docs.

Possible explanations:
1. The change is runtime-only - deployed via ai_provider_configs database table or environment
   variables without a code change. Architecture already handles this correctly.
2. The change has not yet been applied to this repository.
3. The change happened in a different branch not in the current working tree.

---

## 4. Provider Support Matrix

| Provider | Chat | Embeddings | Auth | Status |
|----------|------|-----------|------|--------|
| OpenAI | Yes | Yes | Bearer token | Fully implemented |
| Anthropic | Yes | No (by design) | x-api-key header | Fully implemented |
| Gemini | Yes | Yes | key query param | Fully implemented |

### Compatibility Checks

| Check | Status | Notes |
|-------|--------|-------|
| Request format | OK | Each provider handles its own format |
| Response format | OK | Each provider extracts content correctly |
| Authentication | OK | Provider-specific auth per class |
| Token handling | OK | Prompt + completion tokens extracted |
| Embeddings | OK | OpenAI /embeddings, Gemini batchEmbedContents |
| Structured JSON output | OK | WorkflowGenerator parses raw JSON |
| System prompts | OK | All providers handle system role |
| Tool/function calling | N/A | Not used |
| Streaming | N/A | Not used |
| Error handling | OK | RuntimeException on non-2xx |
| Retry handling | MISSING | PHP has .retry(2, 500) - Java providers do not |
| Context window | OK | max_tokens passed per call via opts |

---

## 5. Embedding Compatibility

| Item | Value |
|------|-------|
| OpenAI embed model | text-embedding-3-small (1536 dimensions) |
| Gemini embed model | text-embedding-004 (768 dimensions) |
| Storage format | JSON array string in ai_kb_chunks.embedding (LONGTEXT) |
| Search | In-memory cosine similarity via EmbeddingStore.search() |

EmbeddingStore.cosine() checks a.size() != b.size() and returns 0.0 if dimensions mismatch.
This is safe behavior - mismatched vectors return zero similarity, not crashes.

Re-indexing requirement:
- If the embedding model changes, all existing ai_kb_chunks.embedding values are incompatible
- Old embeddings: 1536-dim (OpenAI) or 768-dim (Gemini)
- Mixed embeddings: will silently return near-zero similarity for mismatched pairs
- DO NOT modify database schema
- DO NOT auto-re-index production data
- If model confirmed changed: document as a deployment operation (re-run IndexDocumentJob for all docs)

Current conclusion: Since no model change is confirmed, re-indexing is NOT currently required.

---

## 6. AI Background Processing Inventory

| AI Task | Current Java Service | Async? | Queue | Model Used | Phase |
|---------|---------------------|--------|-------|-----------|-------|
| Document chunking + embedding | DocumentIndexer + LlmGateway.embed() | Stub (handler logs only) | ai / IndexDocumentJob | Workspace embed provider | 11+15 |
| Chatbot RAG query embedding | ChatbotRunner -> LlmGateway.embed() | Sync inline | N/A | Workspace embed provider | 11 |
| Chatbot reply generation | ChatbotRunner -> LlmGateway.chat() | Sync inline | N/A | Workspace chat provider | 11 |
| AI reply automation node | AutomationEngine.executeAiReply() | Sync (Phase 15 adds async) | automation / ExecuteAutomationRunJob | Workspace chat provider | 10+11+15 |
| Run chatbot automation node | AutomationEngine.executeRunChatbot() | Sync (Phase 15 adds async) | automation / ExecuteAutomationRunJob | Workspace chat/embed | 10+11+15 |
| Workflow generation | WorkflowGenerator -> LlmGateway.chat() | Sync (user-initiated) | N/A | Workspace chat provider | 11 |

Phase 15 AI Queue Wiring Required:
1. IndexDocumentJobHandler must call DocumentIndexer.indexDocument(documentId, workspaceId) - currently stub
2. ExecuteAutomationRunJobHandler must call AutomationEngine.executeRun(run) - currently stub
3. Both route through LlmGateway -> LlmManager -> current provider (no hardcoding)

---

## 7. Laravel AI Jobs

| Laravel Job | PHP File | Queue | Tries | Timeout | Trigger | AI Usage |
|-------------|----------|-------|-------|---------|---------|----------|
| IndexDocumentJob | app/Modules/AI/Jobs/IndexDocumentJob.php | ai | 3 | 120s | AiKnowledgeBaseController | Embed model (workspace config) |
| ExecuteAutomationRunJob | app/Modules/Automation/Jobs/ExecuteAutomationRunJob.php | automation | 3 | 120s | AutomationEngine | chat+embed (indirect via ai_reply, run_chatbot nodes) |

---

## 8. Phase 15 Implementation Gap Analysis

### What Is Already Implemented

| Component | Status |
|-----------|--------|
| QueueDispatcher | Implemented - dispatches to DB jobs table |
| QueueWorker | Implemented - polling loop, retry/backoff, failed job logging |
| JobRegistry | Implemented - auto-discovers all JobHandler beans |
| JobHandler interface | Implemented |
| JobPayload | Implemented - UUID, jobType, maxTries, backoff, data |
| Job model + JobRepository | Implemented |
| FailedJob model + FailedJobRepository | Implemented |
| SchedulerConfig | Implemented - @EnableScheduling |
| SystemSchedulerTasks | Implemented - 12 scheduled tasks |
| All 22 job handlers | Registered but STUBS (log only) |
| Phase15 integration tests | 5 tests implemented and passing |

### Critical Gaps (Must Fix in Phase 15)

| Gap | Severity | Required Action |
|-----|----------|----------------|
| All 22 job handlers are stubs | CRITICAL | Wire real services into each handler |
| IndexDocumentJobHandler doesn't call DocumentIndexer | CRITICAL | Inject DocumentIndexer, call indexDocument() |
| ExecuteAutomationRunJobHandler doesn't call AutomationEngine | CRITICAL | Inject AutomationEngine, call executeRun() |
| ProcessInboundMessageJobHandler doesn't call WhatsappDriver | CRITICAL | Inject and wire WhatsappDriver |
| 19 other handlers are stubs | CRITICAL | Wire all remaining handlers |
| No WorkspaceExportService for export job | MEDIUM | Create or verify service |
| Billing/notification tasks inline in scheduler | MEDIUM | Wire billing/notification services |
| No retry in Java HTTP providers | MEDIUM | Add retry or rely on queue-level retry |
| No FOR UPDATE SKIP LOCKED in job polling | MEDIUM | Verify MySQL 8 / add optimistic locking |
| No distributed lock for withoutOverlapping tasks | MEDIUM | Implement via Spring or DB flag |
| Missing 2 billing charge-recurring cron variants | MEDIUM | Add to SystemSchedulerTasks |

---

## 9. Queue Architecture Summary

### Queue Names (Java <-> Laravel)

| Queue | Primary Jobs | Workers |
|-------|-------------|---------|
| default | DispatchWebhookJob, GenerateWorkspaceExportJob, ecommerce sync | 2 |
| whatsapp | ProcessInboundMessageJob, ProcessInboundInboxMessageJob, TemplateSyncJob | 3 |
| broadcast | LaunchCampaignJob, DispatchCampaignChunk, SendCampaignMessage, FinalizeCampaign | 2 |
| ai | IndexDocumentJob | 2 |
| social | DispatchScheduledPosts, PublishSocialPost, RefreshSocialTokens | 2 |
| leads | ScrapeLeadsJob | 1 |
| automation | ExecuteAutomationRunJob, ProcessEcommerceWebhook, CheckAbandonedCart | 2 |

### Retry / Backoff Summary

| Job | Tries | Backoff |
|-----|-------|---------|
| DispatchWebhookJob | 5 | 60,300,3600,86400,86400 |
| IndexDocumentJob | 3 | default |
| ExecuteAutomationRunJob | 3 | default |
| ProcessInboundMessageJob | 5 | 30,60,120,240,300 |
| SendCampaignMessageJob | 3 | 60 |
| FinalizeCampaignJob | 60 | self-reschedule +1min |
| CheckAbandonedCartJob | 2 | delayed 30min |
| ScrapeLeadsJob | 2 | default |

### Delayed Jobs

| Job | Delay | Purpose |
|-----|-------|---------|
| CheckAbandonedCartJob | 30 minutes | Cart conversion window |
| SendCampaignMessageJob | 0 to N*100ms | Rate limit ~10 msg/s |
| FinalizeCampaignJob | +1 minute self-reschedule | Poll until recipients settle |

---

## 10. Phase 15 Test Strategy

All AI-related queue tests must mock LlmGateway. No real AI API calls.

| Test | Mock | Assertion |
|------|------|-----------|
| IndexDocumentJob dispatches and calls DocumentIndexer | LlmGateway mocked | Handler invokes indexDocument() |
| IndexDocumentJob LLM uses current workspace provider | LlmGateway capture args | workspaceId passed, no hardcoded model |
| ExecuteAutomationRunJob calls AutomationEngine | LlmGateway mocked | Run status updated |
| AI job retry on LLM failure | LlmGateway throws then succeeds | Job retried, eventually completes |
| AI job fails permanently after max retries | LlmGateway always throws | Job in failed_jobs table |
| Delayed CheckAbandonedCartJob | None | available_at = now + 1800s |
| Scheduled campaigns cron | None | Jobs appear in broadcast queue |
| Provider resolution via LlmManager | Mock AiProviderConfigRepository | Correct provider from config |
| Embedding job uses embed-capable provider | Mock LlmManager | forWorkspaceEmbed() called |
| Model name from ai_provider_configs reaches provider | Mock provider capture | Constructor gets default_model_chat value |

Regression target:
- Phase 1-14 existing: 129 tests
- Phase 15 new: ~15-20 tests
- Total: >=144 tests, 0 failures

---

## 11. Old / Stale Model References Found

| Location | Reference | Type | Action Required? |
|----------|-----------|------|-----------------|
| OpenAiProvider.java:25 | "gpt-4o-mini" | Fallback default | No - correct |
| OpenAiProvider.java:26 | "text-embedding-3-small" | Fallback default | No - correct |
| AnthropicProvider.java:23 | "claude-3-haiku-20240307" | Fallback default | No - correct |
| GeminiProvider.java:24 | "gemini-1.5-flash" | Fallback default | No - correct |
| GeminiProvider.java:25 | "text-embedding-004" | Fallback default | No - correct |
| AiParityIntegrationTest.java:119,121,139,246 | "gpt-4o-mini" | Mock label | Update only if asserting new model name |
| migration/ai-provider-mapping.md | All Phase 11 model names | Documentation | Update when new model confirmed |
| migration/ai-inventory.md | Phase 11 model names | Documentation | Update when new model confirmed |
| php/.env.example:120 | OPENAI_MODEL=gpt-4o-mini | Laravel env example | Out of scope for Java migration |

---

## 12. Risks

| Risk | Severity | Mitigation |
|------|----------|-----------|
| New AI model not yet specified | CRITICAL | Confirm new model name before Phase 15 implementation |
| Embedding dimension mismatch if model changed | CRITICAL | Document re-indexing; do NOT auto-migrate |
| All 22 job handlers are stubs | CRITICAL | Wire real services in Phase 15 |
| No HTTP retry in Java providers | MEDIUM | Add retry or rely on queue-level retry |
| Single-threaded queue worker | MEDIUM | Acceptable for Phase 15; scale in future phases |
| withoutOverlapping not in Java scheduler | MEDIUM | Add distributed lock for billing/campaign tasks |

---

## 13. Phase Boundaries (Confirmed)

**Phase 15 (CURRENT):**
- Queue infrastructure wiring (all 22 handlers get real logic)
- Scheduler completing billing/notification/cleanup tasks
- AI jobs routing through LlmGateway (no hardcoded model)
- Failed job persistence
- Retry/backoff
- Delayed jobs
- Concurrency/overlap protection for scheduler tasks

**Phase 16 (NEXT - DO NOT IMPLEMENT NOW):**
- Pusher / Laravel Echo
- WebSockets
- Realtime channels
- Presence
- Realtime events / typing indicators

**Phase 17 (FUTURE):**
- Full React + Java E2E regression
- Browser workflows
- Frontend/backend parity

---

## 14. Summary

| Topic | Finding |
|-------|---------|
| Current AI Provider | Multi-provider: OpenAI + Anthropic + Gemini, workspace-driven config |
| Current Chat Model | Configuration-driven from ai_provider_configs.default_model_chat |
| Current Embedding Model | Configuration-driven from ai_provider_configs.default_model_embed |
| What Changed Since Phase 11 | NOTHING in repository - model change not detected in code |
| Old Model References | 5 fallback defaults (safe), 4 mock labels in tests (safe) |
| AI Architecture Compatibility | Fully compatible - provider-agnostic by design |
| Embedding Compatibility | Cannot confirm until new model specified |
| AI Background Jobs | IndexDocumentJob (ai queue), ExecuteAutomationRunJob (automation queue, indirect) |
| Queue Driver | database (dev/test), redis (prod via Docker Compose) |
| Total Laravel Jobs | 22 queue jobs + 13 queued notifications = 35 async items |
| Total Scheduled Tasks | 14 |
| Queue Tables | jobs, failed_jobs, job_batches (unused) - no schema changes needed |
| Retry Policies | 1 to 60 tries depending on job |
| Delayed Jobs | CheckAbandonedCart 30min, SendCampaignMessage throttle, FinalizeCampaign +1min |
| Failed Jobs | DB-backed via failed_jobs table - implemented |
| Concurrency / Locks | withoutOverlapping needed for 7 scheduler tasks - NOT YET implemented |
| Phase 15 Scope | Wire all 22 handler stubs, complete scheduler, AI queue routing |
| Phase 16 Scope | Pusher/Echo/WebSocket/Realtime |
| Existing Dependencies | All Phases 7-14 services must be injected into handlers |
| Testing Strategy | Mock LlmGateway, assert config-driven model resolution, preserve 129 tests |
| Re-indexing Required | Unknown until new embedding model confirmed |
