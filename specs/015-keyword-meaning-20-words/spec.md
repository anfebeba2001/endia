# Feature Specification: Expand Keyword Meaning Limit from 3 to 20 Words

**Feature Branch**: `015-keyword-meaning-20-words`

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "Raise the keyword-definition (meaning) length cap from at most 3 words to at most 20 words across generation, normalization, and documentation, so keyword meanings can be richer without changing snack behavior." (Batch backlog Task 10, from item 13.)

## Context & Scope *(established — do not re-investigate)*

This is a **small, focused enhancement** to the keyword-meaning feature delivered by `014-meaning-snacks`, which itself built on the colored-keyword highlight and click-to-show definition card of `013-snack-keywords`. Today, a keyword's `definition` ("meaning") is capped at **at most 3 words** in three coordinated places: the generation prompt, the backend normalization at the service boundary, and the documentation/comments that state the rule. This feature raises that cap to **at most 20 words** consistently, so keyword meanings can be richer, while changing nothing else about snacks, quizzes, meaning snacks, or the existing keyword interaction.

The following current code state is treated as confirmed input and is referenced, not re-designed:

**Backend**
- `backend/src/main/java/com/endia/application/service/SnackService.java` — `normalizeKeywords(...)` trims terms/definitions, drops blanks, truncates each definition to the first **3** whitespace-separated tokens, and de-duplicates terms case-insensitively (order-preserving). Keyword issues never trigger a re-roll or a failure.
- `backend/src/main/java/com/endia/application/service/MeaningSnackService.java` — `normalizeDefinition(...)` mirrors that rule for the **source definition** of a meaning snack, truncating to the first **3** whitespace-separated tokens.
- `backend/src/main/java/com/endia/domain/model/SnackKeyword.java` — `record SnackKeyword(String term, String definition)`; javadoc says "at most 3 words".
- `backend/src/main/java/com/endia/interfaces/rest/dto/SnackKeywordDto.java` — javadoc says "at most 3 words, guaranteed by service normalization".
- `backend/src/main/resources/prompts/generate-snack.md` — the externalized, provider-agnostic prompt. Its example JSON uses `"definition": "at most three words"` and its explicit rule says "at most 3 words (never more than three words)".
- `backend/src/main/resources/prompts/generate-meaning-snack.md` — the meaning-snack prompt, which describes the supplied definition as a "very short definition" / "short definition".
- `backend/src/test/java/com/endia/application/service/SnackServiceTest.java` — `overLimitDefinitionIsTruncatedToFirstThreeTokens` asserts the 3-token truncation.
- `backend/src/test/java/com/endia/application/service/MeaningSnackServiceTest.java` — `definitionIsTruncatedToFirstThreeTokens` asserts the 3-token truncation for the source definition.
- `backend/src/test/java/com/endia/infrastructure/llm/GenerateSnackPromptTest.java` — `promptLimitsDefinitionsToThreeWordsAndGuidesTwoToFive` asserts the prompt states the 3-word limit.

**Frontend**
- `frontend/src/app/models/snack.model.ts` — `SnackKeyword` comment says "at most 3 words".
- `frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.{html,scss}` — the standalone, OnPush, signals-first card that shows `term` + `definition`, rendered with escaped interpolation only (no `[innerHTML]`, no `DomSanitizer`); `overflow-wrap: anywhere`, width clamped to `min(280px, calc(100vw - 16px))`, bottom-sheet fallback on narrow viewports.
- `frontend/src/app/components/rich-text/keyword-highlighter.ts` + `rich-text.component.{html,scss}` — the highlight pass and the highlighted-keyword button (`data-definition` carries the definition); terms are single words and unaffected by definition length.
- `frontend/e2e/snack-keywords.spec.ts` — end-to-end coverage of the keyword highlight/definition-card flow; comments reference the "≤3-word definition".

**Confirmed interpretation:** the cap applies **only** to keyword meanings — i.e. `SnackKeyword.definition` and the meaning-snack **source definition** derived from it. It does **not** touch the snack content fields (`concept`, `metaphor`, `whyItMatters`), the `related` list (whose separate 2–3 **item** cap is a different rule and is unchanged), the keyword count guideline (2–5), or any quiz behavior.

