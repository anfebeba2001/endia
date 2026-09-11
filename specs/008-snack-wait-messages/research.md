# Research: Accurate Step-by-Step Snack Waiting Messages

**Phase 0 output** for `/specs/008-snack-wait-messages`. Each section records the Decision, Rationale, and Alternatives, grounded in the real code:
`backend/src/main/java/com/endia/application/service/SnackService.java`,
`backend/src/main/java/com/endia/application/service/BatchService.java`,
`backend/src/main/java/com/endia/interfaces/rest/BatchController.java`,
`backend/src/main/java/com/endia/interfaces/rest/dto/BatchStatusApiResponse.java`,
`frontend/src/app/models/batch.model.ts`, and
`frontend/src/app/pages/snacks/snacks.component.{ts,html}`.

The spec is an observability feature over the existing `001`/`002`/`005` batch flow. The governing constraint is the smallest correct change set: no schema change, no new statuses, no new endpoint, no LLM-algorithm change, no snack-page redesign, and preservation of the bounded retry/polling/empty-state behavior. The separate liveness/termination concern (Task 9) is out of scope.

---

## R1. Where the per-item phase lives

**Decision**: A transient, in-memory `SnackPhaseRegistry` (`infrastructure/tracking`) keyed by **subtopic id**, plus a `SnackPhaseListener` application port that `SnackService` invokes at the real code points. No DB migration.

**Rationale**:
- The phase is meaningful only while an item is actively generating and must be absent once the item is READY/FAILED (FR-004). Persisting it would add a `study_session_item.snack_phase` column for volatile state that is never needed across a restart, violating MVP-first and requiring a migration.
- Keying by subtopic id matches the identity the API already exposes (`BatchItem.subtopicId`) and the 1:1 item↔subtopic relation within a batch, so `SnackService` needs no new identifier and per-item isolation (FR-008) is automatic. The frontend keys its rendering on the same field.
- A `ConcurrentHashMap` is safe because the `@Async` worker writes and the HTTP request thread reads; `ConcurrentHashMap` provides the happens-before needed for the enum reference. Reads are O(1) on the poll path.
- Clearing is explicit: `BatchService.runBatch` clears the entry before each generation and in a `finally` after it, and `getStatus` only exposes the phase when `snackStatus == GENERATING` (defence in depth). A restart simply loses in-progress phases, which is correct for a background batch.

**Alternatives**:
- **Persist `snackPhase` on `StudySessionItem`**: rejected. Requires a schema migration for volatile state; the spec allows a shape change but the plan's explicit decision is the smallest change with no migration.
- **Store the phase on `StudySession`**: rejected. The phase is per item, not per batch; a single field cannot represent concurrent per-item phases (FR-008).
- **Derive the phase from elapsed time or status age**: rejected outright by FR-002/SC-003.
- **A dedicated `snack_phase` field on `GenerationStatus`**: rejected. `GenerationStatus` is a persisted, coarse lifecycle enum shared with quiz; overloading it would change the DB column semantics and existing contracts.

---

## R2. How progression reaches the exact code points

**Decision**: Introduce an application-layer functional port:

```java
@FunctionalInterface
public interface SnackPhaseListener {
    SnackPhaseListener NOOP = phase -> {};
    void onPhase(SnackGenerationPhase phase);
}
```

`SnackService` gains a listener-aware overload (`generateSingleSnack(UUID subtopicId, SnackPhaseListener listener)` and a private `generateAndPersist(Subtopic, SnackPhaseListener)`), and emits at the real lines:

| Phase | Code point (current source) |
|-------|-----------------------------|
| `RESOLVING_CONTEXT` | before `subtopic.getTheme().getPrompt()` in `generateAndPersist` |
| `AWAITING_MODEL` | before each `callGenerateSnack(...)` in `generateValidSnack` (initial **and** the one re-roll) |
| `ASSEMBLING` | after `isValid` passes, before `new Snack(...)` |
| `PERSISTING` | before `snackRepository.save(snack)` in `generateAndPersist` |

