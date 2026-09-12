# Feature Specification: Read-Only History of Created Themes

**Feature Branch**: `011-theme-history`

**Created**: 2026-09-11

**Status**: Draft

**Input**: User description: "Read-only history of created themes with snacks and quiz questions — Add a read-only history view of every created theme. Backend `GET /api/themes` returns all themes (newest first) with their subtopics, snacks, and quiz questions; frontend adds a `/history` page and header link to browse it." (Batch backlog Task 10, from item 11.)

## Context & Scope *(established — do not re-investigate)*

This is a **read-only, end-to-end (backend + frontend) history feature**. It adds one new read endpoint and one new page; it does **not** change how themes, subtopics, snacks, or quizzes are generated, and it does **not** change any existing endpoint's behavior.

The following current code state is treated as confirmed input and is referenced, not re-designed:

**Backend**
- `ThemeController` is `@RestController @RequestMapping("/api/themes")` and currently exposes only `POST /suggest` (`ThemeController.suggest`) returning `ThemeSuggestApiResponse`.
- `ThemeService` (`com.endia.application.service.ThemeService`) currently exposes `suggestSubtopics(String prompt)` (`@Transactional`) and returns a `ThemeSuggestResult(UUID themeId, List<Subtopic> subtopics)`.
- Domain models and their real fields:
  - `Theme`: `id` (UUID, mapped from `@GeneratedValue`), `prompt` (String, 500), `createdAt` (`LocalDateTime`, set by `@PrePersist`, column `created_at`, `nullable = false`, `updatable = false`), `subtopics` (`@OneToMany(mappedBy = "theme", fetch = LAZY)`).
  - `Subtopic`: `id` (UUID), `theme` (`@ManyToOne LAZY`), `name` (String, 200), `selected` (boolean), `source` (`SubtopicSource`), `snacks` (`@OneToMany(mappedBy = "subtopic", fetch = LAZY)`), `quizQuestions` (`@OneToMany(mappedBy = "subtopic", fetch = LAZY)`).
  - `Snack`: `id` (UUID), `subtopic` (`@ManyToOne LAZY`), `concept`, `metaphor`, `whyItMatters` (column `why_it_matters`), `related` (`List<String>`, JSON).
  - `QuizQuestion`: `id` (UUID), `subtopic` (`@ManyToOne LAZY`), `type` (`QuestionType` = `MULTIPLE_CHOICE` | `DRAG_DROP`), `prompt`, `options` (`List<String>`, JSON), `correctIndex` (nullable), `variant` (`DragDropVariant` = `SEQUENCING` | `CLASSIFICATION` | `MATCHING`), `dragDropContent` (`DragDropContent`, JSON, nullable), `explanation`, `answerAttempts` (LAZY).
- Repositories `JpaThemeRepository` (`JpaRepository<Theme, UUID>`) and `JpaSubtopicRepository` (`JpaRepository<Subtopic, UUID>`, `findByThemeId(UUID)`) exist; `JpaThemeRepository` currently declares no finder methods.
- Response DTO conventions: records under `com.endia.interfaces.rest.dto` with nested records and static mapping in the controller (e.g. `ThemeSuggestApiResponse`, `SnackGenerateApiResponse`).
- Existing endpoints that MUST keep current behavior: `POST /api/themes/suggest` (`ThemeController.suggest`), `POST /api/themes/{themeId}/subtopics` (`SubtopicController`), `POST /api/snacks/generate` (`SnackController.generate`), `/api/batches` (`BatchController`), and the quiz endpoints (`QuizController`).

**Frontend**
- `frontend/src/app/app.routes.ts` defines lazy-loaded routes for `''` (`HomeComponent`), `subtopics` (`SubtopicsComponent`), `snacks` (`SnacksComponent`), `quiz` (`QuizComponent`), `results` (`ResultsComponent`), and a `**` redirect to `''`.
- `frontend/src/app/app.html` renders `.app-shell__header` containing the `.brand` link to `/` and the `.theme-toggle` button; there is currently **no** navigation link.
- `frontend/src/app/services/theme.service.ts` (`ThemeService`, `baseUrl = '/api/themes'`) currently exposes `suggest(prompt)` and `addSubtopics(themeId, names)`.
- `frontend/src/app/services/error.service.ts` (`ErrorService.getMessage(error: unknown): string`) is the single error-presentation path and maps HTTP/`cause` values to user-facing messages.
- Frontend models: `models/theme.model.ts` (`Subtopic`, `ThemeSuggestResponse`, `SubtopicAddResponse`), `models/snack.model.ts` (`Snack`), `models/quiz.model.ts` (`QuestionType`, `DragDropVariant`, `QuizQuestion`).
- Page components are Angular 21 standalone components using `signals` + `ChangeDetectionStrategy.OnPush`, with `loading`/`errorMessage` signals and `ErrorService` (e.g. `HomeComponent`, `ResultsComponent`).
- E2E suite under `frontend/e2e/` protects the existing flow (e.g. `happy-path.spec.ts`, `direct-url-access.spec.ts`, `logo-visibility/*`, `dark-mode.spec.ts`, `back-navigation.spec.ts`, `restart-clears-state.spec.ts`).

