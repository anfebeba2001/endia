---
description: "Task list for Guarantee Bounded Snack Generation (backend liveness hardening over the 001/002/005 async batch flow)"
---

# Tasks: Guarantee Bounded Snack Generation

**Input**: Design documents from `/specs/009-bounded-snack-generation/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Branch**: `009-bounded-snack-generation`

## Organization

This is a small, surgical liveness/defect-prevention feature over the existing `001-batch-mvp-enhancements` /
`002-snack-retry-bound` / `005-batch-snack-generation-fix` async snack/batch flow (Spring Boot backend; the
Angular frontend is untouched). The confirmed current state is that the outbound HTTP call already has a
connect+read timeout, `RetryingLlmProvider` already caps retries, and `SnackService` already re-rolls at
most once. The remaining gaps closed here are: (1) **no per-subtopic wall-clock bound** around snack
generation, (2) **bounded quiz join does not finalize stuck items** so the batch can stay `IN_PROGRESS`,
and (3) the **empty-batch aggregation fall-through**. The change set is **2 modified backend main files
(`BatchService.java`, `LlmConfig.java`) + 1 resource file (`application.yml`) + 4 backend test files
(3 modified, 1 new)**. There is **no schema change**, **no endpoint/DTO change**, **no new status or enum
value**, **no frontend source change**, and **no new dependency**.

The tasks follow the plan's milestones (M0–M3) and are grouped into the five canonical phases:
**Setup → Tests → Core → Integration → Polish**. Story labels (`[US1]`..`[US7]`) provide traceability to
`spec.md`; setup/integration/polish tasks carry no label and are mapped by the coverage table at the end.

### Milestone ↔ story ↔ phase map

| Milestone | Plan phase | Story | Conceptual phase |
|-----------|-----------|-------|------------------|
| — | Setup | none | Setup |
| — | Tests (TDD) | US1–US5 | Tests |
| M0 | Phase 0 — per-subtopic bounded snack generation (`snackExecutor` + config) | US1/US2/US3 | Core |
| M1 | Phase 1 — terminal aggregate guarantee (finalization + empty-batch guard) | US4 | Core |
| M2 | Phase 2 — unit + integration tests | US1–US5 | Tests (authored) / Integration (green) |
| M3 | Phase 3 — contracts, docs, verification | US6/US7 (preservation) | Integration / Polish |

> **US6 (bounded polling preserved)** and **US7 (safe bounded logging)** add no new product behavior beyond
> verification and a log line; per house style their tasks live in Core (logging) and Integration/Polish
> (polling non-regression) and are mapped in the Requirement Coverage table.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Which user story this task belongs to (`US1`–`US7`); absent for setup/integration/polish
- Base paths (verified against the repo):
  - Backend main: `backend/src/main/java/com/endia/`
  - Backend resources: `backend/src/main/resources/`
  - Backend tests: `backend/src/test/java/com/endia/`
  - Frontend: `frontend/src/app/`
  - Frontend E2E: `frontend/e2e/`
  - Feature docs: `specs/009-bounded-snack-generation/`

> **Testing constraint (from `research.md` / `plan.md`)**: backend unit tests are **JUnit 5 + Mockito**;
> backend integration tests are **Spring Boot Test + Testcontainers MySQL** (`@Tag("integration")`,
> excluded from default `mvn test`, run with `mvn test -Pintegration`). Frontend has **no unit-test
> runner**; verification is **Playwright** (`frontend/e2e/`) and is non-regression only. Test tasks are
> written first and are expected to fail (or fail to compile, where the constructor changes) before their
> implementation targets land.

> **Test wall-clock constraint**: the never-returning integration test MUST drive
> `batch.snack-timeout-seconds` and `batch.quiz-join-timeout-seconds` down via `@DynamicPropertySource`
> (e.g. `2`) and use an **interruptible** latch double. Relying on the `240 s` defaults would make the
> suite unusable; a non-interruptible spin would hang JVM shutdown.

> **Known limitation (plan §Risks, flagged for orchestrator)**: `CompletableFuture.orTimeout` completes
> the future but does **not** interrupt the running task. A pathological never-returning provider leaves
> one `snack-worker-` thread and its transaction parked. This is a bounded resource cost, not a liveness
> gap, precisely because the pool is **bounded with daemon threads and the default AbortPolicy** (T013).
> The batch worker still proceeds and terminates.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify the baseline and confirm scope boundaries before any behavior change. No production code is written here.

- [X] T001 Verify the baseline: run `cd backend && mvn test` (green), `cd frontend && npm run build` (green), and `cd frontend && npx playwright test` (existing suite green); record the baseline test counts. Required to prove SC-009 (no regression) later. (FR-022, SC-009) — Baseline: backend 89 unit tests pass (0 failures), `npm run build` green, Playwright 47 passed (5.5m).
- [X] T002 [P] Confirm no dependency or schema change is required: verify `backend/pom.xml`, `frontend/package.json`, and `frontend/package-lock.json` need no edit; confirm there is no Flyway/Liquibase migration and `ddl-auto=update` produces no delta (consistent with `data-model.md` §Persistence/Schema Change Statement). Record the finding. (FR-021, FR-022) — Confirmed: no Flyway/Liquibase dependency or migration dir; `ddl-auto=update`; no pom/package edits needed.
- [X] T003 [P] Confirm the existing config seams and frozen surfaces are present and record current values: `llm.timeout-seconds` (default 30, connect+read in `LlmConfig.llmRestClient`), `batch.retry.max-attempts` (default 3 in `RetryingLlmProvider`), `batch.quiz-join-timeout-seconds` (default 240), frontend `MAX_POLLING_WINDOW_MS` (default 120000) and the `SnackRetryService` reattempt cap. These are preserved and verified, not redesigned. (FR-002, FR-004, FR-018, FR-023, SC-010) — Confirmed: llm 30, retry max 3, quiz join 240, `MAX_POLLING_WINDOW_MS` 120000, `MAX_SNACK_ATTEMPTS` 3.

**Checkpoint**: Baseline green, no-dependency/no-schema confirmed, existing bounds identified — test authoring can begin.

---

## Phase 2: Tests (TDD — write first, ensure they FAIL)

**Purpose**: Encode the required bounded behavior as failing tests before implementing it. Tests touching `BatchServiceTest.java` / the new integration test may not compile until their Core task lands; that is the expected pre-implementation failure.

> **NOTE**: Write these tests FIRST and confirm they fail for the intended reason (or fail to compile for the constructor change) before starting Phase 3.

- [X] T004 [P] [US2] Extend `backend/src/test/java/com/endia/application/service/SnackServiceTest.java`: assert the re-roll is **exactly one** — two invalid `GeneratedSnack` responses produce exactly **two** provider calls, then a typed `ServiceUnavailableException`; verify no third call. (FR-005, FR-006, SC-002)
- [X] T005 [P] [US2] Extend `backend/src/test/java/com/endia/infrastructure/llm/RetryingLlmProviderTest.java`: confirm a retryable `LlmGenerationException` produces **exactly** `maxAttempts` (default 3) delegate calls and a non-retryable cause passes through after **one** call — no further retry. (FR-004, FR-006, SC-002, SC-009)
- [X] T006 [P] [US1] Extend `backend/src/test/java/com/endia/infrastructure/llm/OpenAiLlmProviderTest.java`: add a config-driven case where a `LlmConfig`-built `RestClient` with a small `llm.timeout-seconds` fails a delayed `MockWebServer` response with `LlmErrorCause.TIMEOUT` at or before the bound (proves the configured property drives the connect+read bound). Keep the existing retry/cause tests. (FR-001, FR-002, FR-003, SC-001, SC-010)
- [X] T007 [P] [US3] Update `backend/src/test/java/com/endia/application/service/BatchServiceTest.java` for the new constructor deps (`@Qualifier("snackExecutor") TaskExecutor snackExecutor`, `int snackTimeoutSeconds`) and add the **per-subtopic timeout** test: a `snackService` stub that blocks on a latch, a real single-thread `TaskExecutor`, `snackTimeoutSeconds = 1`; assert the item becomes `FAILED`/`TIMEOUT`, the phase is cleared, and a second (healthy) item still becomes `READY` — proving the worker is not blocked. (FR-007, FR-008, FR-009, FR-010, SC-003)
- [X] T008 [US4] Extend `BatchServiceTest.java` with: (a) **quiz-join finalization** — a hung quiz stub plus a short join asserts the item's `quizStatus` is `FAILED`/`TIMEOUT` and the session status is terminal (`FAILED`/`PARTIAL`), never `IN_PROGRESS` with a `GENERATING` item; (b) **empty batch** — `aggregateStatus(empty)` yields a terminal status; (c) **config normalization** — a `snackTimeoutSeconds <= 0` falls back to the documented default `240`. Depends on T007 (same file). (FR-011, FR-012, FR-014, SC-004, SC-010)
- [X] T009 [US2] Extend `BatchServiceTest.java` with a **finite call-count** test: a fast always-failing snack stub asserts the total number of provider calls is finite and bounded (`<= maxAttempts × re-roll allowance`) and that no further call is issued after the bound. Depends on T007/T008 (same file). (FR-006, FR-007, SC-002)
- [X] T010 [US4] Create `backend/src/test/java/com/endia/application/integration/BoundedSnackGenerationIntegrationTest.java` (NEW, `@Tag("integration")`): real Spring context + Testcontainers MySQL + a **never-returning** `@MockitoBean LlmProvider` whose `generateSnack`/`generateQuiz` block on an interruptible `CountDownLatch`; short `batch.snack-timeout-seconds=2` / `batch.quiz-join-timeout-seconds=2` via `@DynamicPropertySource`. Case **all items hang**: every item settles `FAILED`/`TIMEOUT`, the batch reaches a terminal status within the bounded window, and no item is `PENDING`/`GENERATING`. (FR-012, FR-013, FR-014, SC-004, SC-008) — Green.
- [X] T011 [US3] Add to `BoundedSnackGenerationIntegrationTest.java` the **one-hangs-others-healthy** case: a stub that hangs only for a designated subtopic while other subtopics return valid content; assert the hanging item is `FAILED`/`TIMEOUT`, the healthy items are `READY`, the batch reaches a terminal status (`PARTIAL`), and the worker proceeded. Depends on T010 (same file). (FR-010, FR-014, SC-003, SC-004) — Green.
- [X] T012 [US5] Add to `BoundedSnackGenerationIntegrationTest.java` the **HTTP liveness** case under the never-returning provider: `POST /api/batches` returns `202` promptly (well under the snack bound) and does not wait for generation; `GET /api/batches/{id}` returns promptly while the batch is still non-terminal. Release the latch in `@AfterEach`/context shutdown so the test JVM exits promptly. Depends on T010 (same file). (FR-015, FR-016, FR-017, SC-005) — Green.

**Checkpoint**: Failing tests define the target behavior. Phase 3 may begin.

---

## Phase 3: Core Implementation

**Purpose**: Implement M0 (per-subtopic bounded snack generation) and M1 (terminal aggregate guarantee). Tasks touching `BatchService.java` are sequential; different files may run in parallel.

### M0 — Per-subtopic bounded snack generation (US1/US2/US3, P1)

- [X] T013 [P] [US3] In `backend/src/main/java/com/endia/infrastructure/llm/LlmConfig.java`, add a separate `@Bean(name = "snackExecutor")` `ThreadPoolTaskExecutor` (`snack-worker-`, core=2, max=8, queue=50) with **daemon threads** and the **default `AbortPolicy`** (never `CallerRunsPolicy` — caller-runs would run the hang synchronously on the batch worker before a future is returned, defeating the bound). Leave `batchExecutor`/`quizExecutor` unchanged. (FR-008, FR-010, FR-021)
- [X] T014 [P] [US3] In `backend/src/main/resources/application.yml`, add `batch.snack-timeout-seconds: ${BATCH_SNACK_TIMEOUT_SECONDS:240}` and make `batch.quiz-join-timeout-seconds: ${BATCH_QUIZ_JOIN_TIMEOUT_SECONDS:240}` explicit. Documented defaults: snack bound 240, quiz join 240. (FR-011, FR-023, SC-010)
- [X] T015 [US3] In `backend/src/main/java/com/endia/application/service/BatchService.java`, inject `@Qualifier("snackExecutor") TaskExecutor snackExecutor` and `@Value("${batch.snack-timeout-seconds:240}") int snackTimeoutSeconds` into the constructor; normalize an invalid value (`<= 0`) to the documented default `240` so the edge case cannot hang or fail unpredictably. Depends on T013, T014. (FR-007, FR-008, FR-011, SC-010)
- [X] T016 [US3] In `BatchService.java`, add `generateSnackBounded(UUID subtopicId, SnackPhaseListener listener)` that runs `snackService.generateSingleSnack(subtopicId, listener)` on `snackExecutor` via `CompletableFuture.supplyAsync(...)` and awaits `.orTimeout(snackTimeoutSeconds, TimeUnit.SECONDS).join()`; translate `TimeoutException` to `LlmGenerationException(LlmErrorCause.TIMEOUT, false, ...)` and `RejectedExecutionException` to `LlmGenerationException(LlmErrorCause.UNAVAILABLE, false, ...)`, preserving any inner typed `LlmGenerationException`. Depends on T015. (FR-007, FR-009, FR-021)
- [X] T017 [US3] In `BatchService.java`, replace the direct `snackService.generateSingleSnack(subtopicId, listener)` call in `runBatch` with `generateSnackBounded(...)`; keep the existing per-item `try`/`catch (LlmGenerationException)` (sets `snackStatus = FAILED`, `snackCause = e.getCauseType()`) and the `finally` phase clear so one hung item cannot block the worker. Depends on T016. (FR-009, FR-010, SC-003)
- [X] T018 [US7] In `BatchService.java`, log the bounded per-subtopic outcome with the real cause only (subtopic id + configured bound + `TIMEOUT`), with no prompt bodies, API keys, or provider payloads. Depends on T016. (FR-019, FR-020, SC-007)

### M1 — Terminal aggregate guarantee (US4, P1)

- [X] T019 [US4] In `BatchService.java`, after the bounded quiz join and before `aggregateStatus`, reload the session items and **finalize any item still `PENDING`/`GENERATING`** (snack or quiz) to `FAILED` with `LlmErrorCause.TIMEOUT`, saving changed items, so the aggregation input is always terminal. Depends on T017. (FR-012, FR-014, SC-004)
- [X] T020 [US4] In `BatchService.java`, guard `aggregateStatus(...)` for an **empty item list** to return a terminal status (`FAILED`) instead of falling through to `IN_PROGRESS`, so a batch with no subtopics terminates immediately. Depends on T019. (FR-012, SC-004)
- [X] T021 [US7] In `BatchService.java`, log the bounded quiz-join outcome with the real typed cause (session id + bound) and no payload. Depends on T019. (FR-019, FR-020, SC-007)

**Checkpoint**: M0–M1 implemented; the Phase 2 tests for the corresponding behavior should now pass.

---

## Phase 4: Integration

**Purpose**: Reconcile contracts/docs, confirm the frozen surfaces, and run the integration/non-regression suites.

- [X] T022 [P] Verify/update `specs/009-bounded-snack-generation/contracts/batch-status.md`: restate the unchanged HTTP shape with the `202`-immediate, non-blocking-status, terminal-status, and no-pending-item guarantees. (FR-012, FR-015, FR-016, FR-017, SC-005) — Verified accurate against shipped `BatchController`/`BatchService`; no edit required.
- [X] T023 [P] Verify/update `specs/009-bounded-snack-generation/contracts/snack-generation-bounds.md`: confirm the bounded-behavior contract (per-call HTTP timeout, retry bound, single re-roll, per-subtopic bound → typed `FAILED`, non-caller-runs executor policy) and the config properties/defaults match the shipped code. (FR-001, FR-004, FR-005, FR-007, FR-008, FR-009, FR-011, FR-023, SC-010) — Verified accurate; defaults 240/240, AbortPolicy, typed TIMEOUT all match.
- [X] T024 [P] Verify/update `specs/009-bounded-snack-generation/quickstart.md`: confirm the manual hang reproduction (small `BATCH_SNACK_TIMEOUT_SECONDS`) and the exact test commands match the shipped behavior and the no-schema statement. (US1–US7) — Verified; env overrides resolve via Spring relaxed binding; no schema change.
- [X] T025 Confirm the frozen surfaces are unchanged: no edits to `backend/.../interfaces/rest/` controllers/DTOs, `RetryingLlmProvider`, the LLM port/prompts, `SnackService` generation/validation logic, `QuizService`, `QuizQualityGate`, `frontend/src/app/services/batch.service.ts`, `frontend/src/app/services/snack-retry.service.ts`, or `batchExecutor`/`quizExecutor` sizing; confirm the `llm.timeout-seconds` (30), `batch.retry.max-attempts` (3), single re-roll, and 120 s polling window are preserved. (FR-002, FR-004, FR-005, FR-018, FR-021, FR-022, SC-009) — `git status` shows only `BatchService`, `LlmConfig`, `application.yml`, 4 test files, new integration test, and tasks.md. All frozen surfaces untouched.
- [X] T026 Run `cd backend && mvn test -Pintegration` and confirm the new `BoundedSnackGenerationIntegrationTest` plus the existing `BatchGenerationIntegrationTest` and `BatchFlowIntegrationTest` are green. Depends on T010–T021. (FR-012, FR-013, FR-014, FR-015, FR-016, FR-017, SC-004, SC-005, SC-009) — Green: `BoundedSnackGenerationIntegrationTest` 3/3, `BatchGenerationIntegrationTest` 3/3, `BatchFlowIntegrationTest` 2/2, `QuizFlowIntegrationTest` 4/4. NOTE: `ThemeFlowIntegrationTest.contextLoads` fails (pre-existing, confirmed on clean HEAD; order-dependency within the class: asserts `themeRepository.count()==0` after another method in the same class inserts a theme; fails whether run alone or with the class, not fixed by isolation); untouched by 009. Docker 29 required `-DargLine="-Dapi.version=1.44"` (docker-java default API 1.32 rejected).
- [X] T027 [P] Run `cd frontend && npx playwright test` and confirm the existing bounded-polling/retry suite (`polling-termination.spec.ts`, `retry-bound.spec.ts`, `batch-empty-state.spec.ts`, `progressive-delivery.spec.ts`, `loading-states.spec.ts`) is green; add a spec only if a gap is found. No frontend source change. Depends on Phase 3. (FR-018, SC-006, SC-009) — 48/48 passed (5.7m). A genuine gap was found for the server-settled terminal transition (GENERATING → FAILED/TIMEOUT within the polling window), so a focused spec `frontend/e2e/bounded-snack-settle.spec.ts` was added (1 test, passed). No frontend source change.

**Checkpoint**: Contracts and docs reflect the shipped behavior; integration and non-regression suites are green.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final verification, security/no-regression gates, and checklist sign-off.

- [X] T028 [P] Sensitive-data audit: inspect all new/changed timeout and failure log statements and user-facing messages (per-subtopic bound WARN, quiz-join WARN, typed-cause paths) and confirm they contain only typed `LlmErrorCause` strings and ids — no prompt bodies, API keys, provider request/response payloads, or personal data. (FR-020, SC-007) — Audit passed: the three new WARN lines log only subtopic/batch id, configured bound, and the typed cause enum; exception messages are static strings.
- [X] T029 [P] Configuration verification: confirm every new/changed bound is configurable via properties/environment with a documented default, and that changing a bound changes observed behavior (covered by T008/T014 and the integration `@DynamicPropertySource` short bounds). Record the property table. (FR-011, FR-023, SC-010) — `llm.timeout-seconds` 30 (preserved), `batch.retry.max-attempts` 3 (preserved), `batch.quiz-join-timeout-seconds` 240 / `BATCH_QUIZ_JOIN_TIMEOUT_SECONDS`, `batch.snack-timeout-seconds` 240 / `BATCH_SNACK_TIMEOUT_SECONDS`; behavior change observed in T008 and the 2s integration bounds.
- [X] T030 Run `cd backend && mvn test` and confirm the updated `BatchServiceTest`, `SnackServiceTest`, `OpenAiLlmProviderTest`, and `RetryingLlmProviderTest` are green alongside the full unit suite. Depends on T004–T009, T013–T021. (SC-002, SC-008, SC-009) — 97/97 unit tests pass, 0 failures/errors.
- [X] T031 Execute the `quickstart.md` manual validation against a hanging provider: US1 (call fails at/before the configured bound), US2 (finite call count), US3 (item `FAILED`/`TIMEOUT`, worker proceeds), US4 (batch reaches terminal status, no pending/generating items), US5 (202 immediate + GET prompt), US6 (bounded polling), US7 (typed causes only, no sensitive data); record results. Depends on T024. (SC-001, SC-002, SC-003, SC-004, SC-005, SC-006, SC-007, SC-008) — Executed against a live backend on :8081 with `BATCH_SNACK_TIMEOUT_SECONDS=5`, `LLM_BASE_URL=http://10.255.255.1/v1` (connect hang) and a real MySQL: `POST` 202 in 0.485s, immediate `GET` 200 in 0.102s, snack bound logged at 5s → item `FAILED`/`TIMEOUT`, quiz-join bound at 5s → quiz finalized `FAILED`/`TIMEOUT`, batch reached terminal `FAILED` at ~10s with no pending/generating items; logs show only ids/bound/cause. US2/US6 covered by the unit + Playwright suites.
- [X] T032 Final validation: mark every completed task `[x]` in this file, confirm **all** checklists under `specs/009-bounded-snack-generation/checklists/` have **0 unchecked items**, and record the final task/phase counts in the Summary section. (SC-009) — All 32 tasks `[x]`; all 5 checklists 0 unchecked (api 35, requirements 60, security 21, test 34, ux 18).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Tests (Phase 2)**: Depends on Setup; written first and expected to fail.
- **Core (Phase 3)**: Depends on Tests being authored. M0 and M1 touch `BatchService.java` sequentially; `LlmConfig.java` and `application.yml` are independent.
- **Integration (Phase 4)**: Depends on the relevant Core tasks landing.
- **Polish (Phase 5)**: Depends on all Core + Integration tasks.

