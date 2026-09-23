# Implementation Plan: Colored Keywords with Click-to-Show Short Definition Cards

**Branch**: `013-snack-keywords` | **Date**: 2026-09-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-snack-keywords/spec.md`

## Summary

End-to-end feature across prompt, persistence/API, and every snack-displaying surface. It extends the
snack JSON contract with a `keywords` array (`[{ term, definition }]`, definition ≤ 3 words) and adds a
color highlight + click/tap definition card on top of feature 012's sanitized renderer, preserving the
feature-012 security model (typed AST, no `[innerHTML]`, no `DomSanitizer`).

1. **Prompt / generation (FR-001..FR-005)**: extend `backend/src/main/resources/prompts/generate-snack.md`
   to request a `keywords` array of `{ term, definition }` items (2–5 as a guideline, definitions ≤ 3
   words, terms verbatim in the body text, plain text only), while keeping the existing
   `concept/metaphor/whyItMatters/related` shape and feature-012 formatting rules unchanged.
   `OpenAiLlmProvider.generateSnack` parses the array defensively.
2. **Normalization (FR-007..FR-010)**: `SnackService` trims/drops invalid keywords, truncates
   over-limit definitions to the first 3 whitespace tokens, de-duplicates terms, and never lets
   keywords affect validity or trigger a re-roll.
3. **Persistence (FR-011..FR-012)**: one **additive, nullable JSON column** `snack.keywords`, mapped
   with a new `AttributeConverter<List<SnackKeyword>, String>` that follows the existing
   `JpaJsonObjectConverter` pattern. Applied via Hibernate `ddl-auto=update` (the project's only
   schema mechanism); existing rows load with an empty list.
4. **API round-trip (FR-006, FR-011)**: `keywords` added to all four snack-returning DTOs and their
   controller mappings (fresh generation, batch status, results recap, history).
5. **Rendering (FR-013..FR-029)**: feature-012's typed AST gains a `keyword` inline node and a
   recursive `bold` node; a pure highlight pass matches terms case-insensitively, whole-word, literal,
   longest-wins/non-overlapping, all occurrences, scoped to `concept`/`metaphor`/`whyItMatters`. A new
   standalone `KeywordDefinitionCardComponent` is hosted once per page and opened via a
   `keywordSelected` output, so only one card is ever open (FR-021). All keyword text flows through
   Angular interpolation (escaped), never HTML binding.

The feature adds **no new endpoint, no new layer, no new runtime dependency, and no layout redesign**.

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.4.x (backend); TypeScript 5.9.x + Angular 21.2.x
(frontend, standalone components, signals-first, `ChangeDetectionStrategy.OnPush`).

**Primary Dependencies**: Spring Web / Spring Data JPA / Hibernate / Jackson (backend); Angular core,
Router, signals, RxJS (frontend). **No new runtime dependency in either project** (research.md R6).

**Storage**: MySQL 8.x. One additive, nullable `JSON` column on `snack`. The project has **no
Flyway/Liquibase**; schema is managed by Hibernate `spring.jpa.hibernate.ddl-auto=update`
(`backend/src/main/resources/application.yml`). See "Migration approach" below.

**Testing**:
- Backend: JUnit 5 + Mockito unit tests; MockMvc contract tests; Testcontainers MySQL integration
  tests (tagged `integration`, run via `mvn verify -Pintegration`, excluded from default surefire).
- Frontend: Playwright E2E only — `frontend/package.json` has **no** Karma/Jest/Vitest runner wired
  (`ng test` has no runner dependency), consistent with feature 012 research R5. Pure parser/highlighter
  behavior is covered through route-mocked E2E fixtures; `npm run build` type-checks. (See Testing
  Strategy for the unit-test gap and the open question.)

**Target Platform**: Modern desktop + mobile browsers (Chrome/Firefox/Safari last 2 versions); Linux
server.

**Project Type**: Web-application monorepo (`backend/` + `frontend/`).

**Performance Goals**: No measurable change. Keyword highlighting is a single linear pass over the
already-parsed short text (a few hundred characters), once per field under `computed()`. No network or
storage change. Keyword lists are bounded by the prompt (2–5) and validated at the service boundary.

**Constraints**:
- Additive nullable schema only; no data loss; existing rows must remain valid (FR-012, SC-006).
- Backend is the normalization boundary; frontend renders whatever valid keywords it receives
  (FR-007, FR-008).
- Feature-012 security model preserved unchanged: no `[innerHTML]`, no `DomSanitizer`, render-time
  allowlist (FR-029, SC-007).
- Existing snack/quiz/results/progressive-delivery/history E2E suites must pass unchanged except where
  the new `keywords` field is added to fixtures (FR-030, SC-009).
- Out of scope: definition-card "Generate snack" action and "meaning snack" entry (FR-031, Non-Goals).

**Scale/Scope**: 1 prompt file, 1 new domain record + 1 converter, 1 entity extension, 1 port record
extension, 1 service normalization, 4 DTOs + 4 controller mappings + 1 service record; frontend: 4 model
edits, 1 parser extension + 1 new highlighter, 1 new standalone card component, 3 page integrations,
tests.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment | Verdict |
|-----------|------------|---------|
| **I. Spec-Driven Development** | `spec.md` exists with 31 FRs and 9 SCs and is the source of truth; this `plan.md` and downstream `tasks.md` follow. | PASS |
| **II. Clean Architecture (Layered)** | New `SnackKeyword` is a pure domain record; normalization lives in `application/service` (`SnackService`); the new JSON converter lives in `infrastructure/persistence`; DTO/controller changes stay in `interfaces/rest`. Port record extension in `application/port`. Dependency direction preserved. Frontend adds a reusable standalone component + a parser/AST extension, signals-first, OnPush, no service coupling. | PASS |
| **III. LLM Abstraction First** | Prompt stays externalized in `generate-snack.md`; `LlmProvider` remains the only seam and its record gains a provider-agnostic `List<SnackKeyword>`; no provider class leaks into services/controllers. | PASS |
| **IV. Quality Gate for Generated Content** | Core snack validation (non-empty `concept/metaphor/whyItMatters`, `related` non-empty ≤ 3, single re-roll) is **unchanged**; keywords are explicitly excluded from validity so a keyword slip never re-rolls the snack (FR-005/FR-008). | PASS |
| **V. MVP-First, Iterate / YAGNI** | Smallest viable change: prompt wording + one nullable column + one AST node + one small card component. No Markdown/HTML dialect, no keyword editor, no retro-generation, no new dependency, no new endpoint. | PASS |

**Re-check after Phase 1 design**: No new violations. No violations to justify → **Complexity
Tracking table is intentionally empty.**

## Project Structure

### Documentation (this feature)

```text
specs/013-snack-keywords/
├── plan.md              # This file
├── research.md          # Phase 0 output (converter, migration, matching, card decisions)
├── data-model.md        # Phase 1 output (SnackKeyword, Snack extension, DTO shapes, migration)
├── quickstart.md        # Phase 1 output (run + manual verification)
├── contracts/           # Phase 1 output
│   ├── snack-keywords-contract.md    # API response shape + matching/highlight + card contracts
│   └── snack-keywords.sample.json    # example payload on every snack-returning surface
├── checklists/
│   ├── requirements.md  # (existing — extended, not overwritten)
│   ├── security.md      # [NEW] keyword sanitization requirements checklist
│   └── ux.md            # [NEW] highlight + definition-card UX requirements checklist
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root) — files touched by this feature

