# Requirements Quality Checklist: "Generate snack" from a Keyword Definition into the Main Topics Backbone (No Quiz)

**Purpose**: Validate that the meaning-snack requirements are complete, clear, consistent, and measurable before/while implementing `014-meaning-snacks`.
**Created**: 2026-09-23
**Feature**: [spec.md](../spec.md)

**Note**: This checklist tests the *requirements writing* (completeness, clarity, consistency, measurability), not the implementation. Every item must be answerable "yes" at implementation completion; mark `[x]` with the evidence (section or requirement ID) that satisfies it.

## Action on the Definition Card — Completeness

- [x] CHK001 Is a clearly labeled "Generate snack" action required on the keyword definition card? [Completeness, Spec §FR-001]
- [x] CHK002 Is generation of a meaning snack derived from the keyword `term`+`definition` required? [Completeness, Spec §FR-002]
- [x] CHK003 Is a loading/in-progress state required while generating? [Completeness, Spec §FR-003]
- [x] CHK004 Is a meaningful error required on failure? [Completeness, Spec §FR-004]
- [x] CHK005 Is it required that no broken/empty/placeholder entry is added on failure? [Completeness, Spec §FR-004]
- [x] CHK006 Is single-concurrent-generation / no-duplicate-on-repeat required? [Completeness, Spec §FR-005]
- [x] CHK007 Is reflecting an already-generated meaning snack (no silent duplicate) required? [Completeness, Spec §FR-006]

## Meaning Snack Content — Completeness

- [x] CHK008 Is the meaning snack required to use the same snack reading structure (concept/metaphor/why-it-matters/related)? [Completeness, Spec §FR-007]
- [x] CHK009 Is retention of the source term+definition required so the origin is visible? [Completeness, Spec §FR-008]
- [x] CHK010 Is generated content required to be treated as untrusted and rendered safely? [Completeness, Spec §FR-009]

## Backbone Integration & Visual Distinction — Completeness

- [x] CHK011 Is adding the meaning snack to the main topics backbone as a selectable entry required? [Completeness, Spec §FR-010]
- [x] CHK012 Is mixed rendering with regular snacks without corrupting ordering/selection/navigation required? [Completeness, Spec §FR-011]
- [x] CHK013 Is a distinct visual treatment (color and/or badge) required? [Completeness, Spec §FR-012]
- [x] CHK014 Is it required that selecting a meaning snack never renders a blank card? [Completeness, Spec §FR-013]

## No-Quiz Invariant — Completeness

- [x] CHK015 Is it required that no quiz is generated/prefetched/stored for a meaning snack? [Completeness, Spec §FR-014]
- [x] CHK016 Is it required that no quiz entry or "Start Quiz" action is offered for a meaning snack? [Completeness, Spec §FR-015]
- [x] CHK017 Is exclusion from all quiz-bearing surfaces (batch/quiz flow, results, XP, history) required? [Completeness, Spec §FR-016]

## Persistence & Retrieval — Completeness

- [x] CHK018 Is backend persistence of a generated meaning snack required? [Completeness, Spec §FR-017]
- [x] CHK019 Is survival across page reload and a new session for the same theme required? [Completeness, Spec §FR-018]
- [x] CHK020 Is the additive/backward-compatible nature of the persistence change required? [Completeness, Spec §FR-019]
- [x] CHK021 Is a retrieval path for persisted meaning snacks required? [Completeness, Spec §FR-020]
- [x] CHK022 Is graceful degradation when retrieval fails/returns nothing required? [Completeness, Spec §FR-020]

## Security / Sanitization — Completeness

- [x] CHK023 Is keyword/meaning content required to be escaped via the safe allowlist (no HTML binding)? [Completeness, Spec §FR-021]
- [x] CHK024 Is non-execution/non-injection of markup required? [Completeness, Spec §FR-022]
- [x] CHK025 Is preservation of the feature-012/013 sanitization model required? [Completeness, Spec §FR-023]

## Non-Regression & Scope — Completeness

