# Data Model: Batch Snack Generation Fix

**Database**: MySQL 8.x
**ORM**: Spring Data JPA + Hibernate 6.x
**Persistence change**: **NONE.** This is a defect fix. No new entity, column, table, index, enum value, or migration is introduced. `ddl-auto=update` produces no delta.

> This document captures the **conceptual** model the feature reasons about, then states explicitly that no schema change is required. It is compatible with `/specs/001-batch-mvp-enhancements/data-model.md` and `/specs/002-snack-retry-bound/data-model.md`; the `StudySession` / `StudySessionItem` / `Snack` / `Subtopic` / `Theme` / `GenerationStatus` / `BatchStatus` model is reused unchanged.

---

## Conceptual Model

The feature reasons about five concepts. None of them map to a new database table.

```
Theme 1 ── owns ── * Subtopic
Subtopic 1 ── has ── 1 Theme context (prompt)          # must be readable at generation time
Batch (StudySession) 1 ── aggregates ── * Generation Targets (StudySessionItem)
StudySessionItem * ── references ── 1 Subtopic (lazy)   # must not be dereferenced after detach
StudySessionItem 1 ── optionally references ── 1 Snack
Batch Status ∈ { IN_PROGRESS (only non-terminal), PARTIAL, COMPLETE, FAILED (terminal) }
```

---

## 1. Batch (StudySession)

Existing persistence identity: `StudySession`. Unchanged.

| Attribute | Existing representation | Notes |
|-----------|-------------------------|-------|
| batch identity | `StudySession.id` | Unchanged |
| theme | `StudySession.theme` (`@ManyToOne` LAZY) | Unchanged |
| aggregate status | `StudySession.status` (`BatchStatus`) | Unchanged; terminal statuses are now actually reached because the worker no longer deadlocks |
| items | `StudySession.items` (`@OneToMany` LAZY) | Unchanged |

---

## 2. Generation Target (StudySessionItem)

A single unit of generation — a snack for one subtopic within a batch. Existing persistence identity: `StudySessionItem`. No new fields.

| Attribute | Existing representation | Notes |
|-----------|-------------------------|-------|
| target identity | `StudySessionItem.id`, `session_id`, `subtopic_id` | Unchanged |
| related subtopic | `StudySessionItem.subtopic` (`@ManyToOne` LAZY) | Unchanged mapping; **now only its id is read on the detached instance** |
| snack status | `StudySessionItem.snackStatus` (`PENDING`/`GENERATING`/`READY`/`FAILED`) | Unchanged |
| snack reference | `StudySessionItem.snack` (`@OneToOne` LAZY) | Unchanged; populated on success |
| snack cause | `StudySessionItem.snackCause` (`LlmErrorCause`) | Unchanged; no longer always `UNAVAILABLE` |
| quiz status | `StudySessionItem.quizStatus` | Unchanged |
| quiz cause | `StudySessionItem.quizCause` | Unchanged |

**Persistence-scope rule (FR-001/FR-002)**: The `Subtopic` (and its lazy `Theme`) MUST be resolved inside a transaction at generation time. The batch worker passes only the subtopic **id** across the transaction boundary; it MUST NOT pass a detached `Subtopic` into a step that dereferences its lazy associations after the persistence context has closed.

---

## 3. Subtopic / Theme (related data)

The study slice and the parent theme whose prompt informs generation. Existing entities; no mapping change.

| Attribute | Existing representation | Notes |
|-----------|-------------------------|-------|
| subtopic identity | `Subtopic.id` | Read from the detached item safely (identifier only) |
| parent theme | `Subtopic.theme` (`@ManyToOne(fetch = LAZY)`) | Loaded lazily **inside** `SnackService.generateSingleSnack(UUID)`'s transaction |
| theme prompt | `Theme.prompt` | The exact value that previously caused `LazyInitializationException`; now always available |

**Optional read hardening (non-schema)**: `JpaStudySessionItemRepository.findBySessionId` may be annotated `@EntityGraph(attributePaths = {"subtopic"})` so the subtopic reference is initialized before detach. This is a fetch-shape annotation, not a schema change.

