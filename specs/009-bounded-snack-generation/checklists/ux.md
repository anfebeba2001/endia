# UX Requirements Quality Checklist: Guarantee Bounded Snack Generation

**Purpose**: Validate that the preserved user-facing requirements for terminal settling, bounded polling, and per-item failure surfacing are complete, clear, and consistent — before/while implementing `009-bounded-snack-generation`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md)

**Note**: This checklist tests the *UX requirements writing* for quality, not the implementation. `009` makes **no frontend source change**; the UX requirements are preserved from 002/005 and verified, not redesigned. Each item must be answerable "yes" at implementation completion.

## Screen Always Settles

- [ ] CHK001 Is it required that the screen settles (stops polling) within one poll interval of any terminal status? [Completeness, Spec §FR-018/§SC-006, contracts/batch-status.md §Terminality]
- [ ] CHK002 Is the terminal check required to treat every non-`IN_PROGRESS` status as terminal (including `PARTIAL`)? [Clarity, Spec §FR-018, contracts/batch-status.md §Terminality]
- [ ] CHK003 Is the bounded polling window (configurable, default 120 s) required to surface a terminal, user-visible error rather than polling indefinitely? [Completeness, Spec §FR-018, contracts/batch-status.md §Polling window]
- [ ] CHK004 Is it required that the backend always reaches a terminal status so the existing client bound is exercised normally? [Consistency, Spec §FR-012, contracts/batch-status.md §Terminal guarantee]
- [ ] CHK005 Is it stated that no further poll is scheduled once a terminal status or timeout is observed? [Completeness, Spec §FR-018, §US6.3]

## Per-Item Failure Surfacing

- [ ] CHK006 Is the mixed-result rendering defined (ready snacks render alongside failed-item errors)? [Completeness, Spec §Edge Cases, contracts/batch-status.md §Implementation Notes]
- [ ] CHK007 Is it specified that a failed item does not block ready snacks from displaying? [Consistency, Spec §Edge Cases, contracts/batch-status.md]
- [ ] CHK008 Is a `FAILED` item required to render an error banner with its typed cause and a retry affordance? [Completeness, contracts/batch-status.md §Implementation Notes]
- [ ] CHK009 Is the terminal all-fail outcome required to show a terminal, user-visible result (not a silent empty list, not an infinite spinner)? [Completeness, Spec §Edge Cases, §US4]
- [ ] CHK010 Is the error copy required to be human-readable while still naming the typed cause? [Clarity, Spec §FR-019, data-model.md §Typed Failure Cause]

## Preserved Interaction Behavior

- [ ] CHK011 Is it explicitly stated that `009` does not redesign the UI, wait messages, or empty-state behavior? [Clarity, Spec §Out of Scope, plan.md §Constraints]
- [ ] CHK012 Is the reuse of the existing card/error-banner/spinner styles (no redesign) stated? [Clarity, Spec §Out of Scope, plan.md §Constraints]
- [ ] CHK013 Is the bounded automatic retry (≤3 attempts) preserved in the UX flow? [Consistency, Spec §FR-022, plan.md §Technical Context]
- [ ] CHK014 Is the per-phase wait-message behavior (008) preserved as non-regression? [Consistency, Spec §Context, §FR-022]
- [ ] CHK015 Is the `retrying` indicator required to reflect only active generation, clearing on terminal status and on poll error/timeout? [Clarity, contracts/batch-status.md §retrying semantics]

## Accessibility & Copy (Gaps)

- [ ] CHK016 Are requirements defined for the progress/loading state's accessibility (announced, not color-only)? [Gap, Accessibility]
- [ ] CHK017 Is it specified that the existing retry affordance remains keyboard-operable? [Gap, Accessibility]
- [ ] CHK018 Is it specified that timeout/failure user-facing copy contains no sensitive data? [Gap, Spec §FR-020]

## Notes

- Check items off as completed: `[x]`.
- Reference the spec/contract section or task ID that satisfies each item.
- Items marked `[Gap]` should be resolved (documented or explicitly excluded) before release; all items must be checked at implementation completion (tasks.md T032).
