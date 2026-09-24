# Requirements Quality Checklist: "Create snack" from a Custom Keyword's Generated Meaning (Task 12)

**Purpose**: Validate that the custom-keyword "Create snack" requirements are complete, clear, consistent, and measurable before/while implementing `017-custom-keyword-create-snack`.
**Created**: 2026-09-24
**Feature**: [spec.md](../spec.md)

**Note**: This checklist tests the *requirements writing* (completeness, clarity, consistency, measurability), not the implementation. Every item must be answerable "yes" at implementation completion; mark `[x]` with the evidence (section or requirement ID) that satisfies it.

## Action Availability — Completeness

- [x] CHK001 Is a clearly labeled "Create snack" action on the definition card required after a custom keyword's meaning is generated and displayed? [Completeness, Spec §FR-001, §US1 AS1]
- [x] CHK002 Is the action required to be offered **only** when a valid generated meaning exists (generation finished, definition non-empty)? [Completeness, Spec §FR-002, §SC-001]
- [x] CHK003 Is it required that the action is NOT offered before "Define" completes or when the definition is blank/errored? [Completeness, Spec §FR-002, §Edge Cases, §SC-001]
- [x] CHK004 Is the action required to reuse/relabel the feature-014 "Generate snack" action slot so exactly one snack-creation action is offered? [Completeness, Spec §FR-003, §Clarifications #2]

## Creation & Backbone Integration — Completeness

- [x] CHK005 Is activating "Create snack" required to generate a meaning snack derived from the custom term + generated meaning? [Completeness, Spec §FR-004, §US1 AS3]
- [x] CHK006 Is reuse of the **existing feature-014** generate/persistence/append/mark-generated/backbone flow required? [Completeness, Spec §FR-004, §Context & Scope]
- [x] CHK007 Is the source subtopic required to be the active entry's source subtopic (regular `subtopicId` / meaning `sourceSubtopicId`, Option A)? [Completeness, Spec §FR-005, §Clarifications #1]
- [x] CHK008 Is a loading/in-progress state required while creating? [Completeness, Spec §FR-006, §US1 AS2]
- [x] CHK009 Is single-concurrent-creation / at-most-one-request-on-rapid-repeat required? [Completeness, Spec §FR-007, §US1 AS5]
- [x] CHK010 Is a meaningful, user-safe error required on failure, with no broken/empty/placeholder entry added? [Completeness, Spec §FR-008, §US1 AS4, §SC-003]
- [x] CHK011 Is the created snack required to be added to the backbone as a first-class selectable entry via the feature-014 append path? [Completeness, Spec §FR-009, §US1 AS3]
- [x] CHK012 Is reflecting the already-generated state (no silent duplicate) required when the card is shown again? [Completeness, Spec §FR-010, §US1 AS6]

## Quiz-Free & Visual Distinction — Completeness

- [x] CHK013 Is the created snack required to be quiz-free (no quiz generated/prefetched/stored/offered, no "Start Quiz")? [Completeness, Spec §FR-011, §US2 AS2/AS3]
- [x] CHK014 Is the created snack required to be visually distinct via the feature-014 meaning-snack treatment? [Completeness, Spec §FR-012, §US2 AS1]
- [x] CHK015 Is it required that no surface treats the created snack as a regular quiz-bearing snack? [Completeness, Spec §FR-013, §US2 AS4]

## Persistence & Retrieval — Completeness

- [x] CHK016 Is persistence through the existing feature-014 path required? [Completeness, Spec §FR-014]
- [x] CHK017 Is survival of a page reload and a new session for the same theme required? [Completeness, Spec §FR-014, §US3 AS2, §SC-005]
- [x] CHK018 Is reuse of the existing theme-scoped retrieval path with graceful degradation required? [Completeness, Spec §FR-015, §US3 AS5]
- [x] CHK019 Is it required that no new entity, schema change, or new endpoint is introduced? [Completeness, Spec §FR-016, §Clarifications #5, §SC-007]

## Security — Completeness

- [x] CHK020 Is escaped-text-only rendering of the term, generated meaning, and created content required (no `[innerHTML]`, no `DomSanitizer`)? [Completeness, Spec §FR-017, §US4 AS5]
- [x] CHK021 Is non-execution/non-injection of markup/handlers/dangerous URLs required? [Completeness, Spec §FR-018, §SC-006]
- [x] CHK022 Is preservation of the feature-012/013/014/015/016 sanitization model required unchanged? [Completeness, Spec §FR-019]

## Non-Regression & Scope — Completeness

- [x] CHK023 Is non-regression of the regular snack/quiz/results/progressive-delivery/history flow and the existing E2E suites required? [Completeness, Spec §FR-020, §US5 AS1/AS4, §SC-008]
- [x] CHK024 Is non-regression of feature 014's keyword meaning-snack ("Generate snack") flow required? [Completeness, Spec §FR-021, §US5 AS2]
- [x] CHK025 Is non-regression of feature 016's selection-Define flow required apart from the added action? [Completeness, Spec §FR-022, §US5 AS2]
- [x] CHK026 Is non-regression of feature 013's highlight/definition-card behavior required? [Completeness, Spec §FR-023, §US5 AS2]
- [x] CHK027 Is the scope limited to reuse without changing feature-014's kind/persistence/backbone interleave or the regular flow? [Completeness, Spec §FR-024, §Non-Goals]

## Requirement Clarity

- [x] CHK028 Is "valid generated meaning" made concrete (generation finished successfully and definition non-empty)? [Clarity, Spec §FR-002, §Clarifications #3, §Assumptions]
- [x] CHK029 Is "source subtopic" made concrete for both entry kinds (regular `subtopicId` / meaning `sourceSubtopicId`)? [Clarity, Spec §FR-005, §Clarifications #1, §Key Entities]
- [x] CHK030 Is the action label and slot made concrete ("Create snack"; replaces/hides feature-014 action)? [Clarity, Spec §FR-003, §Clarifications #2]
- [x] CHK031 Is the duplicate guard made concrete (feature-014 unique key `(sourceSubtopicId, term)`, case-insensitive)? [Clarity, Spec §FR-007/§FR-010, §Clarifications #4, §Key Entities]
- [x] CHK032 Is the failure behavior (meaningful error + nothing added) concretely described? [Clarity, Spec §FR-008, §Edge Cases]
- [x] CHK033 Is the reuse of the feature-014 path made concrete (service call, endpoint, retrieval path)? [Clarity, Spec §FR-004/§FR-016, §Context & Scope, §Clarifications #5]

## Requirement Consistency

- [x] CHK034 Do FR-001/FR-002/FR-003 (action availability) agree with the availability edge cases and SC-001? [Consistency, Spec §FR-001–§FR-003, §SC-001]
- [x] CHK035 Do FR-004/FR-005/FR-009 (creation, source subtopic, backbone) agree with the Option A decision and US1? [Consistency, Spec §FR-004/§FR-005/§FR-009, §Clarifications #1]
- [x] CHK036 Do FR-006/FR-007/FR-008 (loading/single-flight/error) agree with SC-002/SC-003? [Consistency, Spec §FR-006–§FR-008, §SC-002/§SC-003]
- [x] CHK037 Do FR-011–FR-013 (quiz-free/distinct) match feature 014's meaning-snack invariants? [Consistency, Spec §FR-011–§FR-013, §Context & Scope]
- [x] CHK038 Do FR-014–FR-016 (persistence/reuse/no schema change) agree with §Clarifications #5 and §Non-Goals? [Consistency, Spec §FR-014–§FR-016, §Clarifications #5]
- [x] CHK039 Do the Non-Goals avoid contradicting the requirements (reuse, no schema change, no feature-014/016 changes)? [Consistency, Spec §Non-Goals]

## Acceptance Criteria Quality

- [x] CHK040 Is SC-001 expressed as 100% action-when-valid / 0 before completion or when invalid? [Measurability, Spec §SC-001]
- [x] CHK041 Is SC-002 expressed as 100% exactly-one-snack / 0 duplicates from repeat or reopen? [Measurability, Spec §SC-002]
- [x] CHK042 Is SC-003 expressed as 100% loading / 100% meaningful-error with 0 broken/empty/placeholder? [Measurability, Spec §SC-003]
- [x] CHK043 Is SC-004 expressed as 100% quiz-free and distinct / 0 quizzes and 0 quiz affordances? [Measurability, Spec §SC-004]
- [x] CHK044 Is SC-005 tied to reload/new-session persistence and 0 data invalidation? [Measurability, Spec §SC-005]
- [x] CHK045 Is SC-006 expressed as 0 executions / 0 injected nodes? [Measurability, Spec §SC-006]
- [x] CHK046 Is SC-007 tied to 0 new entities/schema/endpoints (plan-confirmed)? [Measurability, Spec §SC-007, §Clarifications #5]
- [x] CHK047 Is SC-008 tied to named existing suites and feature-012/013/014/015/016 preservation? [Measurability, Spec §SC-008]

## Edge Case & Scenario Coverage

- [x] CHK048 Is activation before the definition completes covered (not offered / fails meaningfully)? [Coverage, Spec §Edge Cases]
- [x] CHK049 Is a blank/empty generated meaning covered (action not offered, no snack)? [Coverage, Spec §Edge Cases]
- [x] CHK050 Is creation failure / LLM unavailable covered (error, nothing added, retry)? [Coverage, Spec §Edge Cases]
- [x] CHK051 Are rapid repeated activations covered (one in-flight request, no duplicate)? [Coverage, Spec §Edge Cases]
- [x] CHK052 Is a duplicate for the same (source subtopic, term) covered (idempotent existing)? [Coverage, Spec §Edge Cases]
- [x] CHK053 Is the active entry being a meaning snack covered (sourceSubtopicId)? [Coverage, Spec §Edge Cases]
- [x] CHK054 Is the active entry being a regular snack covered (subtopicId)? [Coverage, Spec §Edge Cases]
- [x] CHK055 Is an unresolvable source subtopic covered (meaningful failure, nothing added)? [Coverage, Spec §Edge Cases]
- [x] CHK056 Is a retrieval/reload path unavailable covered (graceful degradation)? [Coverage, Spec §Edge Cases]
- [x] CHK057 Is the created snack alongside zero regular snacks / empty backbone covered? [Coverage, Spec §Edge Cases]
- [x] CHK058 Is the created snack selected in the backbone covered (content, no blank card, no quiz)? [Coverage, Spec §Edge Cases]
- [x] CHK059 Are first/last position and mixed ordering covered (Previous/Next, stable indices)? [Coverage, Spec §Edge Cases]
- [x] CHK060 Are rich formatting, Unicode/emoji/RTL covered? [Coverage, Spec §Edge Cases]
- [x] CHK061 Are injection payloads and escaped entities covered? [Coverage, Spec §Edge Cases]
- [x] CHK062 Is very long generated content covered (stable layout, no overflow break)? [Coverage, Spec §Edge Cases]
- [x] CHK063 Is backend persistence failure during creation covered? [Coverage, Spec §Edge Cases]
- [x] CHK064 Are pre-existing data and unaffected surfaces (results/history/quiz) covered as unchanged? [Coverage, Spec §Edge Cases]

## Scope, Assumptions & Dependencies

- [x] CHK065 Is the Option A source-subtopic decision recorded as confirmed? [Assumption, Spec §Assumptions, §Clarifications #1]
- [x] CHK066 Is the action label/slot decision recorded as confirmed? [Assumption, Spec §Assumptions, §Clarifications #2]
- [x] CHK067 Is the action-availability decision recorded as confirmed? [Assumption, Spec §Assumptions, §Clarifications #3]
- [x] CHK068 Is the dedup-key decision recorded as confirmed? [Assumption, Spec §Assumptions, §Clarifications #4]
- [x] CHK069 Is the no-backend/schema/endpoint-change decision recorded as confirmed? [Assumption, Spec §Assumptions, §Clarifications #5]
- [x] CHK070 Is the reuse of the feature-016 short definition (≤20 words) as the generate argument recorded? [Assumption, Spec §Assumptions, §Context & Scope]
- [x] CHK071 Is the existing stack (Java 21 + Spring Boot, Angular 21, MySQL 8) recorded? [Assumption, Spec §Assumptions]
- [x] CHK072 Is the untrusted-text safe-rendering path recorded? [Assumption, Spec §Assumptions, §FR-017–§FR-019]
- [x] CHK073 Is the LLM-abstraction/externalized-prompt reuse recorded? [Assumption, Spec §Project Structure Impact, §Assumptions]

## Project Structure Impact

- [x] CHK074 Is the frontend change scoped to `snacks.component` and `keyword-definition-card` confirmed? [Completeness, Spec §Project Structure Impact]
- [x] CHK075 Is the backend "no change expected" finding stated as plan-confirmable (not a limiting assumption)? [Completeness, Spec §Project Structure Impact, §Clarifications #5]
- [x] CHK076 Is the no-schema-change impact documented? [Completeness, Spec §Project Structure Impact, §FR-016]
- [x] CHK077 Is the reuse of the feature-014 entity/service/endpoint/retrieval path stated? [Completeness, Spec §Context & Scope, §Project Structure Impact]
- [x] CHK078 Is the preservation of the safe-renderer boundary stated? [Completeness, Spec §Project Structure Impact, §FR-017–§FR-019]

## Notes

- Check items off as completed: `[x]`.
- Add the satisfying section/requirement reference inline.
- Items are numbered sequentially for easy reference; all must be checked at implementation completion.
- All former open items are resolved in the clarification session; no `[NEEDS CLARIFICATION]` markers remain. Implementation-adjacent detail is confined to the `Context & Scope` / `Project Structure Impact` sections, mirroring features 013/014/016.
