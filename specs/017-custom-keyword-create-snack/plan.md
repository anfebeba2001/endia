# Implementation Plan: "Create snack" from a Custom Keyword's Generated Meaning (Task 12)

**Branch**: `017-custom-keyword-create-snack` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/017-custom-keyword-create-snack/spec.md`

## Summary

Complete the custom-keyword journey begun by feature 016: after a learner defines an arbitrary selected
term and the short definition is displayed in the single definition card, offer a **"Create snack"**
action that turns that **term + generated meaning** into a **meaning snack** in the main topics
backbone. The feature reuses the **feature-014** generation/persistence/retrieval/distinct/quiz-free
path **unchanged**.

**Backend-change finding (explicit):** **NO backend change is required.** Feature 014 already exposes
everything this feature needs — see §Backend finding below. This is **entirely a frontend change**:
two files (`snacks.component.{ts,html}` wiring + `keyword-definition-card.component.{ts,html}` label
mode) plus one new E2E spec and a small helper addition. No entity, no schema change, no endpoint, no
DTO, no service change, no new runtime dependency.

The only new behavior is (1) **capturing the correct source subtopic** at the moment the custom
definition is requested (the active backbone entry's `subtopicId` for a regular entry, its
`sourceSubtopicId` for a meaning entry — spec Clarification #1, Option A) and (2) **exposing the
action only when a valid generated meaning exists**, reusing/relabeling the feature-014 action slot so
exactly one snack-creation action is offered per card (Clarifications #2/#3).

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.4.x (backend, **unchanged**); TypeScript 5.9.x +
Angular 21.2.x (frontend, standalone components, signals-first, `ChangeDetectionStrategy.OnPush`).

**Primary Dependencies**: Angular core, Router, signals, RxJS (frontend). **No new runtime dependency
in either project.** Backend dependencies are untouched.

**Storage**: MySQL 8.x. **No schema change, no entity change, no migration.** The created snack is a
feature-014 `MeaningSnack` persisted through the existing `POST /api/meaning-snacks/generate` path.
Because no entity/column changes, Hibernate `ddl-auto=update` issues **no DDL**.

**Testing**:
- Frontend: **Playwright E2E is the only runner** (`frontend/package.json` has `@playwright/test` and
  no Karma/Jest/Vitest). The change is a pure Angular wiring/label change and is covered by a new
  route-mocked E2E spec; `npm run build` type-checks.
- Backend: **no backend test change is required** (no production backend change). The existing
  `MeaningSnackServiceTest` / `MeaningSnackContractTest` already cover the reused contract; they must
  pass unchanged.

**Target Platform**: Modern desktop + mobile browsers (Chrome/Firefox/Safari last 2 versions); Linux
server.

**Project Type**: Web-application monorepo (`backend/` + `frontend/`).

**Performance Goals**: No measurable change. One explicit user-click triggers one existing LLM call
(plus at most one re-roll) through the feature-014 endpoint; no new polling, no new network work on
load.

**Constraints**:
- Reuse the feature-014 contract exactly (constitution II/III): the frontend calls the existing
  `MeaningSnackService.generate(themeId, subtopicId, term, definition)` and `listByTheme(themeId)`;
  no new port, prompt, service, or DTO.
- Security model (features 012/013/014/015/016) preserved unchanged: escaped interpolation only, no
  `[innerHTML]`, no `DomSanitizer`; the custom term and generated meaning are untrusted.
- Exactly one snack-creation action per card; for a custom keyword it relabels the feature-014 action
  slot (never a second control).
- Existing backend/frontend/E2E suites are the regression contract and must pass unchanged.

**Scale/Scope**: Frontend only — 1 page component edit (capture source subtopic; two computed
predicates), 1 presentational-card input + label branch, 1 new E2E spec, 1 helper addition. No backend
edit, no new dependency, no schema change.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment | Verdict |
|-----------|------------|---------|
| **I. Spec-Driven Development** | `spec.md` exists with 24 FRs, 8 SCs and 5 user stories; all clarifications confirmed (no `[NEEDS CLARIFICATION]` markers) and it is the source of truth. This `plan.md` and downstream `tasks.md` follow. | PASS |
| **II. Clean Architecture (Layered)** | No backend layer is touched: the existing `MeaningSnackService` (application) → `MeaningSnack` (domain) → `MeaningSnackController` (interfaces) path is reused verbatim. Frontend follows Angular standalone/signals/OnPush: the page owns orchestration/state; the card stays a presentational component with an additive input. | PASS |
| **III. LLM Abstraction First** | The existing externalized, provider-agnostic `LlmProvider.generateMeaningSnack` flow is reused unchanged; the frontend never reaches a provider, and no prompt/port is added. | PASS |
| **IV. Quality Gate for Generated Content** | The reused `MeaningSnackService.generate` remains the single validation/normalization boundary (definition ≤20 tokens/≤200 chars, non-blank, one re-roll, typed 503). The custom definition (already ≤20 words from feature 016) flows through it unchanged. | PASS |
| **V. MVP-First, Iterate / YAGNI** | Smallest viable change: capture one source subtopic, expose one relabeled action, reuse the existing endpoint/entity/append/retrieval path. No new entity, schema, endpoint, DTO, service, provider, prompt or dependency. | PASS |

**Re-check after Phase 1 design**: No new violations. No violations to justify → **Complexity Tracking
table is intentionally empty.**

## Backend finding — "Backend change required: NO"

Feature 014 already provides every backend capability this feature needs, and the custom inputs are
valid for the existing contract. Evidence from the actual code:

| Need | Existing provision | Evidence |
|------|--------------------|----------|
| Generate a meaning snack from `term`+`definition` | `POST /api/meaning-snacks/generate` `{themeId, subtopicId, term, definition}` | `MeaningSnackController.generate`; `MeaningSnackGenerateApiRequest` (`themeId`,`subtopicId` `@NotNull`, `term`,`definition` `@NotBlank`) |
| Custom term validity | No term-length bound in the service; `source_term` column is `VARCHAR(500)` | `MeaningSnackService.generate` trims only; `MeaningSnack.sourceTerm length = 500` — a feature-016 term (≤10 tokens) fits comfortably |
| Custom definition validity | Service normalizes to ≤20 tokens and clamps to ≤200 chars; `source_definition` is `VARCHAR(200)` | `MeaningSnackService.normalizeDefinition` (`MAX_DEFINITION_TOKENS=20`, `MAX_SOURCE_DEFINITION_CHARS=200`); `MeaningSnack.sourceDefinition length = 200`. The feature-016 definition is already ≤20 words, so it passes through un-truncated |
| Source subtopic resolution | `subtopicId` must resolve to an existing `Subtopic` | Both a regular entry's `subtopicId` and a meaning entry's `sourceSubtopicId` are persisted `Subtopic` ids, so `subtopicRepository.findById` resolves |
| Duplicate / idempotency guard | Unique index `ux_meaning_snack_source (source_subtopic_id, source_term)` + case-insensitive pre-query returning `alreadyExisted=true` | `MeaningSnack` `@Table` index; `findBySourceSubtopicIdAndSourceTermIgnoreCase` |
| Persistence + reload | `GET /api/meaning-snacks?themeId=` theme-scoped, `createdAt` ascending | `MeaningSnackController.list`; `findByThemeIdOrderByCreatedAtAsc` |
| Quiz-free + distinct | `MeaningSnack` has no quiz association; `kind = MEANING`; backbone renders `snack-card--meaning` with no `.quiz-btn` | `MeaningSnack` (no quiz fields); `snacks.component.html` meaning branch |

**Conclusion**: The existing contract fully suffices. **No backend, schema, endpoint, DTO, service, or
prompt change is made.** If a future requirement demanded a backend change it would have to be additive
and must not alter the feature-014 contract; this plan introduces none.

## Project Structure

### Documentation (this feature)

```text
specs/017-custom-keyword-create-snack/
├── plan.md              # This file
├── research.md          # Phase 0 output (capture point, backend finding, action-slot reuse, dedup)
├── data-model.md        # Phase 1 output (no schema change; UI value types)
├── quickstart.md        # Phase 1 output (run + manual verification + rollback)
├── contracts/           # Phase 1 output
│   ├── meaning-snack-contract.md   # reused /api/meaning-snacks contract (unchanged)
│   └── meaning-snack.sample.json   # example custom create request/response
├── e2e-test-plan.md     # Phase 1 output (browser-level create/loading/dedup/failure/persistence)
├── checklists/          # (existing — provided with the spec)
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root) — files touched by this feature

