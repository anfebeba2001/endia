# Feature Specification: Colored Keywords with Click-to-Show Short Definition Cards

**Feature Branch**: `013-snack-keywords`

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "Have the snack output identify key terms with very short definitions (max 3 words each), render those terms in a distinct color, and show a short definition card when the user clicks a term." (Batch backlog Task 8.)

## Context & Scope *(established — do not re-investigate)*

This is an **end-to-end feature** spanning the snack generation prompt (backend), the persistence/API round-trip of a new `keywords` structure, and every surface that displays snack text (frontend): the snack reading UI, the end-of-test recap, **and the read-only history view (feature 011)**. It extends the snack JSON contract with a `keywords` list (each item a `term` plus a `definition` of at most 3 words) and adds a highlight + click-to-show definition card to the frontend. It builds directly on feature 012's sanitized rich-text renderer and preserves its security model.

The following current code state is treated as confirmed input and is referenced, not re-designed:

**Backend**
- `backend/src/main/resources/prompts/generate-snack.md` is the externalized snack prompt loaded by `LlmPromptTemplates.generateSnack(theme, subtopic)` (`{{theme}}`/`{{subtopic}}` substitution). It currently requires a JSON object with exactly `concept`, `metaphor`, `whyItMatters`, `related`, plus the feature-012 formatting rules (bold `**...**`, bullet `- `, short paragraphs).
- `OpenAiLlmProvider.generateSnack(...)` parses the model response with Jackson `readTree` and returns `LlmProvider.GeneratedSnack(concept, metaphor, whyItMatters, related)` (record defined in `backend/src/main/java/com/endia/application/port/LlmProvider.java`).
- `SnackService.generateValidSnack(...)` validates `concept`/`metaphor`/`whyItMatters` non-blank and `related` non-empty with ≤ 3 entries, re-rolls once, then raises `ServiceUnavailableException`. It builds `new Snack(null, concept, metaphor, whyItMatters, related)`.
- Domain entity `Snack` (`backend/src/main/java/com/endia/domain/model/Snack.java`) stores `concept`/`metaphor`/`whyItMatters` as `TEXT` and `related` as a JSON list via `JpaJsonConverter`. A JSON-object converter `JpaJsonObjectConverter` already exists (added in feature 001) for structured drag-drop JSON.
- Snack-returning surfaces / DTOs that carry snack text and must gain the new `keywords` field: `SnackGenerateApiResponse.SnackItem` (`SnackController`), `BatchStatusApiResponse.SnackDto` (`BatchController`; note `BatchService.BatchItemStatus` carries the raw `Snack` entity and needs no change), `QuizSummaryApiResponse.Recap.SnackDto` (results recap), `ThemeHistoryApiResponse.SnackItem` (`ThemeController`, feature 011), and `ThemeService.SnackHistory`.
- `Snack` response shape is currently `id, subtopicId, subtopicName, concept, metaphor, whyItMatters, related`.

**Frontend**
- `frontend/src/app/models/snack.model.ts` defines `Snack` with `id, subtopicId, subtopicName, concept, metaphor, whyItMatters, related`.
- `frontend/src/app/components/rich-text/` (feature 012) is the shared sanitized rich-text renderer: `rich-text.component.ts` (standalone, OnPush, input `text`), `rich-text.parser.ts` (pure parser producing a typed block/inline AST — paragraphs, lists, bold, text, break; NO `[innerHTML]`, no `DomSanitizer`; Angular interpolation escapes everything), `rich-text.model.ts`, plus html/scss.
- `app-rich-text` is used in `snacks.component.html` (concept/metaphor/whyItMatters), and the results and history pages.
- Snack card surface: `frontend/src/app/pages/snacks/snacks.component.{ts,html,scss}`. Results recap: `frontend/src/app/pages/results/`. Read-only history: `frontend/src/app/pages/history/`.

