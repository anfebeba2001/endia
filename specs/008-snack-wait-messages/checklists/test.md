# Test Requirements Quality Checklist: Accurate Step-by-Step Snack Waiting Messages

**Purpose**: Validate that the feature's verification requirements are complete, unambiguous, and executable — covering backend JUnit unit tests, backend integration/contract tests (Testcontainers/MockMvc), frontend Playwright, and manual verification — before/while implementing `008-snack-wait-messages`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md)

**Note**: This checklist tests the *test/verification requirements* for quality, not the implementation. Each item must be answerable "yes" at implementation completion.

## Test Coverage Completeness

- [x] CHK001 Is there a test requirement for the exact ordered phase sequence emitted by `SnackService`? [Coverage, Spec §SC-001, tasks.md T007]
- [x] CHK002 Is there a test requirement for the re-roll phase sequence (`AWAITING_MODEL → AWAITING_MODEL → ASSEMBLING`)? [Coverage, Spec §US2.3, tasks.md T007]
- [x] CHK003 Is there a test requirement proving the `NOOP`/single-shot path is unchanged and emits no phase? [Coverage, Spec §Edge Cases, tasks.md T007]
- [x] CHK004 Is there a test requirement for `getStatus` exposing the phase while `GENERATING` and `null` when `READY`/`FAILED`? [Coverage, Spec §FR-003/§FR-004, tasks.md T008]
- [x] CHK005 Is there a test requirement that `runBatch` clears the registry on both the success and failure paths? [Coverage, Spec §FR-004, tasks.md T008]
- [x] CHK006 Is there a test requirement for retry re-entry from `RESOLVING_CONTEXT`? [Coverage, Spec §FR-009, tasks.md T008, T011]
- [x] CHK007 Is there a test requirement observing a non-null phase mid-generation and its clearing at terminal via a slow/blocking stub LLM? [Coverage, Spec §SC-001/§SC-004, tasks.md T009]
- [x] CHK008 Is there a contract/serialization test requirement for `snackPhase` (value while `GENERATING`, `null` otherwise) with no other shape change? [Coverage, Spec §FR-003, tasks.md T010]
- [x] CHK009 Is there an E2E requirement for each of the four phase messages rendering the exact mapped text? [Coverage, Spec §FR-005/§SC-002, tasks.md T011]
- [x] CHK010 Is there an E2E requirement for per-item isolation (two generating items show their own distinct messages)? [Coverage, Spec §FR-008, tasks.md T011]
- [x] CHK011 Is there an E2E requirement that settled (`READY`/`FAILED`) items show no phase message? [Coverage, Spec §SC-004, tasks.md T011]
- [x] CHK012 Is there an explicit no-regression test requirement for the existing backend and Playwright suites? [Coverage, Spec §SC-005, tasks.md T001, T024, T025]

## Test Clarity & Feasibility

- [x] CHK013 Is the absence of a frontend unit-test runner documented, and is the verification path (Playwright) stated? [Clarity, plan.md §Testing, tasks.md preamble]
- [x] CHK014 Is the Testcontainers/`@Tag("integration")` execution path and its exclusion from default `mvn test` documented? [Clarity, plan.md §Testing, tasks.md preamble]
- [x] CHK015 Is the E2E seam (`snackPhase` on the mock item + `batchItemGenerating` phase parameter) identified before the spec is authored? [Completeness, tasks.md T003]
- [x] CHK016 Is the phase-skipping caveat (short phases may not be observable in a real run) documented, with the E2E mock as the deterministic proof? [Clarity, plan.md §Risks, research.md §R5]
- [x] CHK017 Is each new test mapped to the FR/SC it verifies? [Traceability, tasks.md §Requirement Coverage]
- [x] CHK018 Are the test tasks ordered so tests are authored and failing before their implementation targets? [Consistency, tasks.md §Dependencies, TDD ordering]

## Test Consistency

- [x] CHK019 Do the unit, integration, and contract tests agree on the four phase enum names? [Consistency, data-model.md §1, contracts/batch-status.md]
- [x] CHK020 Does the E2E message assertion table match the `PHASE_MESSAGES` copy in the model? [Consistency, plan.md §Architecture Decisions 3]
- [x] CHK021 Do the tests assert the truthful-backend-phase rule rather than any timer/poll-count behavior? [Consistency, Spec §FR-002/§SC-003]
- [x] CHK022 Do the tests assert user-visible rendering separately from backend phase state, respecting the layering? [Consistency, Spec §FR-012, tasks.md T007–T009 vs T011]

## Test Gaps / Boundaries

- [x] CHK023 Is there a verification requirement for the skipped-phase / no-stale-phase boundary? [Gap, Spec §Edge Cases, tasks.md T011]
- [x] CHK024 Is there a verification requirement for the failure-during-a-phase path (item transitions to failed, phase cleared)? [Gap, Spec §Edge Cases, §US1.5, tasks.md T008/T011, quickstart.md §Manual failure-injection checks]
- [x] CHK025 Is there a verification requirement for the sensitive-data constraint on messages/logs? [Coverage, Spec §FR-011/§SC-006, tasks.md T023]
- [x] CHK026 Is it stated that no DB migration test or new-endpoint test is required, and why? [Clarity, plan.md §Testing Strategy, data-model.md §Persistence]

## Manual / Integration Verification

- [x] CHK027 Does `quickstart.md` provide a deterministic way to observe all four phases (real run + driven mock)? [Completeness, quickstart.md §Making short phases observable]
- [x] CHK028 Does `quickstart.md` provide an on-the-wire check of `snackPhase`? [Completeness, quickstart.md §Observe the phase on the wire]
- [x] CHK029 Does the manual verification cover the happy path, failure injection, and retry re-entry? [Coverage, quickstart.md §Manual failure-injection checks, tasks.md T026]

## Notes

- Check items off as completed: `[x]`.
- Reference the spec/plan section or task ID that satisfies each item.
- Reviewed: 2026-09-11 — all 29 items verified against the artifacts.
- All items must be checked at implementation completion (tasks.md T027).
