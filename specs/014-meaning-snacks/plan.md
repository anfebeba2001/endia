# Implementation Plan: "Generate snack" from a Keyword Definition into the Snack Backbone (No Quiz)

**Branch**: `014-meaning-snacks` | **Date**: 2026-09-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/014-meaning-snacks/spec.md`

## Summary

End-to-end feature that turns a keyword's `term` + ≤3-word `definition` (feature 013) into a
persisted **meaning snack** and adds it to the **snack reading surface** on `/snacks` (the confirmed
"main topics backbone"), visually distinct and **without any quiz**. It adds a **"Generate snack"**
action to the existing keyword definition card and implements the behaviour feature 013 explicitly
deferred (013 FR-031).

1. **Generation action (FR-001..FR-006, FR-025)**: the existing standalone
   `KeywordDefinitionCardComponent` gains a "Generate snack" button, a loading state, an
   already-generated state, and an inline error state, while keeping feature 013's open/dismiss/focus
   behaviour unchanged. It emits a `generateRequested` output; the hosting page owns the request.
2. **Meaning-snack generation (FR-002, FR-007, FR-008)**: a new externalized prompt
   `generate-meaning-snack.md` (placeholders `{{theme}}`, `{{term}}`, `{{definition}}`) drives a new
   `LlmProvider.generateMeaningSnack(...)` port method; `OpenAiLlmProvider` parses the same
   `concept/metaphor/whyItMatters/related` JSON shape (no keywords), and a new
   `MeaningSnackService` validates (non-empty core, `related` 1..3, one re-roll), normalizes the
   source definition to ≤3 tokens, and persists.
3. **Distinct entity + persistence (FR-017, FR-019, Clarification #3)**: a new `MeaningSnack` JPA
   entity mapped to a **new additive table `meaning_snack`** (theme FK, source subtopic FK, source
   term/definition, generated content, `kind = MEANING`, `created_at`), reusing the existing
   `JpaJsonConverter` for `related`. Applied by Hibernate `ddl-auto=update`; existing tables, rows and
   columns are untouched.
4. **Retrieval path (FR-018, FR-020, Clarification #2)**: a new theme-scoped listing
   `GET /api/meaning-snacks?themeId=<uuid>` read by the snacks page on load, so persisted meaning
   snacks reappear after a reload/new session for the same theme. Failure degrades to an empty list.
5. **Backbone integration (FR-010..FR-013, FR-015)**: the snacks page keeps its regular `BatchItem[]`
   flow and gains a separate `meaningSnacks` signal; a pure `entries` computed interleaves meaning
   entries **after their source subtopic's** regular entry (unmatched meaning entries appended at the
   end), keyed by a stable `entryId`. Meaning entries render a distinct card variant (colour + badge)
   and **never** render "Start Quiz".
6. **No quiz, structurally (FR-014..FR-016)**: a meaning snack is a different table/kind, is never
   attached to a `StudySessionItem`, is never passed to `QuizService`, and is excluded from batch
   status, quiz generation/prefetch, results recap, XP/accuracy and history.
7. **Security (FR-021..FR-023)**: all meaning-snack and keyword text is rendered through the existing
   feature-012 `RichTextComponent` (typed AST, no `[innerHTML]`, no `DomSanitizer`); the card uses
   escaped interpolation only. The security model is unchanged.

The feature adds **one new endpoint group (`/api/meaning-snacks`), one new table, one new prompt, one
new domain entity, one new port method, and no new runtime dependency**. The existing snack/batch/
quiz flow is not modified.

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.4.x (backend); TypeScript 5.9.x + Angular 21.2.x
(frontend, standalone components, signals-first, `ChangeDetectionStrategy.OnPush`).

**Primary Dependencies**: Spring Web / Spring Data JPA / Hibernate / Jackson (backend); Angular core,
Router, signals, RxJS (frontend). **No new runtime dependency in either project** (research.md R9).

**Storage**: MySQL 8.x. One **new additive table** `meaning_snack`; no existing column is added,
renamed or rewritten. The project has **no Flyway/Liquibase**; schema is managed by Hibernate
`spring.jpa.hibernate.ddl-auto=update` (`backend/src/main/resources/application.yml`). See "Migration
approach" below.

**Testing**:
- Backend: JUnit 5 + Mockito unit tests; MockMvc contract tests; Testcontainers MySQL integration
  tests (tagged `integration`, run via `mvn verify -Pintegration`, excluded from default surefire).
- Frontend: Playwright E2E only — `frontend/package.json` has **no** Karma/Jest/Vitest runner wired,
  consistent with features 012/013. Pure interleave/normalisation logic is covered through
  route-mocked E2E fixtures; `npm run build` type-checks.

**Target Platform**: Modern desktop + mobile browsers (Chrome/Firefox/Safari last 2 versions); Linux
server.

**Project Type**: Web-application monorepo (`backend/` + `frontend/`).

**Performance Goals**: No measurable change. The listing is one indexed query per theme load; the
interleave is a single linear pass over a bounded list; generation is one LLM call (plus at most one
re-roll) triggered explicitly by a user click.

**Constraints**:
- Additive/backward-compatible schema only; no existing data invalidated (FR-019, SC-007).
- Backend is the normalization/validation boundary; the frontend renders whatever valid content it
  receives through the safe allowlist (FR-021..FR-023).
- LLM-Abstraction principle: the meaning-snack prompt stays externalized; the service depends only on
  `LlmProvider`, never on a provider class (constitution III).
- No quiz, at any layer, for a meaning snack (FR-014..FR-016, SC-005).
- Existing snack/quiz/results/progressive-delivery/history E2E suites must pass unchanged (FR-024,
  SC-009).
- Out of scope: changing regular snack generation, batch semantics, quiz content/scoring, or the
  history data shape; user editing of meaning snacks; retro-generation (spec Non-Goals).

**Scale/Scope**: 1 new prompt, 1 new entity + 1 enum + 1 repository, 1 new port method (+ provider
parse, + retry decorator, + test stub), 1 new service, 4 new DTOs, 1 new controller with 2 endpoints;
frontend: 1 new model file, 1 new service, 1 card edit (3 files), the snacks backbone refactor
(3 files), 2 secondary page wirings (results/history), 1 global token edit, E2E helper + 1 new spec.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment | Verdict |
|-----------|------------|---------|
| **I. Spec-Driven Development** | `spec.md` exists with 26 FRs and 9 SCs and is the source of truth; this `plan.md` and downstream `tasks.md` follow. | PASS |
| **II. Clean Architecture (Layered)** | New `MeaningSnack` entity + `SnackKind` enum are pure domain; orchestration/validation/normalisation live in `application/service` (`MeaningSnackService`); the repository stays in `infrastructure/persistence`; DTOs and the new controller stay in `interfaces/rest`. The service depends only on the `LlmProvider` port. Dependency direction (domain → application → infrastructure → interfaces) preserved. Frontend follows standalone/signals/OnPush; the card gains inputs/outputs, the backbone gains a pure computed interleave. | PASS |
| **III. LLM Abstraction First** | New prompt externalized in `generate-meaning-snack.md`; the only seam is a new provider-agnostic `LlmProvider.generateMeaningSnack(...)` record method; no provider class leaks into services/controllers. | PASS |
| **IV. Quality Gate for Generated Content** | Meaning-snack content is validated at the service boundary (non-empty `concept/metaphor/whyItMatters`, `related` non-empty ≤ 3) with exactly one re-roll, mirroring the snack gate. A meaning snack carries **no quiz**, so the quiz quality gate is untouched. | PASS |
| **V. MVP-First, Iterate / YAGNI** | Smallest viable change: one new entity/table, one new prompt, two endpoints, one card button, one backbone variant. No kind flag on `Snack`, no meaning-snack editing, no retro-generation, no new dependency, no history data-shape change. | PASS |

**Re-check after Phase 1 design**: No new violations. No violations to justify → **Complexity
Tracking table is intentionally empty.**

## Project Structure

### Documentation (this feature)

```text
specs/014-meaning-snacks/
├── plan.md              # This file
├── research.md          # Phase 0 output (entity vs flag, retrieval, ordering, dedupe, no-quiz, card)
├── data-model.md        # Phase 1 output (MeaningSnack, table, DTOs, validation, migration)
├── quickstart.md        # Phase 1 output (run + manual verification + rollback)
├── contracts/           # Phase 1 output
│   ├── meaning-snack-contract.md     # generate + list + backbone consumption contract
│   └── meaning-snack.sample.json     # example payloads (generate, list, mixed backbone)
├── checklists/
│   └── requirements.md  # (existing — provided with the spec)
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root) — files touched by this feature