### Within each milestone

- **M0 (US1/US2/US3)**: T013 and T014 are independent (different files) → T015 → T016 → T017; T018 depends on T016.
- **M1 (US4)**: T019 → T020; T021 depends on T019.
- Tests (Phase 2) MUST be authored and failing before their implementation target.

### Sequential vs parallel rules

- A task may be marked `[P]` only when it edits a **different file** than other in-flight tasks and has no dependency on an incomplete task.
- Tasks editing `BatchService.java` (T015, T016, T017, T018, T019, T020, T021) are **never** `[P]` relative to each other.
- Tasks editing `BatchServiceTest.java` (T007, T008, T009) are **never** `[P]` relative to each other.
- Tasks editing `BoundedSnackGenerationIntegrationTest.java` (T010, T011, T012) are **never** `[P]` relative to each other.
- T013 (`LlmConfig.java`) and T014 (`application.yml`) are different files → parallel.

### Parallel opportunities

- **Setup**: T002 and T003 are different concerns — parallel.
- **Tests**: T004 (SnackServiceTest), T005 (RetryingLlmProviderTest), T006 (OpenAiLlmProviderTest), and T007 (BatchServiceTest) are distinct files and can be authored in parallel; T008/T009 then follow T007, and T011/T012 follow T010.
- **Core**: T013 (LlmConfig) and T014 (application.yml) are parallel; the `BatchService.java` chain is sequential.
- **Integration**: T022, T023, T024 are separate docs — parallel; T027 (frontend) is parallel with T026 (backend integration).
- **Polish**: T028 (audit) and T029 (config) are parallel with the suite runs.