This feature adds (1) prompt instructions that make the model emit a `keywords` array of `{ term, definition }` items with definitions of at most 3 words, (2) persistence and API round-trip of that structure on every snack-returning surface, and (3) highlighting of key terms (distinct color) plus a click/tap-to-open, dismissible definition card on every snack-displaying surface. It does **not** change what a snack is, the generation flow, or the feature-012 formatting contract.

## Project Structure Impact

**The existing layered layout does not change.** This feature fits the current architecture without introducing new layers or restructuring modules.

- **Backend** (`src/main/java/com/endia/`): the new structured keyword type belongs in `domain/model` (a plain value object), the `Snack` entity in `domain/model` gains a `keywords` collection, validation/normalization belongs in `application/service` (`SnackService`), and the port record in `application/port` (`LlmProvider.GeneratedSnack`) is extended. A new `infrastructure/persistence/JpaSnackKeywordListConverter` mirrors the *pattern* of the already-existing `JpaJsonObjectConverter` for JSON persistence — it does **not** reuse that class, because JPA `AttributeConverter`s are statically typed per attribute (the existing one is `AttributeConverter<DragDropContent, String>` and cannot serve `List<SnackKeyword>`); no new infrastructure layer or adapter is needed. DTO/record changes stay in `interfaces/rest/dto` and `application/dto`. Dependency direction (domain → application → infrastructure → interfaces) is preserved.
- **Frontend** (`src/app/`): the `Snack` model in `models/` is extended; highlighting extends the feature-012 `components/rich-text/` typed AST and parser (still no `[innerHTML]`/`DomSanitizer`), and the definition card is a new **standalone** Angular component under `components/` (e.g. `components/keyword-definition-card/`), consistent with the existing standalone-component, signals-first, OnPush convention. No new routing or module structure.
- **Data**: one **additive, nullable** column on the `snack` table for the structured keyword list (stored as JSON via the new `JpaSnackKeywordListConverter`, which mirrors the existing JSON-object converter pattern). This is the only schema change; it is backward compatible so pre-existing snacks load with an empty keyword list.
- **No deviation from the constitution's Clean Architecture / LLM-Abstraction principles**: the prompt stays externalized and provider-agnostic; keyword extraction remains model output validated at the service boundary.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Each snack identifies its key terms with a very short definition (Priority: P1)

When a snack is generated, the model also identifies the few key terms in that snack and gives each a definition of at most 3 words (for example, `term: "mitosis", definition: "cell division"`). This keyword set travels with the snack through the API on every surface that returns snacks. The snack's existing fields are unchanged.

**Why this priority**: This is the source of the value. Without the model emitting key terms and their short definitions, there is nothing to highlight and nothing to show in a definition card. It is independently testable by inspecting the generation response and stored snack data. P1 because every downstream rendering benefit depends on it.

**Independent Test**: Generate snacks for several subtopics and confirm each non-trivial snack returns a `keywords` array whose items each have a non-empty `term` and a `definition` of at most 3 words, while the existing fields (`concept`, `metaphor`, `whyItMatters`, `related`) keep their current shape and validation. Delivers the value of "the snack tells the learner which terms matter and what they briefly mean."

**Acceptance Scenarios**:

1. **Given** a snack is generated for a subtopic, **When** its response is inspected, **Then** it contains a `keywords` array and each item has a non-empty `term` and a non-empty `definition` of at most 3 words.
2. **Given** the prompt is extended for keywords, **When** a snack is generated, **Then** `concept`, `metaphor`, `whyItMatters`, and `related` keep their existing shape and validation rules (non-empty text fields, `related` non-empty with ≤ 3 entries) unchanged.
3. **Given** the model emits a definition longer than 3 words, **When** the snack is persisted or served, **Then** the definition is normalized to at most 3 words before it is stored or returned.
4. **Given** the model emits a keyword with a blank term or a blank definition, **When** the snack is persisted or served, **Then** that invalid keyword is omitted and the snack is still accepted (no generation failure or re-roll caused by keywords).
5. **Given** the model emits keyword text containing markup or HTML, **When** the snack is persisted or served, **Then** the keyword text is stored and returned as plain text and is never interpreted as markup.
6. **Given** a snack with keywords is returned, **When** any snack-returning surface responds (fresh generation, batch status, results recap, or history), **Then** the `keywords` array is present with the same items.