```text
backend/
└── src/
    ├── main/
    │   ├── resources/prompts/
    │   │   └── generate-meaning-snack.md                     # [NEW] externalized prompt (FR-002/007/008)
    │   └── java/com/endia/
    │       ├── domain/
    │       │   ├── enums/
    │       │   │   └── SnackKind.java                        # [NEW] enum { MEANING } — kind marker
    │       │   └── model/
    │       │       └── MeaningSnack.java                     # [NEW] entity (theme FK, subtopic FK, source, content)
    │       ├── application/
    │       │   ├── port/
    │       │   │   └── LlmProvider.java                      # [EDIT] + generateMeaningSnack + GeneratedMeaningSnack
    │       │   └── service/
    │       │       └── MeaningSnackService.java              # [NEW] generate (idempotent) + list by theme
    │       ├── infrastructure/
    │       │   ├── llm/
    │       │   │   ├── LlmPromptTemplates.java               # [EDIT] + generateMeaningSnack(theme, term, definition)
    │       │   │   ├── OpenAiLlmProvider.java                # [EDIT] implement + defensive parse
    │       │   │   └── RetryingLlmProvider.java              # [EDIT] delegate the new port method
    │       │   └── persistence/
    │       │       └── JpaMeaningSnackRepository.java        # [NEW] findByThemeId..., findByIdempotencyKey
    │       └── interfaces/rest/
    │           ├── MeaningSnackController.java               # [NEW] POST /generate, GET ?themeId=
    │           └── dto/
    │               ├── MeaningSnackGenerateApiRequest.java   # [NEW] themeId, subtopicId, term, definition
    │               ├── MeaningSnackGenerateApiResponse.java  # [NEW] alreadyExisted + MeaningSnackItem
    │               ├── MeaningSnackListApiResponse.java      # [NEW] themeId + MeaningSnackItem[]
    │               └── MeaningSnackItem.java                 # [NEW] shared item (kind, source, content, no quiz)
    └── test/java/com/endia/
        ├── application/service/MeaningSnackServiceTest.java              # [NEW] unit
        ├── application/integration/MeaningSnackPersistenceIntegrationTest.java # [NEW] Testcontainers
        ├── interfaces/rest/MeaningSnackContractTest.java                 # [NEW] MockMvc shape/errors
        └── infrastructure/llm/
            ├── GenerateMeaningSnackPromptTest.java                       # [NEW] prompt assertions
            ├── OpenAiLlmProviderTest.java                                # [EDIT] parse meaning snack
            └── RetryingLlmProviderTest.java                              # [EDIT] StubProvider implements new method

frontend/
├── src/
│   ├── styles.scss                                           # [EDIT] meaning colour tokens (light + dark)
│   └── app/
│       ├── models/
│       │   ├── meaning-snack.model.ts                        # [NEW] MeaningSnack + request/response types
│       │   └── batch.model.ts                                # [EDIT] BackboneEntry union + entryId
│       ├── services/
│       │   ├── meaning-snack.service.ts                      # [NEW] generate + listByTheme + generatedKeys
│       │   └── app-state.service.ts                          # [EDIT] + meaningSnacks cache signal
│       ├── components/keyword-definition-card/
│       │   ├── keyword-definition-card.component.ts          # [EDIT] + inputs/output for generation
│       │   ├── keyword-definition-card.component.html        # [EDIT] + action button/loading/error/done
│       │   └── keyword-definition-card.component.scss        # [EDIT] action styles
│       └── pages/
│           ├── snacks/snacks.component.ts                    # [EDIT] meaningSnacks + entries interleave
│           ├── snacks/snacks.component.html                  # [EDIT] index badge + meaning card variant
│           ├── snacks/snacks.component.scss                  # [EDIT] meaning card/index variant
│           ├── results/results.component.{ts,html}           # [EDIT] wire card generation (recap subtopic)
│           └── history/history.component.{ts,html}           # [EDIT] wire card generation (theme + subtopic)
└── e2e/
    ├── meaning-snacks.spec.ts                                # [NEW] generate/distinct/no-quiz/persist/XSS
    └── helpers.ts                                            # [EDIT] + meaning-snack mocks in setupAllMocks
```

