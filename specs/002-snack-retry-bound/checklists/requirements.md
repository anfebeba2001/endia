# Requirements Quality Checklist: Bounded Snack Reattempts and Polling Termination

**Purpose**: Validate that the feature's requirements are complete, clear, consistent, and measurable before/while implementing `002-snack-retry-bound`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md)

**Note**: This checklist tests the *requirements writing* (completeness, clarity, consistency, measurability), not the implementation. Every item must be answerable "yes" at implementation completion; mark `[x]` with the evidence (section or task ID) that satisfies it.

## Requirement Completeness

- [ ] CHK001 Are all four user stories (US1–US4) each paired with at least one independently testable acceptance scenario? [Completeness, Spec §User Scenarios]
- [ ] CHK002 Is a per-target attempt counter explicitly required, and is its scope (per snack-per-subtopic, per cycle) stated unambiguously? [Completeness, Spec §FR-001, §Assumptions]
- [ ] CHK003 Is the "3 attempts" definition stated as *total* (initial + at most 2 reattempts) everywhere it appears? [Clarity, Spec §FR-002, §Assumptions]
- [ ] CHK004 Are all three explicit failure signals enumerated (failed item, typed cause, failed batch-create)? [Completeness, Spec §FR-003]
- [ ] CHK005 Is the prohibition on timer-based and `IN_PROGRESS`-based retries stated as a MUST NOT? [Completeness, Spec §FR-004]
- [ ] CHK006 Is the manual-retry reset rule (fresh 3-attempt budget, exactly one new cycle) fully specified? [Completeness, Spec §FR-006, §Clarifications]
- [ ] CHK007 Is the duplicate/in-flight guard defined for both automatic and manual retry paths? [Completeness, Spec §FR-007]
- [ ] CHK008 Are the terminal batch statuses enumerated, and is the negative definition (every status other than `IN_PROGRESS`) explicit? [Completeness, Spec §FR-008, §US2]
- [ ] CHK009 Is the polling-window default (120 s) and its configurability specified? [Completeness, Spec §FR-010, §Clarifications]
- [ ] CHK010 Is the `retrying` indicator's intended semantics stated as active work only, and is the current whole-lifetime behavior called out as a defect? [Completeness, Spec §FR-011, §Assumptions]
- [ ] CHK011 Are the preserved-behavior requirements (`POST /api/snacks/generate`, `RetryingLlmProvider` semantics) explicit? [Completeness, Spec §FR-012, §FR-013]
- [ ] CHK012 Is the contract-documentation update (terminality + `retrying`) captured as a requirement? [Completeness, Spec §FR-016]

## Requirement Clarity

- [ ] CHK013 Is "explicit error signal" defined with concrete observable conditions rather than vague wording? [Clarity, Spec §FR-003, §US3]
- [ ] CHK014 Is "terminal error state" defined with observable properties (progress stops, message names cause, no background activity)? [Clarity, Spec §FR-005, §US1]
- [ ] CHK015 Is the interaction between application-level cycles and LLM-level internal retries quantified without ambiguity (max 3 cycles × up to 3 internal tries, only 3 user-visible)? [Clarity, Spec §FR-014, §Assumptions]
- [ ] CHK016 Is "sensitive data" itemized (credentials, API keys, raw prompts, provider payloads, personal data)? [Clarity, Spec §FR-015]
- [ ] CHK017 Is the per-target versus per-batch scoping of the counter unambiguous for mixed success/failure batches? [Clarity, Spec §Assumptions, §Edge Cases]

## Requirement Consistency

- [ ] CHK018 Do US1 (attempt bound), FR-002 (cap at 3), and FR-006 (manual reset) agree on the counting semantics? [Consistency, Spec §US1, §FR-002, §FR-006]
- [ ] CHK019 Do US2, FR-008, FR-009, and the `batch-status.md` terminality table agree that only `IN_PROGRESS` is non-terminal? [Consistency, Spec §US2, §FR-008, contracts/batch-status.md]
- [ ] CHK020 Does FR-011 (`retrying` = active activity) align with the frontend OR of `hasInFlight()` and the backend `GENERATING` derivation? [Consistency, Spec §FR-011, contracts/snack-retry-behavior.md §C10]
- [ ] CHK021 Do FR-003/FR-004 (explicit error only) and FR-007 (in-flight guard) avoid contradicting the manual-retry flow in FR-006? [Consistency, Spec §FR-003, §FR-006, §FR-007]

## Acceptance Criteria Quality

- [ ] CHK022 Can SC-001 (≤3 automatic attempts, no 4th) be objectively counted from `POST /api/batches` calls? [Measurability, Spec §SC-001]
- [ ] CHK023 Is SC-002's "within a bounded time / no indefinite spinner" tied to a concrete observable (progress stopped)? [Measurability, Spec §SC-002]
- [ ] CHK024 Does SC-003 specify the unsubscribe deadline ("within one poll interval of the terminal status")? [Measurability, Spec §SC-003]
- [ ] CHK025 Does SC-004 name the configured default window (120 s) and the required terminal outcome? [Measurability, Spec §SC-004]
- [ ] CHK026 Can SC-006's "exactly one new generation cycle" be verified by counting create calls? [Measurability, Spec §SC-006]

## Edge Case & Scenario Coverage

- [ ] CHK027 Are all-success (`COMPLETE`), all-fail (`FAILED`), and mixed (`PARTIAL`) batch outcomes covered with distinct behavior? [Coverage, Spec §Edge Cases]
- [ ] CHK028 Is the case "a single target fails while others still generate" addressed without retrying in-progress items? [Coverage, Spec §Edge Cases]
- [ ] CHK029 Is the stuck-`IN_PROGRESS` scenario covered by the bounded-window requirement? [Coverage, Spec §Edge Cases, §FR-010]
- [ ] CHK030 Are duplicate/rapid user actions addressed for both manual and automatic paths? [Coverage, Spec §Edge Cases, §US4.3]
- [ ] CHK031 Is the non-retryable-cause scenario specified (terminal without consuming repeated automatic attempts)? [Coverage, Spec §Edge Cases, §Assumptions]
- [ ] CHK032 Is the legacy single-shot endpoint explicitly out of scope and unchanged? [Coverage, Spec §Edge Cases, §Out of Scope]

## Assumptions & Dependencies

- [ ] CHK033 Is the `PARTIAL`-as-root-cause assumption documented and traceable to FR-008? [Assumption, Spec §Assumptions]
- [ ] CHK034 Is the decision that attempt counters are not persisted documented and consistent with the Out-of-Scope clause? [Assumption, Spec §Out of Scope, data-model.md]
- [ ] CHK035 Is the residual limitation of the backend `retrying` proxy (cannot distinguish first-pass vs retry) recorded as a known assumption/risk? [Assumption, research.md §R3, plan.md §Risks]
- [ ] CHK036 Is the stale feature-001 contract note explicitly flagged for reconciliation? [Traceability, Spec §FR-016, tasks.md T029]

## Notes

- Check items off as completed: `[x]`.
- Add the satisfying section/task reference inline.
- Items are numbered sequentially for easy reference; all must be checked at implementation completion (tasks.md T037).
