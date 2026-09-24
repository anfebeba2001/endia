# Implementation Plan: Custom Keyword "Define" from a Held Text Selection (2-Second Trigger)

**Branch**: `016-custom-keyword-define` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/016-custom-keyword-define/spec.md`

## Summary

End-to-end feature that lets a learner define **any** term they select in a snack's reading content
on the `/snacks` reading page, not only the model-highlighted keywords of feature 013. The learner
selects one or more words; only after the selection is **held for ~2 seconds** does a transient,
selection-anchored **"Define"** affordance appear. Activating it generates a **short definition**
(≤20 words, consistent with feature 015) through the existing externalized, provider-agnostic LLM
flow and shows it in the **existing single definition card**. It builds on feature 013 (highlight +
card), feature 014 (meaning-snack generation wiring), and feature 015 (≤20-word meaning policy).

1. **Backend capability (FR-004/FR-005)**: a new externalized prompt `generate-definition.md`
   (placeholders `{{theme}}`, `{{term}}`) drives a new provider-agnostic
   `LlmProvider.generateDefinition(theme, term)` returning a small `GeneratedDefinition(definition)`
   record; `OpenAiLlmProvider` parses the `{ "definition": "..." }` JSON defensively;
   `RetryingLlmProvider` delegates the new method through its existing `retry(...)`.
2. **Service + endpoint (FR-004/FR-010/FR-011)**: a new `DefinitionService` resolves the theme,
   normalizes the term (trim + whitespace-collapse), enforces the **≤10 whitespace-token** bound
   (reject blank/oversized with a typed 400), calls the port, validates the generated definition
   (non-blank, truncate to ≤20 tokens) with **one re-roll**, then raises a typed 503. A new
   `DefinitionController` exposes `POST /api/definitions/generate` with `{ themeId, term }`.
3. **Selection trigger (FR-001..FR-003, FR-016)**: a standalone OnPush signals-first directive
   `SelectionDefineDirective` applied to the snacks reading container listens to
   `document:selectionchange`, starts a ~2s timer for a non-collapsed in-content selection, cancels
   it on selection change/clear, and emits the normalized term + selection rect when the hold
   completes. It works for pointer **and** programmatic/keyboard selection (`document.getSelection()`)
   and never reads/renders HTML (uses `Selection.toString()`).
4. **Affordance (FR-003)**: a small standalone `SelectionDefineAffordanceComponent` renders the
   transient, viewport-clamped, selection-anchored **"Define"** button; the snacks page hosts exactly
   one (mirroring the single-card rule). It is dismissed on selection change/clear, Escape, or
   navigation.
5. **Generation + display (FR-006..FR-009, FR-013..FR-015)**: the page owns the request
   (`DefinitionService`, one in-flight guard), shows a loading state, and on success feeds the
   generated definition into the existing `keyword-definition-card` as a `SnackKeyword`
   (`{ term, definition }`), rendered as **escaped interpolation only** (no `[innerHTML]`, no
   `DomSanitizer`). On failure a meaningful, user-safe error is shown and nothing empty/placeholder
   is added.
6. **Scope + non-regression (FR-017/FR-018)**: the trigger is offered **only** on the snacks reading
   page; the results recap and read-only history surfaces are untouched and offer no trigger.
   Existing keyword highlight/click, the single-card rule, the meaning-snack flow and rich-text
   rendering are unchanged. Task 12 ("Create snack" from the custom meaning) is **out of scope**.

The feature adds **one new endpoint group (`/api/definitions`), one new prompt, one new port method,
one new service, one new typed exception, two DTOs, one new directive, one new affordance component,
one new frontend model + service**, a small additive extension to the existing definition card, and
**no schema change and no new runtime dependency**.

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.4.x (backend); TypeScript 5.9.x + Angular 21.2.x
(frontend, standalone components, signals-first, `ChangeDetectionStrategy.OnPush`).

**Primary Dependencies**: Spring Web / Spring Data JPA / Hibernate / Jackson (backend); Angular core,
Router, signals, RxJS (frontend). **No new runtime dependency in either project** (research.md R9).

**Storage**: MySQL 8.x. **No schema change, no entity change, no migration.** The generated
definition is produced on demand, returned in the response and shown transiently in the card;
nothing is persisted (spec §Project Structure Impact, Clarification #6, Non-Goals). Because no
entity/column changes, Hibernate `ddl-auto=update` issues **no DDL**.

**Testing**:
- Backend: JUnit 5 + Mockito unit tests; MockMvc contract tests (standalone, real
  `GlobalExceptionHandler`); existing Testcontainers suites must pass unchanged. No new test
  infrastructure.
- Frontend: Playwright E2E only — `frontend/package.json` has **no** Karma/Jest/Vitest runner wired,
  consistent with features 012/013/014/015. Selection/timer logic is covered through route-mocked
  E2E fixtures; `npm run build` type-checks.

**Target Platform**: Modern desktop + mobile browsers (Chrome/Firefox/Safari last 2 versions); Linux
server.

**Project Type**: Web-application monorepo (`backend/` + `frontend/`).

**Performance Goals**: No measurable change. The endpoint is one LLM call (plus at most one re-roll)
triggered explicitly by a user click; selection detection is one `selectionchange` handler with a
single debounce timer and no per-keystroke work.

**Constraints**:
- LLM-Abstraction principle (constitution III): the new prompt stays externalized; the service
  depends only on `LlmProvider`, never on a provider class.
- Quality gate (constitution IV): the generated definition is validated at the service boundary and
  re-rolled once before failing.
- Backend is the normalization/validation boundary (≤10-token term, ≤20-token definition); the
  frontend mirrors the term rule for immediate feedback but the backend is authoritative.
- Security model (features 012/013/014/015) preserved unchanged: escaped interpolation only, no
  `[innerHTML]`, no `DomSanitizer`; selected and generated text are untrusted.
- The trigger is scoped to the snacks reading page only; results/history are unchanged.
- Existing backend/frontend/E2E suites are the regression contract and must pass unchanged.
- Out of scope: Task 12 ("Create snack" from the custom meaning); keyword highlight/click changes;
  meaning-snack flow changes; any persistence/schema change (spec Non-Goals).

**Scale/Scope**: 1 prompt, 1 port record + method (+ provider parse, + retry decorator, + test stub),
1 new service, 1 new typed exception, 2 DTOs, 1 new controller with 1 endpoint; frontend: 1 model,
1 service, 1 directive, 1 affordance component (3 files), 1 card extension (3 files), the snacks page
wiring (2 files), E2E helper + 1 new spec. No new dependency, no schema change.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment | Verdict |
|-----------|------------|---------|
| **I. Spec-Driven Development** | `spec.md` exists with 19 FRs and 7 SCs, all clarifications confirmed (no `[NEEDS CLARIFICATION]` markers) and is the source of truth; this `plan.md` and downstream `tasks.md` follow. | PASS |
| **II. Clean Architecture (Layered)** | The provider-agnostic `GeneratedDefinition` record lives on the existing `application/port` `LlmProvider`; orchestration/validation live in `application/service` (`DefinitionService`); the prompt loader + provider parse live in `infrastructure/llm`; DTOs and the new controller stay in `interfaces/rest`. Dependency direction (domain → application → infrastructure → interfaces) preserved. Frontend follows standalone/signals/OnPush: a pure directive, a presentational affordance component, and an in-place additive card extension. | PASS |
| **III. LLM Abstraction First** | New prompt externalized in `generate-definition.md`; the only seam is the provider-agnostic `LlmProvider.generateDefinition(theme, term)`; no provider class leaks into services/controllers. | PASS |
| **IV. Quality Gate for Generated Content** | The generated definition is validated at the service boundary (non-blank, ≤20-token bound enforced) with exactly one re-roll, then a typed 503 — mirroring the snack/meaning-snack gate. The term is normalized/validated (≤10 tokens, blank rejected) before any call. | PASS |
| **V. MVP-First, Iterate / YAGNI** | Smallest viable change: one prompt, one port method, one service, one endpoint, one directive, one affordance, an additive card input. No persistence, no "Create snack" (Task 12), no provider-specific logic, no new dependency, no results/history change. | PASS |

**Re-check after Phase 1 design**: No new violations. No violations to justify → **Complexity
Tracking table is intentionally empty.**

## Project Structure

### Documentation (this feature)

```text
specs/016-custom-keyword-define/
├── plan.md              # This file
├── research.md          # Phase 0 output (endpoint shape, port method, selection strategy, card feed)
├── data-model.md        # Phase 1 output (no persistence; in-flight UI/DTO value types)
├── quickstart.md        # Phase 1 output (run + manual verification + rollback)
├── contracts/           # Phase 1 output
│   ├── definition-contract.md     # endpoint request/response + status/error shapes
│   └── definition.sample.json     # example payloads (success + error)
├── e2e-test-plan.md     # Phase 1 output (browser-level selection/trigger/generation plan)
├── checklists/          # (existing — provided with the spec)
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root) — files touched by this feature

