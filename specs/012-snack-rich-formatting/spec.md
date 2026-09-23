# Feature Specification: Snack Rich Formatting (Bold Emphasis, Bullets, Short Paragraphs)

**Feature Branch**: `012-snack-rich-formatting`

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "Snack prompt and rendering with bold emphasis, bullets, and short paragraphs. Update the snack generation prompt so important words/sentences are emitted as bold and long text is broken into bullet points and shorter paragraphs, and render that rich formatting in the snack UI instead of plain walls of text." (Batch backlog Task 7.)

## Context & Scope *(established — do not re-investigate)*

This is an **end-to-end formatting feature** spanning the snack generation prompt (backend), the persistence/API round-trip, and every surface that displays snack text (frontend): the snack reading UI, the end-of-test recap, **and the read-only history view (feature 011)**. It changes *how snack text is written and displayed*; it does **not** change the snack JSON contract, the generation flow, progressive/batch delivery, quiz behavior, or the data model.

The following current code state is treated as confirmed input and is referenced, not re-designed:

**Backend**
- `backend/src/main/resources/prompts/generate-snack.md` is the externalized snack prompt loaded by `LlmPromptTemplates.generateSnack(theme, subtopic)` (`{{theme}}`/`{{subtopic}}` substitution). It currently requires a JSON object with exactly `concept`, `metaphor`, `whyItMatters`, `related`, and explicitly says **"No markdown"**.
- `OpenAiLlmProvider.generateSnack(...)` parses the model response with Jackson `readTree`, reading `concept`/`metaphor`/`whyItMatters` as text and `related` as an array, then returns a `LlmProvider.GeneratedSnack`. Text values are passed through as-is (no stripping/normalization).
- `SnackService.generateValidSnack(...)` validates that `concept`, `metaphor`, `whyItMatters` are non-blank and `related` is non-empty with ≤ 3 entries; a failed validation re-rolls once, then raises a service-unavailable error.
- `Snack` (domain entity) stores `concept`, `metaphor`, `whyItMatters` as `TEXT` and `related` as a JSON list. `SnackGenerateResponse.SnackItem` exposes `id`, `subtopicId`, `subtopicName`, `concept`, `metaphor`, `whyItMatters`, `related`. Other snack-returning surfaces exist (batch status items, the results recap, the read-only history view from feature 011).

**Frontend**
- `frontend/src/app/models/snack.model.ts` defines `Snack` with `concept`, `metaphor`, `whyItMatters`, `related`.
- `frontend/src/app/pages/snacks/snacks.component.html` renders `snack.concept`, `snack.metaphor`, and `snack.whyItMatters` through plain `<p>{{ ... }}</p>` interpolation, and `related` as `.tag` chips.
- The results screen renders the end-of-test snack recap; it displays the same snack text fields.
- The history page (feature 011, `/history`) renders each theme's nested snacks read-only; it currently displays snack text through plain interpolation too.
- Angular 21 standalone components, signals-first, `ChangeDetectionStrategy.OnPush`, with an existing global stylesheet/design-token system.

This feature adds (1) prompt instructions that make the model emit a bold marker for emphasis and split long content into bullet points and short paragraphs **while keeping the existing JSON shape**, (2) preservation of that markup through persistence and the API, and (3) a single sanitized rich-text renderer reused across **the snack UI, the results recap, and the history view**, displaying only whitelisted formatting (bold, paragraphs, bullet lists). It does **not** change what a snack is, how it is generated (beyond the prompt wording), or the JSON contract.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Snacks are written to be scannable: bold emphasis, bullets, short paragraphs (Priority: P1)

When a snack is generated, its text is written the way a good study card is written: the key term in each idea is emphasized, and a long explanation is broken into a few short paragraphs or a short bullet list instead of one dense wall of text. The user can skim the card and immediately see what matters. The snack still arrives with the same fields (`concept`, `metaphor`, `whyItMatters`, `related`) — only the writing style changes.

**Why this priority**: This is the source of the value: without the model actually emitting emphasis and structure, there is nothing for the UI to render richly. It is independently testable by inspecting generated snack text. P1 because every downstream rendering benefit depends on it.