**Out of scope (explicit):** the custom-selection "Define" flow (Task 11) and "Create snack" from a custom keyword (Task 12). This feature adds no new endpoint, no new entity, no new user action, and no schema change; the definition is already stored as free text/JSON.

## Project Structure Impact

**The existing layered layout does not change.** This is a constant/policy change plus documentation and tests, not a structural change.

- **Backend** (`src/main/java/com/endia/`): only the two normalization helpers in `application/service` change their bound (3 → 20); the domain record and DTO javadoc in `domain/model` and `interfaces/rest/dto` are corrected; the prompt in `resources/prompts/` is updated. No new classes, no layer changes, dependency direction preserved.
- **Frontend** (`src/app/`): only a comment in `models/` is corrected; the definition card and highlight rendering are **verified** for readability at up-to-20-word definitions (no new component, no structural change, no security-model change).
- **Data**: **no schema change.** Definitions are stored as text within the existing keyword JSON; previously stored 3-word definitions remain valid and unchanged (no migration).
- **Security**: the feature-012/013/014 rendering model is preserved unchanged — escaped interpolation only, no `[innerHTML]`, no `DomSanitizer`.
- **No deviation from the constitution**: the LLM flow stays externalized and provider-agnostic (no provider-specific logic in services); generated content is still validated/normalized at the service boundary.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Richer keyword meanings survive from generation to display (Priority: P1)

A learner reading a snack sees keywords whose definitions can now be meaningfully longer — up to 20 words — instead of being squeezed into a 3-word gloss. When the model produces a definition longer than 3 words (for example 8 or 15 words), that definition is stored and returned in full and shown in the definition card; it is no longer silently shortened to 3 words.

**Why this priority**: This is the entire point of the change and the source of the user value. It is independently testable by generating/persisting a snack whose keyword definition has between 4 and 20 words and confirming the full definition reaches the card. P1 because every other story only makes sense once longer definitions are actually preserved.

**Independent Test**: Produce a keyword definition of, say, 12 words, take it through generation → persistence → API → definition card, and confirm all 12 words are present at every step and rendered in the card. Delivers the value of "the meaning of a key term is explained well, not truncated."

**Acceptance Scenarios**:

1. **Given** the model emits a keyword definition of 4 to 20 words, **When** the snack is normalized and persisted, **Then** the definition is stored unchanged (no truncation to 3 words).
2. **Given** a stored keyword definition of 4 to 20 words, **When** the snack is returned by the API, **Then** the full definition is returned unchanged.
3. **Given** a snack with such a definition is displayed, **When** the user opens the definition card for that keyword, **Then** the card shows the full definition (all words up to 20).
4. **Given** a meaning snack is generated from a source definition of 4 to 20 words, **When** it is generated, **Then** the source definition passed into generation is not truncated to 3 words.
5. **Given** a keyword definition of exactly 20 words, **When** it is normalized, **Then** it is kept in full (20 is valid, inclusive).

---

### User Story 2 — Oversized definitions are still safely bounded (Priority: P1)

The system keeps a hard upper bound: a definition longer than 20 words is safely capped at the first 20 words before it is stored or returned, so no unbounded text is ever persisted or served. The boundary is exact and consistent between regular snack keywords and meaning-snack source definitions.

**Why this priority**: Raising the cap must not remove the bound. This is the data-integrity/safety half of the same change and is independently testable at the 19/20/21-word boundary. P1 because an unbounded definition is a regression the feature must prevent.

**Independent Test**: Normalize definitions of 19, 20, and 21 words and confirm 19 and 20 are kept in full while 21 is truncated to exactly its first 20 words; confirm the same rule for the meaning-snack source definition. Delivers the value of "richer meanings, still bounded."

**Acceptance Scenarios**:

1. **Given** a definition of 19 words, **When** it is normalized, **Then** all 19 words are kept.
2. **Given** a definition of exactly 20 words, **When** it is normalized, **Then** all 20 words are kept.
3. **Given** a definition of 21 or more words, **When** it is normalized, **Then** it is truncated to exactly the first 20 whitespace-separated tokens.
4. **Given** an oversized source definition for a meaning snack, **When** it is normalized, **Then** it is capped at 20 tokens by the same rule used for regular snack keywords.
5. **Given** a blank or empty definition (including one that becomes empty after trimming), **When** it is normalized, **Then** it is still dropped (the existing blank-handling behavior is unchanged).
6. **Given** the change, **When** it is applied, **Then** no stored or returned definition exceeds 20 words.