**Structure Decision**: Web-application monorepo. Backend keeps the classic layered architecture
(`domain → application → infrastructure → interfaces`); no layer is added. The meaning snack is a
**new aggregate** rather than a flag on `Snack`, so it gets its own entity, repository, service,
DTOs and controller group — this is what makes the "no quiz" invariant structural rather than
conditional (research.md R1/R5). Frontend follows the Angular 21 standalone/signals convention: the
shared card is extended in place, and the snacks backbone gains a pure `entries` computed that
interleaves regular and meaning entries without altering the regular batch semantics.

### Exact integration call sites

| Surface | Component | File(s) | Change |
|---------|-----------|---------|--------|
| Snack backbone | `SnacksComponent` | `frontend/src/app/pages/snacks/snacks.component.{ts,html,scss}` | regular `BatchItem[]` → `batchItems`; new `meaningSnacks`; `entries` interleave; meaning card variant; no quiz |
| Definition card | `KeywordDefinitionCardComponent` | `frontend/src/app/components/keyword-definition-card/*` | + "Generate snack" button, loading/error/done, `generateRequested` output |
| Results recap | `ResultsComponent` | `frontend/src/app/pages/results/results.component.{ts,html}` | host card action using `recap.subtopicId` + `AppState.theme().themeId` |
| History view | `HistoryComponent` | `frontend/src/app/pages/history/history.component.{ts,html}` | host card action using `theme.themeId` + `subtopic.id` |

`related` rendering and all feature-012/013 rendering paths are unchanged.

## Design

### Backend

**`SnackKind` (domain/enums)** — `public enum SnackKind { MEANING }`. The explicit kind marker
persisted on the entity and carried in the API so no surface can mistake a meaning snack for a
regular one. (A single value today; the enum keeps the discriminator typed and future-proof.)

**`MeaningSnack` (domain/model)** — new `@Entity @Table(name = "meaning_snack")`:

```java
@Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
@ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "theme_id", nullable = false) private Theme theme;
@ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "source_subtopic_id", nullable = false) private Subtopic sourceSubtopic;
@Column(name = "source_term", nullable = false, length = 500) private String sourceTerm;
@Column(name = "source_definition", nullable = false, length = 200) private String sourceDefinition;
@Column(nullable = false, columnDefinition = "TEXT") private String concept;
@Column(nullable = false, columnDefinition = "TEXT") private String metaphor;
@Column(name = "why_it_matters", nullable = false, columnDefinition = "TEXT") private String whyItMatters;
@Convert(converter = JpaJsonConverter.class) @Column(nullable = false, columnDefinition = "JSON") private List<String> related;
@Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private SnackKind kind = SnackKind.MEANING;
@Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
```

`Theme` and `Subtopic` are **not** modified (no back-reference collection): the relationship is
one-directional `@ManyToOne`, so existing entities and their fetch/cascade behaviour are untouched
(FR-024/FR-026). `@PrePersist` sets `createdAt`. `related` reuses `JpaJsonConverter` — the same
`AttributeConverter<List<String>, String>` already used by `Snack.related`; **no new converter is
needed** (data-model.md §3). A unique index `ux_meaning_snack_source (source_subtopic_id, source_term)`
plus indexes `idx_meaning_snack_theme (theme_id)` and `idx_meaning_snack_subtopic
(source_subtopic_id)` are declared on `@Table`.

