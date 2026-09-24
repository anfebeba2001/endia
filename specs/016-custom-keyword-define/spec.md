# Feature Specification: Custom Keyword "Define" from a Held Text Selection (2-Second Trigger)

**Feature Branch**: `016-custom-keyword-define`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "Let the user select words in the snack reading content; after the selection is held for 2 seconds a 'Define' option appears; activating it generates the meaning for the selected term and shows it in the definition card." (Batch backlog Task 11, from item 13.)

## Context & Scope *(established — do not re-investigate)*

This is an **end-to-end feature** spanning the snack reading content (frontend selection + a transient affordance), a new provider-agnostic generation capability (backend), and the existing definition card (frontend display). It builds directly on the colored-keyword highlight and click-to-show definition card of feature `013-snack-keywords`, the meaning-snack generation flow of `014-meaning-snacks`, and the keyword-meaning length policy of `015-keyword-meaning-20-words` (definitions up to **20 words**). It lets the learner define **any** term they select in the reading content, not only the model-highlighted keywords.

The task that feature 015 explicitly deferred is this feature:

> Feature 015, **Out of scope (explicit)**: "the custom-selection 'Define' flow (Task 11) and 'Create snack' from a custom keyword (Task 12)."

This feature adds (1) a 2-second hold trigger on a text selection inside a snack's reading content, (2) a transient, selection-anchored "Define" affordance, (3) a new backend capability that generates a meaning/definition for an arbitrary selected term through the existing externalized, provider-agnostic LLM flow, and (4) display of the generated meaning in the existing single definition card. It does **not** change what a snack is, the existing keyword highlight/click behavior, the meaning-snack flow, or the generation/persistence contract beyond adding the new capability.