---

### User Story 3 — Definition card and highlight stay readable at up to 20 words (Priority: P2)

With longer definitions, the definition card and the highlighted-keyword rendering stay readable: the text wraps within the card, there is no horizontal overflow and no clipping, the card remains usable on desktop and mobile widths, and features 013/014 (highlight, open/dismiss, "Generate snack", meaning snacks) are not regressed. Definitions are still rendered as safe escaped text.

**Why this priority**: Longer text can break a layout that was tuned for a 3-word gloss. This is a quality/non-regression concern that can be verified independently of generation. P2 because the primary value (US1/US2) does not depend on it, but shipping a clipped card would be a visible defect.

**Independent Test**: Render a definition card and highlighted keywords for a 20-word definition (including a very long single token, Unicode/RTL, and markup-like text) at desktop and mobile widths, and confirm the content wraps, nothing overflows horizontally or is clipped, the close/dismiss and "Generate snack" controls still work, and nothing executes. Delivers the value of "long meanings that still read cleanly."

**Acceptance Scenarios**:

1. **Given** a definition of up to 20 words, **When** the definition card renders, **Then** the full definition is visible, wrapped within the card, with no horizontal overflow and no clipping.
2. **Given** the same definition, **When** it renders at a narrow (mobile) width, **Then** the card uses its existing narrow-viewport presentation and remains fully readable with no horizontal overflow.
3. **Given** a definition containing a single very long token or Unicode/RTL/emoji text, **When** it renders, **Then** it wraps within the card and does not break the layout.
4. **Given** a definition containing markup-like or injection payloads (e.g. `<script>`, `onerror=`, `javascript:`), **When** it renders, **Then** it is shown as inert escaped text and nothing executes or is injected.
5. **Given** features 013/014, **When** the feature ships, **Then** the highlight, click-to-open, dismiss (outside-click/close/Escape), focus handling, and "Generate snack" behavior are unchanged.
6. **Given** a definition generated from the updated prompt, **When** it is inspected, **Then** it is no longer pre-shortened to 3 words and may use up to 20 words.

---

### Edge Cases

- **Exactly at the boundary**: 19 words kept; 20 words kept; 21 words truncated to the first 20; 3 words and fewer are now simply valid short definitions (no special 3-word rule remains).
- **Whitespace variants**: tabs, multiple spaces, or newlines inside a definition are treated as token separators; a definition that is only whitespace is dropped.
- **Blank/empty definitions**: still dropped by the existing normalization (never persisted as an empty meaning).
- **Hyphenated compounds and punctuation**: counted as a single token (unchanged from the feature-013 word-count rule); punctuation does not split tokens.
- **Very long single token**: a single token longer than a normal word counts as one token (within the 20-token bound) and must wrap in the card rather than overflow.
- **Long definitions and card height**: the definition card grows naturally to fit an up-to-20-word definition; no max-height or internal scroll is introduced now. The card MUST stay within the viewport horizontally and MUST NOT clip its content. If later verification shows clipping at realistic viewport sizes, adding a bounded max-height with internal scroll is the agreed fallback.
- **Previously stored 3-word definitions**: remain valid, are returned as-is, and still render correctly (no migration, no re-normalization surprises).
- **Meaning-snack source definition of up to 20 words**: accepted and passed into generation without truncation to 3.
- **20 tokens but more than 200 characters**: a valid 20-token meaning-snack source definition can still exceed the `sourceDefinition` column's 200-character capacity (e.g. long tokens). It MUST be bounded to at most 200 characters **on a word boundary** (no word is split) before it is persisted, in addition to the 20-token cap, so the persisted value always fits the column with no schema change. Regular snack `SnackKeyword.definition` lives in a JSON column and is not subject to this character bound.
- **The `related` list**: its separate 2–3 **item** cap is a different rule and MUST NOT be affected or conflated with the 20-word definition cap.
- **Injection payloads in a longer definition**: still neutralized as escaped text; no execution, no DOM injection, no layout breakage.
- **Unicode, emoji, and RTL text** in a long definition: display remains readable and correctly wrapped.
- **Prompt/docs consistency**: no stale "at most 3 words" statement remains in the changed surfaces; the prompt's example JSON and explicit rule both state 20.
- **Non-regression of 013/014**: keyword count guideline (2–5), term-verbatim matching, no-re-roll-on-keyword-issues, meaning-snack generation, and the no-quiz invariant all remain unchanged.

