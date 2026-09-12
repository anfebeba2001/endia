# Implementation Requirements Quality Checklist: Return to Snacks from Results + Snacks Navigation

**Purpose**: Validate that the implementation requirements for the return flow, shared navigation state, index, and selector safety are complete, clear, ordered, and internally consistent before/while implementing `010-snacks-navigation`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md) | [data-model.md](../data-model.md) | [contracts/ui-navigation.md](../contracts/ui-navigation.md)

**Note**: This checklist tests the *implementation requirements writing* (completeness, clarity, ordering, consistency), not the implementation. Every item must be answerable "yes" at implementation completion; mark `[x]` with the evidence (section, requirement ID, or task ID) that satisfies it.

## Change-Set Completeness

- [X] CHK001 Is the exact file set to modify enumerated (`results.component.{ts,html,scss}`, `snacks.component.{ts,html,scss}`, `app-state.service.ts`)? [Completeness, plan.md §Project Structure, tasks.md §Organization]
- [X] CHK002 Is `AppStateService.currentSnackIndex` named as the single source of truth, replacing the component-local index? [Completeness, Spec §FR-013, data-model.md §2]
- [X] CHK003 Is the return-path hydration of `items()` from `appState.snacks()` specified as the fix for the existing early-return gap? [Completeness, Spec §FR-004, plan.md §Summary]
- [X] CHK004 Is skipping `createBatch`/polling on the return path required explicitly? [Completeness, Spec §FR-002/§FR-004, tasks.md T010]
- [X] CHK005 Is it required that `backToSnacks()` never calls `AppStateService.reset()`? [Completeness, Spec §FR-002/§FR-005, tasks.md T007]
- [X] CHK006 Is the existing "Start a new theme" reset-and-home behavior required to remain unchanged? [Completeness, Spec §FR-005, tasks.md T008]

## Ordering & Clarity

- [X] CHK007 Is the clamp-after-hydration ordering specified so the shared index is not reset to 0? [Clarity, plan.md §Risks, tasks.md T010]
- [X] CHK008 Is the READY-only Next rule stated explicitly (no advancing onto generating/failed)? [Clarity, Spec §FR-016, tasks.md T016]
- [X] CHK009 Is Previous specified as disabled only at index 0 (no READY requirement)? [Clarity, Spec §FR-015, tasks.md T016]
- [X] CHK010 Is `goTo(index)` required to reject out-of-range targets so rapid clicks cannot desync? [Clarity, Spec §FR-012/§FR-019, tasks.md T016]
- [X] CHK011 Is the no-READY-match-on-return fallback (keep the current index) specified? [Clarity, Spec §FR-018, tasks.md T007]
- [X] CHK012 Is the `selectedSubtopic` → `quizSubTopicId()` fallback for the match specified? [Clarity, plan.md AD-1, contracts/ui-navigation.md §1]
- [X] CHK013 Is `selectIndex` required to guard on READY even though the entry button is disabled? [Clarity, Spec §FR-009/§FR-010, tasks.md T012]

## Selector Safety & Consistency

- [X] CHK014 Is `.ghost-btn` (never `primary-btn`/`link-btn`) specified for "Back to snacks" and "Previous"? [Consistency, Spec §FR-006/§FR-023, research.md R2]
- [X] CHK015 Are index entries required to use `.snack-index__item` and never `link-btn`? [Consistency, Spec §FR-023, contracts/ui-navigation.md §2]
- [X] CHK016 Is the `data-testid="back-to-snacks"` selector contract recorded? [Consistency, contracts/ui-navigation.md §1, tasks.md T008]
- [X] CHK017 Is exactly one `primary-btn` per existing selector (`.results-footer`, `.snack-card`) required? [Consistency, Spec §FR-006/§FR-023, tasks.md T030]
- [X] CHK018 Is the single-clamped-index design stated as the desync prevention for card/highlight/enabled state? [Consistency, Spec §FR-019, data-model.md §2]

## Scope, Assumptions & Non-Goals

- [X] CHK019 Is the frontend-only scope and backend freeze stated (no endpoint/DTO/schema change)? [Completeness, Spec §FR-022, contracts/backend-unchanged.md]
- [X] CHK020 Is the no-new-dependency/component/route constraint stated? [Completeness, plan.md §Technical Context, tasks.md §Organization]
- [X] CHK021 Is responsive/mobile layout for the index explicitly out of scope? [Clarity, Spec §Non-Goals]
- [X] CHK022 Is the FAILED-middle-snack interaction (Next cannot pass it; index reaches it) recorded as an interpretation, not a defect? [Assumption, research.md R4, tasks.md preamble]
- [X] CHK023 Is the session-scoped (non-persisted) nature of the active index recorded? [Assumption, Spec §Assumptions, data-model.md §6]

## Acceptance & Traceability

- [X] CHK024 Is every functional requirement FR-001..FR-023 mapped to at least one task? [Traceability, tasks.md §Requirement Coverage]
- [X] CHK025 Is every success criterion SC-001..SC-010 mapped to at least one task? [Traceability, tasks.md §Requirement Coverage]
- [X] CHK026 Does every task carry exact file paths and a verifiable done-definition? [Measurability, tasks.md]
- [X] CHK027 Are tests authored before their implementation targets (TDD ordering)? [Consistency, tasks.md §Dependencies]

## Notes

- Check items off as completed: `[x]`.
- Add the satisfying spec/plan/task reference inline.
- All items must be checked at implementation completion (tasks.md T034).