---

### User Story 2 — Key terms appear in a distinct color inside the snack text (Priority: P1)

While reading a snack, the user sees each key term that appears in the snack text rendered in a color distinct from the surrounding body text, so the terms stand out at a glance. Terms that do not actually appear in the snack text are not highlighted, and snacks without keywords look exactly as they do today.

**Why this priority**: This is the visible half of the feature: it is what makes the identified terms discoverable in the reading flow. It is independently testable with fixed snack fixtures. P1 because without the highlight there is no affordance to open a definition card.

**Independent Test**: Render snacks whose text contains the keyword terms and confirm every occurrence is shown in a distinct color, that non-matching terms produce no highlight, and that a snack with an empty keyword list renders identically to today. Delivers the value of "key terms pop out while reading."

**Acceptance Scenarios**:

1. **Given** a snack whose rendered text contains a key term, **When** the snack renders, **Then** every occurrence of that term is displayed in a color distinct from the surrounding body text.
2. **Given** a keyword whose term does not appear in the rendered snack text, **When** the snack renders, **Then** that term is not highlighted anywhere and produces no visual artifact.
3. **Given** a snack with no keywords or an empty keyword list, **When** it renders, **Then** it looks exactly as it does today with no highlight, no artifact, and no error.
4. **Given** a key term appears inside bold text, **When** the snack renders, **Then** the term is both emphasized and highlighted, and the surrounding text and feature-012 formatting are not corrupted.
5. **Given** a term appears in the text with different letter casing, **When** the snack renders, **Then** it is still highlighted (matching is case-insensitive).
6. **Given** the same snack is shown in the snack card, the results recap, and the history view, **When** each renders, **Then** highlighting is applied consistently across all three.

---

### User Story 3 — Clicking/tapping a highlighted term shows a short definition card (Priority: P1)

When the user clicks or taps a highlighted term, a short card or popover opens showing that term's definition (at most 3 words). The user can dismiss the card by clicking outside it, using a close control, or pressing Escape, and doing so never navigates away from the snack. This works on both desktop and mobile widths.

**Why this priority**: This is the payoff of the highlight — the definition is only useful if it can be revealed on demand without leaving the reading flow. It is independently testable by interacting with a rendered snack. P1 because it is the core user-facing interaction requested by the task.

**Independent Test**: Render a snack with keywords, click/tap a highlighted term, confirm a card opens showing the correct ≤3-word definition, and confirm it closes via outside-click, close control, and Escape without navigation; repeat at desktop and mobile widths. Delivers the value of "instant, low-friction definitions."

**Acceptance Scenarios**:

1. **Given** a highlighted key term is visible, **When** the user clicks or taps it, **Then** a short card/popover opens showing that term's definition.
2. **Given** a definition card is open, **When** its content is inspected, **Then** it shows only that term's definition and never displays more than 3 words.
3. **Given** a definition card is open, **When** the user clicks or taps outside it, **Then** the card closes and the user remains on the snack.
4. **Given** a definition card is open, **When** the user activates the close control, **Then** the card closes and the user remains on the snack.
5. **Given** a definition card is open, **When** the user presses Escape, **Then** the card closes and the user remains on the snack.
6. **Given** the user dismisses the card by any supported means, **When** it closes, **Then** no navigation occurs and the snack remains displayed.
7. **Given** a desktop width and a mobile width, **When** the user clicks or taps a highlighted term, **Then** the card opens and is fully visible and usable at both widths.
8. **Given** a keyboard user, **When** they focus a highlighted term and activate it, **Then** the card opens, and Escape closes it and returns focus appropriately.
9. **Given** a card for one term is open, **When** the user activates a different highlighted term, **Then** the previous card closes and the new term's card opens.