**`JpaMeaningSnackRepository` (infrastructure/persistence)**:

```java
List<MeaningSnack> findByThemeIdOrderByCreatedAtAsc(UUID themeId);
Optional<MeaningSnack> findBySourceSubtopicIdAndSourceTermIgnoreCase(UUID subtopicId, String term);
```

The `IgnoreCase` derived query is the primary idempotency guard (case-insensitive term match);
the unique index is defence-in-depth.

**`LlmProvider` (application/port) [EDIT]** — add a provider-agnostic record and method:

```java
GeneratedMeaningSnack generateMeaningSnack(String theme, String term, String definition);

record GeneratedMeaningSnack(String concept, String metaphor, String whyItMatters, List<String> related) {}
```

Adding an interface method requires updating the two main implementers (`OpenAiLlmProvider`,
`RetryingLlmProvider`) and the `RetryingLlmProviderTest.StubProvider` test double. No other caller is
affected. (A `default` throwing method was rejected as poor design — see research.md R9.)

**`LlmPromptTemplates` [EDIT]** — `generateMeaningSnack(theme, term, definition)` loads
`prompts/generate-meaning-snack.md` and replaces `{{theme}}`, `{{term}}`, `{{definition}}`.

**`generate-meaning-snack.md` [NEW]** — provider-agnostic, asks for exactly the existing snack JSON
shape `{concept, metaphor, whyItMatters, related}` (no `keywords`), expanded from the supplied term
and ≤3-word definition; keeps the feature-012 formatting rules (bold, short paragraphs, `- ` bullets)
and the forbidden-markup list. It explicitly instructs the model **not** to produce quiz questions,
options, answers, or a `keywords` array.

**`OpenAiLlmProvider` [EDIT]** — `generateMeaningSnack` calls `templates.generateMeaningSnack(...)`,
parses `concept/metaphor/whyItMatters` and the `related` array with the same defensive style as
`generateSnack` (missing fields → `null`/empty; a malformed overall response → `MALFORMED` typed
exception). It ignores any `keywords` the model may emit.

**`MeaningSnackService` (application/service) [NEW]** — constructor-injected `LlmProvider`,
`JpaThemeRepository`, `JpaSubtopicRepository`, `JpaMeaningSnackRepository`.

```java
@Transactional
public GenerateResult generate(UUID themeId, UUID subtopicId, String term, String definition);
// GenerateResult(MeaningSnack snack, boolean alreadyExisted)

@Transactional(readOnly = true)
public List<MeaningSnack> listByTheme(UUID themeId);
```

`generate`:
1. resolve `Theme` (404 if absent) and `Subtopic` (404 if absent);
2. normalize: trim `term`; trim `definition` and truncate to the first 3 whitespace tokens
   (mirrors `SnackService.normalizeKeywords` step 4); a blank normalized term/definition →
   `ServiceUnavailableException`/validation error (the controller also bean-validates `@NotBlank`);
3. **idempotency**: `findBySourceSubtopicIdAndSourceTermIgnoreCase(...)` → if present, return it with
   `alreadyExisted = true` **without calling the LLM** (FR-006, no silent duplicate);
4. else call `llmProvider.generateMeaningSnack(theme.getPrompt(), term, definition)` through the same
   typed-error wrapper used by `SnackService` (`LlmGenerationException` rethrown; any other exception →
   `ServiceUnavailableException`); validate (non-empty `concept/metaphor/whyItMatters`, `related`
   non-empty ≤ 3) and re-roll **once**; a second failure → `ServiceUnavailableException`;
5. persist a `MeaningSnack` bound to the resolved theme and source subtopic, `kind = MEANING`;
6. **never** touch `QuizService`, `BatchService`, `StudySessionItem`, `QuizQuestion` or the phase
   registry. No quiz is generated, prefetched or stored (FR-014).

`listByTheme` resolves the theme (404 if absent) and returns
`findByThemeIdOrderByCreatedAtAsc(themeId)` (empty list is valid).

**DTOs (interfaces/rest/dto) [NEW]**:
- `MeaningSnackGenerateApiRequest(@NotNull UUID themeId, @NotNull UUID subtopicId, @NotBlank String term, @NotBlank String definition)`.
- `MeaningSnackItem(UUID id, String kind, UUID themeId, UUID sourceSubtopicId, String sourceSubtopicName,
  String sourceTerm, String sourceDefinition, String concept, String metaphor, String whyItMatters,
  List<String> related, LocalDateTime createdAt)` with `static MeaningSnackItem from(MeaningSnack, String subtopicName)`.
  **Deliberately no `subtopicId` quiz target, no `quizStatus`, no `quizQuestions`, no `keywords`.**
- `MeaningSnackGenerateApiResponse(boolean alreadyExisted, MeaningSnackItem meaningSnack)`.
- `MeaningSnackListApiResponse(UUID themeId, List<MeaningSnackItem> meaningSnacks)`.

**`MeaningSnackController` (interfaces/rest) [NEW]** — `@RequestMapping("/api/meaning-snacks")`:
- `POST /generate` → 200 `MeaningSnackGenerateApiResponse` (idempotent).
- `GET ?themeId=<uuid>` → 200 `MeaningSnackListApiResponse`; empty list when the theme has none.

Errors use the existing `GlobalExceptionHandler` shapes: 400 `VALIDATION_ERROR` (missing/blank
fields), 404 `NOT_FOUND` (theme/subtopic), 503 `SERVICE_UNAVAILABLE` (LLM/validation after re-roll).