```text
backend/
└── src/
    ├── main/
    │   ├── resources/prompts/
    │   │   └── generate-definition.md                       # [NEW] externalized prompt (FR-004/FR-005)
    │   └── java/com/endia/
    │       ├── application/
    │       │   ├── port/
    │       │   │   └── LlmProvider.java                     # [EDIT] + generateDefinition + GeneratedDefinition
    │       │   └── service/
    │       │       └── DefinitionService.java               # [NEW] normalize + validate + generate/re-roll
    │       ├── domain/exception/
    │       │   └── InvalidSelectionException.java           # [NEW] 400 VALIDATION_ERROR (blank/oversized term)
    │       ├── infrastructure/llm/
    │       │   ├── LlmPromptTemplates.java                  # [EDIT] + generateDefinition(theme, term)
    │       │   ├── OpenAiLlmProvider.java                   # [EDIT] implement + defensive parse
    │       │   └── RetryingLlmProvider.java                 # [EDIT] delegate the new port method
    │       └── interfaces/rest/
    │           ├── DefinitionController.java                # [NEW] POST /api/definitions/generate
    │           └── dto/
    │               ├── DefinitionGenerateApiRequest.java    # [NEW] themeId, term
    │               └── DefinitionGenerateApiResponse.java   # [NEW] term, definition
    └── test/java/com/endia/
        ├── application/service/DefinitionServiceTest.java             # [NEW] unit (normalize/validate/re-roll)
        ├── interfaces/rest/DefinitionContractTest.java                # [NEW] MockMvc shape/errors
        └── infrastructure/llm/
            ├── GenerateDefinitionPromptTest.java                      # [NEW] prompt assertions
            ├── OpenAiLlmProviderTest.java                             # [EDIT] parse definition response
            └── RetryingLlmProviderTest.java                           # [EDIT] StubProvider implements new method

frontend/
├── src/app/
│   ├── models/
│   │   └── definition.model.ts                              # [NEW] DefinitionGenerateResponse
│   ├── services/
│   │   └── definition.service.ts                            # [NEW] POST /api/definitions/generate
│   ├── components/
│   │   ├── selection-define/
│   │   │   ├── selection-define.directive.ts                # [NEW] selectionchange + 2s timer + term/rect output
│   │   │   ├── selection-define-affordance.component.ts     # [NEW] transient, viewport-clamped "Define" button
│   │   │   ├── selection-define-affordance.component.html   # [NEW]
│   │   │   └── selection-define-affordance.component.scss   # [NEW]
│   │   └── keyword-definition-card/
│   │       ├── keyword-definition-card.component.ts         # [EDIT] + definitionLoading/definitionError/anchorRect
│   │       └── keyword-definition-card.component.html       # [EDIT] loading/error states; escaped only
│   └── pages/snacks/
│       ├── snacks.component.ts                              # [EDIT] selection-define wiring + definition request
│       └── snacks.component.html                            # [EDIT] directive + affordance host + card bindings
└── e2e/
    ├── custom-keyword-define.spec.ts                        # [NEW] hold/trigger/generate/security/scope
    └── helpers.ts                                           # [EDIT] + setupDefinitionsMock + default mock
```