---

### User Story 4 — Keyword text is always rendered as safe, escaped text (Priority: P1)

Keyword terms and definitions are untrusted model output. They are displayed as plain escaped text through the same safe allowlist mechanism as the rest of the snack, so markup or malicious content inside a keyword cannot execute or inject into the page. The feature-012 sanitization model is preserved unchanged.

**Why this priority**: Keyword text reaches the DOM in the user's browser and is generated by a language model; treating it as HTML would reintroduce the cross-site-scripting risk that feature 012 deliberately closed. P1 because it is a security invariant that must hold on every render, not an enhancement.

**Independent Test**: Feed keyword terms/definitions containing `<script>`, `<img onerror=...>`, `javascript:` links, `<iframe>`, inline styles, and other non-whitelisted markup, render the snack, and confirm nothing executes and only inert, readable text appears. Delivers the value of "helpful definitions without opening an injection hole."

**Acceptance Scenarios**:

1. **Given** a keyword term or definition contains raw HTML such as `<script>alert(1)</script>`, **When** the snack renders, **Then** the script does not execute and is not injected into the DOM.
2. **Given** keyword text contains an event handler or dangerous URL (e.g. `<img src=x onerror=...>`, `javascript:` link), **When** the snack renders, **Then** no handler runs and no dangerous URL is activated.
3. **Given** keyword text contains markup outside the whitelist (links, images, headings, tables, code, iframes, inline styles), **When** the snack renders, **Then** it is neutralized as inert text and never becomes an element.
4. **Given** keyword text contains escaped entities, **When** the snack renders, **Then** the intended characters are shown without double-escaping.
5. **Given** any snack-displaying surface, **When** keyword text is shown, **Then** it is produced through the safe allowlist mechanism (no untrusted HTML binding) and the feature-012 whitelist for body text is preserved unchanged.

---

### User Story 5 — Keywords round-trip and older snacks degrade gracefully (Priority: P2)

The keyword set the model produced survives storage and the API on every surface without being stripped, collapsed, or HTML-escaped. Snacks generated before this feature (with no keywords) load and render exactly as before, with no errors or visual artifacts, and the quiz/results/progressive-delivery flow is untouched.

**Why this priority**: The rendering story fails if the keyword data is mangled or if old snacks break, but this is a data-integrity and compatibility concern that is easy to verify independently of the UI. P2 because it hardens the feature rather than unlocking the visible value (US1–US3).

**Independent Test**: Generate or seed a snack with keywords, persist it, read it back through the API (including batch status, recap, and history surfaces), and confirm the keywords are returned unchanged (modulo the ≤3-word rule); load a pre-existing snack row with no keywords and confirm it renders normally. Delivers the value of "what the model produced is what the user's UI receives, and nothing old breaks."

**Acceptance Scenarios**:

1. **Given** a snack with keywords is persisted, **When** it is read back, **Then** the keyword terms and definitions are unchanged except for any over-limit definition normalized to at most 3 words.
2. **Given** keywords pass through the API, **When** the response is serialized, **Then** the shape is `[{ term, definition }]` and values are not stripped or HTML-escaped into literal entity characters.
3. **Given** a snack stored before this feature (no keywords), **When** it is loaded and rendered, **Then** it has an empty keyword list and renders exactly as before with no artifacts.
4. **Given** the schema change, **When** it is applied to existing data, **Then** pre-existing rows remain valid (the keyword field is optional/nullable) and no existing data is lost.
5. **Given** keywords are present on all snack-returning surfaces (fresh generation, batch status, results recap, history), **When** each responds, **Then** the keywords are present and round-trip consistently.
6. **Given** the existing flow (subtopics → snacks → quiz → results), **When** the feature ships, **Then** progressive delivery, retries, navigation, and quiz/results behavior are unchanged and the existing E2E suites pass unchanged.

---

### Edge Cases