## Requirements *(mandatory)*

### Functional Requirements

**Length policy (3 → 20 words)**

- **FR-001**: The system MUST accept, store, and return keyword definitions (`SnackKeyword.definition`) of up to and including 20 whitespace-separated tokens; the previous 3-token cap MUST no longer apply.
- **FR-002**: A keyword definition longer than 20 tokens MUST be normalized (truncated) to its first 20 whitespace-separated tokens before it is persisted or served; no definition exceeding 20 tokens MAY be stored or returned.
- **FR-003**: The same 20-token rule MUST be applied consistently to the meaning-snack source definition, matching the rule used for regular snack keywords.
- **FR-004**: A generated definition between 4 and 20 words MUST survive generation → persistence → API → display without being truncated to 3 words.
- **FR-005**: The word count MUST be whitespace-separated tokens, consistent with the existing feature-013 rule; hyphenated compounds count as one token.
- **FR-006**: Blank, empty, or whitespace-only definitions MUST still be dropped, and the existing trim, de-duplication (case-insensitive, first-wins), and order-preserving behavior MUST be unchanged.
- **FR-007**: The 20-token cap MUST apply only to keyword meanings and meaning-snack source definitions; snack content fields (`concept`, `metaphor`, `whyItMatters`) and the `related` list's separate 2–3 item cap MUST NOT be changed.

**Generation prompt and documentation**

- **FR-008**: The snack-generation prompt MUST instruct the model to produce keyword definitions of at most 20 words, with both the example JSON and the explicit rule updated (no instruction to shorten to 3 words).
- **FR-009**: Documentation/comments/javadoc that state the old 3-word limit MUST be updated to state the 20-word limit (`SnackKeyword`, `SnackKeywordDto`, the frontend `snack.model.ts`, and any equivalent in-code statement changed by this feature).
- **FR-010**: The generation flow MUST remain externalized and provider-agnostic; no provider-specific logic may be introduced into services.

**Rendering and readability**

- **FR-011**: The definition card MUST display the full definition (up to 20 words) with escaped interpolation only — no `[innerHTML]` and no `DomSanitizer`.
- **FR-012**: The definition card and highlighted-keyword rendering MUST remain readable at up to 20 words: text wraps within the card, there is no horizontal overflow and no clipping, and the card remains usable at desktop and narrow (mobile) widths.
- **FR-013**: Markup or injection payloads in a definition (including `<script>`, event handlers such as `onerror`, `javascript:` URLs, `<iframe>`, `<img>`, `<style>`, and inline styles) MUST NOT be executed or injected into the DOM.
- **FR-014**: The feature-012/013/014 sanitization model (render-time allowlist, no `[innerHTML]`, no `DomSanitizer`, escaped interpolation) MUST be preserved unchanged.

**Tests and non-regression**

- **FR-015**: Tests MUST cover the boundary at 19, 20, and 21 words for both keyword definitions and the meaning-snack source definition; tests that previously asserted the 3-word cap MUST be updated to the 20-word rule. A focused frontend test/assertion MUST verify that a 20-word definition wraps within the definition card with no horizontal overflow (making SC-004 objectively verifiable).
- **FR-016**: Existing backend and frontend suites MUST pass, and feature 013/014 behavior (highlight, click-to-open, dismiss/focus, keyword count guideline, term-verbatim matching, no re-roll on keyword issues, meaning-snack generation, and the no-quiz invariant) MUST NOT be regressed.
- **FR-017**: The change MUST NOT add a new endpoint, entity, user action, or schema change; previously stored definitions MUST remain valid and unchanged.
- **FR-018**: The meaning-snack prompt (`generate-meaning-snack.md`) MUST be updated so its description of the supplied definition no longer calls it "very short"/"short" and instead reflects a source definition of up to 20 words (wording only; its JSON shape and content rules MUST NOT change).
- **FR-019**: In addition to the 20-token cap, a meaning-snack source definition MUST never exceed **200 characters** when persisted: if a valid up-to-20-token definition would exceed 200 characters, it MUST be bounded to at most 200 characters **on a word boundary** (a word MUST NOT be split) before persistence. This bound applies only to the meaning-snack source definition (stored in a 200-character column); `SnackKeyword.definition` (JSON column) is unconstrained by it and MUST NOT receive a character cap. No schema change is introduced.