**Migration approach (explicit)** — the repository uses **no migration tool**: schema is owned by
Hibernate `ddl-auto=update`. On the next backend start Hibernate creates the new table (MySQL 8):

```sql
create table meaning_snack (
  id binary(16) not null,
  theme_id binary(16) not null,
  source_subtopic_id binary(16) not null,
  source_term varchar(500) not null,
  source_definition varchar(200) not null,
  concept TEXT not null,
  metaphor TEXT not null,
  why_it_matters TEXT not null,
  related JSON not null,
  kind varchar(20) not null,
  created_at datetime(6) not null,
  primary key (id),
  constraint ux_meaning_snack_source unique (source_subtopic_id, source_term)
);
create index idx_meaning_snack_theme on meaning_snack (theme_id);
create index idx_meaning_snack_subtopic on meaning_snack (source_subtopic_id);
```

No existing table, column, index or row is altered. Authoritative manual DDL and rollback are in
data-model.md §5 / quickstart.md §6. Integration tests run with
`spring.jpa.hibernate.ddl-auto=create-drop` (Testcontainers).

### Frontend

**Models (`meaning-snack.model.ts` [NEW])**:

```ts
export type MeaningSnackKind = 'MEANING';
export interface MeaningSnack {
  id: string; kind: MeaningSnackKind; themeId: string;
  sourceSubtopicId: string; sourceSubtopicName: string;
  sourceTerm: string; sourceDefinition: string;
  concept: string; metaphor: string; whyItMatters: string;
  related: string[]; createdAt: string;
}
export interface MeaningSnackGenerateResponse { alreadyExisted: boolean; meaningSnack: MeaningSnack; }
export interface MeaningSnackListResponse { themeId: string; meaningSnacks: MeaningSnack[]; }
```

**`batch.model.ts` [EDIT]** — add the backbone union so the index/navigation can hold both kinds:

```ts
export interface RegularEntry {
  entryId: string; kind: 'regular'; subtopicId: string; subtopicName: string;
  snackStatus: GenerationStatus; snackPhase?: SnackGenerationPhase | null;
  snack: Snack | null; snackCause: LlmErrorCause | null;
  quizStatus: GenerationStatus; quizCause: LlmErrorCause | null;
  quizQuestions?: QuizQuestion[] | null;
}
export interface MeaningEntry {
  entryId: string; kind: 'meaning'; sourceSubtopicId: string; meaning: MeaningSnack;
}
export type BackboneEntry = RegularEntry | MeaningEntry;
```

`entryId` is `subtopicId` for a regular entry and `meaning:<id>` for a meaning entry, so a subtopic
that has both a regular and a meaning snack yields two distinct, stable keys (no `track` collision).

**`MeaningSnackService` (`services/meaning-snack.service.ts` [NEW])** — HTTP + shared generated-key
state:

```ts
generate(themeId, subtopicId, term, definition): Observable<MeaningSnackGenerateResponse>;
listByTheme(themeId): Observable<MeaningSnackListResponse>;
readonly generatedKeys = signal<ReadonlySet<string>>(new Set()); // `${subtopicId}::${term.toLowerCase()}`
hasGenerated(subtopicId, term): boolean;   // card "already generated" state
markGenerated(snack: MeaningSnack): void;  // after generate or after listing
```

`AppStateService` [EDIT] gains `meaningSnacks = signal<MeaningSnack[]>([])` (reset in `reset()`), used
as an in-memory cache so a return visit can render meaning entries before/while the listing resolves.

**`KeywordDefinitionCardComponent` [EDIT]** — new inputs/outputs, feature-013 behaviour untouched:

```ts
readonly generateEnabled = input<boolean>(true);
readonly generateState = input<'idle' | 'loading' | 'done'>('idle');
readonly generateError = input<string | null>(null);
readonly generateRequested = output<SnackKeyword>();
```

The template adds an action row (below the definition) with a `Generate snack` button, a spinner +
`Generating…` label while `generateState === 'loading'` (button disabled/guarded, FR-003/FR-005), a
disabled `Added to your snacks ✓` state when `'done'` (FR-006), and an inline `role="alert"` error
when `generateError` is set (FR-004). Outside-pointerdown/Escape/close/focus-return logic is
unchanged (FR-025). All keyword text remains escaped interpolation (FR-021..FR-023).

**`SnacksComponent` [EDIT]** — the backbone refactor (the riskiest change):

- rename the current `items` signal to `batchItems` (regular `BatchItem[]`), keep every existing
  batch/poll/retry/progressive-delivery code path byte-for-byte;
- add `meaningSnacks = signal<MeaningSnack[]>([])` and a `generationState` map signal;
- add a **pure** interleave:

```ts
readonly entries = computed<BackboneEntry[]>(() => {
  const regular = this.batchItems();
  const meanings = this.meaningSnacks();
  const placed = new Set<string>();
  const out: BackboneEntry[] = [];
  for (const item of regular) {
    out.push(toRegularEntry(item));
    for (const m of meanings.filter((m) => m.sourceSubtopicId === item.subtopicId)) {
      out.push(toMeaningEntry(m)); placed.add(m.id);
    }
  }
  for (const m of meanings.filter((m) => !placed.has(m.id))) out.push(toMeaningEntry(m));
  return out;
});
```