---

## Parallel Example: Tests phase

```bash
# Author the independent failing tests in parallel:
Task: "T004 SnackServiceTest single re-roll call-count"
Task: "T005 RetryingLlmProviderTest exactly-maxAttempts"
Task: "T006 OpenAiLlmProviderTest config-driven connect+read bound"
Task: "T007 BatchServiceTest ctor update + per-subtopic timeout"
```

---

## Implementation Strategy

### MVP First

1. Complete Setup (T001–T003).
2. Author failing tests (T004–T012).
3. **M0** (T013–T018): `snackExecutor` + `batch.snack-timeout-seconds` + `generateSnackBounded` — independently delivers US1/US2/US3 (a stuck subtopic fails fast and the worker proceeds).
4. **M1** (T019–T021): quiz-join finalization + empty-batch guard — independently delivers US4 (the batch always reaches a terminal status).
5. **STOP and VALIDATE**: run the backend + Playwright suites and the quickstart hang recipes.
6. M3 (contracts/docs/verification) closes out US5/US6/US7 and the no-regression story.

### Incremental Delivery

M0 restores per-item liveness; M1 makes the batch aggregate terminal even against a never-returning
provider; Phase 4/5 lock the preserved 002/005 behavior and the sensitive-data constraint.

---

## Requirement Coverage

