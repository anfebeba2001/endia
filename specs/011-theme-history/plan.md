# Implementation Plan: Read-Only History of Created Themes

**Branch**: `011-theme-history` | **Date**: 2026-09-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-theme-history/spec.md`

## Summary

Read-only, end-to-end feature that makes every created theme reachable again. Two changes:

1. **Backend history endpoint (US1/US2, FR-001..FR-008)**: add `GET /api/themes` to the existing
   `ThemeController`. It returns a JSON **array** of every theme ordered by `createdAt` descending,
   each entry carrying `themeId`, `prompt`, `createdAt`, and nested `subtopics[]`; each subtopic
   carries `id`, `name`, `snacks[]`, `quizQuestions[]`; snacks carry
   `id/concept/metaphor/whyItMatters/related`; questions carry `id/type/prompt/explanation`.
   `ThemeService.listHistory()` is a `@Transactional(readOnly = true)` read that assembles the nested
   data with **3 fixed queries** (no N+1, no `LazyInitializationException`, no
   `MultipleBagFetchException`). Mapping to the new `ThemeHistoryApiResponse` record happens in the
   controller (FR-006/FR-022).
2. **Frontend history page + header link (US1/US3/US4, FR-009..FR-018)**: add a lazy-loaded
   `/history` route (`HistoryComponent`), a `ThemeService.listHistory()` method mapping the array into
   a `ThemeHistory[]` model, a visible "History" link in `.app-shell__header`, and loading / error
   (`ErrorService`) / empty / populated states with read-only nested detail.

**Key read-strategy decision (FR-005/FR-022)**: `Theme`, `Subtopic.snacks`, and
`Subtopic.quizQuestions` are all `List` bags. A single `JOIN FETCH` / `@EntityGraph` across all three
levels would raise Hibernate's `MultipleBagFetchException` (or a cartesian product), so the plan
**fetches themes+subtopics via one `@EntityGraph` query, then batch-loads snacks and quiz questions
by subtopic id in two `findBySubtopicIdIn` queries, and assembles a service read model inside the
read-only transaction**. The controller maps that read model to `ThemeHistoryApiResponse`. No entity
mutation, no schema change, no OSIV reliance.

**No behavior change** to `POST /api/themes/suggest`, `POST /api/themes/{themeId}/subtopics`,
`POST /api/snacks/generate`, `/api/batches`, or the quiz endpoints (FR-007).

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.4.x (backend); TypeScript 5.9.x + Angular 21.2.x
(frontend, standalone components, signals, `ChangeDetectionStrategy.OnPush`).

**Primary Dependencies**: Spring Web / Spring Data JPA / Hibernate (backend); Angular Router, Angular
signals, RxJS, `@angular/common/http` (frontend). **No new dependency** in either project.

**Storage**: MySQL 8.x. **No schema or migration change** — history is derived from existing
`theme`, `subtopic`, `snack`, and `quiz_question` tables (FR-022, Non-Goals). `ddl-auto=update`
produces no delta.

**Testing**:
- Backend: JUnit 5 + Mockito unit tests, MockMvc standalone contract test, and a Testcontainers
  MySQL integration test (tagged `integration`, run with `mvn verify -Pintegration`; excluded from
  the default surefire run — see `backend/pom.xml` lines 89-108).
- Frontend: Playwright E2E only (no Karma/Jest runner exists), plus `npm run build` type-check.

**Target Platform**: Modern desktop browsers (Chrome/Firefox/Safari last 2 versions); Linux server.

**Project Type**: Web application monorepo (`backend/` + `frontend/`).

**Performance Goals**: `GET /api/themes` returns in a bounded number of queries regardless of theme
count (3 queries + 1 per collection when a subtopic exists: themes+subtopics, snacks, questions).
Full-history, no pagination (spec Non-Goals). No generation latency change.

**Constraints**:
- Read-only: 0 writes, no create/update/delete/regenerate (FR-008/FR-022).
- No `LazyInitializationException` while assembling nested data (FR-005/SC-003).
- Existing endpoint contracts and behavior unchanged (FR-007/SC-004).
- No schema/migration change (FR-022/Non-Goals).
- Frontend must reuse `ErrorService.getMessage` and the standalone-signals/`OnPush` pattern
  (FR-012..FR-014).
- The header gains a link but must not break the header/logo/dark-mode suites (FR-021/SC-008).
- No `[NEEDS CLARIFICATION]` markers remain (spec §Open Questions: all non-blocking, defaults chosen).

**Scale/Scope**: 1 new backend DTO, 1 new controller method, 1 new service read method + read model,
3 repository additions, 4 new frontend files, 5 modified frontend files, 2 new backend tests + 1
modified, 1 new Playwright spec + helper addition. No new component libraries, routes beyond
`/history`, or dependencies.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Evidence |
|---|-----------|--------|----------|
| I | **Spec-Driven Development** | PASS | spec.md defines 4 user stories, 22 FRs, 8 SCs, 57 checklist items (all checked). Every FR/SC maps to a concrete file/method/test (see Mapping). |
| II | **Clean Architecture (Layered)** | PASS | Domain models unchanged. Read logic in `ThemeService` (application); repository finders in `infrastructure/persistence`; endpoint + mapping in `interfaces/rest`; DTO in `interfaces/rest/dto`. Application returns its own read model and never imports an interface DTO, so the dependency rule holds. Frontend uses standalone components + signals. |
| III | **LLM Abstraction First** | PASS | No `LlmProvider`/prompt/provider code touched. History is a pure DB read; no LLM call. |
| IV | **Quality Gate for Generated Content** | PASS | No quiz/snack generation or validation touched. |
| V | **MVP-First, Iterate** | PASS | Smallest change set that satisfies the spec: one endpoint, one page, one link. No auth, pagination, delete/edit/regenerate, export, or mobile menu (Non-Goals). Reuses existing DTO/record, service, and error/state patterns. |

**All gates PASS. No violations to justify.**

## Complexity Tracking

> No constitution violations. No complexity justifications needed.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | N/A | N/A |

## Architecture (classic layers)

```text
interfaces/rest        ThemeController.getHistory()  → maps service read model → ThemeHistoryApiResponse
interfaces/rest/dto    ThemeHistoryApiResponse (new record, nested records)
        ▲
