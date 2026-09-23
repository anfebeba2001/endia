# Requirements Quality Checklist: Colored Keywords with Click-to-Show Short Definition Cards

**Purpose**: Validate that the snack keyword requirements are complete, clear, consistent, and measurable before/while implementing `013-snack-keywords`.
**Created**: 2026-09-23
**Feature**: [spec.md](../spec.md)

**Note**: This checklist tests the *requirements writing* (completeness, clarity, consistency, measurability), not the implementation. Every item must be answerable "yes" at implementation completion; mark `[x]` with the evidence (section or requirement ID) that satisfies it.

## Prompt / Generation Completeness

- [x] CHK001 Is the `keywords: [{ term, definition }]` output required from the prompt? [Completeness, Spec §FR-001]
- [x] CHK002 Is the ≤3-word definition limit stated as a prompt instruction? [Completeness, Spec §FR-001]
- [x] CHK003 Is the requirement that terms appear verbatim in the snack text stated? [Completeness, Spec §FR-002]
- [x] CHK004 Are the existing fields/formatting rules required to stay unchanged? [Completeness, Spec §FR-003]
- [x] CHK005 Is keyword text constrained to plain text with HTML forbidden? [Completeness, Spec §FR-004]
- [x] CHK006 Is it required that generated snacks actually contain keywords? [Completeness, Spec §FR-005]
- [x] CHK007 Is it required that keywords never cause a generation failure or re-roll? [Completeness, Spec §FR-005, §FR-008]

## Contract & Normalization Completeness