This feature adds (1) a read-only `GET /api/themes` history endpoint that returns every theme newest-first with its subtopics, snacks, and quiz questions, and (2) a `/history` page reachable from a header link, with loading/error/empty states and read-only nested detail. It does **not** add authentication, per-user scoping, pagination, deletion, editing, regeneration, or any change to the generation flow.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Browse every created theme from a History page (Priority: P1)

A user can open a "History" link in the app header and see a read-only list of every theme ever created, newest first. Each entry shows the theme's original prompt and when it was created. The user can navigate to `/history` directly (deep link) without having gone through the create flow.

**Why this priority**: This is the reported gap — created themes are currently unreachable once the session moves on. A browsable, newest-first history is the core value and is independently testable.

**Independent Test**: Open the app, click the "History" link, and confirm the page lists themes with `prompt` and `createdAt` ordered newest-first, and that navigating directly to `/history` renders the page rather than redirecting. Delivers the value of "I can find the themes I created."

**Acceptance Scenarios**:

1. **Given** the app header is rendered, **When** it is inspected, **Then** a visible "History" link exists in `.app-shell__header` that navigates to `/history`.
2. **Given** the user activates the "History" link, **When** navigation completes, **Then** the URL is `/history` and `HistoryComponent` renders.
3. **Given** the user navigates directly to `/history` with no prior app state, **When** the page loads, **Then** it renders the history page (it does not redirect away, unlike `/quiz` and `/results`).
4. **Given** the backend has themes, **When** the history page loads, **Then** each theme entry renders its `prompt` and `createdAt`.
5. **Given** the backend has multiple themes, **When** the history page loads, **Then** themes are presented newest-first (`createdAt` descending).

---

### User Story 2 — Inspect a theme's subtopics, snacks, and quiz questions (Priority: P1)

From a theme in the history list, the user can view its subtopics and, for each subtopic, the knowledge snacks and quiz questions that were generated. The view is strictly read-only: it never generates, edits, deletes, or re-quizzes.

**Why this priority**: The history is only useful if it exposes the actual learning content (snacks and questions), not just theme titles. It is independently testable by expanding a theme and inspecting its nested content.

**Independent Test**: With a theme that has subtopics, snacks, and quiz questions, open its history entry and confirm each subtopic lists its snacks (`concept`, `metaphor`, `whyItMatters`, `related`) and its quiz questions (`type`, `prompt`, `explanation`), with no generation request issued. Delivers the value of "I can revisit what I learned."

**Acceptance Scenarios**:

1. **Given** a theme entry is shown, **When** the user views its subtopics, **Then** each subtopic renders its `name`.
2. **Given** a subtopic has snacks, **When** the subtopic is viewed, **Then** each snack renders `concept`, `metaphor`, `whyItMatters`, and `related`.
3. **Given** a subtopic has quiz questions, **When** the subtopic is viewed, **Then** each question renders its `type`, `prompt`, and `explanation`.
4. **Given** the history page is open, **When** the user interacts with it, **Then** no generation, edit, delete, or re-quiz request is issued (read-only).
5. **Given** a theme has a subtopic with no snacks or no quiz questions, **When** the subtopic is viewed, **Then** it renders without error and shows an appropriate "nothing generated yet" indication for the missing content.

---

### User Story 3 — Clear loading, error, and empty states (Priority: P2)

While the history is loading, the page shows a loading state. If loading fails, the page shows a message derived from `ErrorService`. If there are no themes yet, the page shows an empty-state message instead of a broken or blank list.

