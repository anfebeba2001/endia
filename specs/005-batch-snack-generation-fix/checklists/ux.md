# UX Requirements Quality Checklist: Batch Snack Generation Fix

**Purpose**: Validate that the user-facing requirements for progress, empty-state gating, and polling/retry termination are complete, clear, and consistent — before/while implementing `005-batch-snack-generation-fix`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md)

**Note**: This checklist tests the *UX requirements writing* for quality, not the implementation. Each item must be answerable "yes" at implementation completion.

## Progress & Empty-State Gating

- [x] CHK001 Is it required that a progress/loading state is shown while a batch is being created and the item list is still empty? [Completeness, Spec §FR-009, §US3.1]
- [x] CHK002 Is it required that the empty state is suppressed before the first poll response is processed? [Completeness, Spec §FR-010, §US3.1]
- [x] CHK003 Is it required that the empty state is suppressed while the item list is momentarily empty during `IN_PROGRESS`? [Completeness, Spec §FR-009, §US3.2]
- [x] CHK004 Is the empty state's correct terminal condition (terminal status + zero ready snacks) specified? [Completeness, Spec §FR-011, §US3.4]
- [x] CHK005 Is it specified that the empty state is shown only after polling has stopped? [Consistency, Spec §FR-011/§FR-013, §US3.4]

## Polling & Retry Termination

- [x] CHK006 Is it required that polling stops within one interval of any terminal status? [Completeness, Spec §FR-013, §SC-005]
- [x] CHK007 Is the terminal check required to treat every non-`IN_PROGRESS` status as terminal (including `PARTIAL`)? [Completeness, Spec §FR-012]
- [x] CHK008 Is it required that the retrying indicator reflects only active generation/retry activity, not the whole in-progress lifetime? [Completeness, Spec §FR-014]
- [x] CHK009 Is it required that the retrying indicator clears on terminal status and on poll error/timeout? [Consistency, Spec §FR-014, plan.md §Architecture Decisions 3]
- [x] CHK010 Is the bounded polling window (configurable, default 120 s) required to surface a terminal, user-visible error rather than polling indefinitely? [Completeness, Spec §FR-015, §SC-005]

## Mixed / Partial Results & Terminal Zero-Ready

- [x] CHK011 Is the mixed-result rendering defined (ready snacks render alongside failed-item errors and the per-target retry affordance)? [Completeness, Spec §Edge Cases]
- [x] CHK012 Is it specified that a failed target does not block ready snacks from displaying? [Consistency, Spec §Edge Cases, contracts/batch-status.md]
- [x] CHK013 Is the terminal zero-ready outcome required to show a terminal, user-visible result (not a silent empty list, not an infinite spinner)? [Completeness, Spec §FR-011, §US3.4]
- [x] CHK014 Is the bounded automatic retry (≤3 attempts) preserved in the UX flow? [Consistency, Spec §FR-016, §SC-006]

## Interaction & Copy

- [x] CHK015 Is the no-empty-state-flash behavior defined in user-visible terms (progress shown across the create→first-poll window)? [Clarity, Spec §FR-010]
- [x] CHK016 Is the progress copy specified as human-readable (e.g. "Preparing your snack…") and not a raw enum/code? [Clarity, plan.md §Architecture Decisions 3]
- [x] CHK017 Is the reuse of the existing card/error-banner/spinner styles (no redesign) explicitly stated? [Clarity, Spec §Out of Scope, plan.md §Constraints]
- [x] CHK018 Is the client navigation-away behavior covered (no background polling for an abandoned screen, no duplicate batch)? [Coverage, Spec §Edge Cases]

## Accessibility & Copy (Gaps)

- [x] CHK019 Are requirements defined for the progress state's accessibility (announced via `aria-live`, not color-only)? [Gap, Accessibility]
- [x] CHK020 Is the terminal-error copy specified as human-readable while still naming the typed cause? [Gap, Clarity, Spec §FR-005]
- [x] CHK021 Is it specified that the existing retry affordance remains keyboard-operable? [Gap, Accessibility]

## Notes

- Check items off as completed: `[x]`.
- Reference the spec section or task ID that satisfies each item.
- Items marked `[Gap]` should be resolved (documented or explicitly excluded) before release; all items must be checked at implementation completion (tasks.md T024).