---

## 4. Batch Status

The aggregate state of a batch. Reused from `001`/`002`; **no new values**.

```
BatchStatus { IN_PROGRESS, PARTIAL, COMPLETE, FAILED }
```

| Status | Terminal? | Meaning |
|--------|-----------|---------|
| `IN_PROGRESS` | **NO** (the only non-terminal state) | At least one item is still `PENDING`/`GENERATING` |
| `PARTIAL` | **YES** | All items settled; a mix of `READY` and `FAILED` |
| `COMPLETE` | **YES** | All items settled; all `READY` |
| `FAILED` | **YES** | All items settled; none `READY` |

**Rule (FR-012)**: terminality is defined negatively — every status other than `IN_PROGRESS` is terminal. The frontend poll observable already completes on any terminal status (`002`); this feature ensures the worker actually writes a terminal status.

---

## 5. Generation Error

A typed failure surfaced to the user. Reused from `001`/`002`; no new fields.

| Attribute | Existing representation | Notes |
|-----------|-------------------------|-------|
| cause category | `LlmErrorCause` (`UNAVAILABLE`/`TIMEOUT`/`NETWORK`/`QUOTA`/`MALFORMED`) | Unchanged |
| human-readable message | `ErrorService.fromCause` / existing banners | Unchanged; cause-named |
| associated target | `StudySessionItem.subtopicId` / item key | Unchanged |
| user-safe | generic cause strings | FR-019: no credentials, keys, raw prompts, or provider payloads |

---

## Runtime Model (not persisted)

The two root-cause fixes introduce runtime-only concepts. They are **not** stored and are **not** schema.

### Executors

| Pool | Thread prefix | Role | Sizing | Rejection policy |
|------|---------------|------|--------|------------------|
| `batchExecutor` | `batch-worker-` | Top-level `@Async runBatch` workers only | core=2, max=8, queue=50 (unchanged) | default (Abort) — must not run the batch inside the create request/transaction |
| `quizExecutor` | `quiz-worker-` | Nested per-subtopic quiz prefetch (new) | core=4, max=16, queue=100 | `CallerRunsPolicy` — liveness valve; the batch worker can always make progress |

**Invariant (FR-004/FR-005)**: nested work MUST NOT be submitted only to the pool currently running the batch worker and then awaited. The two pools are distinct, so the batch worker can never be starved by its own children; the bounded join additionally guarantees it proceeds to aggregate a terminal status.

### Presentation state (frontend, in-memory)

| Signal | Type | Storage | Notes |
|--------|------|---------|-------|
| `creating` | `boolean` | component memory | True until `POST /api/batches` returns |
| `polling` | `boolean` | component memory (new) | True from create/retry success until the first terminal/error; gates the empty state |
| `items` | `BatchItem[]` | component memory | Empty until the first poll response |
| `batchStatus` | `string` | component memory | `IN_PROGRESS` default; set from each poll |
| `retrying` | `boolean` | component memory | `status.retrying || retryService.hasInFlight()` (from `002`) |

**Empty-state rule (FR-009/FR-010/FR-011)**: the empty state renders only when `!creating && !polling && items.length === 0 && batchStatus !== 'IN_PROGRESS' && !errorMessage` — i.e. a genuinely terminal batch that produced no ready snacks. It MUST NOT render during creation, before the first poll response, or while the batch is `IN_PROGRESS`.

---

## Persistence / Schema Change Statement

**No persistence or schema change is required or made by this feature.**

- No new JPA entity, field, column, index, or constraint.
- No new or changed enum value (`BatchStatus` and `GenerationStatus` are unchanged).
- No repository query change is required; the optional `@EntityGraph` is a fetch-shape annotation only.
- No Flyway/Liquibase migration (the project uses `ddl-auto=update`; there is nothing to apply).
- The `polling`/`creating` flags and the retry budget live only in frontend memory for the browser session.

This matches the spec's Out-of-Scope clause: *"Persistence/schema changes ... unless a later implementation decision requires them."* The implementation decision here is explicitly **not** to change persistence.