```text
backend/                          # [UNCHANGED] no file is modified by this feature
└── src/main/java/com/endia/...   # MeaningSnackService / MeaningSnackController / MeaningSnack reused as-is

frontend/
├── src/app/
│   ├── components/keyword-definition-card/
│   │   ├── keyword-definition-card.component.ts    # [EDIT] + `customDefinition` input (label mode)
│   │   └── keyword-definition-card.component.html  # [EDIT] relabel action slot idle/loading for custom
│   └── pages/snacks/
│       ├── snacks.component.ts                     # [EDIT] capture source subtopic at Define;
│       │                                           #        custom branch in generateEnabled;
│       │                                           #        activeGenerateState keyed by captured id
│       └── snacks.component.html                   # [EDIT] pass [customDefinition] to the card
└── e2e/
    ├── custom-keyword-create-snack.spec.ts         # [NEW] create/loading/dedup/failure/persist/security
    └── helpers.ts                                  # [EDIT] (only if a custom-create fixture helper is
                                                    #         needed; existing meaning-snack + definition
                                                    #         mocks already cover the flow)
```

**Structure Decision**: Web-application monorepo. No layer is added on either side. The change is an
**in-place, additive frontend integration**: the page keeps owning orchestration/state and reuses the
existing `MeaningSnackService`; the shared definition card gains one boolean input that selects its
action label. Feature-013/014/016 behavior is preserved because every new input defaults to a no-op
and the reused method is unchanged.