- **Definition with exactly 3 words vs. 4+ words**: a definition of 3 whitespace-separated words is valid; 4 or more is normalized to the first 3 words before storage/serving.
- **Word-count rule ambiguity**: words are counted as whitespace-separated tokens; hyphenated compounds count as one token. Definitions that are blank after trimming are dropped.
- **Definition that becomes empty after normalization** (e.g. only punctuation/whitespace): the keyword is dropped rather than shown as an empty card.
- **Duplicate terms** in the model output (same term twice): de-duplicated to a single keyword so the user does not see two cards for the same term.
- **Term that is a substring of another term** (e.g. "cell" and "cell membrane"): the longer term wins on the overlapping span and highlights never overlap or nest.
- **Very short or very common term** (e.g. a one-letter term): treated literally and matched whole-word; it must not produce broken or excessive highlighting, and blank/whitespace terms are dropped.
- **Term containing characters that look like regular-expression syntax** (e.g. `C++`, `(x)`, `a.b`): treated as literal text, not as a pattern.
- **Term with leading/trailing whitespace or different casing**: trimmed and matched case-insensitively.
- **Term spanning a formatting boundary** (e.g. partly bold, or across a line break/bullet boundary): matching is confined to a contiguous text run and never crosses block boundaries; surrounding rich formatting is preserved.
- **Term that appears only in `related`** (not in `concept`/`metaphor`/`whyItMatters`): not highlighted, because only the snack body text is the rendered/highlighted scope.
- **A term appears multiple times** in the snack text: all occurrences are highlighted consistently.
- **Snack with no keywords or an empty keyword list** (including pre-existing snacks): renders normally with no highlight, card, error, or layout artifact.
- **All keywords invalid after normalization**: the snack is still accepted and renders with no keywords.
- **Keyword list unexpectedly large**: rendering remains stable and performant; no layout break.
- **Multiple cards / rapid interaction**: opening another term closes the previous card; repeated clicks/taps do not leave orphaned or stacked cards.
- **Card near a viewport edge on a narrow screen**: the card stays fully visible/usable (does not overflow off-screen).
- **Unicode, emoji, and RTL text in a term or definition**: matching and display still work and remain readable.
- **Keyword markup/injection payloads** (`<script>`, `onerror=`, `javascript:` URLs, `<style>`, `<iframe>`, `<img>`): no execution, no DOM injection, no layout breakage; readable text is preserved.
- **Escaped entities in stored keyword text** (e.g. `&lt;`, `&amp;`): displayed as the intended characters without double-escaping.
- **Same snack rendered in more than one place** (snack card, results recap, history): highlighting and the definition card behave consistently, and no surface falls back to raw interpolation.
- **Old snacks generated before the prompt change**: still render correctly with an empty keyword list.

## Requirements *(mandatory)*

### Functional Requirements

**Prompt / generation**

- **FR-001**: The snack generation prompt MUST instruct the model to emit a `keywords` array, where each item has a short `term` and a `definition` of at most 3 words.
- **FR-002**: The snack generation prompt MUST instruct the model to choose terms that are key terms of the snack and that appear verbatim in the snack text (`concept`, `metaphor`, `whyItMatters`).
- **FR-003**: The snack generation prompt MUST continue to require the existing fields `concept`, `metaphor`, `whyItMatters`, and `related` with their current shape and the feature-012 formatting rules unchanged.
- **FR-004**: The snack generation prompt MUST constrain keyword terms and definitions to plain text and MUST forbid markup and arbitrary HTML in them.
- **FR-005**: Generated snacks with non-trivial content MUST actually contain at least one keyword when key terms exist, and the presence, absence, or invalidity of keywords MUST NOT cause generation to fail or trigger a re-roll.

**Contract and normalization**