```text
backend/
└── src/
    ├── main/
    │   ├── resources/prompts/
    │   │   └── generate-snack.md                         # [EDIT] keywords instructions (FR-001..FR-005)
    │   └── java/com/endia/
    │       ├── domain/model/
    │       │   ├── Snack.java                            # [EDIT] + keywords field + 6-arg ctor
    │       │   └── SnackKeyword.java                     # [NEW] record(term, definition)
    │       ├── infrastructure/persistence/
    │       │   └── JpaSnackKeywordListConverter.java     # [NEW] AttributeConverter<List<SnackKeyword>, String>
    │       ├── application/
    │       │   ├── port/LlmProvider.java                 # [EDIT] GeneratedSnack + keywords + 4-arg overload
    │       │   └── service/
    │       │       ├── SnackService.java                 # [EDIT] normalizeKeywords + build Snack
    │       │       └── ThemeService.java                 # [EDIT] SnackHistory + keywords
    │       ├── infrastructure/llm/
    │       │   └── OpenAiLlmProvider.java                # [EDIT] parse keywords array
    │       └── interfaces/rest/
    │           ├── dto/SnackKeywordDto.java              # [NEW] record(term, definition) + from()
    │           ├── dto/SnackGenerateApiResponse.java     # [EDIT] + keywords
    │           ├── dto/BatchStatusApiResponse.java       # [EDIT] SnackDto + keywords
    │           ├── dto/QuizSummaryApiResponse.java       # [EDIT] SnackDto + keywords
    │           ├── dto/ThemeHistoryApiResponse.java      # [EDIT] SnackItem + keywords
    │           ├── SnackController.java                  # [EDIT] pass keywords
    │           ├── BatchController.java                  # [EDIT] pass keywords
    │           ├── QuizController.java                   # [EDIT] pass keywords
    │           └── ThemeController.java                  # [EDIT] pass keywords
    └── test/java/com/endia/
        ├── infrastructure/llm/
        │   ├── GenerateSnackPromptTest.java              # [EDIT] keywords prompt assertions
        │   └── OpenAiLlmProviderTest.java                # [EDIT] keyword parsing/robustness
        ├── application/service/SnackServiceTest.java     # [EDIT] normalization tests
        ├── interfaces/rest/SnackMarkupRoundTripTest.java # [EDIT] keywords on all 4 surfaces + shape counts
        └── application/integration/
            └── SnackKeywordsPersistenceIntegrationTest.java # [NEW] nullable JSON column round trip

frontend/
├── src/app/
│   ├── models/
│   │   ├── snack.model.ts                              # [EDIT] SnackKeyword + keywords
│   │   ├── batch.model.ts                              # [EDIT] inline snack + keywords
│   │   ├── quiz.model.ts                               # [EDIT] Recap.snack + keywords
│   │   └── history.model.ts                            # [EDIT] HistorySnack + keywords
│   ├── components/
│   │   ├── rich-text/
│   │   │   ├── rich-text.model.ts                      # [EDIT] + keyword node, recursive bold
│   │   │   ├── rich-text.parser.ts                     # [EDIT] parseRichText(text, keywords?)
│   │   │   ├── keyword-highlighter.ts                  # [NEW] pure highlight pass
│   │   │   ├── rich-text.component.ts                  # [EDIT] keywords input + keywordSelected output
│   │   │   ├── rich-text.component.html                # [EDIT] render keyword buttons, recursive bold
│   │   │   └── rich-text.component.scss                # [EDIT] keyword color token
│   │   └── keyword-definition-card/
│   │       ├── keyword-definition-card.component.ts    # [NEW] standalone, OnPush
│   │       ├── keyword-definition-card.component.html  # [NEW] role=dialog, close control
│   │       └── keyword-definition-card.component.scss  # [NEW] responsive anchor/sheet
│   └── pages/
│       ├── snacks/snacks.component.{ts,html}           # [EDIT] pass keywords, host card
│       ├── results/results.component.{ts,html}         # [EDIT] pass keywords, host card
│       └── history/history.component.{ts,html}         # [EDIT] pass keywords, host card
└── e2e/
    ├── snack-keywords.spec.ts                          # [NEW] highlight + card + XSS + responsive
    └── theme-history.spec.ts                           # [EDIT] keyword rendering in history
```

