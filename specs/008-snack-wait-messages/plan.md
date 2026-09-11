# Implementation Plan: Accurate Step-by-Step Snack Waiting Messages

**Branch**: `008-snack-wait-messages` | **Date**: 2026-09-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-snack-wait-messages/spec.md`

## Summary

The snack screen currently shows one generic "Preparing your snack…" message for the entire in-progress window. This feature replaces it with a distinct, accurate message per **real** generation phase, driven by the actual backend phase rather than a timer.

The backend generation path is `BatchService.runBatch` → `SnackService.generateSingleSnack(UUID)` → `generateAndPersist` → `generateValidSnack`/`callGenerateSnack`/`isValid` → `snackRepository.save`. The plan exposes the four real steps as a per-item phase using an **in-memory phase registry keyed by subtopic id** and a small application-layer `SnackPhaseListener` callback that `SnackService` invokes at the exact code points. No DB migration: the phase is transient runtime state, absent once an item settles.

The phase travels to the frontend as a new nullable `snackPhase` field on each `GET /api/batches/{batchId}` item. The frontend maps each phase to a static, user-safe message and renders it in place of the generic text. All existing ready/failed rendering, empty-state gating, bounded polling, and retry behavior are preserved unchanged. Task 9 (liveness/termination) is explicitly out of scope.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.x (frontend), Angular 21

**Primary Dependencies**:
- Backend: Spring Boot 3.4.x, Spring MVC, Spring Data JPA/Hibernate 6.x. **No new dependencies.** Reuses the existing `@Async` `batchExecutor` and the `002`/`005` batch flow.
- Frontend: Angular 21 standalone components + signals, `HttpClient`, RxJS (`interval`, `race`, `takeWhile`). **No new dependencies.**

**Storage**: MySQL 8.x — **no schema change**. The phase is in-memory runtime state (`ConcurrentHashMap`), never persisted. `StudySessionItem` is untouched; no new column, enum, or migration.

**Testing**: JUnit 5 + Mockito (backend unit), Spring Boot Test + Testcontainers MySQL (backend integration), Playwright (frontend E2E). No Karma/Jest runner exists under `frontend/src/app`, so frontend phase mapping is covered by Playwright plus a pure, trivially unit-testable helper.

**Target Platform**: Linux server (backend), modern browsers Chrome/Firefox/Safari latest 2 versions (frontend).

**Project Type**: Web application (monorepo: `backend/` + `frontend/`).

**Performance Goals**: A phase is visible on the very next poll after the backend enters the corresponding step (≤1 poll interval latency). Phase reads are O(1) in-memory lookups. Polling interval stays ~1.5 s; a batch settles and the phase is cleared within one interval of the item becoming READY/FAILED.

**Constraints**: No auth. No schema change. No new statuses. No new endpoint, query parameter, or request shape. No change to `POST /api/batches`, `POST /api/snacks/generate`, the single-shot snack contract, `RetryingLlmProvider`, `QuizService`, prompts, validation, or the `002` bounded retry/polling behavior. Must keep the existing Playwright suite and backend tests green. Phase messages/logs must never expose prompts, API keys, provider payloads, or personal data.

**Scale/Scope**: Single-user MVP. ~4 new backend files + 3 modified backend files + backend tests + 2 modified frontend files + 1 E2E spec + 1 helper extension. No DB, no new endpoint, no new dependency.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Evidence |
|---|-----------|--------|----------|
| I | **Spec-Driven Development** | PASS | spec.md defines 3 user stories, 12 FRs, 6 SCs. Every FR traces to a concrete file/code point (see Mapping). |
| II | **Clean Architecture (Layered)** | PASS | Phase progression lives in the **application** layer (`SnackService` invokes the listener at real code points; `BatchService` owns begin/clear and reads it in `getStatus`). The phase enum is pure **domain**. The registry implementation is **infrastructure**. The message presentation is the **interfaces** layer (`BatchController` + Angular component). |
| III | **LLM Abstraction First** | PASS | No `LlmProvider`, prompt, or provider change. The phase wraps the existing `callGenerateSnack` call; no provider-specific logic is added. |
| IV | **Quality Gate for Generated Content** | PASS | `generateValidSnack`/`isValid` and the single re-roll are preserved verbatim; only phase notifications are added around them. |
| V | **MVP-First, Iterate** | PASS | Smallest change: no persistence, no new statuses, no new endpoint, no new dependency, no UI redesign. Reuses existing poll/retry/empty-state logic. |

**All gates PASS. No violations to justify.**

## Complexity Tracking

> No constitution violations. No complexity justifications needed.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | N/A | N/A |

## Project Structure

### Documentation (this feature)

```text
specs/008-snack-wait-messages/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Phase 0 — decisions + rationale
├── data-model.md        # Phase 1 — conceptual model (no schema change)
├── quickstart.md        # Phase 1 — local run + phase-observation verification
├── contracts/           # Phase 1 — API contract
│   └── batch-status.md  # UPDATED: adds the per-item `snackPhase` field
├── checklists/
│   └── requirements.md  # Existing spec-quality checklist
└── tasks.md             # Phase 2 — NOT created by planner
```

### Source Code (repository root)

Only the marked files change. The rest of the batch flow is untouched.

```text
endia/
├── backend/
│   └── src/
│       ├── main/java/com/endia/
│       │   ├── domain/enums/
│       │   │   └── SnackGenerationPhase.java            # NEW: phase enum (zero deps)
│       │   ├── application/
│       │   │   ├── port/
│       │   │   │   └── SnackPhaseListener.java          # NEW: onPhase callback + NOOP
│       │   │   └── service/
│       │   │       ├── SnackService.java                # MOD: advance listener at real code points
│       │   │       └── BatchService.java                # MOD: own registry, pass listener, expose phase
│       │   ├── infrastructure/tracking/
│       │   │   └── SnackPhaseRegistry.java              # NEW: in-memory ConcurrentHashMap registry
│       │   └── interfaces/rest/
│       │       ├── BatchController.java                 # MOD: map phase to the DTO
│       │       └── dto/BatchStatusApiResponse.java      # MOD: add `snackPhase` to BatchItem
│       └── test/java/com/endia/
│           ├── application/service/
│           │   ├── SnackServiceTest.java                # MOD: ordered-phase assertions
│           │   └── BatchServiceTest.java                # MOD: phase exposure + clear assertions
│           └── application/integration/
│               └── BatchGenerationIntegrationTest.java  # MOD: phase visible then cleared
│
└── frontend/
    ├── e2e/
    │   ├── helpers.ts                                   # MOD: snackPhase on mock items
    │   └── snack-wait-messages.spec.ts                  # NEW: per-phase message E2E
    └── src/app/
        ├── models/batch.model.ts                        # MOD: SnackGenerationPhase + snackPhase + message map
        └── pages/snacks/
            ├── snacks.component.ts                      # MOD: expose phase -> message helper
            └── snacks.component.html                    # MOD: replace generic in-progress text
