# Requirements Quality Checklist: Snack Rich Formatting (Bold Emphasis, Bullets, Short Paragraphs)

**Purpose**: Validate that the snack rich-formatting requirements are complete, clear, consistent, and measurable before/while implementing `012-snack-rich-formatting`.
**Created**: 2026-09-23
**Feature**: [spec.md](../spec.md)

**Note**: This checklist tests the *requirements writing* (completeness, clarity, consistency, measurability), not the implementation. Every item must be answerable "yes" at implementation completion; mark `[x]` with the evidence (section or requirement ID) that satisfies it.

## Prompt / Generation Completeness

- [x] CHK001 Is the bold marker requirement stated (`**...**` for important words/sentences)? [Completeness, Spec §FR-001]
- [x] CHK002 Is the bullets/short-paragraph instruction stated? [Completeness, Spec §FR-002]
- [x] CHK003 Is the unchanged JSON shape required (`concept`, `metaphor`, `whyItMatters`, `related`)? [Completeness, Spec §FR-003]
- [x] CHK004 Is the allowed formatting vocabulary (whitelist) defined for the prompt? [Completeness, Spec §FR-004]
- [x] CHK005 Is arbitrary HTML explicitly forbidden in the prompt? [Completeness, Spec §FR-004]
- [x] CHK006 Is it required that generated fields actually contain markup? [Completeness, Spec §FR-005]
- [x] CHK007 Are the existing validation constraints (non-empty fields, 2–3 related) preserved? [Completeness, Spec §FR-006]

## Persistence & API Completeness

- [x] CHK008 Is markup preservation through persistence required? [Completeness, Spec §FR-007]
- [x] CHK009 Is markup preservation through the API required on every snack-returning surface? [Completeness, Spec §FR-008]
- [x] CHK010 Is the "not stripped / not HTML-escaped / not collapsed" requirement explicit? [Completeness, Spec §FR-007, §FR-008]
- [x] CHK011 Is the unchanged API/JSON contract required (no new fields/endpoints/schema)? [Completeness, Spec §FR-009, §FR-022]

## Frontend Rendering Completeness

- [x] CHK012 Is bold rendered as actual bold (never literal `**`) required? [Completeness, Spec §FR-010]
- [x] CHK013 Are bullet lines rendered as a real list required? [Completeness, Spec §FR-011]
- [x] CHK014 Are long fields rendered as multiple short paragraphs required? [Completeness, Spec §FR-012]
- [x] CHK015 Is consistent formatting across `concept`/`metaphor`/`whyItMatters` and the recap required? [Completeness, Spec §FR-013]
- [x] CHK016 Is `related` required to stay plain labels (no markup interpretation)? [Completeness, Spec §FR-013]
- [x] CHK017 Is clean plain-text rendering for unmarked fields required? [Completeness, Spec §FR-014]
- [x] CHK018 Is non-mangling of legitimate non-formatting characters required? [Completeness, Spec §FR-014]

## Security / Sanitization Completeness

- [x] CHK019 Is the render whitelist (bold, paragraphs, bullet lists) explicit? [Completeness, Spec §FR-015]
- [x] CHK020 Is neutralization of all non-whitelisted markup required? [Completeness, Spec §FR-015]
- [x] CHK021 Is non-execution of raw/arbitrary HTML required (script, handlers, `javascript:`, iframe, img, style)? [Completeness, Spec §FR-016]
- [x] CHK022 Is render-time sanitization (defense in depth) required? [Completeness, Spec §FR-017]
- [x] CHK023 Is a safe allowlist binding mechanism (no unsanitized HTML binding) required? [Completeness, Spec §FR-018]
- [x] CHK024 Is preservation of readable text when markup is neutralized required? [Completeness, Spec §FR-019]

## Non-Regression & Scope Completeness

- [x] CHK025 Is existing snack-field and recap rendering preservation required? [Completeness, Spec §FR-020]
- [x] CHK026 Are progressive delivery, retries, and navigation required to stay unchanged? [Completeness, Spec §FR-021]
- [x] CHK027 Is the generation flow/provider/batch/data-model scope boundary stated? [Completeness, Spec §FR-022, §Non-Goals]
- [x] CHK028 Is the history page explicitly IN scope for rich rendering, with its read-only behavior preserved? [Completeness, Spec §Non-Goals, §FR-023, §Clarifications]

## Requirement Clarity

- [x] CHK029 Is the bold marker concretely identified? [Clarity, Spec §FR-001, §Assumptions]
- [x] CHK030 Is "long content" given an observable rendering outcome (multiple short paragraphs and/or a bullet list)? [Clarity, Spec §FR-002, §FR-012, §SC-004]
- [x] CHK031 Is the whitelist defined as a closed set? [Clarity, Spec §FR-015, §Key Entities]
- [x] CHK032 Is "neutralized" clarified as stripped-or-escaped, never executed? [Clarity, Spec §FR-015, §FR-016]
- [x] CHK033 Is the difference between the snack card, the results recap, and the history view rendering made explicit? [Clarity, Spec §FR-013, §FR-023, §US2 AS5–AS6]
- [x] CHK034 Is graceful degradation when the model ignores formatting clarified? [Clarity, Spec §FR-014, §Assumptions]
- [x] CHK035 Is the byte-for-byte preservation expectation clear? [Clarity, Spec §FR-007, §SC-006]

## Requirement Consistency