- `currentIndex` clamps against `entries().length`; `currentEntry = entries()[currentIndex]`;
  `hasPrev`/`hasNext` use `entries`; `canNext` is `hasNext() && (next.kind === 'meaning' ||
  next.snackStatus === 'READY')` (meaning entries are always readable);
- `showEmptyState` requires `entries().length === 0`;
- `selectIndex(i)` allows a meaning entry unconditionally and a regular entry only when `READY`;
- `startQuiz()` early-returns unless `currentEntry.kind === 'regular'` (FR-015);
- `init()` always fetches `meaningSnackService.listByTheme(themeId)` in **both** the return path and
  the fresh-batch path; on error it sets `[]` and continues (FR-020, SC-006); results are cached in
  `AppStateService.meaningSnacks` and each item marks `generatedKeys`;
- `syncAppStateSnacks()` continues to store **only** regular snacks in `appState.snacks()` (return
  path unchanged);
- `onGenerateRequested(keyword)`: resolve the active entry's `sourceSubtopicId` (regular entry's
  `subtopicId`), set `generateState[key] = 'loading'`, call
  `meaningSnackService.generate(themeId, subtopicId, keyword.term, keyword.definition)`; on success
  append the item to `meaningSnacks` (dedupe by id) and set `'done'`; on error set the card error via
  `ErrorService` and return to `'idle'`. At most one in-flight request per keyword (FR-005).

**`snacks.component.html` [EDIT]**:
- index list keys `entry.entryId`; a meaning entry shows its source term (or `Meaning: <term>`) with a
  `snack-index__item--meaning` class/badge;
- branch the card: `entry.kind === 'regular'` renders the existing card unchanged; `entry.kind ===
  'meaning'` renders `<article class="snack-card snack-card--meaning">` with a `Meaning snack` badge,
  the source term/definition, and the concept/metaphor/why/related content via `<app-rich-text>`
  (**no keywords passed** — meaning content has no highlight terms), and **no** `.quiz-btn`;
- the definition card bindings add `[generateEnabled]`, `[generateState]`, `[generateError]`,
  `(generateRequested)="onGenerateRequested($event)"`.

**`snacks.component.scss` / `styles.scss` [EDIT]** — add `--meaning-accent`, `--meaning-accent-soft`,
`--meaning-ink` tokens for light and dark, and `.snack-card--meaning` / `.snack-index__item--meaning`
/ `.snack-card__badge--meaning` rules. The distinction is not colour-only (a text badge is always
present) to satisfy WCAG 1.4.1 (SC-004).

**`ResultsComponent` / `HistoryComponent` [EDIT]** — wire the same card action with the context each
page already has (`recap.subtopicId` + `AppState.theme()?.themeId`; `theme.themeId` + `subtopic.id`)
through the shared `MeaningSnackService`, with the same loading/error/done state. The generated entry
appears in the `/snacks` backbone (the confirmed surface); the card shows an inline confirmation.

## Milestones / Phases

| # | Milestone | Scope | FRs | Depends on |
|---|-----------|-------|-----|------------|
| **M1** | Domain + persistence | `SnackKind`, `MeaningSnack`, `JpaMeaningSnackRepository`, additive `meaning_snack` table, idempotency query, Testcontainers round-trip | FR-017, FR-019, FR-008 | — |
| **M2** | Prompt + provider | `generate-meaning-snack.md`, `LlmPromptTemplates`, `LlmProvider.GeneratedMeaningSnack`, `OpenAiLlmProvider` parse, `RetryingLlmProvider` + test stub, prompt/provider tests | FR-002, FR-007, FR-008 | — |
| **M3** | Service + API | `MeaningSnackService` (normalize, idempotent generate, validate/re-roll, list), 4 DTOs, `MeaningSnackController`, unit + MockMvc contract tests, no-quiz guarantee | FR-001, FR-004, FR-005, FR-006, FR-014, FR-015, FR-016, FR-018, FR-020 | M1, M2 |
| **M4** | Frontend model + card action | `meaning-snack.model.ts`, `MeaningSnackService`, card button/loading/error/done, wire on snacks/results/history | FR-001, FR-003, FR-004, FR-005, FR-006, FR-025 | M3 (contract), M2 |
| **M5** | Backbone integration + distinct rendering | `BackboneEntry`, `entries` interleave, meaning card variant + badge/colour, index badge, no-quiz affordance, reload fetch | FR-010..FR-013, FR-015, FR-018, FR-020 | M4 |
| **M6** | Security + regression + E2E | XSS/injection, graceful degradation, duplicate/repeat, persistence reload E2E, helper mocks, full existing suite | FR-021..FR-024, SC-001..SC-009 | M3, M5 |

Rough effort ordering: M1 and M2 are independent and can run in parallel; M3 needs both; M4 needs the
M3 contract (it can be built against the frozen contract while M3 lands); M5 needs M4; M6 closes the
loop. Surface area is moderate: 1 new entity + 1 enum + 1 repository + 1 service + 1 controller + 4
DTOs + 1 prompt + 1 port method, and on the frontend 1 new model + 1 service + 1 card edit + the
backbone refactor + 2 secondary page wirings.

## Requirement Traceability