**Independent Test**: Generate snacks for several subtopics and confirm each non-trivial text field contains at least one bold marker around an important word/sentence and, when the content is long, is organized as multiple short paragraphs and/or a bullet list — while the JSON shape (`concept`, `metaphor`, `whyItMatters`, `related`) is unchanged and all fields remain non-empty/meaningful. Delivers the value of "the card is easy to skim."

**Acceptance Scenarios**:

1. **Given** a snack is generated for a subtopic, **When** its text fields are inspected, **Then** at least one important word or sentence in the card is wrapped in a bold marker (e.g. `**...**`).
2. **Given** a snack field's content is long enough to be a wall of text, **When** it is generated, **Then** the content is emitted as multiple short paragraphs and/or a bullet list rather than a single long paragraph.
3. **Given** the prompt is updated for rich formatting, **When** a snack is generated, **Then** the returned JSON still has exactly the existing shape `{ concept, metaphor, whyItMatters, related }` — no new fields, and `related` remains a list of plain concept names.
4. **Given** the prompt is updated, **When** a snack is generated, **Then** `concept`, `metaphor`, and `whyItMatters` are all still non-empty and meaningful, and `related` still has 2–3 entries (existing validation still passes; no re-roll caused by empty fields).
5. **Given** the model is instructed to format, **When** it emits content, **Then** it is constrained to the whitelisted formatting set (bold, paragraphs, bullet lists) and does not emit arbitrary HTML, links, images, scripts, tables, headings, or code blocks.

---

### User Story 2 — The snack UI renders the formatting instead of showing markup (Priority: P1)

The user reads the snack card and sees actual **bold** text where the model emphasized something, real bullet points where a list was intended, and clean short paragraphs — never literal `**` characters and never one unbroken block of text. The same rich rendering appears everywhere snack text is shown: the end-of-test snack recap and the read-only history view.

**Why this priority**: Even perfectly formatted generated text is worthless if the UI shows the raw markers. This is the visible half of the feature and is independently testable with fixed snack fixtures. P1 because it is what the user actually experiences.

**Independent Test**: Render snacks whose fields contain bold markers, bullet lines, and multiple paragraphs — in the snack card, the results recap, and the history view — and confirm bold becomes real bold text (e.g. `<strong>`), bullet lines become list items, paragraph breaks become separate paragraphs, and no literal `**` or `- ` markers are visible. Delivers the value of "the card looks formatted, not like raw markup."

**Acceptance Scenarios**:

1. **Given** a snack field contains a bold marker such as `**mitosis**`, **When** the snack card renders, **Then** "mitosis" is displayed as actual bold text and the literal `**` characters are not shown.
2. **Given** a snack field contains bullet lines, **When** the snack card renders, **Then** they are displayed as a real bullet list (list items), not as literal leading dashes/asterisks.
3. **Given** a snack field contains multiple short paragraphs, **When** the snack card renders, **Then** they are displayed as separate paragraphs rather than one merged block.
4. **Given** a long snack field, **When** it renders, **Then** it shows as multiple short paragraphs and/or a bullet list, not as a single long paragraph.
5. **Given** the same snack is shown in the end-of-test recap, **When** the recap renders, **Then** the same rich formatting is applied consistently with the snack card.
6. **Given** a snack is shown in the read-only history view (feature 011), **When** the history view renders it, **Then** the same rich formatting is applied consistently with the snack card, under the same whitelist and sanitization rules.
7. **Given** formatting is applied, **When** `concept`, `metaphor`, and `whyItMatters` are rendered, **Then** all three support the same rich formatting; `related` entries remain plain labels (no markup interpretation).

---

### User Story 3 — Only safe, whitelisted formatting is rendered (Priority: P1)

The app never trusts the language model's output as HTML. Only the whitelisted formatting — bold, paragraph breaks, and bullet lists — is rendered; anything else (script tags, event handlers, images, links, styles, iframes, or other arbitrary HTML) is neutralized and cannot execute or inject into the page. A malicious or malformed model response is shown as safe text.

**Why this priority**: Snack text is untrusted, model-generated content displayed in the user's browser. Rendering it as HTML without an allowlist is a cross-site-scripting risk. P1 because it is a security requirement that must hold for every render, not an enhancement.

**Independent Test**: Feed snack fields containing `<script>`, `<img onerror=...>`, `javascript:` links, `<iframe>`, inline styles, and other non-whitelisted markup, then render the snack and confirm nothing executes and only whitelisted formatting appears. Delivers the value of "rich formatting without opening an injection hole."