application/service    ThemeService.listHistory() @Transactional(readOnly=true)
                       returns List<ThemeService.ThemeHistory> (read model, nested records)
        ▲
infrastructure/        JpaThemeRepository.findAllByOrderByCreatedAtDesc() @EntityGraph("subtopics")
persistence            JpaSnackRepository.findBySubtopicIdIn(Collection<UUID>)
                       JpaQuizQuestionRepository.findBySubtopicIdIn(Collection<UUID>)
        ▲
domain/model           Theme / Subtopic / Snack / QuizQuestion — UNCHANGED
domain/enums           QuestionType — UNCHANGED
```

- The **application layer does not depend on `interfaces/rest/dto`**; it returns its own nested read
  model (`ThemeService.ThemeHistory`), consistent with the existing `ThemeService.ThemeSuggestResult`
  nested-record convention. The controller performs the API mapping (FR-022).
- The **domain layer is untouched** (FR-022): no entity, field, annotation, or enum change.
- The repositories own the query strategy (fetch graph + batch-by-id), keeping Hibernate concerns in
  infrastructure.

## Read Strategy (the critical design point)

`Theme.subtopics`, `Subtopic.snacks`, and `Subtopic.quizQuestions` are all `List` (bag) collections.
Fetching two or more bags in one query is invalid in Hibernate (`MultipleBagFetchException`) and
joining all three would multiply rows. The chosen strategy:

1. `themeRepository.findAllByOrderByCreatedAtDesc()` with
   `@EntityGraph(attributePaths = {"subtopics"})` — **one query**; subtopics are initialized in the
   same persistence context. Single bag, so safe. `ORDER BY created_at DESC` gives FR-001 ordering.
2. Collect every `Subtopic.id` across all themes.
3. `snackRepository.findBySubtopicIdIn(ids)` — **one query** (empty `ids` → skip).
4. `quizQuestionRepository.findBySubtopicIdIn(ids)` — **one query** (empty `ids` → skip).
5. Group each result by `snack.getSubtopic().getId()` / `question.getSubtopic().getId()` (accessing
   the lazy proxy's identifier does **not** trigger a load) and build the `ThemeService.ThemeHistory`
   read model inside the read-only transaction.

Total: 1 query for themes+subtopics, plus 2 batch queries when at least one subtopic exists; **0 N+1,
0 lazy initialization exceptions, 0 writes**. When there are no themes, step 1 returns an empty list
and the method returns `[]` (HTTP 200, FR-005).

## Project Structure

### Documentation (this feature)

```text
specs/011-theme-history/
├── spec.md                    # Feature specification (exists)
├── plan.md                    # This file
├── research.md                # Phase 0 — decisions + rationale
├── data-model.md              # Phase 1 — entities/DTOs + loading strategy
├── quickstart.md              # Phase 1 — run + manual verification + test commands
├── contracts/
│   └── get-themes.md          # Phase 1 — GET /api/themes contract (200 array, [] case, errors)
├── checklists/
│   ├── requirements.md        # Requirements-quality checklist (exists, all checked)
│   ├── implementation.md      # Implementation-quality checklist
│   ├── test.md                # Test-quality checklist
│   ├── ux.md                  # UX-quality checklist
│   └── security.md            # Security-quality checklist
└── tasks.md                   # Phase 2 — NOT created by planner
```

### Source Code (repository root)

```text
endia/
├── backend/
│   ├── src/main/java/com/endia/
│   │   ├── application/service/
│   │   │   └── ThemeService.java                         # MOD: + listHistory() @Transactional(readOnly=true)
│   │   │   │                                             #      + nested read-model records
│   │   ├── infrastructure/persistence/
│   │   │   ├── JpaThemeRepository.java                   # MOD: + findAllByOrderByCreatedAtDesc() @EntityGraph("subtopics")
│   │   │   ├── JpaSnackRepository.java                   # MOD: + findBySubtopicIdIn(Collection<UUID>)
│   │   │   └── JpaQuizQuestionRepository.java            # MOD: + findBySubtopicIdIn(Collection<UUID>)
│   │   └── interfaces/rest/
│   │       ├── ThemeController.java                      # MOD: + @GetMapping getHistory() → List<ThemeHistoryApiResponse>
│   │       └── dto/
│   │           └── ThemeHistoryApiResponse.java          # NEW: record + nested SubtopicItem/SnackItem/QuestionItem
│   └── src/test/java/com/endia/
│       ├── application/service/
│       │   └── ThemeServiceTest.java                     # MOD: + listHistory ordering/empty/nested tests
│       ├── application/integration/
│       │   └── ThemeHistoryIntegrationTest.java          # NEW: Testcontainers MySQL, no-lazy + ordering + nested
│       └── interfaces/rest/
│           └── ThemeHistoryContractTest.java             # NEW: MockMvc array shape, 200 [], ordering
└── frontend/
    ├── e2e/
    │   ├── theme-history.spec.ts                         # NEW: header link, populated/empty/error/loading, deep link, read-only, a11y
    │   └── helpers.ts                                    # MOD: + setupThemesHistoryMock(page, themes)
    └── src/app/
        ├── app.routes.ts                                 # MOD: + lazy /history route
        ├── app.html                                      # MOD: + "History" link in .app-shell__header
        ├── app.scss                                      # MOD: header nav/link styles + :focus-visible
        ├── models/
        │   └── history.model.ts                          # NEW: ThemeHistory, HistorySubtopic, HistorySnack, HistoryQuestion
        ├── services/
        │   └── theme.service.ts                          # MOD: + listHistory(): Observable<ThemeHistory[]>
        └── pages/history/
            ├── history.component.ts                      # NEW: standalone OnPush, signals, ErrorService
            ├── history.component.html                    # NEW: loading/error/empty/populated + nested detail
            └── history.component.scss                    # NEW: page + nested detail styles