| Spec ID | Covered by |
|---------|-----------|
| FR-001 | T006, T023 |
| FR-002 | T003, T006, T023, T025 |
| FR-003 | T006 |
| FR-004 | T003, T005, T023, T025 |
| FR-005 | T004, T023, T025 |
| FR-006 | T004, T005, T009 |
| FR-007 | T007, T009, T015, T016, T023 |
| FR-008 | T007, T013, T014, T015, T023 |
| FR-009 | T007, T016, T017, T023 |
| FR-010 | T007, T011, T013, T017 |
| FR-011 | T008, T014, T015, T029 |
| FR-012 | T008, T010, T019, T020, T022, T026 |
| FR-013 | T010, T026 |
| FR-014 | T008, T010, T011, T019, T022, T026 |
| FR-015 | T012, T022, T026 |
| FR-016 | T012, T022, T026 |
| FR-017 | T012, T022, T026 |
| FR-018 | T003, T025, T027 |
| FR-019 | T018, T021 |
| FR-020 | T018, T021, T028 |
| FR-021 | T002, T013, T016, T025 |
| FR-022 | T001, T002, T025, T027, T030 |
| FR-023 | T003, T014, T023, T029 |
| SC-001 | T006, T010, T031 |
| SC-002 | T004, T005, T009, T030, T031 |
| SC-003 | T007, T011, T031 |
| SC-004 | T008, T010, T011, T019, T020, T026, T031 |
| SC-005 | T012, T022, T026, T031 |
| SC-006 | T027, T031 |
| SC-007 | T018, T021, T028, T031 |
| SC-008 | T010, T030, T031 |
| SC-009 | T001, T005, T025, T026, T027, T030, T032 |
| SC-010 | T003, T006, T008, T014, T029 |

