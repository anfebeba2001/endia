# API / Contract Requirements Quality Checklist: Accurate Step-by-Step Snack Waiting Messages

**Purpose**: Validate that the batch-status contract delta and the phase-transport requirements are complete, unambiguous, and consistent with the spec — before/while implementing `008-snack-wait-messages`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [contracts/batch-status.md](../contracts/batch-status.md) | [data-model.md](../data-model.md)

**Note**: This checklist tests the *contract/requirements writing* for quality, not the implementation. Each item must be answerable "yes" at implementation completion.

## Response Shape & Compatibility

- [x] CHK001 Is the `snackPhase` addition explicitly declared as the single additive nullable field on `BatchItem`? [Completeness, contracts/batch-status.md header]
- [x] CHK002 Is the field's position/type (`string | null`) and its allowed values (`RESOLVING_CONTEXT`/`AWAITING_MODEL`/`ASSEMBLING`/`PERSISTING`) documented? [Completeness, contracts/batch-status.md §Response]
- [x] CHK003 Is it documented that no existing field, type, or value changes (backward compatibility)? [Consistency, contracts/batch-status.md header]
- [x] CHK004 Is the `GENERATING`-only presence rule stated for every non-generating status (`PENDING`/`READY`/`FAILED` → `null`)? [Clarity, Spec §FR-004, contracts/batch-status.md §snackPhase semantics]
- [x] CHK005 Is the `POST /api/batches` shape explicitly declared unchanged? [Consistency, plan.md §Constraints, research.md §R4, tasks.md T022]
- [x] CHK006 Is the `POST /api/snacks/generate` (single-shot) contract explicitly declared unchanged and phase-free? [Consistency, Spec §Edge Cases, plan.md §Constraints]
- [x] CHK007 Is it documented that no new endpoint, query parameter, or request shape is introduced? [Completeness, Spec §Out of Scope, plan.md §Technical Context/§Constraints, research.md §R4]

## Phase Semantics

- [x] CHK008 Is each phase mapped to the exact real backend code point that sets it? [Completeness, data-model.md §1, contracts/batch-status.md §snackPhase semantics]
- [x] CHK009 Is the "advanced at the real code point, never on a timer/elapsed time/poll count" rule stated unambiguously? [Clarity, Spec §FR-002, contracts/batch-status.md §snackPhase semantics]
- [x] CHK010 Is the re-roll sequence (`AWAITING_MODEL → AWAITING_MODEL → ASSEMBLING`) described as real behavior rather than an extra fabricated phase? [Clarity, research.md §R2]
- [x] CHK011 Is per-item isolation (keyed by `subtopicId`, one item's phase never reported for another) documented? [Completeness, Spec §FR-008, data-model.md §2]
- [x] CHK012 Is retry re-entry (a retried item restarts at `RESOLVING_CONTEXT`) documented? [Coverage, Spec §FR-009, contracts/batch-status.md §Rules]
- [x] CHK013 Is the phase lifecycle (present only while `GENERATING`, cleared on settle) defined, including the clear-before/clear-in-`finally` rule? [Completeness, data-model.md §2, plan.md §Architecture Decisions 2]
- [x] CHK014 Is the "may skip a short phase; never show a stale/back-dated phase" rule stated? [Clarity, Spec §Edge Cases, contracts/batch-status.md §Rules]

## Transport & Presentation Boundary

- [x] CHK015 Is it documented that the backend transports only the enum token and the interfaces layer owns the human text? [Consistency, Spec §FR-012, plan.md §Architecture Decisions 3]
- [x] CHK016 Is the frontend fallback behavior defined (generic message only when no phase is reported, never as a substitute for a reported phase)? [Clarity, contracts/batch-status.md §Implementation Notes]
- [x] CHK017 Is it stated that `batch.service.ts` needs no change (typed passthrough)? [Consistency, research.md §R6, plan.md §Risks]
- [x] CHK018 Are the pre-first-poll and skipped-phase cases distinguished from a reported phase in the rendering requirements? [Coverage, research.md §R3]

## Terminality & Unchanged Semantics

- [x] CHK019 Is the terminality rule (`status !== 'IN_PROGRESS'`) restated as unchanged from `002`/`005`? [Consistency, contracts/batch-status.md §Terminality]
- [x] CHK020 Is the `retrying` semantics restated as unchanged? [Consistency, contracts/batch-status.md §retrying semantics]
- [x] CHK021 Is the bounded polling window (default 120 s) restated as unchanged? [Consistency, contracts/batch-status.md §Polling window]
- [x] CHK022 Is it documented that the phase is in-memory and not persisted across restarts? [Clarity, contracts/batch-status.md §Implementation Notes, data-model.md §Persistence]

## Traceability

- [x] CHK023 Does every contract rule trace to an FR (shape → FR-003, settle → FR-004, real code point → FR-002/FR-006, isolation → FR-008, retry → FR-009, privacy → FR-011)? [Traceability, contracts/batch-status.md §Rules]
- [x] CHK024 Is the one-item-per-subtopic keying assumption documented as a contract constraint? [Assumption, data-model.md §2, plan.md §Risks]

## Notes

- Check items off as completed: `[x]`.
- Reference the contract section or task ID that satisfies each item.
- Reviewed: 2026-09-11 — all 24 items verified against the artifacts.
- All items must be checked at implementation completion (tasks.md T027).
