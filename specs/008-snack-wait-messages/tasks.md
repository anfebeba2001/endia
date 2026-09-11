---
description: "Task list for accurate step-by-step snack waiting messages (observability over the 001/002/005 batch flow)"
---

# Tasks: Accurate Step-by-Step Snack Waiting Messages

**Input**: Design documents from `/specs/008-snack-wait-messages/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/batch-status.md, quickstart.md

**Branch**: `008-snack-wait-messages`

## Organization

This is an observability feature over the existing `001-batch-mvp-enhancements` / `002-snack-retry-bound` / `005-batch-snack-generation-fix` async snack batch flow (Spring Boot backend + Angular frontend). It replaces the single generic in-progress message with a distinct message per **real** generation phase, driven by the actual backend phase rather than a timer. The change set is **~4 new backend files + 3 modified backend files + backend tests + 2 modified frontend files + 1 modified E2E helper + 1 new E2E spec**. There is **no schema change**, **no new endpoint**, **no new status**, **no new dependency**, and **no change to `batch.service.ts`**.

The tasks follow the plan's milestones (M0–M4) and are grouped into six canonical phases: **Setup → Foundational → Tests (TDD) → Core → Integration → Polish**. Story labels (`[US1]`–`[US3]`) provide traceability to `spec.md`; setup/foundational/integration/polish tasks carry no label and are mapped by the coverage table at the end.

### Milestone ↔ story ↔ phase map

| Milestone | Plan phase | Story | Conceptual phase |
|-----------|-----------|-------|------------------|
| — | Setup | none | Setup |
| — | Foundational (phase vocabulary + registry) | none | Foundational |
| — | Tests (TDD) | US1–US2 | Tests |
| M0 | Phase 0 — domain + application phase plumbing | US2 | Core |
| M1 | Phase 1 — contract exposure | US2 | Core |
| M2 | Phase 2 — frontend phase messages | US1 | Core |
| M3 | Phase 3 — unit + integration + E2E tests | US1–US2 | Tests (authored) / Integration (green) |
| M4 | Phase 4 — contracts, docs, privacy, no-regression | US3 | Integration / Polish |

> **US3 (no regression / privacy)** adds no new product behavior; it is verified by keeping the existing suites green and confirming the frozen surfaces and the no-sensitive-data rule. Per house style, its verification tasks live in Integration/Polish (unlabeled) and are mapped in the Requirement Coverage table.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Which user story this task belongs to (`US1`–`US3`); absent for setup/foundational/integration/polish
- Base paths (verified against the repo):
  - Backend main: `backend/src/main/java/com/endia/`
  - Backend tests: `backend/src/test/java/com/endia/`
  - Frontend: `frontend/src/app/`
  - Frontend E2E: `frontend/e2e/`
  - Feature docs: `specs/008-snack-wait-messages/`

> **Testing constraint (from `research.md` / `plan.md`)**: there is **no frontend unit-test runner** (`*.spec.ts` under `frontend/src/app/` do not exist). Frontend verification is via **Playwright** (`frontend/e2e/`). Backend verification is via **JUnit 5 + Mockito** (unit) and **Spring Boot Test + Testcontainers MySQL** (integration, `@Tag("integration")`, excluded from the default `mvn test`). Test tasks are written first and are expected to fail before their implementation tasks land.

> **No-schema constraint**: the phase is in-memory runtime state (`ConcurrentHashMap`) and is never persisted. No entity, column, index, or migration changes; `ddl-auto=update` produces no delta (see `data-model.md` §Persistence/Schema Change Statement).

> **Out of scope**: Batch backlog Task 9 (liveness/termination) is explicitly **not** part of this feature; no task addresses it. This feature assumes the batch already reaches a terminal status and that `snackPhase` is therefore always cleared.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify the baseline and prepare the shared test seam before any behavior change. No production code is written here.

- [x] T001 Verify the baseline in `backend/` and `frontend/`: run `cd backend && mvn -q test` (green), `cd frontend && npm run build` (green), and `cd frontend && npx playwright test` (existing suite green). Record the baseline test counts. This baseline is required to prove SC-005 (no regression) later.
- [x] T002 [P] Confirm no dependency or schema change is required: verify `backend/pom.xml`, `frontend/package.json`, and `frontend/package-lock.json` need no edit; confirm there is no Flyway/Liquibase migration and `ddl-auto=update` produces no delta (consistent with `data-model.md` §Persistence/Schema Change Statement). Record the finding for the PR description.
- [x] T003 [P] Prepare the E2E test seam in `frontend/e2e/helpers.ts`: add `snackPhase?: string | null` to `BatchMockItem` and an optional `phase` parameter to `batchItemGenerating(subtopicId, phase?)` so a mock item can report each of the four phases. Confirm `setupConfigurableBatchMock` serializes `items` verbatim so the field flows through unchanged. This is a shared helper file and blocks T011.

**Checkpoint**: Baseline green, no-dependency/no-schema confirmed, and the E2E phase seam exists — test authoring and implementation can begin.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Introduce the shared phase vocabulary and the in-memory tracking component that both the tests and the user-story implementations depend on. These are pure scaffolding with no behavior change to the existing flow.

**⚠️ CRITICAL**: No user-story implementation may begin until this phase is complete.

- [x] T004 Create `backend/src/main/java/com/endia/domain/enums/SnackGenerationPhase.java` (NEW): a pure domain enum with exactly `RESOLVING_CONTEXT`, `AWAITING_MODEL`, `ASSEMBLING`, `PERSISTING`. Zero external dependencies (FR-001/FR-012).
- [x] T005 [P] Create `backend/src/main/java/com/endia/application/port/SnackPhaseListener.java` (NEW): a `@FunctionalInterface` `void onPhase(SnackGenerationPhase phase)` with a `NOOP = phase -> {}` constant. No registry/Spring dependency (FR-002/FR-012).
- [x] T006 [P] Create `backend/src/main/java/com/endia/infrastructure/tracking/SnackPhaseRegistry.java` (NEW): a `@Component` wrapping `ConcurrentHashMap<UUID, SnackGenerationPhase>` with `update(UUID, SnackGenerationPhase)`, `SnackGenerationPhase current(UUID)` (nullable), and `clear(UUID)`. Keyed by **subtopic id** for per-item isolation (FR-008, `data-model.md` §2).

**Checkpoint**: The phase enum, listener port, and registry exist and compile; the modified services, DTO, and frontend can now be wired.

---

## Phase 3: Tests (TDD — write first, ensure they FAIL)

**Purpose**: Encode the required behavior as failing tests before implementing it. Backend tests are executable TDD; the frontend spec is Playwright (no unit runner exists) and will fail against current behavior.

> **NOTE**: Write these tests FIRST and confirm they fail for the intended reason before starting Phase 4. Tests that reference the new listener-aware `SnackService` overload, the `snackPhase` DTO field, or the `snackPhase` mock field may not compile until their Core task lands; that is the expected pre-implementation failure.

- [x] T007 [P] [US2] Extend `backend/src/test/java/com/endia/application/service/SnackServiceTest.java` with a recording `SnackPhaseListener`: assert the exact sequence `RESOLVING_CONTEXT → AWAITING_MODEL → ASSEMBLING → PERSISTING` for a valid generation; add a re-roll case (invalid first response) asserting `AWAITING_MODEL → AWAITING_MODEL → ASSEMBLING`; assert the existing `NOOP`/single-shot overloads still generate and persist without any listener interaction (FR-001/FR-002, SC-001/SC-003).
- [x] T008 [P] [US2] Extend `backend/src/test/java/com/endia/application/service/BatchServiceTest.java`: construct `BatchService` with a real `SnackPhaseRegistry` and a stub `SnackService` that emits a phase; assert `getStatus` returns that phase while the item is `GENERATING` and `null` once it is `READY`/`FAILED`; assert `runBatch` clears the registry entry on both the success and failure paths; assert a retried item re-enters from `RESOLVING_CONTEXT` (FR-003/FR-004/FR-009, SC-004).
- [x] T009 [P] [US2] Extend `backend/src/test/java/com/endia/application/integration/BatchGenerationIntegrationTest.java` (`@Tag("integration")`, Testcontainers MySQL): with a stub `LlmProvider` that blocks on a latch (or is slow), start a batch, poll `getStatus`, and assert a non-null `snackPhase` is observed mid-generation and that it becomes `null` once the item is terminal (FR-003/FR-004, SC-001/SC-004).
- [x] T010 [P] [US2] Create `backend/src/test/java/com/endia/interfaces/rest/BatchStatusContractTest.java` (NEW, `@Tag("integration")` or focused MockMvc): assert the `GET /api/batches/{batchId}` JSON includes `snackPhase` (a value while `GENERATING`, `null` otherwise) and that no other field, type, or value changed (FR-003/FR-004, `contracts/batch-status.md`).
- [x] T011 [P] [US1] Create `frontend/e2e/snack-wait-messages.spec.ts` (NEW) using `setupConfigurableBatchMock` + the T003 seam: (a) per-phase messages — return `GENERATING` items whose `snackPhase` is each of the four values across successive polls and assert the rendered text equals the mapped message each time; (b) per-item isolation — two `GENERATING` items with different `snackPhase` values render their own distinct messages; (c) settled items — a `READY` and a `FAILED` item render no phase message (ready card / error banner instead); (d) retry re-entry — after a failed item is retried, its phase messages appear again from `RESOLVING_CONTEXT` (FR-005..FR-009, SC-001/SC-002/SC-004).

**Checkpoint**: Failing tests define the target behavior. Phase 4 may begin.

---

## Phase 4: Core Implementation

**Purpose**: Implement M0 (backend phase plumbing), M1 (contract exposure), and M2 (frontend phase messages). Tasks touching the same file are sequential; different files may run in parallel.

### M0 — Backend phase plumbing (US2, P1)

- [x] T012 [US2] In `backend/src/main/java/com/endia/application/service/SnackService.java`, add the listener-aware overloads (`generateSingleSnack(UUID, SnackPhaseListener)` and a private `generateAndPersist(Subtopic, SnackPhaseListener)`); emit `RESOLVING_CONTEXT` immediately before `subtopic.getTheme().getPrompt()`, `AWAITING_MODEL` immediately before **each** `callGenerateSnack(...)` (initial and the one re-roll), `ASSEMBLING` after `isValid` passes and before `new Snack(...)`, and `PERSISTING` immediately before `snackRepository.save(snack)`. Keep the existing overloads delegating with `SnackPhaseListener.NOOP` so single-shot is unchanged (FR-001/FR-002/FR-012). Depends on T005.
- [x] T013 [US2] In `backend/src/main/java/com/endia/application/service/BatchService.java`, inject `SnackPhaseRegistry`; in `runBatch`, clear the item's registry entry **before** generation and in a `finally` after it, and pass a per-item listener (`phase -> phaseRegistry.update(subtopicId, phase)`) to `snackService.generateSingleSnack(subtopicId, listener)`; add a `SnackGenerationPhase snackPhase` component to `BatchItemStatus`, set from `phaseRegistry.current(subtopicId)` **only when `snackStatus == GENERATING`**, else `null` (FR-003/FR-004/FR-008/FR-009/FR-012). Depends on T006 and T012.

### M1 — Contract exposure (US2, P1)

- [x] T014 [P] [US2] In `backend/src/main/java/com/endia/interfaces/rest/dto/BatchStatusApiResponse.java`, add a nullable `String snackPhase` to `BatchItem` (placed after `snackStatus`); no other field changes (FR-003, `contracts/batch-status.md`).
- [x] T015 [US2] In `backend/src/main/java/com/endia/interfaces/rest/BatchController.java`, map `i.snackPhase() == null ? null : i.snackPhase().name()` into the new DTO field in `status`, passing the extra constructor argument (FR-003/FR-004). Depends on T013 and T014.

### M2 — Frontend phase messages (US1, P1)

- [x] T016 [P] [US1] In `frontend/src/app/models/batch.model.ts`, add the `SnackGenerationPhase` union (`'RESOLVING_CONTEXT' | 'AWAITING_MODEL' | 'ASSEMBLING' | 'PERSISTING'`), an optional `snackPhase?: SnackGenerationPhase | null` on `BatchItem`, and the `PHASE_MESSAGES: Record<SnackGenerationPhase, string>` map plus a `phaseMessage(phase)` helper using the plan's static copy (`Finding the topic context…`, `Waiting for the AI response…`, `Assembling your snack…`, `Saving your snack…`) (FR-005/FR-006).
- [x] T017 [US1] In `frontend/src/app/pages/snacks/snacks.component.ts`, expose the `phaseMessage` helper to the template (public method) and import it from the model. Do not alter polling, retry, empty-state, or ownership logic (FR-005/FR-006/FR-007). Depends on T016.
- [x] T018 [US1] In `frontend/src/app/pages/snacks/snacks.component.html`, replace the per-item in-progress generic text (`<p>Preparing your snack…</p>` at the `@else` branch, currently line 116) with the phase message for the current `BatchItem`, falling back to the existing generic text only when `item.snackPhase` is absent. Leave the pre-first-poll empty-list block (currently line 25) generic, because no item/phase exists yet. Preserve the existing `aria-live="polite"` wrapper and spinner in the in-progress block (only the text node changes), and leave the failed-item Retry `<button>` untouched so it stays keyboard-operable. Ready/failed branches are otherwise untouched (FR-005/FR-007/FR-008/FR-010). Depends on T017.

**Checkpoint**: M0–M2 implemented; the Phase 3 tests for the corresponding behavior should now pass.

---

## Phase 5: Integration

**Purpose**: Reconcile contracts/docs, confirm existing E2E expectations still hold, verify the frozen surfaces, and run the privacy inspection.

- [x] T019 [P] Verify/update `specs/008-snack-wait-messages/contracts/batch-status.md`: confirm the documented additive nullable `snackPhase`, its four values, its `GENERATING`-only presence, and the unchanged terminality/`retrying`/polling-window semantics match the implementation (FR-003/FR-004/FR-011).
- [x] T020 [P] Verify/update `specs/008-snack-wait-messages/quickstart.md`: confirm the local phase-observation recipe (watch context → AI → assembling → saving), the on-the-wire `curl | jq` check, the short-phase-skipping note, and the no-schema statement match the shipped behavior (SC-002/SC-006).
- [x] T021 [P] Reconcile existing E2E specs whose expectations touch the in-progress branch: inspect `frontend/e2e/loading-states.spec.ts`, `frontend/e2e/progressive-delivery.spec.ts`, `frontend/e2e/batch-empty-state.spec.ts`, `frontend/e2e/polling-termination.spec.ts`, and `frontend/e2e/retry-bound.spec.ts`; confirm the null-`snackPhase` fallback keeps the generic text and that no assertion breaks, updating only what is required (FR-010, SC-005). Depends on T003 and T018.
- [x] T022 Confirm the frozen surfaces are unchanged: `frontend/src/app/services/batch.service.ts` (typed passthrough; no edit), `POST /api/batches`, `POST /api/snacks/generate`, the single-shot snack contract, `RetryingLlmProvider`, `QuizService`, prompts/validation, the ~1.5 s polling interval and bounded window, and the DB schema/migrations (FR-010/FR-012, plan.md §Constraints). Depends on T013 and T015.
- [x] T023 Privacy check (FR-011/SC-006): inspect the `snackPhase` wire payload and the `PHASE_MESSAGES` map and confirm they contain only static enum tokens / static copy — no prompts, API keys, provider request/response payloads, or personal data; inspect any log statements added by this feature for the same constraint.

**Checkpoint**: Contracts and docs reflect the shipped behavior; existing specs hold; frozen surfaces confirmed; privacy verified.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final verification, no-regression gates, and checklist sign-off.

- [x] T024 [P] Run `cd backend && mvn test` and confirm `SnackServiceTest`, `BatchServiceTest`, and the rest of the unit suite are green; if Docker is available, run the integration profile (`mvn test -Pintegration`) and confirm `BatchGenerationIntegrationTest` and `BatchStatusContractTest` pass (SC-001/SC-002/SC-003/SC-004). Depends on T007–T010 and T012–T015.
- [x] T025 Run `cd frontend && npx playwright test` and confirm the new `snack-wait-messages.spec.ts` plus the existing suite (including `batch-empty-state.spec.ts`, `polling-termination.spec.ts`, `retry-bound.spec.ts`, `progressive-delivery.spec.ts`, `loading-states.spec.ts`) are green (SC-005). Depends on T011, T018, and T021.
- [x] T026 Execute the `quickstart.md` manual validation: observe the message change across a real (or slow) run, verify the on-the-wire `snackPhase`, confirm ready/failed render unchanged with no phase message, and confirm a retry re-enters from the context phase. Record results. Depends on T020.
- [x] T027 Final completion validation: mark every completed task `[x]` in this file, confirm **all** checklists under `specs/008-snack-wait-messages/checklists/` have **0 unchecked items**, and record the final task/phase counts in the Summary section. Depends on all preceding tasks.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup; **blocks** all tests and user stories.
- **Tests (Phase 3)**: Depends on Foundational; written first and expected to fail.
- **Core (Phase 4)**: Depends on Tests being authored. M0, M1, and M2 touch different files/stacks and can proceed in parallel; within M0 the `BatchService.java` task depends on the `SnackService.java` task, and the controller depends on the DTO.
- **Integration (Phase 5)**: Depends on the relevant Core tasks landing.
- **Polish (Phase 6)**: Depends on all Core + Integration tasks.

### Within each milestone

- **Foundational**: T004 → T005/T006; T005 and T006 are independent of each other.
- **M0 (US2)**: T012 → T013 (T013 reads the registry from T006).
- **M1 (US2)**: T014 → T015; T015 also depends on T013 (same result type).
- **M2 (US1)**: T016 → T017 → T018.
- Tests (Phase 3) MUST be authored and failing before their implementation target.

### Sequential vs parallel rules

- A task may be marked `[P]` only when it edits a **different file** than other in-flight tasks and has no dependency on an incomplete task.
- Tasks editing `BatchService.java` (T013) and `SnackService.java` (T012) are different files, but T013 depends on T012 — T013 is **not** `[P]`.
- Tasks editing `snacks.component.ts` (T017) and `snacks.component.html` (T018) are **not** `[P]` relative to each other (T018 depends on T017).
- `BatchStatusApiResponse.java` (T014) and `models/batch.model.ts` (T016) are independent files and may be parallel with each other and with T012.

### Parallel opportunities

- **Setup**: T002 and T003 are different files — parallel.
- **Foundational**: T005 and T006 are different files — parallel (T004 first).
- **Tests**: T007, T008, T009, T010, T011 are distinct files and can be authored in parallel once T003 lands.
- **Core**: T012, T014, T016 are different files — parallel; T013 follows T012, T015 follows T013+T014, T017 follows T016, T018 follows T017.
- **Integration**: T019, T020, T021, T023 are separate files — parallel; T022 is a read-only audit.
- **Polish**: T024 (backend suite) is parallel with T025 (frontend suite) once their dependencies land.

---

## Parallel Example: Tests phase

```bash
# After T003 (shared E2E helper) and Phase 2, author the tests in parallel:
Task: "T007 SnackServiceTest ordered-phase + re-roll + NOOP"
Task: "T008 BatchServiceTest phase exposure + clear + retry re-entry"
Task: "T009 BatchGenerationIntegrationTest phase visible then cleared"
Task: "T010 BatchStatusContractTest snackPhase JSON"
Task: "T011 snack-wait-messages.spec.ts (per-phase / isolation / settled / retry)"
```

---

## Implementation Strategy

### MVP First

1. Complete Setup (T001–T003).
2. Complete Foundational (T004–T006).
3. Author failing tests (T007–T011).
4. **M0** (T012–T013): real code-point phase emission + registry lifecycle — independently delivers US2 (the message is never a lie).
5. **M1** (T014–T015): contract exposure — independently delivers the polled `snackPhase`.
6. **M2** (T016–T018): phase→message mapping and rendering — independently delivers US1 (the user sees what is happening).
7. **STOP and VALIDATE**: run the backend + Playwright suites and the quickstart recipes.

### Incremental Delivery

Each of M0, M1, and M2 is independently valuable and testable: M0 makes the backend phase truthful, M1 exposes it on the existing poll, and M2 renders it. M4 (contracts/docs/privacy/no-regression) closes out US3.

---

## Requirement Coverage

| Spec ID | Covered by |
|---------|-----------|
| FR-001 | T004, T012, T013, T014, T015 |
| FR-002 | T007, T012 |
| FR-003 | T008, T009, T010, T014, T015 |
| FR-004 | T008, T013, T015 |
| FR-005 | T011, T016, T018 |
| FR-006 | T011, T016 |
| FR-007 | T011, T016, T018 |
| FR-008 | T006, T011, T013, T016, T018 |
| FR-009 | T008, T011, T013 |
| FR-010 | T001, T021, T022, T025 |
| FR-011 | T023 |
| FR-012 | T004, T005, T006, T012, T013, T015, T016 |
| SC-001 | T007, T009, T011 |
| SC-002 | T007, T011, T016 |
| SC-003 | T007, T012 |
| SC-004 | T008, T011, T013, T015 |
| SC-005 | T001, T021, T024, T025 |
| SC-006 | T023 |

**No FR or SC is left uncovered.** All 12 FRs and all 6 SCs map to at least one task.

---

## Summary

- **Total tasks: 27**
- **Phase breakdown**:
  - Setup: **3** (T001–T003)
  - Foundational: **3** (T004–T006)
  - Tests (TDD): **5** (T007–T011)
  - Core: **7** (T012–T018; M0=2, M1=2, M2=3)
  - Integration: **5** (T019–T023)
  - Polish: **4** (T024–T027)
- **Story task counts** (labeled tasks):
  - US1 (user sees the real phase): **4** (T011, T016, T017, T018)
  - US2 (phase reflects the real code point): **6** (T007, T008, T009, T010, T012, T013) + exposure T014/T015
  - US3 (no regression / privacy): **0** labeled — covered by T001, T021, T022, T023, T024, T025 (per house style, integration/polish tasks are unlabeled)
  - Unlabeled (setup/foundational/integration/polish): **15**

## Notes

- `[P]` tasks touch different files and have no dependency on incomplete tasks; T013 depends on T012 (both services), T015 depends on T013+T014, and T018 depends on T017.
- Every task carries exact file paths (create or modify) and a clear done-definition.
- The frontend has no unit-test runner; frontend verification is Playwright.
- **Task 9 (liveness/termination) is out of scope** — no task addresses it. This feature assumes a terminal batch status and a cleared `snackPhase` on settle. If Task 9 lands later, it must preserve the clear-on-settle contract (plan §Risks).
- `batch.service.ts`, `POST /api/batches`, `POST /api/snacks/generate`, `RetryingLlmProvider`, `QuizService`, prompts/validation, the polling interval/window, the `BatchStatus`/`GenerationStatus` vocabularies, and the DB schema are intentionally unchanged (plan §Constraints, data-model.md).
- **Assumption flagged**: the registry is keyed by subtopic id, which assumes one `StudySessionItem` per subtopic per batch. If a future feature allows duplicates, the key must become the item id (data-model.md §2, plan §Risks).
- Checklists live in `specs/008-snack-wait-messages/checklists/` and must end with 0 unchecked items at implementation completion (T027).
