# UX Checklist: Keyword Meanings Up to 20 Words (Readability & Non-Regression)

**Purpose**: Validate the quality, clarity, and completeness of the *readability and non-regression
display requirements* for keyword meanings raised from 3 to 20 words — not the implementation. This is a
"unit test for the requirements" for how a longer definition must render and behave (US1–US3, FR-004,
FR-011–FR-014, SC-001, SC-004).
**Created**: 2026-09-23
**Feature**: [spec.md](../spec.md) — sections §User Story 1, §User Story 2, §User Story 3,
§Requirements (Length policy, Rendering and readability), §Edge Cases, §Success Criteria

**Note**: Items test whether the requirements are well-written, complete, and verifiable — they are not
runtime test cases.

## Readability Requirement Completeness

- [x] CHK001 Is the definition card required to display the full up-to-20-word definition (not clipped)? [Completeness, Spec §FR-011] — satisfied by FR-011 / T014
- [x] CHK002 Is text wrapping within the card required? [Completeness, Spec §FR-012] — satisfied by FR-012 / T014
- [x] CHK003 Is "no horizontal overflow and no clipping" required? [Completeness, Spec §FR-012, §SC-004] — satisfied by FR-012, SC-004 / T006, T014
- [x] CHK004 Is usability at both desktop and narrow (mobile) widths required? [Completeness, Spec §FR-012] — satisfied by FR-012 / T014
- [x] CHK005 Is a focused frontend test/assertion for a 20-word definition with no horizontal overflow required? [Completeness, Spec §FR-015, §SC-004] — satisfied by FR-015, SC-004 / T006

## Requirement Clarity

- [x] CHK006 Is "readable at up to 20 words" tied to an observable outcome (wrapped, not overflowing, not clipped)? [Clarity, Spec §FR-012, §SC-004] — satisfied by FR-012, SC-004 / T006
- [x] CHK007 Is the card's natural growth stated (no max-height/internal scroll now; bounded max-height as the agreed fallback)? [Clarity, Spec §Edge Cases, §Clarifications #3] — satisfied by spec §Edge Cases / §Clarifications #3
- [x] CHK008 Is the narrow-viewport presentation (existing bottom-sheet fallback) clarified as unchanged? [Clarity, Spec §Project Structure Impact, §Assumptions] — satisfied by spec §Project Structure Impact / §Assumptions

## Requirement Consistency

- [x] CHK009 Do FR-011 (full definition) and FR-012 (no overflow/clipping) agree with the "no redesign" non-goal? [Consistency, Spec §FR-011, §FR-012, §Non-Goals] — satisfied by FR-011, FR-012 / spec §Non-Goals
- [x] CHK010 Is the highlighted-keyword rendering required to stay readable and unchanged at longer definitions? [Consistency, Spec §FR-012, §FR-016] — satisfied by FR-012, FR-016 / T015
- [x] CHK011 Is the card's open/dismiss/focus behavior required to remain unchanged (feature 013)? [Consistency, Spec §FR-016, US3 AS5] — satisfied by FR-016 / US3 AS5 / T014, T015

## Acceptance Criteria Quality

- [x] CHK012 Can "100% of definition cards render an up-to-20-word definition with no horizontal overflow and no clipping" be objectively measured? [Measurability, Spec §SC-004] — satisfied by SC-004 / T006, T014
- [x] CHK013 Can "4–20-word definitions are displayed in full; 0 truncated to 3" be objectively verified? [Measurability, Spec §SC-001] — satisfied by SC-001 / T003, T004, T014

## Scenario & Edge Case Coverage

- [x] CHK014 Is the 20-word definition rendering at desktop width covered? [Coverage, Spec §US3 AS1, §SC-004] — satisfied by spec §US3 AS1 / T006
- [x] CHK015 Is the same rendering at narrow (mobile) width covered? [Coverage, Spec §US3 AS2] — satisfied by spec §US3 AS2 / T006
- [x] CHK016 Is a very long single token covered (wraps within the card, no overflow)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T014
- [x] CHK017 Are Unicode/emoji/RTL long definitions covered? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T014
- [x] CHK018 Is the 19/20/21 boundary display behavior covered (20 valid, 21 bounded)? [Coverage, Spec §Edge Cases, §SC-003] — satisfied by spec §Edge Cases / T003, T004
- [x] CHK019 Are previously stored 3-word definitions covered as still rendering correctly (no migration)? [Coverage, Spec §FR-017, §Edge Cases] — satisfied by FR-017 / spec §Edge Cases
- [x] CHK020 Is non-regression of feature 013/014 interaction (highlight, click-to-open, dismiss, focus, "Generate snack") covered? [Coverage, Spec §FR-016, US3 AS5] — satisfied by FR-016 / T015, T018

## Accessibility & Non-Functional

- [x] CHK021 Is the card's dismiss/close affordance and focus return required to remain unchanged? [Completeness, Spec §FR-016, US3 AS5] — satisfied by FR-016 / US3 AS5
- [x] CHK022 Is the performance expectation stated as bounded (a single `split` on a bounded string; no layout change)? [Coverage, plan.md §Performance Goals] — satisfied by plan.md §Performance Goals

## Notes

- Check items off as completed: `[x]`
- Add findings inline; link to spec/contract sections where resolved
- Items are numbered sequentially for easy reference; all must be checked at implementation completion
