# Data Model: Guarantee Bounded Snack Generation

**Database**: MySQL 8.x
**ORM**: Spring Data JPA + Hibernate 6.x
**Persistence change**: **NONE.** This is a liveness/defect-prevention feature. No new entity, column,
table, index, enum value, or migration is introduced. `ddl-auto=update` produces no delta.

> This document captures the **configuration values** and the **per-item status/cause** the feature
> reasons about, then states explicitly that no schema change is required. It is compatible with
> `/specs/001-batch-mvp-enhancements/data-model.md`, `/specs/002-snack-retry-bound/data-model.md`, and
> `/specs/005-batch-snack-generation-fix/data-model.md`; the `StudySession` / `StudySessionItem` /
> `Snack` / `Subtopic` / `Theme` / `GenerationStatus` / `BatchStatus` / `LlmErrorCause` model is reused
> unchanged.

---

## 1. Timeout / Bound Configuration (external properties)

All bounds are configuration, not persisted data. Values below are the documented defaults; each is
overridable via an environment variable.

| Property | Default | Env override | Meaning | Status |
|----------|---------|--------------|---------|--------|
| `llm.timeout-seconds` | `30` | (Spring relaxed binding / `LLM_TIMEOUT_SECONDS`) | Per-call **connect + read** timeout applied to the outbound HTTP client (`SimpleClientHttpRequestFactory`). | Existing — preserved (FR-001/FR-002) |
| `batch.retry.max-attempts` | `3` | `BATCH_RETRY_MAX_ATTEMPTS` | Total attempts per provider call in `RetryingLlmProvider` (retryable failures only). | Existing — preserved (FR-004) |
| `batch.quiz-join-timeout-seconds` | `240` | `BATCH_QUIZ_JOIN_TIMEOUT_SECONDS` | Wall-clock bound on the parallel quiz prefetch join. | Existing `@Value`; now made explicit in `application.yml` |
| `batch.snack-timeout-seconds` | `240` | `BATCH_SNACK_TIMEOUT_SECONDS` | **NEW.** Per-subtopic wall-clock bound around `SnackService.generateSingleSnack` (includes retries + one re-roll). | **NEW** (FR-008/FR-011/FR-023) |

### Validation / normalization

| Input | Behavior |
|-------|----------|
| `batch.snack-timeout-seconds <= 0` | Normalized to the documented default `240` (the edge case must not hang or fail unpredictably). |
| `batch.retry.max-attempts < 1` | Already normalized to `1` inside `RetryingLlmProvider` (`Math.max(1, maxAttempts)`). |
| `llm.timeout-seconds` non-positive | Out of scope; the existing HTTP factory behavior is preserved. |

### Finite worst-case computation (FR-007)

```
per_subtopic_worst_case
  = re_roll_allowance × (max_attempts × per_call_timeout + backoff)
  = 2 × (3 × 30 s + ~3 s)
  ≈ 186 s
  ≤ batch.snack-timeout-seconds (default 240 s)
```

Where `re_roll_allowance = 2` (initial + at most one re-roll), `max_attempts = batch.retry.max-attempts`
= 3, `per_call_timeout = llm.timeout-seconds` = 30, and `backoff ≈ initial + factor` (1 s + 2 s).
The per-subtopic bound is therefore a true backstop that never fires during healthy (even
retry-heavy) generation but always fires for a provider that ignores the HTTP timeout.

---

## 2. Per-Item Generation Status (StudySessionItem)

Existing persistence identity: `StudySessionItem`. **No new fields.** The feature only changes *which*
values are written and *when*.

| Attribute | Existing representation | Notes |
|-----------|-------------------------|-------|
| snack status | `StudySessionItem.snackStatus` (`GenerationStatus`) | On per-subtopic timeout → `FAILED` |
| snack cause | `StudySessionItem.snackCause` (`LlmErrorCause`) | On per-subtopic timeout → `TIMEOUT` |
| quiz status | `StudySessionItem.quizStatus` (`GenerationStatus`) | On quiz-join timeout finalization → `FAILED` |
| quiz cause | `StudySessionItem.quizCause` (`LlmErrorCause`) | On quiz-join timeout finalization → `TIMEOUT` |

```
GenerationStatus { PENDING, GENERATING, READY, FAILED }   # unchanged
```

**Terminality rule**: `PENDING` and `GENERATING` are non-terminal; `READY` and `FAILED` are terminal.
At a terminal **batch** status, **no** item may be `PENDING`/`GENERATING` for either snack or quiz
(FR-014). The finalization step enforces this after the bounded quiz join.

---

## 3. Typed Failure Cause (LlmErrorCause)

