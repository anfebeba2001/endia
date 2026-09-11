# Data Model: Accurate Step-by-Step Snack Waiting Messages

**Database**: MySQL 8.x
**ORM**: Spring Data JPA + Hibernate 6.x
**Persistence change**: **NONE.** No new entity, column, table, index, enum column, or migration is introduced. `ddl-auto=update` produces no delta.

> This document captures the **conceptual** model the feature reasons about, then states explicitly that no schema change is required. It is compatible with `/specs/001-batch-mvp-enhancements/data-model.md`, `/specs/002-snack-retry-bound/data-model.md`, and `/specs/005-batch-snack-generation-fix/data-model.md`. The `StudySession` / `StudySessionItem` / `Snack` / `Subtopic` / `Theme` / `GenerationStatus` / `BatchStatus` model is reused unchanged.

---

## Conceptual Model

The feature reasons about five concepts. None of them map to a new database table.

```
Batch (StudySession) 1 ── aggregates ── * Generation Targets (StudySessionItem)
StudySessionItem * ── references ── 1 Subtopic
Generation Target 1 ── while GENERATING ── 1 Snack Generation Phase   # in-memory only
Wait Message 1 ── mapped from ── 1 Snack Generation Phase             # static label
Batch Status Response 1 ── carries ── * per-item phase (nullable)
```

---

## 1. Snack Generation Phase

The real step an in-progress snack is in. **New domain enum, not persisted.**

| Value | Real code point where it is set |
|-------|---------------------------------|
| `RESOLVING_CONTEXT` | `SnackService.generateAndPersist`, immediately before `subtopic.getTheme().getPrompt()` |
| `AWAITING_MODEL` | `SnackService.generateValidSnack`, immediately before each `callGenerateSnack(...)` (initial and the one re-roll) |
| `ASSEMBLING` | `SnackService.generateValidSnack`, after `isValid` passes, before `new Snack(...)` |
| `PERSISTING` | `SnackService.generateAndPersist`, immediately before `snackRepository.save(snack)` |

- **Representation**: `com.endia.domain.enums.SnackGenerationPhase` (pure enum, zero dependencies).
- **Lifecycle**: absent (cleared) when the item is `PENDING`/`READY`/`FAILED`; present only while `snackStatus == GENERATING`.
- **Ownership**: per generation target, keyed by subtopic id.
- **Not persisted**: never written to a column; never serialized to the DB.

---

## 2. Phase Registry (runtime, in-memory)

| Attribute | Representation | Notes |
|-----------|----------------|-------|
| key | `UUID subtopicId` | The identity the API already exposes as `BatchItem.subtopicId` |
| value | `SnackGenerationPhase` | Current step |
| storage | `ConcurrentHashMap<UUID, SnackGenerationPhase>` | Thread-safe; worker writes, request thread reads |
| owner | `SnackPhaseRegistry` (`infrastructure/tracking`, `@Component`) | Application services depend on it via constructor injection (existing pragmatic style) |
| lifecycle | `update` on each code point; `clear` before and in `finally` after each `runBatch` item | Guarantees no phase survives a settled item |

**Isolation rule (FR-008)**: the phase is keyed per subtopic id; one item's phase is never applied to another.

**Assumption**: one `StudySessionItem` per subtopic per batch. If a future feature allows duplicates, the key must become the item id.

---

## 3. Batch Item (StudySessionItem) — unchanged persistence

Existing entity; **no new fields**.

| Attribute | Existing representation | Notes |
|-----------|-------------------------|-------|
| item identity | `StudySessionItem.id`, `session_id`, `subtopic_id` | Unchanged |
| snack status | `StudySessionItem.snackStatus` (`PENDING`/`GENERATING`/`READY`/`FAILED`) | Unchanged; the phase is exposed only while `GENERATING` |
| snack reference | `StudySessionItem.snack` | Unchanged |
| snack cause | `StudySessionItem.snackCause` (`LlmErrorCause`) | Unchanged |
| quiz status/cause | `StudySessionItem.quizStatus` / `quizCause` | Unchanged |
| **snack phase** | *not a column* | Runtime-only, held in the registry |

---

## 4. Batch Status Response (contract)

The payload the snack screen polls. **One additive nullable field.**

| Field | Type | Notes |
|-------|------|-------|
| `batchId` | `string (UUID)` | Unchanged |
| `themeId` | `string (UUID)` | Unchanged |
| `status` | `string` | Unchanged (`IN_PROGRESS`/`PARTIAL`/`COMPLETE`/`FAILED`) |
| `retrying` | `boolean` | Unchanged |
| `items` | `array` | Unchanged |
| `items[].subtopicId` | `string (UUID)` | Unchanged |
| `items[].subtopicName` | `string` | Unchanged |
| `items[].snackStatus` | `string` | Unchanged |
| `items[].snack` | `object \| null` | Unchanged |
| `items[].snackCause` | `string \| null` | Unchanged |
| `items[].quizStatus` | `string` | Unchanged |
| `items[].quizCause` | `string \| null` | Unchanged |
| **`items[].snackPhase`** | `string \| null` | **NEW.** One of `RESOLVING_CONTEXT`/`AWAITING_MODEL`/`ASSEMBLING`/`PERSISTING` while `snackStatus == GENERATING`; `null` otherwise |

**Rule (FR-003/FR-004)**: the phase is reported per item and only while the item is actively generating. For a `PENDING`/`READY`/`FAILED` item the server returns `null`.

---

## 5. Wait Message

The user-facing text mapped from a phase. **Frontend-only, static.**

| Phase | Message |
|-------|---------|
| `RESOLVING_CONTEXT` | `Finding the topic context…` |
| `AWAITING_MODEL` | `Waiting for the AI response…` |
| `ASSEMBLING` | `Assembling your snack…` |
| `PERSISTING` | `Saving your snack…` |
| *no phase reported yet* | `Preparing your snack…` (existing generic fallback) |

- **Representation**: `PHASE_MESSAGES: Record<SnackGenerationPhase, string>` + `phaseMessage(phase)` in `frontend/src/app/models/batch.model.ts`.
- **Rules**: distinct per phase (FR-005); derived solely from the reported token, never from a timer (FR-006); updates as the phase changes (FR-007); static and content-free (FR-011).

---

## Runtime Model (not persisted)

| Concept | Type | Storage | Notes |
|---------|------|---------|-------|
| `SnackPhaseRegistry` | `ConcurrentHashMap<UUID, SnackGenerationPhase>` | backend process memory | Cleared on settle; lost on restart (correct) |
| `PHASE_MESSAGES` | `Record<phase, string>` | frontend bundle | Static copy |
| `items` / `snackPhase` | `BatchItem[]` signal | component memory | From each poll response |

---

## Persistence / Schema Change Statement

**No persistence or schema change is required or made by this feature.**

- No new JPA entity, field, column, index, or constraint.
- No new or changed persisted enum value (`GenerationStatus` and `BatchStatus` are unchanged).
- No repository query change.
- No Flyway/Liquibase migration (the project uses `ddl-auto=update`; there is nothing to apply).
- The phase registry and the message map live only in process/browser memory.

This matches the spec's Out-of-Scope clause and the prior features' no-migration stance.
