# Requirements Quality Checklist: Custom Keyword "Define" from a Held Text Selection (2-Second Trigger)

**Purpose**: Validate that the selection-Define requirements are complete, clear, consistent, and measurable before/while implementing `016-custom-keyword-define`.
**Created**: 2026-09-24
**Feature**: [spec.md](../spec.md)

**Note**: This checklist tests the *requirements writing* (completeness, clarity, consistency, measurability), not the implementation. Every item must be answerable "yes" at implementation completion; mark `[x]` with the evidence (section or requirement ID) that satisfies it.

## Selection & Trigger — Completeness

- [x] CHK001 Is a hold timer of approximately 2 seconds on a non-collapsed in-content selection required before any affordance appears? [Completeness, Spec §FR-001, §US1 AS1/AS2]
- [x] CHK002 Is a clearly labeled "Define" affordance required to appear only after the hold? [Completeness, Spec §FR-001, §US1 AS1]
- [x] CHK003 Is cancellation on selection change/clear before 2 seconds required (timer reset, no affordance for the old selection)? [Completeness, Spec §FR-002, §US1 AS3]
- [x] CHK004 Is the affordance required to be transient and anchored to the current selection? [Completeness, Spec §FR-003, §Clarifications #5]
- [x] CHK005 Is non-interference with the single-definition-card rule and existing highlighted-keyword click behavior required? [Completeness, Spec §FR-003, §US4 AS3/AS4]

## Generation — Completeness

- [x] CHK006 Is activating "Define" required to request generation of a **short definition** for the selected term? [Completeness, Spec §FR-004]
- [x] CHK007 Is the generated result required to be bounded to at most **20 words** (consistent with feature 015)? [Completeness, Spec §FR-004, §Clarifications #1, §Key Entities]
- [x] CHK008 Is generation required to use the externalized, provider-agnostic LLM flow with no provider-specific logic in services/controllers? [Completeness, Spec §FR-005]
- [x] CHK009 Is a loading/in-progress state required while generating? [Completeness, Spec §FR-006, §US2 AS1]
- [x] CHK010 Is single-concurrent-generation / at-most-one-request-on-rapid-repeat required? [Completeness, Spec §FR-007, §US2 AS2]
- [x] CHK011 Is opening the definition card for the selected term with the generated meaning required on success? [Completeness, Spec §FR-008, §US2 AS3]
- [x] CHK012 Is a meaningful, user-safe error required on failure, with no broken/empty/placeholder content added? [Completeness, Spec §FR-009, §US2 AS4, §SC-002]

## Normalization & Validation — Completeness

- [x] CHK013 Is the selected term required to be normalized (trimmed, whitespace-collapsed) before use? [Completeness, Spec §FR-010, §Clarifications #2]
- [x] CHK014 Is the selected term required to be bounded to at most **10** whitespace-separated tokens? [Completeness, Spec §FR-010, §Clarifications #2]
- [x] CHK015 Is a blank/whitespace-only selection required to be rejected with a meaningful error and never submitted? [Completeness, Spec §FR-011, §SC-005]
- [x] CHK016 Is an oversized (>10 tokens) selection required to be rejected with a meaningful error and never submitted? [Completeness, Spec §FR-011, §SC-005]
- [x] CHK017 Is the whitespace-separated token rule (hyphenated compounds count as one) required, consistent with feature 013/015? [Completeness, Spec §FR-012]

## Display & Security — Completeness

- [x] CHK018 Is escaped-text-only rendering of the selected term and generated meaning required (no `[innerHTML]`, no `DomSanitizer`)? [Completeness, Spec §FR-013, §US3 AS5]
- [x] CHK019 Is non-execution/non-injection of markup/handlers/dangerous URLs required? [Completeness, Spec §FR-014, §SC-004]
- [x] CHK020 Is preservation of the feature-012/013/014/015 sanitization model required unchanged? [Completeness, Spec §FR-015]

## Reachability & Scope — Completeness

- [x] CHK021 Is reachability via pointer text selection required? [Completeness, Spec §FR-016, §US4 AS1]
- [x] CHK022 Is reachability via programmatic/keyboard selection (`document.getSelection()`) required? [Completeness, Spec §FR-016, §US4 AS2]
- [x] CHK023 Is the trigger required to be offered **only** on the snack reading page, with results/history unchanged? [Completeness, Spec §FR-017, §US4 AS6, §SC-006]
- [x] CHK024 Is non-regression of keyword/card/meaning-snack/rich-text/results/history and the existing E2E suites required? [Completeness, Spec §FR-018, §US5, §SC-007]
- [x] CHK025 Is Task 12 ("Create snack" from the custom meaning) required to remain out of scope? [Completeness, Spec §FR-019, §Non-Goals]

## Requirement Clarity

- [x] CHK026 Is "approximately 2 seconds" given a concrete working interpretation (threshold; not before it)? [Clarity, Spec §Assumptions, §FR-001]
- [x] CHK027 Is "short definition" made concrete (≤20 words, plain text, not a multi-paragraph explanation)? [Clarity, Spec §FR-004, §Clarifications #1, §Key Entities]
- [x] CHK028 Is "normalized term" made concrete (trim + whitespace-collapse, ≤10 tokens, hyphenated = one)? [Clarity, Spec §FR-010/§FR-012, §Clarifications #2]
- [x] CHK029 Is the "Define" affordance made concrete (transient, selection-anchored floating button/popover, dismissed on change/clear)? [Clarity, Spec §FR-003, §Clarifications #5]
- [x] CHK030 Is the failure behavior (meaningful error + nothing added) concretely described? [Clarity, Spec §FR-009, §Edge Cases]
- [x] CHK031 Is the snack-reading-page-only scope made concrete (`snacks.component` reading content)? [Clarity, Spec §FR-017, §Clarifications #3]
- [x] CHK032 Is the generated-definition display made concrete (existing single card, escaped interpolation only)? [Clarity, Spec §FR-008/§FR-013, §Project Structure Impact]

## Requirement Consistency

- [x] CHK033 Do FR-001/FR-002 (hold/cancel) agree with FR-003 (transient, selection-anchored)? [Consistency, Spec §FR-001–§FR-003]
- [x] CHK034 Do FR-004/FR-008 (generate → card) agree with FR-006/FR-009 (loading/error)? [Consistency, Spec §FR-004–§FR-009]
- [x] CHK035 Do FR-010–FR-012 (normalization/validation) agree with FR-011 (never submitted) and SC-005? [Consistency, Spec §FR-010–§FR-012, §SC-005]
- [x] CHK036 Do FR-013–FR-015 (security) match feature 012/013's sanitization model? [Consistency, Spec §FR-013–§FR-015, §Assumptions]
- [x] CHK037 Do FR-016/FR-017 (reachability/scope) agree with the US4 edge cases and SC-006? [Consistency, Spec §FR-016/§FR-017, §SC-006]
- [x] CHK038 Do the Non-Goals avoid contradicting the requirements (reuse the card, no schema change, no Task 12)? [Consistency, Spec §Non-Goals, §Clarifications #4–#6]

## Acceptance Criteria Quality

- [x] CHK039 Is SC-001 expressed as 100% held selections show the affordance / 0 before 2s or after change-clear? [Measurability, Spec §SC-001]
- [x] CHK040 Is SC-002 expressed as 100% loading→meaning or meaningful error / 0 broken/empty/placeholder? [Measurability, Spec §SC-002]
- [x] CHK041 Is SC-003 expressed as 0 duplicate requests / at most one in-flight? [Measurability, Spec §SC-003]
- [x] CHK042 Is SC-004 expressed as 0 executions / 0 injected nodes? [Measurability, Spec §SC-004]
- [x] CHK043 Is SC-005 expressed as 100% normalized / 0 blank-or-oversized submitted? [Measurability, Spec §SC-005]
- [x] CHK044 Is SC-006 tied to pointer + programmatic reachability, page-only scope, and 0 keyword-click/single-card regressions? [Measurability, Spec §SC-006]
- [x] CHK045 Is SC-007 tied to named existing suites and feature-012/013/014/015 preservation? [Measurability, Spec §SC-007]

## Edge Case & Scenario Coverage

- [x] CHK046 Are selection changed/cleared before 2s and released before 2s covered? [Coverage, Spec §Edge Cases]
- [x] CHK047 Are blank/whitespace-only and oversized (>10 tokens) selections covered? [Coverage, Spec §Edge Cases]
- [x] CHK048 Are rapid repeated activations covered (one in-flight request, no duplicate card content)? [Coverage, Spec §Edge Cases]
- [x] CHK049 Is generation failure / LLM unavailable covered (error, nothing added, retry)? [Coverage, Spec §Edge Cases]
- [x] CHK050 Is a selection spanning multiple elements/blocks covered (plain-text normalization, usable anchoring)? [Coverage, Spec §Edge Cases]
- [x] CHK051 Is a selection inside/overlapping an existing highlighted keyword covered? [Coverage, Spec §Edge Cases]
- [x] CHK052 Is a selection entirely outside the reading content covered (no affordance)? [Coverage, Spec §Edge Cases]
- [x] CHK053 Is a selection on the results recap / read-only history surface covered (no affordance)? [Coverage, Spec §Edge Cases]
- [x] CHK054 Is a keyboard/programmatic (`document.getSelection()`) selection covered? [Coverage, Spec §Edge Cases]
- [x] CHK055 Is touch/mobile selection and horizontal overflow covered? [Coverage, Spec §Edge Cases]
- [x] CHK056 Is affordance dismissal (selection clear, click elsewhere, Escape) covered? [Coverage, Spec §Edge Cases]
- [x] CHK057 Is the card already open when a new selection is held covered (replace, one card)? [Coverage, Spec §Edge Cases]
- [x] CHK058 Are Unicode/emoji/RTL covered? [Coverage, Spec §Edge Cases]
- [x] CHK059 Are injection payloads and escaped entities covered? [Coverage, Spec §Edge Cases]
- [x] CHK060 Is a very long generated meaning covered (stable layout, no overflow break)? [Coverage, Spec §Edge Cases]
- [x] CHK061 Is backend unavailable/timeout covered (meaningful error, nothing added)? [Coverage, Spec §Edge Cases]
- [x] CHK062 Are pre-existing behaviors (highlight, card, meaning-snack) covered as unchanged? [Coverage, Spec §Edge Cases]

## Scope, Assumptions & Dependencies

- [x] CHK063 Is the short-definition content shape (≤20 words) recorded as confirmed? [Assumption, Spec §Assumptions, §Clarifications #1]
- [x] CHK064 Is the ≤10-token selected-term bound recorded as confirmed? [Assumption, Spec §Assumptions, §Clarifications #2]
- [x] CHK065 Is the snack-reading-page-only surface scope recorded as confirmed? [Assumption, Spec §Assumptions, §Clarifications #3]
- [x] CHK066 Is the new provider-agnostic endpoint decision recorded as confirmed? [Assumption, Spec §Assumptions, §Clarifications #4]
- [x] CHK067 Is the transient selection-anchored affordance recorded as confirmed? [Assumption, Spec §Assumptions, §Clarifications #5]
- [x] CHK068 Is the no-persistence/no-schema-change decision recorded as confirmed? [Assumption, Spec §Assumptions, §Clarifications #6]
- [x] CHK069 Is the existing stack (Java 21 + Spring Boot, Angular 21, MySQL 8) recorded? [Assumption, Spec §Assumptions]
- [x] CHK070 Is the untrusted-text safe-rendering path recorded? [Assumption, Spec §Assumptions, §FR-013–§FR-015]
- [x] CHK071 Is the reuse of the existing single definition card recorded? [Assumption, Spec §Assumptions, §Project Structure Impact]
- [x] CHK072 Is the LLM-abstraction/externalized-prompt constraint recorded? [Assumption, Spec §Project Structure Impact, §Assumptions]

## Project Structure Impact

- [x] CHK073 Is the backend layered layout confirmed unchanged (domain → application → infrastructure → interfaces)? [Completeness, Spec §Project Structure Impact]
- [x] CHK074 Is the frontend standalone/OnPush/signals layout confirmed unchanged? [Completeness, Spec §Project Structure Impact]
- [x] CHK075 Is the placement of the new port record/method, prompt, service, exception, DTOs and endpoint stated? [Completeness, Spec §Project Structure Impact]
- [x] CHK076 Is the reuse of the existing definition card and the unchanged `RichTextComponent` stated? [Completeness, Spec §Project Structure Impact]
- [x] CHK077 Is the no-persistence/no-schema-change impact documented? [Completeness, Spec §Project Structure Impact, §Clarifications #6]

## Open Clarifications (confirmed by the product owner)

- [x] CHK078 Is the content shape confirmed (short bounded definition ≤20 words)? → **Confirmed Option A** [Clarification, Spec §Clarifications #1]
- [x] CHK079 Is the max-words rule confirmed (≤10 whitespace tokens; blank/oversized rejected)? → **Confirmed Option A** [Clarification, Spec §Clarifications #2]
- [x] CHK080 Are the surfaces confirmed (snack reading page only; results/history unchanged)? → **Confirmed Option A** [Clarification, Spec §Clarifications #3]
- [x] CHK081 Is the new endpoint confirmed (provider-agnostic, not the meaning-snack endpoint)? → **Confirmed** [Clarification, Spec §Clarifications #4]
- [x] CHK082 Is the affordance confirmed (transient, selection-anchored)? → **Confirmed** [Clarification, Spec §Clarifications #5]
- [x] CHK083 Is no-persistence/no-schema-change confirmed? → **Confirmed** [Clarification, Spec §Clarifications #6]

## Notes

- Check items off as completed: `[x]`.
- Add the satisfying section/requirement reference inline.
- Items are numbered sequentially for easy reference; all must be checked at implementation completion.
- All former open items are resolved in the clarification session; no `[NEEDS CLARIFICATION]` markers remain. Implementation-adjacent detail is confined to the `Context & Scope` / `Project Structure Impact` sections, mirroring features 013/014/015.