**No FR or SC is left uncovered.** All 23 FRs and all 10 SCs map to at least one task.

---

## Summary

- **Total tasks: 32**
- **Completion status**: **32/32 complete** (`[x]`). All 5 checklists under `checklists/` have **0 unchecked items**.
- **Verification**: `mvn test` 97/97; `mvn test -Pintegration` — `BoundedSnackGenerationIntegrationTest` 3/3, `BatchGenerationIntegrationTest` 3/3, `BatchFlowIntegrationTest` 2/2, `QuizFlowIntegrationTest` 4/4 (pre-existing `ThemeFlowIntegrationTest.contextLoads` order-dependency noted in T026); Playwright 48/48 (including the new `bounded-snack-settle.spec.ts`); manual hang validation executed against a live backend + MySQL.
- **Phase breakdown**:
  - Setup: **3** (T001–T003)
  - Tests: **9** (T004–T012)
  - Core: **9** (T013–T021; M0=6, M1=3)
  - Integration: **6** (T022–T027)
  - Polish: **5** (T028–T032)
- **Story task counts** (labeled tasks):
  - US1 (every outbound call time-bounded): **1** (T006)
  - US2 (retries/re-rolls finite): **3** (T004, T005, T009)
  - US3 (per-subtopic fails fast): **6** (T007, T011, T013, T015, T016, T017)
  - US4 (batch always terminal): **4** (T008, T010, T019, T020)
  - US5 (no unanswered HTTP request): **1** (T012)
  - US6 (bounded polling preserved): **0** labeled — covered by T003, T025, T027 (per house style, integration/polish tasks are unlabeled)
  - US7 (safe bounded logging): **2** (T018, T021)
  - Unlabeled (setup/integration/polish): **16**

## Notes

- `[P]` tasks touch different files and have no dependency on incomplete tasks; tasks editing `BatchService.java` are never `[P]`, and the `BatchServiceTest.java` / `BatchServiceTest` / integration-test groups are sequential.
- Every task carries exact file paths (create or modify) and a clear done-definition.
- The frontend has no unit-test runner; frontend verification is Playwright (non-regression only).
- `POST /api/snacks/generate`, `POST /api/batches`, all DTOs, `RetryingLlmProvider`, `SnackService` generation logic, `QuizService`, `QuizQualityGate`, `batch.service.ts`, `snack-retry.service.ts`, the `BatchStatus` vocabulary, and the DB schema are intentionally unchanged (FR-021/FR-022, data-model.md).
- **`orTimeout` does not interrupt the running task** (plan §Risks). T013 bounds the blast radius with a bounded, daemon, AbortPolicy `snackExecutor`; the abandoned-task resource cost is flagged for the orchestrator as a limitation, not a liveness gap.
- The never-returning integration test MUST use short configured bounds and an interruptible latch (see preamble) so the suite is fast and the JVM exits.
- Checklists live in `specs/009-bounded-snack-generation/checklists/` and must end with 0 unchecked items at implementation completion (T032).
