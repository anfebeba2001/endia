# Research: Read-Only History of Created Themes

**Phase 0 output** for `/specs/011-theme-history`. Each section records the Decision, Rationale, and
Alternatives, grounded in the real codebase (`backend/src/main/java/com/endia/**`,
`frontend/src/app/**`, `frontend/e2e/**`).

This is a small, read-only, end-to-end feature. The governing constraints are: no schema change, no
change to existing endpoints or generation, layered architecture, and the existing Playwright
regression contract.

---

## R1. How to load the nested history without `LazyInitializationException` or N+1

**Decision**: In `ThemeService.listHistory()` (`@Transactional(readOnly = true)`):

1. `themeRepository.findAllByOrderByCreatedAtDesc()` annotated
   `@EntityGraph(attributePaths = "subtopics")` — one query, initializes the (single) `subtopics` bag.
2. Collect all `Subtopic.id`s.
3. `snackRepository.findBySubtopicIdIn(ids)` and `quizQuestionRepository.findBySubtopicIdIn(ids)` —
   one batch query each.
4. Group by the child's `subtopic` id and assemble a `ThemeService.ThemeHistory` read model inside the
   transaction.

**Rationale**:
- `Theme.subtopics`, `Subtopic.snacks`, and `Subtopic.quizQuestions` are all `List` bags. Hibernate
  cannot `JOIN FETCH` two bags simultaneously (`MultipleBagFetchException`) and joining all three
  would multiply rows. A single `@EntityGraph` spanning both `subtopics.snacks` and
  `subtopics.quizQuestions` has the same failure mode (verified against Hibernate's
  `MultipleBagFetchException` contract and Spring Data JPA issue #4284).
- Fetching exactly **one** bag via `@EntityGraph` is safe and is already the project's convention
  (`JpaStudySessionItemRepository.findBySessionId` uses `@EntityGraph(attributePaths = {"subtopic"})`).
- The two `findBySubtopicIdIn` queries keep the total query count fixed regardless of how many themes
  or subtopics exist — no N+1.
- Accessing `snack.getSubtopic().getId()` / `question.getSubtopic().getId()` reads a lazy proxy's
  identifier, which Hibernate serves without initializing the association, so grouping adds no
  queries.
- Materializing a read model inside the transaction means the controller maps plain records after the
  transaction closes — no lazy proxy can be touched.

**Alternatives considered**:
- **Single 3-level `JOIN FETCH`** — rejected: `MultipleBagFetchException` / cartesian product.
- **Lazy-load per subtopic in the transaction** — works and is explicitly permitted by the spec's
  Assumptions, but is N+1; rejected because the task requires avoiding N+1.
- **Rely on OSIV** (`spring.jpa.open-in-view` defaults to `true`, and is not set in
  `application.yml`) — rejected: it would mask a missing initialization and break if OSIV is disabled.
- **Mutate entities (`subtopic.setSnacks(...)`) and return `List<Theme>`** — rejected: mutation of
  managed entities plus `orphanRemoval` is fragile; a read model is cleaner and layering-safe.

---

## R2. Where the DTO mapping happens (layering)

**Decision**: The service returns `List<ThemeService.ThemeHistory>` (an application-layer read model);
`ThemeController.getHistory()` maps each `ThemeHistory` to the new
`ThemeHistoryApiResponse` and returns `ResponseEntity<List<ThemeHistoryApiResponse>>`.

**Rationale**:
- Constitution Principle II: the application layer must not depend on the interfaces layer. If
  `ThemeService` returned `ThemeHistoryApiResponse` it would import `interfaces/rest/dto`, violating
  the dependency rule.
- The spec's FR-022 requires "read logic in `ThemeService`, mapping in `ThemeController`, and the
  response DTO in `interfaces/rest/dto`" — this design matches exactly.
- `ThemeService` already uses nested result records (`ThemeSuggestResult`), so the read model follows
  an established convention.
- FR-001 requires the HTTP body to be a JSON **array**; returning `List<ThemeHistoryApiResponse>`
  yields the array directly, with no wrapper object.

**Alternatives considered**:
- **Service returns the API DTO** — rejected: breaks the layered dependency rule.
- **Controller does the nested assembly** — rejected: it would run after the service transaction
  closes (lazy access), and it moves read logic out of the service (violates FR-022).
- **Repository returns DTO projections** — rejected: infrastructure must not know interface DTOs;
  also awkward for nested collections.

---

## R3. Route and guard conventions for `/history`

**Decision**: Add a lazy-loaded route in `app.routes.ts`:
`{ path: 'history', loadComponent: () => import('./pages/history/history.component').then(m => m.HistoryComponent), title: 'Endia — History' }`,
placed before the `**` wildcard. Add **no** route guard and **no** redirect.

**Rationale**:
- Every existing route uses the same `loadComponent` lazy pattern with a `title`; matching it keeps
  the bundle split consistent.
- FR-017/Edge Cases require `/history` to render on a cold deep link. `/quiz` and `/results`
  self-redirect inside their components when state is missing; `/history` must not, because it is
  server-backed and stateless.
- The `**` wildcard must stay last so `/history` is matched before it.
- No `canActivate` guard is needed: the page has no dependency on `AppStateService`.

**Alternatives considered**:
- **Eager component import** — rejected: inconsistent with the other routes and needlessly inflates
  the initial bundle.
- **Reuse `/results` or a query param** — rejected: the spec requires a distinct `/history` page.
- **Add an auth/state guard** — rejected: Non-Goals (no auth); the endpoint is global.

---

## R4. Header link placement and non-regression

**Decision**: In `app.html`, wrap the existing `.theme-toggle` and the new History link in a
`<nav class="app-shell__nav" aria-label="Primary">` container:
`<a class="header-link" routerLink="/history">History</a>` + the existing toggle button. Add
`.app-shell__nav { display:flex; align-items:center; gap:.75rem; }` and `.header-link` styles (with
`:focus-visible`) to `app.scss`.

**Rationale**:
- `.app-shell__header` uses `justify-content: space-between`. Adding a third direct child would push
  the brand and the actions apart; grouping the actions in a nav keeps the brand left and the actions
  right.
- The logo-visibility suite reads only `.app-shell__header`, `.brand__name`, `.brand__dot`, and
  `.theme-toggle` (see `frontend/e2e/logo-visibility/styles.ts`), all of which remain untouched, so
  `light-mode-header-and-logo`, `dark-mode-header-and-logo`, `header-adjacent-elements-visible`, and
  `toggle-back-to-light-restores` stay green (FR-021/SC-008).
- `App` already imports `RouterLink`, so the link needs no new import.
- A real `<a>` gives keyboard reachability, an accessible name, and native Enter activation for free
  (FR-018).

**Alternatives considered**:
- **A third direct header child** — rejected: breaks the visual spacing and could shift the
  header/brand geometry the visibility tests assert.
- **A button that calls `router.navigate`** — rejected: a link is the semantically correct control
  for navigation and is more accessible.
- **A mobile hamburger menu** — rejected: Non-Goals (no responsive/mobile navigation redesign).

---

## R5. Timestamp formatting

**Decision**: Serialize `createdAt` as the default Jackson ISO-8601 string (no `@JsonFormat`); the
frontend keeps the raw string in `ThemeHistory.createdAt` and formats it for display with
`Intl.DateTimeFormat`/`Date` in the browser locale.

**Rationale**:
- Spring Boot's default Jackson configuration includes Java Time support and writes `LocalDateTime` as
  ISO-8601 (`2026-09-11T19:52:00`), matching the spec Assumption ("ISO-8601 date-time string").
- Keeping the raw value in the model and formatting at the view preserves the underlying value and
  respects the user's locale/timezone (spec Assumption).
- No backend annotation or configuration is needed, keeping the change minimal.

**Alternatives considered**:
- **`@JsonFormat` on the DTO** — rejected: unnecessary; the default already emits ISO-8601, and a
  fixed pattern would fight the "format locally" assumption.
- **Format on the backend** — rejected: it would bake in a timezone/format the spec leaves to the
  client (Open Question 2).
- **`DatePipe` with a fixed format** — acceptable, but the spec leaves the exact format open; a
  locale-aware formatter is the safer default and can be adjusted during implementation.

---

## R6. Error, empty, and loading states

**Decision**: `HistoryComponent` uses three signals — `loading`, `errorMessage`, `themes` — and a
single template branch chain: loading → error → empty → populated. Errors are rendered from
`ErrorService.getMessage(error)` (FR-013); an empty array renders an empty-state message (FR-014);
`loading` is cleared in both `next` and `error` callbacks (FR-012).

**Rationale**:
- This mirrors `HomeComponent`/`ResultsComponent`, which already use `loading` + `errorMessage`
  signals and `ErrorService.getMessage`, so the pattern and copy style stay consistent.
- A single branch chain guarantees the states are mutually exclusive: the list can never render
  alongside an error or the empty message (SC-006), and loading cannot remain visible after the
  request settles.
- Distinguishing "empty" (`[]`, HTTP 200) from "error" (non-2xx) is exactly the spec's Edge Cases and
  FR-013/FR-014.

**Alternatives considered**:
- **Reuse `AppStateService` for history** — rejected: history is server-backed and stateless; sharing
  session state would couple the read-only page to the generation flow and risk clearing it.
- **A resolver to preload data** — rejected: more moving parts for no benefit; the component can load
  on construction like the other pages.

---

## R7. Read-only integrity and nested rendering

**Decision**: The endpoint performs zero writes (`@Transactional(readOnly = true)`, only finder
calls). The history page issues exactly one HTTP request (`GET /api/themes`) and renders nested
content by client-side expansion; it has no generate/edit/delete/re-quiz controls and calls no batch,
snack, or quiz service.

**Rationale**:
- FR-008/FR-022 require no mutation; a read-only transaction plus finder-only code enforces it at the
  backend, and the frontend has no mutation code path.
- FR-016/SC-007 require 0 generation/edit/delete/re-quiz requests; the E2E spec asserts that no
  `POST /api/batches` or `POST /api/snacks/generate` fires while on `/history`.
- Expansion is pure UI state (`expanded` signal), which the spec explicitly calls "purely client-side
  UI state" (Assumptions).

**Alternatives considered**:
- **Re-fetch per expanded theme** — rejected: adds endpoints/queries and contradicts "read-only
  snapshot from load time" (Edge Cases).
- **Deep-link to the existing snack/quiz pages** — rejected: they are stateful and would require
  regeneration; Non-Goals forbid generation from history.

---

## R8. Test surface and non-regression

**Decision**:
- Backend: extend `ThemeServiceTest` (unit), add `ThemeHistoryContractTest` (MockMvc standalone,
  mirrors `BatchStatusContractTest`), and add `ThemeHistoryIntegrationTest` (Testcontainers MySQL,
  `@Tag("integration")`) that asserts ordering, nested content, empty case, and — critically — that
  nested data is accessible **after the service transaction ends** (proving no lazy leak).
- Frontend: add `frontend/e2e/theme-history.spec.ts` and a `setupThemesHistoryMock` helper; run the
  full existing Playwright suite unchanged.

**Rationale**:
- The project's backend conventions are Mockito unit tests plus focused MockMvc contract tests; the
  Testcontainers integration test matches `ThemeFlowIntegrationTest` and is the only way to exercise
  the real bag-fetch/initialization behavior against MySQL.
- Playwright is the established frontend test surface (no Karma/Jest runner). The header link,
  routing, states, and read-only guarantee are all end-to-end concerns.
- `setupThemesHistoryMock` must **not** be added to `setupAllMocks`: doing so would change the shared
  fixture used by every existing spec and risk unintended interception. The new spec installs it
  explicitly. (The route `**/api/themes` does not match `**/api/themes/suggest`, but isolation is
  safer.)

**Alternatives considered**:
- **Only a MockMvc contract test** — rejected: it would not prove the no-lazy/no-N+1 behavior against
  a real database.
- **Only an integration test** — rejected: a fast contract test pins the JSON shape and the `[]` case.
- **Add a frontend unit runner** — rejected: extra infrastructure for little gain; the behavior is
  observable through the UI.

---

## Cross-cutting: what is NOT changed

- No new endpoint other than `GET /api/themes`; no change to `POST /api/themes/suggest`,
  `POST /api/themes/{themeId}/subtopics`, `POST /api/snacks/generate`, `/api/batches`, or the quiz
  endpoints (FR-007).
- No entity, enum, converter, or schema change (FR-022).
- No LLM/provider/prompt change; history never calls the LLM.
- No auth, per-user scoping, pagination, delete/edit/regenerate, export, or mobile menu (Non-Goals).
- No new npm/Maven dependency; no new Angular service or shared state.