**Acceptance Scenarios**:

1. **Given** a snack field contains raw HTML such as `<script>alert(1)</script>`, **When** the snack renders, **Then** the script does not execute and is not injected into the DOM (shown as inert text or removed).
2. **Given** a snack field contains an event handler or dangerous URL (e.g. `<img src=x onerror=...>`, `javascript:` link), **When** the snack renders, **Then** no handler runs and no dangerous URL is activated.
3. **Given** a snack field contains markup outside the whitelist (links, images, headings, tables, code, iframes, inline styles), **When** the snack renders, **Then** only whitelisted formatting is applied and the non-whitelisted markup is neutralized.
4. **Given** sanitization runs at render time, **When** even a non-whitelisted payload reaches the frontend, **Then** it is still neutralized before entering the DOM (defense in depth, independent of what the backend stored).
5. **Given** sanitization removes unsupported markup, **When** the snack renders, **Then** the underlying readable text is not silently lost (the user still sees the content as safe plain text).

---

### User Story 4 — Formatting survives storage and the API unchanged (Priority: P2)

The bold and bullet markup the model produced is exactly what comes back out of the database and the API. Nothing strips it, collapses it, or HTML-escapes it into literal characters (such as `&ast;`/`&lt;`) along the way. This is what makes the frontend renderer reliable regardless of whether the snack comes from a fresh generation, the batch status response, the results recap, or history.

**Why this priority**: The rendering story fails if the pipeline mangles the markup, but this is a data-integrity concern that is easy to verify and can be tested independently of the UI. P2 because it hardens rather than unlocks the feature (the prompt and renderer deliver the visible value).

**Independent Test**: Generate or seed a snack containing bold/bullet markup, persist it, read it back through the API (including batch status, recap, and history surfaces), and confirm the text is returned byte-for-byte identical to what was generated, with the JSON shape unchanged. Delivers the value of "what the model wrote is what the user's renderer receives."

**Acceptance Scenarios**:

1. **Given** a snack field contains `**bold**` markup, **When** it is persisted and read back, **Then** the stored and returned value still contains `**bold**` unmodified.
2. **Given** a snack field contains bullet lines and paragraph breaks, **When** it is persisted and read back, **Then** the line breaks and bullet markers are preserved (not collapsed into a single line).
3. **Given** markup passes through the API, **When** the response is serialized, **Then** the markup is not HTML-escaped into literal entity characters and not stripped.
4. **Given** the JSON contract, **When** the prompt/rendering feature ships, **Then** the snack response shape is unchanged (`concept`, `metaphor`, `whyItMatters`, `related`) and no new fields or endpoints are introduced.

---

### User Story 5 — Existing snack fields and the recap still work (Priority: P2)

Snacks that contain no formatting markup — including previously stored snacks — still render as clean plain text with no artifacts (no empty bullet list, no stray `**`, no empty paragraph). The quiz and results recap keep working exactly as before, and progressive delivery, retries, and navigation are untouched.

**Why this priority**: The feature must not trade a new rendering path for regressions in the existing reading loop. P2 because it protects value rather than adding it, and the core value is already delivered by US1–US3.

**Independent Test**: Render a snack whose fields contain no markup and confirm it looks exactly as before; run the existing snack/quiz/results/progressive-delivery E2E suites and confirm they pass unchanged. Delivers the value of "nothing else broke."

**Acceptance Scenarios**:

1. **Given** a snack field contains no markup (plain text, possibly from existing data), **When** it renders, **Then** it displays as clean text with no stray markers, empty paragraphs, or empty lists.
2. **Given** a snack field contains literal characters that are not formatting (e.g. a stray `*` used for multiplication), **When** it renders, **Then** it is not mangled and displays as readable text.
3. **Given** the quiz/results recap renders, **When** this feature ships, **Then** the recap still renders the snack and the existing results behavior is unchanged.
4. **Given** the existing flow (subtopics → snacks → quiz → results), **When** the feature ships, **Then** progressive delivery, retries, and snack navigation still behave as before and the existing E2E suites pass unchanged.

---

### Edge Cases