`BatchService.runBatch` builds `phase -> phaseRegistry.update(subtopicId, phase)` for the current item and passes it to the listener-aware overload.

**Rationale**:
- The listener keeps `SnackService` decoupled from the registry (a batch-only storage concern). Single-shot overloads delegate with `SnackPhaseListener.NOOP`, so the single-shot contract is byte-for-byte unchanged (spec edge case). The ordering is unit-testable with a plain recording lambda and no Spring context.
- The four points are the only truthful places to advance: they bracket the context read, the network call(s), the assembly/validation, and the DB write. No phase is advanced on a timer or poll count (FR-002).
- The re-roll is part of `ASSEMBLING`/validation, so the sequence `AWAITING_MODEL → AWAITING_MODEL → ASSEMBLING` is exactly the real behavior, not a fabricated extra step.

**Alternatives**:
- **Inject `SnackPhaseRegistry` into `SnackService` with an "advance only if begun" rule**: rejected. More implicit, couples the generator to batch storage, and complicates single-shot semantics.
- **AOP/`@EventListener` around `LlmProvider`**: rejected. It cannot observe context resolution or persistence, and it hides the real code points behind a proxy.
- **Advance only in `BatchService`**: rejected. `BatchService` sees only "before/after `generateSingleSnack`"; any phases set there would be approximations, violating FR-002.

---

## R3. Phase enum and user-facing messages

**Decision**: Domain enum `SnackGenerationPhase { RESOLVING_CONTEXT, AWAITING_MODEL, ASSEMBLING, PERSISTING }`. The backend transports the enum name only; the frontend maps it to static text:

| Phase | Message |
|-------|---------|
| `RESOLVING_CONTEXT` | `Finding the topic context…` |
| `AWAITING_MODEL` | `Waiting for the AI response…` |
| `ASSEMBLING` | `Assembling your snack…` |
| `PERSISTING` | `Saving your snack…` |

The frontend falls back to the existing `Preparing your snack…` **only** when no phase is reported yet (before the first poll or a skipped phase), never in place of a reported phase.

**Rationale**:
- The wire carries machine tokens, so copy can change without a backend release and no prose is stored or logged (FR-011).
- Messages are static and content-free: no generated text, prompt fragments, identifiers, keys, or provider payloads.
- The mapping is a pure function of the reported token, so the screen never invents, infers, or time-schedules a phase (FR-006).

**Alternatives**:
- **Send the message text from the backend**: rejected. Couples copy to the server and risks leaking generated content or prompts into the payload/logs.
- **Reuse a single message with a spinner**: rejected. That is the defect being fixed (FR-005/SC-002).

---

## R4. Contract change: nullable per-item `snackPhase`

**Decision**: Add a nullable `String snackPhase` to `BatchStatusApiResponse.BatchItem`. `BatchService.BatchItemStatus` gains `SnackGenerationPhase snackPhase`. `BatchService.getStatus` sets it to `phaseRegistry.current(subtopicId)` only when `snackStatus == GENERATING`, otherwise `null`. `BatchController.status` maps the enum to `.name()` (or `null`).

**Rationale**:
- A single additive nullable field is the smallest possible delta and keeps the response backward compatible (unknown-field tolerance; explicit `null` for settled items).
- Restricting it to `GENERATING` makes the field self-describing: a non-null `snackPhase` implies active generation, and its absence for READY/FAILED enforces FR-004 at the boundary.
- No change to `POST /api/batches`, `POST /api/snacks/generate`, `GenerationStatus`, `BatchStatus`, `LlmErrorCause`, or any quiz field.

**Alternatives**:
- **Nested `phase` object**: rejected. Needless structure for one token.
- **A separate `/phase` endpoint**: rejected. Extra round-trips and a new surface for data the poll already returns.
- **Reuse `snackStatus`**: rejected. It is a coarse lifecycle value and cannot express the four steps.

---

## R5. Poll-skipping and interval

**Decision**: Keep the existing `pollStatus` interval (~1.5 s). Do not shorten it during active generation.

