# UX Checklist: Custom "Create snack" Action, Loading State & Backbone Placement

**Purpose**: Validate the quality, clarity, and completeness of the *action availability, creation UX,
backbone placement, visual distinction and persistence* requirements for the custom-keyword
"Create snack" flow — not the implementation. This is a "unit test for the requirements" for how the
action must appear and behave (US1–US3, FR-001–FR-016, SC-001–SC-005, SC-007).
**Created**: 2026-09-24
**Feature**: [spec.md](../spec.md) — sections §User Story 1, §User Story 2, §User Story 3,
§Requirements (Action availability, Creation and backbone integration, Quiz-free and visual
distinction, Persistence and retrieval), §Edge Cases, §Success Criteria

**Note**: Items test whether the requirements are well-written, complete, and verifiable — they are not
runtime test cases.

## Action Availability Requirement Completeness

- [x] CHK001 Is a clearly labeled "Create snack" action required after a custom keyword's meaning is generated and displayed? [Completeness, Spec §FR-001, §US1 AS1] — satisfied by FR-001
- [x] CHK002 Is it required that the action is offered only when a valid generated meaning exists (generation finished, definition non-empty)? [Completeness, Spec §FR-002, §SC-001] — satisfied by FR-002
- [x] CHK003 Is it required that the action is **not** offered before "Define" completes or when the definition is blank/errored? [Completeness, Spec §FR-002, §Edge Cases] — satisfied by FR-002 / spec §Edge Cases
- [x] CHK004 Is it required that the action reuses/relabels the feature-014 action slot (exactly one snack-creation action per card)? [Completeness, Spec §FR-003, §Clarifications #2] — satisfied by FR-003 / Spec §Clarifications #2

## Creation UX Requirement Completeness

- [x] CHK005 Is activating "Create snack" required to request creation of a meaning snack from the custom term + generated meaning? [Completeness, Spec §FR-004, §US1 AS3] — satisfied by FR-004
- [x] CHK006 Is reuse of the existing feature-014 generate/persistence/append flow required? [Completeness, Spec §FR-004, §Context & Scope] — satisfied by FR-004 / Spec §Context & Scope
- [x] CHK007 Is a loading/in-progress state required while creating? [Completeness, Spec §FR-006, §US1 AS2] — satisfied by FR-006
- [x] CHK008 Is single-concurrent-creation / at-most-one-request-on-rapid-repeat required (in-flight guard)? [Completeness, Spec §FR-007, §SC-002] — satisfied by FR-007, SC-002
- [x] CHK009 Is a meaningful, user-safe error required on failure? [Completeness, Spec §FR-008, §US1 AS4] — satisfied by FR-008
- [x] CHK010 Is it required that no broken/empty/placeholder entry is added on failure? [Completeness, Spec §FR-008, §SC-003] — satisfied by FR-008, SC-003
- [x] CHK011 Is the created snack required to be added to the backbone as a selectable/readable first-class entry? [Completeness, Spec §FR-009, §US1 AS3] — satisfied by FR-009
- [x] CHK012 Is reflecting the already-generated state (no silent duplicate) required on re-open? [Completeness, Spec §FR-010, §US1 AS6] — satisfied by FR-010

## Backbone Placement Requirement Completeness

- [x] CHK013 Is the source subtopic required to be the active entry's source subtopic (regular `subtopicId`)? [Completeness, Spec §FR-005, §Clarifications #1] — satisfied by FR-005
- [x] CHK014 Is the source subtopic required to be the active meaning entry's `sourceSubtopicId`? [Completeness, Spec §FR-005, §Clarifications #1] — satisfied by FR-005
- [x] CHK015 Is it required that placement reuses the feature-014 backbone interleave (next to the read snack) without corrupting ordering/selection? [Completeness, Spec §FR-004/§FR-009, §Edge Cases] — satisfied by FR-004/FR-009 / spec §Edge Cases
- [x] CHK016 Is it required that the created snack is never a blank card and does not imply a quiz when selected? [Completeness, Spec §US2 AS5, §Edge Cases] — satisfied by US2 AS5 / spec §Edge Cases

## Visual Distinction & Quiz-Free Requirement Completeness

- [x] CHK017 Is the created snack required to be visually distinct via the feature-014 meaning-snack treatment? [Completeness, Spec §FR-012, §US2 AS1] — satisfied by FR-012
- [x] CHK018 Is it required that no quiz is generated/prefetched/stored for the created snack? [Completeness, Spec §FR-011, §US2 AS2] — satisfied by FR-011
- [x] CHK019 Is it required that no "Start Quiz" (or any quiz) action is offered for the created snack? [Completeness, Spec §FR-011, §US2 AS3] — satisfied by FR-011
- [x] CHK020 Is it required that a regular snack keeps its existing quiz affordance/badge/behavior? [Completeness, Spec §US2 AS4, §FR-020] — satisfied by US2 AS4, FR-020
- [x] CHK021 Is it required that no surface treats the created snack as a regular quiz-bearing snack? [Completeness, Spec §FR-013, §US2 AS4] — satisfied by FR-013

## Persistence & Reload Requirement Completeness

- [x] CHK022 Is persistence through the existing feature-014 path required? [Completeness, Spec §FR-014] — satisfied by FR-014
- [x] CHK023 Is survival of a page reload and a new session for the same theme required? [Completeness, Spec §FR-014, §US3 AS2] — satisfied by FR-014
- [x] CHK024 Is reuse of the theme-scoped retrieval path with graceful degradation required? [Completeness, Spec §FR-015, §US3 AS5] — satisfied by FR-015
- [x] CHK025 Is it required that the loaded snack keeps its distinct treatment and no-quiz behavior? [Completeness, Spec §US3 AS3, §SC-004] — satisfied by US3 AS3, SC-004
- [x] CHK026 Is it required that pre-existing data remains valid (no schema change)? [Completeness, Spec §FR-016, §US3 AS4, §SC-005] — satisfied by FR-016, US3 AS4, SC-005

## Requirement Clarity

- [x] CHK027 Is "valid generated meaning" made concrete (generation finished successfully and definition non-empty)? [Clarity, Spec §FR-002, §Clarifications #3] — satisfied by FR-002 / Spec §Clarifications #3
- [x] CHK028 Is the action label and slot made concrete ("Create snack"; replaces/hides feature-014 action)? [Clarity, Spec §FR-003, §Clarifications #2] — satisfied by FR-003 / Spec §Clarifications #2
- [x] CHK029 Is the loading state made concrete (per-action in-progress state, disabled/guarded)? [Clarity, Spec §FR-006/§FR-007] — satisfied by FR-006/FR-007
- [x] CHK030 Is the failure behavior concretely described (meaningful error, no entry added)? [Clarity, Spec §FR-008, §Edge Cases] — satisfied by FR-008 / spec §Edge Cases
- [x] CHK031 Is the source-subtopic rule made concrete for both entry kinds? [Clarity, Spec §FR-005, §Clarifications #1] — satisfied by FR-005 / Spec §Clarifications #1
- [x] CHK032 Is the duplicate guard made concrete (feature-014 unique key, case-insensitive)? [Clarity, Spec §FR-007/§FR-010, §Clarifications #4] — satisfied by FR-007/FR-010 / Spec §Clarifications #4

## Requirement Consistency

- [x] CHK033 Do FR-001/FR-002/FR-003 (availability) agree with the availability edge cases and SC-001? [Consistency, Spec §FR-001–§FR-003, §SC-001] — satisfied by FR-001–FR-003, SC-001
- [x] CHK034 Do FR-004/FR-005/FR-009 (creation/placement/backbone) agree with the Option A decision? [Consistency, Spec §FR-004/§FR-005/§FR-009, §Clarifications #1] — satisfied by FR-004/FR-005/FR-009 / Spec §Clarifications #1
- [x] CHK035 Do FR-006/FR-007/FR-008 (loading/single-flight/error) agree with SC-002/SC-003? [Consistency, Spec §FR-006–§FR-008, §SC-002/§SC-003] — satisfied by FR-006–FR-008, SC-002/SC-003
- [x] CHK036 Do FR-011–FR-013 (quiz-free/distinct) match feature 014's invariants? [Consistency, Spec §FR-011–§FR-013, §Context & Scope] — satisfied by FR-011–FR-013
- [x] CHK037 Do FR-014–FR-016 (persistence/reuse) agree with the no-schema-change non-goal? [Consistency, Spec §FR-014–§FR-016, §Non-Goals] — satisfied by FR-014–FR-016 / spec §Non-Goals

## Acceptance Criteria Quality

- [x] CHK038 Can "100% action-when-valid; 0 before completion or when invalid" be objectively measured? [Measurability, Spec §SC-001] — satisfied by SC-001
- [x] CHK039 Can "100% exactly-one-snack; 0 duplicates from repeat/reopen" be objectively verified? [Measurability, Spec §SC-002] — satisfied by SC-002
- [x] CHK040 Can "100% loading; 100% meaningful error with 0 broken/empty/placeholder" be objectively measured? [Measurability, Spec §SC-003] — satisfied by SC-003
- [x] CHK041 Can "100% quiz-free and distinct; 0 quizzes and 0 quiz affordances" be objectively verified? [Measurability, Spec §SC-004] — satisfied by SC-004
- [x] CHK042 Can "100% survive reload/new session; 0 data invalidated" be objectively measured? [Measurability, Spec §SC-005] — satisfied by SC-005
- [x] CHK043 Can "0 new entities/schema/endpoints" be objectively confirmed by the plan? [Measurability, Spec §SC-007, §Clarifications #5] — satisfied by SC-007 / Spec §Clarifications #5

## Scenario & Edge Case Coverage

- [x] CHK044 Is activation before the definition completes covered (not offered / fails meaningfully)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK045 Is a blank/empty generated meaning covered (action not offered, no snack)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK046 Is creation failure / LLM unavailable covered (error, nothing added, retry)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK047 Are rapid repeated activations covered (one in-flight request, no duplicate)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK048 Is a duplicate for the same (source subtopic, term) covered (idempotent existing)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK049 Is the active entry being a meaning snack covered (sourceSubtopicId)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK050 Is the active entry being a regular snack covered (subtopicId)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK051 Is an unresolvable source subtopic covered (meaningful failure, nothing added)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK052 Is a retrieval/reload path unavailable covered (graceful degradation)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK053 Is the created snack alongside zero regular snacks / empty backbone covered? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK054 Is the created snack selected in the backbone covered (content, no blank card, no quiz)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK055 Are first/last position and mixed ordering covered (Previous/Next, stable indices)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK056 Are rich formatting, Unicode/emoji/RTL covered? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK057 Is very long generated content covered (stable layout, no overflow break)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK058 Is backend persistence failure during creation covered? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK059 Are pre-existing data and unaffected surfaces (results/history/quiz) covered as unchanged? [Coverage, Spec §Edge Cases, §FR-020] — satisfied by spec §Edge Cases / FR-020

## Accessibility & Non-Functional

- [x] CHK060 Is the action required to communicate its disabled/loading state accessibly (consistent with the feature-014 action states)? [Completeness, Spec §FR-006, §Project Structure Impact] — satisfied by FR-006 / Spec §Project Structure Impact
- [x] CHK061 Is the error required to be user-safe and non-technical (reusing the existing error mechanism)? [Clarity, Spec §FR-008, §Assumptions] — satisfied by FR-008 / spec §Assumptions
- [x] CHK062 Is the created snack's rendering required to reuse the existing safe renderer and design tokens? [Coverage, Spec §FR-017, §Non-Goals] — satisfied by FR-017 / spec §Non-Goals

## Notes

- Check items off as completed: `[x]`
- Add findings inline; link to spec sections where resolved
- Items are numbered sequentially for easy reference; all must be checked at implementation completion