### Exact integration call sites

| Surface | Component / file | Change |
|---------|------------------|--------|
| Snacks reading page (orchestration) | `SnacksComponent` — `frontend/src/app/pages/snacks/snacks.component.ts` | In `onDefineRequested()` capture the active entry's source subtopic into `activeKeywordSubtopicId`; add a custom branch to `generateEnabled`; key `activeGenerateState` off the captured `activeKeywordSubtopicId` |
| Definition card (view) | `KeywordDefinitionCardComponent` — `frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.{ts,html}` | Add `customDefinition` input; relabel the existing action slot (`Create snack` / `Creating…`) while keeping the `idle｜loading｜done` states and `.keyword-card__generate` class |
| Snacks page template | `frontend/src/app/pages/snacks/snacks.component.html` | Bind `[customDefinition]="customDefinition()"` on `<app-keyword-definition-card>` |
| Generation request | `SnacksComponent.onGenerateRequested()` | **Reused unchanged** — already reads `activeKeywordSubtopicId()`, uses `generatingKeys`, appends deduped by id, `markGenerated`, `clampStoredIndex`, error via `ErrorService` |
| Meaning-snack service | `frontend/src/app/services/meaning-snack.service.ts` | **Unchanged** — `generate` / `listByTheme` / `hasGenerated` / `markGenerated` reused |
| Meaning-snack models | `frontend/src/app/models/meaning-snack.model.ts`, `batch.model.ts` | **Unchanged** — `MeaningSnack`, `MeaningEntry.sourceSubtopicId` reused |
| Backend | `MeaningSnackService`, `MeaningSnackController`, `MeaningSnack` | **Unchanged** |
| Results / History | `ResultsComponent`, `HistoryComponent` | **Unchanged** (no custom-Define flow there; card input defaults false) |

## Design

### Backend