| Spec item | Plan item(s) |
|-----------|--------------|
| FR-001 | Card `Generate snack` action (M4); `MeaningSnackContractTest`; `meaning-snacks.spec.ts` |
| FR-002 | `generate-meaning-snack.md` + `LlmProvider.generateMeaningSnack` + `MeaningSnackService.generate` (M2/M3) |
| FR-003 | Card `generateState='loading'` + spinner; E2E loading assertion |
| FR-004 | Card `generateError` inline `role="alert"`; service failure adds nothing; E2E forced-failure |
| FR-005 | Card button disabled while loading; page single in-flight guard; E2E rapid-click duplicate guard |
| FR-006 | Service idempotency (`findBySourceSubtopicIdAndSourceTermIgnoreCase`); `alreadyExisted`; card `done` state; E2E reopen |
| FR-007 | Prompt shape `concept/metaphor/whyItMatters/related`; service validation/re-roll; renderer reuse |
| FR-008 | `sourceTerm`/`sourceDefinition` columns; `MeaningSnackItem` fields; source shown on the card |
| FR-009 | `RichTextComponent` typed AST; no `[innerHTML]`/`DomSanitizer`; XSS E2E |
| FR-010 | `MeaningEntry` in `BackboneEntry`; `entries` computed; index list |
| FR-011 | Pure interleave preserves `batchItems` semantics; E2E mixed ordering/first-last/prev-next |
| FR-012 | `.snack-card--meaning` + badge + `--meaning-*` tokens; E2E distinct-treatment assertion |
| FR-013 | Meaning card always renders content; `currentEntry` never blank; E2E select meaning entry |
| FR-014 | Service never calls `QuizService`/batch/quiz; unit test asserts no quiz interaction |
| FR-015 | Template omits `.quiz-btn` for meaning; `startQuiz()` kind guard; E2E no-quiz assertion |
| FR-016 | Separate table/kind; excluded from batch status, quiz prefetch, results recap, XP, history |
| FR-017 | `MeaningSnack` entity + repository; Testcontainers persistence test |
| FR-018 | `GET /api/meaning-snacks?themeId=`; `init()` fetch; E2E reload/new session |
| FR-019 | New additive table only; no existing table/column touched; integration test on legacy rows |
| FR-020 | Listing fetch error → `[]`; regular snacks unaffected; E2E listing-failure degradation |
| FR-021..FR-023 | `RichTextComponent` reuse + card escaped interpolation; no `[innerHTML]`/`DomSanitizer`; XSS E2E |
| FR-024 | Regular batch/quiz code paths unchanged; existing E2E suite; `AppStateService` reset preserved |
| FR-025 | Card open/dismiss/focus code untouched; only new inputs/outputs; feature-013 E2E unchanged |
| FR-026 | No change to `SnackService`, `BatchService`, `QuizService`, provider selection, scoring |
| SC-001..SC-009 | See Testing Strategy + quickstart verification checklist |

## Testing Strategy

**Backend**
- *Prompt unit test* (`GenerateMeaningSnackPromptTest`, new): the rendered
  `generate-meaning-snack.md` includes the `{{theme}}`/`{{term}}`/`{{definition}}` values, asks for
  the exact `concept/metaphor/whyItMatters/related` JSON shape, forbids quiz questions/options/answers
  and a `keywords` array, and preserves the feature-012 formatting/forbidden-markup rules.
- *Provider unit test* (`OpenAiLlmProviderTest`, extended): a well-formed meaning-snack response
  parses into `GeneratedMeaningSnack`; a missing `related` yields an empty list; a malformed overall
  response throws `MALFORMED`; an emitted `keywords` field is ignored.
- *Service unit test* (`MeaningSnackServiceTest`, new, Mockito): blank term/definition rejected;
  definition truncated to 3 tokens; an existing meaning snack is returned with `alreadyExisted=true`
  and the provider is **never** called; invalid core re-rolls exactly once, then 503; the service
  never references `QuizService`/batch (no quiz); a valid generation persists a `MEANING` entity bound
  to theme + source subtopic.
- *Contract test* (`MeaningSnackContractTest`, new, MockMvc standalone mirroring
  `SnackMarkupRoundTripTest`): `POST /api/meaning-snacks/generate` returns `alreadyExisted` +
  `meaningSnack` with `kind: "MEANING"`, the source fields, and **exactly the 12 expected fields (no
  quiz fields, no `keywords`)**; `GET /api/meaning-snacks?themeId=` returns `{themeId, meaningSnacks}`;
  validation → 400 `VALIDATION_ERROR`; unknown theme/subtopic → 404 `NOT_FOUND`; LLM failure → 503
  `SERVICE_UNAVAILABLE` with `cause`.
- *Integration test* (`MeaningSnackPersistenceIntegrationTest`, new, Testcontainers, tag
  `integration`): a saved `MeaningSnack` reloads with identical source term/definition and `related`
  JSON; the idempotency query matches case-insensitively; the unique index rejects a duplicate
  (source subtopic, term); pre-existing `snack`/`theme`/`subtopic` rows are unaffected (FR-019).
- *Retry decorator* (`RetryingLlmProviderTest`, extended): `StubProvider` implements the new method;
  a retryable `generateMeaningSnack` failure retries and rethrows the typed cause.
- *Existing suites*: `SnackServiceTest`, `SnackMarkupRoundTripTest`, `ThemeHistoryContractTest`,
  `SnackKeywordsPersistenceIntegrationTest`, `BatchFlowIntegrationTest` must pass unchanged (the
  meaning snack adds no field to any existing DTO and touches no existing entity).

