# Requirements Quality Checklist: Guarantee Bounded Snack Generation

**Purpose**: Validate that the feature's liveness requirements are complete, clear, consistent, and measurable before/while implementing `009-bounded-snack-generation`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md)

**Note**: This checklist tests the *requirements writing* (completeness, clarity, consistency, measurability), not the implementation. Every item must be answerable "yes" at implementation completion; mark `[x]` with the evidence (section or requirement ID) that satisfies it.

## Requirement Completeness

- [x] CHK001 Is every outbound LLM call in the snack path required to be time-bounded, with the bound named and defaulted? [Completeness, Spec §FR-001]
- [x] CHK002 Is the connect-plus-read scope of the per-call timeout specified (both connect and read), not just "a timeout"? [Completeness, Spec §FR-002]
- [x] CHK003 Is a test requirement for the hanging-call bound stated so the call must fail at/before the bound? [Completeness, Spec §FR-003]
- [x] CHK004 Is the automatic-retry bound tied to an explicit configured maximum with a documented default? [Completeness, Spec §FR-004]
- [x] CHK005 Is the content re-roll explicitly limited to a single bounded re-roll followed by a typed failure? [Completeness, Spec §FR-005]
- [x] CHK006 Is a test requirement for a finite, bounded LLM call count under failing/hanging providers stated? [Completeness, Spec §FR-006]
- [x] CHK007 Is the combined worst-case wall-clock cost of one subtopic's generation required to be finite and within the per-subtopic bound? [Completeness, Spec §FR-007]
- [x] CHK008 Is the per-subtopic bound specified as a configurable maximum (wall-clock and/or attempts)? [Completeness, Spec §FR-008]
- [x] CHK009 Is the exceeded-bound outcome required to mark the item `FAILED` with a typed cause? [Completeness, Spec §FR-009]
- [x] CHK010 Is non-blocking of the batch worker on the per-subtopic bound required? [Completeness, Spec §FR-010]
- [x] CHK011 Is the per-subtopic bound required to be configurable with a documented default? [Completeness, Spec §FR-011]
- [x] CHK012 Is the terminal aggregate-status guarantee stated for the batch worker under hang conditions? [Completeness, Spec §FR-012]
- [x] CHK013 Is a never-returning-provider test requirement for terminal aggregation stated? [Completeness, Spec §FR-013]
- [x] CHK014 Is the no-pending/generating-item condition at terminal status stated? [Completeness, Spec §FR-014]
- [x] CHK015 Is the immediate `202` requirement for `POST /api/batches` stated? [Completeness, Spec §FR-015]
- [x] CHK016 Is the non-blocking `GET /api/batches/{id}` requirement stated for slow/hung generation? [Completeness, Spec §FR-016]
- [x] CHK017 Is the general no-unanswered-synchronous-request requirement stated? [Completeness, Spec §FR-017]
- [x] CHK018 Is the preserved bounded frontend polling (default 120 s, terminal error) captured as non-regression? [Completeness, Spec §FR-018]
- [x] CHK019 Is the bounded-outcome logging requirement stated with the real cause? [Completeness, Spec §FR-019]
- [x] CHK020 Is the sensitive-data prohibition for logs and user-facing errors explicit? [Completeness, Spec §FR-020]
- [x] CHK021 Is layered-architecture adherence captured without prescribing implementation details? [Completeness, Spec §FR-021]
- [x] CHK022 Is preservation of 002/005 behavior captured as a non-regression requirement? [Completeness, Spec §FR-022]
- [x] CHK023 Is configurability-with-documented-defaults stated for all new bounds? [Completeness, Spec §FR-023]

## Requirement Clarity

- [x] CHK024 Is "per-subtopic bound" distinguished from the per-call HTTP timeout and the retry bound (three separate bounds)? [Clarity, Spec §Context, §Key Entities]
- [x] CHK025 Is "bounded time" for a batch defined as a finite function of the per-subtopic bound and item count? [Clarity, Spec §Assumptions]
- [x] CHK026 Is the per-subtopic default (240 s) tied to the finite worst-case computation rather than arbitrary? [Clarity, Spec §Assumptions, data-model.md §Finite worst-case]
- [x] CHK027 Is "typed cause" enumerated to an existing vocabulary (`TIMEOUT`/`UNAVAILABLE`/etc.) rather than left open? [Clarity, Spec §Key Entities, data-model.md §Typed Failure Cause]
- [x] CHK028 Is "terminal aggregate status" enumerated (`COMPLETE`/`PARTIAL`/`FAILED`) with `IN_PROGRESS` as the only non-terminal state? [Clarity, Spec §Key Entities, contracts/batch-status.md §Terminality]
- [x] CHK029 Is the behavior for a `<= 0` / absurdly small configured bound specified (normalize to default)? [Clarity, Spec §Edge Cases, data-model.md §Validation]
- [x] CHK030 Is "no sensitive data" itemized (prompt bodies, API keys, provider payloads, personal data)? [Clarity, Spec §FR-020]

## Requirement Consistency

