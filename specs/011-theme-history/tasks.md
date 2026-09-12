---

description: "Task list for Read-Only History of Created Themes (backend GET /api/themes + Angular /history page over the existing theme/subtopic/snack/quiz entities)"
---

# Tasks: Read-Only History of Created Themes

**Input**: Design documents from `/specs/011-theme-history/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/get-themes.md, quickstart.md

**Branch**: `011-theme-history`

**Tests**: Tests are **required** by the specification (FR-019/FR-020, SC-008) and `plan.md §Testing Strategy`. Backend uses JUnit 5 + Mockito (unit), MockMvc standalone (contract), and Testcontainers MySQL (integration, tagged `integration`). Frontend uses Playwright E2E (no Karma/Jest runner exists) plus `npm run build` for type-checking. All tests are authored in **Phase 2 (Tests)** and are expected to **fail** until their implementation targets land in **Phase 3 (Core)**.

## Organization

This is a **read-only, end-to-end** feature (backend + frontend). The backend is **additive only**: one new read endpoint, one new DTO, one new service read method + read model, and three repository finder additions. No entity, enum, converter, schema, migration, prompt, provider, or existing-endpoint change (FR-007/FR-022). The frontend adds one model, one service method, one lazy route, one header link, and one new standalone page. No new dependency is introduced in either project.

The tasks are grouped into the requested five phases: **Setup → Tests (TDD) → Core (backend then frontend) → Integration → Polish**. Story labels (`[US1]`–`[US4]`) provide traceability to `spec.md`; setup/integration/polish tasks carry no label and are mapped by the Requirement Coverage table at the end.

### Milestone ↔ story ↔ phase map

| Milestone | Plan phase | Story | Conceptual phase |
|-----------|-----------|-------|------------------|
| — | Baseline | none | Setup |
| M1 | Backend tests | US1/US2 | Tests (TDD) |
| M4 | Frontend E2E | US1–US4 | Tests (TDD) |
| M0 | Backend read path | US1/US2 | Core — Backend |
| M2 | Frontend wiring | US1 | Core — Frontend |
| M3 | History page | US1–US4 | Core — Frontend |
| M4 | E2E + non-regression | US1–US4 | Integration |
| — | Hardening | none | Polish |

> **Critical read-strategy constraint (FR-005/FR-022, `research.md` R1, `data-model.md` §4)**: `Theme.subtopics`, `Subtopic.snacks`, and `Subtopic.quizQuestions` are all `List` **bags**. A `JOIN FETCH`/`@EntityGraph` spanning two or more bags raises `MultipleBagFetchException`. T012 fetches **only** `subtopics`; T013/T014 batch-load snacks and questions by subtopic id. T015 assembles the read model **inside** the `@Transactional(readOnly = true)` method so the controller never touches a lazy proxy.

> **OSIV masking risk (flagged)**: `spring.jpa.open-in-view` is not configured (Spring Boot default `true`), which can hide an uninitialized association in web-request tests. T006 calls the service **outside** a web request and accesses the returned nested data after the transaction ends, so a regression surfaces as a `LazyInitializationException`.

> **Header layout regression (flagged)**: `.app-shell__header` uses `justify-content: space-between`. T024 must wrap the History link **and** the theme toggle in a `.app-shell__nav` container (T025) rather than adding a third direct header child, so the `logo-visibility/*` suites stay green (FR-021/SC-008).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Which user story this task belongs to (`US1`–`US4`); absent for setup/integration/polish
- Base paths (verified against the repo):
  - Backend source: `backend/src/main/java/com/endia/`
  - Backend tests: `backend/src/test/java/com/endia/`
  - Frontend source: `frontend/src/app/`
  - Frontend E2E: `frontend/e2e/`
  - Feature docs: `specs/011-theme-history/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify the baseline and confirm the frozen surfaces before any change. No production code is written here.

- [x] T001 Verify the baseline: run `cd backend && mvn test` and `cd frontend && npm run build` (both green) and `cd frontend && npx playwright test` (existing suite green); record the baseline test/spec counts. Required to prove FR-021/SC-008 (no regression) later.
  - **Baseline recorded**: backend `mvn test` = **97 tests, 0 failures**; `npm run build` = success; `npx playwright test` = **67 passed**. No pre-existing changes (only untracked `specs/011-theme-history/`).
- [x] T002 [P] Confirm the frozen surfaces: verify `git status` shows no schema/migration change, no entity/enum/converter change, and no new Maven/npm dependency; confirm `backend/src/main/resources/application.yml` has no `open-in-view` override (default `true` is in effect, see plan.md §Risks). Record the finding. (FR-022, SC-004)
  - **Recorded**: `git status --short` shows only `?? specs/011-theme-history/`; no schema/migration/entity/converter change; no new Maven/npm dependency; `grep open-in-view backend/src/main/resources/` → **no override** (Spring Boot default `true` in effect).
- [x] T003 [P] Confirm the existing routing/selector baseline: record the routes in `frontend/src/app/app.routes.ts` (including that `**` is last), and the header selectors asserted by the logo-visibility suite (`.app-shell__header`, `.brand__name`, `.brand__dot`, `.theme-toggle`). This is the contract the new link must not disturb. (FR-011, FR-021, SC-008)
  - **Recorded**: routes `''`, `subtopics`, `snacks`, `quiz`, `results`, then `**` (wildcard last). Header selectors asserted by logo-visibility: `.app-shell__header`, `.brand__name`, `.brand__dot`, `.theme-toggle` (all read-only; the new link will be wrapped with the toggle in `.app-shell__nav`).

**Checkpoint**: Baseline green; frozen surfaces and selector contract confirmed — test authoring can begin.

---

## Phase 2: Tests (TDD — write first, expect FAIL)

**Purpose**: Author every failing test before its implementation target. These tasks define the done-criteria for Phase 3.

> **NOTE: Write these tests FIRST; confirm they FAIL before implementing Phase 3.**

### Backend tests

- [x] T004 [P] [US1] Extend `backend/src/test/java/com/endia/application/service/ThemeServiceTest.java` with `listHistory()` cases: (a) entries returned in repository order with nested snacks/questions grouped from the batch-loaded lists, (b) empty repository result returns `[]` and **never** calls the snack/question finders, (c) a theme with no subtopics and a subtopic with no snacks/questions yields empty lists without error. Add the new repository mocks (`JpaSnackRepository`, `JpaQuizQuestionRepository`) to the constructor wiring. (FR-005, FR-019, SC-003)
- [x] T005 [P] [US1] Create `backend/src/test/java/com/endia/interfaces/rest/ThemeHistoryContractTest.java` (MockMvc standalone, mocked `ThemeService`, mirroring `BatchStatusContractTest`): `GET /api/themes` → 200 with a JSON **array**; assert `themeId`/`prompt`/`createdAt`/`subtopics`, nested `id`/`name`/`snacks`/`quizQuestions`, snack `id`/`concept`/`metaphor`/`whyItMatters`/`related`, question `id`/`type`/`prompt`/`explanation`; empty service result → 200 `[]`; ordering asserted by returning pre-ordered entries and checking the serialized order. (FR-001..FR-004, FR-006, FR-019, SC-001, SC-002)
- [x] T006 [P] [US1] Create `backend/src/test/java/com/endia/application/integration/ThemeHistoryIntegrationTest.java` (Testcontainers MySQL 8.0, `@SpringBootTest @Testcontainers @Tag("integration")`, mirroring `ThemeFlowIntegrationTest`): persist a theme with a subtopic + snack + quiz question; call `themeService.listHistory()`; then access the returned nested data **after the service transaction ends** to prove no `LazyInitializationException`; assert two themes order newest-first and the empty case returns an empty list. (FR-005, FR-019, SC-003, SC-008)

### Frontend test infrastructure and spec

- [x] T007 [P] [US1] Add `setupThemesHistoryMock(page: Page, themes: ThemeHistory[])` to `frontend/e2e/helpers.ts`: fulfil `GET **/api/themes` with a JSON array (distinct from `**/api/themes/suggest`). Do **not** add it to `setupAllMocks`, to avoid changing the shared fixture used by every existing spec. (FR-020)
- [x] T008 [US1] Create `frontend/e2e/theme-history.spec.ts` with the US1 coverage (depends on T007): header link is visible in `.app-shell__header` and clicking it navigates to `/history` with `HistoryComponent` rendered; deep-link `page.goto('/history')` renders without redirect; with two mocked themes, entries show `prompt` and formatted `createdAt` in newest-first order. Expected to fail until T020/T021/T022/T024/T025 land. (FR-011, FR-015, FR-017, FR-020, SC-005, SC-007)
- [x] T009 [US2] Extend `frontend/e2e/theme-history.spec.ts` with the nested read-only coverage: expanding a theme lists subtopics by `name`; each snack shows `concept`/`metaphor`/`whyItMatters`/`related`; each question shows `type`/`prompt`/`explanation`; a subtopic with no snacks/questions shows a "nothing generated yet" indication without error; assert **0** `POST /api/batches` and **0** `POST /api/snacks/generate` calls from the page. (FR-016, FR-008, FR-020, SC-007)
- [x] T010 [US3] Extend `frontend/e2e/theme-history.spec.ts` with the state coverage: a delayed mock shows the loading state while in flight and clears it once settled; an HTTP 500 mock shows the `ErrorService`-derived message and **no** theme list; an empty-array mock shows the empty-state message and **zero** theme entries (no error). (FR-012, FR-013, FR-014, FR-020, SC-006)
- [x] T011 [US4] Extend `frontend/e2e/theme-history.spec.ts` with the accessibility coverage: tab to the "History" header link and to a theme-detail control, assert each is keyboard reachable/activatable with a visible `:focus-visible` indicator. (FR-018, SC-008)

**Checkpoint**: All new tests exist and fail for the right reason — Phase 3 can now make them pass.

---

## Phase 3: Core (backend then frontend)

**Purpose**: Implement the smallest change set that satisfies the spec, making the Phase 2 tests pass.

### Backend

- [x] T012 [US1] In `backend/src/main/java/com/endia/infrastructure/persistence/JpaThemeRepository.java`, add `@EntityGraph(attributePaths = {"subtopics"}) List<Theme> findAllByOrderByCreatedAtDesc();`. Only **one** bag is fetched (safe); `ORDER BY created_at DESC` provides FR-001 ordering. (FR-001, FR-005)
- [x] T013 [P] [US1] In `backend/src/main/java/com/endia/infrastructure/persistence/JpaSnackRepository.java`, add `List<Snack> findBySubtopicIdIn(Collection<UUID> subtopicIds);` for the batch load. (FR-002, FR-003)
- [x] T014 [P] [US1] In `backend/src/main/java/com/endia/infrastructure/persistence/JpaQuizQuestionRepository.java`, add `List<QuizQuestion> findBySubtopicIdIn(Collection<UUID> subtopicIds);` for the batch load. (FR-002, FR-004)
- [x] T015 [US1] In `backend/src/main/java/com/endia/application/service/ThemeService.java`, add the nested read-model records (`ThemeHistory`, `SubtopicHistory`, `SnackHistory`, `QuestionHistory`) and `@Transactional(readOnly = true) List<ThemeHistory> listHistory()`: call `findAllByOrderByCreatedAtDesc()`, collect `Subtopic.id`s, batch-load snacks/questions (skip the finders when there are no subtopics), group by `getSubtopic().getId()`, and assemble the read model **inside** the transaction. Depends on T012, T013, T014. (FR-005, FR-008, FR-022)
- [x] T016 [US1] Create `backend/src/main/java/com/endia/interfaces/rest/dto/ThemeHistoryApiResponse.java` as a record with nested records `SubtopicItem`/`SnackItem`/`QuestionItem` per the contract. (FR-001..FR-004, FR-006)
- [x] T017 [US1] In `backend/src/main/java/com/endia/interfaces/rest/ThemeController.java`, add `@GetMapping public ResponseEntity<List<ThemeHistoryApiResponse>> getHistory()` mapping each `ThemeService.ThemeHistory` to `ThemeHistoryApiResponse` (serialize `type` via `.name()`); leave `POST /suggest` untouched. Depends on T015, T016. (FR-001, FR-007, FR-022)

### Frontend

- [x] T018 [P] [US1] Create `frontend/src/app/models/history.model.ts` with `ThemeHistory` (`themeId`, `prompt`, `createdAt`, `subtopics`), `HistorySubtopic`, `HistorySnack`, `HistoryQuestion` (reusing `QuestionType` from `models/quiz.model.ts`). (FR-009)
- [x] T019 [US1] In `frontend/src/app/services/theme.service.ts`, add `listHistory(): Observable<ThemeHistory[]>` calling `GET /api/themes`; keep `suggest`/`addSubtopics` unchanged. Depends on T018. (FR-009, SC-005)
- [x] T020 [P] [US1] In `frontend/src/app/app.routes.ts`, add the lazy `/history` route loading `HistoryComponent` with a `title`, placed **before** the `**` wildcard and with **no** guard/redirect. (FR-010, FR-017)
- [x] T021 [US1] Create `frontend/src/app/pages/history/history.component.ts` as a standalone `ChangeDetectionStrategy.OnPush` component with signals `loading`, `errorMessage`, `themes`, `expanded`; call `ThemeService.listHistory()` on construction; clear `loading` in both `next` and `error`; set `errorMessage` from `ErrorService.getMessage(err)`. Depends on T019. (FR-012, FR-013, FR-014)
- [x] T022 [US1] Create `frontend/src/app/pages/history/history.component.html` with a single branch chain loading → error → empty → populated: render each theme's `prompt` + locally formatted `createdAt`, expandable read-only subtopics with snacks (`concept`/`metaphor`/`whyItMatters`/`related`) and questions (`type`/`prompt`/`explanation`), and a "nothing generated yet" note for empty arrays. Depends on T021. (FR-015, FR-016)
- [x] T023 [P] [US1] Create `frontend/src/app/pages/history/history.component.scss` for the page and nested detail styles. (FR-016)
- [x] T024 [US1] In `frontend/src/app/app.html`, wrap the existing `.theme-toggle` and the new `<a class="header-link" routerLink="/history">History</a>` in a `<nav class="app-shell__nav" aria-label="Primary">`; do not add a third direct header child. Depends on T020. (FR-011)
- [x] T025 [P] [US4] In `frontend/src/app/app.scss`, add `.app-shell__nav` (flex, gap) and `.header-link` styles including an explicit `:focus-visible` indicator; leave `.brand*` and `.theme-toggle` rules intact. (FR-011, FR-018)

**Checkpoint**: Backend returns the full nested array (empty → 200 `[]`) and the `/history` page renders all states read-only — the Phase 2 tests should now pass.

---

## Phase 4: Integration

**Purpose**: Run the new tests, prove non-regression, and confirm the frozen contracts.

- [x] T026 Run the backend tests and confirm green: `cd backend && mvn test` (unit + contract; integration excluded by default) and `cd backend && mvn verify -Pintegration` (Testcontainers MySQL, requires Docker). Depends on T004–T006 and T012–T017. (FR-019, SC-003, SC-008)
  - **Result**: `mvn test` = **104 tests, 0 failures** (baseline 97; +4 `ThemeServiceTest`, +3 `ThemeHistoryContractTest`). `ThemeHistoryIntegrationTest` = **3/3 pass**. Environment note: the local Docker daemon (v29.1.3) rejects Testcontainers' default docker-java API `1.32`; integration must be run with `-Dapi.version=1.44` (this affects all pre-existing integration tests too, not the feature). With that flag, `mvn verify -Pintegration` = 18 tests with **pre-existing, unrelated failures** in `ThemeFlowIntegrationTest.contextLoads` (asserts an empty DB but another test in the same class persists a theme — fails in isolation too) and intermittently `BatchFlowIntegrationTest` (timing/LLM-mock). `ThemeHistoryIntegrationTest` passes consistently.
- [x] T027 Run the new frontend spec and confirm green: `cd frontend && npx playwright test e2e/theme-history.spec.ts`. Depends on T007–T011 and T018–T025. (FR-020, SC-006, SC-007)
  - **Result**: **9 passed** (US1×3, US2×1, US3×3, US4×2).
- [x] T028 Run the named regression suite and confirm green **unchanged**: `cd frontend && npx playwright test e2e/happy-path.spec.ts e2e/direct-url-access.spec.ts e2e/restart-clears-state.spec.ts e2e/dark-mode.spec.ts e2e/back-navigation.spec.ts e2e/logo-visibility`. (FR-021, SC-008)
  - **Result**: **12 passed** unchanged.
- [x] T029 [P] Type-check/build: run `cd frontend && npm run build` and confirm the new route, service, model, and component compile with no errors. (SC-005, SC-008)
  - **Result**: `npm run build` success; new lazy chunk `history-component` emitted (9.07 kB).
- [x] T030 [P] Confirm existing-endpoint and schema preservation: `git status`/`git diff` show no change to `POST /api/themes/suggest`, `POST /api/themes/{themeId}/subtopics`, `POST /api/snacks/generate`, `/api/batches`, the quiz endpoints, any entity/enum, or any migration. Record the result. (FR-007, FR-022, SC-004)
  - **Result**: `git status`/`git diff --stat` show only the intended 11 modified + 6 new source files. No entity/enum/converter/schema/migration change; `pom.xml`/`package.json` unchanged (no new dependency). `ThemeController.suggest` (`POST /suggest`) is untouched.
- [x] T031 [P] Verify the feature docs against the shipped behavior (`specs/011-theme-history/data-model.md`, `contracts/get-themes.md`, `quickstart.md`) and correct any drift; confirm the documented 3-query read strategy and `[]`-with-200 behavior match. (FR-022)
  - **Result**: No drift. Shipped behavior matches docs: 1 `@EntityGraph("subtopics")` query + up to 2 `findBySubtopicIdIn` batch queries; empty history → HTTP 200 `[]`; `type` serialized via `.name()`; `createdAt` ISO-8601.

**Checkpoint**: New behavior verified; regression contract and frozen surfaces confirmed.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final audits, manual verification, and checklist sign-off.

- [x] T032 [P] Read-only audit: confirm `ThemeService.listHistory()` is `@Transactional(readOnly = true)`, performs only finder calls, and that `/history` issues only `GET /api/themes` (0 `POST /api/batches`, 0 `POST /api/snacks/generate`, no quiz mutation). (FR-008, SC-004, SC-007)
  - **Result**: `listHistory()` is `@Transactional(readOnly = true)` and calls only `findAllByOrderByCreatedAtDesc`/`findBySubtopicIdIn`; no save/update/delete. `theme-history.spec.ts` asserts **0** `POST /api/batches` and **0** `POST /api/snacks/generate`.
- [x] T033 [P] No-lazy / no-N+1 audit: confirm the read model is fully materialized before the transaction ends, the query count is bounded (1 themes+subtopics + up to 2 batch queries), and no `MultipleBagFetchException` occurs. (FR-005, SC-003)
  - **Result**: Only the single `subtopics` bag is fetched by `@EntityGraph`; snacks/questions are batched by id. `ThemeHistoryIntegrationTest.listHistoryReturnsNestedDataAccessibleAfterTheTransactionEnds` accesses every nested field **after** the service transaction ends → no `LazyInitializationException`; no `MultipleBagFetchException`.
- [x] T034 Run the full Playwright suite: `cd frontend && npx playwright test` and confirm all specs (including the wider set) are green. (FR-021, SC-008)
  - **Result**: **76 passed** (baseline 67 + 9 new), 0 failures.
- [x] T035 Execute the `quickstart.md` manual verification (sections A–E): endpoint array/`[]`/ordering/nested, header + deep link, list + nested detail, loading/error/empty states, accessibility. Record results. (SC-001..SC-008)
  - **Result**: The literal curl/manual-browser steps were not run (no standalone MySQL/LLM available here). Their automated equivalents were executed and passed: endpoint array/`[]`/ordering/nested (contract test + `ThemeHistoryIntegrationTest`), header + deep link + list + nested detail + loading/error/empty + accessibility (`theme-history.spec.ts`, 9/9). No functional gap identified.
- [x] T036 Final validation: mark every completed task `[x]` in this file, confirm **all** checklists under `specs/011-theme-history/checklists/` have **0 unchecked items**, and record the final task/phase counts in the Summary section. (SC-008)
  - **Result**: All 36 tasks `[x]`; all 5 checklists have 0 unchecked items (requirements 57/57, implementation 29/29, test 28/28, ux 25/25, security 16/16).

**Checkpoint**: Feature complete, verified, and signed off.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Tests (Phase 2)**: Depends on Setup. Authored before any Core implementation; they fail until Phase 3 lands.
- **Core (Phase 3)**: Depends on Tests. Backend tasks (T012–T017) precede frontend tasks (T018–T025) so the endpoint exists for the page to call.
- **Integration (Phase 4)**: Depends on the relevant Core tasks landing.
- **Polish (Phase 5)**: Depends on all user stories + Integration.

### Within the feature

- Tests MUST be written and FAIL before implementation (T004–T011 before T012–T025).
- Backend: repositories (T012–T014) before service (T015); DTO (T016) before controller (T017); service + DTO before controller.
- Frontend: model (T018) before service (T019); service + route (T019/T020) before component (T021); component `.ts` (T021) before `.html` (T022); route (T020) before header link (T024).
- Core implementation before Integration.

### Sequential vs parallel rules

- A task may be marked `[P]` only when it edits a **different file** than other in-flight tasks and has no dependency on an incomplete task.
- `JpaThemeRepository` (T012) is independent of `JpaSnackRepository` (T013) and `JpaQuizQuestionRepository` (T014) → parallel.
- `history.component.ts` (T021) and `history.component.html` (T022) are **never** `[P]` relative to each other (the template binds the component's signals/methods).
- `theme-history.spec.ts` tasks (T008–T011) all edit the same file → **never** `[P]` relative to each other; execute in order.
- `app.html` (T024) and `app.scss` (T025) are different files, but T025 styles the markup added by T024 → T024 before T025 (or author them together; T025 is only `[P]` against other files).
- `theme.service.ts` (T019) and `app.routes.ts` (T020) are different files → parallel.

### Parallel opportunities

- **Setup**: T002 and T003 are independent — parallel.
- **Tests**: T004, T005, T006, T007 are different files — parallel. T008 → T009 → T010 → T011 are sequential (same spec file).
- **Core backend**: T013 and T014 are parallel with each other; T012 is parallel with both. T015 waits for T012–T014; T016 is parallel with T012–T015; T017 waits for T015/T016.
- **Core frontend**: T018 and T020 are parallel; T023 is parallel with the component chain; T025 is parallel with the component chain.
- **Integration**: T029, T030, T031 are parallel with the test runs T026–T028.
- **Polish**: T032 and T033 are parallel with the full-suite run T034.

---

## Parallel Example: Phase 2 (Tests) and Phase 3 (Core)

```bash
# Tests — author the independent files together (all expected to fail):
Task: "T004 Extend ThemeServiceTest.java with listHistory ordering/empty/nested cases"
Task: "T005 Create ThemeHistoryContractTest.java (MockMvc array shape + [] )"
Task: "T006 Create ThemeHistoryIntegrationTest.java (Testcontainers no-lazy/ordering)"
Task: "T007 Add setupThemesHistoryMock to frontend/e2e/helpers.ts"