- **Unclosed or malformed bold marker** (e.g. `**important` with no closing marker): render safely as plain text without swallowing the rest of the field or breaking the layout.
- **Stray asterisks that are not formatting** (e.g. "5 * 3 = 15", footnote-style `*`): do not treat every asterisk as emphasis; avoid mangling legitimate text.
- **Mixed content** (a paragraph followed by a bullet list followed by another paragraph): render each block in order without merging or reordering.
- **Mixed content with non-whitelisted markup interspersed** (e.g. a paragraph, a bullet list, and an embedded `<script>`/`<img onerror>` within the same field): each whitelisted block renders in order and the non-whitelisted markup is neutralized as inert text without disturbing block order or the surrounding readable content.
- **Bullet lines with varying markers** (`-`, `*`, `•`) or inconsistent spacing: still recognized as bullets and rendered as a list.
- **Very long field with no bullets** (model did not fully comply): render as multiple readable paragraphs rather than one giant block, without error.
- **Field is empty or whitespace**: no empty paragraph/list artifact; existing empty-content handling (validation/re-roll) is preserved.
- **`related` entries contain markup-like characters**: `related` is treated as plain labels and is never interpreted as rich text.
- **Non-whitelisted markup** (links, images, headings, tables, code blocks, iframes, inline styles): neutralized, not executed; readable text is preserved.
- **Injection payloads** (`<script>`, `onerror=`, `javascript:` URLs, `<style>`, `<iframe>`, `<img>`): no execution, no DOM injection, no layout breakage.
- **Escaped entities in stored text** (e.g. `&lt;`, `&amp;`): displayed as the intended characters without double-escaping.
- **Unicode, emoji, and RTL text mixed with bold/bullets**: formatting still applies and text remains readable.
- **Bold spanning a line break or adjacent to punctuation**: emphasis is applied correctly without leaving orphaned markers.
- **Same snack rendered in more than one place** (snack card, results recap, and history view): formatting is consistent across all surfaces, and no surface shows literal markers.
- **History view with a large nested set of themes/snacks**: each nested snack's rich text is sanitized and rendered independently; no surface falls back to raw interpolation.
- **Old snacks generated before the prompt change**: still render correctly (plain or lightly marked) with no artifacts, including in the history view.

## Requirements *(mandatory)*

### Functional Requirements

**Prompt / generation**

- **FR-001**: The snack generation prompt MUST instruct the model to wrap important words or sentences in a bold marker (e.g. `**...**`).
- **FR-002**: The snack generation prompt MUST instruct the model to break long content into bullet points (each line starting with `- `) and shorter paragraphs rather than emitting a single long paragraph.
- **FR-003**: The snack generation prompt MUST continue to require the existing JSON shape with exactly `concept`, `metaphor`, `whyItMatters`, and `related`; no new fields may be introduced, and `related` MUST remain a list of plain concept names.
- **FR-004**: The snack generation prompt MUST constrain formatting to the whitelisted set (bold, paragraph breaks, bullet lists) and MUST forbid arbitrary HTML and other markup (links, images, scripts, headings, tables, code blocks, inline styles).
- **FR-005**: Generated snack text fields MUST actually contain the bold/bullet/paragraph markup for non-trivial content (the model emits formatting, not only plain prose).
- **FR-006**: Generated snack content MUST remain meaningful and non-empty; the existing validation (non-empty `concept`/`metaphor`/`whyItMatters`, `related` non-empty with ≤ 3 entries) MUST continue to pass without causing spurious re-rolls.

**Persistence and API**

- **FR-007**: Snack text markup MUST survive persistence unmodified — stored exactly as generated, with no stripping, collapsing, or HTML-escaping into literal entity characters.
- **FR-008**: Snack text markup MUST survive the API response unmodified on every snack-returning surface (fresh generation response, batch status items, results recap, and history), not just one of them.
- **FR-009**: The snack API/JSON contract MUST remain unchanged in shape and field types; no new fields, endpoints, or schema changes are introduced by this feature.

**Rendering**

