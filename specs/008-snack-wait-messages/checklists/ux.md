# UX Requirements Quality Checklist: Accurate Step-by-Step Snack Waiting Messages

**Purpose**: Validate that the user-facing waiting-message requirements (accuracy, distinctness, fallback, per-item rendering, no regression) are complete, clear, and consistent — before/while implementing `008-snack-wait-messages`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md)

**Note**: This checklist tests the *UX requirements writing* for quality, not the implementation. Each item must be answerable "yes" at implementation completion.

## Message Content & Accuracy

- [x] CHK001 Is a distinct, plain-language message specified for each of the four phases? [Completeness, Spec §FR-005, plan.md §Architecture Decisions 3]
- [x] CHK002 Is it required that the displayed message corresponds to the item's actual current backend phase, with no invented/inferred/time-scheduled phase? [Clarity, Spec §FR-006]
- [x] CHK003 Is it required that the message update as the backend phase changes during a real run? [Completeness, Spec §FR-007]
- [x] CHK004 Is the single generic in-progress message explicitly required to be replaced for an in-progress item? [Completeness, Spec §FR-005/§SC-002]
- [x] CHK005 Is the copy specified as human-readable and not a raw enum/code token? [Clarity, plan.md §Architecture Decisions 3]

## Fallback & Edge Cases

- [x] CHK006 Is the fallback to the existing generic `Preparing your snack…` defined only for the no-phase-reported case (pre-first-poll / skipped phase)? [Clarity, research.md §R3, contracts/batch-status.md §Implementation Notes]
- [x] CHK007 Is it specified that a skipped phase must never cause a stale or back-dated message? [Coverage, Spec §Edge Cases]
- [x] CHK008 Is it specified that a slow model call keeps the AI-response message visible for the whole wait (no timeout-driven message change)? [Completeness, Spec §Edge Cases]
- [x] CHK009 Is it specified that once an item is ready/failed, no in-progress phase message is shown for it? [Completeness, Spec §SC-004]
- [x] CHK010 Is the failure-during-a-phase UX defined (error banner + retry takes over, no phase message)? [Coverage, Spec §Edge Cases, §US1.5]

## Per-Item Rendering

- [x] CHK011 Is per-item tracking and rendering required so one item's phase is never shown for another? [Completeness, Spec §FR-008]
- [x] CHK012 Is the multiple-items-in-one-batch scenario addressed (each item carries its own phase/message)? [Coverage, Spec §Edge Cases]
- [x] CHK013 Is retry re-entry specified in user-visible terms (phases appear again from the context message)? [Completeness, Spec §FR-009]

## No Regression / Scope

- [x] CHK014 Is it required that ready snacks render exactly as before with no phase message? [Consistency, Spec §FR-010, §US3.1]
- [x] CHK015 Is it required that failed items keep their cause and retry affordance? [Consistency, Spec §FR-010, §US3.2]
- [x] CHK016 Is it required that the empty-state gating is unchanged? [Consistency, Spec §FR-010, §US3.3]
- [x] CHK017 Is it required that the polling interval, bounded window, and terminal-stop behavior are unchanged? [Consistency, Spec §FR-010, §US3.4]
- [x] CHK018 Is it stated that no UI redesign beyond the in-progress waiting text is in scope (existing card/banner/spinner styles reused)? [Clarity, Spec §Out of Scope, plan.md §Constraints]
- [x] CHK019 Is the pre-first-poll empty-list block explicitly kept generic because no item/phase exists yet? [Clarity, plan.md §Architecture Decisions 6]

## Accessibility & Copy (Gaps)

- [x] CHK020 Are requirements defined for the waiting message's accessibility (announced via `aria-live`, not color-only)? [Gap, Accessibility, plan.md §Architecture Decisions 6, snacks.component.html:114]
- [x] CHK021 Is the phase message area required to keep the existing spinner affordance? [Gap, Clarity, plan.md §Architecture Decisions 6, snacks.component.html:115]
- [x] CHK022 Is it specified that the existing retry affordance remains keyboard-operable? [Gap, Accessibility — N/A: retry is the unchanged native `<button>` at snacks.component.html:105, preserved by FR-010]

## Notes

- Check items off as completed: `[x]`.
- Reference the spec section or task ID that satisfies each item.
- Reviewed: 2026-09-11 — all 22 items verified against the artifacts. The two accessibility gaps (CHK020/CHK021) were resolved by documenting in plan.md §Architecture Decisions 6 and tasks.md T018 that the existing `aria-live="polite"` container and spinner are preserved; CHK022 is N/A because the unchanged native button is already keyboard-operable.
- Items marked `[Gap]` should be resolved (documented or explicitly excluded) before release; all items must be checked at implementation completion (tasks.md T027).