**Structure Decision**: Web-application monorepo. Backend keeps the classic layered architecture
(`domain → application → infrastructure → interfaces`); no layer is added. The new capability is a
provider-agnostic port method + service + endpoint, exactly parallel to feature 014's
`/api/meaning-snacks` but returning a single short definition rather than a full snack. Frontend
follows the Angular 21 standalone/signals convention: the selection logic is a **pure directive**
(no card/DOM-render responsibility), the affordance is a small presentational component, and the
shared definition card is extended **additively** so feature 013/014 behaviour and E2E assertions
are preserved.

### Exact integration call sites

| Surface | Component | File(s) | Change |
|---------|-----------|---------|--------|
| Snacks reading page | `SnacksComponent` | `frontend/src/app/pages/snacks/snacks.component.{ts,html}` | host `SelectionDefineDirective` + one `SelectionDefineAffordanceComponent`; own the definition request + card feed |
| Reading container | `.snack-content` | `frontend/src/app/pages/snacks/snacks.component.html` | `appSelectionDefine` directive + `(termSelected)`/`(selectionCleared)` |
| Definition card | `KeywordDefinitionCardComponent` | `frontend/src/app/components/keyword-definition-card/*` | + `definitionLoading`/`definitionError`/`anchorRect` inputs; loading/error states; escaped only |
| Results recap | `ResultsComponent` | — | **unchanged** (no trigger, no directive) |
| History view | `HistoryComponent` | — | **unchanged** (no trigger, no directive) |

`RichTextComponent`, the keyword highlighter, the meaning-snack flow and all existing DTOs are
unchanged.

## Design

### Backend