**No change.** The feature reuses `MeaningSnackService.generate(themeId, subtopicId, term,
definition)`, `POST /api/meaning-snacks/generate`, and `GET /api/meaning-snacks?themeId=` exactly as
feature 014 defines them. The custom definition produced by feature 016 is already a short (≤20-word)
non-blank string and is a valid `definition` argument; the custom term is a valid `term` argument
(trimmed; `source_term` is `VARCHAR(500)`). The source subtopic (regular `subtopicId` or meaning
`sourceSubtopicId`) resolves to an existing `Subtopic`. Duplicate protection comes from the existing
unique index + case-insensitive idempotent generate. See §Backend finding and
[contracts/meaning-snack-contract.md](./contracts/meaning-snack-contract.md).

### Frontend

#### 1. Capturing the source subtopic (design problem #1) — DECISION: capture at `onDefineRequested`

The source subtopic is captured **when the custom definition is requested** (the moment the card opens
for the selected term), into the existing private `activeKeywordSubtopicId` signal, using the active
backbone entry at that instant:

```ts
// onDefineRequested(), before opening the card:
const entry = this.currentEntry();
const sourceSubtopicId =
  entry?.kind === 'regular' ? entry.subtopicId
  : entry?.kind === 'meaning' ? entry.sourceSubtopicId
  : null;
this.activeKeywordSubtopicId.set(sourceSubtopicId);
```

Rationale:
- **Matches the spec (Option A)**: the created snack attaches to the active entry's source subtopic —
  regular → `subtopicId`, meaning → `sourceSubtopicId`.
- **Matches feature 014's established pattern**: the page already captures the source at card-open time
  ("Use the source captured when the card opened, never the possibly-navigated current entry") so a
  later navigation can never attach a snack to the wrong subtopic. Capturing at Define time pins the
  entry the meaning was read from.
- **Handles meaning entries**: today `activeKeywordSubtopicId` is only set in `openKeyword` for regular
  entries; the custom-Define flow leaves it `null`. Setting it here (including the meaning-entry case)
  fixes that gap and makes the existing `onGenerateRequested` work **unchanged**.
- **Navigation safety is already structural**: `goTo(...)` calls `closeKeyword()` (which clears
  `activeKeywordSubtopicId`), and `openKeyword(...)` replaces the card and resets custom state, so the
  active entry cannot change while the custom card is open. Capturing at open time is belt-and-braces,
  consistent with feature 014.

Rejected alternative (research R1): capture at "Create snack" activation from `currentEntry()`.
Rejected because it diverges from the feature-014 capture-at-open invariant and would rely on the
current entry being unchanged at a later moment rather than pinning the entry the meaning was read
from.

#### 2. Action availability (design problem #2) — DECISION: reuse the slot, gate on a valid meaning

The action **reuses the feature-014 action slot** in `keyword-definition-card.component.html` (same
`.keyword-card__generate` button and `idle｜loading｜done` states), so there is exactly one
snack-creation action per card (FR-003). It is relabeled when the card is in custom mode via a new
boolean input:

```ts
// keyword-definition-card.component.ts (additive; default false → feature-014 unchanged)
readonly customDefinition = input<boolean>(false);
```

```html
@if (generateState() === 'loading') {
  <span class="spinner spinner--sm" aria-hidden="true"></span>
  {{ customDefinition() ? 'Creating…' : 'Generating…' }}
} @else if (generateState() === 'done') {
  Added to your snacks <span aria-hidden="true">✓</span>
} @else {
  {{ customDefinition() ? 'Create snack' : 'Generate snack' }}
}
```

Availability is computed in the page:

```ts
readonly generateEnabled = computed(() => {
  if (!this.themeId) {
    return false;
  }
  if (this.customDefinition()) {
    // Custom-Define card: offer "Create snack" only once a valid generated meaning exists and a
    // source subtopic was captured (FR-002). No action while loading, on error, or when blank.
    return (
      !this.definitionLoading() &&
      !this.definitionError() &&
      !!this.activeKeyword()?.definition &&
      !!this.activeKeywordSubtopicId()
    );
  }
  // Feature-014 regular keyword card: unchanged.
  return this.currentEntry()?.kind === 'regular';
});
```

