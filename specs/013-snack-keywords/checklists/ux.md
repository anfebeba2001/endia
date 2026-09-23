# UX Checklist: Snack Keyword Highlight & Definition Card

**Purpose**: Validate the quality, clarity, and completeness of the *highlight and definition-card
display requirements* for snack keywords — not the implementation. This is a "unit test for the
requirements" for how keywords must appear and behave across surfaces (US2, US3, FR-013–FR-025).
**Created**: 2026-09-23
**Feature**: [spec.md](../spec.md) — sections §User Story 2, §User Story 3, §Requirements (Rendering —
highlight, Rendering — definition card), §Edge Cases, §Success Criteria;
[contracts/snack-keywords-contract.md](../contracts/snack-keywords-contract.md) §2–§3

**Note**: Items test whether the requirements are well-written, complete, and verifiable — they are not
runtime test cases.

## Highlight Requirement Completeness

- [x] CHK001 Is a distinct highlight color required for key terms, tied to an observable outcome (distinct from body text)? [Completeness, Spec §FR-013, §SC-002] — satisfied by FR-013, SC-002
- [x] CHK002 Is it required that only terms present in the rendered body text are highlighted? [Completeness, Spec §FR-014] — satisfied by FR-014
- [x] CHK003 Is case-insensitive, whole-word, literal matching required? [Completeness, Spec §FR-015] — satisfied by FR-015
- [x] CHK004 Is longest-term-wins / non-overlapping / non-nested highlighting required? [Completeness, Spec §FR-016] — satisfied by FR-016
- [x] CHK005 Is highlighting **every** occurrence required? [Completeness, Spec §FR-017] — satisfied by FR-017
- [x] CHK006 Is preservation of feature-012 rich formatting (bold, bullets, paragraphs) during highlighting required? [Completeness, Spec §FR-015, §SC-009] — satisfied by FR-015, SC-009
- [x] CHK007 Is the highlight scope (body fields only, never `related`) explicit? [Completeness, Spec §FR-014, contracts §2] — satisfied by FR-014 / contract §2

## Definition Card Requirement Completeness

- [x] CHK008 Is click/tap-to-open required? [Completeness, Spec §FR-018] — satisfied by FR-018
- [x] CHK009 Is "card shows only the definition, never more than 3 words" required? [Completeness, Spec §FR-019] — satisfied by FR-019
- [x] CHK010 Are all three dismissal methods (outside-click/tap, close control, Escape) required? [Completeness, Spec §FR-020] — satisfied by FR-020
- [x] CHK011 Is "no navigation / no loss of reading position on dismissal" required? [Completeness, Spec §FR-020] — satisfied by FR-020
- [x] CHK012 Is "opening another term closes the previous card" required? [Completeness, Spec §FR-021] — satisfied by FR-021
- [x] CHK013 Is desktop + mobile usability with no overflow required? [Completeness, Spec §FR-022] — satisfied by FR-022
- [x] CHK014 Is keyboard activation and appropriate focus return required? [Completeness, Spec §FR-023] — satisfied by FR-023
- [x] CHK015 Is no-keyword rendering required to be unchanged (no artifact)? [Completeness, Spec §FR-024] — satisfied by FR-024
- [x] CHK016 Is consistency across snack card, results recap, and history required? [Completeness, Spec §FR-025] — satisfied by FR-025

## Requirement Clarity

- [x] CHK017 Is "distinct color" quantified against body text rather than left to a visual adjective? [Clarity, Spec §FR-013, §SC-002] — satisfied by FR-013, SC-002
- [x] CHK018 Is "whole-word, literal" matching defined precisely enough to cover regex-like terms? [Clarity, Spec §FR-015, §Edge Cases, contracts §2.1] — satisfied by FR-015 / spec §Edge Cases / contract §2.1
- [x] CHK019 Is "the card displays only the definition" clarified (no extra keyword content beyond the term label)? [Clarity, Spec §FR-019, contracts §3.1] — satisfied by FR-019 / contract §3.1
- [x] CHK020 Is "focus returns appropriately" clarified as returning to the activating term control? [Clarity, Spec §FR-023, contracts §3.3] — satisfied by FR-023 / contract §3.3
- [x] CHK021 Is the no-artifact outcome for empty/absent keywords defined (no highlight, no affordance, no error, no layout artifact)? [Clarity, Spec §FR-024] — satisfied by FR-024

## Requirement Consistency