**Confirmed content contract (see §Clarifications #1/#2/#3).** The generated result is a **short definition** bounded to at most **20 words**, consistent with the feature-015 keyword-meaning model (not a long multi-paragraph explanation). A selected term is normalized and bounded to at most **10** whitespace-separated tokens. The selection-"Define" trigger is offered **only** on the snack reading page (`snacks.component`) reading content; the results recap and read-only history rich-text surfaces do **not** offer the trigger and MUST remain unchanged.

The following current code state is treated as confirmed input and is referenced, not re-designed:

**Backend**
- `backend/src/main/java/com/endia/application/port/LlmProvider.java` — the provider-agnostic port. Existing methods: `suggestSubtopics`, `generateSnack`, `generateQuiz`, `generateMeaningSnack`, plus the `GeneratedSnack` / `GeneratedMeaningSnack` / `GeneratedQuestion` records. There is **no** method that returns a single generated *definition/meaning* for an arbitrary term.
- `backend/src/main/java/com/endia/infrastructure/llm/OpenAiLlmProvider.java`, `RetryingLlmProvider.java`, `LlmPromptTemplates.java` — the provider implementations and the externalized-prompt loader. Prompts live as markdown under `backend/src/main/resources/prompts/` (`generate-snack.md`, `generate-quiz.md`, `generate-meaning-snack.md`, `suggest-subtopics.md`).
- `backend/src/main/java/com/endia/application/service/MeaningSnackService.java` + `backend/src/main/java/com/endia/interfaces/rest/MeaningSnackController.java` (`/api/meaning-snacks`) — already generate a full **meaning snack** (concept/metaphor/whyItMatters/related) from a keyword `term` + `definition`, validating/normalizing at the service boundary, re-rolling once, then raising a typed `ServiceUnavailableException`; `normalizeDefinition(...)` enforces the 20-token cap (feature 015) and the 200-character source-definition clamp. This is the closest existing capability, but it returns a **full snack**, not a short definition.
- `backend/src/main/java/com/endia/domain/exception/` (`ServiceUnavailableException`, `LlmGenerationException`, `EntityNotFoundException`, `DomainException`) and `backend/src/main/java/com/endia/infrastructure/exception/GlobalExceptionHandler.java` — errors are mapped to `ErrorResponse` shapes (400/404/503); controllers do not handle errors themselves.
- Schema is managed by Hibernate `ddl-auto: update` (no migration tool).

**Frontend**
- `frontend/src/app/components/rich-text/` — the shared, sanitized rich-text renderer (feature 012, extended by 013): a typed block/inline AST (`rich-text.model.ts`, `rich-text.parser.ts`, `keyword-highlighter.ts`), rendered with template bindings only — deliberately **no** `[innerHTML]`, **no** `DomSanitizer`. `rich-text.component.ts` exposes `text` and `keywords` inputs and a `keywordSelected` output. It is reused by the snack card, the results recap, and the history view.
- `frontend/src/app/components/keyword-definition-card/` — the single, standalone, OnPush, signals-first definition card. It takes `keyword: SnackKeyword | null`, `anchor`, `generateEnabled`, `generateState`, `generateError`; emits `generateRequested` (feature 014) and `closed`; renders `term` + `definition` with escaped interpolation only; positions itself viewport-clamped (bottom-sheet on narrow screens).
- `frontend/src/app/pages/snacks/snacks.component.{ts,html,scss}` — the snack reading page and the host of the single definition card via `activeKeyword` / `activeKeywordAnchor` + `openKeyword(event)` / `closeKeyword()`. It already owns `generateStates`, `generateError`, and the feature-014 "Generate snack" wiring.
- Results recap (`frontend/src/app/pages/results/`) and read-only history (`frontend/src/app/pages/history/`) also render snack text through `app-rich-text`.
- `frontend/src/app/models/snack.model.ts` defines `Snack` and `SnackKeyword { term, definition }` (comment: definition up to 20 words).
- Existing E2E suites include `frontend/e2e/snack-keywords.spec.ts`, `meaning-snacks.spec.ts`, `snack-rich-formatting.spec.ts`, and others — the regression contract.

**Design intent (encoded below).** A new user journey: *select text → hold ~2s → "Define" affordance → generate meaning → definition card shows it.* The affordance is **transient and selection-anchored** and must not interfere with the existing single-definition-card rule or with existing highlighted-keyword clicks. The backend gains a new provider-agnostic "generate a short definition/meaning for a term" capability (new prompt template + `LlmProvider` method + `RetryingLlmProvider` delegation + a service + a new endpoint), validated/normalized at the service boundary. All selected and generated text is **untrusted** and is rendered escaped only. Features 012/013/014/015 and the existing E2E must not regress. Task 12 ("Create snack" from the custom meaning) is explicitly **out of scope** and only noted as future work.

## Project Structure Impact

**The existing layered layout does not change.** This feature fits the current architecture without introducing new layers or restructuring modules.

- **Backend** (`src/main/java/com/endia/`): the new generated-definition value type is a provider-agnostic record on the existing `application/port` `LlmProvider` (mirroring `GeneratedSnack`/`GeneratedMeaningSnack`); the prompt is a new externalized markdown file under `resources/prompts/` loaded through `LlmPromptTemplates` (LLM-Abstraction principle — no provider-specific logic in services); the generation/validation orchestration belongs in `application/service`; the new request/response DTOs live in `interfaces/rest/dto` and the new endpoint in `interfaces/rest`; the `RetryingLlmProvider` delegates the new method. Dependency direction (domain → application → infrastructure → interfaces) is preserved. The existing `GlobalExceptionHandler` shapes are reused for errors.
- **Frontend** (`src/app/`): selection detection and the transient, selection-anchored "Define" affordance are added without breaking the feature-012 typed-AST security boundary (no `[innerHTML]`, no `DomSanitizer`); the affordance is a small standalone OnPush signals-first component under `components/`; the generated meaning is shown through the existing single `keyword-definition-card/` component (hosted by the page), which keeps its open/dismiss/focus behavior. Models under `models/` are extended with the new definition request/response shape. No new routing or module structure.
- **Data**: **no schema change.** The generated definition is produced on demand and shown in the card; nothing new is persisted for this feature (confirmed — §Clarifications #3).
- **Security**: the feature-012/013/014/015 rendering model is preserved unchanged — escaped interpolation only, no `[innerHTML]`, no `DomSanitizer`; selected and generated text are treated as untrusted.
- **No deviation from the constitution's Clean Architecture / LLM-Abstraction principles**: the new prompt stays externalized and provider-agnostic; generated content is validated/normalized at the service boundary and rendered through the safe allowlist.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Select text, hold for 2 seconds, and "Define" the term (Priority: P1)

While reading a snack, the learner selects one or more words in the reading content. Only after the selection has been held for about 2 seconds does a clearly labeled **"Define"** affordance appear, anchored to that selection. If the learner changes or clears the selection before the 2 seconds elapse, no affordance appears. Activating "Define" generates the meaning for the selected term and opens the definition card showing it.

**Why this priority**: This is the new user-facing capability requested by the task and the source of the feature's value. It is independently testable by selecting text, waiting (or not), observing the affordance, and activating it. P1 because every other story only matters once the trigger and the affordance work.

**Independent Test**: Select a term in a snack's reading content and hold it for ~2 seconds; confirm the "Define" affordance appears. Repeat but change/clear the selection within 2 seconds; confirm no affordance appears. Activate "Define" and confirm a loading state and then the definition card showing the generated meaning. Delivers the value of "define any word I am reading, on my terms."

**Acceptance Scenarios**:

1. **Given** a snack's reading content is displayed, **When** the user selects one or more words and holds the selection for about 2 seconds, **Then** a clearly labeled "Define" affordance appears anchored to that selection.
2. **Given** a selection is being held, **When** less than about 2 seconds have elapsed, **Then** no "Define" affordance has appeared yet.
3. **Given** a selection is being held, **When** the user changes or clears the selection before about 2 seconds elapse, **Then** the timer is cancelled and no "Define" affordance appears for the old selection.
4. **Given** the "Define" affordance is shown, **When** the user activates it, **Then** the selected term is sent for generation and the definition card eventually shows the generated meaning.
5. **Given** the user selects text, **When** the selection is held and then released without activating "Define", **Then** no generation occurs and the reading flow is unaffected.

---

### User Story 2 — Generate the meaning provider-agnostically with a loading state and safe failures (Priority: P1)

Activating "Define" asks the backend to generate a meaning/definition for the selected term. The generation goes through the existing provider-agnostic LLM flow (externalized prompt, `LlmProvider` port), is normalized/validated at the service boundary, and shows a loading/in-progress state while it runs. Rapid repeated activation starts at most one request. On failure, a meaningful, user-safe error is shown and nothing broken, empty, or placeholder is added.

**Why this priority**: The affordance is only useful if it actually produces a definition reliably and degrades safely. It is independently testable at the service/endpoint level and through the loading/error UI states. P1 because US1 without working generation delivers nothing.

**Independent Test**: Activate "Define" for a valid term and confirm a loading state appears and a generated meaning is returned; force an LLM failure and confirm a meaningful error with no broken/empty content; activate "Define" twice rapidly and confirm at most one request is started. Delivers the value of "a definition that is produced safely and predictably."

**Acceptance Scenarios**:

1. **Given** a valid selected term, **When** "Define" is activated, **Then** a request for its meaning is made through the provider-agnostic generation flow and a loading/in-progress state is shown.
2. **Given** a generation is in progress, **When** the user activates "Define" again for the same selection, **Then** no second/duplicate request is started.
3. **Given** generation succeeds, **When** it completes, **Then** the generated meaning is returned and the loading state ends.
4. **Given** generation fails, **When** the failure is reported, **Then** a meaningful, user-safe error is shown and nothing broken, empty, or placeholder is added to the reading flow.
5. **Given** a generation request is made, **When** the backend handles it, **Then** no provider-specific logic is used in services/controllers (only the `LlmProvider` port and the externalized prompt).

---

### User Story 3 — Selected and generated text is always rendered as safe, escaped text (Priority: P1)

The selected term and the generated meaning are untrusted text (the term comes from the document, the meaning from a language model). Both are displayed as plain escaped text through the same safe mechanism as the rest of the snack, so markup or malicious content cannot execute or inject into the page. The feature-012/013/014/015 sanitization model is preserved unchanged.

**Why this priority**: The generated meaning reaches the DOM in the user's browser and is model output; treating it as HTML would reintroduce the cross-site-scripting risk feature 012 deliberately closed. P1 because it is a security invariant that must hold on every render.

**Independent Test**: Select a term containing markup-like text and force a generated meaning containing `<script>`, `<img onerror=...>`, `javascript:` links, `<iframe>`, inline styles, and other non-whitelisted markup; confirm nothing executes and only inert, readable text appears. Delivers the value of "defining words without opening an injection hole."

**Acceptance Scenarios**:

1. **Given** the selected term or generated meaning contains raw HTML such as `<script>alert(1)</script>`, **When** it renders, **Then** the script does not execute and is not injected into the DOM.
2. **Given** the text contains an event handler or dangerous URL (e.g. `<img src=x onerror=...>`, `javascript:` link), **When** it renders, **Then** no handler runs and no dangerous URL is activated.
3. **Given** the text contains markup outside the whitelist (links, images, headings, tables, code, iframes, inline styles), **When** it renders, **Then** it is neutralized as inert text and never becomes an element.
4. **Given** the text contains escaped entities, **When** it renders, **Then** the intended characters are shown without double-escaping.
5. **Given** the definition card is used, **When** it shows the term and the generated meaning, **Then** it uses escaped interpolation only (no `[innerHTML]`, no `DomSanitizer`) and the feature-012/013/014/015 whitelist is preserved unchanged.

---

### User Story 4 — Reachable by pointer and keyboard/programmatic selection without disturbing existing interactions (Priority: P2)

The selection-Define flow works both when the user selects text with a pointer and when the selection is made programmatically or by keyboard (`document.getSelection()`). The transient affordance does not break the existing highlighted-keyword clicks, the single-definition-card rule, or the snack reading flow.

**Why this priority**: Accessibility and non-interference determine whether the feature is usable by everyone and whether it regresses the existing keyword experience. It is independently testable by driving selection both ways and by exercising existing keyword interactions. P2 because the core value (US1–US3) can exist before full reachability hardening.

**Independent Test**: Create a selection via pointer drag and via a programmatic/`document.getSelection()` path; confirm the affordance appears after the hold in both cases. Then click an existing highlighted keyword and confirm the existing definition card behavior is unchanged, and confirm only one card is ever shown. Delivers the value of "works for how I select, and does not break what already worked."

**Acceptance Scenarios**:

1. **Given** a pointer text selection inside the reading content, **When** it is held for about 2 seconds, **Then** the "Define" affordance appears.
2. **Given** a programmatic/keyboard selection (via `document.getSelection()`) inside the reading content, **When** it is held for about 2 seconds, **Then** the "Define" affordance appears.
3. **Given** the user clicks an existing highlighted keyword, **When** the click is handled, **Then** the existing keyword definition card behavior is unchanged and no selection-Define flow is triggered.
4. **Given** the single-definition-card rule, **When** the selection-Define flow shows its result, **Then** at most one definition card is shown at a time and the rule is not violated.
5. **Given** the snack reading flow, **When** the feature is present, **Then** navigation, quiz start, and existing interactions are unchanged.
6. **Given** the selection-Define trigger is offered only on the snack reading page, **When** text is selected on the results recap or the read-only history surface, **Then** no "Define" affordance is offered there.

---

### User Story 5 — Existing snack, keyword, meaning-snack, and formatting behavior is not regressed (Priority: P2)

The feature ships without changing the snack reading experience, the keyword highlight/definition-card interaction, the meaning-snack flow, the rich-text rendering, or the results/history surfaces. Existing end-to-end suites pass unchanged.

**Why this priority**: This is a cross-cutting non-regression guarantee that is easy to verify independently but does not itself unlock user value. P2 because it hardens the feature.

**Independent Test**: Run the existing backend and frontend suites (including `snack-keywords`, `meaning-snacks`, `snack-rich-formatting`) and confirm they pass unchanged; manually confirm the keyword highlight, definition card, and meaning-snack flows behave as before. Delivers the value of "a new capability with no collateral damage."

**Acceptance Scenarios**:

1. **Given** the feature ships, **When** the existing E2E suites run, **Then** they pass unchanged.
2. **Given** features 012/013/014/015, **When** the feature ships, **Then** rich-text formatting, keyword highlighting, click-to-open/dismiss/focus, and the meaning-snack ("Generate snack") flow are unchanged.
3. **Given** the results recap and read-only history surfaces, **When** the feature ships, **Then** they render as before, offer no selection-Define trigger, and are not broken by the new selection handling.

---

### Edge Cases

- **Selection changed or cleared before 2 seconds**: the timer is cancelled and no "Define" affordance appears; a subsequent held selection starts a fresh timer.
- **Selection released before 2 seconds**: no affordance appears and no generation occurs.
- **Blank or whitespace-only selection**: rejected with a meaningful error; no generation request is sent.
- **Oversized selection** (more than 10 whitespace-separated tokens): rejected with a meaningful error; no generation request is sent.
- **Rapid repeated activation of "Define"**: while a generation is in flight the action is guarded/disabled; at most one request is started and no duplicate card content is produced.
- **Generation failure / LLM unavailable**: a meaningful, user-safe error is shown; nothing broken, empty, or placeholder is added; the user can retry.
- **Selection spans multiple elements/blocks**: the selected term is normalized to plain text; the affordance anchoring remains usable and does not corrupt surrounding formatting.
- **Selection is inside or overlaps an existing highlighted keyword**: the selection-Define flow does not break the existing keyword click; the existing single-card rule is preserved.
- **Selection made entirely outside the reading content** (e.g. in navigation/chrome): no "Define" affordance is offered.
- **Selection on the results recap or read-only history surface**: no "Define" affordance is offered (selection-Define is scoped to the snack reading page only); those surfaces render unchanged.
- **Selection made via keyboard/programmatic API** (`document.getSelection()`): behaves like a pointer selection.
- **Touch/mobile text selection**: the affordance appears and remains usable without horizontal overflow.
- **The affordance is dismissed** (selection cleared, click elsewhere, or Escape): it disappears with no navigation and no leftover state.
- **Definition card already open when a new selection is held**: the new result replaces the previous card; only one card is shown at a time.
- **Unicode, emoji, and RTL text** in the selected term or generated meaning: display and interaction still work and remain readable.
- **Injection payloads** in the selected term or generated meaning (`<script>`, `onerror=`, `javascript:` URLs, `<style>`, `<iframe>`, `<img>`): no execution, no DOM injection, no layout breakage; readable text is preserved.
- **Escaped entities** in the term or generated meaning (`&lt;`, `&amp;`): displayed as the intended characters without double-escaping.
- **Very long generated meaning**: the card layout remains stable and performant; no overflow break (consistent with feature 015's readability behavior).
- **Backend unavailable or times out**: the UI shows a meaningful error and adds nothing.
- **Pre-existing behavior**: keyword highlighting, definition-card open/dismiss/focus, and meaning-snack generation are unchanged.

## Requirements *(mandatory)*

### Functional Requirements

**Selection and trigger**

- **FR-001**: Selecting one or more words inside a snack's reading content MUST start a hold timer of approximately 2 seconds; only after the selection has been held for approximately 2 seconds MUST a clearly labeled "Define" affordance appear for that selection.
- **FR-002**: If the selection changes or is cleared before approximately 2 seconds elapse, the timer MUST be cancelled and no "Define" affordance MUST appear for the previous selection.
- **FR-003**: The "Define" affordance MUST be transient and anchored to the current selection, and MUST NOT interfere with the existing single-definition-card rule or with existing highlighted-keyword click behavior.

**Generation**

- **FR-004**: Activating "Define" MUST request the generation of a **short definition** for the selected term from the backend; the generated result MUST be bounded to at most **20 words**, consistent with the feature-015 keyword-meaning model (not a long multi-paragraph explanation).
- **FR-005**: The backend MUST generate the definition through the existing externalized, provider-agnostic LLM flow (the `LlmProvider` port with an externalized prompt); no provider-specific logic MAY appear in services or controllers.
- **FR-006**: While a generation is in progress, the UI MUST show a loading/in-progress state.
- **FR-007**: Rapid repeated activation of "Define" for the same selection MUST start at most one request; a request in flight MUST guard against duplicates.
- **FR-008**: On success, the definition card MUST open for the selected term and show the generated meaning.
- **FR-009**: On failure, the UI MUST show a meaningful, user-safe error message and MUST NOT add any broken, empty, or placeholder content.

**Normalization and validation**

- **FR-010**: The selected term MUST be normalized before use (trimmed, whitespace-collapsed) and bounded to at most **10** whitespace-separated tokens.
- **FR-011**: A blank/whitespace-only selection, or a selection exceeding 10 whitespace-separated tokens, MUST be rejected with a meaningful error and MUST NOT be submitted as a generation request.
- **FR-012**: The word-count rule MUST count whitespace-separated tokens, consistent with the existing feature-013/015 rule; hyphenated compounds count as one token.

**Display and security**

- **FR-013**: The selected term and the generated meaning MUST be rendered as escaped text only — there MUST be no `[innerHTML]` and no `DomSanitizer`.
- **FR-014**: Markup or arbitrary HTML in the selected term or generated meaning (including `<script>`, event handlers such as `onerror`, `javascript:` URLs, `<iframe>`, `<img>`, `<style>`, and inline styles) MUST NOT be executed or injected into the DOM.
- **FR-015**: The feature-012/013/014/015 sanitization model (render-time allowlist, no `[innerHTML]`, no `DomSanitizer`, escaped interpolation) MUST be preserved unchanged.

**Reachability and scope**

- **FR-016**: The "Define" affordance MUST be reachable via pointer text selection and via programmatic/keyboard selection (`document.getSelection()`).
- **FR-017**: The selection-"Define" trigger MUST be offered **only** on the snack reading page (`snacks.component`) reading content; the results recap and read-only history rich-text surfaces MUST NOT offer the trigger and MUST remain unchanged.
- **FR-018**: The feature MUST NOT change the existing keyword highlight/definition-card behavior, the snack reading flow, the meaning-snack ("Generate snack") flow, rich-text rendering, or the results/history surfaces, and the existing E2E suites MUST pass unchanged.
- **FR-019**: The feature MUST NOT implement Task 12 ("Create snack" from the custom meaning); that remains future work.

### Key Entities *(include if feature involves data)*

- **SelectedTerm** (UI concept): The text the user selects inside a snack's reading content and holds for ~2 seconds. It is normalized (trimmed, whitespace-collapsed) and bounded to at most **10** whitespace-separated tokens, and is untrusted text.
- **GeneratedDefinition** (new, provider-agnostic): The result of generating a **short definition** (bounded to at most **20 words**, consistent with feature 015) for an arbitrary term through the externalized LLM flow. It is untrusted generated text, produced on demand and shown in the definition card; it is not persisted.
- **"Define" affordance** (UI concept): The transient, clearly labeled, selection-anchored control that appears only after the ~2-second hold and is dismissed when the selection changes/clears.
- **Definition card** (existing, reused): The single, dismissible card that shows a term and its meaning; opened by the page from the selection-Define flow and still governed by the single-card rule.
- **Hold trigger** (concept): The ~2-second timer that converts a held selection into the "Define" affordance; cancelled if the selection changes or is cleared first.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of selections held for approximately 2 seconds inside reading content show a "Define" affordance; 0 affordances appear before approximately 2 seconds elapse or after the selection is changed/cleared.
- **SC-002**: 100% of "Define" activations on valid terms either show a loading state followed by the generated meaning in the card, or show a meaningful error; 0 activations add broken, empty, or placeholder content.
- **SC-003**: 0 duplicate generation requests result from rapid repeated activation; a single selection yields at most one in-flight request.
- **SC-004**: 0 executable or injected markup from the selected term or generated meaning: injection payloads (`<script>`, `onerror=`, `javascript:` URLs, `<iframe>`, inline styles) execute 0 times and inject 0 nodes.
- **SC-005**: 100% of valid selected terms are normalized before use; 0 blank/whitespace-only or oversized (more than 10 words) selections are submitted as generation requests (each yields a meaningful error instead).
- **SC-006**: 100% of the selection-Define flow is reachable via pointer and via programmatic/keyboard selection; 0 selection-Define triggers are offered outside the snack reading page (the results recap and read-only history surfaces remain unchanged); 0 regressions to existing highlighted-keyword clicks and 0 violations of the single-definition-card rule.
- **SC-007**: Non-regression: the existing backend/frontend/E2E suites pass unchanged, and features 012/013/014/015 (rich formatting, keyword highlight/card, meaning-snack generation, 20-word readability) remain intact.

## Non-Goals

- **No "Create snack" from the custom meaning (Task 12)**: this feature generates and shows a definition only; turning it into a snack remains future work.
- **No change to the existing highlighted-keyword interaction**: the keyword highlight, click-to-open, dismiss, and focus behavior of feature 013 are preserved unchanged.
- **No change to the meaning-snack flow or the "Generate snack" action** of feature 014.
- **No change to the snack generation flow, provider selection, batch/progressive delivery, retries, quiz, scoring, XP, accuracy, navigation, results, or history** beyond adding the selection-Define capability.
- **No redesign of the definition card** beyond reusing it to show the generated meaning; its open/dismiss/focus behavior and layout are preserved.
- **No Markdown/HTML dialect**: selected and generated text remain plain escaped text; markup is neutralized, not rendered.
- **No user editing/authoring of the generated meaning**: it is read-only generated content.
- **No selection-Define outside the snack reading page**: the trigger is scoped to the snack reading content only; the results recap and read-only history rich-text surfaces do not offer it and MUST remain unchanged.
- **No new persistence or schema change**: the definition is generated on demand and shown in the card; nothing new is persisted for this feature.
- **No unrelated gamification, auth, or advanced features** (MVP-first / YAGNI).

## Assumptions

- **Hold duration**: "2 seconds" is the trigger threshold; the implementation may treat it as approximately 2 seconds and MUST NOT show the affordance before it, consistent with FR-001/FR-002.
- **Content shape (confirmed)**: the generated result is a **short definition** bounded to at most **20 words**, consistent with the feature-015 keyword-meaning model, not a long multi-paragraph explanation (see §Clarifications #1).
- **Max words (confirmed)**: a selected term is normalized (trimmed, whitespace-collapsed) and bounded to at most **10** whitespace-separated tokens; blank/whitespace-only or over-limit selections are rejected with a meaningful error and never submitted (see §Clarifications #2).
- **Surfaces (confirmed)**: selection-Define is offered on the **snack reading page** (`snacks.component`) reading content only; the results recap and read-only history surfaces do not offer the trigger and must not regress (see §Clarifications #3).
- **Affordance (confirmed)**: the "Define" affordance is a transient, selection-anchored floating button/popover, dismissed when the selection changes/clears (see §Clarifications #5).
- **Endpoint (confirmed)**: the backend capability is a **new provider-agnostic endpoint** (rather than reusing the meaning-snack endpoint, which returns a full snack), requiring the selected term and accepting theme/subtopic context when available (see §Clarifications #4). This is a plan-level decision.
- **Error handling**: errors surface through the existing `GlobalExceptionHandler`/`ErrorResponse` shapes with typed exceptions; the frontend shows the existing user-safe error message mechanism.
- **No persistence (confirmed)**: the generated definition is shown transiently in the card; no new entity or schema change is required (see §Clarifications #6).
- **Security model**: selected and generated text are untrusted and rendered through the feature-012/013/014/015 safe model (escaped interpolation only, no `[innerHTML]`, no `DomSanitizer`).
- The existing stack is unchanged: Java 21 + Spring Boot backend, Angular 21 frontend, MySQL 8.
- The existing E2E suites remain the regression contract and are extended (not replaced) to cover the selection-Define flow.

## Clarifications

The following decisions were made to resolve the open questions for this feature; all items are **confirmed** by the product owner and no clarification markers remain. Decisions marked **[CONFIRMED]** were raised as open questions and resolved exactly as recommended; the remaining items were resolved with reasonable defaults and are recorded as confirmed decisions.

1. **[CONFIRMED — content shape: Option A, short bounded definition]** The generated result shown in the definition card is a **short definition** bounded to at most **20 words**, consistent with feature 015's ≤20-word keyword meanings — not a longer multi-paragraph meaning. The generation prompt/contract targets a concise definition of ≤20 words. Encoded in §Context & Scope, §Project Structure Impact, FR-004, §Key Entities (`GeneratedDefinition`), §Assumptions.
2. **[CONFIRMED — max words: Option A, ≤10 words]** A selected term is normalized (trimmed, whitespace-collapsed) and bounded to at most **10** whitespace-separated tokens; blank/whitespace-only or >10-word selections are rejected with a meaningful error and never submitted. Encoded in FR-010/FR-011, §Edge Cases, SC-005, §Assumptions.
3. **[CONFIRMED — surfaces: Option A, snack reading page only]** The selection-"Define" trigger is offered **only** on the snack reading page (`frontend/src/app/pages/snacks/snacks.component`) reading content. The results recap and read-only history rich-text surfaces do **not** offer the trigger and MUST remain unchanged/non-regressed. Encoded in FR-017, US4 AS6, US5 AS3, §Edge Cases, SC-006, §Non-Goals, §Assumptions.
4. **[CONFIRMED — endpoint]** A **new provider-agnostic endpoint** (not the meaning-snack endpoint, which returns a full snack) accepts the selected term and, when available, the theme/subtopic context. Encoded in §Context & Scope, §Project Structure Impact, FR-005, §Assumptions.
5. **[CONFIRMED — affordance]** The "Define" affordance is a **transient, selection-anchored floating button/popover**, dismissed when the selection changes or clears. Encoded in FR-003, US1, §Edge Cases, §Assumptions.
6. **[CONFIRMED — no persistence/schema change]** The generated definition is produced on demand and shown in the card; **no new persistence or schema change** is introduced by this feature. Encoded in §Project Structure Impact, §Non-Goals, §Assumptions.
