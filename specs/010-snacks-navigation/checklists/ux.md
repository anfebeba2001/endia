# UX Requirements Quality Checklist: Return to Snacks from Results + Snacks Navigation

**Purpose**: Validate that the user-facing requirements for the return action, the left-side index, Next/Previous navigation, and accessibility/state consistency are complete, clear, and consistent before/while implementing `010-snacks-navigation`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md)

**Note**: This checklist tests the *UX requirements writing* for quality, not the implementation. Each item must be answerable "yes" at implementation completion.

## Return Flow UX

- [X] CHK001 Is the new action's exact label ("Back to snacks") and placement (`.results-footer`, next to "Start a new theme") specified? [Completeness, Spec §FR-001]
- [X] CHK002 Is it required in BOTH completion states ("Quiz complete" and "Perfect score!")? [Completeness, Spec §SC-001/§US1 AS1]
- [X] CHK003 Is it specified that returning shows the existing snacks with no empty/creation/loading state? [Completeness, Spec §FR-004]
- [X] CHK004 Is the no-READY-match fallback (keep the current snack, no arbitrary jump) specified? [Clarity, Spec §FR-018]
- [X] CHK005 Is the "Start a new theme" action required to remain visible and distinct? [Consistency, Spec §FR-005/§FR-006]

## Left Index UX

- [X] CHK006 Is the left-side index required to list every snack labelled by `subtopicName`? [Completeness, Spec §FR-007]
- [X] CHK007 Is the active-entry highlight specified with `aria-current="true"` plus an active class? [Clarity, Spec §FR-008]
- [X] CHK008 Is click-to-jump (card + highlight update together) required? [Completeness, Spec §FR-009]
- [X] CHK009 Is the disabled affordance for non-READY/FAILED entries specified? [Clarity, Spec §FR-010]
- [X] CHK010 Is the single-snack case specified (one entry, both controls disabled)? [Coverage, Spec §Edge Cases]

## Next/Previous & Persistence UX

- [X] CHK011 Are Next and Previous required to move exactly one snack at a time? [Completeness, Spec §FR-011]
- [X] CHK012 Is the boundary behavior specified (disabled/no-op, no out-of-range, no blank card)? [Clarity, Spec §FR-012]
- [X] CHK013 Is it required that the app remembers the active snack across navigation ("remembers where I was")? [Completeness, Spec §FR-014]
- [X] CHK014 Is Previous explicitly able to return to a previously-READY snack (disabled only at index 0)? [Clarity, Spec §FR-015]
- [X] CHK015 Is the Next READY-only affordance (cooking label / all-served) preserved? [Consistency, Spec §FR-016, plan.md AD-4]

## Accessibility & Consistency

- [X] CHK016 Are all navigation controls required to be real interactive elements with accessible names? [Coverage, Spec §FR-020]
- [X] CHK017 Are keyboard reachability and Enter/Space activation required for all controls? [Coverage, Spec §FR-020/§SC-008]
- [X] CHK018 Is a visible `:focus-visible` indicator required for all new controls? [Coverage, Spec §FR-020/§SC-008]
- [X] CHK019 Is mutual consistency of card content, highlight, and enabled states required after any interaction sequence? [Consistency, Spec §FR-019/§SC-007]

## Scope & Non-Goals

- [X] CHK020 Is it stated that the snack card and progressive-delivery experience are preserved (not redesigned)? [Clarity, Spec §Non-Goals]
- [X] CHK021 Is responsive/mobile layout for the index explicitly deferred? [Clarity, Spec §Non-Goals]
- [X] CHK022 Are auto-advance, autoplay, extra keyboard shortcuts, and animated transitions excluded? [Clarity, Spec §Non-Goals]
- [X] CHK023 Is the theme-history backlog item explicitly out of scope? [Clarity, Spec §Non-Goals]

## Notes

- Check items off as completed: `[x]`.
- Reference the spec/plan/contract section or task ID that satisfies each item.
- All items must be checked at implementation completion (tasks.md T034).
