# Test Requirements Quality Checklist: Guarantee Bounded Snack Generation

**Purpose**: Validate that the feature's verification requirements are complete, unambiguous, and executable — covering backend JUnit unit tests, backend integration tests (Testcontainers), frontend Playwright non-regression, and manual verification — before/while implementing `009-bounded-snack-generation`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md) | [quickstart.md](../quickstart.md)

**Note**: This checklist tests the *test/verification requirements* for quality, not the implementation. Each item must be answerable "yes" at implementation completion.

## Coverage Completeness

- [ ] CHK001 Is there a test requirement that a hanging provider call fails at/before the configured connect+read bound? [Coverage, Spec §FR-003/§SC-001, tasks.md T006]
- [ ] CHK002 Is there a test requirement that the configured `llm.timeout-seconds` property actually drives the bound? [Coverage, Spec §FR-002/§SC-010, tasks.md T006]
- [ ] CHK003 Is there a test requirement that a retryable failure yields exactly `maxAttempts` calls and a non-retryable cause yields one? [Coverage, Spec §FR-004/§SC-002, tasks.md T005]
- [ ] CHK004 Is there a test requirement that two invalid responses produce exactly two provider calls and then a typed throw (single re-roll)? [Coverage, Spec §FR-005/§FR-006, tasks.md T004]
- [ ] CHK005 Is there a test requirement asserting a finite, bounded total LLM call count for a failing/hanging provider? [Coverage, Spec §FR-006/§SC-002, tasks.md T009]
- [ ] CHK006 Is there a test requirement that a subtopic exceeding its bound becomes `FAILED`/`TIMEOUT` while a healthy sibling still becomes `READY`? [Coverage, Spec §FR-009/§FR-010/§SC-003, tasks.md T007]
- [ ] CHK007 Is there a test requirement that the quiz-join timeout finalizes stuck items and yields a terminal session status? [Coverage, Spec §FR-012/§FR-014/§SC-004, tasks.md T008]
- [ ] CHK008 Is there a test requirement that an empty batch aggregates to a terminal status? [Coverage, Spec §Edge Cases/§SC-004, tasks.md T008]
- [ ] CHK009 Is there a test requirement that a `<= 0` per-subtopic bound normalizes to the documented default? [Coverage, Spec §FR-011/§SC-010, tasks.md T008]
- [ ] CHK010 Is there an integration test requirement with a **never-returning** provider for the all-items-hang case reaching terminal status? [Coverage, Spec §FR-013/§SC-004, tasks.md T010]
- [ ] CHK011 Is there an integration test requirement for the one-hangs-others-healthy case? [Coverage, Spec §FR-010/§SC-003, tasks.md T011]
- [ ] CHK012 Is there an integration test requirement that `POST` returns `202` and `GET` returns promptly under a hanging provider? [Coverage, Spec §FR-015/§FR-016/§SC-005, tasks.md T012]
- [ ] CHK013 Is there an explicit no-regression test requirement for the existing backend and Playwright suites, including the bounded-retry and bounded-polling tests? [Coverage, Spec §FR-018/§FR-022/§SC-009, tasks.md T026, T027, T030]

## Test Clarity & Feasibility

- [ ] CHK014 Is the `@Tag("integration")` / Testcontainers execution path and its exclusion from default `mvn test` documented? [Clarity, plan.md §Testing, tasks.md preamble]
- [ ] CHK015 Is the requirement to drive short `batch.snack-timeout-seconds` / `batch.quiz-join-timeout-seconds` via `@DynamicPropertySource` stated so the suite is fast? [Clarity, research.md §R5, tasks.md preamble]
- [ ] CHK016 Is the never-returning test double required to be an **interruptible** latch so JVM shutdown is prompt? [Clarity, research.md §R3, tasks.md preamble]
- [ ] CHK017 Is the absence of a frontend unit-test runner documented, with Playwright named as the verification path? [Clarity, plan.md §Testing, tasks.md preamble]
- [ ] CHK018 Is the new `BatchService` constructor dependency (`snackExecutor` + `snackTimeoutSeconds`) identified as a test-update requirement? [Completeness, plan.md §Risks, tasks.md T007]
- [ ] CHK019 Is the `@MockitoBean LlmProvider` context-reload isolation requirement stated (keep the hanging stub in its own test class)? [Clarity, plan.md §Risks, research.md §R3]
- [ ] CHK020 Is each new test mapped to the FR/SC it verifies? [Traceability, tasks.md Requirement Coverage]
- [ ] CHK021 Are the test tasks ordered so tests are authored and failing before their implementation targets? [Consistency, tasks.md §Dependencies, TDD ordering]

## Test Consistency

- [ ] CHK022 Do the unit and integration tests agree that the per-subtopic bound is a wall-clock timeout surfaced as `TIMEOUT`? [Consistency, Spec §FR-008/§FR-009]
- [ ] CHK023 Do the unit and integration tests agree on the terminal `BatchStatus` vocabulary (`IN_PROGRESS`/`PARTIAL`/`COMPLETE`/`FAILED`)? [Consistency, Spec §FR-012]
- [ ] CHK024 Do the tests assert user-visible batch termination rather than internal LLM retry mechanics, respecting the layering? [Consistency, Spec §FR-021, plan.md §Testing]
- [ ] CHK025 Does the per-call-bound test (HTTP layer) avoid duplicating the per-subtopic-bound test (provider layer)? [Consistency, research.md §R1/§R3]

## Test Gaps / Boundaries

- [ ] CHK026 Is there a verification requirement for the mixed success/failure (`PARTIAL`) outcome? [Gap, Spec §Edge Cases, tasks.md T011]
- [ ] CHK027 Is there a verification requirement for the all-fail (`FAILED`) outcome? [Gap, Spec §Edge Cases, tasks.md T010]
- [ ] CHK028 Is there a verification requirement that no item remains `PENDING`/`GENERATING` at terminal status? [Gap, Spec §FR-014, tasks.md T010]
- [ ] CHK029 Is there a verification requirement for the sensitive-data constraint on failure messages/logs? [Coverage, Spec §FR-020/§SC-007, tasks.md T028]
- [ ] CHK030 Is it stated that no DB migration test or new endpoint/DTO test is required, and why? [Clarity, plan.md §Testing Strategy, data-model.md]

## Manual / Integration Verification

- [ ] CHK031 Does `quickstart.md` provide a deterministic hanging-provider reproduction with a small bound? [Completeness, quickstart.md §Simulate a hanging provider]
- [ ] CHK032 Does `quickstart.md` provide the exact backend unit, backend integration, and Playwright commands? [Completeness, quickstart.md §Automated tests]
- [ ] CHK033 Does the manual verification cover US1–US7 (call bound, finite calls, per-item failure, terminal batch, prompt HTTP, bounded polling, safe logs)? [Coverage, tasks.md T031]
- [ ] CHK034 Is a troubleshooting table present mapping symptoms to the bound/finalization mechanism? [Completeness, quickstart.md §Troubleshooting]

## Notes

- Check items off as completed: `[x]`.
- Reference the spec/plan section or task ID that satisfies each item.
- All items must be checked at implementation completion (tasks.md T032).
