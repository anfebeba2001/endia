# Test Requirements Quality Checklist: Bounded Snack Reattempts and Polling Termination

**Purpose**: Validate that the feature's verification requirements are complete, unambiguous, and executable — covering backend JUnit, frontend Playwright, and manual verification — before/while implementing `002-snack-retry-bound`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md)

**Note**: This checklist tests the *test/verification requirements* for quality, not the implementation. Each item must be answerable "yes" at implementation completion.

## Test Coverage Completeness

- [ ] CHK001 Is there a test requirement for the exact 3-attempt bound and the absence of a 4th automatic attempt? [Coverage, Spec §SC-001, tasks.md T007]
- [ ] CHK002 Is there a test requirement for the terminal error state and stopped spinner after the 3rd failure? [Coverage, Spec §SC-002, tasks.md T007]
- [ ] CHK003 Is there a test requirement for polling termination on `PARTIAL`, `COMPLETE`, and `FAILED`? [Coverage, Spec §SC-003, tasks.md T006]
- [ ] CHK004 Is there a test requirement for the stuck-`IN_PROGRESS` bounded-window timeout? [Coverage, Spec §SC-004, tasks.md T006]
- [ ] CHK005 Is there a test requirement for zero blind-timer retries while a batch is `IN_PROGRESS`? [Coverage, Spec §SC-005, tasks.md T008]
- [ ] CHK006 Is there a test requirement for manual retry starting exactly one new cycle and duplicate-click suppression? [Coverage, Spec §SC-006, tasks.md T009]
- [ ] CHK007 Is there a test requirement for the backend `retrying` flag being `false` for `PENDING`-only and terminal batches and `true` when an item is `GENERATING`? [Coverage, Spec §FR-011, tasks.md T004]
- [ ] CHK008 Is there an explicit no-regression test requirement for the existing 17 Playwright specs and the unchanged `RetryingLlmProviderTest`? [Coverage, Spec §SC-007, tasks.md T033, T034]

## Test Clarity & Feasibility

- [ ] CHK009 Is the absence of a frontend unit-test runner documented, and is the verification path (Playwright + pure service) stated? [Clarity, plan.md §Testing, tasks.md preamble]
- [ ] CHK010 Is the `MAX_POLLING_WINDOW_MS` override mechanism specified so the timeout test does not wait 120 s? [Clarity, Spec §FR-010, plan.md §Risks]
- [ ] CHK011 Are the batch-mock capabilities required by the tests (call counting, per-target failure, `PARTIAL`/stuck responses) identified before the specs are authored? [Completeness, tasks.md T005]
- [ ] CHK012 Is each new E2E spec mapped to the FR/SC it verifies? [Traceability, tasks.md T006–T009]
- [ ] CHK013 Are the test tasks ordered so tests are authored and failing before their implementation targets? [Consistency, tasks.md §Dependencies, TDD ordering]
- [ ] CHK014 Is it specified that the polling observable must unsubscribe (no further HTTP requests), not merely stop rendering? [Clarity, Spec §SC-003, contracts/snack-retry-behavior.md §C1]

## Test Consistency

- [ ] CHK015 Do the E2E terminal-status tests and the backend `retrying` tests agree on the `BatchStatus` vocabulary (`IN_PROGRESS`/`PARTIAL`/`COMPLETE`/`FAILED`)? [Consistency, Spec §FR-008, §FR-011]
- [ ] CHK016 Do the retry-bound test and the manual-retry test agree that a manual retry starts a fresh 3-attempt budget? [Consistency, Spec §FR-006, §SC-006]
- [ ] CHK017 Are the in-flight-guard assertions consistent across automatic and manual retry tests? [Consistency, Spec §FR-007, tasks.md T008, T009]
- [ ] CHK018 Do the tests assert user-visible attempts (cycles), not internal LLM retries, so FR-014's layering is respected? [Consistency, Spec §FR-014, plan.md §Testing]

## Test Gaps / Boundaries

- [ ] CHK019 Is there a verification requirement for the non-retryable-cause path (terminal without repeated automatic attempts)? [Gap, Spec §Edge Cases]
- [ ] CHK020 Is there a verification requirement that a successful later attempt within budget clears the error and does not consume further attempts? [Gap, Spec §US1.4]
- [ ] CHK021 Is there a verification requirement that a stale original-batch poll cannot re-trigger a handed-off target (`ownerBatchId` guard)? [Gap, Spec §FR-007, research.md §R5]
- [ ] CHK022 Is there a verification requirement for the sensitive-data constraint on failure messages/logs? [Coverage, Spec §FR-015, §SC-008, tasks.md T032]

## Manual / Integration Verification

- [ ] CHK023 Does `quickstart.md` provide deterministic failure-injection recipes for each typed cause? [Completeness, quickstart.md §Force failures]
- [ ] CHK024 Does the manual verification cover all four user stories plus FR-010? [Coverage, tasks.md T035]
- [ ] CHK025 Is it stated that no new backend integration test or migration test is required, and why? [Clarity, plan.md §Testing Strategy]

## Notes

- Check items off as completed: `[x]`.
- Reference the spec/plan section or task ID that satisfies each item.
- All items must be checked at implementation completion (tasks.md T037).