**Structure Decision**: Web-application monorepo. Backend keeps the classic layered architecture
(`domain → application → infrastructure → interfaces`); no layer is added. Frontend follows the Angular
21 standalone/signals convention: the shared renderer is extended in place, and the definition card is
a new standalone component under `frontend/src/app/components/` (alongside `drag-drop-question/` and
`rich-text/`). The highlighter is a pure function separate from the component so its matching rules are
deterministic and independently reasoned about.

### Exact call sites that must pass keywords to the renderer

| Surface | Component | File(s) | Current markup |
|---------|-----------|---------|----------------|
| **Snack card** | `SnacksComponent` | `frontend/src/app/pages/snacks/snacks.component.html` (lines 82, 85, 88) + `.ts` | `<app-rich-text class="snack-card__concept" [text]="snack.concept" />` |
| **Results recap** | `ResultsComponent` | `frontend/src/app/pages/results/results.component.html` (lines 84, 85) + `.ts` | `<app-rich-text class="recap-card__concept" [text]="snack.concept" />` |
| **History view** | `HistoryComponent` | `frontend/src/app/pages/history/history.component.html` (lines 62, 63, 64) + `.ts` | `<app-rich-text class="history-snack__concept" [text]="snack.concept" />` |

`related` rendering is **unchanged** and is never a highlight source (FR-014).