- [x] CHK036 Do the prompt whitelist (FR-004) and the render whitelist (FR-015) agree? [Consistency, Spec §FR-004, §FR-015]
- [x] CHK037 Do the API-preservation (FR-007/FR-008) and unchanged-contract (FR-009) requirements agree? [Consistency, Spec §FR-007–§FR-009]
- [x] CHK038 Do the rendering requirements (FR-010–FR-014, FR-023–FR-024) match the prompt output requirements (FR-001–FR-005) and cover all surfaces? [Consistency, Spec §FR-001–§FR-014, §FR-023–§FR-024]
- [x] CHK039 Do the non-goals avoid contradicting the requirements (no full Markdown dialect vs. whitelist rendering)? [Consistency, Spec §Non-Goals, §FR-015]
- [x] CHK040 Is `related` consistently treated as plain text in both prompt and rendering requirements? [Consistency, Spec §FR-003, §FR-013]

## Acceptance Criteria Quality

- [x] CHK041 Is SC-001 expressed as 100% markup/structure coverage with 0 walls of text? [Measurability, Spec §SC-001]
- [x] CHK042 Is SC-002 expressed as 0 literal `**` and 100% real bold across all snack-displaying surfaces? [Measurability, Spec §SC-002]
- [x] CHK043 Is SC-003 expressed as 100% list/paragraph rendering with 0 literal markers across all surfaces? [Measurability, Spec §SC-003]
- [x] CHK044 Is SC-004 expressed as 0 long fields rendered as a single paragraph across all surfaces? [Measurability, Spec §SC-004]
- [x] CHK045 Is SC-005 expressed as 0 executions / 0 injected nodes for injection payloads, including the history view? [Measurability, Spec §SC-005]
- [x] CHK046 Is SC-006 expressed as byte-for-byte round-trip preservation across all surfaces? [Measurability, Spec §SC-006]
- [x] CHK047 Is SC-007 expressed as 0 contract/schema changes? [Measurability, Spec §SC-007]
- [x] CHK048 Is SC-008 tied to named E2E non-regression and 0 rendering artifacts? [Measurability, Spec §SC-008]

## Edge Case & Scenario Coverage

- [x] CHK049 Is the unclosed/malformed bold marker case covered? [Coverage, Spec §Edge Cases]
- [x] CHK050 Are stray, non-formatting asterisks covered? [Coverage, Spec §Edge Cases]
- [x] CHK051 Is mixed paragraph + list content covered? [Coverage, Spec §Edge Cases]
- [x] CHK052 Are varying bullet markers/spacing covered? [Coverage, Spec §Edge Cases]
- [x] CHK053 Is the long field with no bullets covered? [Coverage, Spec §Edge Cases]
- [x] CHK054 Are empty/whitespace fields covered? [Coverage, Spec §Edge Cases]
- [x] CHK055 Are markup-like characters in `related` covered? [Coverage, Spec §Edge Cases]
- [x] CHK056 Are non-whitelisted markup and injection payloads covered? [Coverage, Spec §Edge Cases]
- [x] CHK057 Are escaped entities and Unicode/emoji/RTL cases covered? [Coverage, Spec §Edge Cases]
- [x] CHK058 Is the same-snack-in-multiple-surfaces case covered (snack card, results recap, history view)? [Coverage, Spec §Edge Cases]
- [x] CHK059 Are pre-existing (pre-prompt-change) snacks covered, including in the history view? [Coverage, Spec §Edge Cases]

## Scope, Assumptions & Dependencies

- [x] CHK060 Is the `**...**` marker assumption recorded? [Assumption, Spec §Assumptions]
- [x] CHK061 Are the bullet/paragraph conventions recorded (prompt emits `- `; renderer accepts `-`, `*`, `•`)? [Assumption, Spec §Assumptions, §Clarifications]
- [x] CHK062 Is the no-schema-change dependency recorded? [Assumption, Spec §Assumptions, §FR-009]
- [x] CHK063 Is the backend-preserves / frontend-render-time-allowlist boundary recorded? [Assumption, Spec §Assumptions, §FR-017, §Clarifications]
- [x] CHK064 Is the existing stack (Java 21/Spring Boot, Angular 21, MySQL 8) recorded? [Assumption, Spec §Assumptions]
- [x] CHK065 Are the resolved clarifications (history rendering in scope, bullet convention, sanitization location) recorded? [Assumption, Spec §Clarifications]

## History View Rendering (feature 011) — In Scope

- [x] CHK066 Is history-view rich rendering required (bold as bold, bullets as lists, long content as short paragraphs)? [Completeness, Spec §FR-023, §US2 AS6]
- [x] CHK067 Is history-view sanitization required to match the snack card and recap (same whitelist, render-time, no execution)? [Completeness, Spec §FR-024, §FR-015–§FR-019]
- [x] CHK068 Is history-view non-regression required (read-only behavior and data shape unchanged)? [Completeness, Spec §Non-Goals, §FR-023]
- [x] CHK069 Is a measurable success criterion present for history-view rendering + sanitization? [Measurability, Spec §SC-009]
- [x] CHK070 Are history-view edge cases covered (large nested set, pre-existing snacks)? [Coverage, Spec §Edge Cases]

## Notes

- Check items off as completed: `[x]`.
- Add the satisfying section/requirement reference inline.
- Items are numbered sequentially for easy reference; all must be checked at implementation completion.