```

**Structure Decision**: Monorepo as-is. Backend edits stay in the existing `application/service`,
`infrastructure/persistence`, and `interfaces/rest` layers. Frontend follows the established
`pages/<name>/` standalone-component layout (one new folder), reuses the existing `ThemeService` and
`ErrorService`, and adds exactly one lazy route. No new architecture is introduced.

## API Contract Summary

Full contract: `contracts/get-themes.md`.

- **`GET /api/themes`** — no request body, no parameters. Returns **HTTP 200** with a JSON array
  ordered by `createdAt` descending. Empty history → **HTTP 200 `[]`** (not 404/500).
- Each array entry: `{ "themeId": UUID, "prompt": string, "createdAt": ISO-8601 string,
  "subtopics": [...] }`.
- Each subtopic: `{ "id": UUID, "name": string, "snacks": [...], "quizQuestions": [...] }`.
- Each snack: `{ "id": UUID, "concept": string, "metaphor": string, "whyItMatters": string,
  "related": string[] }`.
- Each question: `{ "id": UUID, "type": "MULTIPLE_CHOICE" | "DRAG_DROP", "prompt": string,
  "explanation": string }` (options/correctIndex/variant/dragDrop intentionally omitted, Non-Goals).
- Errors use the existing `GlobalExceptionHandler.ErrorResponse`
  `{ "error": string, "message": string, "field": string|null, "cause": string|null }`; an
  unexpected failure is HTTP 500 `INTERNAL_ERROR`. There is no feature-specific error path.

## Milestones

- **M0 (P1, backend read path)**: repositories + `ThemeService.listHistory()` read model +
  `ThemeHistoryApiResponse` + `ThemeController.getHistory()`. Delivers FR-001..FR-008, SC-001..SC-004.
- **M1 (P1, backend tests)**: `ThemeServiceTest` additions, `ThemeHistoryContractTest`,
  `ThemeHistoryIntegrationTest`. Delivers FR-019, SC-003/SC-008.
- **M2 (P1, frontend wiring)**: `history.model.ts`, `ThemeService.listHistory()`, `/history` route,
  header link. Delivers FR-009..FR-011, FR-017, SC-005.
- **M3 (P1/P2, history page)**: `HistoryComponent` + template + styles with loading/error/empty/
  populated states, read-only nested detail, keyboard/`aria` affordances. Delivers FR-012..FR-016,
  FR-018, SC-006/SC-007.
- **M4 (P2, E2E + non-regression)**: `theme-history.spec.ts`, helper, full-suite run. Delivers
  FR-020/FR-021, SC-008.

## Risks

- **`MultipleBagFetchException` / cartesian product.** A naive 3-level `JOIN FETCH` or a single
  `@EntityGraph` over `subtopics.snacks` + `subtopics.quizQuestions` is invalid. Mitigated by the
  fetch-one-bag + batch-by-id strategy above; documented in `research.md` R1.
- **OSIV masking lazy bugs.** `spring.jpa.open-in-view` is not configured, so Spring Boot's default
  (`true`) is in effect during web requests; this can hide an uninitialized association in
  production tests. The `ThemeHistoryIntegrationTest` calls the service **outside** a web request and
  accesses the returned nested data after the transaction, so a regression surfaces as a
  `LazyInitializationException`. Flagged for task order: the read model must be fully materialized
  inside the transaction.
- **Ordering ties.** `createdAt` has no unique tie-breaker; equal timestamps may order arbitrarily.
  The spec accepts ties (Edge Cases). A stable secondary sort (`id`) is optional and not required.
- **Header layout regression.** Adding a third header child with `justify-content: space-between`
  would space the brand/toggle apart. Mitigated by wrapping the link + toggle in
  `.app-shell__nav`; the logo-visibility specs read `.brand__name`, `.brand__dot`, `.theme-toggle`,
  and `.app-shell__header` only, so they remain green (FR-021/SC-008).
- **Payload size.** Full history with no pagination; acceptable for MVP volumes (spec Open
  Questions/Non-Goals). No cap is added this pass.
- **Timestamp format.** `createdAt` is a `LocalDateTime`; Spring Boot's default Jackson config
  serializes it as ISO-8601 (e.g. `2026-09-11T19:52:00`) with no `@JsonFormat` needed. The frontend
  formats for display locally (Assumptions/Open Question 2).

## Mapping: Spec Requirements → Plan Items

| Spec ID | Requirement | Plan Item |
|---------|-------------|-----------|
| FR-001 | `GET /api/themes` → 200 array newest-first with `themeId/prompt/createdAt/subtopics` | M0: repo ordering + controller method + DTO |
| FR-002 | Subtopic `id/name/snacks/quizQuestions` | M0: `ThemeHistoryApiResponse.SubtopicItem` |
| FR-003 | Snack `id/concept/metaphor/whyItMatters/related` | M0: `SnackItem` |
| FR-004 | Question `id/type/prompt/explanation` | M0: `QuestionItem` |
| FR-005 | `listHistory()` read-only, empty → 200 `[]`, no lazy exception | M0 + read strategy; M1 integration test |
| FR-006 | New `ThemeHistoryApiResponse` under `interfaces/rest/dto` | M0: new DTO file |
| FR-007 | Existing endpoints unchanged | M0 (additive only) + M1 contract/regression |
| FR-008 | Read-only, no mutations | M0: `@Transactional(readOnly=true)`, no save calls |
| FR-009 | `ThemeService.listHistory()` → `ThemeHistory` model | M2: service + model |
| FR-010 | Lazy `/history` route → `HistoryComponent` | M2: `app.routes.ts` |
| FR-011 | Visible "History" link in `.app-shell__header` | M2: `app.html` |
| FR-012 | Loading state cleared when settled | M3: `HistoryComponent` signals |
| FR-013 | Error uses `ErrorService.getMessage`, hides list | M3: error branch |
| FR-014 | Empty state, no entries | M3: empty branch |
| FR-015 | Render `prompt` + `createdAt` | M3: theme header |
| FR-016 | Read-only nested subtopics/snacks/questions | M3: expandable detail |
| FR-017 | Deep link `/history` renders (no redirect) | M2 (no guard) + M4 deep-link test |
| FR-018 | Keyboard reachable + visible focus | M3: real `<a>`/`<button>` + `:focus-visible` |
| FR-019 | Backend tests: ordering, nested, empty | M1: contract + service + integration |
| FR-020 | Frontend tests: empty + populated | M4: `theme-history.spec.ts` |
| FR-021 | Existing routes/flow pass unchanged | M4: full suite |
| FR-022 | Layered architecture, no provider/schema leak | M0 (layers) + M1 |
| SC-001 | 200 + 100% newest-first | M0 + M1 |
| SC-002 | 100% field coverage all four levels | M0 + M1 |
| SC-003 | 200 `[]`, 0 lazy exceptions | M0 + M1 integration |
| SC-004 | Read-only + DTO present + 0 existing changes | M0 + M1 |
| SC-005 | Route + component + link + model mapping | M2 + M4 |
| SC-006 | Loading/error/empty 100%, 0 blank list | M3 + M4 |
| SC-007 | 100% prompt/createdAt + read-only, 0 generation requests | M3 + M4 |
| SC-008 | Backend coverage + frontend coverage + flow unchanged | M1 + M4 |

## Testing Strategy

### Backend

- **`ThemeServiceTest` (unit, Mockito)** — new cases:
  - `listHistory` returns entries in repository order and preserves nested snacks/questions from the
    batch-loaded maps.
  - `listHistory` returns an empty list when `themeRepository` returns empty and never calls the
    snack/question finders.
  - `listHistory` handles a theme with no subtopics and a subtopic with no snacks/questions (empty
    lists, no error).
- **`ThemeHistoryContractTest` (MockMvc standalone, mocked `ThemeService`)** — mirrors
  `BatchStatusContractTest`:
  - `GET /api/themes` → 200, JSON is an array, fields `themeId/prompt/createdAt/subtopics` and nested
    snack/question fields present.
  - Empty service result → 200 with `[]`.
  - Ordering is asserted by returning pre-ordered entries and checking the serialized order.
- **`ThemeHistoryIntegrationTest` (Testcontainers MySQL, `@Tag("integration")`)** — persists a theme
  with a subtopic + snack + quiz question, calls `themeService.listHistory()`, then **accesses the
  returned nested data after the service transaction has ended** to prove it is initialized (no
  `LazyInitializationException`). Also asserts newest-first ordering with two themes and the empty
  case returning an empty list. Run with `mvn verify -Pintegration`.

### Frontend (Playwright)

- **`frontend/e2e/theme-history.spec.ts`** (new):
  - Header link visible on `/`, click → URL `/history` and `HistoryComponent` renders (FR-011/FR-017).
  - Deep link `page.goto('/history')` renders without redirect (FR-017).
  - Populated: mock two themes newest-first → entries show `prompt` and formatted `createdAt` in
    order; expanding shows subtopics with snacks (`concept/metaphor/whyItMatters/related`) and
    questions (`type/prompt/explanation`) (FR-015/FR-016/SC-007).
  - Empty: mock `[]` → empty-state message, zero entries, no error (FR-014/SC-006).
  - Error: mock HTTP 500 → `ErrorService`-derived message shown, no list (FR-013/SC-006).
  - Loading: delayed mock → loading visible while in flight, gone once settled (FR-012/SC-006).
  - Read-only: assert 0 `POST /api/batches` and 0 `POST /api/snacks/generate` calls from the page
    (FR-008/SC-007).
  - A11y: tab to the header link and a detail control, assert visible focus indicator (FR-018).
- **`frontend/e2e/helpers.ts`** (modified): add `setupThemesHistoryMock(page, themes)` that fulfils
  `GET **/api/themes` with a JSON array (distinct from `**/api/themes/suggest`). Do **not** add it to
  `setupAllMocks` to avoid changing the existing fixture's behavior.
- **Regression**: run the full suite unchanged, especially `happy-path`, `direct-url-access`,
  `restart-clears-state`, `dark-mode`, `back-navigation`, and `logo-visibility/*` (FR-021/SC-008).
- **Type-check**: `npm run build`.

### Commands

```bash
# Backend unit + contract tests (integration excluded by default)
cd backend && mvn test
# Backend integration tests (Docker/Testcontainers)
cd backend && mvn verify -Pintegration
# Frontend build + E2E
cd frontend && npm run build
cd frontend && npx playwright test
# Focused new specs
cd frontend && npx playwright test e2e/theme-history.spec.ts
```

### Explicitly not tested / not changed

- No schema/migration tests (no schema change).
- No auth/per-user scoping, pagination, delete/edit/regenerate, export, or mobile menu (Non-Goals).
- No rich drag-drop/option/correctness rendering in history (Non-Goals).
- No unit test runner is introduced for the frontend.