- **FR-006**: The snack JSON contract MUST expose `keywords` as an array of items shaped `{ term, definition }` on every snack-returning surface (fresh generation, batch status, results recap, and history), in addition to the existing fields.
- **FR-007**: No stored or returned definition MAY exceed 3 words; when the model emits a definition of more than 3 words, the system MUST normalize it to at most 3 words (the first 3 whitespace-separated tokens) before persisting or serving it.
- **FR-008**: A keyword whose term is blank/whitespace or whose definition is blank after trimming and normalization MUST be dropped; a snack with no valid keywords MUST be accepted with an empty keyword list and MUST NOT fail or re-roll.
- **FR-009**: Duplicate terms MUST be de-duplicated to a single keyword.
- **FR-010**: Keyword term and definition values MUST be treated as plain text; markup or HTML contained in them MUST be neutralized and never interpreted.

**Persistence**

- **FR-011**: Keywords MUST persist with the snack and round-trip through the API unmodified except for the FR-007 normalization, consistently on every snack-returning surface.
- **FR-012**: Persistence of keywords MUST be backward compatible: snacks stored before this feature (no keywords) MUST load as an empty keyword list, and the storage change MUST be additive and optional/nullable so existing data is not lost or invalidated.

**Rendering — highlight**

- **FR-013**: The frontend MUST render each key term that appears in the rendered snack text in a color distinct from the surrounding body text.
- **FR-014**: Only terms that actually appear in the rendered snack text (`concept`, `metaphor`, `whyItMatters`) MAY be highlighted; terms with no match MUST NOT be highlighted and MUST NOT produce artifacts.
- **FR-015**: Term matching MUST be case-insensitive, whole-word, and literal (term characters MUST NOT be interpreted as patterns), and MUST NOT corrupt the surrounding text or the feature-012 rich formatting.
- **FR-016**: When terms overlap on the same span, the longest term MUST win, and highlights MUST be non-overlapping and non-nested.
- **FR-017**: Every occurrence of a matched term in the rendered snack text MUST be highlighted.

**Rendering — definition card**

- **FR-018**: Clicking or tapping a highlighted term MUST open a short card/popover that displays that term's definition.
- **FR-019**: The definition card MUST display only that term's definition and MUST NOT display more than 3 words.
- **FR-020**: The definition card MUST be dismissible by (a) clicking/tapping outside it, (b) a visible close control, and (c) the Escape key, and dismissal MUST NOT navigate away from the snack or lose the current reading position.
- **FR-021**: Opening the card for another term MUST close any previously open card.
- **FR-022**: Highlighting and the definition card MUST function at both desktop and mobile widths, with the card fully visible and usable and no horizontal overflow.
- **FR-023**: A highlighted term MUST be activatable by keyboard as well as pointer, and dismissing the card MUST return focus appropriately.
- **FR-024**: A snack with no keywords or an empty keyword list MUST render exactly as before, with no highlight, no card affordance, no error, and no layout artifact.
- **FR-025**: Highlighting and the definition card MUST be applied consistently across all snack-displaying surfaces — the snack card, the results recap, and the read-only history view.

**Security / sanitization**

- **FR-026**: Keyword term and definition text MUST be rendered as escaped text through the same safe allowlist mechanism used for the rest of the snack; untrusted keyword text MUST NOT be bound as HTML.
- **FR-027**: Markup or arbitrary HTML in keyword text (including `<script>`, event handlers such as `onerror`, `javascript:` URLs, `<iframe>`, `<img>`, `<style>`, and inline styles) MUST NOT be executed or injected into the DOM.
- **FR-028**: When non-whitelisted markup in keyword text is neutralized, the underlying readable text MUST NOT be silently lost (it remains visible as safe plain text).
- **FR-029**: The feature-012 sanitization model for snack body text (render-time allowlist, no `[innerHTML]`, escaped interpolation) MUST be preserved unchanged.

**Non-regression and scope**

- **FR-030**: Existing snack fields, the results recap, the history view, and quiz behavior MUST continue to work unchanged; progressive delivery, retries, and snack navigation MUST be unchanged, and the existing E2E suites MUST pass unchanged.
- **FR-031**: The feature MUST NOT add the "Generate snack" action on the definition card or the "meaning snack" backbone entry (these belong to a separate backlog task), and MUST NOT change the generation flow, provider selection, or batch status semantics beyond the prompt wording and the new keyword data.