- [x] CHK031 Do FR-007 (worst-case ≤ per-subtopic bound) and the stated defaults (`llm.timeout-seconds` 30, `batch.retry.max-attempts` 3, 240 s bound) agree numerically? [Consistency, Spec §FR-007, §Assumptions]
- [x] CHK032 Do FR-004 (retry bound) and FR-005 (single re-roll) jointly imply a finite call count consistent with FR-006? [Consistency, Spec §FR-004–§FR-006]
- [x] CHK033 Do FR-012 (always terminal) and FR-014 (no pending items) agree with the finalization mechanism? [Consistency, Spec §FR-012, §FR-014, plan.md §Architecture Decisions 2]
- [x] CHK034 Do FR-015/FR-016 (immediate/non-blocking HTTP) agree with the `@Async`/after-commit trigger described in the contract? [Consistency, Spec §FR-015, §FR-016, contracts/batch-status.md]
- [x] CHK035 Do the preserved-behavior requirements (FR-018, FR-022) avoid contradicting the new bounds (FR-008)? [Consistency, Spec §FR-008, §FR-018, §FR-022]
- [x] CHK036 Is the `TIMEOUT` cause reused for both the per-subtopic bound and quiz-join finalization without adding a new enum value? [Consistency, Spec §FR-009, data-model.md §Typed Failure Cause]

## Acceptance Criteria Quality

- [x] CHK037 Is SC-001 expressed as a 0%-remain-in-flight condition verifiable by a hanging-provider test? [Measurability, Spec §SC-001]
- [x] CHK038 Is SC-002 expressed as a finite/bounded call count with 0 unbounded loops? [Measurability, Spec §SC-002]
- [x] CHK039 Is SC-003 expressed as 100% typed `FAILED` plus non-blocking worker? [Measurability, Spec §SC-003]
- [x] CHK040 Is SC-004 expressed as 100% terminal batches and 0% `IN_PROGRESS`-with-pending? [Measurability, Spec §SC-004]
- [x] CHK041 Is SC-005 expressed as 100% prompt `202`/`GET` including against a never-returning provider? [Measurability, Spec §SC-005]
- [x] CHK042 Is SC-006 tied to the existing 120 s polling window and one-interval termination? [Measurability, Spec §SC-006]
- [x] CHK043 Is SC-007 expressed as a log-content condition (real cause present, sensitive data absent)? [Measurability, Spec §SC-007]
- [x] CHK044 Is SC-008 stated as a zero-occurrence count over observed tests? [Measurability, Spec §SC-008]
- [x] CHK045 Is SC-009 tied to existing 002/005 tests passing unchanged? [Measurability, Spec §SC-009]
- [x] CHK046 Is SC-010 tied to configuration inspection plus a changed-bound-observed test? [Measurability, Spec §SC-010]

## Edge Case & Scenario Coverage

- [x] CHK047 Are read-hang and connect-hang provider cases covered with the same typed timeout outcome? [Coverage, Spec §Edge Cases]
- [x] CHK048 Are retry-exhaustion and always-invalid-content cases covered as distinct finite outcomes? [Coverage, Spec §Edge Cases]
- [x] CHK049 Is the one-subtopic-hangs-others-healthy case covered (only the hung item fails)? [Coverage, Spec §Edge Cases]
- [x] CHK050 Is the all-subtopics-hang case covered (batch still terminal)? [Coverage, Spec §Edge Cases]
- [x] CHK051 Is the timeout-fires-just-after-success case covered (successful response not discarded)? [Coverage, Spec §Edge Cases]
- [x] CHK052 Is the empty-batch (no subtopics) case covered as immediately terminal? [Coverage, Spec §Edge Cases]
- [x] CHK053 Is the persistence-fails-after-model-response case covered as item-local failure? [Coverage, Spec §Edge Cases]
- [x] CHK054 Is the worker-pool-saturation case covered (nested work cannot starve the worker)? [Coverage, Spec §Edge Cases, FR-022]
- [x] CHK055 Are client-disconnect and duplicate/rapid batch creation covered? [Coverage, Spec §Edge Cases]

## Assumptions & Dependencies

- [x] CHK056 Is the existing per-call timeout and retry mechanism recorded as a confirmed input (not re-designed)? [Assumption, Spec §Context, §Assumptions]
- [x] CHK057 Is the 240 s per-subtopic default recorded as an informed implementation decision, not a hard requirement? [Assumption, Spec §Clarifications]
- [x] CHK058 Is the no-schema-change / no-new-status scope documented and consistent with the Out-of-Scope clause? [Assumption, Spec §Out of Scope, data-model.md §Persistence Statement]
- [x] CHK059 Is the anonymous/stub user context and unchanged provider abstraction recorded? [Assumption, Spec §Assumptions]
- [x] CHK060 Is the sequential-snack assumption (batch time is per-item bound × count) documented? [Assumption, Spec §Assumptions]

## Notes

- Check items off as completed: `[x]`.
- Add the satisfying section/requirement reference inline.
- Items are numbered sequentially for easy reference; all must be checked at implementation completion (tasks.md T032).