- **FR-010**: The frontend MUST render the bold marker as actual bold text (e.g. `<strong>`), and MUST NOT display literal `**` characters.
- **FR-011**: The frontend MUST render bullet-point lines as an actual bullet list (list items), not as literal leading `-`/`*`/`•` characters.
- **FR-012**: The frontend MUST render long content as multiple short paragraphs rather than a single long paragraph.
- **FR-013**: Rich formatting MUST be applied consistently to the formatted snack text fields (`concept`, `metaphor`, `whyItMatters`) across **all snack-displaying surfaces — the snack card, the end-of-test snack recap, and the read-only history view**; `related` entries MUST remain plain labels.
- **FR-014**: When a field contains no recognized markup, the frontend MUST render it as clean plain text with no formatting artifacts (no empty paragraphs, empty lists, or stray markers), and MUST NOT mangle legitimate non-formatting characters.

**History view rendering (feature 011)**

- **FR-023**: The read-only history view (`/history`) MUST render snack text (`concept`, `metaphor`, `whyItMatters`) with the same rich formatting and the same whitelist as the snack card and the results recap — bold as actual bold, bullet lines as a list, and long content as short paragraphs — and MUST NOT show literal `**` or bullet markers.
- **FR-024**: The history view MUST apply the same render-time sanitization guarantees as every other snack-displaying surface (FR-015–FR-019): non-whitelisted or malicious markup in stored snacks is neutralized and never executed in the history view.

**Security / sanitization**

- **FR-015**: Only whitelisted formatting (bold, paragraphs, bullet lists) MUST be rendered; all other markup MUST be neutralized by rendering it as **inert, escaped text** — never executed, never injected into the DOM, and never silently dropped (readable text is preserved per FR-019).
- **FR-016**: Raw or arbitrary HTML from the model (including `<script>`, event handlers such as `onerror`, `javascript:` URLs, `<iframe>`, `<img>`, `<style>`, and inline styles) MUST NOT be executed or injected into the DOM.
- **FR-017**: Sanitization MUST be applied at render time (defense in depth), so that even if non-whitelisted content reaches the frontend, it is neutralized before entering the DOM.
- **FR-018**: Rendered rich text MUST be produced through a safe allowlist mechanism, not by binding untrusted content directly as HTML without sanitization.
- **FR-019**: When non-whitelisted markup is neutralized, the underlying readable text MUST NOT be silently lost (the user still sees the content as safe plain text).

**Non-regression and scope**

- **FR-020**: Existing snack fields and the end-of-test snack recap MUST continue to render correctly; the quiz/results behavior is unchanged.
- **FR-021**: Progressive/batch delivery, retry behavior, and snack navigation MUST be unchanged, and the existing E2E suites MUST pass unchanged.
- **FR-022**: The feature MUST NOT change the generation flow, provider selection, batch status semantics, or data model beyond the prompt wording and the frontend rendering path.

### Key Entities *(include if feature involves data)*

- **Snack** (existing, unchanged shape): A knowledge card for a subtopic. Key attributes used here: `concept`, `metaphor`, `whyItMatters` (now rich text containing whitelisted formatting), and `related` (a list of plain concept-name labels).
- **Formatting whitelist** (concept): The only markup the app recognizes and renders — bold emphasis, paragraph breaks, and bullet lists. Everything else is treated as untrusted and neutralized.
- **Rich-text rendering boundary** (concept): The frontend rendering path that converts stored snack text into safe, whitelisted display markup; it is the single point where untrusted model output is sanitized before reaching the DOM.
- **Generation prompt** (existing asset): The externalized instruction that shapes snack output; it now also specifies the formatting style and the allowed formatting vocabulary while preserving the JSON contract.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of newly generated snacks with non-trivial content contain at least one bold marker, and 100% of long text fields contain bullet and/or short-paragraph structure (0 fields emitted as a single long wall of text).
- **SC-002**: Across all snack-displaying surfaces (snack card, results recap, history view), 0 literal `**` sequences are visible; 100% of bold markers render as actual bold text.
- **SC-003**: 100% of bullet-point lines render as list items and 100% of paragraph breaks render as separate paragraphs across all snack-displaying surfaces; 0 bullet lines or paragraph breaks are shown as literal marker characters.
- **SC-004**: 100% of long snack fields render as multiple short paragraphs and/or a bullet list across all snack-displaying surfaces; 0 long fields render as one unbroken paragraph.
- **SC-005**: 0 executable or unsanitized markup reaches the DOM: injection payloads (`<script>`, `onerror=`, `javascript:` URLs, `<iframe>`, inline styles) execute 0 times and inject 0 nodes across the security test set, including in the history view.
- **SC-006**: 100% of snack text markup round-trips through persistence and the API byte-for-byte identical to what was generated (0 stripped, collapsed, or HTML-escaped values) across all snack-returning surfaces.
- **SC-007**: The snack JSON contract is unchanged: 0 new fields, 0 new endpoints, and 0 schema changes attributable to this feature.
- **SC-008**: Non-regression: the existing snack/quiz/results/progressive-delivery E2E suites pass unchanged, and snacks with no markup render with 0 formatting artifacts.
- **SC-009**: The history view renders 100% of its nested snack text fields with the same rich formatting as the snack card (bold as bold, bullets as lists, long content as short paragraphs) and 0 literal markers; 0 unsanitized markup from stored snacks executes in the history view.