This makes the action appear only after "Define" completes successfully with a non-empty definition
(FR-001/FR-002), keeps it absent during definition loading and after a definition error (edge cases,
SC-001), and never offers a second action for the custom card (FR-003). The regular-keyword branch is
byte-for-byte equivalent to today, so feature 014 is not regressed.

The card's `requestGenerate()` guard already refuses to emit unless `generateEnabled()` is true and
`generateState() === 'idle'`, so no card-side change beyond the label is needed.

#### 3. Generation wiring (design problem #3) — DECISION: reuse `onGenerateRequested` unchanged

`onGenerateRequested(keyword)` already implements exactly the required semantics for both surfaces:

- reads the **captured** `activeKeywordSubtopicId()` (never the possibly-changed current entry);
- one-in-flight guard `generatingKeys.has(generateKey(subtopicId, keyword.term))`;
- sets `generateStates[key] = 'loading'`, clears `generateError`;
- calls `meaningSnackService.generate(themeId, subtopicId, keyword.term, keyword.definition)` — for the
  custom card `keyword.definition` is the generated meaning;
- on success: appends deduped by id (`list.some(m => m.id === …)`), `markGenerated`, updates
  `AppState.meaningSnacks`, sets state `done`, clamps the stored index;
- on error: deletes the guard, resets state to `idle`, sets `generateError` from
  `errorService.getMessage(err)`, adds nothing.

The only change is that `activeGenerateState` must resolve for the custom card, keyed by the captured
`activeKeywordSubtopicId`:

```ts
readonly activeGenerateState = computed<GenerateState>(() => {
  const keyword = this.activeKeyword();
  const subtopicId = this.activeKeywordSubtopicId();
  if (!keyword || !subtopicId) {
    return 'idle';
  }
  const state = this.generateStates()[generateKey(subtopicId, keyword.term)];
  if (state) {
    return state;
  }
  return this.meaningSnackService.hasGenerated(subtopicId, keyword.term) ? 'done' : 'idle';
});
```

This is behavior-equivalent for regular keyword cards (where `openKeyword` already sets
`activeKeywordSubtopicId` to the regular `subtopicId`) and now also resolves for the custom card. The
`hasGenerated` fallback gives FR-010 (re-opened card reflects the already-generated state) for free,
because a successfully created snack marks the `${subtopicId}::${term.toLowerCase()}` key.

#### 4. Dedup / at-most-one (design problem #4) — DECISION: existing key + guard, no new work

At-most-one is guaranteed by three existing layers, unchanged:
1. card `requestGenerate()` refuses a second emit while `generateState() !== 'idle'`;
2. page `generatingKeys` Set keyed by `${sourceSubtopicId}::${term.toLowerCase()}` blocks a concurrent
   duplicate;
3. backend unique index `ux_meaning_snack_source` + idempotent `alreadyExisted=true` return prevents a
   persisted duplicate (and the page dedupes the appended list by `id`).

Rapid double activation therefore yields at most one request and at most one entry (FR-007, SC-002).

#### 5. Persistence / reload (design problem #5) — DECISION: existing theme-scoped listing suffices

`SnacksComponent.init()`/`loadMeaningSnacks(themeId)` already calls `GET /api/meaning-snacks?themeId=`
on every page load, stores the result in `meaningSnacks`/`AppState`, and marks each generated. A
custom-created snack is a persisted `MeaningSnack`, so it reappears in the backbone after a reload/new
session for the same theme with its distinct, quiz-free rendering. **No new work** (FR-014/FR-015,
SC-005). A failing/empty listing already degrades gracefully to an empty list.

#### 6. Quiz-free + distinct (design problem #6) — DECISION: feature-014 kind guarantees it

The created snack is a `MeaningSnack` (`kind: 'MEANING'`), projected into the backbone as a
`MeaningEntry` and rendered by the existing `snack-card--meaning` branch (distinct variant + text
badge, no `.quiz-btn`, never stored in `AppState.snacks()`). No quiz is generated/prefetched/stored/
offered. **No new work** (FR-011–FR-013, SC-004).