Existing persistence identity: `StudySessionItem.snackCause` / `quizCause`. **No new enum value.**

```
LlmErrorCause { UNAVAILABLE, TIMEOUT, NETWORK, QUOTA, MALFORMED }   # unchanged
```

| Cause | When written by this feature | User-safe message (existing) |
|-------|------------------------------|------------------------------|
| `TIMEOUT` | Per-subtopic snack bound exceeded; quiz-join finalization of a stuck item | "The AI service took too long to respond. Please try again." |
| `UNAVAILABLE` | `snackExecutor` rejected the task (pool/queue saturated) | "The AI service is temporarily unavailable. Please try again shortly." |

`TIMEOUT` is reused deliberately: it already exists in the enum, the DTOs, the frontend `LlmErrorCause`
union, and the `ErrorService` message map, so no schema/DTO/UI change is required.

---

## 4. Batch Aggregate Status (StudySession)

Existing persistence identity: `StudySession.status`. **No new values.**

```
BatchStatus { IN_PROGRESS, PARTIAL, COMPLETE, FAILED }   # unchanged
```

| Status | Terminal? | Meaning |
|--------|-----------|---------|
| `IN_PROGRESS` | **NO** (the only non-terminal state) | At least one item is `PENDING`/`GENERATING` |
| `PARTIAL` | **YES** | All items settled; a mix of `READY` and `FAILED` |
| `COMPLETE` | **YES** | All items settled; all `READY` |
| `FAILED` | **YES** | All items settled; none `READY` (also the empty-batch guard outcome) |

**Rule (FR-012)**: the worker MUST write a terminal status. The finalization step guarantees the input
to `aggregateStatus` contains no non-terminal items, so the fall-through `IN_PROGRESS` branch is only
reachable while the worker is mid-run — never after it returns.

---

## Runtime Model (not persisted)

### Executors

| Pool | Thread prefix | Role | Sizing | Rejection policy |
|------|---------------|------|--------|------------------|
| `batchExecutor` | `batch-worker-` | Top-level `@Async runBatch` workers only | core=2, max=8, queue=50 (unchanged) | default (Abort) — must not run the batch inside the create request/transaction |
| `quizExecutor` | `quiz-worker-` | Nested per-subtopic quiz prefetch | core=4, max=16, queue=100 (unchanged) | `CallerRunsPolicy` (unchanged from 005) |
| `snackExecutor` | `snack-worker-` | **NEW.** Per-subtopic bounded snack generation | core=2, max=8, queue=50, daemon | default (Abort) — caller-runs would run the hang on the batch worker, defeating the bound |

**Invariant (FR-010)**: the batch worker MUST NOT execute a potentially hanging snack call on its own
thread. Generation runs on `snackExecutor`; the worker only awaits a bounded future.

### In-memory bound state

| Value | Type | Storage | Notes |
|-------|------|---------|-------|
| `snackTimeoutSeconds` | `int` | `BatchService` field (from config) | Per-subtopic wall-clock bound; normalized `<= 0` → 240 |
| `quizJoinTimeoutSeconds` | `int` | `BatchService` field (from config) | Existing join bound |

### Timeout data flow

```
BatchService.runBatch (batch-worker-)
  └─ generateSnackBounded(subtopicId)
       ├─ CompletableFuture.supplyAsync(..., snackExecutor)   -> snack-worker- thread
       │     └─ SnackService.generateSingleSnack (transactional)
       │           └─ RetryingLlmProvider.generateSnack (≤3 attempts, per-call HTTP timeout 30s)
       │                 └─ re-roll at most once on invalid content
       └─ .orTimeout(240s).join()
             ├─ success            -> snackStatus = READY
             ├─ LlmGenerationException -> snackStatus = FAILED, snackCause = typed cause
             └─ TimeoutException   -> snackStatus = FAILED, snackCause = TIMEOUT  (worker proceeds)

  ... quiz prefetch on quizExecutor with bounded join ...
  └─ finalize non-terminal items -> FAILED/TIMEOUT
  └─ aggregateStatus -> COMPLETE | PARTIAL | FAILED
```

---

## Persistence / Schema Change Statement

**No persistence or schema change is required or made by this feature.**

- No new JPA entity, field, column, index, or constraint.
- No new or changed enum value (`BatchStatus`, `GenerationStatus`, `LlmErrorCause` are unchanged).
- No repository query change.
- No Flyway/Liquibase migration (the project uses `ddl-auto=update`; there is nothing to apply).
- All new bounds are configuration values; the `snackExecutor` is a runtime bean.

This matches the spec's Out-of-Scope clause: *"Persistence/schema changes ... unless a later
implementation decision requires them."* The implementation decision here is explicitly **not** to
change persistence.