**Endpoint shape (decision)** — `POST /api/definitions/generate` under a new small
`DefinitionController` (`@RequestMapping("/api/definitions")`). Request `{ themeId, term }`:
`themeId` is **required** because the only surface that offers the trigger is the snacks reading
page, which always has `AppState.theme().themeId`, and the theme supplies the prompt context
(`theme.getPrompt()`) exactly as `MeaningSnackService` does. `subtopicId` is deliberately **not**
included: a term's definition is theme-scoped, not subtopic-scoped, and the term may be selected in
a meaning-snack card that has no quiz subtopic; adding it would be unused context (YAGNI, research
R1). A later additive field is non-breaking if ever needed.

**`LlmProvider` (application/port) [EDIT]** — add a provider-agnostic record and method:

```java
GeneratedDefinition generateDefinition(String theme, String term);

/** Provider-agnostic result of a short-definition generation (feature 016, ≤20 words). */
record GeneratedDefinition(String definition) {}
```

Adding an interface method requires updating the two main implementers (`OpenAiLlmProvider`,
`RetryingLlmProvider`) and the `RetryingLlmProviderTest.StubProvider` test double. A `default`
throwing method was rejected (research R2/R9).

**`LlmPromptTemplates` [EDIT]** — `generateDefinition(theme, term)` loads
`prompts/generate-definition.md` and replaces `{{theme}}` and `{{term}}`.

**`generate-definition.md` [NEW]** — provider-agnostic; asks for exactly
`{ "definition": "..." }` and no other fields. Rules: the definition is a **plain-text, concise
meaning of at most 20 words** (not a multi-paragraph explanation); non-empty; same language as the
theme; **no markup** (no `**bold**`, bullets, headings, links, HTML, event handlers, `javascript:`
URLs); **no quiz content** (no questions/options/answers); **no `keywords` array**. Because the
result is displayed via escaped interpolation (not the rich-text renderer), no formatting markers
are requested.

**`OpenAiLlmProvider` [EDIT]** — `generateDefinition` calls
`templates.generateDefinition(...)`, then parses `node.path("definition").asText(null)` in the same
defensive style as `generateMeaningSnack` (a missing field → `null`; a malformed overall response →
`MALFORMED` typed exception). It ignores any extra fields the model may emit.

**`DefinitionService` (application/service) [NEW]** — constructor-injected `LlmProvider` and
`JpaThemeRepository`. Constants: `MAX_TERM_TOKENS = 10`, `MAX_DEFINITION_TOKENS = 20`.

```java
public DefinitionResult generate(UUID themeId, String term);

public record DefinitionResult(String term, String definition) {}
```

`generate`:
1. resolve `Theme` (404 `NOT_FOUND` via `EntityNotFoundException` if absent);
2. normalize the term: `term.trim().replaceAll("\\s+", " ")` (trim + whitespace-collapse);
3. validate: blank → `InvalidSelectionException` (400) "Select a word or phrase to define."; more
   than `MAX_TERM_TOKENS` whitespace-separated tokens → `InvalidSelectionException` (400) "Select
   no more than 10 words to define." (hyphenated compounds count as one token; `split("\\s+")`);
   **no LLM call is made** for a rejected term (FR-010/FR-011);
4. call `llmProvider.generateDefinition(theme.getPrompt(), normalizedTerm)` through the same typed
   wrapper as `MeaningSnackService.callGenerate` (`LlmGenerationException` rethrown; any other
   exception → `ServiceUnavailableException`);
5. validate the generated definition: non-blank; on failure re-roll **once**, then
   `ServiceUnavailableException` (503) — mirrors the snack/meaning-snack gate (constitution IV);
6. normalize the accepted definition to at most `MAX_DEFINITION_TOKENS` whitespace tokens
   (`String.join(" ", Arrays.copyOf(tokens, 20))`), so the response is **always** bounded to ≤20
   words (FR-004); truncation (never reject) is consistent with feature 015's definition policy;
7. return `new DefinitionResult(normalizedTerm, normalizedDefinition)`; nothing is persisted.

**`InvalidSelectionException` (domain/exception) [NEW]** — `extends DomainException`, constructed
with `super(400, "VALIDATION_ERROR", message)`, so the existing `GlobalExceptionHandler` maps it to
the standard 400 `{error, message, field:null, cause:null}` shape with **no handler change**
(mirrors `InvalidOptionIndexException`).

**DTOs (interfaces/rest/dto) [NEW]**:
- `DefinitionGenerateApiRequest(@NotNull(message="themeId is required") UUID themeId, @NotBlank(message="term must not be blank") String term)`.
- `DefinitionGenerateApiResponse(String term, String definition)`.

**`DefinitionController` (interfaces/rest) [NEW]** — `@RequestMapping("/api/definitions")`:
- `POST /generate` → 200 `DefinitionGenerateApiResponse`.

Errors use the existing `GlobalExceptionHandler` shapes: 400 `VALIDATION_ERROR` (missing/blank term,
or >10 tokens), 404 `NOT_FOUND` (unknown theme), 503 `SERVICE_UNAVAILABLE` (LLM unavailable, or the
generated definition still invalid after one re-roll).