#### 7. Security (design problem #7) — DECISION: escaped interpolation only

The card renders `{{ kw.term }}` / `{{ kw.definition }}`; the new label is static text; the backbone
meaning branch renders generated content through `RichTextComponent` (typed AST). The new code
introduces **no** `[innerHTML]` and **no** `DomSanitizer`. The custom term and generated meaning remain
untrusted and inert (FR-017–FR-019, SC-006). **No new rendering surface is added.**

### Snacks page template change

```html
<app-keyword-definition-card
  [keyword]="activeKeyword()"
  [anchor]="activeKeywordAnchor()"
  [generateEnabled]="generateEnabled()"
  [generateState]="activeGenerateState()"
  [generateError]="generateError()"
  [customDefinition]="customDefinition()"   <!-- [NEW] label mode -->
  [definitionLoading]="definitionLoading()"
  [definitionError]="definitionError()"
  [anchorRect]="activeKeywordRect()"
  (generateRequested)="onGenerateRequested($event)"
  (closed)="closeKeyword()"
/>
```

## Milestones / Phases

| # | Milestone | Scope | FRs | Depends on |
|---|-----------|-------|-----|------------|
| **M1** | Capture source subtopic + availability | `onDefineRequested` captures `activeKeywordSubtopicId` (regular `subtopicId` / meaning `sourceSubtopicId`); custom branch in `generateEnabled`; `activeGenerateState` keyed by the captured id | FR-002, FR-005, FR-010 | — |
| **M2** | Action slot relabel + wiring | `customDefinition` input + label branch in the card (`Create snack`/`Creating…`); bind it on the snacks page; reuse `onGenerateRequested` unchanged | FR-001, FR-003, FR-004, FR-006, FR-007, FR-008, FR-009 | M1 |
| **M3** | Persistence / quiz-free / security confirmation | No code beyond M1/M2; confirm the reused listing path reloads the snack, the `MEANING` kind is quiz-free/distinct, and no `[innerHTML]`/`DomSanitizer` is introduced | FR-011–FR-019 | M1, M2 |
| **M4** | E2E + regression | `custom-keyword-create-snack.spec.ts` (create/loading/dedup/failure/persist/reload/quiz-free/security/non-regression); helper additions if needed; full existing suite passes unchanged | FR-020–FR-024, SC-001–SC-008 | M2, M3 |

Rough effort ordering: M1 is the only logic change and is small; M2 is additive template/label work; M3
is a confirmation pass (no code); M4 closes the loop. M1 and M2 can be built together; M3/M4 verify.

## Requirement Traceability