### Key Entities *(include if feature involves data)*

- **SnackKeyword** (feature 013): A key term of a snack with a plain-text, untrusted `definition` that is now at most **20** whitespace-separated tokens (previously 3). Stored within the snack's keyword list.
- **Keyword definition ("meaning")**: The user-facing gloss shown on the definition card and used as the seed for a meaning snack; its maximum length changes from 3 to 20 words, with over-limit values truncated to 20.
- **Meaning-snack source definition** (feature 014): The copy of the keyword definition attached to a generated meaning snack; normalized under the same 20-token rule and additionally bounded to ≤200 characters on a word boundary (the `sourceDefinition` column stays `VARCHAR(200)`; no schema change).
- **Definition card** (UI concept): The dismissible card/popover that reveals a keyword's definition without navigating away; must remain readable with up-to-20-word definitions.
- **Word-count rule** (concept): A definition is at most 20 whitespace-separated tokens; over-limit definitions are truncated to the first 20 and blank definitions are dropped.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of generated keyword definitions between 4 and 20 words are persisted, returned, and displayed in full; 0 are truncated to 3 words.
- **SC-002**: 0 stored or returned keyword definitions exceed 20 whitespace-separated tokens.
- **SC-003**: Boundary tests at 19, 20, and 21 words pass for both regular snack keywords and the meaning-snack source definition (19 and 20 kept, 21 truncated to 20).
- **SC-004**: 100% of definition cards render an up-to-20-word definition with no horizontal overflow and no clipping, at both desktop and narrow (mobile) widths.
- **SC-005**: 0 executable or injected markup from any definition: injection payloads execute 0 times and inject 0 nodes.
- **SC-006**: The existing backend and frontend suites pass unchanged, and feature 013/014 behavior is not regressed.
- **SC-007**: The generation prompt and the changed documentation/comments state the 20-word limit; 0 stale "at most 3 words" statements remain in the surfaces changed by this feature.
- **SC-008**: 0 persisted meaning-snack source definitions exceed 200 characters; a valid 20-token definition longer than 200 characters is bounded to ≤200 characters on a word boundary with 0 words split, with no schema change.

## Non-Goals

- **No change to snack content or quiz behavior**: `concept`, `metaphor`, `whyItMatters`, quiz generation/content/scoring, XP/accuracy, and the batch/progressive-delivery flow are untouched.
- **No change to the `related` list rule**: its separate 2–3 item cap is unaffected and must not be conflated with the 20-word definition cap.
- **No change to the keyword count guideline or matching rules**: 2–5 keywords, term-verbatim matching, and no-re-roll-on-keyword-issues are preserved.
- **No new endpoint, entity, user action, or schema change**: this is a bound/documentation change, not a new capability.
- **No custom-selection "Define" flow (Task 11) and no "Create snack" from a custom keyword (Task 12)**.
- **No redesign of the definition card or highlight rendering** beyond keeping them readable with longer text; the existing structure and design tokens are preserved.
- **No Markdown/HTML dialect**: definitions remain plain escaped text; markup is neutralized, not rendered.
- **No migration of previously stored definitions**: existing 3-word definitions remain valid as-is.

## Assumptions