**No migration / schema change (explicit)** — no entity, column, index or table is added or altered;
Hibernate `ddl-auto=update` issues no DDL. The definition is transient (research R7, data-model.md
§4).

### Frontend

**`definition.model.ts` [NEW]**:

```ts
export interface DefinitionGenerateResponse {
  term: string;
  definition: string;
}
```

**`DefinitionService` (`services/definition.service.ts` [NEW])**:

```ts
generate(themeId: string, term: string): Observable<DefinitionGenerateResponse>;
// POST /api/definitions/generate { themeId, term }
```

**`SelectionDefineDirective` (`components/selection-define/selection-define.directive.ts` [NEW])** —
standalone, `OnPush`, applied to the reading container:

```ts
@Directive({ selector: '[appSelectionDefine]', standalone: true })
export class SelectionDefineDirective {
  readonly termSelected = output<{ term: string; rect: DOMRect }>();
  readonly selectionCleared = output<void>();
}
```

Behavior:
- `@HostListener('document:selectionchange')` (plus `@HostListener('pointerdown')` and
  `@HostListener('keyup')` as belt-and-braces) → read `document.getSelection()`;
- compute the selected text via `selection.toString()` (plain text — **never** `innerHTML`) and the
  range via `selection.getRangeAt(0)`;
- accept only a **non-collapsed** selection whose range is contained inside the host element
  (`host.contains(range.commonAncestorContainer)`); a selection outside the reading content yields
  no trigger (FR-017/edge case);
- on any change/clear: `clearTimeout`, hide the current affordance (`selectionCleared`), and if the
  new selection is valid start a fresh `setTimeout(2000)` (a changed/cleared selection therefore
  cancels the pending timer — FR-002);
- when the timer fires: normalize the text (`trim` + collapse whitespace) and emit
  `termSelected({ term, rect: range.getBoundingClientRect() })` (FR-001);
- on `ngOnDestroy`: clear the timer and the listeners.
- Works identically for pointer drags and programmatic/keyboard selections because both fire
  `selectionchange` (FR-016).

**`SelectionDefineAffordanceComponent`
(`components/selection-define/selection-define-affordance.component.{ts,html,scss}` [NEW])** —
standalone, `OnPush`, presentational:

```ts
readonly rect = input<DOMRect | null>(null);
readonly visible = input<boolean>(false);
readonly loading = input<boolean>(false);
readonly defineRequested = output<void>();
readonly dismissed = output<void>();
```

- renders a single clearly-labelled `Define` button (`type="button"`, `aria-label="Define selected
  text"`), positioned `fixed` from `rect`, **viewport-clamped** (never overflows horizontally; flips
  above the selection when it would cross the viewport bottom) — FR-003;
- disabled/spinner while `loading`; the label is static text (no user/model content rendered);
- `@HostListener('document:keydown.escape')` → `dismissed`;
- no `[innerHTML]`, no `DomSanitizer`.

**`KeywordDefinitionCardComponent` [EDIT]** — additive inputs (feature 013/014 contract preserved):

```ts
readonly definitionLoading = input<boolean>(false);
readonly definitionError = input<string | null>(null);
readonly anchorRect = input<DOMRect | null>(null); // used for positioning when `anchor` is null
```

Template changes (all escaped interpolation only):
- `@if (definitionLoading())` → spinner + `Defining…` in place of `.keyword-card__definition`;
- `@else if (kw.definition)` → the existing `.keyword-card__definition` (unchanged for keyword cards,
  whose definitions are always non-empty);
- `@if (definitionError(); as err)` → `.keyword-card__error` `role="alert"` (an additional slot; the
  feature-014 `generateError` slot is untouched).
- `updatePosition()` prefers `anchor()`; when it is null it uses `anchorRect()`; when both are null
  the card keeps its default (sheet on narrow screens).
- Feature-013 open/dismiss/focus behaviour and the feature-014 action row are unchanged.

**`SnacksComponent` [EDIT]** — wiring:
- imports `SelectionDefineDirective`, `SelectionDefineAffordanceComponent`, `DefinitionService`;
- new signals: `selectionAffordance = signal<{ term: string; rect: DOMRect } | null>(null)`,
  `defining = signal(false)`, `definitionLoading = signal(false)`, `definitionError = signal<string | null>(null)`,
  `customDefinition = signal(false)`, `activeKeywordRect = signal<DOMRect | null>(null)`;
- `onTermSelected({ term, rect })`: normalize + locally validate (blank or >10 tokens → set the
  affordance error path / show a meaningful error and return without a request); otherwise show the
  affordance;