| Spec item | Plan item(s) |
|-----------|--------------|
| FR-001 | Card action slot relabeled `Create snack` when `customDefinition` (M2) |
| FR-002 | `generateEnabled` custom branch requires `!definitionLoading && !definitionError && definition non-empty` (M1); E2E "no action before Define completes / on error" |
| FR-003 | Reuse of the single `.keyword-card__generate` slot; no second control (M2); E2E exactly one action |
| FR-004 | Reuse `MeaningSnackService.generate` via `onGenerateRequested` (M2) |
| FR-005 | Capture active entry's source subtopic at `onDefineRequested` (regular/meaning) (M1); E2E asserts POST `subtopicId` |
| FR-006 | Card `loading` state (`Creating…`) reused (M2); E2E loading assertion |
| FR-007 | `requestGenerate` guard + `generatingKeys` (M1/M2); E2E rapid double activation = one request |
| FR-008 | `onGenerateRequested` error path sets `generateError`, adds nothing (M2); E2E forced failure |
| FR-009 | Append deduped by id via existing path (M2); E2E one backbone entry |
| FR-010 | `activeGenerateState` `hasGenerated` fallback (M1); E2E re-open shows done, no duplicate |
| FR-011 | `MeaningSnack` kind has no quiz; backbone meaning branch has no `.quiz-btn` (M3); E2E quiz-free |
| FR-012 | Existing `snack-card--meaning` distinct treatment (M3); E2E distinct badge/border |
| FR-013 | Meaning entries never stored in `AppState.snacks()` / never quiz targets (M3); E2E regular quiz unchanged |
| FR-014 | Existing `GET /api/meaning-snacks?themeId=` listing on load (M3); E2E reload/new session |
| FR-015 | `loadMeaningSnacks` graceful degradation reused (M3); E2E failing listing |
| FR-016 | Backend finding: no new entity/schema/endpoint (M1–M3); §Backend finding |
| FR-017 | Escaped interpolation only in card/backbone (M2/M3); E2E injection |
| FR-018 | Injection payloads inert (M2/M3); E2E injection |
| FR-019 | No `[innerHTML]`/`DomSanitizer` introduced; feature-012/013/014/015/016 model preserved (M2/M3) |
| FR-020 | Existing suites unchanged (M4) |
| FR-021 | Regular "Generate snack" branch of `generateEnabled` unchanged (M1/M4) |
| FR-022 | Feature-016 Define trigger/display unchanged apart from the action (M1/M4) |
| FR-023 | Feature-013 highlight/card open/dismiss/focus unchanged (M4) |
| FR-024 | No change to regular snack/quiz/batch or feature-014 kind/persistence/interleave (M1–M4) |
| SC-001 | E2E "action only after Define completes"; "no action on blank/errored" |
| SC-002 | E2E rapid double activation + re-open = one snack |
| SC-003 | E2E loading state + failure adds nothing |
| SC-004 | E2E quiz-free + distinct |
| SC-005 | E2E reload/new session persistence |
| SC-006 | E2E injection payloads execute/inject nothing |
| SC-007 | §Backend finding (no new entity/schema/endpoint) |
| SC-008 | Full existing E2E suite unchanged (M4) |

## Testing Strategy

**Frontend (Playwright E2E — the only runner in the repo)** — new `custom-keyword-create-snack.spec.ts`:

- **Create action availability (US1 / FR-001–FR-003, SC-001)**: hold a selection → Define with a
  delayed mock → while defining, assert **no** `.keyword-card__generate` action; after the definition
  resolves, assert exactly one action labelled `Create snack`.
- **Success adds exactly one meaning snack (US1 / FR-004/FR-005/FR-009, SC-002)**: activate
  `Create snack` with a mocked `POST /api/meaning-snacks/generate`; assert the request body
  `{themeId, subtopicId, term, definition}` carries the captured subtopic (`sub-001`) and the
  generated definition; assert one `.snack-index__item--meaning` appears and the button shows the
  `done` state.
- **Loading state (US1 / FR-006, SC-003)**: with a delayed generate mock, assert the loading state
  (`Creating…` / `.keyword-card__action-status`).
- **Rapid double activation = one snack (US1 / FR-007, SC-002)**: click twice via `evaluate`; assert
  one generate call and one meaning entry.
- **Failure shows an error and adds nothing (US1 / FR-008, SC-003)**: 503 mock → `.keyword-card__error`
  `role="alert"`, no meaning entry, backbone count unchanged.
- **Re-open reflects done (US1 / FR-010, SC-002)**: create, dismiss, re-define the same term → action
  disabled/done, no extra request/entry.
- **Source subtopic for a meaning active entry (US1 / FR-005)**: seed a meaning snack, select it, then
  define a term from the meaning card content → assert the POST `subtopicId` equals the meaning entry's
  `sourceSubtopicId` (Option A).
- **Persistence / reload (US3 / FR-014/FR-015, SC-005)**: a `stored[]` listing mock; create then
  re-enter the theme (full load) → the custom meaning snack is re-listed with distinct, quiz-free
  rendering.
- **Quiz-free + distinct (US2 / FR-011–FR-013, SC-004)**: the created entry renders `snack-card--meaning`
  + badge with **no** `.quiz-btn`; a regular snack keeps its `Start Quiz` and routes to `/quiz`.
