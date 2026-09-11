# API / Contract Requirements Quality Checklist: Guarantee Bounded Snack Generation

**Purpose**: Validate that the batch-status HTTP contract and the new bounded-behavior contract are complete, unambiguous, and consistent with the spec — before/while implementing `009-bounded-snack-generation`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [contracts/batch-status.md](../contracts/batch-status.md) | [contracts/snack-generation-bounds.md](../contracts/snack-generation-bounds.md)

**Note**: This checklist tests the *contract/requirements writing* for quality, not the implementation. Each item must be answerable "yes" at implementation completion.

## Response Shape & Compatibility

- [x] CHK001 Is the `POST /api/batches` request/response shape explicitly declared unchanged from `002`/`005`? [Consistency, Spec §FR-015, plan.md §Constraints]
- [x] CHK002 Is the `GET /api/batches/{batchId}` response shape explicitly declared unchanged from `002`/`005`? [Consistency, Spec §FR-016, contracts/batch-status.md header]
- [x] CHK003 Is the `BatchStatus` vocabulary enumerated (`IN_PROGRESS`/`PARTIAL`/`COMPLETE`/`FAILED`) with no new values? [Completeness, data-model.md §Batch Aggregate Status]
- [x] CHK004 Is the `GenerationStatus` vocabulary enumerated (`PENDING`/`GENERATING`/`READY`/`FAILED`) with no new values? [Completeness, data-model.md §Per-Item Status]
- [x] CHK005 Is the `LlmErrorCause` vocabulary enumerated with `TIMEOUT` reused (no new enum value)? [Completeness, data-model.md §Typed Failure Cause]
- [x] CHK006 Are all response fields (`status`, `retrying`, `items[]`, `snackStatus`, `snackCause`, `quizStatus`, `quizCause`, `quizQuestions`) documented with allowed values? [Completeness, contracts/batch-status.md §GET]
- [x] CHK007 Is it documented that `009` changes no field, type, or value? [Clarity, contracts/batch-status.md header]

## Immediate / Non-Blocking Guarantees

- [x] CHK008 Is `POST /api/batches` required to return `202` immediately after persistence without running generation on the request thread? [Clarity, Spec §FR-015, contracts/batch-status.md §POST]
- [x] CHK009 Is the after-commit worker trigger documented as the reason the `202` cannot race generation? [Completeness, contracts/batch-status.md §POST guarantees]
- [x] CHK010 Is `GET /api/batches/{id}` documented as read-only and never blocking on background generation? [Clarity, Spec §FR-016, contracts/batch-status.md §GET guarantees]
- [x] CHK011 Is it documented that a hanging provider cannot delay the `POST` response? [Completeness, Spec §FR-017, contracts/batch-status.md §POST]
- [x] CHK012 Is the `404 Not Found` case for an unknown batch id documented? [Completeness, contracts/batch-status.md §404]

## Terminality Contract

- [x] CHK013 Is terminality defined negatively (`status !== 'IN_PROGRESS'`) rather than as an allow-list? [Clarity, Spec §FR-012, contracts/batch-status.md §Terminality]
- [x] CHK014 Is `PARTIAL` explicitly classified as terminal? [Completeness, Spec §FR-012, contracts/batch-status.md §Terminality]
- [x] CHK015 Is the client behavior per status documented (continue polling vs stop)? [Completeness, contracts/batch-status.md §Terminality]
- [x] CHK016 Is the `009` terminal guarantee (worker always writes a terminal status within a bounded time) documented? [Clarity, contracts/batch-status.md §Terminal guarantee]
- [x] CHK017 Is the no-pending-item rule at terminal status stated (`snackStatus`/`quizStatus` never `PENDING`/`GENERATING`)? [Completeness, Spec §FR-014, contracts/batch-status.md §No-pending rule]
- [x] CHK018 Is it documented that the per-subtopic bound marks an item `FAILED`/`TIMEOUT` and the worker proceeds? [Completeness, Spec §FR-009/§FR-010, contracts/batch-status.md §Terminal guarantee]

## `retrying` & Polling Window (Preserved)

- [x] CHK019 Is the `retrying` field's semantics documented precisely (true only while an item is `GENERATING`)? [Clarity, contracts/batch-status.md §retrying semantics]
- [x] CHK020 Is the configurable polling window documented with its default (120 s) and injection seam (`MAX_POLLING_WINDOW_MS`)? [Completeness, Spec §FR-018, contracts/batch-status.md §Polling window]
- [x] CHK021 Is the timeout error type (`BatchPollingTimeoutError`) and its terminal UI consequence documented? [Clarity, contracts/batch-status.md §Polling window]
- [x] CHK022 Is it documented that the in-flight poll request is cancelled on window elapse? [Completeness, contracts/batch-status.md §Polling window]
- [x] CHK023 Is it documented that `009` changes no frontend source and only makes the backend actually terminal? [Clarity, contracts/batch-status.md §Polling window]

## Bounded-Behavior Contract

- [x] CHK024 Is the per-call connect-plus-read bound documented with its property and default (`llm.timeout-seconds`, 30)? [Completeness, Spec §FR-001/§FR-002, contracts/snack-generation-bounds.md §B1]
- [x] CHK025 Is the retry bound documented with its property and default (`batch.retry.max-attempts`, 3)? [Completeness, Spec §FR-004, contracts/snack-generation-bounds.md §B2]
- [x] CHK026 Is the single re-roll bound documented (at most one, then typed failure)? [Completeness, Spec §FR-005, contracts/snack-generation-bounds.md §B3]
- [x] CHK027 Is the per-subtopic wall-clock bound documented with its property and default (`batch.snack-timeout-seconds`, 240)? [Completeness, Spec §FR-008/§FR-011, contracts/snack-generation-bounds.md §B4]
- [x] CHK028 Is the finite worst-case computation (`≈186 s ≤ 240 s`) documented as the reason the bound never fires on healthy retries? [Clarity, Spec §FR-007, contracts/snack-generation-bounds.md §B4]
- [x] CHK029 Is the non-caller-runs executor rejection policy documented with its rationale? [Clarity, contracts/snack-generation-bounds.md §B4]
- [x] CHK030 Is the batch terminal guarantee documented including the post-join finalization step? [Completeness, Spec §FR-012, contracts/snack-generation-bounds.md §B5]
- [x] CHK031 Is the empty-batch terminal aggregation documented? [Completeness, Spec §Edge Cases, contracts/snack-generation-bounds.md §B5]
- [x] CHK032 Is the no-sensitive-logging rule documented for timeout/failure paths? [Completeness, Spec §FR-020, contracts/snack-generation-bounds.md §B8]

## Traceability & Frozen Surfaces

- [x] CHK033 Does every bounded-behavior rule (B1–B9) trace to an FR? [Traceability, contracts/snack-generation-bounds.md]
- [x] CHK034 Are the frozen surfaces enumerated (`POST /api/snacks/generate`, DTOs, `RetryingLlmProvider`, LLM port/prompts, `SnackService`, `QuizService`, `QuizQualityGate`, frontend polling/retry, `batchExecutor`/`quizExecutor`)? [Completeness, contracts/snack-generation-bounds.md §Frozen surfaces]
- [x] CHK035 Is the `orTimeout`-does-not-interrupt limitation documented as a non-contract-violation? [Clarity, contracts/snack-generation-bounds.md §Known limitation, plan.md §Risks]

## Notes

- Check items off as completed: `[x]`.
- Reference the contract section or task ID that satisfies each item.
- All items must be checked at implementation completion (tasks.md T032).