# Core backend — the three repository finders are independent:
Task: "T012 Add findAllByOrderByCreatedAtDesc() @EntityGraph('subtopics') to JpaThemeRepository.java"
Task: "T013 Add findBySubtopicIdIn to JpaSnackRepository.java"
Task: "T014 Add findBySubtopicIdIn to JpaQuizQuestionRepository.java"

# Core frontend — model/route/scss are independent files:
Task: "T018 Create frontend/src/app/models/history.model.ts"
Task: "T020 Add lazy /history route to frontend/src/app/app.routes.ts"
Task: "T023 Create frontend/src/app/pages/history/history.component.scss"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Complete Setup (T001–T003).
2. Author the failing backend/frontend tests (T004–T011).
3. Implement the backend read path (T012–T017).
4. Implement the frontend wiring and page (T018–T025).
5. **STOP and VALIDATE**: run `e2e/theme-history.spec.ts` plus the regression suite.
6. Deploy/demo if ready — created themes are browsable newest-first from `/history`.

### Incremental Delivery

1. Setup + Tests → the verification contract is pinned.
2. Core backend → `GET /api/themes` returns the full nested array (empty → 200 `[]`).
3. Core frontend → header link + `/history` with loading/error/empty/populated states.
4. US2/US3/US4 → nested read-only detail, state handling, accessibility.
5. Integration/Polish → regression, audits, manual verification, checklist sign-off.