**Why this priority**: The states make the page trustworthy and prevent a blank screen from being mistaken for "no history". P2 because the core value is delivered by US1/US2.

**Independent Test**: Load the page against a slow/empty/failing backend and confirm the loading, empty, and error states render respectively, with the error text coming from `ErrorService`. Delivers the value of "the page always tells me what is happening."

**Acceptance Scenarios**:

1. **Given** the request is in flight, **When** the page renders, **Then** a loading state is visible.
2. **Given** the request fails, **When** the error is received, **Then** the page shows the message returned by `ErrorService.getMessage(error)` and no list.
3. **Given** the backend returns an empty array, **When** the page renders, **Then** an empty-state message is shown and no theme entries are rendered.
4. **Given** the request has completed (success or error), **When** the page settles, **Then** the loading state is no longer shown.

---

### User Story 4 — Read-only, accessible, and non-regressive (Priority: P2)

The history is a safe, read-only view that does not change any data, is reachable and usable by keyboard, and does not regress the existing learning flow or header behavior.

**Why this priority**: Read-only integrity and non-regression protect the rest of the app, and accessibility makes the new page usable. P2 because it hardens the feature after the core value.

**Independent Test**: Confirm the history request is a read-only transaction, that the existing home→subtopics→snacks→quiz→results flow still passes, and that the header link and theme detail controls are keyboard reachable with a visible focus indicator. Delivers the value of "the history is safe and does not break what worked."

**Acceptance Scenarios**:

1. **Given** the history endpoint is invoked, **When** it runs, **Then** it is a read-only transaction and performs no writes.
2. **Given** the "History" link and the theme detail controls are rendered, **When** the user navigates by keyboard, **Then** they are reachable and activatable with a visible focus indicator.
3. **Given** the history feature is added, **When** the existing E2E suites run, **Then** the home→subtopics→snacks→quiz→results flow and existing routes still pass unchanged.
4. **Given** the header now contains an extra link, **When** the header/brand visibility and dark-mode suites run, **Then** they still pass.

---

### Edge Cases

- **No themes exist**: `GET /api/themes` returns HTTP 200 with `[]`; the frontend shows the empty-state message and no entries (no error).
- **Theme with no subtopics**: the theme entry renders `prompt`/`createdAt` and indicates no subtopics; no error.
- **Subtopic with no snacks**: the subtopic renders without a snack list and indicates nothing generated; no error.
- **Subtopic with no quiz questions**: the subtopic renders without a question list and indicates nothing generated; no error.
- **Theme with multiple subtopics**: all subtopics are included, each with their own snacks and quiz questions.
- **Large history (many themes / many nested items)**: the response is returned and rendered without crashing; the page remains usable. (Full-history, no pagination, is the specified contract.)
- **`createdAt` values equal or very close**: ordering by `createdAt` descending is applied; ties are acceptable and do not break the view.
- **Deep link to `/history` with no prior state**: renders the page (no redirect), unlike `/quiz` and `/results`.
- **Backend failure on history load**: the page shows the `ErrorService` message; no unhandled exception and no infinite loading.
- **Lazy associations**: building the history response must not raise `LazyInitializationException`; nested collections must be safely initialized before the transaction ends.
- **No sensitive data**: history contains user-entered prompts and generated learning content only; no API keys, provider payloads, or credentials.
- **Concurrent creation while viewing history**: the page shows a read-only snapshot from load time; it is not required to update live.

## Requirements *(mandatory)*

### Functional Requirements

**Backend — history endpoint**

- **FR-001**: `ThemeController` MUST expose `GET /api/themes` returning HTTP 200 with a JSON array of all themes ordered by `createdAt` descending; each array entry MUST contain `themeId`, `prompt`, `createdAt`, and `subtopics`.
- **FR-002**: Each `subtopics` entry MUST contain `id`, `name`, `snacks`, and `quizQuestions`.
- **FR-003**: Each snack entry MUST contain `id`, `concept`, `metaphor`, `whyItMatters`, and `related`.
- **FR-004**: Each quiz-question entry MUST contain `id`, `type`, `prompt`, and `explanation`.
- **FR-005**: `ThemeService.listHistory()` MUST be annotated `@Transactional(readOnly = true)`, MUST return an empty array with HTTP 200 when no themes exist, and MUST NOT raise `LazyInitializationException` while assembling nested data.
- **FR-006**: A new response DTO `ThemeHistoryApiResponse` MUST be added under `interfaces/rest/dto` (i.e. `com.endia.interfaces.rest.dto`), following the existing record-with-nested-records convention.
- **FR-007**: The new endpoint and service method MUST NOT change the behavior of existing endpoints: `POST /api/themes/suggest`, `POST /api/themes/{themeId}/subtopics`, `POST /api/snacks/generate`, `/api/batches`, and the quiz endpoints MUST keep their current contracts and behavior.
- **FR-008**: The history endpoint MUST be read-only: it MUST NOT create, update, delete, or regenerate any theme, subtopic, snack, or quiz question.

