# Research: Snack Rich Formatting (Bold Emphasis, Bullets, Short Paragraphs)

**Phase 0 output** for `/specs/012-snack-rich-formatting`. Each section records the **Decision**,
**Rationale**, and **Alternatives considered**, grounded in the real codebase
(`backend/src/main/java/com/endia/**`, `backend/src/main/resources/prompts/**`, `frontend/src/app/**`,
`frontend/e2e/**`).

The governing constraints are: **no schema change, no JSON-contract change, no backend
sanitization, no new heavyweight dependency, sanitization at the frontend render boundary, and the
existing Playwright suite as the regression contract.**

---

## R1. Sanitization/rendering approach in Angular without new heavyweight dependencies

### Options evaluated

**Option A — `DomSanitizer` + `[innerHTML]` (Angular built-in).**
Build an HTML string from the stored text, pass it through `DomSanitizer.sanitize(SecurityContext.HTML, html)`
(or `bypassSecurityTrustHtml`), and bind it via `[innerHTML]`.

**Option B — small allowlist parser → typed AST → Angular template bindings (no `innerHTML`).**
Parse the stored text into a typed block model (paragraphs, bullet lists, bold spans, line breaks) and
render it with `@for`/`@if`/`@switch` and ordinary interpolation, so Angular's own escaping applies to
every text node.

### Decision

**Option B.** A single dependency-free parser + a standalone `RichTextComponent` renders the three
whitelisted constructs through Angular template bindings. `DomSanitizer` is not used.

### Rationale

- **Tighter whitelist than `DomSanitizer`.** Angular's default HTML sanitizer targets *general* HTML:
  it permits many tags we must neutralize (links, headings, tables, etc.) and only strips the
  dangerous subset. Our requirement (FR-015/FR-016) is the inverse — allow exactly bold, paragraphs,
  bullet lists and neutralize *everything else*. Option B's output vocabulary is literally
  `<p>`, `<ul>`, `<li>`, `<strong>`, `<br>`; nothing else can be produced, so the whitelist is exact
  by construction.
- **No HTML string is ever created.** With Option B there is no parse-then-sanitize step that could be
  bypassed. Every text node flows through Angular interpolation, which escapes it. This is a stronger
  defense-in-depth guarantee than "sanitize an HTML string, then trust it."
- **`bypassSecurityTrustHtml` is a foot-gun.** Using it disables Angular's guard entirely; using
  `sanitize()` instead still requires us to have constructed the HTML string first (so we would need
  our own escaping *and* the sanitizer). Option B avoids both.
- **Zero new dependency and zero new API surface.** No Markdown library (e.g. `marked`, `markdown-it`)
  is needed for a three-construct subset. This respects the constitution's MVP/YAGNI principle and the
  feature constraint to prefer built-ins.
- **Testable and deterministic.** `parseRichText` is a pure function, so behavior (unclosed `**`,
  stray `*`, mixed content) is covered without a DOM.
- **Preserves readable text (FR-019).** Because non-whitelisted markup is simply *text* to the parser,
  `<script>alert(1)</script>` is rendered as the visible characters `<script>alert(1)</script>` (inert),
  never dropped silently.

### Alternatives considered and rejected

- **`DomSanitizer` + `[innerHTML]` as primary** — broader allowlist, HTML-string construction, extra
  complexity, and either a disabled guard or a redundant double-escape path. Rejected.
- **Third-party sanitizer (e.g. DOMPurify) or Markdown library** — a new runtime dependency for a
  three-construct subset; violates the "no new deps unless truly needed" constraint. Rejected.
- **Trusting the prompt to never emit HTML (no client sanitization)** — violates FR-017 (render-time
  defense in depth) and leaves a stored-XSS hole if a malicious/malformed model response is persisted.
  Rejected.
- **Backend sanitization** — explicitly out of scope (Clarification 3); it would mutate stored text and
  break the byte-for-byte round-trip requirement (FR-007, SC-006). Rejected.

### How bold/bullets/short paragraphs are rendered safely

- **Bold**: `**` toggles are tokenized into `bold` inline nodes; rendered as `<strong>{{ text }}</strong>`.
  A span is only recognized when the opening marker is immediately followed by non-space and the closing
  marker immediately preceded by non-space (Markdown-like), so `5 * 3 = 15` and `**  ` are not mangled.
  An **unclosed** `**` is emitted as literal text and never swallows the rest of the field.
- **Bullets**: a line matching `/^\s*[-*•]\s+/` starts a list item; consecutive items form one `<ul>`.
  Accepting `-`, `*`, and `•` satisfies the clarified convention (prompt emits `- `; renderer is robust).
- **Short paragraphs**: blank lines separate `<p>` blocks; a single `\n` inside a paragraph becomes a
  `<br>` so authored line breaks are preserved rather than collapsed. Empty/whitespace input yields zero
  blocks — no empty `<p>`/`<ul>` artifact (FR-014).

---

## R2. Rendering long content as paragraphs without a Markdown dialect

**Decision**: treat blank-line separation as the paragraph boundary and preserve intra-paragraph line
breaks with `<br>`; do **not** implement headings, blockquotes, code fences, tables, or emphasis beyond
`**`.

**Rationale**: The spec's whitelist is deliberately narrow (Non-Goals). A blank-line paragraph model is
the minimum that turns a wall of text into scannable blocks and degrades gracefully when the model emits
plain prose (US5). It requires no grammar beyond line classification.