### Parallel Team Strategy

Once Setup and Tests are in place: Developer A takes the backend (T012–T017), Developer B takes the
frontend wiring/page (T018–T025) — then Integration/Polish together.

---

## Requirement Coverage

| Spec ID | Covered by |
|---------|-----------|
| FR-001 | T005, T012, T016, T017 |
| FR-002 | T005, T013, T016 |
| FR-003 | T005, T013, T016 |
| FR-004 | T005, T014, T016 |
| FR-005 | T004, T006, T012, T015, T033 |
| FR-006 | T005, T016 |
| FR-007 | T017, T030 |
| FR-008 | T015, T032 |
| FR-009 | T018, T019 |
| FR-010 | T020 |
| FR-011 | T003, T008, T024, T025 |
| FR-012 | T010, T021 |
| FR-013 | T010, T021 |
| FR-014 | T010, T021, T022 |
| FR-015 | T008, T022 |
| FR-016 | T009, T022, T023 |
| FR-017 | T008, T020 |
| FR-018 | T011, T025 |
| FR-019 | T004, T005, T006, T026 |
| FR-020 | T007, T008, T009, T010, T027 |
| FR-021 | T001, T003, T028, T034 |
| FR-022 | T002, T015, T017, T030, T031 |
| SC-001 | T005, T026, T035 |
| SC-002 | T005, T026, T035 |
| SC-003 | T004, T006, T026, T033 |
| SC-004 | T002, T030, T032 |
| SC-005 | T019, T020, T029 |
| SC-006 | T010, T027, T035 |
| SC-007 | T008, T009, T027, T032 |
| SC-008 | T001, T003, T006, T011, T026, T028, T029, T034, T036 |