- `onDefineRequested()`: guard `if (this.defining()) return;` (at most one request — FR-007);
  `defining.set(true)`; close the affordance; open the existing card for the selected term with
  `definitionLoading=true` (`activeKeyword = { term, definition: '' }`,
  `activeKeywordRect = rect`, `customDefinition = true`); call
  `definitionService.generate(themeId, term)`; on success set
  `activeKeyword = { term, definition: res.definition }` and `definitionLoading=false`; on failure
  `definitionLoading=false` and `definitionError = errorService.getMessage(err)` (nothing
  empty/placeholder added — FR-009); clear `defining` in both branches;
- `generateEnabled` for the card becomes `!customDefinition() && currentEntry()?.kind === 'regular'`,
  so the custom-define card offers **no** "Generate snack" action (Task 12 out of scope);
- `closeKeyword()` also clears `definitionLoading`, `definitionError`, `customDefinition`,
  `activeKeywordRect`; `goTo(...)` (navigation) clears the affordance too;
- `onSelectionCleared()` hides the affordance.

**`snacks.component.html` [EDIT]**:
- add `appSelectionDefine` + `(termSelected)="onTermSelected($event)"` +
  `(selectionCleared)="onSelectionCleared()"` to the `.snack-content` wrapper;
- host exactly one `<app-selection-define-affordance>` at page level (mirroring the single card),
  bound to `rect`/`visible`/`loading`, `(defineRequested)="onDefineRequested()"`,
  `(dismissed)="onSelectionCleared()"`;
- extend the existing `<app-keyword-definition-card>` bindings with
  `[definitionLoading]="definitionLoading()"`, `[definitionError]="definitionError()"`,
  `[anchorRect]="activeKeywordRect()"`.

**`ResultsComponent` / `HistoryComponent`** — **unchanged**: no directive, no affordance, no
definition request. `RichTextComponent` and the keyword highlighter are untouched.

## Milestones / Phases

| # | Milestone | Scope | FRs | Depends on |
|---|-----------|-------|-----|------------|
| **M1** | Prompt + port + providers | `generate-definition.md`, `LlmPromptTemplates.generateDefinition`, `LlmProvider.GeneratedDefinition` + `generateDefinition`, `OpenAiLlmProvider` parse, `RetryingLlmProvider` + test stub, prompt/provider/retry tests | FR-004, FR-005 | — |
| **M2** | Service + endpoint | `InvalidSelectionException`, `DefinitionService` (normalize ≤10, validate, re-roll once, truncate ≤20, typed 503), 2 DTOs, `DefinitionController`, unit + MockMvc contract tests | FR-004, FR-005, FR-010, FR-011, FR-012 | M1 |
| **M3** | Selection + affordance | `definition.model.ts`, `DefinitionService`, `SelectionDefineDirective`, `SelectionDefineAffordanceComponent`, card additive inputs, snacks-page wiring | FR-001, FR-002, FR-003, FR-016 | — (parallel with M1/M2; request wiring needs the M2 contract) |
| **M4** | Generation UX + scope + security | loading state, single-in-flight guard, error path, escaped-only card feed, results/history untouched, keyword-click + single-card rule preserved | FR-006, FR-007, FR-008, FR-009, FR-013, FR-014, FR-015, FR-017 | M3 (and M2 contract) |
| **M5** | E2E + regression | `custom-keyword-define.spec.ts`, `helpers.ts` definitions mock, full existing backend + Playwright suites unchanged | FR-018, SC-001..SC-007 | M2, M4 |

Rough effort ordering: M1 and M3 are independent and can proceed in parallel; M2 needs M1; M4 needs
M3 plus the M2 contract (it can be built against the frozen contract while M2 lands); M5 closes the
loop. Surface area is moderate: 1 prompt + 1 port method + 1 service + 1 exception + 2 DTOs + 1
controller, and on the frontend 1 model + 1 service + 1 directive + 1 affordance component + 1 card
extension + the snacks-page wiring.

## Requirement Traceability

