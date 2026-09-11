---
description: "Task list for the Endia bounded snack reattempts and polling termination feature (bug-fix over the 001 batch flow)"
---

# Tasks: Bounded Snack Reattempts and Polling Termination

**Input**: Design documents from `/specs/002-snack-retry-bound/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Branch**: `002-snack-retry-bound`

## Organization

This is a small bug-fix over the existing `001-batch-mvp-enhancements` async snack batch flow (Spring Boot backend + Angular frontend). It is **frontend-heavy**: 3 modified frontend files + 1 new frontend service + 1 injection token + 1 modified backend service. There is **no schema change** and **no new dependency**.

The tasks follow the plan's milestones (M0–M4) and are grouped into the five canonical phases: **Setup → Tests → Core → Integration → Polish**. Story labels (`[US1]`..`[US4]`) provide traceability to `spec.md`; setup/integration/polish tasks carry no label and are mapped by the coverage table at the end.

### Milestone ↔ story ↔ phase map

| Milestone | Plan phase | Story | Conceptual phase |
|-----------|-----------|-------|------------------|
| — | Setup | none | Setup |
| — | Tests (TDD) | US1–US4 | Tests |
| M0 | Phase 0 — polling terminality + bounded window | US2 | Core |
| M1 | Phase 1 — bounded explicit-error reattempts | US1 + US3 | Core |
| M2 | Phase 2 — manual retry resets budget | US4 | Core |
| M3 | Phase 3 — backend `retrying` correctness | US1 (FR-011) | Core |
| M4 | Phase 4 — tests, contracts, docs | none | Integration / Polish |

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Which user story this task belongs to (`US1`–`US4`); absent for setup/integration/polish
- Base paths (verified against the repo):
  - Backend: `backend/src/main/java/com/endia/`
  - Backend tests: `backend/src/test/java/com/endia/`
  - Frontend: `frontend/src/app/`
  - Frontend E2E: `frontend/e2e/`
  - Feature docs: `specs/002-snack-retry-bound/`

> **Testing constraint (from `research.md` / `plan.md`)**: there is **no frontend unit-test runner** (`*.spec.ts` under `frontend/src/app/` do not exist). Frontend verification is via **Playwright** (`frontend/e2e/`) plus the pure `SnackRetryService` being deliberately dependency-free so it can be unit-tested if a runner is added. Backend verification is via **JUnit 5 + Mockito** (`BatchServiceTest`). Test tasks are written first where feasible and are expected to fail before their implementation tasks land.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify the baseline and repository hygiene before any behavior change. No production code is written here.

- [ ] T001 Verify the baseline in `backend/` and `frontend/`: run `mvn -q test` (green), `npm run build` (green), and `npx playwright test` (the existing 17 specs green). Confirm `.env` at repo root documents `LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`, `DB_*`, and the `BATCH_RETRY_*` vars. This baseline is required to prove SC-007 (no regression) later.
- [ ] T002 [P] Create/adjust ignore files per the detected stack: add `backend/.gitignore` (Maven `target/`, `*.class`, IDE files) since it does not exist, and add `frontend/playwright-report/`, `frontend/test-results/`, and `frontend/.playwright-cli/` to `frontend/.gitignore` (or the root `.gitignore`). Confirm `frontend/test-results/` is currently untracked/ignored.
- [ ] T003 [P] Confirm no dependency or lockfile change is required: verify `backend/pom.xml` and `frontend/package.json`/`package-lock.json` need no edit for this feature (the design is dependency-free) and record that in the PR description.

**Checkpoint**: Baseline green and repo hygiene correct — implementation can begin.

---

## Phase 2: Tests (TDD — write first, ensure they FAIL)

**Purpose**: Encode the required behavior as failing tests before implementing it. Backend tests are executable TDD; frontend specs are Playwright (no unit runner exists) and will fail against current behavior.

> **NOTE**: Write these tests FIRST and confirm they fail for the intended reason before starting Phase 3.

- [ ] T004 [P] [US1] Extend `backend/src/test/java/com/endia/application/service/BatchServiceTest.java` with failing `retrying` assertions for FR-011: keep the existing `GENERATING → true` case; add `all items PENDING → false` (batch just created) and `all items terminal (READY/FAILED) → false`. These fail until T024.
- [ ] T005 Extend `frontend/e2e/helpers.ts` with reusable batch mocks needed by the new specs: a `POST /api/batches` counter/always-fail option, per-target failure injection, a `PARTIAL`/`COMPLETE`/`FAILED`/stuck-`IN_PROGRESS` status responder, and a helper to serve a short `MAX_POLLING_WINDOW_MS` override. Blocking for T006–T009 (shared file).
- [ ] T006 [P] [US2] Create `frontend/e2e/polling-termination.spec.ts`: assert polling stops (request count stops increasing) for `PARTIAL`, `COMPLETE`, and `FAILED`, and that a stuck `IN_PROGRESS` batch terminates with a terminal error under a short polling-window override. Covers FR-008/009/010, SC-003/SC-004.
- [ ] T007 [P] [US1] Create `frontend/e2e/retry-bound.spec.ts`: force every generation to fail and assert exactly **3** `POST /api/batches` cycles for the target, **no 4th**, a terminal error naming the cause, and the spinner stopped. Covers FR-001/002/005, SC-001/SC-002.
- [ ] T008 [P] [US3] Create `frontend/e2e/retry-explicit-error.spec.ts`: hold a batch `IN_PROGRESS` with no failed item and assert **zero** extra `POST /api/batches` while time passes; then inject an explicit failure and assert exactly one reattempt; assert no overlapping attempt while a call is in flight. Covers FR-003/004/007, SC-005.
- [ ] T009 [P] [US4] Create `frontend/e2e/manual-retry.spec.ts`: after exhaustion assert the retry affordance is visible; one click starts exactly one new cycle with progress shown; a rapid double-click starts at most one cycle; a successful manual retry clears the terminal error and renders the snack. Covers FR-006, SC-006.

**Checkpoint**: Failing tests define the target behavior. Phase 3 may begin.

---

## Phase 3: Core Implementation

**Purpose**: Implement M0 (polling terminality + bounded window), M1 (bounded explicit-error reattempts), M2 (manual retry), and M3 (backend `retrying`). Tasks touching the same file are sequential; different files may run in parallel.

### M0 — Polling terminality + bounded window (US2, P1)

- [ ] T010 [US2] In `frontend/src/app/services/batch.service.ts`, change `isTerminal` to the negative predicate `status.status !== 'IN_PROGRESS'` (FR-008/FR-009). `takeWhile((s) => !this.isTerminal(s), true)` then emits the terminal value and completes.
- [ ] T011 [US2] In `frontend/src/app/services/batch.service.ts`, add the `BatchPollingTimeoutError` class and bound `pollStatus` by racing the poll stream against `timer(maxWindowMs)` mapped to `throwError(() => new BatchPollingTimeoutError(maxWindowMs))`; add the `maxWindowMs` parameter (FR-010). Depends on T010.
- [ ] T012 [US2] In `frontend/src/app/services/batch.service.ts`, export the `MAX_POLLING_WINDOW_MS` `InjectionToken<number>` with `providedIn: 'root'` and factory default `120_000`; inject it into `BatchService` and use it as the `pollStatus` default (FR-010). Depends on T011.
- [ ] T013 [US2] In `frontend/src/app/app.config.ts`, add the explicit `MAX_POLLING_WINDOW_MS` provider seam (documented default) so deployments/tests can override the window without code changes elsewhere (FR-010). Depends on T012.
- [ ] T014 [US2] In `frontend/src/app/pages/snacks/snacks.component.ts`, handle `BatchPollingTimeoutError` in the poll error path: set a terminal, user-visible error, set `batchStatus` to a terminal value, and stop `creating`/spinner (FR-010, SC-004). Depends on T011.

### M1 — Bounded, explicit-error reattempts (US1 + US3, P1/P2)

- [ ] T015 [US1] Create `frontend/src/app/services/snack-retry.service.ts` (NEW, dependency-free, root-provided): per-target `attempts: Map<targetId, number>`, `inFlight: Set<targetId>`, and API `registerAttempt`, `attemptsFor`, `canRetry` (`!inFlight && attempts < 3`), `markInFlight`, `clearInFlight`, `isInFlight`, `hasInFlight`, `resetBudget`, `resetAll` (FR-001/002/006/007).
- [ ] T016 [US1] In `frontend/src/app/pages/snacks/snacks.component.ts`, inject `SnackRetryService`; on `init()` call `resetAll()` and register attempt 1 for every selected target before/after the initial `createBatch` (FR-001/002). Depends on T015.
- [ ] T017 [US1] In `frontend/src/app/pages/snacks/snacks.component.ts`, on each poll scan `items` for an explicit `snackStatus === 'FAILED'` (or non-null `snackCause`); for each failed, non-exhausted, non-in-flight target call `createBatch(themeId, [targetId])` (single-target), `registerAttempt` + `markInFlight`, and merge the returned item by `subtopicId` (FR-003/004/007, contract C6). Depends on T015, T016.
- [ ] T018 [US1] In `frontend/src/app/pages/snacks/snacks.component.ts`, add an `ownerBatchId: Map<targetId, batchId>` guard so a stale poll of the original batch cannot re-trigger a target already handed to a retry batch (FR-007, contract C5). Depends on T017.
- [ ] T019 [US3] In `frontend/src/app/pages/snacks/snacks.component.ts`, treat a failed `createBatch` request as an explicit error: consume one attempt for the affected target, respect the 3-attempt bound, and never start a timer-based retry (FR-003 scenario 3, FR-004). Depends on T015.
- [ ] T020 [US1] In `frontend/src/app/pages/snacks/snacks.component.ts` and `frontend/src/app/pages/snacks/snacks.component.html`, on exhaustion set a terminal per-target error naming the typed cause, stop progress, and disable the retry affordance while a cycle is in flight (FR-005, FR-007). Depends on T017.
- [ ] T021 [US1] In `frontend/src/app/pages/snacks/snacks.component.ts`, set the `retrying` signal to `status.retrying || this.retryService.hasInFlight()` (FR-011 frontend half, contract C10). Depends on T015.

### M2 — Manual retry resets the budget (US4, P2)

- [ ] T022 [US4] In `frontend/src/app/pages/snacks/snacks.component.ts`, make `retry(subtopicId)` use the passed id: `resetBudget(target)`, clear that target's terminal error, and start exactly **one** new single-target cycle; guard rapid double-invocation via the in-flight check (FR-006, US4.2/US4.3). Depends on T015, T018.
- [ ] T023 [US4] In `frontend/src/app/pages/snacks/snacks.component.html`, ensure the existing retry affordance is present after exhaustion and disabled while a cycle is in flight; minimal copy only, no layout redesign (US4.1). Depends on T020, T022.

### M3 — Backend `retrying` correctness (FR-011)

- [ ] T024 [US1] In `backend/src/main/java/com/endia/application/service/BatchService.java`, change `getStatus` so `retrying = items.stream().anyMatch(i -> i.getSnackStatus() == GENERATING || i.getQuizStatus() == GENERATING)` instead of `session.getStatus() == BatchStatus.IN_PROGRESS` (FR-011). Must keep the DTO shape unchanged.

**Checkpoint**: M0–M3 implemented; the Phase 2 tests for the corresponding behavior should now pass.

---

## Phase 4: Integration

**Purpose**: Wire the milestones together, remove the defective whole-batch retry path, reconcile contracts/docs, and adjust existing E2E mocks for the new per-target behavior.

- [ ] T025 [US1] In `frontend/src/app/pages/snacks/snacks.component.ts`, remove the whole-batch `resetFailed` re-creation path and route **both** the automatic (T017) and manual (T022) retry flows through the same single-target helper so the counter/guard/owner-map are single-sourced (FR-003, FR-007, plan Risk). Depends on T017, T022.
- [ ] T026 [US1] In `frontend/src/app/pages/snacks/snacks.component.ts`, ensure a retry batch's item is merged back by `subtopicId` into `items` and `appState.snacks` without resetting `currentIndex` or regenerating healthy targets (FR-003, contract C6). Depends on T025.
- [ ] T027 [P] Update existing E2E expectations/mocks affected by the behavior change: `frontend/e2e/progressive-delivery.spec.ts` and `frontend/e2e/error-recovery.spec.ts` (the shared batch mock and retry-button expectations must reflect per-target retries and `PARTIAL`-as-terminal). Depends on T005.
- [ ] T028 [P] Verify/refine `specs/002-snack-retry-bound/contracts/batch-status.md` so it matches the implemented terminality (only `IN_PROGRESS` non-terminal), `retrying` semantics, and polling window (FR-016). The planner already drafted the update; confirm and correct any drift.
- [ ] T029 [P] Reconcile the stale feature-001 contract: add a superseded-by pointer in `specs/001-batch-mvp-enhancements/contracts/batch-status.md` (its lines describing terminal = `COMPLETE`/`FAILED` and `retrying` = whole-lifetime) referencing the `002` contract as canonical (FR-016).
- [ ] T030 [P] Verify `specs/002-snack-retry-bound/contracts/snack-retry-behavior.md` matches the implemented budget/precondition/guard/reset/window behavior; correct any divergence (FR-001..FR-011).
- [ ] T031 [P] Verify/update `specs/002-snack-retry-bound/quickstart.md` failure-injection recipes so the documented steps reproduce the 3-attempt bound and polling termination (SC-001/SC-004), and confirm `specs/002-snack-retry-bound/contracts/snack-generate.md` remains an accurate unchanged reference for `POST /api/snacks/generate` (FR-012).

**Checkpoint**: All milestones integrated; contracts and docs reflect the shipped behavior; existing specs updated.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final verification, security/no-regression gates, and checklist sign-off.

- [ ] T032 [P] Sensitive-data audit (FR-015/SC-008): inspect all new terminal-error messages and any logs to confirm they contain only typed `LlmErrorCause` strings — no credentials, API keys, raw prompts, provider payloads, or personal data.
- [ ] T033 Run `cd backend && mvn test` and confirm `BatchServiceTest`, `BatchFlowIntegrationTest`, and `RetryingLlmProviderTest` are green (SC-007). Depends on T004, T024.
- [ ] T034 Run `cd frontend && npx playwright test` and confirm the existing 17 specs plus the new `002` specs are green (SC-001..SC-007). Depends on T006–T009, T027.
- [ ] T035 Execute the `quickstart.md` manual validation for US1 (3 attempts then terminal), US2 (`PARTIAL` terminates), US3 (no blind-timer retries), US4 (manual retry resets budget), and FR-010 (short-window timeout); record results. Depends on T031.
- [ ] T036 Confirm no schema/migration/dependency change: verify `data-model.md`'s no-persistence statement holds (no entity/enum/migration delta) and that `backend/pom.xml`, `frontend/package.json`, and `frontend/package-lock.json` are unchanged (FR-012/FR-013/FR-014, SC-007). Depends on T024.
- [ ] T037 Final validation: mark every completed task `[x]` in this file and confirm **all** checklists under `specs/002-snack-retry-bound/checklists/` have **0 unchecked items**, then record the final task/phase counts in the Summary section.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Tests (Phase 2)**: Depends on Setup; written first and expected to fail.
- **Core (Phase 3)**: Depends on Tests being authored. M0, M1, M2, M3 are internally sequential where they touch the same file; M0 and M3 touch different stacks and can proceed in parallel.
- **Integration (Phase 4)**: Depends on the relevant Core tasks landing.
- **Polish (Phase 5)**: Depends on all Core + Integration tasks.

### Within each milestone

- **M0**: T010 → T011 → T012 → T013; T014 depends on T011.
- **M1**: T015 → {T016, T019, T021} → T017 → {T018, T020}.
- **M2**: T022 depends on T015 + T018; T023 depends on T020 + T022.
- **M3**: T024 depends on the T004 test and is independent of the frontend tasks.
- Tests (Phase 2) MUST be authored and failing before their implementation target.

### Parallel opportunities

- **Setup**: T002 and T003 are different files — parallel.
- **Tests**: T004 (backend Java) and T006–T009 (separate new E2E files) are parallel once T005 (shared helper) is done.
- **Core**: T010/T024 touch different stacks; T015 (new file) can start while M0 proceeds.
- **Integration docs**: T027–T031 are separate files — parallel.
- **Polish**: T032 (audit) is parallel with T033/T034 runs.

> **No `[P]` on tasks touching the same file**: T016/T017/T018/T019/T020/T021/T022/T025/T026 all edit `snacks.component.ts` and are therefore sequential.

---

## Parallel Example: Tests phase

```bash
# After T005 (shared helper), author the four new specs in parallel:
Task: "T006 polling-termination.spec.ts"
Task: "T007 retry-bound.spec.ts"
Task: "T008 retry-explicit-error.spec.ts"
Task: "T009 manual-retry.spec.ts"