**Frontend — history page and header link**

- **FR-009**: `frontend/src/app/services/theme.service.ts` (`ThemeService`) MUST expose `listHistory()` that calls `GET /api/themes` and maps the response into a `ThemeHistory` model.
- **FR-010**: A lazy-loaded `/history` route MUST be added to `frontend/src/app/app.routes.ts`, loading a `HistoryComponent`.
- **FR-011**: `frontend/src/app/app.html` MUST render a visible "History" link in `.app-shell__header` that navigates to `/history`.
- **FR-012**: `HistoryComponent` MUST render a loading state while the request is in flight and clear it once the request settles (success or error).
- **FR-013**: On failure, `HistoryComponent` MUST show the message produced by `ErrorService.getMessage(error)` and MUST NOT show the theme list.
- **FR-014**: When the backend returns no themes, `HistoryComponent` MUST show an empty-state message and MUST NOT show theme entries.
- **FR-015**: For each theme, `HistoryComponent` MUST render the theme's `prompt` and `createdAt`.
- **FR-016**: For each theme, the user MUST be able to view its subtopics with their snacks and quiz questions, rendering the fields required by FR-002–FR-004, in a read-only manner (no generation, edit, delete, or re-quiz).
- **FR-017**: The `/history` route MUST render when accessed directly (deep link) with no prior app state; it MUST NOT redirect away.
- **FR-018**: The "History" link and the theme-detail controls MUST be keyboard reachable and activatable with a visible focus indicator.

**Testing and non-regression**

- **FR-019**: Tests MUST cover the backend history endpoint for (a) ordering newest-first, (b) nested subtopic/snack/quiz data, and (c) the empty case returning HTTP 200 with `[]`.
- **FR-020**: Tests MUST cover the frontend history page for the empty state and the populated state.
- **FR-021**: The existing routes and the home→subtopics→snacks→quiz→results flow MUST still pass after this feature.
- **FR-022**: The feature MUST respect the layered architecture: domain models unchanged, read logic in `ThemeService`, mapping in `ThemeController`, and the response DTO in `interfaces/rest/dto`. No provider/LLM logic may leak into the controller or service, and no schema/migration changes are introduced.

### Key Entities *(include if feature involves data)*

