# Research: Expand Keyword Meaning Limit from 3 to 20 Words

**Phase 0 output** for `/specs/015-keyword-meaning-20-words`. Each section records the **Decision**,
**Rationale**, and **Alternatives considered**, grounded in the real codebase
(`backend/src/main/java/com/endia/**`, `backend/src/main/resources/prompts/**`,
`frontend/src/app/**`, `frontend/e2e/**`) and the feature-013/014 precedents.

The governing constraints are: **a constant/policy change only** (no new endpoint, entity, port method
or schema change), **truncate-don't-reject** at the service boundary, **whitespace-separated tokens
with hyphenated compounds counting as one**, **escaped-text-only rendering** (no `[innerHTML]`, no
`DomSanitizer`), **externalized/provider-agnostic LLM flow**, and **the existing suites as the
regression contract**.

---

## R1. Tokenization rule and the "word" definition

### Options evaluated

**Option A — whitespace split (`split("\\s+")`), hyphenated compounds count as one token.**
**Option B — a regex word count (`\b`) or language-aware tokenizer.**
**Option C — a character-length cap instead of a token cap.**

### Decision

**Option A**, unchanged from feature 013.

### Rationale

- `SnackService.normalizeKeywords` and `MeaningSnackService.normalizeDefinition` already use
  `definition.split("\\s+")` and `String.join(" ", Arrays.copyOf(tokens, N))`. Reusing that exact
  mechanism for N = 20 keeps the two rules visibly identical (FR-003, FR-005) and keeps the change to
  a single constant.
- Tabs, multiple spaces and newlines are already treated as separators by `\\s+`; a whitespace-only
  definition already becomes `[""]`/empty and is dropped (FR-006). No new edge-case handling is needed.
- Hyphenated compounds (`state-of-the-art`) are one token because the split is on whitespace only —
  consistent with the feature-013 rule and the spec Assumption.
- A regex/`\b` counter would split hyphenated compounds and punctuation differently, changing behavior
  beyond the requested policy and risking a mismatch between the two services.

### Alternatives considered and rejected

- **B** — changes the counting semantics and diverges from the existing helper; the spec fixes the
  whitespace-token rule.
- **C** — the requirement is a word (token) cap; a character cap would not express "20 words" and would
  interact differently with the `VARCHAR(200)` column (R6).

---

## R2. Where the bound lives: two private constants vs. a shared policy type

### Options evaluated

**Option A — a private `MAX_DEFINITION_TOKENS = 20` constant in each of the two services.**
**Option B — a shared constant in a new domain policy class / enum.**
**Option C — the bare literal `20` inline (minimal diff).**

### Decision

**Option A** (recommended); **C** is acceptable if the orchestrator prefers the smallest diff.

### Rationale

- The two services are in the same layer (`application/service`) but have no existing shared policy
  type. Introducing one (B) is a structural addition the spec's Non-Goals discourage for a constant
  change.
- A private named constant removes the magic number and makes the "mirrors SnackService" javadoc
  self-evident, with a two-line diff per service.
- The literal (C) is a strictly smaller change but leaves `20` unexplained and duplicated; the spec's
  scope permits either.

### Alternatives considered and rejected

- **B** — a new type/class for one integer is over-engineering (constitution V / YAGNI) and would touch
  a layer boundary the spec says must not change.

---

## R3. Card layout: natural growth vs. bounded max-height with internal scroll

### Options evaluated