**Rationale**:
- `ASSEMBLING` and `PERSISTING` are in-process and typically far shorter than the interval, so they may be skipped; the spec explicitly accepts this ("skipping is not a defect"). Each poll overwrites the displayed phase with the server's current value, so a stale/backdated phase is never shown (the edge-case requirement is "never display a stale phase once a newer phase has been reported").
- `RESOLVING_CONTEXT` and `AWAITING_MODEL` bracket the network-bound LLM call and are normally longer than one interval in real runs, so they are observable. `SC-002` ("distinct messages observable across a driven run") is met by these two in production plus the E2E spec that drives the mock through all four phases deterministically.
- Keeping the interval is the smallest change and avoids extra request volume for no guaranteed gain. `pollStatus` already accepts an `intervalMs` override for tests, so no production change is needed to drive phases quickly in E2E.

**Alternatives**:
- **Adaptive/shorter polling during generation**: rejected. More requests, larger change, not required by any SC.
- **Client-side phase timers/interpolation**: rejected. Explicitly forbidden by FR-002/FR-006 and would produce fake messages.

---

## R6. Frontend touchpoints and no-regression

**Decision**: Add the `SnackGenerationPhase` union, an optional `snackPhase?: SnackGenerationPhase | null` on `BatchItem`, and a `PHASE_MESSAGES` map + `phaseMessage()` helper to `models/batch.model.ts`; expose the helper from `snacks.component.ts`; render it in the per-item in-progress branch of `snacks.component.html` (currently the generic text at line 116). Leave the pre-first-poll empty-list block (line 25) generic. `batch.service.ts` needs no change (typed passthrough).

**Rationale**:
- The mapping is a pure function, so it is deterministic and testable, and the component renders only what the backend reported (FR-006/FR-007).
- Per-item rendering is inherent: the template already selects the current `BatchItem`, so one item's phase can never be shown for another (FR-008).
- The ready branch (`snackStatus === 'READY'`), failed branch with cause + retry, the `showEmptyState` gate, the `polling` flag, and `pollStatus` are all untouched, preserving FR-010/SC-005.

**Alternatives**:
- **A separate `snack-phase.service.ts`**: unnecessary for a static map; a const map in the model is simpler and importable.
- **Change `batch.service.ts`**: unnecessary; the added optional field flows through the existing typed `BatchStatusResponse`.

---

## R7. Persistence / schema impact

**Decision**: **No persistence or schema change.**

- No new JPA entity, column, index, constraint, or enum value. `StudySessionItem` is unchanged.
- The `SnackGenerationPhase` enum is a domain type, not a persisted column.
- The registry is in-memory (`ConcurrentHashMap`) and lives only for the process/batch lifetime.
- No repository query change and no Flyway/Liquibase migration (the project uses `ddl-auto=update`; there is nothing to apply).

**Rationale**: The phase is volatile, batch-scoped runtime state with no cross-restart meaning; persisting it would be over-engineering contrary to MVP-first. This matches the spec's Out-of-Scope clause and the prior features' explicit no-migration stance.

---

## R8. Task 9 boundary

**Decision**: Treat liveness/termination (Task 9) as an external dependency and out of scope.

**Rationale**: This feature assumes the batch already reaches a terminal status. The phase-clear contract (`finally` + `GENERATING`-only exposure) is robust whether or not a batch terminates, but if a batch never terminates the phase would remain set while generation is genuinely still active — which is truthful. Task 9 must preserve the clear-on-settle behavior when it lands.

---

## Open assumptions (flagged for the orchestrator)

1. **One item per subtopic per batch.** The registry is keyed by subtopic id; if a future feature allows the same subtopic twice in one batch, the key must become the `StudySessionItem` id and the DTO would need a stable item id.
2. **`ASSEMBLING`/`PERSISTING` may not be observable in real runs** because they are sub-interval. Accepted by the spec; proven observable via the E2E mock and backend unit tests.
3. **No frontend unit-test runner.** The pure mapping is covered by Playwright; if a runner is added later it can be unit-tested directly.
4. **`batch.service.ts` is intentionally unchanged**; the model addition is the single source of the new type.
