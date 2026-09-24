# Research: "Create snack" from a Custom Keyword's Generated Meaning (Task 12)

**Phase 0 output** for `/specs/017-custom-keyword-create-snack`. Each section records the **Decision**,
**Rationale**, and **Alternatives considered**, grounded in the real codebase
(`frontend/src/app/**`, `frontend/e2e/**`, `backend/src/main/java/com/endia/**`) and the feature-013/
014/015/016 precedents (`specs/013-snack-keywords/*`, `specs/014-meaning-snacks/*`,
`specs/015-keyword-meaning-20-words/*`, `specs/016-custom-keyword-define/*`).

The governing constraints are: **the confirmed spec** (Option A source subtopic; action only after a
valid generated meaning; reuse/relabel the feature-014 slot; feature-014 dedup key; no backend/schema/
endpoint change; escaped-text-only rendering), **Clean Architecture + LLM-Abstraction** (constitution
II/III), **quality gate for generated content** (constitution IV), **MVP-first/YAGNI** (constitution V),
and **the existing Playwright suite as the regression contract**.

---

## R1. Where to capture the source subtopic (Option A)

### Options evaluated

**Option A — capture at `onDefineRequested` (when the custom definition is requested / the card opens),
into the existing `activeKeywordSubtopicId` signal, resolving the active entry's source subtopic
(regular `subtopicId` / meaning `sourceSubtopicId`).**
**Option B — capture at "Create snack" activation time from `currentEntry()`.**
**Option C — capture in the directive when the selection is made.**
**Option D — send no `subtopicId` and let the backend pick a source.**

### Decision

**Option A.**

### Rationale