## Design

### Backend

**`SnackKeyword` (domain)** — `public record SnackKeyword(String term, String definition)`. Pure value
object, no framework annotations, Jackson-serializable (needed by the JSON converter). The compact
constructor normalizes `null` to `""` but performs **no** truncation/dropping (that is service policy).

**`JpaSnackKeywordListConverter` (infrastructure)** — `AttributeConverter<List<SnackKeyword>, String>`.
`convertToDatabaseColumn`: `null`/empty → `"[]"`; otherwise Jackson `writeValueAsString`.
`convertToEntityAttribute`: `null`/blank → `List.of()`; else
`readValue(dbData, new TypeReference<List<SnackKeyword>>(){})`. This mirrors `JpaJsonObjectConverter`
exactly, but is a **new class** because JPA `AttributeConverter`s are statically typed per attribute
(see research.md R1 for why the existing converter cannot be literally reused).

**`Snack` (domain)** — add:
```java
@Convert(converter = JpaSnackKeywordListConverter.class)
@Column(nullable = true, columnDefinition = "JSON")
private List<SnackKeyword> keywords = new ArrayList<>();
```
Keep the existing 5-arg constructor (delegating with `List.of()`) for non-regression, and add a 6-arg
constructor `(Subtopic, concept, metaphor, whyItMatters, related, keywords)` used by `SnackService`.

**`LlmProvider.GeneratedSnack` (application/port)** — add `List<SnackKeyword> keywords` as the fifth
component and an overloaded 4-arg constructor delegating with `List.of()`, so existing tests/callers
compile unchanged.

**`OpenAiLlmProvider.generateSnack` (infrastructure)** — after parsing `related`, parse
`node.path("keywords")`: if array, for each element that `isObject()`, read `term`/`definition` via
`asText(null)`; skip non-object elements. Missing/absent → empty list. Parsing keywords **never**
throws by itself; a malformed keyword element is skipped, not fatal (only a malformed overall response
throws, preserving current behavior).

**`SnackService` (application)** — `isValid` is unchanged (keywords not consulted). Add a pure
normalization step invoked in `generateValidSnack` before constructing the `Snack`:
1. drop `null` keywords and any whose trimmed `term` or trimmed `definition` is blank;
2. trim both values;
3. truncate the definition to the first 3 whitespace-separated tokens (hyphenated compounds count as
   one token; `split("\\s+")`);
4. drop a keyword whose definition becomes empty after truncation;
5. de-duplicate by trimmed term, case-insensitively, keeping the first occurrence;
6. preserve input order.
The result may be empty; the snack is still accepted (no re-roll, no failure).

**DTOs / controllers** — `SnackKeywordDto(String term, String definition)` in `interfaces/rest/dto`
with `static SnackKeywordDto from(SnackKeyword)`. Add `List<SnackKeywordDto> keywords` as the last
component of `SnackGenerateApiResponse.SnackItem`, `BatchStatusApiResponse.SnackDto`,
`QuizSummaryApiResponse.SnackDto`, `ThemeHistoryApiResponse.SnackItem`, and add `keywords` to
`ThemeService.SnackHistory`. Map with `s.getKeywords().stream().map(SnackKeywordDto::from).toList()`.
`BatchService.BatchItemStatus` carries the raw `Snack` entity and needs **no** change.

