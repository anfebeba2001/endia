# UX Checklist: Selection-Define Trigger, Transient Affordance & Definition-Card Display

**Purpose**: Validate the quality, clarity, and completeness of the *selection trigger, transient
affordance, generation UX, definition-card display, reachability and scope* requirements for the
selection-Define flow — not the implementation. This is a "unit test for the requirements" for how the
trigger, affordance and card must appear and behave (US1–US4, FR-001–FR-009, FR-016–FR-018, SC-001–
SC-003, SC-006).
**Created**: 2026-09-24
**Feature**: [spec.md](../spec.md) — sections §User Story 1, §User Story 2, §User Story 4,
§Requirements (Selection and trigger, Generation, Reachability and scope), §Edge Cases,
§Success Criteria; [contracts/definition-contract.md](../contracts/definition-contract.md) §3;
[e2e-test-plan.md](../e2e-test-plan.md)

**Note**: Items test whether the requirements are well-written, complete, and verifiable — they are not
runtime test cases.

## Selection Trigger Requirement Completeness

- [x] CHK001 Is a hold timer of approximately 2 seconds required before the affordance appears? [Completeness, Spec §FR-001, §US1 AS1] — satisfied by FR-001
- [x] CHK002 Is it required that **no** affordance appears before ~2 seconds elapse? [Completeness, Spec §FR-001, §US1 AS2] — satisfied by FR-001 / e2e-test-plan.md §1.2
- [x] CHK003 Is cancellation of the timer on selection change/clear before ~2 seconds required? [Completeness, Spec §FR-002, §US1 AS3] — satisfied by FR-002 / e2e-test-plan.md §1.3
- [x] CHK004 Is it required that releasing the selection without activating shows no generation and leaves reading unaffected? [Completeness, Spec §US1 AS5, §Edge Cases] — satisfied by US1 AS5 / e2e-test-plan.md §1.4
- [x] CHK005 Is the affordance required to be transient (dismissed on selection clear, click elsewhere, Escape)? [Completeness, Spec §FR-003, §Edge Cases] — satisfied by FR-003 / e2e-test-plan.md §1.5

## Affordance Presentation Requirement Completeness

- [x] CHK006 Is a clearly labeled "Define" affordance required to appear anchored to the selection? [Completeness, Spec §FR-001, §US1 AS1] — satisfied by FR-001
- [x] CHK007 Is the affordance required to be selection-anchored and viewport-clamped (no horizontal overflow, flip above when near the bottom)? [Completeness, Spec §FR-003, plan.md §Design] — satisfied by FR-003 / plan.md §Design / T024, T025
- [x] CHK008 Is it required that the affordance does not interfere with the single-definition-card rule? [Completeness, Spec §FR-003, §US4 AS4] — satisfied by FR-003 / e2e-test-plan.md §4.3
- [x] CHK009 Is it required that the affordance does not break existing highlighted-keyword click behavior? [Completeness, Spec §FR-003, §US4 AS3] — satisfied by FR-003 / e2e-test-plan.md §4.2
- [x] CHK010 Is it required that the affordance be reachable on touch/mobile without horizontal overflow? [Completeness, Spec §Edge Cases] — satisfied by spec §Edge Cases / T025

## Generation UX Requirement Completeness

- [x] CHK011 Is activating "Define" required to request a short definition for the selected term? [Completeness, Spec §FR-004, §US2 AS1] — satisfied by FR-004
- [x] CHK012 Is a loading/in-progress state required while generating? [Completeness, Spec §FR-006, §US2 AS1] — satisfied by FR-006 / e2e-test-plan.md §2.1
- [x] CHK013 Is single-concurrent-generation / at-most-one-request-on-rapid-repeat required (in-flight guard)? [Completeness, Spec §FR-007, §SC-003] — satisfied by FR-007, SC-003 / e2e-test-plan.md §2.2
- [x] CHK014 Is a meaningful, user-safe error required on failure? [Completeness, Spec §FR-009, §US2 AS4] — satisfied by FR-009 / e2e-test-plan.md §2.3
- [x] CHK015 Is it required that no broken/empty/placeholder content is added on failure? [Completeness, Spec §FR-009, §SC-002] — satisfied by FR-009, SC-002 / e2e-test-plan.md §2.3
- [x] CHK016 Is opening the existing definition card with the generated meaning required on success? [Completeness, Spec §FR-008, §US2 AS3] — satisfied by FR-008 / e2e-test-plan.md §2.1

