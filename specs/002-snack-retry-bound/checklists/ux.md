# UX Requirements Quality Checklist: Bounded Snack Reattempts and Polling Termination

**Purpose**: Validate that the user-facing requirements for terminal errors, progress indication, and manual retry are complete, clear, and consistent — before/while implementing `002-snack-retry-bound`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md)

**Note**: This checklist tests the *UX requirements writing* for quality, not the implementation. Each item must be answerable "yes" at implementation completion.

## Terminal State & Progress

- [x] CHK001 Is the terminal error state's visible behavior defined (spinner stopped, no background activity)? [Completeness, Spec §FR-005, §US1]
- [x] CHK002 Is it required that the terminal message names the failure cause? [Clarity, Spec §FR-005]
- [x] CHK003 Is the timeout case required to surface a terminal, user-visible error rather than a silent stop? [Completeness, Spec §FR-010, §US2.5]
- [x] CHK004 Is it specified that progress stops when a target is exhausted or the polling window elapses? [Consistency, Spec §FR-005, §FR-010]
- [x] CHK005 Is the `retrying` indicator's visible meaning (active work only) defined so it does not appear "stuck on"? [Clarity, Spec §FR-011, contracts/snack-retry-behavior.md §C10]

## Manual Retry Affordance

- [x] CHK006 Is it required that a manual retry affordance is available after the automatic budget is exhausted? [Completeness, Spec §FR-006, §US4.1]
- [x] CHK007 Is the retry affordance required to show progress again when invoked? [Completeness, Spec §US4.2]
- [x] CHK008 Is it specified that the terminal error is cleared when a manual retry succeeds and the snack is displayed? [Completeness, Spec §US4.4]
- [x] CHK009 Is the retry affordance required to be disabled (or non-triggering) while a cycle is in flight? [Consistency, Spec §FR-007, tasks.md T023]
- [x] CHK010 Is the reuse of the existing retry affordance / card / banner (no redesign) explicitly stated? [Clarity, Spec §Assumptions, §Out of Scope]

## Mixed / Partial Results

- [x] CHK011 Is the mixed-result rendering defined: ready snacks render alongside failed-item errors and a per-target retry affordance? [Completeness, Spec §US2.1, §Edge Cases]
- [x] CHK012 Is it specified that a failed target does not block ready snacks from displaying? [Consistency, Spec §Edge Cases, contracts/batch-status.md]
- [x] CHK013 Is the one-snack-at-a-time progression preserved when only one target is retried? [Consistency, plan.md §Risks, tasks.md T026]

## Interaction & Duplicate Actions

- [x] CHK014 Is the duplicate/rapid-action behavior defined (at most one new cycle per target)? [Completeness, Spec §US4.3, §FR-007]
- [x] CHK015 Is the no-blind-timer behavior defined in user-visible terms (no surprise regeneration while waiting)? [Clarity, Spec §US3.1, §FR-004]
- [x] CHK016 Is the error message required to be user-safe (no sensitive data)? [Completeness, Spec §FR-015]

## Accessibility & Copy (Gaps)

- [x] CHK017 Are requirements defined for the terminal error's accessibility (announced/readable, not color-only)? [Gap, Accessibility]
- [x] CHK018 Is the terminal-error copy specified as human-readable (not a raw enum/code) while still naming the cause? [Gap, Clarity, Spec §FR-005]
- [x] CHK019 Is it specified that the retry affordance remains keyboard-operable? [Gap, Accessibility]

## Notes

- Check items off as completed: `[x]`.
- Reference the spec section or task ID that satisfies each item.
- Items marked `[Gap]` should be resolved (documented or explicitly excluded) before release; all items must be checked at implementation completion (tasks.md T037).

**Completion verification (T037, 2026-09-11)**: The exhaustion banner uses `role="alert"` (announced, not color-only), native `<button>` retry (keyboard-operable), and human-readable copy ("Couldn't prepare this snack after 3 attempts.") while naming the typed cause (CHK017–CHK019). Terminal progress stops via the `retrying` signal; the retry affordance is reused unchanged (no redesign).