- [x] CHK022 Do highlight (FR-013–FR-017) and card (FR-018–FR-025) requirements agree on matching scope? [Consistency, Spec §FR-013–§FR-025] — satisfied by FR-013–FR-025
- [x] CHK023 Do the requirements agree across all three surfaces with no surface-specific exceptions? [Consistency, Spec §FR-025, contracts §3] — satisfied by FR-025 / contract §3
- [x] CHK024 Does the "no layout redesign" non-goal conflict with the highlight/card requirements? [Consistency, Spec §Non-Goals, plan.md §Design] — no conflict; satisfied by spec §Non-Goals / plan.md §Design (additive-only, no layout redesign)

## Acceptance Criteria Quality

- [x] CHK025 Can "100% of present terms highlighted; 0 false highlights" be objectively measured? [Measurability, Spec §SC-002] — satisfied by SC-002 / T024, T026
- [x] CHK026 Can "100% correct card content; 0 cards >3 words" be objectively verified? [Measurability, Spec §SC-003] — satisfied by SC-003 / T033
- [x] CHK027 Can "100% dismiss via all three means; 0 navigation" be objectively measured? [Measurability, Spec §SC-004] — satisfied by SC-004 / T033
- [x] CHK028 Can "100% desktop+mobile; 0 overflow" be objectively verified? [Measurability, Spec §SC-005] — satisfied by SC-005 / T033, T036
- [x] CHK029 Can "0 artifacts for empty/missing keywords" be objectively measured? [Measurability, Spec §SC-006] — satisfied by SC-006 / T044

## Scenario & Edge Case Coverage

- [x] CHK030 Are substring/overlapping terms covered (longer wins)? [Coverage, Spec §Edge Cases, contracts §2.1 M5] — satisfied by spec §Edge Cases / contract §2.1 M5
- [x] CHK031 Are very short/common and regex-like terms covered? [Coverage, Spec §Edge Cases, contracts §2.1 M2/M3] — satisfied by spec §Edge Cases / contract §2.1 M2/M3
- [x] CHK032 Are whitespace/casing variants covered? [Coverage, Spec §Edge Cases, contracts §2.1 M1] — satisfied by spec §Edge Cases / contract §2.1 M1
- [x] CHK033 Is a term spanning a formatting/block boundary covered (no cross-boundary match)? [Coverage, Spec §Edge Cases, contracts §2.1 M6] — satisfied by spec §Edge Cases / contract §2.1 M6
- [x] CHK034 Is a term inside bold covered (both emphasized and highlighted)? [Coverage, Spec §Edge Cases, contracts §2.1 M8] — satisfied by spec §Edge Cases / contract §2.1 M8
- [x] CHK035 Is a term present only in `related` covered (not highlighted)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / FR-014
- [x] CHK036 Are multiple cards / rapid interaction covered (previous closes)? [Coverage, Spec §Edge Cases, §FR-021] — satisfied by spec §Edge Cases / FR-021
- [x] CHK037 Is card overflow at viewport edges on a narrow screen covered? [Coverage, Spec §Edge Cases, contracts §3.4] — satisfied by spec §Edge Cases / contract §3.4
- [x] CHK038 Are Unicode/emoji/RTL in a term or definition covered? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK039 Is the same snack rendered in multiple surfaces covered? [Coverage, Spec §Edge Cases, §FR-025] — satisfied by spec §Edge Cases / FR-025
- [x] CHK040 Are pre-existing (pre-feature) snacks covered? [Coverage, Spec §Edge Cases, §FR-024] — satisfied by spec §Edge Cases / FR-024

## Accessibility & Non-Functional

- [x] CHK041 Is keyboard activation of a highlighted term required (not just pointer)? [Completeness, Spec §FR-023] — satisfied by FR-023
- [x] CHK042 Is focus return on dismissal required for keyboard users? [Completeness, Spec §FR-023] — satisfied by FR-023
- [x] CHK043 Is the highlighted-term control's accessible role/nature specified (activatable control) or explicitly delegated to existing app conventions? [Clarity, contracts §2.2] — satisfied by contract §2.2 / T031 (native `<button>`)
- [x] CHK044 Is the performance expectation for highlighting a large keyword list stated as acceptable/bounded? [Coverage, plan.md §Performance Goals] — satisfied by plan.md §Performance Goals

## Notes

- Check items off as completed: `[x]`
- Add findings inline; link to spec/contract sections where resolved
- Items are numbered sequentially for easy reference
