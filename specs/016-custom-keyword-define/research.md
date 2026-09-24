# Research: Custom Keyword "Define" from a Held Text Selection (2-Second Trigger)

**Phase 0 output** for `/specs/016-custom-keyword-define`. Each section records the **Decision**,
**Rationale**, and **Alternatives considered**, grounded in the real codebase
(`backend/src/main/java/com/endia/**`, `backend/src/main/resources/prompts/**`,
`frontend/src/app/**`, `frontend/e2e/**`) and the feature-013/014/015 precedents
(`specs/013-snack-keywords/*`, `specs/014-meaning-snacks/*`, `specs/015-keyword-meaning-20-words/*`).

The governing constraints are: **the confirmed spec** (2-second hold trigger on the snacks reading
page only; short ≤20-word definition; ≤10-word selected term; escaped-text-only rendering; no
persistence/schema change; Task 12 out of scope), **Clean Architecture + LLM-Abstraction**
(constitution II/III), **quality gate for generated content** (constitution IV), **MVP-first/YAGNI**
(constitution V), and **the existing Playwright suite as the regression contract**.

---

## R1. Endpoint shape: a new provider-agnostic endpoint vs. reusing the meaning-snack endpoint

### Options evaluated

**Option A — reuse `POST /api/meaning-snacks/generate` (returns a full meaning snack).**
**Option B — a new `POST /api/definitions/generate` returning `{ term, definition }` under a small
`DefinitionController`.**
**Option C — fold the definition into the existing keyword/meaning-snack response shapes.**

### Decision

**Option B**, per the confirmed spec Clarification #4 ("a new provider-agnostic endpoint … rather
than reusing the meaning-snack endpoint, which returns a full snack").

### Rationale

- **The response shapes differ fundamentally.** `POST /api/meaning-snacks/generate` returns a
  persisted `MeaningSnackItem` (concept/metaphor/whyItMatters/related) and is idempotent by
  `(subtopicId, term)`; the selection-Define flow needs a single transient `definition` string and
  nothing persisted. Reusing it would force the frontend to discard four of five fields and would
  couple the trigger to a subtopic it may not have (a meaning-snack card has no quiz subtopic).
- **`themeId` is required and available.** The trigger is offered only on the snacks reading page,
  which always has `AppState.theme().themeId`. The theme supplies `theme.getPrompt()` to the LLM call,
  exactly as `MeaningSnackService.generate` does.
- **`subtopicId` is deliberately omitted.** A term's definition is theme-scoped, not
  subtopic-scoped; the selected term may be inside a meaning-snack card (which has only a placement
  `sourceSubtopicId`, not a quiz subtopic). Including it would be unused context (YAGNI,
  constitution V). A later additive field would be non-breaking if ever needed.
- **Consistent conventions.** The project already exposes small per-aggregate controllers
  (`MeaningSnackController` → `/api/meaning-snacks`); a `/api/definitions` group follows the same
  shape and keeps the regular snack/meaning-snack contracts frozen.

### Alternatives considered and rejected

- **A** — returns a full snack, requires a subtopic the trigger may not have, and persists a row the
  spec says must not exist.
- **C** — would change existing DTO shapes and the meaning-snack contract, inflating the regression
  surface (constitution V).

### Open decision