**Migration approach (explicit)** — the repository uses **no migration tool**: schema is owned by
Hibernate `ddl-auto=update`. The new column is declared nullable with `columnDefinition = "JSON"`, so
on the next backend start Hibernate issues (MySQL 8):
```sql
alter table snack add column keywords JSON null;
```
Pre-existing rows receive `NULL`; the converter maps `NULL` → empty list, so old snacks load and
serialize as `"keywords": []` (FR-012, SC-006). The change is additive and non-destructive; no data is
rewritten. Authoritative manual DDL (for environments where `ddl-auto` is disabled) and rollback are
documented in data-model.md §5 and quickstart.md §6. Integration tests run with
`spring.jpa.hibernate.ddl-auto=create-drop` (Testcontainers), which exercises the same mapping.

### Frontend

**AST extension (`rich-text.model.ts`)**:
```ts
export interface SnackKeyword { term: string; definition: string; }

export type RichInline =
  | { kind: 'text'; text: string }
  | { kind: 'bold'; nodes: RichInline[] }            // was { kind:'bold'; text:string }
  | { kind: 'break' }
  | { kind: 'keyword'; text: string; term: string; definition: string };
```
Making `bold` recursive lets a matched term inside bold render as `<strong><button …>…</button></strong>`
(US2 AS4) while keeping every output element inside the same tiny vocabulary. With no keywords, a bold
node is `{ kind:'bold', nodes:[{kind:'text',…}] }` and renders exactly as before, so feature-012 E2E
assertions (`strong` text/count) stay green.

**Highlight pass (`keyword-highlighter.ts`, pure)** — `highlightKeywords(blocks, keywords): RichBlock[]`.
For each block's inline nodes:
- `text` node → split into `text`/`keyword` nodes by scanning all terms;
- `bold` node → recurse into its child nodes (keyword inside bold);
- `break`/`keyword` → unchanged.
Matching rules (FR-014..FR-017): case-insensitive; **literal** (terms are escaped / matched by index
scan, never compiled as patterns, so `C++`, `(x)`, `a.b` are safe); **whole-word** (a match is valid
only when the character before and after are not word characters — with terms that themselves start/end
with non-word characters exempted at that edge); **all occurrences**; **longest-term-wins** by sorting
terms by length descending and consuming matches left-to-right with a non-overlapping cursor; matching
is confined to a single contiguous inline text run and never crosses a block/bold boundary.
`related` is never passed, so it is never highlighted.

**Parser (`rich-text.parser.ts`)** — `parseRichText(text, keywords?)` performs the existing base parse
and, when `keywords` is non-empty, returns `highlightKeywords(baseBlocks, keywords)`. The base grammar
and entity decoding are unchanged.

**`RichTextComponent`** — add `readonly keywords = input<SnackKeyword[]>([])` and
`readonly keywordSelected = output<{ keyword: SnackKeyword; anchor: HTMLElement }>()`. `blocks` becomes
`computed(() => parseRichText(this.text(), this.keywords()))`. A keyword node renders as
`<button type="button" class="rich-text__keyword" (click)="onKeywordClick($event, node)">` — a native
button gives pointer + keyboard activation for free. The component holds **no** card state.

**`KeywordDefinitionCardComponent` (new, standalone)** — inputs `keyword: SnackKeyword | null` and
`anchor: HTMLElement | null`; output `closed`. Behavior contract (contracts/snack-keywords-contract.md
§3): shows only `keyword.definition`; dismisses on outside pointerdown, a visible close control, and
Escape; moves focus into the card on open and returns it to the anchor button on close; positions
relative to the anchor with viewport clamping, and falls back to a bottom-sheet layout on narrow
screens (no horizontal overflow). No `@angular/cdk`; no new dependency.