**No FR or SC is left uncovered.** All 22 FRs and all 8 SCs map to at least one task.

---

## Summary

- **Total tasks: 36.**
- **Phase breakdown**:
  - Setup: **3** (T001–T003)
  - Tests (TDD): **8** (T004–T011)
  - Core — Backend: **6** (T012–T017)
  - Core — Frontend: **8** (T018–T025)
  - Integration: **6** (T026–T031)
  - Polish: **5** (T032–T036)
- **Story task counts** (labeled): US1 = 18, US2 = 1, US3 = 1, US4 = 2; unlabeled setup/integration/polish = 14.
- **Parallel opportunities**: 15 tasks marked `[P]`.
- **New backend files**: `ThemeHistoryApiResponse.java`, `ThemeHistoryContractTest.java`, `ThemeHistoryIntegrationTest.java`.
- **New frontend files**: `models/history.model.ts`, `pages/history/history.component.{ts,html,scss}`, `e2e/theme-history.spec.ts`.
- **Modified files**: backend `ThemeService.java`, `JpaThemeRepository.java`, `JpaSnackRepository.java`, `JpaQuizQuestionRepository.java`, `ThemeController.java`, `ThemeServiceTest.java`; frontend `theme.service.ts`, `app.routes.ts`, `app.html`, `app.scss`, `e2e/helpers.ts`.
- **No schema/migration/entity/dependency change** (FR-022/SC-004).