### Key Entities *(include if feature involves data)*

- **SnackKeyword** (new): A key term of a snack together with its very short definition (at most 3 words). Both `term` and `definition` are plain text and untrusted. A snack holds an ordered list of these; the list may be empty.
- **Snack** (extended): The existing knowledge card. Key attributes used here: `concept`, `metaphor`, `whyItMatters` (rich text, feature 012), `related` (plain labels), and the new `keywords` list. Its existing fields and validation are unchanged.
- **Snack JSON contract** (extended): The response shape shared by all snack-returning surfaces, now including `keywords: [{ term, definition }]` alongside the existing fields.
- **Keyword highlight** (UI concept): The presentation of a matched key term in a distinct color inside the rendered snack text; only terms present in the body text are highlighted.
- **Definition card** (UI concept): The short, dismissible card/popover that reveals a highlighted term's ≤3-word definition without navigating away.
- **Word-count rule** (concept): The rule that a definition is at most 3 whitespace-separated tokens; over-limit definitions are normalized and blank definitions are dropped.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of newly generated snacks with non-trivial content expose at least one keyword with a non-empty term and a non-empty definition; 0 stored or returned definitions exceed 3 words.
- **SC-002**: 100% of key terms present in the rendered snack text are highlighted in a color distinct from body text; 0 terms that do not appear in the rendered text are highlighted.
- **SC-003**: 100% of highlighted terms open a card showing the correct definition on click/tap; 0 cards display more than 3 words.
- **SC-004**: 100% of open cards dismiss via outside-click/tap, the close control, and Escape; 0 dismissals cause navigation away from the snack.
- **SC-005**: 100% of highlight-and-card interactions function at desktop and mobile widths; 0 cases of the card overflowing off-screen or breaking layout.
- **SC-006**: 0 errors, highlights, or visual artifacts for snacks with missing or empty keywords, including pre-existing stored snacks (100% render as before).
- **SC-007**: 0 executable or injected markup from keyword text across all snack-displaying surfaces: injection payloads (`<script>`, `onerror=`, `javascript:` URLs, `<iframe>`, inline styles) execute 0 times and inject 0 nodes.
- **SC-008**: 100% of keyword data round-trips through persistence and the API on every snack-returning surface (fresh generation, batch status, results recap, history) with 0 stripped, escaped, or extra fields, except for FR-007 normalization.
- **SC-009**: Non-regression: the existing snack/quiz/results/progressive-delivery/history E2E suites pass unchanged, and the feature-012 rich formatting (bold, bullets, short paragraphs) remains intact on every surface.

## Non-Goals

- **No "Generate snack" action on the definition card** and **no "meaning snack" backbone entry** — these belong to the separate backlog Task 9 and MUST NOT be built here.
- **No change to the snack generation flow, provider selection, batch/progressive delivery, or retries** beyond the prompt wording and the new keyword data.
- **No support for a full Markdown/HTML dialect in keywords**: keyword text is plain; any markup is neutralized, not rendered.
- **No rich-text formatting inside keyword terms or definitions** beyond being escaped plain text.
- **No keyword authoring/editing by the user**: keywords are read-only generated content.
- **No retro-generation of keywords for previously stored snacks**: old snacks simply have an empty keyword list and render as before.
- **No change to quiz content, scoring, XP, accuracy, or navigation.**
- **No redesign of the snack card layout** beyond adding the highlight color and the definition card; existing structure and design tokens are preserved.
- **No change to the history view's read-only nature or data shape** beyond adding `keywords`; feature 011's behavior is preserved.
- **No new endpoints**: the existing snack-returning surfaces are extended in place.

## Assumptions

