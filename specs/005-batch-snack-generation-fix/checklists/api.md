# API / Contract Requirements Quality Checklist: Batch Snack Generation Fix

**Purpose**: Validate that the batch-status contract and the new backend behavioral contract are complete, unambiguous, and consistent with the spec — before/while implementing `005-batch-snack-generation-fix`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [contracts/batch-status.md](../contracts/batch-status.md) | [contracts/batch-generation-behavior.md](../contracts/batch-generation-behavior.md)

**Note**: This checklist tests the *contract/requirements writing* for quality, not the implementation. Each item must be answerable "yes" at implementation completion.

## Response Shape & Compatibility

- [x] CHK001 Is the `GET /api/batches/{batchId}` response shape explicitly declared unchanged from `002`? [Completeness, contracts/batch-status.md header]
- [x] CHK002 Is the `POST /api/batches` request/response shape explicitly declared unchanged? [Consistency, Spec §FR-016, plan.md §Constraints]
- [x] CHK003 Is the `POST /api/snacks/generate` contract explicitly declared unchanged (single-shot preserved)? [Consistency, Spec §FR-017, contracts/batch-generation-behavior.md §C2]
- [x] CHK004 Is the `BatchStatus` vocabulary enumerated with no new values? [Completeness, data-model.md §Batch Status]
- [x] CHK005 Are all response fields (`status`, `retrying`, `items[]`, `snack`, `snackCause`, `quizStatus`, `quizCause`) documented with their allowed values? [Completeness, contracts/batch-status.md §Response]
- [x] CHK006 Is it documented that `005` changes no field, type, or value? [Clarity, contracts/batch-status.md header]

## Terminality Contract

- [x] CHK007 Is terminality defined negatively (`status !== 'IN_PROGRESS'`) rather than as a `COMPLETE`/`FAILED` allow-list? [Clarity, Spec §FR-012, contracts/batch-status.md §Terminality]
- [x] CHK008 Is `PARTIAL` explicitly classified as terminal? [Completeness, Spec §FR-012, contracts/batch-status.md §Terminality]
- [x] CHK009 Is the client behavior per status documented (continue polling vs stop)? [Completeness, contracts/batch-status.md §Terminality]
- [x] CHK010 Is the "unsubscribe within one poll interval / no further requests" rule stated? [Measurability, Spec §FR-013, contracts/batch-status.md §Terminality]
- [x] CHK011 Is it documented that `005` guarantees the worker writes a terminal status (previously unreachable)? [Clarity, contracts/batch-status.md header, contracts/batch-generation-behavior.md §C4]

## `retrying` & Polling Window

- [x] CHK012 Is the `retrying` field's semantics documented precisely (true only while an item is `GENERATING`)? [Clarity, Spec §FR-014, contracts/batch-status.md §`retrying` semantics]
- [x] CHK013 Is the configurable polling window documented with its default (120 s) and configuration seam (`MAX_POLLING_WINDOW_MS`)? [Completeness, Spec §FR-015, contracts/batch-status.md §Polling window]
- [x] CHK014 Is the timeout error type (`BatchPollingTimeoutError`) and its terminal UI consequence documented? [Clarity, contracts/batch-status.md §Polling window]
- [x] CHK015 Is it documented that the in-flight poll request is cancelled on timeout? [Completeness, contracts/batch-status.md §Polling window]
- [x] CHK016 Is it documented that `005` adds a frontend-only `polling` flag that does not affect the response? [Clarity, contracts/batch-status.md §Implementation Notes]

## Backend Behavioral Contract

- [x] CHK017 Is the persistence-scope rule documented (subtopic + theme resolved inside a valid persistence context; only the id crosses the boundary)? [Completeness, contracts/batch-generation-behavior.md §C1, Spec §FR-001/§FR-002]
- [x] CHK018 Is the prohibition on logging a detached/lazy access failure from the batch snack path documented? [Completeness, contracts/batch-generation-behavior.md §C1, Spec §FR-003]
- [x] CHK019 Is per-item failure isolation documented? [Completeness, contracts/batch-generation-behavior.md §C1, Spec §FR-008]
- [x] CHK020 Is executor isolation documented (nested work must not run only on the batch worker's pool)? [Clarity, contracts/batch-generation-behavior.md §C3, Spec §FR-004]
- [x] CHK021 Is `quizExecutor`'s `CallerRunsPolicy` documented as the liveness valve, and is `batchExecutor`'s lack of caller-runs documented with its rationale? [Clarity, contracts/batch-generation-behavior.md §C3]
- [x] CHK022 Is the bounded quiz join documented with its configurable default (240 s) and timeout behavior? [Completeness, contracts/batch-generation-behavior.md §C4, Spec §FR-006]
- [x] CHK023 Is the guaranteed-terminal rule (`COMPLETE`/`PARTIAL`/`FAILED`, never `IN_PROGRESS` with pending items) documented? [Completeness, contracts/batch-generation-behavior.md §C4, Spec §FR-005]
- [x] CHK024 Is the persisted `READY` snack + `GET /api/batches/{id}` observability documented, including the mixed-outcome aggregation rule? [Completeness, contracts/batch-generation-behavior.md §C5, Spec §FR-007]
- [x] CHK025 Are the preserved surfaces (`RetryingLlmProvider`, LLM port/prompts, `QuizService`, `QuizQualityGate`, `SnackRetryService`, DTOs) enumerated? [Completeness, contracts/batch-generation-behavior.md §C6, Spec §FR-016–§FR-018]
- [x] CHK026 Is the no-sensitive-logging rule documented? [Completeness, contracts/batch-generation-behavior.md §C6, Spec §FR-019]
- [x] CHK027 Is the layered-architecture rule documented (scope in application, executors in infrastructure, presentation in interface)? [Completeness, contracts/batch-generation-behavior.md §C6, Spec §FR-020]

## Traceability & Concurrency

- [x] CHK028 Does every backend behavioral rule trace to an FR (C1→FR-001/002/003, C2→FR-017, C3→FR-004/005, C4→FR-005/006, C5→FR-007/008, C6→FR-016/018/019/020)? [Traceability, contracts/batch-generation-behavior.md]
- [x] CHK029 Is the concurrency condition for the deadlock documented (≥2 concurrent batches saturate the core pool)? [Clarity, contracts/batch-generation-behavior.md §Concurrency note, plan.md §Risks]
- [x] CHK030 Is the `005` contract noted as restating (not superseding) the `002` terminality/`retrying`/window semantics? [Traceability, contracts/batch-status.md header]

## Notes

- Check items off as completed: `[x]`.
- Reference the contract section or task ID that satisfies each item.
- All items must be checked at implementation completion (tasks.md T024).