**Option A — let the definition card grow naturally; no max-height/scroll (spec Clarification #3).**
**Option B — add a bounded `max-height` with internal scroll now.**
**Option C — redesign the card (e.g. a modal) for long text.**

### Decision

**Option A**, with **B** as the agreed fallback only if verification shows clipping.

### Rationale

- The existing card already clamps width to `min(280px, calc(100vw - 16px))`, applies
  `overflow-wrap: anywhere`, and falls back to a bottom sheet on narrow viewports
  (`keyword-definition-card.component.scss`). A 20-word gloss (typically 1–3 lines) fits without
  structural change.
- Adding a scroll region now would introduce a new interaction (and potential focus/scroll traps) for
  a case not yet observed — contrary to MVP-first.
- The spec explicitly confirms natural growth and reserves max-height/internal scroll as a
  verification-triggered fallback.

### Alternatives considered and rejected

- **B now** — premature; the spec says "no max-height or internal scroll is added now".
- **C** — contradicts the Non-Goals ("No redesign of the definition card").

---

## R4. Prompt change: wording scope and provider-agnosticism

### Options evaluated

**Option A — edit only the prompt text (`generate-snack.md` rules + example; `generate-meaning-snack.md`
wording); no Java/provider change.**
**Option B — also add provider-side enforcement/clamping of definition length in `OpenAiLlmProvider`.**
**Option C — hardcode the limit in Java instead of the prompt.**

### Decision

**Option A**.

### Rationale

- Constitution III / FR-010: the flow stays externalized and provider-agnostic. The prompt is the
  instruction surface; the service normalization is the enforcement surface.
- The service boundary already truncates over-limit definitions to the first 20 tokens (M1), so
  provider-side clamping would duplicate the rule in the wrong layer and couple the provider to a
  domain policy.
- `generate-snack.md`'s only length-bearing lines are the example JSON (`"definition": "at most three
  words"`, two occurrences) and the explicit rule line; the keyword count guideline (2–5), verbatim
  rule, plain-text rule, formatting whitelist and forbidden list are untouched (FR-008, FR-016).
- `generate-meaning-snack.md` describes the supplied definition as "very short"/"short"; this is a
  wording-only consistency fix so the model does not treat a longer source definition as an error. Its
  JSON shape, `related` 1–3 rule and "no quiz / no keywords" rules are unchanged (FR-018).

### Alternatives considered and rejected

- **B** — provider-specific enforcement violates the abstraction and duplicates the service policy.
- **C** — violates the externalized-prompt principle (constitution III).

---

## R5. Documentation surfaces to update (and which to leave)

### Options evaluated

**Option A — update only the in-code comments/javadoc and prompt files changed by this feature.**
**Option B — also rewrite historical spec artifacts from features 013/014 (contracts, data-model,
plan).**
**Option C — update nothing textual; code only.**

### Decision

**Option A**; **B** is flagged as an open decision (not done by default).

### Rationale

- FR-009 enumerates the in-code surfaces: `SnackKeyword`, `SnackKeywordDto`, `frontend snack.model.ts`,
  and "any equivalent in-code statement changed by this feature" (the two service comments). SC-007
  scopes the "0 stale statements" requirement to "the surfaces changed by this feature".
- Historical specs (`specs/013-snack-keywords/contracts/snack-keywords-contract.md` still says "at most
  3 words") are the record of the prior feature; rewriting them would rewrite history and is out of the
  spec's stated scope. If the orchestrator wants a consistent corpus, it is a documentation-only,
  behavior-free edit.

### Alternatives considered and rejected

- **C** — leaves stale in-code statements, failing FR-009/SC-007.
- **B by default** — out of scope; would touch many files unrelated to the runtime change.

---

## R6. Storage bound: keep `VARCHAR(200)` and clamp on a word boundary (decision (a) / FR-019)

### Options evaluated

**Option A — no schema change; keep `VARCHAR(200)` and clamp the normalized source definition to
≤200 characters on a word boundary, in addition to the 20-token cap. (chosen)**
**Option B — widen `MeaningSnack.sourceDefinition` from `VARCHAR(200)` to `VARCHAR(500)`.**
**Option C — migrate/normalize previously stored 3-word definitions.**

### Decision

**Option A** — selected by the product owner (encoded as spec **FR-019**); **B** and **C** rejected.

### Rationale

- **No migration / no schema change (FR-017).** `MeaningSnack.sourceDefinition` stays an existing
  `@Column(length = 200)` `VARCHAR(200)`; Hibernate issues no DDL and previously stored values remain
  valid and untouched.
- **MySQL strict-mode safe.** 20 average words (~8 chars + space) is ≈180 chars and fits, but 20 long
  words could exceed 200 and would make a strict-mode insert fail. Clamping the value to ≤200 chars at
  the service boundary removes that failure mode entirely, so the column can never be overrun.
- **Word-boundary clamp, never a mid-word cut.** When the token-capped value exceeds 200 characters,
  back up to the last whitespace at or before index 200 (`lastIndexOf(' ', 200)`) and cut there. This
  keeps the stored text readable and never splits a word. Degenerate single-token-longer-than-200
  falls back to a hard 200-char cut (still non-empty, so existing blank-handling is unaffected).
- **Only the source definition needs this.** `SnackKeyword.definition` is stored inside `Snack.keywords`,
  a `JSON` column (via `JpaSnackKeywordListConverter`) — effectively unconstrained — so
  `SnackService.normalizeKeywords` gets the 20-token cap but **no** character clamp.
- **Order of operations:** token cap first (FR-001..FR-003), then the 200-char word-boundary clamp
  (FR-019). Both live in `MeaningSnackService.normalizeDefinition`.

### Alternatives considered and rejected

- **B (widen to `VARCHAR(500)`)** — an additive schema change the spec's no-schema-change stance
  (FR-017) rules out; unnecessary once the value is clamped.
- **C (migrate old definitions)** — violates "previously stored definitions remain valid and unchanged
  (no migration)"; definitions are free text and old 3-word values remain valid.

---

## R7. Test approach given the repo's runners

### Options evaluated

**Option A — extend the existing JUnit/Mockito backend tests and the existing Playwright E2E spec; no
new runner.**
**Option B — introduce Vitest for a frontend unit test of wrapping/overflow.**
**Option C — rely on the backend tests only.**

### Decision

**Option A**.

### Rationale

- `frontend/package.json` has `@playwright/test` but no Karma/Jest/Vitest runner (same as 012/013/014).
  Adding one is a new tooling dependency beyond this feature's scope (constitution V).
- FR-015 asks for a "focused frontend test/assertion" for a 20-word definition wrapping with no
  horizontal overflow. The existing `meaning-snacks.spec.ts` already demonstrates the exact
  `scrollWidth > innerWidth` overflow assertion pattern; reusing it in `snack-keywords.spec.ts` is the
  minimal, in-repo-native way to make SC-004 objectively verifiable.
- Backend boundary coverage (19/20/21) belongs in the existing `SnackServiceTest`,
  `MeaningSnackServiceTest` and `GenerateSnackPromptTest`.
- No DTO shape changes, so MockMvc contract tests and Testcontainers integration tests pass unchanged.

### Alternatives considered and rejected

- **B** — a new frontend runner is a project-wide decision, not part of a constant change.
- **C** — would not satisfy FR-015's explicit frontend assertion requirement.

---

## R8. Backward compatibility and no re-roll

### Decision

Previously stored 3-word definitions remain valid and are returned/render as-is; no migration and no
re-normalization on read. Over-limit definitions are **truncated to the first 20 tokens** at the
service boundary, never rejected and never triggering a re-roll — the same behavior as the old 3-token
rule, only with a larger bound.

### Rationale

- The normalization only runs on generated content at write time; stored values are served verbatim
  (FR-017, Assumptions).
- Keyword issues never trigger a re-roll in `SnackService`; `MeaningSnackService`'s re-roll is driven by
  empty generated content / `related` bounds, not by definition length. Raising the cap does not add a
  failure path (FR-002, FR-006, Clarification #6).

### Alternatives considered and rejected

- Rejecting over-limit definitions — would introduce a new failure mode the spec explicitly rules out
  ("truncate, never reject/re-roll").

---

## Summary of decisions

| # | Decision | Key rationale |
|---|----------|---------------|
| R1 | Whitespace-split tokenization; hyphenated = one token | Matches the existing `split("\\s+")` rule in both services (FR-005) |
| R2 | Private `MAX_DEFINITION_TOKENS = 20` in each service | Removes the magic number without a new shared type; literal 20 acceptable |
| R3 | Card grows naturally; no max-height/scroll now | Existing clamp + `overflow-wrap: anywhere` suffices; spec Clarification #3 |
| R4 | Prompt-only wording change; no provider logic | Constitution III / FR-010; service is the enforcement boundary |
| R5 | Update only in-code/prompt surfaces; leave historical specs | FR-009 + SC-007 scope; historical artifacts flagged |
| R6 | No schema change; clamp source definition to ≤200 chars on a word boundary (decision (a) / FR-019) | Honors FR-017; MySQL strict-mode safe; never splits a word; only the `VARCHAR(200)` source definition is clamped |
| R7 | Extend existing JUnit + Playwright tests; no new runner | Repo has no frontend unit runner; reuse the overflow-assertion pattern |
| R8 | Truncate at 20, never reject/re-roll; no migration | Matches existing behavior; backward compatible |