- [x] CHK026 Is preservation of regular snacks, the snack/quiz flow, progressive delivery, retries, navigation, results, and history required? [Completeness, Spec §FR-024]
- [x] CHK027 Is preservation of feature 013's highlight/definition-card behavior required? [Completeness, Spec §FR-025]
- [x] CHK028 Is the boundary against changing regular snack generation/provider/batch semantics/quiz scoring stated? [Completeness, Spec §FR-026]
- [x] CHK029 Is feature 013's explicit deferral (FR-031) referenced as the origin of this feature? [Traceability, Spec §Context & Scope]
- [x] CHK030 Is the no-quiz invariant stated as a non-goal? [Completeness, Spec §Non-Goals]

## Requirement Clarity

- [x] CHK031 Is "main topics backbone" given a concrete working interpretation? [Clarity, Spec §Context & Scope, §Clarifications #1]
- [x] CHK032 Is "visually distinct" tied to an observable outcome (color and/or badge)? [Clarity, Spec §FR-012, §SC-004]
- [x] CHK033 Is "meaning snack" defined as derived from term+definition and shaped like a snack? [Clarity, Spec §FR-007, §Key Entities]
- [x] CHK034 Is "no quiz" made concrete (not generated, not stored, not offered)? [Clarity, Spec §FR-014/§FR-015, §SC-005]
- [x] CHK035 Is "remains present after reload/new session" tied to a verifiable outcome? [Clarity, Spec §FR-018, §SC-006]
- [x] CHK036 Is the failure behavior (error + no entry) concretely described? [Clarity, Spec §FR-004, §Edge Cases]
- [x] CHK037 Is graceful degradation for the retrieval path clarified? [Clarity, Spec §FR-020, §US3 AS5]

## Requirement Consistency

- [x] CHK038 Do FR-001/FR-002 (action → generation) agree with FR-017/FR-018 (persistence → reload)? [Consistency, Spec §FR-001–§FR-002, §FR-017–§FR-018]
- [x] CHK039 Do FR-010/FR-011 (backbone integration) agree with FR-013 (never a blank card)? [Consistency, Spec §FR-010–§FR-013]
- [x] CHK040 Do FR-014–FR-016 (no quiz) agree with §Non-Goals and US2? [Consistency, Spec §FR-014–§FR-016, §Non-Goals]
- [x] CHK041 Do FR-021–FR-023 (security) match feature 013's sanitization model? [Consistency, Spec §FR-021–§FR-023, §Assumptions]
- [x] CHK042 Do the non-goals avoid contradicting the requirements (distinct kind vs. regular snack shape)? [Consistency, Spec §Non-Goals, §Key Entities]
- [x] CHK043 Does the additive persistence (FR-019) agree with the no-regression requirement (FR-024)? [Consistency, Spec §FR-019, §FR-024]

## Acceptance Criteria Quality

- [x] CHK044 Is SC-001 expressed as 100% card action presence / 0 render failures? [Measurability, Spec §SC-001]
- [x] CHK045 Is SC-002 expressed as 100% one-entry-per-activation / 0 duplicates? [Measurability, Spec §SC-002]
- [x] CHK046 Is SC-003 expressed as 100% loading state and 0 broken entries on failure? [Measurability, Spec §SC-003]
- [x] CHK047 Is SC-004 expressed as 100% distinct visual treatment / 0 indistinguishable entries? [Measurability, Spec §SC-004]
- [x] CHK048 Is SC-005 expressed as 0 quizzes generated/stored/offered? [Measurability, Spec §SC-005]
- [x] CHK049 Is SC-006 expressed as 100% survival after reload/new session? [Measurability, Spec §SC-006]
- [x] CHK050 Is SC-007 expressed as 0 invalidated existing data? [Measurability, Spec §SC-007]
- [x] CHK051 Is SC-008 expressed as 0 executions / 0 injected nodes? [Measurability, Spec §SC-008]
- [x] CHK052 Is SC-009 tied to named E2E non-regression and feature 012/013 preservation? [Measurability, Spec §SC-009]

## Edge Case & Scenario Coverage

- [x] CHK053 Is generation failure / LLM unavailable covered? [Coverage, Spec §Edge Cases]
- [x] CHK054 Are repeated/rapid activations covered? [Coverage, Spec §Edge Cases]
- [x] CHK055 Is a duplicate meaning snack for the same keyword covered? [Coverage, Spec §Edge Cases]
- [x] CHK056 Is the same term appearing in more than one snack/subtopic covered? [Coverage, Spec §Edge Cases]
- [x] CHK057 Is a blank/missing definition or absent keyword at activation time covered? [Coverage, Spec §Edge Cases]
- [x] CHK058 Is an unavailable retrieval/reload path covered? [Coverage, Spec §Edge Cases]
- [x] CHK059 Is an empty/zero-regular-snack backbone covered? [Coverage, Spec §Edge Cases]
- [x] CHK060 Is selecting a meaning snack (no blank card) covered? [Coverage, Spec §Edge Cases]
- [x] CHK061 Are first/last-position navigation and mixed ordering covered? [Coverage, Spec §Edge Cases]
- [x] CHK062 Is rich formatting in generated content covered? [Coverage, Spec §Edge Cases]
- [x] CHK063 Are Unicode/emoji/RTL covered? [Coverage, Spec §Edge Cases]
- [x] CHK064 Are injection payloads and escaped entities covered? [Coverage, Spec §Edge Cases]
- [x] CHK065 Is very long generated content covered? [Coverage, Spec §Edge Cases]
- [x] CHK066 Is backend persistence failure during generation covered? [Coverage, Spec §Edge Cases]
- [x] CHK067 Are pre-existing data and unaffected surfaces (results/history/quiz) covered? [Coverage, Spec §Edge Cases]

## Scope, Assumptions & Dependencies

- [x] CHK068 Is the working interpretation of "main topics backbone" recorded? [Assumption, Spec §Assumptions, §Clarifications #1]
- [x] CHK069 Is the meaning-snack shape (full snack structure from term+definition) recorded? [Assumption, Spec §Assumptions, §Clarifications #3]
- [x] CHK070 Is the theme/subtopic association choice recorded as open? [Assumption, Spec §Assumptions, §Clarifications #3]
- [x] CHK071 Is the retrieval-path gap recorded as an open question? [Assumption, Spec §Assumptions, §Clarifications #2]
- [x] CHK072 Is the additive `ddl-auto=update` persistence approach recorded? [Assumption, Spec §Assumptions, §Clarifications #7]
- [x] CHK073 Is the existing stack (Java 21/Spring Boot, Angular 21, MySQL 8) recorded? [Assumption, Spec §Assumptions]
- [x] CHK074 Is the shared safe-rendering path recorded? [Assumption, Spec §Assumptions]
- [x] CHK075 Is the feature-013 single-card mechanism reuse recorded? [Assumption, Spec §Assumptions]
- [x] CHK076 Is the LLM-abstraction/externalized-prompt constraint recorded? [Assumption, Spec §Project Structure Impact, §Assumptions]

## Project Structure Impact

- [x] CHK077 Is the backend layered layout confirmed unchanged (domain/application/infrastructure/interfaces)? [Completeness, Spec §Project Structure Impact]
- [x] CHK078 Is the frontend standalone-component layout confirmed unchanged? [Completeness, Spec §Project Structure Impact]
- [x] CHK079 Is the placement of the new meaning-snack type/service/DTOs/endpoint stated? [Completeness, Spec §Project Structure Impact]
- [x] CHK080 Is the additive persistence change documented? [Completeness, Spec §Project Structure Impact, §Clarifications #7]
- [x] CHK081 Is the retrieval-path implication of reload persistence documented? [Completeness, Spec §Project Structure Impact, §Clarifications #2]
- [x] CHK082 Is the reuse of the existing definition card and rich-text renderer stated? [Completeness, Spec §Project Structure Impact]

## Open Clarifications (confirmed by user before planning)

- [x] CHK083 Is the "main topics backbone" interpretation confirmed (snacks list vs. subtopics list vs. new area)? → **Confirmed Option A: snacks list/reading surface** [Clarification, Spec §Clarifications #1]
- [x] CHK084 Is the retrieval/listing path for persisted meaning snacks confirmed? → **Confirmed: theme-scoped listing read by the snacks-page load** [Clarification, Spec §Clarifications #2]
- [x] CHK085 Is the meaning-snack entity/kind and theme-vs-subtopic attachment confirmed? → **Confirmed: distinct kind, attached to theme + source subtopic** [Clarification, Spec §Clarifications #3]

## Notes

- Check items off as completed: `[x]`.
- Add the satisfying section/requirement reference inline.
- Items are numbered sequentially for easy reference; all must be checked at implementation completion.
- CHK083–CHK085 were resolved in the clarification session (all three confirmed); no `[NEEDS CLARIFICATION]` markers remain in the spec.