**Page integration** — each of `SnacksComponent`, `ResultsComponent`, `HistoryComponent` holds a single
`activeKeyword`/`anchor` signal pair, renders one `<app-keyword-definition-card>` at page level, and
passes `[keywords]="snack.keywords ?? []"` + `(keywordSelected)="openKeyword($event)"` to every
`<app-rich-text>`. One card per page guarantees FR-021 (opening a new term closes the previous card)
and FR-025 (identical behavior on all surfaces). `related` is untouched.

## Milestones / Phases

| # | Milestone | Scope | FRs | Depends on |
|---|-----------|-------|-----|------------|
| **M1** | Prompt emits keywords | Extend `generate-snack.md`; keep JSON shape + 012 rules | FR-001..FR-005 | — |
| **M2** | Domain + persistence | `SnackKeyword`, `JpaSnackKeywordListConverter`, `Snack.keywords`, nullable JSON column | FR-011, FR-012 | — |
| **M3** | Parse + normalize + contract | `GeneratedSnack.keywords`, provider parsing, `SnackService` normalization, 4 DTOs + controller mappings, `ThemeService.SnackHistory` | FR-006..FR-010 | M1, M2 |
| **M4** | Highlight renderer | AST extension, `keyword-highlighter.ts`, parser + component, keyword color | FR-013..FR-017, FR-026..FR-029 | — (parallel with M1–M3) |
| **M5** | Definition card + surfaces | `KeywordDefinitionCardComponent`, page integration on snack card / recap / history, responsive + keyboard | FR-018..FR-025 | M4 |
| **M6** | Security + regression | XSS/injection E2E, empty/legacy/no-match fixtures, update 012 round-trip shape counts, full existing E2E suite | FR-030, SC-005..SC-009 | M3, M5 |

Rough effort ordering: M1, M2, and M4 are independent and can proceed in parallel; M3 needs M1+M2; M5
needs M4; M6 closes the loop. Surface area is moderate: 1 prompt file, 1 new record + 1 converter + 1
entity edit, 1 port record, 1 service normalization, 4 DTOs + 4 controllers, and on the frontend 1 AST
extension + 1 highlighter + 1 card component + 3 page integrations.

## Requirement Traceability

| Spec item | Plan item(s) |
|-----------|--------------|
| FR-001..FR-005 | M1 prompt; `GenerateSnackPromptTest`; provider defensive parse |
| FR-006 | `SnackKeywordDto` + 4 DTOs + 4 controller mappings; `SnackKeywordsRoundTrip` contract tests |
| FR-007, FR-008, FR-009 | `SnackService.normalizeKeywords`; `SnackServiceTest`; `SnackKeywordsPersistenceIntegrationTest` |
| FR-010 | Render-time escaping via Angular interpolation; XSS E2E; no HTML binding anywhere |
| FR-011, FR-012 | `Snack.keywords` nullable JSON + converter; integration test for null/legacy rows |
| FR-013..FR-017 | `keyword-highlighter.ts` + AST `keyword` node + `rich-text.component.scss` token; highlight E2E |
| FR-018..FR-023 | `KeywordDefinitionCardComponent` + page-level single-card wiring; card E2E (desktop+mobile, keyboard) |
| FR-024 | Empty/absent keywords → `?? []`, no nodes emitted; no-artifact E2E |
| FR-025 | Same component + same card wiring on all three surfaces; cross-surface E2E |
| FR-026..FR-029 | Typed AST only, no `[innerHTML]`/`DomSanitizer`; keyword text escaped by interpolation; XSS E2E |
| FR-030 | Existing fields/validation untouched; 5-arg/4-arg constructor overloads; existing E2E suite |
| FR-031 | No card "Generate snack", no backbone entry, no generation-flow change (explicit Non-Goal) |
| SC-001..SC-009 | See Testing Strategy + quickstart verification checklist |

## Testing Strategy

**Backend**
- *Prompt unit test* (`GenerateSnackPromptTest`, extended): the rendered `generate-snack.md` asks for a
  `keywords` array, mentions `term`/`definition`, states the ≤ 3-word limit and the 2–5 guideline,
  requires terms to appear in the body text, forbids markup in keyword text, and still specifies the
  exact existing `concept/metaphor/whyItMatters/related` shape (FR-001..FR-005).
