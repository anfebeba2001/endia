# Requirements Quality Checklist: Expand Keyword Meaning Limit from 3 to 20 Words

**Purpose**: Validate that the keyword-meaning 20-word-cap requirements are complete, clear, consistent, and measurable before/while implementing `015-keyword-meaning-20-words`.
**Created**: 2026-09-23
**Feature**: [spec.md](../spec.md)

**Note**: This checklist tests the *requirements writing* (completeness, clarity, consistency, measurability), not the implementation. Every item must be answerable "yes" at implementation completion; mark `[x]` with the evidence (section or requirement ID) that satisfies it.

## Length Policy — Completeness

- [x] CHK001 Is the new 20-token (inclusive) definition cap stated for keyword definitions? [Completeness, Spec §FR-001]
- [x] CHK002 Is over-limit truncation to the first 20 tokens required before persist/serve? [Completeness, Spec §FR-002]
- [x] CHK003 Is the same rule required for the meaning-snack source definition? [Completeness, Spec §FR-003]
- [x] CHK004 Is it required that 4–20-word definitions survive generation → persistence → API → display without truncation to 3? [Completeness, Spec §FR-004]
- [x] CHK005 Is the word-count rule (whitespace-separated tokens, hyphenated = one) stated? [Clarity, Spec §FR-005]
- [x] CHK006 Is blank/trim/dedupe/order behavior required to stay unchanged? [Completeness, Spec §FR-006]

## Scope Boundaries — Completeness & Consistency

- [x] CHK007 Is it explicit that snack content fields are out of scope? [Consistency, Spec §FR-007, §Non-Goals]
- [x] CHK008 Is it explicit that the `related` 2–3 item cap is a separate, unaffected rule? [Consistency, Spec §FR-007, §Edge Cases]
- [x] CHK009 Is it explicit that the keyword count guideline (2–5) and matching rules are unchanged? [Consistency, Spec §Non-Goals, §FR-016]
- [x] CHK010 Is the absence of a new endpoint/entity/user action/schema change stated? [Scope, Spec §FR-017, §Non-Goals]

## Prompt & Documentation — Completeness

- [x] CHK011 Is the prompt required to state the 20-word limit in both example JSON and explicit rule? [Completeness, Spec §FR-008]
- [x] CHK012 Are the javadoc/comment/doc surfaces to update identified? [Completeness, Spec §FR-009, §Context & Scope]
- [x] CHK013 Is the provider-agnostic / externalized LLM constraint restated? [Consistency, Spec §FR-010, §Assumptions]
- [x] CHK029 Is the meaning-snack prompt wording update required (wording only, no JSON/rule change)? [Completeness, Spec §FR-018, §Clarifications #2]

## Rendering & Readability — Completeness & Measurability

- [x] CHK014 Is the card required to show the full up-to-20-word definition with escaped interpolation only? [Completeness, Spec §FR-011]
- [x] CHK015 Is "no horizontal overflow / no clipping / desktop + narrow widths" stated as a requirement? [Measurability, Spec §FR-012, §SC-004]
- [x] CHK016 Are injection payloads required to remain inert? [Completeness, Spec §FR-013]
- [x] CHK017 Is the feature-012/013/014 security model (no `[innerHTML]`, no `DomSanitizer`) explicitly preserved? [Consistency, Spec §FR-014, §SC-005]

## Boundary & Non-Regression — Measurability

- [x] CHK018 Are 19/20/21-word boundary tests required for both surfaces? [Coverage, Spec §FR-015, §SC-003]
- [x] CHK019 Is the update of tests that previously asserted the 3-word cap required? [Coverage, Spec §FR-015]
- [x] CHK020 Are the 013/014 behaviors that must not regress enumerated? [Consistency, Spec §FR-016, §SC-006]
- [x] CHK021 Is "0 stored/returned definitions exceed 20 tokens" objectively measurable? [Measurability, Spec §SC-002]
- [x] CHK022 Is "0 stale 'at most 3 words' statements remain in changed surfaces" measurable? [Measurability, Spec §SC-007]
- [x] CHK023 Are previously stored 3-word definitions required to remain valid without migration? [Consistency, Spec §FR-017, §Assumptions]
- [x] CHK030 Is a focused frontend test/assertion for a 20-word definition wrapping with no horizontal overflow required (making SC-004 verifiable)? [Coverage, Spec §FR-015, §SC-004]
- [x] CHK032 Is the meaning-snack source definition required to stay ≤200 characters when persisted (in addition to the 20-token cap), bounded on a word boundary without splitting words? [Completeness, Spec §FR-019, §SC-008]
- [x] CHK033 Is it explicit that the 200-character bound applies only to the meaning-snack source definition (JSON-column `SnackKeyword.definition` is unconstrained) and that no schema change is made? [Consistency, Spec §FR-019, §Assumptions, §Clarifications #9]

## Edge Cases & Ambiguity

- [x] CHK024 Are whitespace variants and whitespace-only definitions covered? [Edge Case, Spec §Edge Cases]
- [x] CHK025 Is the "very long single token" case addressed (one token, must wrap)? [Edge Case, Spec §Edge Cases]
- [x] CHK026 Are Unicode/emoji/RTL definitions addressed? [Edge Case, Spec §Edge Cases]
- [x] CHK027 Is the exact boundary behavior (19 kept / 20 kept / 21 truncated) unambiguous? [Clarity, Spec §US2, §SC-003]
- [x] CHK028 Are no `[NEEDS CLARIFICATION]` markers left in the spec? [Completeness, Spec §Clarifications]
- [x] CHK031 Is the definition card's natural growth (no max-height/scroll now; bounded max-height as fallback) recorded? [Clarity, Spec §Edge Cases, §Assumptions, §Clarifications #3]
- [x] CHK034 Is the "20 tokens but >200 characters" edge case covered with the word-boundary bound? [Edge Case, Spec §Edge Cases, §FR-019]

## Notes

- All items pass. The five open questions from the initial draft were accepted by the product owner with the recommended defaults and are recorded as **ACCEPTED** decisions in Spec §Clarifications (#1–#5); the corresponding requirements are FR-007, FR-018, FR-012/§Edge Cases, FR-017, and FR-015/SC-004. The storage-bound decision (option (a)) is recorded as **ACCEPTED** in Spec §Clarifications #9 and encoded in FR-019 / SC-008.