**Alternatives**: full CommonMark parsing (rejected — out of scope, needs a dependency); render each
newline as a paragraph (rejected — produces one-line paragraphs and ignores blank-line intent).

---

## R3. Preserving markup byte-for-byte through JPA `TEXT` columns and JSON serialization

**Decision**: **Change nothing in the backend persistence/API path**; prove the round trip with tests.

**Evidence from the codebase**:
- `Snack.concept`, `Snack.metaphor`, `Snack.whyItMatters` are declared
  `@Column(nullable = false, columnDefinition = "TEXT")` (`backend/src/main/java/com/endia/domain/model/Snack.java`).
  MySQL `TEXT` stores arbitrary UTF-8 including `*`, `-`, and newlines with no transformation. No
  `@Lob`/charset nuance applies.
- `OpenAiLlmProvider.generateSnack` reads `node.path("concept").asText(null)` etc.
  (`backend/.../infrastructure/llm/OpenAiLlmProvider.java`). Jackson `asText` returns the decoded string
  verbatim — it does not strip or escape characters (JSON `\n`/`\"` are transport encoding, decoded on
  read).
- `SnackService.generateValidSnack` only checks `isNonEmpty`/`related` size; it never trims, normalizes,
  or HTML-escapes (`backend/.../application/service/SnackService.java`).
- DTOs (`SnackGenerateResponse`, `ThemeHistoryApiResponse.SnackItem`, batch/quiz summary records) expose
  the raw `String` fields with no transformation. Jackson re-encodes control characters per the JSON
  spec on the wire and decodes them back on the client, so the **decoded** value is identical.
- `related` uses `JpaJsonConverter` (JSON column). It is irrelevant to rich text: `related` is plain
  labels and is never rendered as rich text (FR-013).

**Rationale**: The requirement is data integrity, not new behavior. Adding a converter or normalization
would risk the opposite (collapsing whitespace, escaping entities). A test that seeds markup and asserts
equality across every snack-returning surface is the correct artifact (FR-007..FR-009, SC-006).

**Risks / watch items**:
- Any future `@PrePersist`/`@PostLoad` trimming hook would silently break this; the round-trip test is
  the guard.
- MySQL `TEXT` max length (65,535 bytes) is far above snack sizes; not a concern.
- If the LLM returns JSON where `\n` is literal, Jackson decodes it correctly; if it returns real line
  breaks inside a JSON string (invalid JSON), parsing fails and the existing re-roll path handles it.

**Alternatives**: normalizing line endings in the backend (rejected — mutates text, breaks byte-for-byte);
storing a rendered HTML field (rejected — contract/schema change, violates FR-009).

---

## R4. Frontend component shape and reuse across three surfaces

**Decision**: one standalone `RichTextComponent` under `frontend/src/app/components/rich-text/` with a
signal `input<string>()`, `ChangeDetectionStrategy.OnPush`, and a pure `parseRichText` helper. Import it
into `SnacksComponent`, `ResultsComponent`, and `HistoryComponent`; replace the three plain `<p>`
interpolations in each with `<app-rich-text [text]="..." />`.

**Rationale**:
- `frontend/src/app/components/` already hosts a shared standalone component (`drag-drop-question/`), so
  this follows the established convention.
- A single component guarantees FR-013/FR-023 consistency across surfaces by construction — there is no
  second rendering path that could drift.
- Signals + OnPush match every existing page component; `computed()` parses once per input change.
- Keeping the existing typography class on the `<app-rich-text>` host (e.g.
  `class="snack-card__concept"`) preserves the current design tokens; the component normalizes inner
  `<p>`/`<ul>` margins and supplies paragraph/list rhythm.

**Alternatives**: a pipe (`{{ text | richText }}`) returning `SafeHtml` (rejected — pushes toward
`innerHTML`/sanitizer); duplicating rendering per page (rejected — violates the "single boundary" intent
and invites drift); a directive (rejected — component with explicit template is clearer).

---

## R5. Testing approach given the repo's runners

**Decision**: cover parser + rendering + sanitization through **Playwright E2E** with route-mocked API
responses, plus backend JUnit/MockMvc/Testcontainers tests for the prompt and round trip. Introduce no new
frontend test runner.

**Rationale**: `frontend/package.json` has `@playwright/test` but no Karma/Jest runner and no
`ng test` target wired; adding one would be a new dependency/tooling change beyond scope. Route mocking
lets us feed fixed markup and injection payloads deterministically, covering FR-010..FR-019 and
FR-023/FR-024 end to end. The pure parser's edge cases are exercised through those fixtures.

**Alternatives**: add Jest/Vitest for parser unit tests (rejected — new dependency, out of scope);
backend-only tests (rejected — cannot prove DOM safety or visual rendering).

---

## Summary of decisions

| # | Decision | Key rationale |
|---|----------|---------------|
| R1 | Allowlist parser → AST → Angular template bindings; **no `innerHTML`/`DomSanitizer`**, no new dep | Exact 3-construct whitelist; Angular escapes all text; no HTML string to bypass |
| R2 | Blank-line paragraphs + `<br>` soft breaks; narrow grammar | Minimum scannable structure, graceful degradation |
| R3 | Backend persistence/API untouched; prove round trip with tests | `TEXT`/Jackson/DTOs already preserve text; any change risks integrity |
| R4 | One shared standalone `RichTextComponent` reused on all three surfaces | Consistency by construction; matches repo conventions |
| R5 | Playwright E2E + backend JUnit/MockMvc/Testcontainers; no new runner | Repo has no frontend unit runner; deterministic mocks |