- **Spec alignment (Option A, Clarification #1)**: the snack attaches to the active entry's source
  subtopic — regular → `subtopicId`, meaning → `sourceSubtopicId`. Capturing at Define time records the
  entry whose meaning was read.
- **Matches feature-014's established invariant.** `openKeyword` already captures the source at
  card-open (`activeKeywordSubtopicId.set(entry?.kind === 'regular' ? entry.subtopicId : null)`) with
  the explicit comment: "Use the source captured when the card opened, never the (possibly navigated)
  current entry." The custom flow should behave the same way.
- **Fixes a real gap.** Today `activeKeywordSubtopicId` is set **only** in `openKeyword` for regular
  entries; the custom-Define flow (`onDefineRequested`) leaves it `null`. Setting it there makes the
  existing `onGenerateRequested` work **unchanged** for the custom card and also supports the
  meaning-entry case (which `openKeyword` currently maps to `null`).
- **Navigation safety is already structural.** `goTo(...)` calls `closeKeyword()` (clearing
  `activeKeywordSubtopicId`) and `openKeyword(...)` replaces the card/resets custom state, so the active
  entry cannot change while the custom card is open. Capturing at open time is belt-and-braces.
- **No backend/schema change** (see R2): both `subtopicId` and `sourceSubtopicId` are persisted
  `Subtopic` ids, valid `subtopicId` arguments to the feature-014 endpoint.

### Alternatives considered and rejected

- **B** — relies on the current entry being unchanged at a later moment instead of pinning the entry the
  meaning was read from; diverges from the feature-014 capture-at-open invariant.
- **C** — the directive is scoped to selection/timer only (feature 016 R4); it has no knowledge of the
  backbone entry and must not gain card/DOM-render responsibility.
- **D** — the endpoint requires a `subtopicId` (`@NotNull`); omitting it would be a backend contract
  change the spec forbids.

---

## R2. Is any backend change required? (confirm or refute)

### Options evaluated

**Option A — no backend change; reuse `POST /api/meaning-snacks/generate` and
`GET /api/meaning-snacks?themeId=` unchanged.**
**Option B — add a custom-specific endpoint or an additive field to the generate request.**
**Option C — change `MeaningSnackService` to accept a nullable subtopic.**

### Decision

**Option A — refuted any need for a backend change. The existing feature-014 contract fully suffices.**

### Rationale (evidence from the code)

- `MeaningSnackGenerateApiRequest` accepts `{themeId, subtopicId, term, definition}` with `themeId`/
  `subtopicId` `@NotNull` and `term`/`definition` `@NotBlank`. The custom term (a feature-016 selection,
  ≤10 tokens) and the generated definition (≤20 words, non-blank) satisfy these.
- `MeaningSnackService.generate` has **no term-length bound** (it trims), and `MeaningSnack.sourceTerm`
  is `VARCHAR(500)` — a ≤10-token term fits comfortably. `normalizeDefinition` clamps the definition to
  ≤20 tokens / ≤200 chars, matching `sourceDefinition VARCHAR(200)`; the feature-016 definition is
  already within bounds, so it passes through un-truncated.
- The source subtopic resolves: both a regular entry's `subtopicId` and a meaning entry's
  `sourceSubtopicId` are persisted `Subtopic` rows, so `subtopicRepository.findById` succeeds.
- Idempotency/duplication is already handled: `ux_meaning_snack_source (source_subtopic_id,
  source_term)` unique index + `findBySourceSubtopicIdAndSourceTermIgnoreCase` pre-query returning
  `alreadyExisted=true`, plus a `DataIntegrityViolationException` race recovery.
- Retrieval is already theme-scoped: `GET /api/meaning-snacks?themeId=` returns all `MeaningSnack`s
  ordered by `createdAt`, which the page loads on every entry — covering reload/new-session.
- Quiz-free + distinct are properties of the `MeaningSnack` aggregate (no quiz association, `kind =
  MEANING`) and the existing backbone `snack-card--meaning` branch.

### Alternatives considered and rejected

- **B** — would add an endpoint/field the spec explicitly forbids (FR-016, SC-007, Non-Goals) for no
  behavioral gain; the existing shape is a strict superset of what this feature needs.
- **C** — would alter the feature-014 contract (a nullable placement anchor) and is unnecessary.

### Consequence

This is an **entirely frontend change**. If a backend change were ever strictly required it would have to
be additive and must not alter the feature-014 contract; this plan introduces none.

---

## R3. Action availability: how to gate "Create snack"

### Options evaluated

**Option A — compute availability in the page (`generateEnabled` custom branch) as: not loading, no
definition error, definition non-empty, and a captured source subtopic exists.**
**Option B — add a dedicated `createEnabled` input to the card separate from `generateEnabled`.**
**Option C — let the card infer availability from `keyword.definition`.**

### Decision

**Option A.**

### Rationale

- The card already receives `generateEnabled`/`generateState`/`generateError` and already refuses to
  emit unless `generateEnabled() && generateState() === 'idle'`; gating in the page keeps all
  orchestration/state ownership in the page (matching feature 013/014/016).
- Requiring `!definitionLoading && !definitionError && !!definition && !!capturedSubtopic` makes the
  action appear **only** after a valid generated meaning exists (FR-002, SC-001) and never before
  "Define" completes, on a blank definition, or after an error.
- The regular branch is kept identical to today, so feature 014 is not regressed.

### Alternatives considered and rejected

- **B** — an extra input would duplicate the meaning of `generateEnabled` and risk two controls;
  relabeling the existing slot is simpler (FR-003).
- **C** — pushes policy into the presentational component and would not know about loading/error/
  captured-subtopic state owned by the page.

---

## R4. Action slot: reuse/relabel vs. duplicate

### Options evaluated

**Option A — reuse the feature-014 `.keyword-card__generate` slot and its `idle｜loading｜done` states;
relabel for custom mode via a new boolean input `customDefinition` (default `false`).**
**Option B — add a second, separate "Create snack" control.**
**Option C — a separate custom-definition card component.**

### Decision

**Option A.**

### Rationale

- FR-003 requires exactly one snack-creation action per card; reusing the slot is the direct expression
  of that, and the card already handles the states, accessibility (live region, `aria-disabled`), and
  the mounted-through-states focus behavior.
- A boolean input defaulting to `false` is a no-op for the results/history/regular-keyword surfaces, so
  feature-013/014 behavior and E2E assertions are preserved.
- Labels: idle `Create snack`, loading `Creating…`, done `Added to your snacks` (the done state is
  shared semantics: the item is in the backbone).

### Alternatives considered and rejected

- **B** — would show two controls (or require conditional hiding), contradicting FR-003 and the
  single-action-per-card rule.
- **C** — duplicates the card, risks two visible cards (single-card rule), and contradicts the
  spec's Non-Goals ("no redesign of the definition card").

---

## R5. Generation wiring: reuse `onGenerateRequested`

### Decision

Reuse `SnacksComponent.onGenerateRequested(keyword)` **unchanged**. It already:

- reads the captured `activeKeywordSubtopicId()` (not the current entry);
- guards with `generatingKeys` keyed by `${subtopicId}::${term.toLowerCase()}`;
- sets `generateStates[key] = 'loading'`, clears `generateError`;
- calls `meaningSnackService.generate(themeId, subtopicId, keyword.term, keyword.definition)`;
- on success appends deduped by `id`, `markGenerated`, updates `AppState.meaningSnacks`, sets `done`,
  `clampStoredIndex`;
- on error resets the guard/state and sets `generateError` via `ErrorService.getMessage`, adding
  nothing.

The only adjacent change is `activeGenerateState`, which must resolve the state key for the custom card
using the captured `activeKeywordSubtopicId` (behavior-equivalent for regular cards).

### Rationale

- Single-sourcing the flow guarantees identical dedup/append/mark-generated/error semantics across the
  keyword and custom surfaces (FR-004, FR-007, FR-008, FR-009, SC-002/SC-003).
- For the custom card `keyword.definition` is the generated meaning, so the same call carries the
  required `definition` argument.

### Alternatives considered and rejected

- **A parallel `onCreateRequested`** — would duplicate guard/append/error logic and risk divergence.

---

## R6. Dedup / at-most-one

### Decision

Rely on the existing three layers unchanged: the card's `requestGenerate()` guard
(`generateState() !== 'idle'`), the page's `generatingKeys` Set, and the backend's unique index +
idempotent `alreadyExisted=true` return (with the page deduping the appended list by `id`).

### Rationale

- FR-007/FR-010 and SC-002 require at most one request and no duplicate entry on rapid activation or
  re-open; these layers already deliver that for feature 014 and apply identically to the custom card
  because the key is the same `(sourceSubtopicId, term.toLowerCase())`.

### Alternatives considered and rejected

- **A new dedup mechanism** — redundant and would risk divergence from the feature-014 key.

---

## R7. Persistence / reload and quiz-free / distinct

### Decision

No new work. The created snack is a feature-014 `MeaningSnack`; `loadMeaningSnacks(themeId)` on page
load re-lists it after a reload/new session (FR-014/FR-015, SC-005), and the backbone `snack-card--
meaning` branch renders it distinctly with no quiz (FR-011–FR-013, SC-004). A failing/empty listing
already degrades gracefully.

### Rationale

- The spec explicitly requires reusing the feature-014 retrieval/visual/no-quiz path; the code already
  provides it, and the custom-created row is indistinguishable from a keyword-created row at the data
  layer.

### Alternatives considered and rejected

- **A custom retrieval path or a distinct rendering** — forbidden by the Non-Goals and unnecessary.

---

## R8. Security: rendering the custom term, generated meaning, and created content

### Decision

Reuse the existing escaped-interpolation path. The card renders `{{ kw.term }}` / `{{ kw.definition }}`;
the new action label is static text; the backbone meaning content is rendered through
`RichTextComponent` (typed AST). Introduce **no** `[innerHTML]` and **no** `DomSanitizer`.

### Rationale

- The custom term (document text) and the generated meaning (model output) are untrusted and reach the
  DOM; escaping preserves the feature-012/013/014/015/016 XSS defense (FR-017–FR-019, SC-006).
- No new rendering surface is added, so the existing sanitization model is untouched.

### Alternatives considered and rejected

- **A sanitizer / `DomSanitizer`** — reintroduces the HTML-binding path feature 012 deliberately closed.

---

## R9. Testing approach given the repo's runners

### Decision

Cover the change with **Playwright E2E** (route-mocked) — the repo's only frontend runner — and rely on
the existing backend suites unchanged (no backend production change). Introduce no new runner and no
new dependency.

### Rationale

- `frontend/package.json` has `@playwright/test` but no Karma/Jest/Vitest, consistent with 012–016.
- The behavior is DOM/state-driven (card action availability, POST body `subtopicId`, backbone append,
  reload) and is faithfully exercised in a real browser with mocked APIs.
- The reused backend bounds/error shapes are already covered by `MeaningSnackServiceTest` /
  `MeaningSnackContractTest`.

### Resolution (orchestrator, 2026-09-24)

E2E-only coverage is **accepted and the unit-coverage gap is explicitly waived** for this feature,
consistent with features 012–016: the repo has no frontend unit runner, the logic is DOM/state-driven
and exercised in a real browser, and the reused backend bounds/error shapes are already covered by
`MeaningSnackServiceTest` / `MeaningSnackContractTest`. If true unit coverage of the pure capture/state
logic is later wanted, Vitest as a dev-only dependency is the minimal option; this feature does not add
it.

---

## R10. Scope: only the snacks reading page

### Decision

The custom "Create snack" action is offered only through the snacks reading page's definition card
(feature 016's Define flow). The results recap and read-only history surfaces get no custom-Define
trigger and no "Create snack" action (the card's `customDefinition` input defaults `false` there).

### Rationale

- The custom-Define flow itself is scoped to the snacks page (feature 016 R10); "Create snack" is a
  continuation of that flow, so it inherits the same scope. FR-022 requires feature 016's flow to remain
  unchanged apart from the added action.

### Alternatives considered and rejected

- **Enabling a custom-Define + Create snack flow on results/history** — out of scope; would add
  triggers the spec's Non-Goals exclude.

---

## Summary of decisions

| # | Decision | Key rationale |
|---|----------|---------------|
| R1 | Capture the source subtopic at `onDefineRequested` into `activeKeywordSubtopicId` | Spec Option A; feature-014 capture-at-open invariant; fixes the custom/meaning gap; navigation-safe |
| R2 | **No backend change** — reuse the feature-014 contract | Endpoint/DTO/service/entity/idempotency/retrieval all suffice; custom inputs are valid |
| R3 | Gate availability in the page's `generateEnabled` custom branch | Only after a valid meaning; loading/error/blank excluded; regular branch unchanged |
| R4 | Reuse/relabel the feature-014 action slot via a `customDefinition` input | FR-003 one action per card; no-op default preserves 013/014 |
| R5 | Reuse `onGenerateRequested` unchanged; adjust `activeGenerateState` key | Single-sourced dedup/append/error semantics |
| R6 | Reuse the existing three-layer dedup | FR-007/FR-010; identical feature-014 key |
| R7 | No new persistence/quiz-free/distinct work | The `MeaningSnack` kind already provides them |
| R8 | Escaped interpolation only; no `[innerHTML]`/`DomSanitizer` | Preserves the 012/013/014/015/016 XSS defense |
| R9 | Playwright E2E + existing backend suites unchanged; no new runner | Repo has no frontend unit runner; behavior is E2E-native |
| R10 | Only the snacks reading page | Continuation of the feature-016 scoped Define flow |
