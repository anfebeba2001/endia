# Test Requirements Quality Checklist: Batch Snack Generation Fix

**Purpose**: Validate that the feature's verification requirements are complete, unambiguous, and executable — covering backend JUnit unit tests, backend integration tests (Testcontainers), frontend Playwright, and manual verification — before/while implementing `005-batch-snack-generation-fix`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md)

**Note**: This checklist tests the *test/verification requirements* for quality, not the implementation. Each item must be answerable "yes" at implementation completion.

## Test Coverage Completeness

- [x] CHK001 Is there a test requirement for the by-id transactional generation loading subtopic + theme and persisting the snack? [Coverage, Spec §FR-001/§FR-007, tasks.md T004]
- [x] CHK002 Is there a test requirement asserting `runBatch` calls `generateSingleSnack(UUID)` and never the `Subtopic` overload? [Coverage, Spec §FR-002, tasks.md T005]
- [x] CHK003 Is there a test requirement for no `LazyInitializationException` / null `snackCause` on the batch snack path? [Coverage, Spec §SC-001, tasks.md T006]
- [x] CHK004 Is there a test requirement for the batch reaching a terminal aggregate status within a bounded time under concurrency? [Coverage, Spec §SC-002, tasks.md T006]
- [x] CHK005 Is there a test requirement that successful generation yields a persisted `READY` snack returned by the batch status? [Coverage, Spec §SC-003, tasks.md T006]
- [x] CHK006 Is there a test requirement for the empty state never appearing before the first poll / while `IN_PROGRESS`? [Coverage, Spec §SC-004, tasks.md T007]
- [x] CHK007 Is there a test requirement for the empty state appearing only for a terminal, zero-ready batch? [Coverage, Spec §FR-011, tasks.md T007]
- [x] CHK008 Is there a test requirement for polling stopping on `PARTIAL`/`COMPLETE`/`FAILED` and the retry indicator clearing? [Coverage, Spec §SC-005, tasks.md T007, T022]
- [x] CHK009 Is there an explicit no-regression test requirement for the existing backend and Playwright suites, including the bounded-retry tests? [Coverage, Spec §SC-006/§SC-007, tasks.md T021, T022]

## Test Clarity & Feasibility

- [x] CHK010 Is the absence of a frontend unit-test runner documented, and is the verification path (Playwright) stated? [Clarity, plan.md §Testing, tasks.md preamble]
- [x] CHK011 Is the concurrency requirement for the deadlock regression test stated (≥2 concurrent batches or core-pool saturation)? [Clarity, plan.md §Risks, tasks.md preamble]
- [x] CHK012 Is the Testcontainers/`@Tag("integration")` execution path and its exclusion from default `mvn test` documented? [Clarity, plan.md §Testing, tasks.md preamble]
- [x] CHK013 Is the `BATCH_QUIZ_JOIN_TIMEOUT_SECONDS` (default 240) configuration seam identified so the bounded-join test does not wait indefinitely? [Clarity, Spec §FR-006, plan.md §Architecture Decisions]
- [x] CHK014 Is the delayed-first-poll E2E seam identified before the spec is authored? [Completeness, tasks.md T003]
- [x] CHK015 Is each new test mapped to the FR/SC it verifies? [Traceability, tasks.md Requirement Coverage]
- [x] CHK016 Are the test tasks ordered so tests are authored and failing before their implementation targets? [Consistency, tasks.md §Dependencies, TDD ordering]

## Test Consistency

- [x] CHK017 Do the integration test and the E2E spec agree on the `BatchStatus` vocabulary (`IN_PROGRESS`/`PARTIAL`/`COMPLETE`/`FAILED`)? [Consistency, Spec §FR-005/§FR-012]
- [x] CHK018 Do the unit and integration tests agree that the batch path uses the by-id overload while the single-shot path keeps the entity overload? [Consistency, Spec §FR-002/§FR-017]
- [x] CHK019 Does the E2E spec's terminal check match the negative terminality rule (not an allow-list of `COMPLETE`/`FAILED`)? [Consistency, Spec §FR-012]
- [x] CHK020 Do the tests assert user-visible batch termination, not internal LLM retries, so the layering is respected? [Consistency, Spec §FR-016, plan.md §Testing]

## Test Gaps / Boundaries

- [x] CHK021 Is there a verification requirement for the mixed success/failure (`PARTIAL`) outcome? [Gap, Spec §Edge Cases]
- [x] CHK022 Is there a verification requirement for the terminal zero-ready (`FAILED`) outcome? [Gap, Spec §Edge Cases, §US3.4]
- [x] CHK023 Is there a verification requirement for the bounded quiz-join timeout path (hung child still terminates)? [Gap, Spec §FR-006, quickstart.md §Verify US2]
- [x] CHK024 Is there a verification requirement for the sensitive-data constraint on failure messages/logs? [Coverage, Spec §FR-019/§SC-008, tasks.md T020]

## Manual / Integration Verification

- [x] CHK025 Does `quickstart.md` provide a deterministic concurrent-batch deadlock check? [Completeness, quickstart.md §Verify US2]
- [x] CHK026 Does `quickstart.md` provide a deterministic delayed-first-poll empty-state check? [Completeness, quickstart.md §Verify US3]
- [x] CHK027 Does the manual verification cover US1, US2, US3, and FR-019? [Coverage, tasks.md T023]
- [x] CHK028 Is it stated that no DB migration test or new endpoint/DTO test is required, and why? [Clarity, plan.md §Testing Strategy, data-model.md]

## Notes

- Check items off as completed: `[x]`.
- Reference the spec/plan section or task ID that satisfies each item.
- All items must be checked at implementation completion (tasks.md T024).