# Backend JUnit test is a different stack and can run alongside:
Task: "T004 extend BatchServiceTest"
```

---

## Implementation Strategy

### MVP First

1. Complete Setup (T001–T003).
2. Author failing tests (T004–T009).
3. **M0** (T010–T014): terminality + bounded window — independently removes the infinite spinner for `PARTIAL`/stuck batches (US2).
4. **M1** (T015–T021): `SnackRetryService` + explicit-error per-target reattempts + in-flight guard (US1/US3).
5. **M2** (T022–T023): manual retry resets the budget (US4).
6. **M3** (T024): backend `retrying` correctness (FR-011).
7. **STOP and VALIDATE**: run backend + Playwright suites and the quickstart recipes.

### Incremental Delivery

M0 alone fixes the reported infinite spinner and is shippable. M1 adds the bounded retry budget, M2 restores recoverability, and M3 corrects the `retrying` indicator. Each milestone is independently testable via its Phase 2 spec.

---

## Requirement Coverage

| Spec ID | Covered by |
|---------|-----------|
| FR-001 | T015, T016 |
| FR-002 | T015, T016, T017 |
| FR-003 | T017, T019 |
| FR-004 | T017, T019 |
| FR-005 | T020 |
| FR-006 | T015, T022, T023 |
| FR-007 | T015, T017, T018, T020, T022 |
| FR-008 | T010 |
| FR-009 | T010 |
| FR-010 | T011, T012, T013, T014 |
| FR-011 | T021 (frontend), T024 (backend) |
| FR-012 | T031, T036 |
| FR-013 | T036 |
| FR-014 | T015, T017, T036 |
| FR-015 | T032 |
| FR-016 | T028, T029 |
| SC-001 | T007, T034 |
| SC-002 | T007, T020, T034 |
| SC-003 | T006, T010, T034 |
| SC-004 | T006, T011, T012, T014, T035 |
| SC-005 | T008, T017, T034 |
| SC-006 | T009, T022, T023, T034 |
| SC-007 | T001, T027, T033, T034, T036 |
| SC-008 | T032 |

**No FR or SC is left uncovered.** All 16 FRs and all 8 SCs map to at least one task.

---

## Summary

- **Total tasks: 37**
- **Phase breakdown**:
  - Setup: **3** (T001–T003)
  - Tests: **6** (T004–T009)
  - Core: **15** (T010–T024; M0=5, M1=7, M2=2, M3=1)
  - Integration: **7** (T025–T031)
  - Polish: **6** (T032–T037)
- **Story task counts** (labeled tasks):
  - US1 (bounded reattempts): **9** (T004, T007, T015, T016, T017, T018, T020, T021, T024)
  - US2 (polling termination): **6** (T006, T010, T011, T012, T013, T014)
  - US3 (explicit-error precondition): **2** (T008, T019)
  - US4 (manual retry): **3** (T009, T022, T023)
  - Unlabeled (setup/integration/polish): **17**

## Notes

- `[P]` tasks touch different files and have no dependency on incomplete tasks; tasks editing `snacks.component.ts` are never `[P]`.
- Every task carries exact file paths (create or modify) and a clear done-definition.
- The frontend has no unit-test runner; frontend verification is Playwright plus the pure `SnackRetryService` being runner-ready.
- `POST /api/snacks/generate`, `RetryingLlmProvider`, the LLM prompts, the `BatchStatus` vocabulary, and the DB schema are intentionally unchanged (FR-012/FR-013, data-model.md).
- Checklists live in `specs/002-snack-retry-bound/checklists/` and must end with 0 unchecked items at implementation completion (T037).
