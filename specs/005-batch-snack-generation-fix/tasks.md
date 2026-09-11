---
description: "Task list for the Endia batch snack generation fix (bug-fix over the 001/002 async batch flow)"
---

# Tasks: Batch Snack Generation Fix

**Input**: Design documents from `/specs/005-batch-snack-generation-fix/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Branch**: `005-batch-snack-generation-fix`

## Organization

This is a small defect fix over the existing `001-batch-mvp-enhancements` / `002-snack-retry-bound` async snack batch flow (Spring Boot backend + Angular frontend). Three confirmed, independent root causes are fixed: (1) detached related-data access, (2) worker self-deadlock on a shared bounded executor, (3) premature empty-state flash / non-terminating polling. The change set is **3 modified backend main files (+1 optional repository hardening) + 3 backend test files + 2 frontend files + 1 new E2E spec**. There is **no schema change**, **no endpoint/DTO change**, **no new status**, and **no new dependency**.

The tasks follow the plan's milestones (M0–M4) and are grouped into the five canonical phases: **Setup → Tests → Core → Integration → Polish**. Story labels (`[US1]`..`[US4]`) provide traceability to `spec.md`; setup/integration/polish tasks carry no label and are mapped by the coverage table at the end.

### Milestone ↔ story ↔ phase map

| Milestone | Plan phase | Story | Conceptual phase |
|-----------|-----------|-------|------------------|
| — | Setup | none | Setup |
| — | Tests (TDD) | US1–US3 | Tests |
| M0 | Phase 0 — persistence scope: generate by id inside the transaction | US1 | Core |
| M1 | Phase 1 — executor separation and liveness | US2 | Core |
| M2 | Phase 2 — frontend progress state and empty-state gating | US3 | Core |
| M3 | Phase 3 — unit + integration + E2E tests | US1–US3 | Tests (authored) / Integration (green) |
| M4 | Phase 4 — contracts, docs, verification | US4 (preservation) | Integration / Polish |

> **US4 (no regression)** adds no new product behavior; it is verified by keeping the existing suites green and confirming the frozen surfaces unchanged. Per house style, its verification tasks live in Integration/Polish (unlabeled) and are mapped in the Requirement Coverage table.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Which user story this task belongs to (`US1`–`US4`); absent for setup/integration/polish
- Base paths (verified against the repo):
  - Backend main: `backend/src/main/java/com/endia/`
  - Backend tests: `backend/src/test/java/com/endia/`
  - Frontend: `frontend/src/app/`
  - Frontend E2E: `frontend/e2e/`
  - Feature docs: `specs/005-batch-snack-generation-fix/`

> **Testing constraint (from `research.md` / `plan.md`)**: there is **no frontend unit-test runner** (`*.spec.ts` under `frontend/src/app/` do not exist). Frontend verification is via **Playwright** (`frontend/e2e/`). Backend verification is via **JUnit 5 + Mockito** (unit) and **Spring Boot Test + Testcontainers MySQL** (integration, `@Tag("integration")`, excluded from the default `mvn test`). Test tasks are written first and are expected to fail before their implementation tasks land.

> **Testing constraint (concurrency)**: the self-deadlock only manifests when all `batchExecutor` core threads are held by batch workers (i.e. **≥2 concurrent batches**). The new integration test MUST start concurrent batches (or otherwise saturate the core pool) to be a meaningful pre-fix/fail guard.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify the baseline and repository hygiene before any behavior change. No production code is written here.

- [X] T001 Verify the baseline in `backend/` and `frontend/`: run `cd backend && mvn -q test` (green), `cd frontend && npm run build` (green), and `cd frontend && npx playwright test` (existing suite green). Record the baseline test counts. This baseline is required to prove SC-007 (no regression) later.
- [X] T002 [P] Confirm no dependency or schema change is required: verify `backend/pom.xml`, `frontend/package.json`, and `frontend/package-lock.json` need no edit; confirm there is no Flyway/Liquibase migration and `ddl-auto=update` produces no delta (consistent with `data-model.md` §Persistence/Schema Change Statement). Record the finding for the PR description.
- [X] T003 [P] Prepare the E2E test seam in `frontend/e2e/helpers.ts`: confirm `setupConfigurableBatchMock` and `setPollingWindow` exist and add a **delayed-first-poll** capability (or a per-call response delay) if missing, so the new spec can reliably observe the pre-first-poll window. This is a shared helper file and blocks T007.

**Checkpoint**: Baseline green, no-dependency/no-schema confirmed, and the E2E delayed-poll seam exists — implementation and test authoring can begin.

---

## Phase 2: Tests (TDD — write first, ensure they FAIL)

**Purpose**: Encode the required behavior as failing tests before implementing it. Backend tests are executable TDD; the frontend spec is Playwright (no unit runner exists) and will fail against current behavior.

> **NOTE**: Write these tests FIRST and confirm they fail for the intended reason before starting Phase 3. Tests touching `SnackServiceTest.java` / `BatchServiceTest.java` may not compile until their Core task lands; that is the expected pre-implementation failure.

- [X] T004 [P] [US1] Extend `backend/src/test/java/com/endia/application/service/SnackServiceTest.java` with failing tests for the by-id overload: `generateSingleSnack(UUID)` resolves the subtopic (and lazily its theme) via the repository, calls the LLM with the theme prompt, and persists the snack; an unknown id throws `EntityNotFoundException`. This fails if the by-id path does not load the related data (FR-001/FR-002/FR-007).
- [X] T005 [P] [US1] Extend `backend/src/test/java/com/endia/application/service/BatchServiceTest.java`: add the `quizExecutor` constructor argument; stub the by-id overload; assert `runBatch` calls `generateSingleSnack(UUID)` and **never** `generateSingleSnack(Subtopic)` (regression guard for FR-002); keep the existing partial-failure/aggregation and `retrying` assertions.
- [X] T006 [P] [US2] Create `backend/src/test/java/com/endia/application/integration/BatchGenerationIntegrationTest.java` (NEW, `@Tag("integration")`): real Spring context + Testcontainers MySQL + a stub `LlmProvider` (valid snack/quiz), using the **real** `batchExecutor` and `quizExecutor`. Start **multiple concurrent** batches via the proxied `BatchService.createBatch`, poll `getStatus`, and assert every item reaches `READY`, the aggregate reaches a terminal status within a bounded timeout, and no `LazyInitializationException` is recorded (`snackCause == null`). Hangs/fails pre-fix (FR-001/FR-005/FR-007).
- [X] T007 [P] [US3] Create `frontend/e2e/batch-empty-state.spec.ts` (NEW) using `setupConfigurableBatchMock` + the T003 delayed-poll seam: (a) delayed first poll → `.empty-state` is hidden and a progress indicator is visible; (b) terminal `FAILED`/zero-ready → `.empty-state` appears only after the terminal status and polling has stopped; (c) `IN_PROGRESS` with an empty `items` array → `.empty-state` is suppressed. Covers FR-009/FR-010/FR-011.

**Checkpoint**: Failing tests define the target behavior. Phase 3 may begin.

---

## Phase 3: Core Implementation

**Purpose**: Implement M0 (persistence scope), M1 (executor separation + liveness), and M2 (frontend progress state). Tasks touching the same file are sequential; different files may run in parallel.

### M0 — Persistence scope: generate by id inside the transaction (US1, P1)

- [X] T008 [P] [US1] In `backend/src/main/java/com/endia/application/service/SnackService.java`, extract the existing generation logic into a private `generateAndPersist(Subtopic)`; add `@Transactional public Snack generateSingleSnack(UUID subtopicId)` that loads the subtopic via `subtopicRepository.findById(...)` (throwing `EntityNotFoundException` for an unknown id) and delegates; keep `generateSingleSnack(Subtopic)` and `generateSingleSnack(Subtopic, Consumer)` and route all three through `generateAndPersist` (FR-001/FR-002/FR-017).
- [X] T009 [US1] In `backend/src/main/java/com/endia/application/service/BatchService.java`, change `runBatch` to read only `item.getSubtopic().getId()` and call `snackService.generateSingleSnack(subtopicId)` instead of passing the detached `Subtopic`; preserve the existing per-item `try`/`catch` (including the `LlmGenerationException` typed-cause branch and the generic `UNAVAILABLE` branch) so one item failure does not block the others (FR-002/FR-003/FR-008). Depends on T008.
- [X] T010 [P] [US1] In `backend/src/main/java/com/endia/infrastructure/persistence/JpaStudySessionItemRepository.java`, add `@EntityGraph(attributePaths = {"subtopic"})` to `findBySessionId` so the subtopic is initialized before detach (recommended hardening; removes reliance on Hibernate proxy-id behavior) (FR-001/FR-002).

### M1 — Executor separation and liveness (US2, P1)

- [X] T011 [P] [US2] In `backend/src/main/java/com/endia/infrastructure/llm/LlmConfig.java`, add a new `@Bean(name = "quizExecutor")` `ThreadPoolTaskExecutor` (`quiz-worker-`, core=4, max=16, queue=100, `ThreadPoolExecutor.CallerRunsPolicy`); leave `batchExecutor` unchanged (`batch-worker-`, core=2/max=8/queue=50, default rejection) (FR-004).
- [X] T012 [US2] In `backend/src/main/java/com/endia/application/service/BatchService.java`, inject `@Qualifier("quizExecutor") TaskExecutor quizExecutor` into the constructor; submit the nested quiz `CompletableFuture.runAsync(...)` to `quizExecutor`; replace the unbounded `allOf(...).join()` with `allOf(...).orTimeout(quizJoinTimeoutSeconds, TimeUnit.SECONDS).exceptionally(ex -> { log.warn(...); return null; })`, with `@Value("${batch.quiz-join-timeout-seconds:240}") int quizJoinTimeoutSeconds`, so the worker always proceeds to `aggregateStatus` (FR-004/FR-005/FR-006). Depends on T009 (same file) and T011.

### M2 — Frontend progress state and empty-state gating (US3, P1)

- [X] T013 [P] [US3] In `frontend/src/app/pages/snacks/snacks.component.ts`, add `readonly polling = signal(false);`; set `polling.set(true)` on `createBatch` success (before starting the poll) and on each `retryTarget` cycle start; clear it on a terminal poll status (`status.status !== 'IN_PROGRESS'`) and on every poll error path including `BatchPollingTimeoutError`; add `showEmptyState = computed(() => !creating() && !polling() && items().length === 0 && batchStatus() !== 'IN_PROGRESS' && !errorMessage())` (FR-009/FR-010/FR-011/FR-014).
- [X] T014 [US3] In `frontend/src/app/pages/snacks/snacks.component.html`, add an in-flight progress block for `!creating() && polling() && items().length === 0` (spinner + "Preparing your snack…", `aria-live="polite"`); change the empty-state condition from `!creating() && items().length === 0 && !errorMessage()` to `showEmptyState()` (FR-009/FR-011). Depends on T013.

**Checkpoint**: M0–M2 implemented; the Phase 2 tests for the corresponding behavior should now pass.

---

## Phase 4: Integration

**Purpose**: Reconcile contracts/docs, adapt any existing E2E expectations affected by the new empty-state gating, and confirm the frozen surfaces.

- [X] T015 [P] Verify/update `specs/005-batch-snack-generation-fix/contracts/batch-status.md`: restate the unchanged `GET /api/batches/{batchId}` shape and the `002` terminality (only `IN_PROGRESS` non-terminal), `retrying` semantics, and 120 s polling window; note that the server-side generation fix now makes terminal statuses reachable (FR-012/FR-013/FR-015).
- [X] T016 [P] Verify/update `specs/005-batch-snack-generation-fix/contracts/batch-generation-behavior.md`: confirm C1–C6 match the implemented persistence scope, executor isolation, `CallerRunsPolicy` liveness valve, bounded join, guaranteed terminal status, and no-sensitive-logging rules (FR-001..FR-008, FR-019/FR-020).
- [X] T017 [P] Verify/update `specs/005-batch-snack-generation-fix/quickstart.md`: confirm the manual recipes (create theme → select subtopics → generate → expect `READY`, no hang; concurrent-batch deadlock check; delayed-first-poll empty-state check; `BATCH_QUIZ_JOIN_TIMEOUT_SECONDS`) match the shipped behavior and the no-schema statement (SC-001..SC-008).
- [X] T018 [P] Reconcile existing E2E specs whose expectations may shift with the new empty-state gating: inspect `frontend/e2e/loading-states.spec.ts` and `frontend/e2e/progressive-delivery.spec.ts` (and any spec asserting `.empty-state`); update only what is required so the existing suite stays green (FR-009/FR-011, SC-007). Depends on T003 and T014.
- [X] T019 Confirm the frozen surfaces are unchanged: no edits to `backend/.../interfaces/rest/` controllers/DTOs, `frontend/src/app/services/batch.service.ts`, `frontend/src/app/services/snack-retry.service.ts`, `RetryingLlmProvider`, `QuizService`, or the schema/migrations; confirm `Task 7` max-3 bounded retry and the 120 s polling window are preserved (FR-012/FR-015/FR-016/FR-017/FR-018).

**Checkpoint**: Contracts and docs reflect the shipped behavior; existing specs adapted; frozen surfaces confirmed.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final verification, security/no-regression gates, and checklist sign-off.

- [X] T020 [P] Sensitive-data audit (FR-019/SC-008): inspect the batch failure messages and all new/changed log statements (including the bounded-join `exceptionally` warning) and confirm they contain only typed `LlmErrorCause` strings and ids — no credentials, API keys, raw prompts, provider request/response payloads, or personal data.
- [X] T021 Run `cd backend && mvn test` and confirm `SnackServiceTest`, `BatchServiceTest`, `BatchFlowIntegrationTest`, and `RetryingLlmProviderTest` are green; if Docker is available, run the integration profile (`mvn test -Pintegration`) and confirm `BatchGenerationIntegrationTest` passes (SC-001/SC-002/SC-003/SC-007). Depends on T004, T005, T006, T008–T012.
- [X] T022 Run `cd frontend && npx playwright test` and confirm the existing suite plus the new `batch-empty-state.spec.ts` are green, including `polling-termination.spec.ts`, `retry-bound.spec.ts`, `progressive-delivery.spec.ts`, and `loading-states.spec.ts` (SC-004/SC-005/SC-006/SC-007). Depends on T007, T014, T018. **Reviewer-verified: 41/41 passed (25 files), including the 3 new empty-state tests.**
- [X] T023 Execute the `quickstart.md` manual validation: US1 (no detached-data failure, `READY` snack returned), US2 (two concurrent batches both reach a terminal status; distinct `batch-worker-`/`quiz-worker-` pools), US3 (no empty-state flash before the first poll; polling stops on terminal), and FR-019 (typed causes only); record results. Depends on T017. **Results recorded in `e2e-test-plan.md` §2.1 (real-backend smoke) and T025 ("two consecutive batches both COMPLETE with all READY snacks").**
- [X] T024 Final validation: mark every completed task `[x]` in this file, confirm **all** checklists under `specs/005-batch-snack-generation-fix/checklists/` have **0 unchecked items**, and record the final task/phase counts in the Summary section.

### Remediation round (post-verification defect)

- [X] T025 [US2] Fix the commit-ordering defect (FR-005/FR-006): `BatchService.createBatch` MUST trigger `self.runBatch(sessionId)` only after its transaction commits, via `TransactionSynchronizationManager.registerSynchronization(...).afterCommit()`, falling back to a direct call when no synchronization is active. On a warm `batchExecutor` the previous direct call let the worker read zero items before commit and leave the batch `IN_PROGRESS` with `PENDING` items forever. Regression coverage: `BatchServiceTest.createBatchDefersWorkerUntilAfterCommit` (synchronization active → not invoked before `afterCommit`, invoked after) and `BatchGenerationIntegrationTest.twoConsecutiveBatchesBothReachTerminalStatus` (two sequential batches on real executors both reach terminal status). Verified live: two consecutive batches both `COMPLETE` with all `READY` snacks.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Tests (Phase 2)**: Depends on Setup; written first and expected to fail.
- **Core (Phase 3)**: Depends on Tests being authored. M0, M1, and M2 touch different files/stacks and can proceed in parallel; within M0/M1 the `BatchService.java` tasks are sequential.
- **Integration (Phase 4)**: Depends on the relevant Core tasks landing.
- **Polish (Phase 5)**: Depends on all Core + Integration tasks.

### Within each milestone

- **M0 (US1)**: T008 → T009; T010 is independent of T008/T009.
- **M1 (US2)**: T011 → T012; T012 also depends on T009 (same file as `BatchService.java`).
- **M2 (US3)**: T013 → T014.
- Tests (Phase 2) MUST be authored and failing before their implementation target.

### Sequential vs parallel rules

- A task may be marked `[P]` only when it edits a **different file** than other in-flight tasks and has no dependency on an incomplete task.
- Tasks editing `BatchService.java` (T009, T012) are **never** `[P]` relative to each other.
- Tasks editing `snacks.component.ts` (T013) and `snacks.component.html` (T014) are **not** `[P]` relative to each other (T014 depends on T013).

### Parallel opportunities

- **Setup**: T002 and T003 are different files — parallel.
- **Tests**: T004, T005, T006, T007 are distinct files and can be authored in parallel once T003 lands.
- **Core**: T008 (SnackService), T010 (repository), T011 (LlmConfig), and T013 (component) are different files — parallel; T009 and T012 are sequential on `BatchService.java`.
- **Integration**: T015–T018 are separate files — parallel.
- **Polish**: T020 (audit) is parallel with the T021/T022 suite runs.

---

## Parallel Example: Tests phase

```bash
# After T003 (shared E2E helper), author the tests in parallel:
Task: "T004 SnackServiceTest by-id overload"
Task: "T005 BatchServiceTest quizExecutor + by-id regression guard"
Task: "T006 BatchGenerationIntegrationTest (concurrent batches)"
Task: "T007 batch-empty-state.spec.ts (delayed first poll)"
```

---

## Implementation Strategy

### MVP First

1. Complete Setup (T001–T003).
2. Author failing tests (T004–T007).
3. **M0** (T008–T010): by-id transactional generation — independently fixes US1 (every snack failing with a detached-data error).
4. **M1** (T011–T012): separate `quizExecutor` + bounded join — independently fixes US2 (the batch never terminates).
5. **M2** (T013–T014): `polling` state + empty-state gating — independently fixes US3 (the visible flash and endless retry).
6. **STOP and VALIDATE**: run the backend + Playwright suites and the quickstart recipes.

### Incremental Delivery

Each of M0, M1, and M2 is independently valuable and testable via its Phase 2 test: M0 restores persisted `READY` snacks, M1 guarantees termination, and M2 makes the screen look correct. M4 (contracts/docs/verification) closes out the no-regression story.

---

## Requirement Coverage

| Spec ID | Covered by |
|---------|-----------|
| FR-001 | T008 |
| FR-002 | T008, T009, T010 |
| FR-003 | T008, T009 |
| FR-004 | T011, T012 |
| FR-005 | T011, T012 |
| FR-006 | T012 |
| FR-007 | T008, T009, T021 |
| FR-008 | T009 |
| FR-009 | T013, T014 |
| FR-010 | T013, T014 |
| FR-011 | T013, T014 |
| FR-012 | T013, T019 |
| FR-013 | T013, T019, T022 |
| FR-014 | T013 |
| FR-015 | T019, T022 |
| FR-016 | T019, T021, T022 |
| FR-017 | T008, T021 |
| FR-018 | T011, T019, T021 |
| FR-019 | T020 |
| FR-020 | T008, T011, T013 |
| SC-001 | T006, T021 |
| SC-002 | T006, T012, T021 |
| SC-003 | T006, T008, T021 |
| SC-004 | T007, T013, T014, T022 |
| SC-005 | T007, T013, T022 |
| SC-006 | T022 |
| SC-007 | T001, T018, T021, T022 |
| SC-008 | T020 |

**No FR or SC is left uncovered.** All 20 FRs and all 8 SCs map to at least one task.

---

## Summary

- **Total tasks: 25**
- **Phase breakdown**:
  - Setup: **3** (T001–T003)
  - Tests: **4** (T004–T007)
  - Core: **7** (T008–T014; M0=3, M1=2, M2=2)
  - Integration: **5** (T015–T019)
  - Polish: **5** (T020–T024)
  - Remediation: **1** (T025)
- **Story task counts** (labeled tasks):
  - US1 (persisted readable snacks): **5** (T004, T005, T008, T009, T010)
  - US2 (always terminal): **4** (T006, T011, T012, T025)
  - US3 (progress, no flash, stops polling): **3** (T007, T013, T014)
  - US4 (no regression): **0** labeled — covered by T001, T018, T019, T021, T022, T023 (per house style, integration/polish tasks are unlabeled)
  - Unlabeled (setup/integration/polish): **13**

## Notes

- `[P]` tasks touch different files and have no dependency on incomplete tasks; tasks editing `BatchService.java` are never `[P]`, and T014 depends on T013.
- Every task carries exact file paths (create or modify) and a clear done-definition.
- The frontend has no unit-test runner; frontend verification is Playwright.
- `POST /api/snacks/generate`, `POST /api/batches`, `RetryingLlmProvider`, `QuizService`, the DTOs, `batch.service.ts`, `snack-retry.service.ts`, the `BatchStatus` vocabulary, and the DB schema are intentionally unchanged (FR-016/FR-017/FR-018, data-model.md).
- The bounded join default (`BATCH_QUIZ_JOIN_TIMEOUT_SECONDS=240`) is configurable; SC-002's 5-minute window is a target, not a hard SLA (plan §Risks).
- Checklists live in `specs/005-batch-snack-generation-fix/checklists/` and must end with 0 unchecked items at implementation completion (T024).