**Frontend (Playwright E2E — the only runner in the repo)**
- *`meaning-snacks.spec.ts`* (new):
  - **Action + loading (US1 / FR-001..FR-003, SC-001/SC-003)**: open a keyword definition card on
    `/snacks`, assert the `Generate snack` button is present; click with a delayed `POST` mock and
    assert the loading state; assert the new entry appears in the index/card on success.
  - **Failure (FR-004, SC-003)**: a `503`/error generate mock shows a meaningful error and adds **no**
    entry (index length unchanged).
  - **Duplicate/repeat (FR-005/FR-006, SC-002)**: rapid double-click issues one request; reopening the
    card shows the generated state; a second generate does not create a duplicate entry.
  - **Distinct + no quiz (US2 / FR-012/FR-015, SC-004/SC-005)**: a mixed backbone renders the meaning
    entry with `snack-card--meaning` + badge, a colour distinct from a regular card, and **no**
    `.quiz-btn`; the regular card keeps its `.quiz-btn`.
  - **Persistence/reload (US3 / FR-018, SC-006)**: generate, reload `/snacks`, and assert the entry is
    re-listed from `GET /api/meaning-snacks` (or the theme-listing mock) with its treatment intact.
  - **Graceful degradation (FR-020)**: a failing/empty listing still renders regular snacks with no
    crash and no broken entry.
  - **Security (US4 / FR-021..FR-023, SC-008)**: payloads (`<script>`, `onerror=`, `javascript:`,
    `<iframe>`, `<style>`, inline styles) in the source term/definition and generated content execute
    nothing and inject no nodes; readable text is preserved.
  - **Ordering (FR-011)**: a meaning snack for the first/middle/last source subtopic keeps
    Previous/Next and disabled states correct; selecting a meaning entry never renders a blank card.
- *`helpers.ts`* (extended): add `setupMeaningSnacksMock(page, { list?, generate? })`; include a
  default `GET /api/meaning-snacks` → `{themeId, meaningSnacks: []}` in `setupAllMocks` so every
  existing spec remains deterministic (the snacks page now issues this request on load).
- *Regression*: run the full existing suite (`happy-path`, `progressive-delivery`,
  `snack-wait-messages`, `results-*`, `snacks-navigation`, `snack-keywords`, `snack-rich-formatting`,
  `theme-history`, …) unchanged (FR-024, SC-009).

**Frontend unit-test gap (unchanged from 013)**: the repo has no unit-test runner; this plan follows
the 012/013 precedent and covers the pure interleave through route-mocked E2E fixtures. If true unit
coverage is later wanted, Vitest as a dev-only dependency is the minimal option (not assumed here).

## Risks / Watch Items

| Risk | Mitigation |
|------|------------|
| The snacks-backbone refactor (`items` → `batchItems` + `entries`) could regress progressive delivery, retries, prev/next or index clamping. | Keep every batch/poll/retry code path unchanged; the interleave is a pure `computed`; add mixed-ordering E2E; run the existing suites. |
| Adding a method to `LlmProvider` breaks its implementers and the test stub. | Update `OpenAiLlmProvider`, `RetryingLlmProvider` and `RetryingLlmProviderTest.StubProvider` in the same task (M2). |
| The snacks page now issues `GET /api/meaning-snacks` on load; unmocked E2E routes could make existing specs flaky. | Add a default empty listing mock to `setupAllMocks`; the page also degrades to `[]` on any error. |
| Case-insensitive idempotency could create duplicates if the DB collation is case-sensitive. | Derived `IgnoreCase` query is the primary guard; a unique index on `(source_subtopic_id, source_term)` is defence-in-depth; covered by the integration test. |
| "No quiz" could regress if a meaning snack were ever routed through the regular snack shape. | Separate table/kind/endpoint; service never calls `QuizService`/batch; meaning DTO carries no quiz fields; template omits the quiz affordance; unit + E2E assert it. |
| `ddl-auto=update` provides no migration tracking. | Pre-existing project property; change is a new table only; manual DDL/rollback documented in data-model.md §5 / quickstart.md §6. |
| Scope creep from wiring the card action on results/history. | Flagged as open decision #1 (below); the shared service keeps each page handler to a few lines. |

## Open Decisions for the Orchestrator

1. **Card action on all three hosts vs the snacks page only.** The card is shared by the snack card,
   results recap and history (feature 013). SC-001 ("100% of open keyword definition cards show a
   Generate snack action") is absolute, so this plan renders the action on **all** hosts and wires
   generation wherever theme + source subtopic are resolvable. If a snacks-page-only scope is
   preferred, gate the card with `generateEnabled` and wire only `SnacksComponent` (smaller change,
   but SC-001 would then mean "100% of cards on the snacks reading surface").
2. **Meaning-snack content carries no keywords.** The spec's snack shape lists only
   `concept/metaphor/whyItMatters/related`, so a meaning snack's content has no highlight terms and no
   nested definition card. Confirmed by this plan; flag if nested keywords are desired.
3. **Idempotent generate returns 200 + `alreadyExisted`** (rather than 409). Chosen to satisfy FR-006
   ("reflect the already-generated state") without an error path; confirm.
4. **Meaning snacks are excluded from `GET /api/themes` history** (no history data-shape change),
   satisfying FR-016 / Non-Goals. Confirm.
5. **Ordering: interleave after the source subtopic's regular entry**, unmatched appended at the end
   (Clarification #3 "linked to its source subtopic for placement"). Confirm.

## Complexity Tracking

> No Constitution Check violations. This section is intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