Require `themeId`, omit `subtopicId` (plan.md Open Decisions #1).

---

## R2. LLM abstraction: new port method and record vs. reusing `generateMeaningSnack`

### Options evaluated

**Option A — a new provider-agnostic `LlmProvider.generateDefinition(theme, term)` returning
`GeneratedDefinition(definition)`, with a new externalized prompt.**
**Option B — reuse `generateMeaningSnack(theme, term, definition)` (pass a placeholder definition).**
**Option C — hardcode the prompt in Java and call the provider directly from the service.**

### Decision

**Option A**.

### Rationale

- Constitution III: all LLM interactions go through `LlmProvider`; prompts are externalized, and no
  provider class leaks into services/controllers.
- The requested output is a **single short definition**, not the `concept/metaphor/whyItMatters/
  related` shape `generateMeaningSnack` returns. A distinct port method keeps the two contracts
  honest and the parsing simple (`node.path("definition").asText(null)`).
- The record mirrors the existing `GeneratedSnack`/`GeneratedMeaningSnack` records, so the seam is
  consistent and provider-agnostic.
- Adding an interface method requires updating **both** implementers (`OpenAiLlmProvider`,
  `RetryingLlmProvider`) and the `RetryingLlmProviderTest.StubProvider` test double in the same task.

### Alternatives considered and rejected

- **B** — abuses the port (a definition is not a meaning snack), produces the wrong shape, and offers
  no clean prompt.
- **C** — violates the externalized-prompt principle and leaks provider logic into the service.
- **A `default` throwing method** — hides an unimplemented port and violates the spirit of the
  abstraction (same stance as feature 014 R9).

---

## R3. Term validation and tokenization (≤10 words)

### Options evaluated

**Option A — `split("\\s+")` whitespace tokenization with hyphenated compounds counting as one,
bound ≤10, blank rejected; validation at the service boundary (and mirrored in the frontend).**
**Option B — a regex/`\b` word count.**
**Option C — a character-length cap instead of a token cap.**

### Decision

**Option A**, consistent with the feature-013/015 rule.

### Rationale

- The spec fixes the rule: "≤10 whitespace-separated tokens … hyphenated compounds count as one
  token" (FR-010/FR-011/FR-012). Reusing `split("\\s+")` keeps this feature's rule visibly identical
  to `SnackService.normalizeKeywords` / `MeaningSnackService.normalizeDefinition`.
- Tabs, multiple spaces and newlines are already separators under `\\s+`; whitespace-only input
  collapses to blank and is rejected.
- **Backend is authoritative**; the frontend mirrors the rule for immediate feedback but the backend
  re-validates and never calls the LLM for a rejected term (defence-in-depth).
- Rejection (not truncation) is correct here because the term is **user input**, not model output:
  the spec says blank/oversized selections "MUST be rejected with a meaningful error and MUST NOT be
  submitted" (FR-011). This contrasts with generated definitions (R6), where truncation is used.

### Alternatives considered and rejected

- **B** — changes counting semantics and diverges from the existing helper.
- **C** — the requirement is a word (token) cap; a character cap would not express "10 words".

---

## R4. The 2-second hold trigger: directive vs. page-owned vs. component-owned

### Options evaluated

**Option A — a standalone `SelectionDefineDirective` on the reading container owning
`selectionchange` + the 2s timer, emitting the term + rect; the page owns the request/state; a
separate presentational affordance component renders the button.**
**Option B — the snacks page (`SnacksComponent`) owns the selection handling inline.**
**Option C — the affordance component owns the selection handling itself.**

### Decision

**Option A**.

### Rationale

- **Separation of concerns.** The directive is pure selection/timer logic with no DOM rendering and
  no HTTP; the affordance is presentational; the page owns orchestration (theme, request, card).
  This keeps the risky selection/timer logic small and independently reasoned about, and mirrors the
  existing pattern where `RichTextComponent` emits and the page hosts the single card.
- **Testability.** The directive's only output is `{ term, rect }` / `selectionCleared`, so E2E can
  drive it with real selections without touching the page internals.
- **Security.** The directive reads only `Selection.toString()` (plain text) and a `Range`; it never
  reads `innerHTML` and never renders anything, preserving the feature-012 typed-AST boundary.
- **Scope.** Applying the directive only on the snacks reading container is the mechanical expression
  of FR-017 (results/history offer no trigger).

### Alternatives considered and rejected

- **B inline** — mixes DOM/timer logic into an already-large page component and makes the trigger
  harder to scope and test.
- **C in the component** — couples the presentational button to the document selection and makes it
  harder to keep exactly one affordance per page.

### Open decision

The affordance appears for any held non-collapsed in-content selection; validation happens on
activation (plan.md Open Decisions #4).

---

## R5. Feeding the generated definition into the existing single card

### Options evaluated

**Option A — reuse `KeywordDefinitionCardComponent` by setting `activeKeyword = { term, definition }`,
adding only additive inputs (`definitionLoading`, `definitionError`, `anchorRect`).**
**Option B — a separate "custom definition card" component.**
**Option C — render the definition inline near the selection (not in the card).**

### Decision

**Option A**.

### Rationale

- FR-008 says the **definition card** MUST open for the selected term and show the generated meaning;
  the spec's Non-Goals forbid redesigning the card. Reusing it is the direct expression of the
  requirement and guarantees the **single-card rule** (the page already hosts exactly one card and
  opening a new term replaces the previous one).
- The card already renders `term`/`definition` via escaped interpolation; the extension adds only
  static labels and a loading/error state, so the security boundary is unchanged.
- Additive inputs default to no-op, so feature-013/014 behaviour and E2E assertions are preserved.
- For positioning, a selection has no anchor element, so an optional `anchorRect` input is used when
  `anchor` is null (the card already clamps to the viewport and falls back to a bottom sheet).

### Alternatives considered and rejected

- **B** — duplicates the card, risks two visible cards (violating the single-card rule), and
  contradicts the Non-Goals.
- **C** — contradicts FR-008 and would create a second rendering surface to secure.

### Open decision

The custom-define card disables the feature-014 "Generate snack" action (`generateEnabled=false`)
because Task 12 is out of scope (plan.md Open Decisions #3).

---

## R6. Validation of the generated definition (≤20 words) and re-roll policy

### Options evaluated

**Option A — validate non-blank; re-roll once on blank; truncate the accepted definition to ≤20
tokens; a second blank failure → 503.**
**Option B — re-roll on any >20-token result and reject after one retry.**
**Option C — trust the prompt and do no service-side bound.**

### Decision

**Option A**.

### Rationale

- FR-004 requires the result to be **bounded to at most 20 words**; the service boundary is the
  authoritative enforcement point (constitution IV). Truncating to the first 20 tokens (mirroring
  feature 015's `normalizeDefinition`) guarantees the bound without adding a new failure mode for a
  merely-long response.
- A **blank** generated definition is a genuine quality failure and triggers the existing single
  re-roll, then a typed 503 — consistent with `MeaningSnackService.generateValid`.
- Truncate-don't-reject for length matches the established feature-013/015 definition policy and
  keeps the UI stable (the card always gets a non-empty, bounded definition on success).

### Alternatives considered and rejected

- **B** — a >20-token result is not worth discarding and re-rolling; truncation preserves value and
  matches the existing policy.
- **C** — would let an unbounded response reach the UI, failing FR-004 and the "bounded content"
  expectation.

---

## R7. Persistence and schema

### Decision

**No persistence and no schema change.** The generated definition is returned in the response and
shown transiently in the card; no entity, column, index or table is added or altered, so Hibernate
`ddl-auto=update` issues no DDL (spec Clarification #6, Non-Goals, data-model.md §4).

### Rationale

- The spec is explicit: "No new persistence or schema change" and "the generated definition is
  produced on demand and shown in the card; nothing new is persisted".
- There is nothing to key a persisted row on meaningfully (the term is arbitrary and may be selected
  in a meaning-snack card), and the feature has no retrieval/reload requirement.
- The project has no migration tool; avoiding any schema change keeps the feature fully reversible
  and backward compatible.

### Alternatives considered and rejected

- **Persist definitions** (e.g. a `term_definition` table with idempotency) — out of scope, adds a
  table and retrieval path the spec does not require (constitution V).

---

## R8. Security: rendering the selected term and generated definition

### Decision

Reuse the existing escaped-interpolation path: the selected term is read as **plain text**
(`Selection.toString()`) and sent to the backend; the returned definition is shown through the
existing card's `{{ kw.definition }}` interpolation. **No `[innerHTML]`, no `DomSanitizer`** anywhere
in the new code. The affordance button renders only a static label.

### Rationale

- The selected term and the generated definition are untrusted (document text / model output) and
  reach the DOM; escaping them via interpolation preserves the feature-012/013/014/015 XSS defence
  (FR-013/FR-014/FR-015, SC-004).
- The directive never reads `innerHTML` and never renders; it holds a plain string and a `Range`.
- The prompt forbids markup, and the card does not use the rich-text renderer for the definition, so
  no markup is ever interpreted.

### Alternatives considered and rejected

- **A sanitizer / `DomSanitizer`** — reintroduces the HTML-binding path feature 012 deliberately
  closed.
- **Rendering the generated definition through the rich-text renderer** — unnecessary (a definition
  is plain text) and would expand the rendering surface.

---

## R9. Testing approach given the repo's runners

### Decision

Cover the backend behaviour with JUnit/Mockito unit tests and a standalone MockMvc contract test;
cover the selection trigger, cancellation, generation UX, bounds, duplicate guard, failure, scope
and XSS through **Playwright E2E** with route-mocked API responses. Introduce no new runner and no
new dependency.

### Rationale

- `frontend/package.json` has `@playwright/test` but **no** Karma/Jest/Vitest runner, matching
  features 012/013/014/015. Adding one is a new tooling dependency beyond this feature's scope
  (constitution V).
- The selection/timer logic is DOM/event-driven and is most faithfully exercised in a real browser;
  Playwright can create pointer selections (`page.mouse`) and programmatic selections
  (`document.getSelection()`), so both FR-016 paths are objectively verifiable.
- Route mocking feeds deterministic success/failure/latency fixtures, enabling the loading, error,
  duplicate and bounds assertions without a live model.
- The backend suites assert the ≤10-token term bound, the ≤20-token definition bound, the re-roll
  policy and the error shapes — all hidden behind the mocked API in E2E.

### Open question (flagged for the orchestrator)

If true unit coverage of the pure selection/timer logic is wanted, Vitest as a dev-only dependency
is the minimal option; this plan does not assume it (same stance as 013/014/015).

---

## R10. Scope: the trigger is offered only on the snacks reading page

### Decision

Apply the `SelectionDefineDirective` **only** to the snacks reading container
(`frontend/src/app/pages/snacks/`). The results recap and read-only history rich-text surfaces get
**no** directive and **no** affordance; `RichTextComponent` is unchanged.

### Rationale

- FR-017 scopes the trigger to the snacks reading page and requires the other surfaces to remain
  unchanged; not applying the directive is the structural expression of that scope (no runtime
  conditional that a future surface could forget).
- The shared `RichTextComponent` is used by all three surfaces, so putting selection logic there
  would leak the trigger onto results/history. Keeping it in the page-level directive avoids that.

### Alternatives considered and rejected

- **A flag on `RichTextComponent`** — would spread selection logic into the shared renderer and risk
  enabling the trigger on every surface (FR-017/FR-018).

---

## Summary of decisions

| # | Decision | Key rationale |
|---|----------|---------------|
| R1 | New `POST /api/definitions/generate` `{themeId, term}` | Spec Clarification #4; different response shape; theme always available; no unused subtopic |
| R2 | New externalized prompt + `LlmProvider.generateDefinition`/`GeneratedDefinition` | Constitution III; distinct single-definition contract; consistent record seam |
| R3 | Whitespace-token term bound ≤10, blank/oversized rejected (not truncated) | Spec FR-010..FR-012; user input is rejected, unlike model output |
| R4 | Selection logic in a pure directive; page owns the request; presentational affordance | Separation of concerns; testability; security; scope |
| R5 | Reuse the single definition card with additive `definitionLoading`/`definitionError`/`anchorRect` | FR-008; single-card rule; escaped-only boundary preserved |
| R6 | Validate non-blank + re-roll once; truncate the definition to ≤20 tokens | Constitution IV; FR-004; matches feature-015 truncate policy |
| R7 | No persistence, no schema change | Spec Clarification #6 / Non-Goals; nothing to retrieve |
| R8 | Plain-text selection + escaped interpolation; no `[innerHTML]`/`DomSanitizer` | Preserves the feature-012/013/014/015 XSS defence |
| R9 | Backend JUnit/MockMvc + Playwright E2E; no new runner | Repo has no frontend unit runner; DOM-driven logic is E2E-native |
| R10 | Directive only on the snacks reading page | FR-017; shared renderer untouched; structural scope |