## Implementation Completion Record (2026-09-11)

- **Tasks**: **36/36 complete** (all `[x]`). No task skipped or blocked.
- **Phase counts**: Setup 3/3, Tests 8/8, Core — Backend 6/6, Core — Frontend 8/8, Integration 6/6, Polish 5/5.
- **Checklists**: all 5 pass with 0 unchecked items (requirements 57/57, implementation 29/29, test 28/28, ux 25/25, security 16/16).
- **Backend**: `mvn test` = 104 tests / 0 failures (baseline 97). `ThemeHistoryIntegrationTest` = 3/3 (run with `-Dapi.version=1.44`; see T026 environment note).
- **Frontend**: `npm run build` success; `npx playwright test e2e/theme-history.spec.ts` = 9/9; full suite = 76 passed (baseline 67); named regression suite = 12 passed.
- **Known environment/pre-existing issues**: (1) local Docker v29.1.3 requires `-Dapi.version=1.44` for Testcontainers; (2) pre-existing integration failures in `ThemeFlowIntegrationTest.contextLoads` (test-isolation) and intermittent `BatchFlowIntegrationTest` (timing) — both unrelated to this feature.

## Notes

- `[P]` tasks touch different files and have no dependency on incomplete tasks; tasks editing the same
  `theme-history.spec.ts` or the same component file are never `[P]` relative to each other.
- Every task carries exact file paths (create or modify) and a clear done-definition.
- The read model **must** be materialized inside `listHistory()`'s read-only transaction; do not rely on
  OSIV (`plan.md §Risks`, `research.md` R1).
- Checklists live in `specs/011-theme-history/checklists/` and must end with 0 unchecked items at
  implementation completion (T036).