- A "word" is a whitespace-separated token; hyphenated compounds count as one token, consistent with the feature-013 word-count rule.
- Over-limit definitions are **truncated to the first 20 words** at the service boundary, not rejected and not triggering a re-roll, mirroring the existing feature-013 behavior.
- 20 is a hard inclusive upper bound; there is no new minimum length beyond a non-empty definition.
- The change is a constant/policy change: no schema migration is required because definitions are stored as free text/JSON and previously stored values remain valid.
- The keyword count guideline (2–5), term-verbatim matching, and the no-re-roll-on-keyword-issues behavior are unchanged.
- The definition card's existing layout (width clamp, `overflow-wrap: anywhere`, bottom-sheet fallback on narrow viewports) is sufficient for readability; only verification is needed, not a redesign. The card **grows naturally** to fit a long definition — **no max-height or internal scroll is added now**; a bounded max-height with internal scroll is the agreed fallback only if later verification shows clipping.
- The frontend continues to render definitions as escaped interpolation only (no `[innerHTML]`, no `DomSanitizer`); the security model is preserved.
- The generation flow remains externalized and provider-agnostic; only prompt text changes, not provider logic.
- The meaning-snack prompt's "very short"/"short" description of the supplied definition is updated as a wording-only consistency fix (the source definition may now be up to 20 words); its JSON shape and content rules are unchanged.
- The meaning-snack `sourceDefinition` column remains `VARCHAR(200)` with no schema change; the persisted source definition is additionally bounded to ≤200 characters on a word boundary (never splitting a word), so a valid 20-token definition longer than 200 characters still fits. `SnackKeyword.definition` (JSON column) has no character bound.
- A focused frontend test/assertion for a 20-word definition wrapping with no horizontal overflow is added as the objective evidence for SC-004.
- The existing backend/frontend/E2E suites are the regression contract and are updated (not replaced) for the 20-word rule and boundary coverage.

## Clarifications

The following decisions were made to resolve open questions for this feature; all items are **confirmed** and no `[NEEDS CLARIFICATION]` markers remain. Decisions marked **ACCEPTED** were raised as open questions to the orchestrator and accepted by the product owner exactly as recommended.

1. **[ACCEPTED — scope of the cap]** The 20-word cap applies **only** to keyword meanings (`SnackKeyword.definition`) and the meaning-snack source definition. Snack content fields (`concept`, `metaphor`, `whyItMatters`) and the `related` list's separate 2–3 **item** cap are untouched. Rationale: the task targets "the keyword-definition (meaning) length cap" and warns "without changing snack behavior". Encoded in §Context & Scope, FR-007, §Non-Goals.
2. **[ACCEPTED — meaning-snack prompt wording]** `generate-meaning-snack.md` is updated **wording only** from "very short"/"short" to wording accurate for a source definition of up to 20 words; its JSON shape and content rules are unchanged. Encoded in FR-018, §Assumptions, §Context & Scope.
3. **[ACCEPTED — card grows naturally]** The definition card grows naturally to fit long definitions; **no max-height or internal scroll is added now**. A bounded max-height with internal scroll is the agreed fallback only if later verification shows clipping. Encoded in FR-012, §Edge Cases, §Assumptions, SC-004.
4. **[ACCEPTED — backward compatibility]** Definitions are stored as free text/JSON; previously stored 3-word definitions remain valid and are **not migrated**. Encoded in FR-017, §Project Structure Impact, §Assumptions, §Non-Goals.
5. **[ACCEPTED — focused frontend test]** One focused frontend test/assertion verifies that a 20-word definition wraps with no horizontal overflow, making SC-004 objectively verifiable. Encoded in FR-015, §Assumptions, SC-004.
6. **Over-limit handling (decision)**: definitions longer than 20 words are truncated to the first 20 whitespace-separated tokens at the service boundary, consistent with the existing 3-word rule (truncate, never reject/re-roll). Encoded in FR-002/FR-003, US2 AS3/AS4, §Edge Cases.
7. **Word-count rule (decision)**: a word is a whitespace-separated token; hyphenated compounds count as one. Encoded in FR-005, §Assumptions.
8. **Security model (decision)**: definitions remain escaped text rendered through the feature-012/013/014 safe model; no `[innerHTML]`, no `DomSanitizer`. Encoded in FR-013/FR-014, US3 AS4, SC-005.
9. **[ACCEPTED — storage bound for the meaning-snack source definition, option (a)]** The meaning-snack `sourceDefinition` column stays at `VARCHAR(200)` with **no schema change** (honoring FR-017). The stored source definition is safely bounded to at most **200 characters on a word boundary** (never splitting a word), in addition to the 20-token cap, so a valid 20-token definition whose text exceeds 200 characters still fits the column. Only the meaning-snack source definition needs this character bound; `SnackKeyword.definition` lives in a JSON column and remains unconstrained by it. Encoded in FR-019, §Edge Cases, §Assumptions, SC-002/SC-008.
