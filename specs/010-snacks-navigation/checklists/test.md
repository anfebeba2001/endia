# Test Requirements Quality Checklist: Return to Snacks from Results + Snacks Navigation

**Purpose**: Validate that the feature's verification requirements are complete, unambiguous, and executable — covering the two new Playwright specs, the named regression suite, the type-check build, and manual verification — before/while implementing `010-snacks-navigation`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md) | [quickstart.md](../quickstart.md)

**Note**: This checklist tests the *test/verification requirements* for quality, not the implementation. This feature has **no frontend unit runner**; Playwright E2E is the verification surface. Each item must be answerable "yes" at implementation completion.

## Return-Flow Coverage (US1)

- [ ] CHK001 Is there a test requirement that BOTH completion states ("Quiz complete" and "Perfect score!") render "Back to snacks"? [Coverage, Spec §SC-001, tasks.md T006]
- [ ] CHK002 Is there a test requirement asserting 0 additional `POST /api/batches` after clicking "Back to snacks"? [Coverage, Spec §SC-002/§FR-002, tasks.md T006, T031]
- [ ] CHK003 Is there a test requirement that the quizzed subtopic's snack is active on return (matching `subtopicId`)? [Coverage, Spec §SC-003/§FR-003, tasks.md T006]
- [ ] CHK004 Is there a test requirement that existing snacks render with no loading/empty/creation state? [Coverage, Spec §FR-004, tasks.md T006]
- [ ] CHK005 Is there a test requirement that "Start a new theme" still resets state and lands on `/`? [Coverage, Spec §FR-005, tasks.md T006, T027]

## Index Coverage (US2)

- [ ] CHK006 Is there a test requirement that the index lists an entry for every snack labelled by `subtopicName`? [Coverage, Spec §FR-007/§SC-004, tasks.md T011]
- [ ] CHK007 Is there a test requirement that exactly one entry is active (`aria-current="true"` + active class)? [Coverage, Spec §FR-008/§SC-004, tasks.md T011]
- [ ] CHK008 Is there a test requirement that clicking a READY entry updates the card and highlight together? [Coverage, Spec §FR-009, tasks.md T011]
- [ ] CHK009 Is there a test requirement that non-READY (GENERATING/FAILED) entries are disabled and inert? [Coverage, Spec §FR-010, tasks.md T011]

## Navigation & Persistence Coverage (US3)

- [ ] CHK010 Is there a test requirement that Next/Previous move the index by exactly one? [Coverage, Spec §FR-011/§SC-005, tasks.md T015]
- [ ] CHK011 Is there a test requirement for boundary disabled states with no out-of-range/blank card? [Coverage, Spec §FR-012/§SC-005, tasks.md T015]
- [ ] CHK012 Is there a test requirement that the active index survives the quiz→results→return round trip? [Coverage, Spec §FR-014/§SC-006, tasks.md T015]
- [ ] CHK013 Is there a test requirement that Previous can return to a previously-READY snack and is disabled only at index 0? [Coverage, Spec §FR-015, tasks.md T015]
- [ ] CHK014 Is there a test requirement that Next still advances only to a READY snack (progressive delivery preserved)? [Coverage, Spec §FR-016/§SC-009, tasks.md T015, T027]
- [ ] CHK015 Is there a test requirement that "Start Quiz" uses the active snack's `subtopicId`? [Coverage, Spec §FR-017, tasks.md T015]
- [ ] CHK016 Is there a test requirement for the no-READY-match fallback (keep current index)? [Coverage, Spec §FR-018, tasks.md T006]

## Consistency & Accessibility Coverage (US4)

- [ ] CHK017 Is there a test requirement that after mixed interactions the card, highlight, and enabled states agree? [Coverage, Spec §FR-019/§SC-007, tasks.md T020]
- [ ] CHK018 Is there a test requirement for keyboard reachability/activation and a visible `:focus-visible` indicator? [Coverage, Spec §FR-020/§SC-008, tasks.md T020]

## Test Clarity & Feasibility

- [ ] CHK019 Is the absence of a frontend unit-test runner documented, with Playwright named as the verification path? [Clarity, plan.md §Testing, tasks.md preamble]
- [ ] CHK020 Are the two new spec files named exactly and their coverage mapped to stories? [Clarity, quickstart.md §Automated tests, tasks.md T006, T011]
- [ ] CHK021 Is the use of `setupQuizSummaryMock({ correctAnswers: 4 })` to drive the "Perfect score!" state stated? [Clarity, plan.md §Testing Strategy, tasks.md T006]
- [ ] CHK022 Is the use of the configurable batch mock to count `POST /api/batches` stated? [Clarity, helpers.ts, tasks.md T006]
- [ ] CHK023 Are the exact test and build commands recorded? [Clarity, quickstart.md §Automated tests]

## Regression, Boundaries & Gaps

- [ ] CHK024 Are the named regression suites (`restart-clears-state`, `progressive-delivery`, `back-navigation`, `loading-states`) required to pass unchanged? [Consistency, Spec §FR-021/§SC-009, tasks.md T027]
- [ ] CHK025 Is a full-suite Playwright run required? [Coverage, Spec §SC-009, tasks.md T032]
- [ ] CHK026 Is the strict-mode selector baseline (`.results-footer .primary-btn`, `.snack-card .primary-btn`, `button.link-btn`) verified? [Consistency, Spec §FR-023, tasks.md T003, T030]
- [ ] CHK027 Is `npm run build` required to type-check the template/method changes? [Coverage, Spec §SC-009, tasks.md T028]
- [ ] CHK028 Is it stated that no backend tests are added or changed (backend frozen)? [Clarity, Spec §FR-022, tasks.md T029]
- [ ] CHK029 Is the single-snack boundary (both controls disabled, one entry) covered by requirements? [Gap, Spec §Edge Cases]
- [ ] CHK030 Are the all-generating and all-failed set cases covered? [Gap, Spec §Edge Cases]
- [ ] CHK031 Is the stale/out-of-range stored index case covered? [Gap, Spec §Edge Cases, tasks.md T019]
- [ ] CHK032 Is the missing-`selectedSubtopic` and deep-link-no-snacks case covered? [Gap, Spec §Edge Cases]
- [ ] CHK033 Does the manual quickstart verification cover US1–US4? [Coverage, quickstart.md, tasks.md T033]

## Notes

- Check items off as completed: `[x]`.
- Reference the spec/plan section or task ID that satisfies each item.
- All items must be checked at implementation completion (tasks.md T034).