- *Provider unit test* (`OpenAiLlmProviderTest`, extended): a response with a well-formed `keywords`
  array parses into `SnackKeyword`s; a missing array yields an empty list; non-object elements and
  missing fields are skipped without throwing (FR-006).
- *Service unit test* (`SnackServiceTest`, extended): over-limit definition truncated to 3 words; blank
  term/definition dropped; keyword whose definition becomes blank after truncation dropped; duplicate
  terms de-duplicated case-insensitively; an all-invalid keyword list yields an empty list with **no**
  re-roll (provider called once); an invalid core field still re-rolls exactly once (FR-005, FR-007,
  FR-008, FR-009).
- *Contract test* (`SnackMarkupRoundTripTest`, extended): `keywords` is present with the same items on
  fresh generation, batch status, results recap, and history; the shape is `[{term,definition}]`; values
  are not stripped or HTML-escaped; the object key counts become 8/8/8/6 (previously 7/7/7/5) —
  **these existing assertions must be updated** as part of the contract extension (FR-006, FR-011).
- *Integration test* (`SnackKeywordsPersistenceIntegrationTest`, new, Testcontainers, tag
  `integration`): a snack saved with keywords reloads with identical `term`/`definition`; a snack saved
  with `null`/empty keywords reloads as an empty list (legacy row simulation); the JSON column round-trips
  (FR-011, FR-012, SC-008).

**Frontend (Playwright E2E — the only runner in the repo)**
- *`snack-keywords.spec.ts`* (new):
  - **Highlight (FR-013..FR-017, SC-002)**: a term appearing in the body is rendered with the
    highlight class in a color distinct from body text; every occurrence is highlighted; a keyword whose
    term is absent produces no artifact; matching is case-insensitive; overlapping terms highlight only
    the longer one with no nested/overlapping spans; a term inside `**bold**` renders both `<strong>` and
    the keyword button; keyword-like characters (`C++`, `(x)`, `a.b`) match literally.
  - **Graceful degradation (FR-024, SC-006)**: an empty/absent `keywords` renders identically to today
    with no highlight, no card affordance, and no layout artifact.
  - **Definition card (FR-018..FR-023, SC-003..SC-005)**: click opens a card showing only the ≤ 3-word
    definition; outside-click, the close control, and Escape each dismiss it without navigation;
    activating a different term closes the previous card and opens the new one; keyboard focus + Enter
    opens and Escape returns focus; repeat at a desktop and a mobile viewport with no horizontal
    overflow.
  - **Security (FR-026..FR-029, SC-007)**: keyword `term`/`definition` payloads
    (`<script>`, `<img onerror=…>`, `javascript:` URLs, `<iframe>`, `<style>`, inline styles) execute
    nothing and inject no nodes; readable text is preserved; the card's definition text is escaped.
  - **Cross-surface (FR-025, SC-009)**: the same snack renders highlight + card consistently on the
    snack card and the results recap.
- *`theme-history.spec.ts`* (extended): a mocked `GET /api/themes` with keywords renders highlights and
  opens the definition card in the expanded history detail (FR-025).
- *Regression*: run the existing suites (`happy-path`, `progressive-delivery`, `snack-wait-messages`,
  `results-*`, `snacks-navigation`, `theme-history`, `snack-rich-formatting`, …) after adding
  `keywords: []` to their snack fixtures where needed (FR-030, SC-009).

**Frontend unit-test gap (flagged)**: the repo has no unit-test runner; feature 012 deliberately did not
add one (research R5). This plan follows that precedent and covers the pure highlighter/parser through
route-mocked E2E fixtures. If the orchestrator wants true unit coverage of `keyword-highlighter.ts`,
the minimal addition would be Vitest (dev-only) with no runtime dependency; this is recorded as an open
question rather than assumed.

## Complexity Tracking

> No Constitution Check violations. This section is intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