- The prompt will request a small set of key terms (a reasonable default of 2–5 per snack); the exact count is a prompt-level guideline, not a hard contract.
- A definition is counted in whitespace-separated tokens; "at most 3 words" means at most 3 such tokens, with hyphenated compounds counting as one token.
- When the model exceeds the limit, the system **normalizes (truncates) to the first 3 words** rather than rejecting or re-rolling, so a keyword quality issue never regresses the core snack generation.
- Keywords are optional at the contract level: an empty or missing keyword list is valid and MUST NOT cause a generation failure, preserving graceful degradation.
- Term matching is case-insensitive, whole-word, literal, and confined to the snack body text (`concept`, `metaphor`, `whyItMatters`); `related` remains plain labels and is never a highlight source.
- The existing stack is unchanged: Java 21 + Spring Boot backend, Angular 21 frontend, MySQL 8. The structured keyword list is stored as JSON via a new `JpaSnackKeywordListConverter` that mirrors the *pattern* of the existing `JpaJsonObjectConverter` (the existing class is not reused, since JPA `AttributeConverter`s are statically typed per attribute); the only schema change is one additive, nullable column.
- The backend normalizes over-limit definitions and drops invalid keywords at the service boundary; the frontend renders whatever valid keywords it receives through the safe allowlist mechanism and never binds keyword text as HTML.
- The snack card, the results recap, and the history view all render snack text and keywords through the same shared, sanitized rendering path, so adding keywords to the history view requires no new backend surface or data-shape change beyond the DTO field.
- The feature-012 rich-text parser/model is extended to represent highlighted keyword spans in its typed AST, preserving the no-`[innerHTML]`/no-`DomSanitizer` security boundary.
- The existing E2E suites remain the regression contract; history tests (feature 011) and rich-text tests (feature 012) are extended to cover keyword highlighting and the definition card rather than replaced.

## Clarifications

The following decisions were made to resolve open questions for this feature; they are encoded above and flagged for orchestrator/user confirmation (see report). No blocking `[NEEDS CLARIFICATION]` markers remain.

1. **Over-limit definition handling (decision)**: definitions longer than 3 words are **normalized by truncation to the first 3 words** at the service boundary, not rejected and not triggering a re-roll. Rationale: keywords are an enhancement layered on a working generation flow; failing or re-rolling the whole snack for a keyword formatting slip would regress reliability. Encoded in FR-007, US1 AS3, §Edge Cases, SC-001.
2. **Keywords are optional / empty is valid (decision)**: a snack with no valid keywords is accepted with an empty list (no generation failure or re-roll), preserving graceful degradation for pre-existing and model-non-compliant snacks. Encoded in FR-005, FR-008, US1 AS4, US5 AS3, SC-006.
3. **Matching rule and highlight scope (decision)**: matching is case-insensitive, whole-word, literal, non-overlapping with longest-term precedence, all occurrences highlighted, and scoped to `concept`/`metaphor`/`whyItMatters` only (never `related`). Encoded in FR-014–FR-017, US2 AS2/AS5, §Edge Cases.
4. **Word counting (decision)**: a definition is at most 3 whitespace-separated tokens; hyphenated compounds count as one token; blank definitions are dropped. Encoded in FR-007, FR-008, §Edge Cases, §Assumptions.
5. **Keyword count (decision)**: the prompt asks for a small set (default 2–5); this is a guideline, and the contract accepts any non-negative count. Encoded in FR-001, §Assumptions.
6. **Persistence change (decision)**: one additive, nullable JSON column on the `snack` table (persisted by a new `JpaSnackKeywordListConverter` that mirrors the existing JSON-object converter pattern, not the existing class) — the only schema change, required for the structured keyword list and chosen to keep pre-existing snacks valid. Encoded in FR-011, FR-012, §Project Structure Impact, §Assumptions.
7. **Frontend component (decision)**: the definition card is a new standalone Angular component under `components/`, and highlighting extends the feature-012 `components/rich-text/` typed AST/parser; no layout or module restructuring. Encoded in §Project Structure Impact, FR-026, FR-029.
