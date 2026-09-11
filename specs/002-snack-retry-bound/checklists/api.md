# API / Contract Requirements Quality Checklist: Bounded Snack Reattempts and Polling Termination

**Purpose**: Validate that the batch-status contract and the frontend behavioral contract are complete, unambiguous, and consistent with the spec — before/while implementing `002-snack-retry-bound`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [contracts/batch-status.md](../contracts/batch-status.md) | [contracts/snack-retry-behavior.md](../contracts/snack-retry-behavior.md)

**Note**: This checklist tests the *contract/requirements writing* for quality, not the implementation. Each item must be answerable "yes" at implementation completion.

## Response Shape & Compatibility

- [x] CHK001 Is the `GET /api/batches/{batchId}` response shape explicitly declared unchanged? [Completeness, contracts/batch-status.md]
- [x] CHK002 Is the `POST /api/snacks/generate` contract explicitly declared unchanged? [Consistency, Spec §FR-012, contracts/snack-generate.md]
- [x] CHK003 Is the `BatchStatus` vocabulary enumerated with no new values? [Completeness, data-model.md §Batch Status]
- [x] CHK004 Are all response fields (`status`, `retrying`, `items[]`) documented with their allowed values? [Completeness, contracts/batch-status.md §Response]
- [x] CHK005 Is the `retrying` field's new semantics documented precisely (true only while an item is `GENERATING`)? [Clarity, Spec §FR-011, contracts/batch-status.md §`retrying` semantics]

## Terminality Contract

- [x] CHK006 Is terminality defined negatively (`status !== 'IN_PROGRESS'`) rather than as a `COMPLETE`/`FAILED` allow-list? [Clarity, Spec §FR-008, contracts/batch-status.md §Terminality]
- [x] CHK007 Is `PARTIAL` explicitly classified as terminal? [Completeness, Spec §US2, contracts/batch-status.md §Terminality]
- [x] CHK008 Is the client behavior per status documented (continue polling vs stop)? [Completeness, contracts/batch-status.md §Terminality]
- [x] CHK009 Is the "unsubscribe within one poll interval / no further requests" rule stated? [Measurability, Spec §SC-003, contracts/snack-retry-behavior.md §C1]

## Polling Window Contract

- [x] CHK010 Is the configurable window documented with its default (120 s) and configuration seam (`MAX_POLLING_WINDOW_MS`)? [Completeness, Spec §FR-010, contracts/batch-status.md §Polling window]
- [x] CHK011 Is the timeout error type (`BatchPollingTimeoutError`) and its terminal UI consequence documented? [Clarity, contracts/snack-retry-behavior.md §C2]
- [x] CHK012 Is it documented that the in-flight request is cancelled on timeout? [Completeness, contracts/batch-status.md §Polling window]

## Behavioral Contract (Retry)

- [x] CHK013 Is the per-target attempt budget (max 3 cycles) documented with the `canRetry` condition? [Completeness, contracts/snack-retry-behavior.md §C3]
- [x] CHK014 Are the only permitted automatic-retry triggers enumerated? [Completeness, Spec §FR-003, contracts/snack-retry-behavior.md §C4]
- [x] CHK015 Is the in-flight / `ownerBatchId` duplicate guard documented? [Completeness, Spec §FR-007, contracts/snack-retry-behavior.md §C5]
- [x] CHK016 Is single-target re-initiation (`createBatch(themeId, [T])`) and by-`subtopicId` merge documented? [Clarity, contracts/snack-retry-behavior.md §C6]
- [x] CHK017 Is the manual-retry reset sequence documented (reset counter, clear error, one new cycle)? [Completeness, Spec §FR-006, contracts/snack-retry-behavior.md §C8]
- [x] CHK018 Is the `retrying` OR rule (`status.retrying || hasInFlight()`) documented? [Clarity, contracts/snack-retry-behavior.md §C10]

## Traceability & Reconciliation

- [x] CHK019 Is the `002` contract marked as superseding the `001` terminality/`retrying` descriptions? [Traceability, Spec §FR-016, contracts/batch-status.md header]
- [x] CHK020 Is the stale `001` contract note flagged/reconciled so the documented contract matches behavior? [Consistency, Spec §FR-016, tasks.md T029]
- [x] CHK021 Does every contract rule trace to an FR (C1→FR-008/009, C2→FR-010, C3→FR-001/002/006, C4→FR-003/004, C5→FR-007, C6→FR-003, C7→FR-005, C8→FR-006)? [Traceability, contracts/snack-retry-behavior.md]

## Notes

- Check items off as completed: `[x]`.
- Reference the contract section or task ID that satisfies each item.
- All items must be checked at implementation completion (tasks.md T037).

**Completion verification (T037, 2026-09-11)**: `contracts/batch-status.md` (002) matches the implemented negative terminality, `retrying` derivation, and `BatchPollingTimeoutError` window (T028); `contracts/snack-retry-behavior.md` matches the budget/precondition/guard/reset behavior (T030); the stale `001` contract now carries a superseded-by pointer (T029); `snack-generate.md` remains an unchanged reference (FR-012).