- **Theme** (existing): A created study topic. History-relevant attributes: `id` (exposed as `themeId`), `prompt`, `createdAt`, and its `subtopics`. Ordered by `createdAt` descending.
- **Subtopic** (existing): A sub-topic of a theme. History-relevant attributes: `id`, `name`, and its `snacks` and `quizQuestions`.
- **Snack** (existing): A knowledge card for a subtopic. History-relevant attributes: `id`, `concept`, `metaphor`, `whyItMatters`, `related`.
- **QuizQuestion** (existing): A generated question for a subtopic. History-relevant attributes: `id`, `type` (`MULTIPLE_CHOICE` | `DRAG_DROP`), `prompt`, `explanation`. (Options, correct index, and drag-drop payloads are not part of the history contract.)
- **ThemeHistoryApiResponse** (new, response DTO): The nested record returned by `GET /api/themes` — a list of theme entries, each with `themeId`, `prompt`, `createdAt`, and `subtopics`; subtopics with `id`, `name`, `snacks`, `quizQuestions`; snacks and questions per FR-003/FR-004.
- **ThemeHistory** (new, frontend model): The frontend type mirroring the endpoint payload, mapped by `ThemeService.listHistory()` and consumed by `HistoryComponent`.
- **History page state** (UI concept): The loading / error / empty / populated states of `HistoryComponent`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `GET /api/themes` returns HTTP 200 with a JSON array in 100% of calls, and 100% of returned themes are ordered by `createdAt` descending.
- **SC-002**: 100% of theme entries expose `themeId`, `prompt`, `createdAt`, and `subtopics`; 100% of subtopics expose `id`, `name`, `snacks`, `quizQuestions`; 100% of snacks expose `id`, `concept`, `metaphor`, `whyItMatters`, `related`; 100% of questions expose `id`, `type`, `prompt`, `explanation`.
- **SC-003**: When no themes exist, `GET /api/themes` returns HTTP 200 with `[]` in 100% of calls, and 0 `LazyInitializationException`s occur while assembling history data.
- **SC-004**: `ThemeService.listHistory()` is a read-only transaction, and `ThemeHistoryApiResponse` exists under `interfaces/rest/dto`; 0 existing endpoints (`POST /api/themes/suggest`, `POST /api/snacks/generate`, `/api/batches`, quiz) change their behavior.
- **SC-005**: The `/history` route and the `HistoryComponent` are registered, and the "History" header link navigates to `/history` in 100% of attempts; `ThemeService.listHistory()` maps the payload into a `ThemeHistory` model.
- **SC-006**: The history page shows a loading state while loading, an `ErrorService`-derived message on failure, and an empty-state message when there are no themes, in 100% of tested cases; 0 cases show a blank list on error/empty.
- **SC-007**: 100% of rendered theme entries show `prompt` and `createdAt`, and 100% of rendered subtopics expose their snacks and quiz questions read-only; 0 generation/edit/delete/re-quiz requests are issued from the history page.
- **SC-008**: Backend tests cover ordering, nested data, and the empty case; frontend tests cover the empty and populated states; the existing routes and the home→subtopics→snacks→quiz→results flow pass unchanged.

## Non-Goals

- **No authentication or per-user scoping**: all created themes are visible in one global history (the MVP remains anonymous).
- **No delete, edit, rename, or regenerate** from the history view.
- **No pagination, filtering, sorting options, or search**: the endpoint returns all themes (newest-first) and the page lists them.
- **No changes to generation** (prompts, providers, batch/quiz/snack logic) and **no changes to existing endpoint contracts**.
- **No persistence/schema/migration changes**: history is derived from existing entities.
- **No rich rendering of drag-drop payloads, options, or answer correctness** in history: only the fields in FR-003/FR-004 are required.
- **No export, sharing, or print view.**
- **No live/streaming updates** while the page is open.
- **No responsive/mobile navigation redesign**: the header link is required, but a mobile menu is not.

## Assumptions

- The MVP has no authentication, so history is a single global list; no `userId` filtering is needed. (Product decision to confirm — see Open Questions.)
- `Theme.createdAt` is always populated by the existing `@PrePersist` hook, so ordering by `createdAt` descending is well-defined; ties are acceptable.
- The endpoint returns the complete history (no pagination) as specified by the acceptance criteria; current MVP data volumes are small enough for this to be acceptable.
- Nested lazy collections are initialized within the read-only transaction (for example via fetch joins or by mapping inside the service transaction); the observable requirement is "no `LazyInitializationException`".
- `createdAt` is serialized as an ISO-8601 date-time string; the frontend formats it for display in the user's local locale/timezone while preserving the underlying value.
- The frontend reuses the existing `ErrorService.getMessage` path and the standalone-signals/`OnPush` component pattern used by the other pages.
- "Read-only" means no mutation of persisted data; expanding/collapsing theme details is purely client-side UI state.
- The existing E2E suites (home flow, direct URL access, header/logo visibility, dark mode, navigation) remain the regression contract; adding a header link must not break their selectors.
- No new external service, dependency, or configuration property is required.

## Open Questions *(for the orchestrator to confirm with the user; non-blocking — defaults chosen above)*

1. **Global vs per-user history**: with no auth in the MVP, the history is global. Confirm the user does not expect per-user isolation in this pass.
2. **`createdAt` display format/timezone**: the backend sends ISO-8601; the frontend formats it locally. Confirm a preferred format (e.g. `2026-09-11 19:52`) if the user cares.
3. **Unbounded history**: full history is returned with no pagination/limit. Confirm this is acceptable for expected volumes or whether a cap should be added later.
4. **Header placement/mobile**: the "History" link sits in `.app-shell__header` next to the theme toggle. Confirm no mobile menu treatment is required this pass.

No blocking `[NEEDS CLARIFICATION]` markers remain: each open question has an informed default encoded in the requirements/assumptions and can be revised during planning without changing the core scope.