## Definition Card Display Requirement Completeness

- [x] CHK017 Is it required that the generated definition is shown in the **existing single** definition card (no new card)? [Completeness, Spec §FR-008, §Non-Goals] — satisfied by FR-008 / spec §Non-Goals
- [x] CHK018 Is the card required to show the selected term and the generated definition via escaped interpolation only? [Completeness, Spec §FR-013, contract §3.3] — satisfied by FR-013 / contract §3.3
- [x] CHK019 Is the single-definition-card rule required when a new selection/keyword is opened while a card is shown? [Completeness, Spec §FR-003, §Edge Cases, §US4 AS4] — satisfied by FR-003 / e2e-test-plan.md §4.3
- [x] CHK020 Is it required that the custom-define card offers **no** "Generate snack" action (Task 12 out of scope)? [Completeness, Spec §FR-019, §Non-Goals, plan.md §Open Decisions #3] — satisfied by FR-019 / plan.md §Open Decisions #3 / T032
- [x] CHK021 Is the card's existing open/dismiss/focus behaviour required to be preserved? [Completeness, Spec §FR-018, §Non-Goals] — satisfied by FR-018 / spec §Non-Goals

## Reachability & Scope Requirement Completeness

- [x] CHK022 Is reachability via pointer text selection required? [Completeness, Spec §FR-016, §US4 AS1] — satisfied by FR-016 / e2e-test-plan.md §1.1
- [x] CHK023 Is reachability via programmatic/keyboard selection (`document.getSelection()`) required? [Completeness, Spec §FR-016, §US4 AS2] — satisfied by FR-016 / e2e-test-plan.md §4.1
- [x] CHK024 Is the trigger required to be offered **only** on the snack reading page reading content? [Completeness, Spec §FR-017, §US4 AS6] — satisfied by FR-017 / e2e-test-plan.md §4.4
- [x] CHK025 Is it required that the results recap and read-only history surfaces offer no trigger and render unchanged? [Completeness, Spec §FR-017, §US5 AS3, §SC-006] — satisfied by FR-017, SC-006 / e2e-test-plan.md §4.4
- [x] CHK026 Is it required that navigation, quiz start and existing interactions are unchanged? [Completeness, Spec §US4 AS5, §FR-018] — satisfied by US4 AS5, FR-018

## Requirement Clarity

- [x] CHK027 Is "approximately 2 seconds" given a concrete working interpretation (threshold; not before it)? [Clarity, Spec §Assumptions, §FR-001] — satisfied by spec §Assumptions / FR-001
- [x] CHK028 Is the affordance presentation concretely described (transient, selection-anchored, viewport-clamped, static "Define" label)? [Clarity, Spec §FR-003, plan.md §Design] — satisfied by FR-003 / plan.md §Design / T023–T025
- [x] CHK029 Is the loading state concretely described (spinner + "Defining…", disabled affordance)? [Clarity, plan.md §Design, contract §3.2] — satisfied by plan.md §Design / contract §3.2 / T030
- [x] CHK030 Is the failure behaviour concretely described (inline `role="alert"`, no definition paragraph)? [Clarity, Spec §FR-009, plan.md §Design] — satisfied by FR-009 / plan.md §Design / T030
- [x] CHK031 Is "snack reading page only" made concrete (`snacks.component` reading content)? [Clarity, Spec §FR-017, §Clarifications #3] — satisfied by FR-017 / Spec §Clarifications #3
- [x] CHK032 Is the bounds feedback concretely described (blank/oversized → meaningful error, no request)? [Clarity, Spec §FR-011, §SC-005] — satisfied by FR-011, SC-005 / e2e-test-plan.md §3

## Requirement Consistency

- [x] CHK033 Do FR-001/FR-002 (hold/cancel) agree with FR-003 (transient, selection-anchored)? [Consistency, Spec §FR-001–§FR-003] — satisfied by FR-001–FR-003
- [x] CHK034 Do FR-006/FR-007 (loading/single-flight) agree with FR-009 (safe failure)? [Consistency, Spec §FR-006–§FR-009] — satisfied by FR-006–FR-009
- [x] CHK035 Do FR-008 (card shows meaning) and FR-003/FR-018 (single-card rule, keyword clicks) agree? [Consistency, Spec §FR-003, §FR-008, §FR-018] — satisfied by FR-003/FR-008/FR-018
- [x] CHK036 Does the custom-card display-only behaviour agree with the Task-12 non-goal (FR-019)? [Consistency, Spec §FR-019, §Non-Goals] — satisfied by FR-019 / spec §Non-Goals
- [x] CHK037 Is the flow consistent across pointer and programmatic selection (same affordance, same card)? [Consistency, Spec §FR-016, §SC-006] — satisfied by FR-016, SC-006 / e2e-test-plan.md §4.1

## Acceptance Criteria Quality

- [x] CHK038 Can "100% of held selections show the affordance; 0 before 2s / after change-clear" be objectively measured? [Measurability, Spec §SC-001] — satisfied by SC-001 / e2e-test-plan.md §1.1–1.3
- [x] CHK039 Can "100% loading→meaning or meaningful error; 0 broken/empty/placeholder" be objectively verified? [Measurability, Spec §SC-002] — satisfied by SC-002 / e2e-test-plan.md §2.1, §2.3
- [x] CHK040 Can "0 duplicate requests; at most one in-flight" be objectively measured? [Measurability, Spec §SC-003] — satisfied by SC-003 / e2e-test-plan.md §2.2
- [x] CHK041 Can "100% pointer + programmatic reachable; 0 triggers off the snacks page; 0 keyword-click/single-card regressions" be objectively verified? [Measurability, Spec §SC-006] — satisfied by SC-006 / e2e-test-plan.md §4.1–4.4

## Scenario & Edge Case Coverage

- [x] CHK042 Are selection changed/cleared before 2s and released before 2s covered? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / e2e-test-plan.md §1.3–1.4
- [x] CHK043 Are blank/whitespace-only and oversized (>10 tokens) selections covered (error, no request)? [Coverage, Spec §Edge Cases, §SC-005] — satisfied by spec §Edge Cases / e2e-test-plan.md §3.1–3.2
- [x] CHK044 Are rapid repeated activations covered (one in-flight request, one card)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / e2e-test-plan.md §2.2
- [x] CHK045 Is generation failure / LLM unavailable covered (error, nothing added, retry possible)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / e2e-test-plan.md §2.3
- [x] CHK046 Is a selection spanning multiple elements/blocks covered (plain-text normalization, usable anchoring)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK047 Is a selection inside/overlapping an existing highlighted keyword covered? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / e2e-test-plan.md §4.2
- [x] CHK048 Is a selection entirely outside the reading content covered (no affordance)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T036
- [x] CHK049 Is a selection on the results recap / read-only history surface covered (no affordance)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / e2e-test-plan.md §4.4
- [x] CHK050 Is a keyboard/programmatic (`document.getSelection()`) selection covered? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / e2e-test-plan.md §4.1
- [x] CHK051 Is touch/mobile selection and horizontal overflow covered? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T025
- [x] CHK052 Is affordance dismissal (selection clear, click elsewhere, Escape) covered? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / e2e-test-plan.md §1.5
- [x] CHK053 Is the card already open when a new selection is held covered (replace, one card)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / e2e-test-plan.md §4.3
- [x] CHK054 Are Unicode/emoji/RTL covered (display and interaction still work)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK055 Is a very long generated meaning covered (stable layout, no overflow break)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T031
- [x] CHK056 Is backend unavailable/timeout covered (meaningful error, nothing added)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / e2e-test-plan.md §2.3
- [x] CHK057 Are pre-existing behaviors (highlight, card open/dismiss/focus, meaning-snack, rich formatting) covered as unchanged? [Coverage, Spec §Edge Cases, §FR-018] — satisfied by spec §Edge Cases / FR-018 / T038

## Accessibility & Non-Functional

- [x] CHK058 Is the affordance required to have an accessible label (e.g. `aria-label="Define selected text"`)? [Completeness, plan.md §Design] — satisfied by plan.md §Design / T024
- [x] CHK059 Is the action's accessible state communicated (disabled/spinner while loading, `role="alert"` on error)? [Clarity, plan.md §Design] — satisfied by plan.md §Design / T030
- [x] CHK060 Is the performance expectation for selection detection stated as bounded (one `selectionchange` handler, one debounce timer)? [Coverage, plan.md §Performance Goals] — satisfied by plan.md §Performance Goals / T022

## Notes

- Check items off as completed: `[x]`
- Add findings inline; link to spec/contract sections where resolved
- Items are numbered sequentially for easy reference; all must be checked at implementation completion
