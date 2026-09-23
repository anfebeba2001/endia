# UX Checklist: Meaning Snack Generation, Distinct Rendering & No-Quiz Backbone Entry

**Purpose**: Validate the quality, clarity, and completeness of the *generation action, backbone
integration, distinct rendering, no-quiz affordance and reload* display requirements for meaning snacks
— not the implementation. This is a "unit test for the requirements" for how the action and the
meaning-snack entry must appear and behave across surfaces (US1–US3, FR-001–FR-008, FR-010–FR-020).
**Created**: 2026-09-23
**Feature**: [spec.md](../spec.md) — sections §User Story 1, §User Story 2, §User Story 3,
§Requirements (Action on the definition card, Meaning snack content, Backbone integration, No quiz,
Persistence and retrieval), §Edge Cases, §Success Criteria;
[contracts/meaning-snack-contract.md](../contracts/meaning-snack-contract.md) §1–§3

**Note**: Items test whether the requirements are well-written, complete, and verifiable — they are not
runtime test cases.

## Generate Action Requirement Completeness

- [x] CHK001 Is a clearly labeled "Generate snack" action required on the keyword definition card? [Completeness, Spec §FR-001] — satisfied by FR-001
- [x] CHK002 Is a loading/in-progress state for that action required while generating? [Completeness, Spec §FR-003] — satisfied by FR-003
- [x] CHK003 Is a meaningful, user-safe error required on failure? [Completeness, Spec §FR-004] — satisfied by FR-004
- [x] CHK004 Is it required that no broken/empty/placeholder entry is added on failure? [Completeness, Spec §FR-004, §SC-003] — satisfied by FR-004, SC-003
- [x] CHK005 Is single-concurrent-generation / no-duplicate-on-repeat required (in-flight guard)? [Completeness, Spec §FR-005, §SC-002] — satisfied by FR-005, SC-002
- [x] CHK006 Is reflecting an already-generated meaning snack (`done` state, no silent duplicate) required? [Completeness, Spec §FR-006] — satisfied by FR-006
- [x] CHK007 Is it required that the action appears on the keyword definition card on **all three surfaces** (snacks, results, history)? [Completeness, Spec §SC-001, plan.md §Exact integration call sites] — satisfied by SC-001 / plan.md integration call sites / T031, T032

## Meaning Snack Content Requirement Completeness

