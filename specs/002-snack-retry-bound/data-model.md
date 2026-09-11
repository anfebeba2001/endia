# Data Model: Bounded Snack Reattempts and Polling Termination

**Database**: MySQL 8.x
**ORM**: Spring Data JPA + Hibernate 6.x
**Persistence change**: **NONE.** This is a bug-fix feature. No new entity, column, table, index, enum value, or migration is introduced. `ddl-auto=update` produces no delta. The attempt budget is transient, in-memory, frontend state and is intentionally not persisted.

> This document captures the **conceptual** model that the feature reasons about, then states explicitly that no schema change is required. It is compatible with `/specs/001-batch-mvp-enhancements/data-model.md`; the `StudySession` / `StudySessionItem` / `GenerationStatus` / `BatchStatus` model is reused unchanged.

---

## Conceptual Model

The feature reasons about four concepts. None of them map to a new database table.

```
Generation Target 1 ── owns ── 1 Attempt Budget (per cycle, max 3)
Generation Target 1 ── reports ── 0..1 Generation Error (on failure)
Batch (StudySession) 1 ── aggregates ── * Generation Targets (StudySessionItem)
Batch Status ∈ { IN_PROGRESS (only non-terminal), PARTIAL, COMPLETE, FAILED (terminal) }
```

---

## 1. Generation Target

A single unit of generation tracked for reattempts — **a snack for one subtopic within a batch**. Existing persistence identity: `StudySessionItem` (`session_id` + `subtopic_id`). No new fields.

| Attribute (conceptual) | Existing representation | Notes |
|------------------------|-------------------------|-------|
| target identity | `StudySessionItem.id`, `session_id`, `subtopic_id` | Unchanged |
| current state | `StudySessionItem.snackStatus` (`PENDING`/`GENERATING`/`READY`/`FAILED`) | Unchanged |
| last error cause | `StudySessionItem.snackCause` (`LlmErrorCause`) | Unchanged; user-safe typed cause |
| attempt count | **frontend in-memory only** (`SnackRetryService.attempts`) | NOT persisted |
| in-flight flag | **frontend in-memory only** (`SnackRetryService.inFlight`) | NOT persisted |

**State machine (frontend, per cycle)**:

```
            explicit failure observed            canRetry (attempts < 3, not in-flight)
  idle ──► in-progress ──► failed ─────────────────────────────► new cycle (attempts++)
                              │
                              │ attempts == 3 (exhausted)
                              ▼
                           exhausted (terminal error; progress stops)
                              │
                              │ manual retry (resetBudget)
                              ▼
                           new cycle (attempts = 1)
```

Only the *snack* status drives snack retry. Quiz prefetch is out of scope for this feature's retry bound.

---

## 2. Attempt Budget

The bound applied to a generation target **per cycle**: **maximum 3 user-visible automatic generation cycles** (initial + at most 2 automatic reattempts), after which the target is exhausted and only a manual retry is possible.

| Field | Type | Storage | Constraints | Notes |
|-------|------|---------|-------------|-------|
| `attempts` | `Map<targetId, number>` | frontend memory (`SnackRetryService`) | `0 ≤ attempts ≤ 3` | Initial cycle = 1; each automatic reattempt increments |
| `inFlight` | `Set<targetId>` | frontend memory | at most one cycle per target | FR-007 duplicate guard |

**Rules**:
1. The initial batch registers attempt 1 for every selected target.
2. An automatic reattempt is issued only on an explicit failure and only while `attempts < 3` and `!inFlight`.
3. When `attempts == 3` and the cycle fails, the target is exhausted: a terminal error naming the cause is shown and no 4th cycle starts (FR-002/FR-005).
4. A **manual retry** resets `attempts` to 0 and starts a fresh cycle (attempt 1), giving a fresh 3-attempt budget (FR-006).
5. The budget is **per cycle**, not lifetime: each cycle (initial or user-initiated) allows up to 3 automatic attempts.

**Layering note**: the application-level budget counts only user-visible re-initiation **cycles**. The `RetryingLlmProvider` internal retry (retryable-only, up to 3 internal attempts with backoff/jitter) is a separate, transparent mechanism preserved unchanged (FR-013/FR-014). Worst case is 3 cycles × 3 internal LLM tries, but only 3 attempts are user-visible.

---

## 3. Batch Status

The aggregate state of a batch. Reused from `001`; **no new values**.

```
BatchStatus { IN_PROGRESS, PARTIAL, COMPLETE, FAILED }
```

| Status | Terminal? | Meaning |
|--------|-----------|---------|
| `IN_PROGRESS` | **NO** (the only non-terminal state) | At least one item is still `PENDING`/`GENERATING` |
| `PARTIAL` | **YES** | All items settled; a mix of `READY` and `FAILED` |
| `COMPLETE` | **YES** | All items settled; all `READY` |
| `FAILED` | **YES** | All items settled; none `READY` |

**Rule (FR-008/FR-009)**: terminality is defined **negatively** — every status other than `IN_PROGRESS` is terminal. The frontend poll observable completes on any terminal status.

---

## 4. Generation Error

A typed failure surfaced to the user. Reused from `001`; no new fields.

| Attribute (conceptual) | Existing representation | Notes |
|------------------------|-------------------------|-------|
| cause category | `LlmErrorCause` (`UNAVAILABLE`/`TIMEOUT`/`NETWORK`/`QUOTA`/`MALFORMED`) | Unchanged |
| human-readable message | `ErrorService.fromCause` / existing banners | Unchanged; cause-named |
| associated target | `StudySessionItem.subtopicId` / item key | Unchanged |
| user-safe | all messages are generic cause strings | FR-015: no credentials, keys, raw prompts, or provider payloads |

A **terminal** generation error is shown when a target's automatic budget is exhausted (FR-005) or when the polling window elapses (FR-010). It stops progress and names the cause.

---

## Persistence / Schema Change Statement

**No persistence or schema change is required or made by this feature.**

- No new JPA entity, field, column, index, or constraint.
- No new or changed enum value (`BatchStatus` and `GenerationStatus` are unchanged).
- No repository query change.
- No Flyway/Liquibase migration (the project uses `ddl-auto=update`; there is nothing for it to apply).
- The attempt budget and in-flight guard live only in the frontend `SnackRetryService` for the duration of the browser session/cycle. Losing them on reload is acceptable: a reload abandons the in-flight batch and starts fresh, which is consistent with the MVP's anonymous, stateless usage.

This matches the spec's Out-of-Scope clause: *"Introducing persistence/schema changes for attempt counters unless an implementation decision later requires it."* The implementation decision here is explicitly **not** to persist.

---

## Backend Change (non-schema)

The only backend change is a derived, non-persistent boolean in `BatchService.getStatus`:

| Field | Before | After |
|-------|--------|-------|
| `retrying` | `session.getStatus() == BatchStatus.IN_PROGRESS` (whole batch lifetime) | `any item snackStatus == GENERATING \|\| quizStatus == GENERATING` (active work/retries) |

This changes no stored data and no DTO shape — only how the response field is computed. See `contracts/batch-status.md` for the updated semantics (FR-016).