- **Security (US4 / FR-017–FR-019, SC-006)**: a mocked definition and a created content containing
  `<script>`, `onerror=`, `javascript:`, `<iframe>`, `<style>` → `window.__xss` stays `undefined`, no
  `script/img/iframe/style/a` nodes in `.keyword-card`/`.snack-card--meaning`, readable text preserved.
- **Non-regression (US5 / FR-020–FR-024, SC-008)**: the full existing suite
  (`custom-keyword-define`, `meaning-snacks`, `snack-keywords`, `snack-rich-formatting`,
  `snacks-navigation`, `progressive-delivery`, `results-*`, `theme-history`, `happy-path`, …) must pass
  unchanged; the regular keyword card still shows `Generate snack`.

**Backend**: no production change → **no new backend test**; the existing `MeaningSnackServiceTest` and
`MeaningSnackContractTest` (and the full `mvn test` suite) must pass unchanged, which is the evidence
for SC-007 (reused contract) and the backend half of SC-008.

**Frontend unit-test gap (unchanged from 013/014/015/016)**: the repo has no unit-test runner; the pure
capture/state logic is covered through route-mocked E2E fixtures.

## Risks / Watch Items

| Risk | Mitigation |
|------|------------|
| **Wrong source subtopic captured** (the core risk). | Capture at `onDefineRequested` into `activeKeywordSubtopicId` using the active entry at that instant; navigation already calls `closeKeyword()` (clearing it), so a later navigation cannot attach to a wrong subtopic; E2E asserts the POST `subtopicId` for both a regular and a meaning active entry. |
| **Regressing the feature-014 regular card** by changing `generateEnabled`/`activeGenerateState`. | The regular branch is kept behavior-equivalent (regular → `currentEntry().kind === 'regular'`; `activeGenerateState` keyed by `activeKeywordSubtopicId`, which `openKeyword` already sets to the regular `subtopicId`); full `meaning-snacks.spec.ts` runs in M4. |
| **Two snack-creation actions** on the custom card. | The feature-014 action slot is **reused/relabeled**, not duplicated; the card template has exactly one `.keyword-card__generate`; E2E asserts a single action. |
| **Action offered before a valid meaning exists.** | `generateEnabled` custom branch requires `!definitionLoading && !definitionError && definition non-empty && captured subtopic`; E2E covers the loading and error cases. |
| **A blank/oversized custom definition reaching the endpoint.** | The custom definition is produced by feature 016 (already non-blank, ≤20 words) and the action is only offered when it is non-empty; the reused backend re-validates (blank → 503, ≤20-token/≤200-char clamp), so the endpoint is defence-in-depth. |
| **A multi-word custom term exceeding the column.** | Feature-016 terms are ≤10 tokens; `source_term` is `VARCHAR(500)`, ample headroom; no schema change needed. |
| **Card input regressing other surfaces (results/history).** | `customDefinition` defaults to `false`; results/history never bind it, so their card label is unchanged; existing suites run in M4. |
| **Scope creep into backend changes.** | The backend finding is explicit (NO change); the reused contract is documented in `contracts/`. |

## Open Decisions for the Orchestrator

1. **Capture point = `onDefineRequested` (card-open) time.** Chosen to match feature 014's
   capture-at-open invariant and to pin the entry the meaning was read from; the alternative (capture
   at "Create snack" activation) was rejected (research R1). Confirm.
2. **Action label semantics — RESOLVED (orchestrator, 2026-09-24).** Idle `Create snack`; loading
   `Creating…`; done `Added to your snacks`. The wording is now fixed by spec §FR-006. T008 MUST use
   exactly these labels for the custom-Define card.
3. **Relabel mechanism = a new boolean `customDefinition` input on the shared card** (default `false`),
   rather than a separate component or a generic `actionLabel` string. Confirm.
4. **No backend change** (see §Backend finding). Confirm that SC-007 is satisfied by reusing the
   feature-014 contract unchanged.

## Complexity Tracking

> No Constitution Check violations. This section is intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