- [x] CHK008 Is the `keywords` field required on every snack-returning surface? [Completeness, Spec §FR-006]
- [x] CHK009 Is the hard ≤3-word definition limit stated for stored/returned values? [Completeness, Spec §FR-007, §FR-019]
- [x] CHK010 Is the over-limit handling rule (normalize/truncate) stated? [Completeness, Spec §FR-007, §Clarifications #1]
- [x] CHK011 Is dropping of blank-term/blank-definition keywords required? [Completeness, Spec §FR-008]
- [x] CHK012 Is an empty keyword list required to be valid/accepted? [Completeness, Spec §FR-008, §FR-005]
- [x] CHK013 Is de-duplication of repeated terms required? [Completeness, Spec §FR-009]
- [x] CHK014 Is keyword text required to be treated as plain text (markup neutralized)? [Completeness, Spec §FR-010]

## Persistence Completeness

- [x] CHK015 Is keyword round-trip through persistence/API required? [Completeness, Spec §FR-011]
- [x] CHK016 Is backward compatibility (pre-existing snacks → empty list) required? [Completeness, Spec §FR-012, §US5 AS3]
- [x] CHK017 Is the additive/nullable nature of the storage change required? [Completeness, Spec §FR-012, §Clarifications #6]

## Highlight Rendering Completeness

- [x] CHK018 Is a distinct highlight color required for key terms? [Completeness, Spec §FR-013]
- [x] CHK019 Is it required that only terms present in the rendered text are highlighted? [Completeness, Spec §FR-014]
- [x] CHK020 Is case-insensitive, whole-word, literal matching required? [Completeness, Spec §FR-015]
- [x] CHK021 Is longest-term-wins / non-overlapping highlight required? [Completeness, Spec §FR-016]
- [x] CHK022 Is highlighting all occurrences required? [Completeness, Spec §FR-017]
- [x] CHK023 Is preservation of feature-012 rich formatting required during highlighting? [Completeness, Spec §FR-015, §SC-009]

## Definition Card Completeness

- [x] CHK024 Is click/tap-to-open of a definition card required? [Completeness, Spec §FR-018]
- [x] CHK025 Is "card shows only the definition, never >3 words" required? [Completeness, Spec §FR-019]
- [x] CHK026 Is dismissal via outside-click, close control, AND Escape required? [Completeness, Spec §FR-020]
- [x] CHK027 Is "no navigation on dismissal" required? [Completeness, Spec §FR-020]
- [x] CHK028 Is opening a new card closing the previous one required? [Completeness, Spec §FR-021]
- [x] CHK029 Is desktop + mobile support required? [Completeness, Spec §FR-022]
- [x] CHK030 Is keyboard activation/focus return required? [Completeness, Spec §FR-023]
- [x] CHK031 Is no-keyword rendering required to be unchanged? [Completeness, Spec §FR-024]
- [x] CHK032 Is consistency across snack card, results recap, and history required? [Completeness, Spec §FR-025]

## Security / Sanitization Completeness

- [x] CHK033 Is keyword text required to be escaped via the safe allowlist (no HTML binding)? [Completeness, Spec §FR-026]
- [x] CHK034 Is non-execution/non-injection of keyword HTML required? [Completeness, Spec §FR-027]
- [x] CHK035 Is preservation of readable text when markup is neutralized required? [Completeness, Spec §FR-028]
- [x] CHK036 Is preservation of the feature-012 sanitization model required? [Completeness, Spec §FR-029]

## Non-Regression & Scope Completeness

- [x] CHK037 Is existing snack/recap/history/quiz behavior preservation required? [Completeness, Spec §FR-030]
- [x] CHK038 Are progressive delivery, retries, and navigation required to stay unchanged? [Completeness, Spec §FR-030]
- [x] CHK039 Is Task 9 (definition-card generate button, meaning snack) explicitly excluded? [Completeness, Spec §FR-031, §Non-Goals]
- [x] CHK040 Is the no-new-endpoints / in-place-surface-extension boundary stated? [Completeness, Spec §Non-Goals]

## Requirement Clarity

- [x] CHK041 Is the word-count rule (whitespace-separated tokens) made explicit? [Clarity, Spec §FR-007, §Assumptions, §Clarifications #4]
- [x] CHK042 Is the over-limit behavior concretely described (truncate to first 3 words)? [Clarity, Spec §FR-007, §Edge Cases]
- [x] CHK043 Is the highlight scope (body fields only, not `related`) made explicit? [Clarity, Spec §FR-014, §Clarifications #3]
- [x] CHK044 Is "distinct color" tied to an observable outcome (distinct from body text)? [Clarity, Spec §FR-013, §SC-002]
- [x] CHK045 Is "dismiss without navigating away" clarified? [Clarity, Spec §FR-020, §SC-004]
- [x] CHK046 Is graceful degradation for empty/missing keywords clarified? [Clarity, Spec §FR-024, §US5 AS3]

## Requirement Consistency

- [x] CHK047 Do the prompt limit (FR-001) and the contract limit (FR-007) agree on ≤3 words? [Consistency, Spec §FR-001, §FR-007]
- [x] CHK048 Do the optional-keywords requirements (FR-005/FR-008) agree with graceful degradation (FR-024)? [Consistency, Spec §FR-005, §FR-008, §FR-024]
- [x] CHK049 Do highlight (FR-013–FR-017) and card (FR-018–FR-025) requirements agree on matching scope? [Consistency, Spec §FR-013–§FR-025]
- [x] CHK050 Do security requirements (FR-026–FR-029) match feature-012's sanitization model? [Consistency, Spec §FR-026–§FR-029, §Assumptions]
- [x] CHK051 Do the non-goals avoid contradicting the requirements (no HTML dialect vs. escaped keyword text)? [Consistency, Spec §Non-Goals, §FR-010]

## Acceptance Criteria Quality

- [x] CHK052 Is SC-001 expressed as 100% keyword presence and 0 over-limit definitions? [Measurability, Spec §SC-001]
- [x] CHK053 Is SC-002 expressed as 100% present-term highlighting and 0 false highlights? [Measurability, Spec §SC-002]
- [x] CHK054 Is SC-003 expressed as 100% correct card content with 0 over-limit definitions shown? [Measurability, Spec §SC-003]
- [x] CHK055 Is SC-004 expressed as 100% dismissibility via all three means with 0 navigation? [Measurability, Spec §SC-004]
- [x] CHK056 Is SC-005 expressed as 100% desktop/mobile functionality with 0 overflow? [Measurability, Spec §SC-005]
- [x] CHK057 Is SC-006 expressed as 0 artifacts for empty/missing keywords? [Measurability, Spec §SC-006]
- [x] CHK058 Is SC-007 expressed as 0 executions / 0 injected nodes from keyword payloads? [Measurability, Spec §SC-007]
- [x] CHK059 Is SC-008 expressed as 100% round-trip across all surfaces? [Measurability, Spec §SC-008]
- [x] CHK060 Is SC-009 tied to named E2E non-regression and feature-012 formatting preservation? [Measurability, Spec §SC-009]

## Edge Case & Scenario Coverage

- [x] CHK061 Are exactly-3 vs 4+ word definitions covered? [Coverage, Spec §Edge Cases]
- [x] CHK062 Are blank/empty definitions after normalization covered? [Coverage, Spec §Edge Cases]
- [x] CHK063 Are duplicate terms covered? [Coverage, Spec §Edge Cases]
- [x] CHK064 Are substring/overlapping terms covered? [Coverage, Spec §Edge Cases]
- [x] CHK065 Are very short/common and regex-like terms covered? [Coverage, Spec §Edge Cases]
- [x] CHK066 Are whitespace/casing variants covered? [Coverage, Spec §Edge Cases]
- [x] CHK067 Is a term spanning a formatting/block boundary covered? [Coverage, Spec §Edge Cases]
- [x] CHK068 Is a term present only in `related` covered? [Coverage, Spec §Edge Cases]
- [x] CHK069 Are no-keyword / all-invalid-keyword snacks covered? [Coverage, Spec §Edge Cases]
- [x] CHK070 Are multiple cards / rapid interaction covered? [Coverage, Spec §Edge Cases]
- [x] CHK071 Is card overflow at viewport edges covered? [Coverage, Spec §Edge Cases]
- [x] CHK072 Are Unicode/emoji/RTL covered? [Coverage, Spec §Edge Cases]
- [x] CHK073 Are injection payloads and escaped entities covered? [Coverage, Spec §Edge Cases]
- [x] CHK074 Is the same-snack-in-multiple-surfaces case covered? [Coverage, Spec §Edge Cases]
- [x] CHK075 Are pre-existing (pre-feature) snacks covered? [Coverage, Spec §Edge Cases]

## Scope, Assumptions & Dependencies

- [x] CHK076 Is the keyword-count default (2–5, guideline) recorded? [Assumption, Spec §Assumptions, §Clarifications #5]
- [x] CHK077 Is the word-count convention recorded? [Assumption, Spec §Assumptions]
- [x] CHK078 Is the truncation-not-rejection decision recorded and justified? [Assumption, Spec §Assumptions, §Clarifications #1]
- [x] CHK079 Is the optional-keywords decision recorded? [Assumption, Spec §Assumptions, §Clarifications #2]
- [x] CHK080 Is the matching/scope rule recorded? [Assumption, Spec §Assumptions, §Clarifications #3]
- [x] CHK081 Is the additive nullable schema change recorded? [Assumption, Spec §Assumptions, §Clarifications #6]
- [x] CHK082 Is the existing stack (Java 21/Spring Boot, Angular 21, MySQL 8) recorded? [Assumption, Spec §Assumptions]
- [x] CHK083 Is the reuse of `JpaJsonObjectConverter` recorded? [Assumption, Spec §Assumptions, §Project Structure Impact]
- [x] CHK084 Is the shared rendering path across all snack-displaying surfaces recorded? [Assumption, Spec §Assumptions]
- [x] CHK085 Is the feature-012 no-`[innerHTML]` security boundary preservation recorded? [Assumption, Spec §Assumptions, §FR-029]

## Project Structure Impact

- [x] CHK086 Is the backend layered layout confirmed unchanged (domain/application/infrastructure/interfaces)? [Completeness, Spec §Project Structure Impact]
- [x] CHK087 Is the new domain value object placement stated? [Completeness, Spec §Project Structure Impact]
- [x] CHK088 Is the frontend standalone-component layout confirmed unchanged? [Completeness, Spec §Project Structure Impact]
- [x] CHK089 Is the new definition-card component and rich-text AST extension stated? [Completeness, Spec §Project Structure Impact, §Clarifications #7]
- [x] CHK090 Is the single additive schema change documented? [Completeness, Spec §Project Structure Impact, §Clarifications #6]

## Plan-Level Additions *(post-plan review)*

These items were surfaced while writing `plan.md`, `data-model.md`, and `research.md`. They confirm the
plan resolves the requirements' implementation-facing questions; each must be answerable "yes" at
implementation completion.

- [x] CHK091 Does the plan state exactly how the additive schema change is applied given the project has no migration tool (Hibernate `ddl-auto=update`, explicit DDL, rollback)? [Completeness, plan.md §Migration approach, data-model.md §5] — satisfied by plan.md §Migration approach / data-model.md §5 (explicit `ALTER TABLE snack ADD COLUMN keywords JSON NULL` + rollback)
- [x] CHK092 Does the plan acknowledge that `JpaJsonObjectConverter` cannot be literally reused (statically typed converter) and specify the new converter that mirrors its pattern? [Clarity, research.md R1, data-model.md §3] — satisfied by research.md R1 / data-model.md §3 (`JpaSnackKeywordListConverter`)
- [x] CHK093 Is the keyword count (2–5) recorded as a prompt guideline only, with the contract accepting any non-negative count? [Consistency, plan.md M1, contracts/snack-keywords-contract.md §1] — satisfied by plan.md M1 / contract §1.3 (empty list valid, no count limit)
- [x] CHK094 Is the recursive-bold AST extension specified so a keyword inside bold is both emphasized and highlighted without breaking feature-012 rendering? [Completeness, plan.md §Design, research.md R4] — satisfied by plan.md §Design / research.md R4 (recursive `bold` nodes, `<strong>` preserved)
- [x] CHK095 Is the single-card-per-page mechanism specified as the guarantee for "opening another term closes the previous card" across fields and surfaces? [Consistency, research.md R5, contracts/snack-keywords-contract.md §3.2] — satisfied by research.md R5 / contract §3.2 / plan.md §Frontend page integration
- [x] CHK096 Is the highlight matcher specified as literal/index-scan with explicit whole-word boundaries, covering regex-like terms (`C++`, `(x)`, `a.b`)? [Clarity, research.md R4, contracts/snack-keywords-contract.md §2.1] — satisfied by research.md R4 / contract §2.1 M2/M3
- [x] CHK097 Is the frontend unit-test gap explicitly flagged as an open question (no runner in the repo) rather than silently assumed? [Traceability, plan.md §Testing Strategy, research.md R6] — satisfied by plan.md §Testing Strategy (flagged) / research.md R6 (Vitest open question)
- [x] CHK098 Are the existing contract-shape assertions identified for update (key counts 7/7/7/5 → 8/8/8/6) as part of the contract extension? [Consistency, plan.md §Testing Strategy] — satisfied by plan.md §Testing Strategy / T009
- [x] CHK099 Is `BatchService.BatchItemStatus` / `QuizService.Recap` correctly identified as needing no change because they carry the raw entity? [Consistency, data-model.md §6] — satisfied by data-model.md §6 / T046
- [x] CHK100 Is the unused `application/dto/SnackGenerateResponse.java` identified and explicitly left out of scope? [Traceability, data-model.md §6] — satisfied by data-model.md §6 / T046

## Notes

- Check items off as completed: `[x]`.
- Add the satisfying section/requirement reference inline.
- Items are numbered sequentially for easy reference; all must be checked at implementation completion.