| Spec item | Plan item(s) |
|-----------|--------------|
| FR-001 | `SelectionDefineDirective` 2s timer + `termSelected`; affordance (M3); E2E hold test |
| FR-002 | `selectionchange` cancels/resets the timer on change/clear (M3); E2E early-change/clear |
| FR-003 | `SelectionDefineAffordanceComponent` transient, selection-anchored, viewport-clamped; single host (M3) |
| FR-004 | `generate-definition.md` + `LlmProvider.generateDefinition` + `DefinitionService` (≤20-token bound) (M1/M2) |
| FR-005 | Externalized prompt + port-only service; no provider logic in service/controller (M1/M2; constitution III) |
| FR-006 | `definitionLoading` card state + spinner (M4); E2E loading assertion |
| FR-007 | `defining` guard in `onDefineRequested` + disabled affordance (M4); E2E rapid double-activation = one request |
| FR-008 | On success `activeKeyword.definition` set → existing card shows the meaning (M4) |
| FR-009 | On failure `definitionError` inline `role="alert"`, no empty/placeholder definition paragraph (M4); E2E forced failure |
| FR-010 | `DefinitionService` trim + whitespace-collapse + ≤10-token bound (M2); unit tests |
| FR-011 | Blank/oversized → `InvalidSelectionException` 400, no LLM call (M2); unit + contract tests |
| FR-012 | `split("\\s+")` tokenization; hyphenated = one token (M2; research R3) |
| FR-013 | Card escaped interpolation only; directive uses `Selection.toString()`; no `[innerHTML]`/`DomSanitizer` (M3/M4); E2E security |
| FR-014 | Injection payloads inert in the card (M4); E2E payload tests |
| FR-015 | Feature-012/013/014/015 render model preserved; card extension additive (M4/M5) |
| FR-016 | `document.getSelection()`-based detection works for pointer + programmatic (M3); E2E both paths |
| FR-017 | Directive applied only on the snacks reading page; results/history untouched (M4/M5); E2E scope test |
| FR-018 | Existing keyword click + single-card rule + meaning-snack flow + full suites unchanged (M5) |
| FR-019 | Task 12 explicitly not built (M3/M4 scope; Non-Goals) |
| SC-001..SC-007 | See Testing Strategy + quickstart verification checklist |

## Testing Strategy

**Backend**
- *Prompt unit test* (`GenerateDefinitionPromptTest`, new): the rendered `generate-definition.md`
  substitutes `{{theme}}`/`{{term}}` (no placeholders remain), asks for exactly the
  `{ "definition": ... }` JSON shape, states the **≤20-word** bound, requires plain text (no bold
  markers/bullets/headings/links/HTML), forbids quiz content and a `keywords` array.
- *Provider unit test* (`OpenAiLlmProviderTest`, extended): a well-formed `{definition:"..."}`
  response parses into `GeneratedDefinition`; a missing `definition` yields `null`; a malformed
  overall response throws `MALFORMED`; extra fields are ignored.
- *Retry decorator* (`RetryingLlmProviderTest`, extended): `StubProvider` implements the new method;
  a retryable `generateDefinition` failure retries and rethrows the typed cause.
- *Service unit test* (`DefinitionServiceTest`, new, Mockito): unknown theme → `EntityNotFoundException`
  (404); blank term → `InvalidSelectionException` (400) and the provider is **never** called; a
  10-token term is accepted; an 11-token term → 400 and the provider is **never** called; multiple
  spaces/tabs/newlines collapse to single spaces; a valid generation returns a definition of ≤20
  tokens; a >20-token generated definition is truncated to exactly 20; a blank generated definition
  re-rolls exactly once then throws `ServiceUnavailableException`; a provider exception →
  `ServiceUnavailableException`; the provider is called with `theme.getPrompt()` and the normalized
  term.
- *Contract test* (`DefinitionContractTest`, new, standalone MockMvc mirroring
  `MeaningSnackContractTest`): `POST /api/definitions/generate` returns 200 `{term, definition}`
  with exactly two fields; a blank term → 400 `VALIDATION_ERROR` `field=term`; a >10-token term →
  400 `VALIDATION_ERROR`; an unknown theme → 404 `NOT_FOUND`; an LLM failure → 503
  `SERVICE_UNAVAILABLE` with `cause`.
- *Existing suites*: `SnackServiceTest`, `MeaningSnackServiceTest`, `SnackMarkupRoundTripTest`,
  `MeaningSnackContractTest`, `ThemeHistoryContractTest`, `RetryingLlmProviderTest` (existing cases)
  and the Testcontainers integration tests must pass unchanged (the feature adds no field to any
  existing DTO and touches no entity).