```

**Structure Decision**: Monorepo as-is. The phase **progression** belongs in the application layer: `SnackService` invokes a `SnackPhaseListener` at the exact code points where each real step begins, and `BatchService` drives the lifecycle (clear-before, clear-in-finally) and reads the registry in `getStatus`. The phase **enum** is a pure domain type (no dependencies). The **storage** is an infrastructure component (in-memory `ConcurrentHashMap`), matching the existing pragmatic injection style (services already depend on infrastructure repositories). The **presentation** is confined to the interfaces layer: `BatchController` maps the enum to a string on the DTO, and the Angular component maps the string to a static message. No new packages beyond `domain/enums`, `application/port`, and `infrastructure/tracking`.

## Architecture Decisions

### 1. In-memory phase registry keyed by subtopic id — no DB migration (FR-001/FR-002/FR-004)

A phase is transient: it exists only while an item is actively generating and must be gone once the item is READY/FAILED. Persisting it would require a new `StudySessionItem` column (and migration) for data that is meaningless after a restart and never read by anything but the live poll. The smallest correct mechanism is an in-memory registry.

- `SnackPhaseRegistry` (`infrastructure/tracking`, `@Component`) holds `ConcurrentHashMap<UUID, SnackGenerationPhase>` keyed by **subtopic id** — the same key the API and the frontend already use to identify an item (`BatchItem.subtopicId`). Keying by subtopic id satisfies FR-008 (per-item isolation) without changing any service signature, because `SnackService` already receives the subtopic id and the item↔subtopic relation is 1:1 in a batch.
- Methods: `update(UUID, SnackGenerationPhase)`, `SnackGenerationPhase current(UUID)` (nullable), `clear(UUID)`.

**Rationale**: No schema change (FR-004 is naturally satisfied by clearing the entry). O(1) reads on the poll path. Thread-safe because the `@Async` worker writes and the HTTP request thread reads. A restart simply loses in-progress phases, which is correct — the batch itself is a background task.

**Rejected**: (a) a `StudySessionItem.snack_phase` column — needs a migration for volatile state and violates MVP-first; (b) deriving the phase from elapsed time or status age — explicitly forbidden by FR-002; (c) storing the phase on `StudySession` — it is per item, not per batch (FR-008).

### 2. Progression via a `SnackPhaseListener` invoked at the real code points (FR-002/FR-012)

`SnackService` must advance the phase at the exact moment each step begins. Rather than inject the registry into `SnackService` (which would make single-shot generation aware of batch tracking), the plan adds a tiny application-layer port:

```java
@FunctionalInterface
public interface SnackPhaseListener {
    SnackPhaseListener NOOP = phase -> {};
    void onPhase(SnackGenerationPhase phase);
}
```

`SnackService` gains a listener-aware overload; the existing single-shot overloads delegate with `SnackPhaseListener.NOOP`, so they are byte-for-byte unchanged.

The listener is invoked at the real code points:

| Phase | Exact code point (current source) |
|-------|----------------------------------|
| `RESOLVING_CONTEXT` | in `generateAndPersist`, immediately before `subtopic.getTheme().getPrompt()` |
| `AWAITING_MODEL` | in `generateValidSnack`, immediately before each `callGenerateSnack(...)` (initial call **and** the one re-roll) |
| `ASSEMBLING` | in `generateValidSnack`, after `isValid` passes, immediately before constructing the `Snack` |
| `PERSISTING` | in `generateAndPersist`, immediately before `snackRepository.save(snack)` |

`BatchService.runBatch` creates a listener bound to the item's subtopic id and passes it to `snackService.generateSingleSnack(subtopicId, listener)`; it clears the registry entry before the call and in a `finally` after it, so the phase can never outlive the item (FR-004). A re-roll naturally re-emits `AWAITING_MODEL` then `ASSEMBLING`, matching the real flow.

**Rationale**: The listener keeps `SnackService` free of any registry/singleton dependency and makes the ordering unit-testable with a plain recording lambda (no Spring context). The `NOOP` default guarantees single-shot is unaffected (spec edge case: "single-shot snack generation has no batch phase and is unaffected"). Progression is in the application layer, satisfying FR-012.

**Rejected**: (a) injecting `SnackPhaseRegistry` into `SnackService` with a "no-op unless begun" rule — more implicit and couples the generator to batch storage; (b) an `@EventListener`/AOP around `LlmProvider` — cannot see the context-resolve or persist steps and hides the real code points; (c) advancing in `BatchService` only — it cannot see inside `generateAndPersist`, so phases would be approximations, violating FR-002.

### 3. Phase enum values and user-facing messages (FR-001/FR-005/FR-011)

Domain enum `SnackGenerationPhase`: `RESOLVING_CONTEXT`, `AWAITING_MODEL`, `ASSEMBLING`, `PERSISTING`.

Static, user-safe messages (frontend mapping — the backend sends only the enum name, never text):

| Phase | User-facing message |
|-------|---------------------|
| `RESOLVING_CONTEXT` | `Finding the topic context…` |
| `AWAITING_MODEL` | `Waiting for the AI response…` |
| `ASSEMBLING` | `Assembling your snack…` |
| `PERSISTING` | `Saving your snack…` |

**Rationale**: The backend transports a machine token; the interfaces layer owns the human text, so copy can change without a backend release and the wire payload carries no prose (FR-011). The messages contain no generated content, prompt fragments, or identifiers. The frontend falls back to the existing `Preparing your snack…` only when no phase is reported yet (e.g. before the first poll or a skipped phase), never as a substitute for a reported phase.

### 4. Contract delta: nullable `snackPhase` per item (FR-003/FR-004)

`BatchStatusApiResponse.BatchItem` gains a nullable `String snackPhase`. `BatchController.status` maps `i.snackPhase() == null ? null : i.snackPhase().name()`. `BatchService.getStatus` sets the phase to `phaseRegistry.current(subtopicId)` **only when `snackStatus == GENERATING`**, and `null` otherwise (defence-in-depth for FR-004 alongside the `finally` clear). `BatchService.BatchItemStatus` gains the enum field. No other field, type, or value changes.

**Rationale**: A single additive nullable field is the smallest contract delta and keeps the response shape backward compatible (existing clients ignore unknown fields; Jackson serializes `null` explicitly). Restricting it to `GENERATING` makes the field self-documenting: its presence implies active generation.

**Rejected**: (a) a nested `phase` object — needless structure for one token; (b) reusing `snackStatus` — it is coarse and cannot express the four steps; (c) a separate endpoint — extra round-trips and a new surface, contradicting MVP-first.

### 5. Poll-skipping: keep the existing ~1.5 s interval (FR-006/FR-007, SC-002)

**Decision**: Keep the existing `pollStatus(batchId, intervalMs = 1500, …)` interval. Do not shorten it during active generation.

**Rationale**: A phase shorter than the interval (typically `ASSEMBLING`/`PERSISTING`, which are in-process and sub-millisecond) may be skipped; the spec explicitly accepts this ("skipping is not a defect") and requires only that a newer phase never regresses to a stale one. Each poll overwrites the displayed phase with the server's current value, so there is no backdating. `SC-002` ("the distinct messages are observable across a driven run") is satisfied because `RESOLVING_CONTEXT` and `AWAITING_MODEL` bracket the network-bound LLM call and are normally longer than one interval in real runs, and the E2E spec drives the mock through all four phases deterministically. Shortening the interval would increase request volume for no guaranteed benefit and is a larger change.

**Rejected**: (a) adaptive polling (e.g. 300 ms during generation) — more requests and a larger change, not required by any SC; (b) client-side phase timers/interpolation — explicitly forbidden by FR-002/FR-006.

### 6. Frontend message mapping is pure and presentation-only (FR-005/FR-006/FR-007)

`models/batch.model.ts` gains the `SnackGenerationPhase` union, an optional `snackPhase?: SnackGenerationPhase | null` on `BatchItem`, and a `PHASE_MESSAGES` record plus a `phaseMessage(phase)` helper. `snacks.component.ts` exposes that helper to the template. `snacks.component.html` replaces the generic text at the per-item in-progress branch (currently line 116) with the phase message, and leaves the pre-first-poll empty-list block (line 25) generic because no item/phase exists yet. The existing `aria-live="polite"` container and the spinner in that in-progress block are preserved — only the text node changes, so the updated message remains announced to assistive tech. The failed-item retry affordance remains the unchanged native `<button>` and is therefore still keyboard-operable. `batch.service.ts` needs **no change**: it returns `BatchStatusResponse` as a typed passthrough, so the added optional field flows through automatically. Ready/failed branches, the empty-state gate, the retry affordance, and polling are untouched.

**Rationale**: The mapping is a pure function, so it is deterministic and testable, and the component only renders what the backend reported — it never infers or schedules a phase (FR-006). Per-item rendering is naturally achieved because the template iterates/selects by `BatchItem` (FR-008).

## Phase-by-Phase Implementation Plan

### Phase 0 — Domain + application phase plumbing (US2, P1)

| Step | File | Why |
|------|------|-----|
| 0.1 | `domain/enums/SnackGenerationPhase.java` (NEW) | The four real phases; zero dependencies. |
| 0.2 | `application/port/SnackPhaseListener.java` (NEW) | Callback port + `NOOP`; keeps the generator free of storage. |
| 0.3 | `infrastructure/tracking/SnackPhaseRegistry.java` (NEW) | Thread-safe in-memory `subtopicId → phase` registry. |
| 0.4 | `application/service/SnackService.java` (MOD) | Add the listener-aware overload; emit phases at the exact code points (context, model, assemble, persist); single-shot uses `NOOP`. |
| 0.5 | `application/service/BatchService.java` (MOD) | Inject the registry; clear-before and clear-in-finally around each item; pass a per-item listener; expose `snackPhase` in `BatchItemStatus` for `GENERATING` items only. |

### Phase 1 — Contract exposure (US1, P1)

| Step | File | Why |
|------|------|-----|
| 1.1 | `interfaces/rest/dto/BatchStatusApiResponse.java` (MOD) | Add nullable `String snackPhase` to `BatchItem`. |
| 1.2 | `interfaces/rest/BatchController.java` (MOD) | Map the enum to its name (or `null`) in `status`. |

### Phase 2 — Frontend phase messages (US1, P1)

| Step | File | Why |
|------|------|-----|
| 2.1 | `models/batch.model.ts` (MOD) | `SnackGenerationPhase` union, `snackPhase` field, `PHASE_MESSAGES`, `phaseMessage()`. |
| 2.2 | `pages/snacks/snacks.component.ts` (MOD) | Expose `phaseMessage` to the template. |
| 2.3 | `pages/snacks/snacks.component.html` (MOD) | Render the per-item phase message in place of the generic in-progress text. |

### Phase 3 — Tests (all stories)

| Step | File | Why |
|------|------|-----|
| 3.1 | `application/service/SnackServiceTest.java` (MOD) | A recording listener asserts the exact phase order and the re-roll sequence; `NOOP` path proves single-shot is unchanged. |
| 3.2 | `application/service/BatchServiceTest.java` (MOD) | `getStatus` exposes the phase while `GENERATING` and `null` when READY/FAILED; `runBatch` clears the registry on success and failure. |
| 3.3 | `application/integration/BatchGenerationIntegrationTest.java` (MOD) | With a slow stub `LlmProvider`, observe a phase mid-generation and assert it is cleared at terminal. |
| 3.4 | `frontend/e2e/helpers.ts` (MOD) | Add `snackPhase` to `BatchMockItem` and a `batchItemGenerating(subtopicId, phase?)` helper. |
| 3.5 | `frontend/e2e/snack-wait-messages.spec.ts` (NEW) | Drive the mock through each phase; assert each exact message; assert per-item isolation; assert no phase message for READY/FAILED. |
| 3.6 | Existing suites | `mvn test` + `npx playwright test` stay green (FR-010 / SC-005). |

### Phase 4 — Contracts, docs, verification

| Step | File | Why |
|------|------|-----|
| 4.1 | `contracts/batch-status.md` | Document the additive `snackPhase` field, its values, and its `GENERATING`-only presence. |
| 4.2 | `quickstart.md` | How to observe each phase locally and how to drive them in E2E. |
| 4.3 | FR-011 inspection | Confirm the phase field and logs carry only static enum tokens / messages — no prompts, keys, or payloads. |

## Milestones

- **M0** (P1, backend): domain enum + listener + registry + `SnackService` code-point emission (Phase 0). Delivers US2 / FR-001/002.
- **M1** (P1, backend): `BatchService` lifecycle + `getStatus` exposure + DTO/controller mapping (Phases 0.5, 1). Delivers FR-003/004.
- **M2** (P1, frontend): phase→message mapping and rendering (Phase 2). Delivers US1 / FR-005/006/007/008.
- **M3** (P1, tests): unit + integration + E2E coverage (Phase 3). Delivers SC-001..005.
- **M4** (P2, docs/verification): contract, quickstart, privacy inspection, no-regression suite (Phase 4). Delivers SC-005/006.

## Risks

- **Short phases may be skipped between polls.** `ASSEMBLING`/`PERSISTING` can complete between two 1.5 s polls. This is accepted by the spec; the phase is never shown stale because each poll overwrites with the server value. The E2E spec drives the mock per phase to prove all four messages render. **Flagged for the orchestrator** as an observability caveat, not a defect.
- **Registry lifetime vs. retry cycles.** A retry starts a new single-target batch and re-enters the phases. `BatchService.runBatch` clears the entry before each generation, so the retried item shows phases from the start again (FR-009). A stale entry cannot survive because the `finally` always clears.
- **Keying by subtopic id assumes one item per subtopic per batch.** This holds in the current MVP model (`StudySessionItem` is created once per selected subtopic). If a future feature allows the same subtopic twice in one batch, the key must become the item id; documented in `data-model.md`. **Flagged as an assumption.**
- **Concurrency visibility.** The worker thread writes the `ConcurrentHashMap` and the request thread reads it without further synchronization; `ConcurrentHashMap` gives the required happens-before for the enum reference. A poll may momentarily miss the newest phase by one step — acceptable and never stale-backwards.
- **`batch.service.ts` is intentionally unchanged.** The typed passthrough already carries the new optional field. Any reviewer expecting a service edit should treat the model change as the single source of the type.
- **Task 9 dependency.** Liveness/termination is out of scope; this feature assumes the batch already reaches a terminal status and that `snackPhase` is therefore always cleared. If Task 9 lands later, it must preserve the clear-on-settle contract.

## Mapping: Spec Requirements → Plan Items

| Spec ID | Requirement | Plan Item |
|---------|-------------|-----------|
| FR-001 | Per in-progress item, expose a real phase (≥4 steps) | Enum + listener + registry (0.1–0.4); exposure (0.5, 1.1, 1.2) |
| FR-002 | Advance at the actual code point, never on a timer | `SnackService` emission at exact lines (0.4) |
| FR-003 | Polled batch response includes the current phase | `BatchItemStatus` + DTO + controller (0.5, 1.1, 1.2) |
| FR-004 | Phase cleared/ignored for READY/FAILED | `finally` clear + `GENERATING`-only exposure (0.5) |
| FR-005 | Distinct plain-language message per phase | `PHASE_MESSAGES` + template (2.1–2.3) |
| FR-006 | Message corresponds to actual backend phase; no invention | Pure mapping of the reported token (2.1); backend sends enum only (1.2) |
| FR-007 | Message updates as the phase changes | Each poll overwrites `snackPhase`; template re-renders (2.3) |
| FR-008 | Per-item tracking; no cross-item bleed | Registry keyed by subtopic id (0.3); per-`BatchItem` rendering (2.3) |
| FR-009 | Retried item re-enters phases from the start | Clear-before each run + new single-target batch (0.5) |
| FR-010 | No regression to ready/failed/empty-state/polling | Only the in-progress branch changes; existing suites (3.6) |
| FR-011 | No prompts/keys/payloads in messages or logs | Static enum tokens + static messages; inspection (4.3) |
| FR-012 | Layered: progression app/domain, presentation interfaces | Enum (domain), listener/registry/service (app/infra), DTO/controller + Angular (interfaces) |
| SC-001 | 100% of observed messages match the real phase | Ordered unit test (3.1) + E2E per phase (3.5) |
| SC-002 | Generic message replaced; distinct messages observable | Four-message mapping (2.1); E2E drives all four (3.5) |
| SC-003 | 0 phase changes from elapsed time/poll count | Emission only at code points; no timer source (0.4, 2.1) |
| SC-004 | 0 in-progress phase messages for settled items | Clear-on-settle (0.5) + null for non-GENERATING (1.2); E2E (3.5) |
| SC-005 | No regression in batch/retry/empty-state/polling tests | Existing suites unchanged (3.6) |
| SC-006 | 0 sensitive content in messages/logs | Static tokens/messages; inspection (4.3) |

## Testing Strategy

### Backend unit tests
- `SnackServiceTest`: pass a recording `SnackPhaseListener`; assert the exact sequence `RESOLVING_CONTEXT → AWAITING_MODEL → ASSEMBLING → PERSISTING`. Add a re-roll case (invalid first response) asserting `AWAITING_MODEL → AWAITING_MODEL → ASSEMBLING`. Assert the `NOOP`/single-shot overloads still generate and persist without any listener interaction.
- `BatchServiceTest`: with a real `SnackPhaseRegistry` and a stub `SnackService` that emits a phase, assert `getStatus` returns that phase while the item is `GENERATING` and `null` once it is `READY`/`FAILED`; assert `runBatch` clears the registry entry in both the success and failure paths.

### Backend integration test
- `BatchGenerationIntegrationTest`: with a stub `LlmProvider` that blocks on a latch (or is slow), start a batch, poll `getStatus`, and assert a non-null `snackPhase` is observed mid-generation and that it becomes `null` once the item is terminal. Reuses the existing Testcontainers setup.

### Contract / serialization
- Extend the existing batch flow test (or add a focused MockMvc test) to assert the JSON includes `snackPhase` (a value while `GENERATING`, `null` otherwise) and that no other field changed.

### Frontend E2E (`frontend/e2e/snack-wait-messages.spec.ts`)
- **Per-phase messages**: with `setupConfigurableBatchMock`, return `GENERATING` items whose `snackPhase` is each of the four values across successive polls; assert the rendered text equals the mapped message each time.
- **Per-item isolation**: two `GENERATING` items with different `snackPhase` values render their own distinct messages.
- **Settled items**: a `READY` and a `FAILED` item render no phase message (ready card / error banner instead).
- **Retry re-entry**: after a failed item is retried, its `snackPhase` messages appear again from `RESOLVING_CONTEXT`.
- **Regression**: `batch-empty-state.spec.ts`, `polling-termination.spec.ts`, `retry-bound.spec.ts`, `progressive-delivery.spec.ts`, `loading-states.spec.ts`, and the rest of the suite stay green.

### Privacy check
- Assert the wire payload contains only the enum token (no prompt/response/key text) and that the frontend message table is a static, content-free map. Inspect the `BatchService`/`SnackService` log statements added by this feature (none should log generated content).

### Manual verification
See `quickstart.md`: point the backend at a real (or slow) LLM, generate snacks, and watch the message change from context → AI response → assembling → saving; confirm ready/failed render unchanged.

### What is explicitly not tested / not changed
- No DB migration tests (no schema change). No new endpoint tests beyond the added field. No changes to `RetryingLlmProviderTest`, `QuizServiceTest`, or `QuizQualityGateTest`. No frontend unit runner is introduced.