- [x] CHK008 Is the meaning snack required to use the same snack reading structure (concept/metaphor/why-it-matters/related)? [Completeness, Spec §FR-007] — satisfied by FR-007
- [x] CHK009 Is retention of the source term+definition required so the origin is visible? [Completeness, Spec §FR-008] — satisfied by FR-008
- [x] CHK010 Is it stated that meaning content carries **no nested keywords** (no highlight controls / nested card)? [Completeness, Spec §Clarifications #3, plan.md §Open Decisions #2] — satisfied by plan.md §Open Decisions #2 / contract §3.3

## Backbone Integration & Distinct Rendering Requirement Completeness

- [x] CHK011 Is adding the meaning snack to the main topics backbone as a selectable entry required? [Completeness, Spec §FR-010] — satisfied by FR-010
- [x] CHK012 Is mixed rendering with regular snacks without corrupting ordering/selection/Previous-Next/delivery required? [Completeness, Spec §FR-011] — satisfied by FR-011
- [x] CHK013 Is a distinct visual treatment (colour **and/or** an always-present badge) required, in light and dark themes? [Completeness, Spec §FR-012, §SC-004] — satisfied by FR-012, SC-004 / plan.md §Design
- [x] CHK014 Is it required that selecting a meaning snack never renders a blank card? [Completeness, Spec §FR-013] — satisfied by FR-013
- [x] CHK015 Is the interleave placement (immediately after the source subtopic's regular entry; unmatched appended at the end) required? [Completeness, Spec §Clarifications #3, plan.md §Design] — satisfied by plan.md §Design / contract §3.2 / T036

## No-Quiz Affordance Requirement Completeness

- [x] CHK016 Is it required that no quiz is generated/prefetched/stored for a meaning snack? [Completeness, Spec §FR-014] — satisfied by FR-014
- [x] CHK017 Is it required that no quiz entry or "Start Quiz" action is offered for a meaning snack? [Completeness, Spec §FR-015] — satisfied by FR-015
- [x] CHK018 Is it required that a meaning snack is never treated as a regular quiz-bearing snack by any surface (batch/quiz/results/XP/history)? [Completeness, Spec §FR-016] — satisfied by FR-016

## Persistence & Reload Requirement Completeness

- [x] CHK019 Is backend persistence of a generated meaning snack required? [Completeness, Spec §FR-017] — satisfied by FR-017
- [x] CHK020 Is survival across page reload and a new session for the same theme required? [Completeness, Spec §FR-018] — satisfied by FR-018
- [x] CHK021 Is a theme-scoped retrieval path for persisted meaning snacks required? [Completeness, Spec §FR-020, §Clarifications #2] — satisfied by FR-020 / contract §2
- [x] CHK022 Is graceful degradation when retrieval fails/returns nothing required (no crash, no broken entries, regular snacks unaffected)? [Completeness, Spec §FR-020, US3 AS5] — satisfied by FR-020 / US3 AS5

## Requirement Clarity

- [x] CHK023 Is "main topics backbone" given a concrete working interpretation (the `/snacks` reading surface)? [Clarity, Spec §Clarifications #1] — satisfied by Spec §Clarifications #1 / contract §3
- [x] CHK024 Is "visually distinct" tied to an observable outcome (colour token + always-present text badge, WCAG 1.4.1)? [Clarity, Spec §FR-012, §SC-004, contract §3.3] — satisfied by FR-012, SC-004 / contract §3.3
- [x] CHK025 Is the loading/done/error state of the action concretely described (spinner + label, disabled done, inline `role="alert"`)? [Clarity, plan.md §Design] — satisfied by plan.md §Design / T029
- [x] CHK026 Is the failure behaviour (meaningful error + no entry) concretely described? [Clarity, Spec §FR-004, §Edge Cases] — satisfied by FR-004 / spec §Edge Cases
- [x] CHK027 Is "remains present after reload/new session" tied to a verifiable outcome (theme-scoped listing re-read)? [Clarity, Spec §FR-018, §SC-006] — satisfied by FR-018, SC-006 / contract §2
- [x] CHK028 Is the idempotent response (`alreadyExisted: true`, HTTP 200) clarified as the "already-generated state" reflection? [Clarity, Spec §FR-006, plan.md §Open Decisions #3] — satisfied by FR-006 / contract §1.3

## Requirement Consistency

- [x] CHK029 Do FR-001/FR-002 (action → generation) agree with FR-017/FR-018 (persistence → reload)? [Consistency, Spec §FR-001–§FR-002, §FR-017–§FR-018] — satisfied by FR-001–FR-002, FR-017–FR-018
- [x] CHK030 Do FR-010/FR-011 (backbone integration) agree with FR-013 (never a blank card)? [Consistency, Spec §FR-010–§FR-013] — satisfied by FR-010–FR-013
- [x] CHK031 Do FR-014–FR-016 (no quiz) agree with §Non-Goals and US2? [Consistency, Spec §FR-014–§FR-016, §Non-Goals] — satisfied by FR-014–FR-016 / §Non-Goals
- [x] CHK032 Does the "no layout redesign" non-goal conflict with the distinct-treatment/mixed-entry requirements? [Consistency, Spec §Non-Goals, plan.md §Design] — no conflict; satisfied by spec §Non-Goals / plan.md §Design (additive-only)
- [x] CHK033 Is the card action consistent across the three hosting surfaces (same loading/error/done state)? [Consistency, plan.md §Exact integration call sites] — satisfied by plan.md integration call sites / T031, T032, T036

## Acceptance Criteria Quality

- [x] CHK034 Can "100% of cards show the action; 0 render failures" be objectively measured? [Measurability, Spec §SC-001] — satisfied by SC-001 / T011, T012
- [x] CHK035 Can "100% one-entry-per-activation; 0 duplicates" be objectively verified? [Measurability, Spec §SC-002] — satisfied by SC-002 / T010, T012
- [x] CHK036 Can "100% loading state; 0 broken entries on failure" be objectively measured? [Measurability, Spec §SC-003] — satisfied by SC-003 / T010, T012
- [x] CHK037 Can "100% meaning snacks visually distinct; 0 indistinguishable" be objectively verified? [Measurability, Spec §SC-004] — satisfied by SC-004 / T033, T038
- [x] CHK038 Can "0 quizzes generated/stored/offered for meaning snacks" be objectively measured? [Measurability, Spec §SC-005] — satisfied by SC-005 / T010, T011, T033
- [x] CHK039 Can "100% survival after reload/new session; 0 lost/corrupted" be objectively verified? [Measurability, Spec §SC-006] — satisfied by SC-006 / T039, T040, T041

## Scenario & Edge Case Coverage

- [x] CHK040 Is generation failure / LLM unavailable covered (error, no entry, retry possible)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T012
- [x] CHK041 Are repeated/rapid activations covered (one in-flight request, no duplicate)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T012
- [x] CHK042 Is a duplicate meaning snack for the same keyword covered (reflected, not duplicated)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T010, T012
- [x] CHK043 Is the same term appearing in more than one snack/subtopic covered (scoped, predictable)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T010
- [x] CHK044 Is a blank/missing definition or absent keyword at activation time covered (not offered / fails meaningfully)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T010
- [x] CHK045 Is an unavailable retrieval/reload path covered (graceful, regular snacks unaffected)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T040
- [x] CHK046 Is an empty/zero-regular-snack backbone covered (meaning entry still renders, empty-state not falsely triggered)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T036
- [x] CHK047 Is selecting a meaning snack covered (content shown, never blank, no quiz)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T033
- [x] CHK048 Are first/last-position navigation and mixed ordering covered (Previous/Next + disabled states)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T034
- [x] CHK049 Is rich formatting in generated content covered (renders through the safe renderer, not corrupted)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T045
- [x] CHK050 Are Unicode/emoji/RTL covered (display and interaction still work)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T043
- [x] CHK051 Is very long generated content covered (layout stable, no overflow break)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T038
- [x] CHK052 Is backend persistence failure during generation covered (error, nothing added, later retry succeeds)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T010, T012
- [x] CHK053 Are pre-existing data and unaffected surfaces (results recap, history, quiz flow) covered? [Coverage, Spec §Edge Cases, §FR-016, §FR-024] — satisfied by spec §Edge Cases / T042

## Accessibility & Non-Functional

- [x] CHK054 Is the distinct treatment required to not be colour-only (always-present text badge) for WCAG 1.4.1? [Completeness, Spec §FR-012, contract §3.3] — satisfied by FR-012 / contract §3.3 / T038
- [x] CHK055 Is the action's accessible state communicated (disabled while loading, `role="alert"` on error)? [Clarity, plan.md §Design] — satisfied by plan.md §Design / T029
- [x] CHK056 Is the performance expectation for the listing/interleave stated as bounded (one indexed query, one linear pass)? [Coverage, plan.md §Performance Goals] — satisfied by plan.md §Performance Goals

## Notes

- Check items off as completed: `[x]`
- Add findings inline; link to spec/contract sections where resolved
- Items are numbered sequentially for easy reference; all must be checked at implementation completion