**Frontend (Playwright E2E — the only runner in the repo)**
- *`custom-keyword-define.spec.ts`* (new):
  - **Hold trigger (US1 / FR-001..FR-003, SC-001)**: select a term inside the reading content and
    hold ~2s → the `Define` affordance appears; assert it does **not** appear before ~2s.
  - **Cancellation (FR-002, SC-001)**: change/clear the selection before ~2s → no affordance; a
    subsequent held selection starts a fresh timer.
  - **Generate + loading (US2 / FR-006..FR-008, SC-002)**: activate `Define` with a delayed
    `POST /api/definitions/generate` mock → a loading state, then the card shows the generated
    definition for the selected term.
  - **Bounds (FR-010/FR-011, SC-005)**: a blank/whitespace-only selection and an 11-token selection
    show a meaningful error and issue **no** request (assert the mock was not called).
  - **Duplicate guard (FR-007, SC-003)**: rapid double-activation issues one request.
  - **Failure (FR-009, SC-002)**: a 503/error mock shows a meaningful error and adds no empty/
    placeholder content.
  - **Pointer + programmatic (US4 / FR-016, SC-006)**: the affordance appears after a mouse-drag
    selection and after a programmatic `document.getSelection()` range.
  - **Non-interference (US4 / FR-003/FR-018, SC-006)**: clicking an existing highlighted keyword
    still opens the existing card and does not trigger the selection flow; at most one card is shown.
  - **Scope (FR-017, SC-006)**: selecting text on the results recap and on the read-only history
    surface offers no `Define` affordance and those surfaces render unchanged.
  - **Security (US3 / FR-013/FR-014/FR-015, SC-004)**: injection payloads (`<script>`, `onerror=`,
    `javascript:` URLs, `<iframe>`, `<style>`, inline styles) in the selected term and the generated
    definition execute nothing and inject no nodes; readable text is preserved.
- *`helpers.ts`* (extended): add `setupDefinitionsMock(page, { generate? })`; include a benign
  default `POST /api/definitions/generate` in `setupAllMocks` so existing specs remain deterministic
  (no existing spec activates the trigger, so no request is expected; the default is a safety net).
- *Regression*: run the full existing suite (`happy-path`, `progressive-delivery`,
  `snack-wait-messages`, `results-*`, `snacks-navigation`, `snack-keywords`,
  `snack-rich-formatting`, `meaning-snacks`, `theme-history`, …) unchanged (FR-018, SC-007).

**Frontend unit-test gap (unchanged from 013/014/015)**: the repo has no unit-test runner; the pure
selection/timer logic is covered through route-mocked E2E fixtures. If true unit coverage is later
wanted, Vitest as a dev-only dependency is the minimal option (not assumed here).

## Risks / Watch Items

| Risk | Mitigation |
|------|------------|
| **Selection handling / timer races** are the highest-risk area: `selectionchange` fires frequently, and a stale timer could show an affordance for a selection that changed. | One debounce timer, cleared on every `selectionchange`; a fresh timer only for a valid non-collapsed in-content selection; the emitted term/rect are read at fire time; `ngOnDestroy` clears the timer; E2E covers early-change/clear. |
| The new selection handling could break existing highlighted-keyword clicks or the single-card rule. | The directive never intercepts clicks (only `selectionchange`); keyword buttons keep their own `(click)`; both the affordance and the card are single page-level hosts; `RichTextComponent` is untouched; E2E asserts keyword click + one-card. |
| The directive on `.snack-content` could offer the trigger outside reading content. | The range must be contained in the host element; results/history never include the directive; E2E scope test. |
| Adding an interface method breaks `LlmProvider` implementers and the test stub. | Update `OpenAiLlmProvider`, `RetryingLlmProvider` and `RetryingLlmProviderTest.StubProvider` in the same task (M1). |
| The card extension could regress feature 013/014 E2E assertions. | The new inputs default to no-op (`definitionLoading=false`, `definitionError=null`, `anchorRect=null`); the definition paragraph still renders for non-empty keyword definitions; feature-014 `generateError`/action row untouched; full existing suite run in M5. |
| A blank/oversized term could reach the backend. | Frontend validates before requesting (and shows a meaningful error); the backend independently validates and returns 400 without calling the LLM (defence-in-depth). |
| The generated definition could contain markup. | The card renders `definition` via escaped interpolation only; the prompt forbids markup; E2E injection tests. |
| Scope creep into Task 12 ("Create snack" from the custom meaning). | `generateEnabled=false` for the custom card; Task 12 is an explicit Non-Goal (FR-019). |

## Open Decisions for the Orchestrator

1. **Endpoint requires `themeId` and omits `subtopicId`.** Chosen because the trigger is offered only
   on the snacks reading page (which always has the theme) and a term's definition is theme-scoped,
   not subtopic-scoped; `subtopicId` would be unused context. Adding it later is a non-breaking
   additive field. Confirm.
2. **Generated definition is truncated to ≤20 tokens at the service boundary** (rather than rejected
   and re-rolled for length), consistent with feature 015's definition policy; only a **blank**
   generated definition triggers the single re-roll. Confirm.
3. **The custom-define card disables the feature-014 "Generate snack" action** (`generateEnabled=false`)
   because Task 12 is out of scope; the generated meaning is display-only. Confirm.
4. **The affordance appears for any non-collapsed in-content selection held ~2s, and validation
   (blank/>10 tokens) happens on activation**, showing a meaningful error without a request. If the
   orchestrator prefers the affordance to be withheld for oversized selections, the same directive
   can pre-filter on token count (small change). Confirm.

## Complexity Tracking

> No Constitution Check violations. This section is intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