## Non-Goals

- **No change to the snack JSON contract**: no new fields, endpoints, or schema changes; `related` remains a plain list.
- **No change to generation flow or provider behavior**: only the prompt's wording changes; batch/progressive delivery, retries, and provider selection are untouched.
- **No support for a full Markdown/HTML dialect**: headings, links, images, tables, code blocks, blockquotes, inline styles, and arbitrary HTML are explicitly out of scope and must be neutralized, not rendered.
- **No rich-text authoring/editing by the user**: snacks remain read-only generated content.
- **No change to quiz content, scoring, XP, accuracy, or navigation.**
- **No redesign of the snack card layout** beyond rendering the formatted text; the card structure and design tokens are preserved.
- **No requirement to retro-format previously stored snacks** (they render safely as plain/lightly marked text).
- **No change to the history view's read-only nature or data shape**: feature 011's history behavior is preserved; this feature only changes how its snack text is rendered (rich formatting + sanitization), not what it fetches or displays structurally.
- **No new accessibility scope**: rendered rich text uses the native semantic elements it naturally produces (paragraphs, lists, and bold/`<strong>` emphasis); the app's existing accessibility conventions apply unchanged, and no new accessibility requirements are introduced by this feature.

## Assumptions

- `**...**` is the agreed bold marker for this feature; the prompt uses it and the renderer recognizes it.
- The prompt emits bullet lines with a leading `- ` marker; the renderer accepts `-`, `*`, and `•` as bullet markers so it stays robust to minor model variation.
- Paragraph breaks are represented by blank lines (or line breaks) between blocks of text.
- Formatting applies to `concept`, `metaphor`, and `whyItMatters`; `related` stays a plain list of labels.
- The existing stack is unchanged: Java 21 + Spring Boot backend, Angular 21 frontend, MySQL 8. `TEXT` columns already hold arbitrary text, so no schema change is needed.
- The backend preserves model text as-is (it does not sanitize or strip), so markup survives persistence and the API. Sanitization is an **allowlist applied at the frontend render boundary (render time, defense in depth)**; the backend and JSON contract are untouched.
- The snack card, the end-of-test recap, and the history view all render snack text through the same shared, sanitized rich-text renderer; adding the renderer to the history view satisfies its requirement without new backend work or data-shape changes.
- The prompt change is the only backend change; if the model occasionally ignores the formatting instructions, the UI still renders plain text safely (graceful degradation).
- The existing E2E suites remain the regression contract for this change; the history feature's tests (feature 011) are extended to cover rich rendering rather than replaced.

## Clarifications

The following decisions were made by the user and are encoded above; they resolve the open questions for this feature:

1. **Rich formatting on the history page (resolved)**: the read-only history view (feature 011) ALSO renders the rich formatting (bold/bullets/short paragraphs) under the same whitelist and sanitization rules as the snack card and the results recap. Encoded in §Context, US2 AS6, FR-013, FR-023, FR-024, §Edge Cases, SC-002–SC-005, and SC-009.
2. **Bullet marker convention (resolved)**: the prompt emits `- ` bullet lines; the renderer accepts `-`, `*`, and `•`. Encoded in FR-002, FR-011, §Assumptions.
3. **Sanitization location (resolved)**: frontend allowlist sanitization at render time (defense in depth); the backend preserves text unmodified so markup survives the round-trip, and the JSON contract is untouched. Encoded in FR-017, FR-018, FR-024, §Assumptions.

No blocking `[NEEDS CLARIFICATION]` markers remain: all questions have been resolved and encoded in the requirements, edge cases, success criteria, and assumptions.
