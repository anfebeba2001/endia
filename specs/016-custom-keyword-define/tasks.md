---
description: "Task list for feature 016-custom-keyword-define"
---

# Tasks: Custom Keyword "Define" from a Held Text Selection (2-Second Trigger)

**Input**: Design documents from `/specs/016-custom-keyword-define/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md, e2e-test-plan.md

**Tests**: Tests are **required** for this feature (spec §Success Criteria, plan.md §Testing Strategy and e2e-test-plan.md explicitly demand them). Test tasks are written **first**, before the corresponding implementation, per TDD ordering; they MUST FAIL (compilation or assertion) before the implementation task lands.

**Organization**: Tasks are grouped by user story (US1–US5) so each story can be implemented and verified independently, and map to the plan's milestones **M1** (prompt + port + providers), **M2** (service + endpoint), **M3** (selection + affordance), **M4** (generation UX + scope + security), **M5** (E2E + regression). The **Foundational** phase carries the shared backend capability (M1 + M2) that US1 and US2 depend on.

**⚠️ HIGHEST-RISK TASK**: **T022 (`SelectionDefineDirective`)** — the `document:selectionchange` + ~2s timer handling is the single riskiest task. `selectionchange` fires frequently, and a stale timer could show an affordance for a selection that already changed; the directive MUST clear one debounce timer on every change/clear, start a fresh timer only for a valid non-collapsed in-content selection, read the term/rect at fire time, and never read/render HTML. Coupled risks: T027/T028 (the page wiring must not intercept existing keyword clicks or violate the single-card rule) and T034 (the escaped-only security audit). See the task and the Dependencies section.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Which user story this task belongs to (US1–US5)
- Every task includes exact file path(s) and the FR/SC references it satisfies

## Path Conventions

- **Backend**: `backend/src/main/...`, `backend/src/test/...`
- **Frontend**: `frontend/src/app/...`, `frontend/e2e/...`
- **Feature docs**: `specs/016-custom-keyword-define/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the narrow scope and record the pre-change baseline before any story work.

- [X] T001 [P] Confirm no new runtime dependency is required (no `backend/pom.xml` and no `frontend/package.json` change) per plan.md §Technical Context / research.md R9; record the pre-change baseline by running `cd backend && ./mvnw test` (fallback `mvn test`) and `cd frontend && npx playwright test` (baseline for FR-018, SC-007).
- [X] T002 [P] Confirm the no-schema-change baseline: verify `backend/src/main/resources` has no migration tool, that no entity/column/index/table is added or altered, and that Hibernate `ddl-auto=update` issues **no DDL** for this feature (FR-004/§No persistence, SC-007, data-model.md §4).

---

## Phase 2: Foundational (Blocking Prerequisites) — Milestones M1 + M2

**Purpose**: The provider-agnostic backend generation capability (externalized prompt + port method + providers + service + endpoint) that US1's activation and US2's generation both depend on. **No schema change; no existing snack/meaning-snack/quiz DTO touched.**

**⚠️ CRITICAL**: US1's activation wiring (T027) and US2 cannot complete until this phase is done. The frontend-only selection/affordance work (M3) may proceed in parallel with this phase (plan.md: M1 and M3 independent).

### Tests for Foundational (write first, must FAIL) ⚠️

- [X] T003 [P] Create `backend/src/test/java/com/endia/infrastructure/llm/GenerateDefinitionPromptTest.java` asserting the rendered `generate-definition.md` substitutes `{{theme}}`/`{{term}}` (no placeholders remain), requests exactly the `{ "definition": "..." }` JSON shape, states the **≤20-word** bound, requires plain text (no bold markers/bullets/headings/links/HTML), and forbids quiz content and a `keywords` array (FR-004, FR-005, SC-001).
- [X] T004 [P] Extend `backend/src/test/java/com/endia/infrastructure/llm/OpenAiLlmProviderTest.java` asserting a well-formed `{definition:"..."}` response parses into `GeneratedDefinition`, a missing `definition` yields `null`, a malformed overall response throws `MALFORMED`, and extra emitted fields are ignored (FR-004, FR-005).
- [X] T005 [P] Extend `backend/src/test/java/com/endia/infrastructure/llm/RetryingLlmProviderTest.java`: implement `generateDefinition` on `StubProvider` and assert a retryable failure retries and rethrows the typed cause (FR-005).
- [X] T006 [P] Create `backend/src/test/java/com/endia/application/service/DefinitionServiceTest.java` (Mockito): unknown theme → `EntityNotFoundException` (404); blank term → `InvalidSelectionException` (400) and the provider is **never** called; a 10-token term is accepted; an 11-token term → 400 and the provider is **never** called; multiple spaces/tabs/newlines collapse to single spaces; a valid generation returns ≤20 tokens; a >20-token generated definition is truncated to exactly 20; a blank generated definition re-rolls exactly once then throws `ServiceUnavailableException`; a provider exception → `ServiceUnavailableException`; the provider is called with `theme.getPrompt()` and the normalized term (FR-004, FR-005, FR-010, FR-011, FR-012, SC-005).
- [X] T007 [P] Create `backend/src/test/java/com/endia/interfaces/rest/DefinitionContractTest.java` (MockMvc standalone, real `GlobalExceptionHandler`, mirroring `MeaningSnackContractTest`): `POST /api/definitions/generate` returns 200 `{term, definition}` with exactly two fields; a blank term → 400 `VALIDATION_ERROR` `field=term`; a >10-token term → 400 `VALIDATION_ERROR`; an unknown theme → 404 `NOT_FOUND`; an LLM failure → 503 `SERVICE_UNAVAILABLE` with `cause` (FR-004, FR-005, FR-010, FR-011, SC-005).

### Implementation for Foundational — M1: Prompt + port + providers

- [X] T008 [P] Create `backend/src/main/resources/prompts/generate-definition.md` — provider-agnostic; asks for exactly `{ "definition": "..." }` and no other fields; requires a plain-text, concise meaning of **at most 20 words** (not a multi-paragraph explanation), non-empty, same language as the theme, **no markup** (no bold/bullets/headings/links/HTML/event handlers/`javascript:` URLs), **no quiz content**, **no `keywords` array** (FR-004, FR-005).
- [X] T009 Extend `backend/src/main/java/com/endia/application/port/LlmProvider.java`: add `GeneratedDefinition generateDefinition(String theme, String term)` and `record GeneratedDefinition(String definition) {}` (provider-agnostic, mirroring `GeneratedSnack`/`GeneratedMeaningSnack`) (FR-004, FR-005). **Depends on T008 contract; blocks T011/T012.**
- [X] T010 [P] Extend `backend/src/main/java/com/endia/infrastructure/llm/LlmPromptTemplates.java` with `generateDefinition(theme, term)` loading `prompts/generate-definition.md` and replacing `{{theme}}`/`{{term}}` (FR-004, FR-005). **Depends on T008.**
- [X] T011 Extend `backend/src/main/java/com/endia/infrastructure/llm/OpenAiLlmProvider.java` to implement `generateDefinition` in the same defensive parse style as `generateMeaningSnack` (`node.path("definition").asText(null)`; missing field → `null`; malformed overall → `MALFORMED`; extra fields ignored) (FR-004, FR-005). **Depends on T009, T010.**
- [X] T012 Extend `backend/src/main/java/com/endia/infrastructure/llm/RetryingLlmProvider.java` to delegate `generateDefinition` through its existing `retry(...)` (FR-005). **Depends on T009.**

### Implementation for Foundational — M2: Service + endpoint

- [X] T013 [P] Create `backend/src/main/java/com/endia/domain/exception/InvalidSelectionException.java` extending `DomainException` with `super(400, "VALIDATION_ERROR", message)` so the existing `GlobalExceptionHandler` maps it with **no handler change** (mirrors `InvalidOptionIndexException`) (FR-011).
- [X] T014 Create `backend/src/main/java/com/endia/application/service/DefinitionService.java`: constructor-injected `LlmProvider` + `JpaThemeRepository`; constants `MAX_TERM_TOKENS = 10`, `MAX_DEFINITION_TOKENS = 20`; `generate(UUID themeId, String term)` resolves the theme (404), normalizes `term.trim().replaceAll("\\s+", " ")`, rejects blank/`>10`-token terms with `InvalidSelectionException` **before any LLM call**, calls `llmProvider.generateDefinition(theme.getPrompt(), normalizedTerm)` through the typed wrapper (`LlmGenerationException` rethrown; other exceptions → `ServiceUnavailableException`), validates the generated definition non-blank with **one re-roll** then `ServiceUnavailableException` (503), truncates to the first 20 tokens, and returns `DefinitionResult(term, definition)`; nothing is persisted (FR-004, FR-005, FR-010, FR-011, FR-012, data-model.md §2). **Depends on T009, T013.**
- [X] T015 [P] Create `backend/src/main/java/com/endia/interfaces/rest/dto/DefinitionGenerateApiRequest.java` as `record(@NotNull(message="themeId is required") UUID themeId, @NotBlank(message="term must not be blank") String term)` (FR-004, FR-010, data-model.md §3.1).
- [X] T016 [P] Create `backend/src/main/java/com/endia/interfaces/rest/dto/DefinitionGenerateApiResponse.java` as `record(String term, String definition)` — exactly two fields, no `id`/persisted/quiz/`keywords` fields (FR-004, data-model.md §3.2).
- [X] T017 Create `backend/src/main/java/com/endia/interfaces/rest/DefinitionController.java` (`@RequestMapping("/api/definitions")`): `POST /generate` → 200 `DefinitionGenerateApiResponse`; errors use the existing `GlobalExceptionHandler` shapes (400 `VALIDATION_ERROR`, 404 `NOT_FOUND`, 503 `SERVICE_UNAVAILABLE`) (FR-004, FR-005, FR-011). **Depends on T014, T015, T016.**

**Checkpoint**: `POST /api/definitions/generate` generates a ≤20-word definition through the externalized, provider-agnostic flow; blank/oversized terms and unknown themes fail with typed 400/404/503 and no LLM call for rejected terms.

---

## Phase 3: User Story 1 — Select text, hold ~2 seconds, and "Define" the term (Priority: P1) 🎯 MVP

**Goal**: A held (~2s) selection inside the snack reading content shows a transient, selection-anchored "Define" affordance; activating it generates the definition and opens the existing single definition card showing it. This phase covers milestone **M3 (selection + affordance)** and the success half of **M4 (generation + display)**.

**Independent Test**: Select a term in a snack's reading content and hold it ~2s; confirm the "Define" affordance appears. Change/clear the selection within 2s; confirm no affordance appears. Activate "Define" and confirm the existing card opens for the selected term showing the generated meaning.

### Tests for User Story 1 (write first, must FAIL) ⚠️

- [X] T018 [P] [US1] Create `frontend/e2e/custom-keyword-define.spec.ts` (route-mocked) covering the trigger/success blocks: selecting a term in `.snack-card__concept` and holding ~2s shows a `Define` affordance anchored near the selection; no affordance before ~2s; changing/clearing before ~2s cancels and no affordance appears for the old selection; a subsequent held selection starts a fresh timer; releasing without activating issues **no** `POST /api/definitions/generate`; activating `Define` opens the card for the term and (with a delayed mock) ends with the generated definition (FR-001, FR-002, FR-003, FR-004, FR-008, SC-001, SC-002).
- [X] T019 [P] [US1] Extend `frontend/e2e/helpers.ts` with `setupDefinitionsMock(page, { generate? })` and add a benign default `POST /api/definitions/generate` to `setupAllMocks` so existing specs remain deterministic (FR-004, SC-007).

### Implementation for User Story 1 — M3 + M4 (selection, affordance, display)

- [X] T020 [P] [US1] Create `frontend/src/app/models/definition.model.ts` with `DefinitionGenerateResponse { term: string; definition: string }` mirroring the API (FR-004, FR-008, data-model.md §5.2).
- [X] T021 [US1] Create `frontend/src/app/services/definition.service.ts`: `generate(themeId: string, term: string): Observable<DefinitionGenerateResponse>` posting to `POST /api/definitions/generate` with `{ themeId, term }` (FR-004, FR-008). **Depends on T020.**
- [X] T022 [P] [US1] **⚠️ HIGHEST-RISK TASK** — Create `frontend/src/app/components/selection-define/selection-define.directive.ts`: standalone, `OnPush`, `selector: '[appSelectionDefine]'`; `termSelected = output<{ term: string; rect: DOMRect }>()` and `selectionCleared = output<void>()`; on `document:selectionchange` (+ `pointerdown`/`keyup` belt-and-braces) read `document.getSelection()`, compute the text via `selection.toString()` (plain text — **never** `innerHTML`) and the range via `selection.getRangeAt(0)`; accept only a non-collapsed selection whose range is contained in the host (`host.contains(range.commonAncestorContainer)`); on any change/clear `clearTimeout`, emit `selectionCleared`, and start a fresh `setTimeout(2000)` only for a valid in-content selection; when the timer fires normalize (`trim` + collapse whitespace) and emit `termSelected({ term, rect: range.getBoundingClientRect() })`; `ngOnDestroy` clears the timer and listeners; works identically for pointer and programmatic/keyboard selections (FR-001, FR-002, FR-003, FR-016).
- [X] T023 [P] [US1] Create `frontend/src/app/components/selection-define/selection-define-affordance.component.ts`: standalone, `OnPush`, presentational; inputs `rect`, `visible`, `loading`; outputs `defineRequested`, `dismissed`; `@HostListener('document:keydown.escape')` emits `dismissed`; no `[innerHTML]`, no `DomSanitizer` (FR-001, FR-003).
- [X] T024 [P] [US1] Create `frontend/src/app/components/selection-define/selection-define-affordance.component.html`: a single clearly-labelled `Define` button (`type="button"`, `aria-label="Define selected text"`), static label only, positioned `fixed` from `rect` and viewport-clamped (never overflows horizontally; flips above the selection when it would cross the viewport bottom); disabled/spinner while `loading` (FR-001, FR-003).
- [X] T025 [P] [US1] Create `frontend/src/app/components/selection-define/selection-define-affordance.component.scss` with the transient, selection-anchored, viewport-clamped affordance styles (FR-003).
- [X] T026 [US1] Extend `frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.ts` **additively** with `definitionLoading = input<boolean>(false)`, `definitionError = input<string | null>(null)`, `anchorRect = input<DOMRect | null>(null)`; leave the feature-013 open/dismiss/focus code and the feature-014 action-row inputs untouched (FR-003, FR-008, FR-013).
- [X] T027 [US1] Edit `frontend/src/app/pages/snacks/snacks.component.ts`: import `SelectionDefineDirective`, `SelectionDefineAffordanceComponent`, `DefinitionService`; add signals `selectionAffordance`, `defining`, `definitionLoading`, `definitionError`, `customDefinition`, `activeKeywordRect`; `onTermSelected({ term, rect })` normalizes + locally validates (blank/`>10` tokens → meaningful error, no request) else shows the affordance; `onDefineRequested()` guards `if (this.defining()) return;`, sets `defining`, hides the affordance, opens the existing card for the term with `definitionLoading=true` (`activeKeyword = { term, definition: '' }`, `activeKeywordRect = rect`, `customDefinition = true`), calls `definitionService.generate(themeId, term)`, and on success sets `activeKeyword = { term, definition: res.definition }` and `definitionLoading=false`; `onSelectionCleared()` hides the affordance; `closeKeyword()` also clears the new state; `goTo(...)` clears the affordance (FR-001, FR-003, FR-004, FR-008).
- [X] T028 [US1] Edit `frontend/src/app/pages/snacks/snacks.component.html`: add `appSelectionDefine` + `(termSelected)="onTermSelected($event)"` + `(selectionCleared)="onSelectionCleared()"` to the `.snack-content` wrapper; host exactly one `<app-selection-define-affordance>` at page level bound to `rect`/`visible`/`loading` with `(defineRequested)="onDefineRequested()"`/`(dismissed)="onSelectionCleared()"`; extend the existing `<app-keyword-definition-card>` bindings with `[anchorRect]="activeKeywordRect()"` (FR-001, FR-003, FR-008). **Depends on T022, T023, T024, T025, T026, T027.**

**Checkpoint**: Selecting text in the reading content and holding ~2s shows the transient "Define" affordance; activating it opens the existing card for the term showing the generated definition; changing/clearing the selection cancels.

---

## Phase 4: User Story 2 — Generate provider-agnostically with a loading state and safe failures (Priority: P1)

**Goal**: While generation runs the card shows a loading/in-progress state; rapid repeated activation starts at most one request; on failure a meaningful, user-safe error is shown and nothing broken/empty/placeholder is added. This phase covers the UX half of milestone **M4**.

**Independent Test**: Activate "Define" for a valid term and confirm a loading state then a generated meaning; force an LLM failure and confirm a meaningful error with no broken/empty content; activate "Define" twice rapidly and confirm at most one request is started.

### Tests for User Story 2 (write first, must FAIL) ⚠️

- [X] T029 [P] [US2] Extend `frontend/e2e/custom-keyword-define.spec.ts` with the generation-UX blocks: a delayed `POST /api/definitions/generate` mock shows a loading state ("Defining…") then the card shows the generated definition and the loading state ends; a rapid double-activation issues exactly one request and one card; a 503/error mock shows a meaningful, user-safe error (`role="alert"`) with **no** empty/placeholder definition paragraph and the reading content unaffected; a blank/whitespace-only selection and an 11-token selection each show a meaningful error and issue **no** request (assert the mock was not called) (FR-006, FR-007, FR-009, FR-010, FR-011, SC-002, SC-003, SC-005).

### Implementation for User Story 2

- [X] T030 [P] [US2] Extend `frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.html`: `@if (definitionLoading())` → spinner + `Defining…` in place of `.keyword-card__definition`; `@else if (kw.definition)` → the existing `.keyword-card__definition` (unchanged for keyword cards); `@if (definitionError(); as err)` → `.keyword-card__error` `role="alert"`; keep the feature-014 `generateError` slot untouched; escaped interpolation only (FR-006, FR-009, FR-013). **Depends on T026.**
- [X] T031 [P] [US2] Extend `frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.scss` with the `definitionLoading`/`definitionError` styles; `updatePosition()` prefers `anchor()`, falls back to `anchorRect()`, and keeps the default bottom-sheet when both are null (FR-006, FR-009).
- [X] T032 [US2] Edit `frontend/src/app/pages/snacks/snacks.component.ts`: enforce the single-in-flight guard (`if (this.defining()) return;`) and clear `defining` in both success and failure branches; on failure set `definitionLoading=false` and `definitionError = errorService.getMessage(err)` and add **nothing** empty/placeholder; set the card `generateEnabled` to `!customDefinition() && currentEntry()?.kind === 'regular'` so the custom-define card offers no "Generate snack" action (Task 12 out of scope) (FR-006, FR-007, FR-009, FR-019, SC-003). **Depends on T027.**

**Checkpoint**: Generation shows a loading state, starts at most one request, degrades to a meaningful error with nothing added, and the custom card offers no "Generate snack" action.

---

## Phase 5: User Story 3 — Selected and generated text is always rendered as safe, escaped text (Priority: P1)

**Goal**: The selected term (document text) and the generated definition (model output) are untrusted and rendered as plain escaped text only, preserving the feature-012/013/014/015 sanitization model. This phase covers the security portion of milestone **M4**.

**Independent Test**: Select a term containing markup-like text and force a generated definition containing `<script>`, `<img onerror=...>`, `javascript:` links, `<iframe>`, `<style>`, inline styles; confirm nothing executes and no nodes are injected.

### Tests for User Story 3 (write first, must FAIL) ⚠️

- [X] T033 [P] [US3] Extend `frontend/e2e/custom-keyword-define.spec.ts` with the security block: a mock whose `definition` contains `<script>`, `<img onerror>`, `javascript:`, `<iframe>` and `<style>` payloads, and a selected term containing markup-like text — assert `window.__xss` stays `undefined`, no `script/img/iframe/style/a` nodes exist inside `.keyword-card`, and the payload is shown as readable escaped text in `.keyword-card__term`/`.keyword-card__definition` (FR-013, FR-014, FR-015, SC-004).

### Implementation for User Story 3

- [X] T034 [US3] Audit the new rendering paths (`frontend/src/app/components/selection-define/selection-define.directive.ts`, `.../selection-define-affordance.component.{ts,html}`, `frontend/src/app/components/keyword-definition-card/*`, `frontend/src/app/pages/snacks/snacks.component.{ts,html}`) to confirm there is **no** `[innerHTML]` and **no** `DomSanitizer`, that the term is read as plain text (`Selection.toString()`) and the definition is emitted as escaped interpolation only, that the affordance renders a static label only, and add a short boundary comment documenting this (FR-013, FR-014, FR-015, SC-004).

**Checkpoint**: Selected term and generated definition are safe; injection payloads are inert and readable text is preserved.

---

## Phase 6: User Story 4 — Reachable by pointer and keyboard/programmatic selection without disturbing existing interactions (Priority: P2)

**Goal**: The selection-Define flow works for pointer and programmatic/keyboard (`document.getSelection()`) selections; the transient affordance does not break existing highlighted-keyword clicks, the single-definition-card rule, or the snack reading flow; and the trigger is offered only on the snacks reading page. This phase covers the reachability/scope portion of milestone **M4** and hardens **M3**.

**Independent Test**: Create a selection by pointer drag and by a programmatic `document.getSelection()` range; confirm the affordance appears after the hold in both. Click an existing highlighted keyword and confirm the existing card behavior is unchanged and only one card is shown. Select text on results/history and confirm no trigger.

### Tests for User Story 4 (write first, must FAIL) ⚠️

- [X] T035 [P] [US4] Extend `frontend/e2e/custom-keyword-define.spec.ts` with the reachability/non-interference blocks: a programmatic `document.getSelection()` range held ~2s shows the `Define` affordance; clicking an existing highlighted `.rich-text__keyword` still opens the existing definition card unchanged and triggers no selection-Define flow; the single-card rule holds (exactly one `.keyword-card`; a new term replaces the previous); selecting text on the results recap (`.recap-card__concept`) and the read-only history surface (`.history-snack__concept`) offers no `Define` affordance and those surfaces render unchanged (FR-003, FR-016, FR-017, FR-018, SC-006).

### Implementation for User Story 4

- [X] T036 [US4] Verify and harden `frontend/src/app/components/selection-define/selection-define.directive.ts` for programmatic/keyboard selection (both fire `selectionchange`) and the range-containment check (`host.contains(range.commonAncestorContainer)`) so a selection outside the reading content yields no trigger; confirm the directive is applied **only** on the snacks reading container (`snacks.component.html`) and that `frontend/src/app/pages/results/` and `frontend/src/app/pages/history/` receive no directive and no affordance, and that `RichTextComponent` and the keyword highlighter are untouched (FR-003, FR-016, FR-017). **Depends on T022, T028, T035.**

**Checkpoint**: The flow is reachable via pointer and programmatic selection, does not break keyword clicks or the single-card rule, and is scoped to the snacks reading page.

---

## Phase 7: User Story 5 — Existing snack, keyword, meaning-snack, and formatting behavior is not regressed (Priority: P2)

**Goal**: The feature ships without changing the snack reading experience, keyword highlight/definition-card interaction, meaning-snack flow, rich-text rendering, or results/history surfaces. This phase covers milestone **M5 (E2E + regression)**.

**Independent Test**: Run the existing backend and frontend suites (including `snack-keywords`, `meaning-snacks`, `snack-rich-formatting`) and confirm they pass unchanged.

### Verification for User Story 5

- [X] T037 [P] [US5] Run the existing backend suite unchanged: `cd backend && ./mvnw test` (fallback `mvn test`) and, where Docker is available, `./mvnw verify -Pintegration`; confirm `SnackServiceTest`, `MeaningSnackServiceTest`, `SnackMarkupRoundTripTest`, `MeaningSnackContractTest`, `ThemeHistoryContractTest`, `RetryingLlmProviderTest` (existing cases) and the Testcontainers integration tests pass unchanged (FR-018, SC-007).
- [X] T038 [P] [US5] Run the existing frontend gate unchanged: `cd frontend && npm run build` and `npx playwright test`; confirm `happy-path`, `progressive-delivery`, `snack-wait-messages`, `results-*`, `snacks-navigation`, `snack-keywords`, `snack-rich-formatting`, `meaning-snacks`, `theme-history` and the rest pass unchanged (FR-018, SC-007). **Depends on T019 (default definitions mock).**

**Checkpoint**: The full existing backend and frontend suites pass unchanged.

---

## Phase 8: Polish & Cross-Cutting Concerns — Milestone M5 close-out

**Purpose**: Final validation and cleanup across all stories.

- [X] T039 [P] Run the `specs/016-custom-keyword-define/quickstart.md` §5 manual verification checklist (hold trigger, generation, bounds, reachability/non-interference, scope, security payload table, non-regression) (SC-001..SC-007).
- [X] T040 [P] Confirm the rollback path: reverting the backend (prompt, port/provider, service, exception, DTOs, controller, tests) and frontend (directive, affordance, model/service, card inputs, page wiring, E2E) requires no data change and no SQL (data-model.md §4, quickstart.md §6, SC-007).
- [X] T041 [P] Verify checklist-to-task traceability: every FR-001..FR-019 and SC-001..SC-007 is covered by at least one task, every task maps to a requirement (no orphans), and `checklists/requirements.md`, `checklists/security.md` and `checklists/ux.md` remain consistent with tasks.md (all FR/SC).
- [X] T042 Run the full backend suite: `cd backend && ./mvnw test` (fallback `mvn test`) and, where Docker is available, `./mvnw verify -Pintegration` (FR-018, SC-007).
- [X] T043 Run the full frontend gate: `cd frontend && npm run build` and `npx playwright test`; confirm the new spec passes and all existing suites pass unchanged (FR-018, SC-007).
- [X] T044 Final completion validation: run both gates (`cd backend && ./mvnw test` fallback `mvn test`; `cd frontend && npm run build` and `npx playwright test`), confirm every task above is complete, every `checklists/*.md` item is checked, and every FR-001..FR-019 / SC-001..SC-007 is covered by at least one task with no orphans (all FR/SC).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2, M1 + M2)**: Depends on Setup — **blocks US1's activation wiring (T027) and US2**.
- **US1 (Phase 3, M3 + M4-success)**: The pure frontend selection/affordance work (T020–T026, T028) depends on Setup only and may run in parallel with Foundational; the activation wiring (T027) depends on the M2 contract (T014–T017) and T021/T022/T026.
- **US2 (Phase 4, M4-UX)**: Depends on Foundational (contract) + US1 (T026/T027).
- **US3 (Phase 5, M4-security)**: Depends on US1 (the rendering paths it audits/tests).
- **US4 (Phase 6, M4-scope)**: Depends on US1 (T022/T028) + the T035 tests.
- **US5 (Phase 7, M5)**: Depends on all implementation phases.
- **Polish (Phase 8, M5 close)**: Depends on all desired stories being complete.

### Milestone Mapping

| Milestone | Phase(s) | Key tasks |
|-----------|----------|-----------|
| **M1** Prompt + port + providers | Foundational | T003, T004, T005, T008, T009, T010, T011, T012 |
| **M2** Service + endpoint | Foundational | T006, T007, T013, T014, T015, T016, T017 |
| **M3** Selection + affordance | US1 | T018, T020, T021, T022, T023, T024, T025, T026 |
| **M4** Generation UX + scope + security | US1, US2, US3, US4 | T019, T027, T028, T029, T030, T031, T032, T033, T034, T035, T036 |
| **M5** E2E + regression | US5, Polish | T037, T038, T039, T040, T041, T042, T043, T044 |

### User Story Dependencies

- **US1 (P1)**: Can start after Setup; the selection/affordance work is independent of Foundational; activation (T027) needs the M2 contract.
- **US2 (P1)**: Starts after Foundational + US1 (needs the card inputs and page wiring).
- **US3 (P1)**: Starts after US1 (asserts the card/affordance rendering paths).
- **US4 (P2)**: Starts after US1 (asserts the directive and page scope).
- **US5 (P2)**: Starts after all implementation tasks land.

### Within Each User Story

- Test tasks MUST be written and FAIL before the corresponding implementation task.
- Prompt + port before provider implementation; port before retry decorator.
- Exception + DTOs before the service/controller; service before controller.
- Model before service; service before page wiring; directive/affordance before the page template.
- Card inputs (T026) before the card template states (T030/T031).
- Story complete and validated before moving to the next priority.

### ⚠️ Highest-Risk Task

- **T022 (`SelectionDefineDirective`)**: `selectionchange` fires frequently; a stale timer could show an affordance for a selection that already changed. Mitigations: one debounce timer cleared on every change/clear; a fresh timer only for a valid non-collapsed in-content selection; term/rect read at fire time; `ngOnDestroy` clears the timer; the E2E early-change/clear cases (T018) and programmatic selection (T035) are the guards. Coupled risks: **T027/T028** (the page wiring must not intercept existing keyword clicks or violate the single-card rule) and **T034** (escaped-only security audit). Do **not** move selection logic into `RichTextComponent`.

### Parallel Opportunities

- T001 and T002 (Setup) run in parallel.
- Foundational tests T003–T007 run in parallel (different files); T008 and T010 run in parallel; T009 before T011/T012; T011 after T010; T013/T015/T016 in parallel; T014 after T009/T013; T017 after T014/T015/T016.
- Within US1: T018 and T019 in parallel; T020, T022, T023, T024, T025 in parallel; T021 after T020; T026 in parallel with T021/T022; T027 after T021/T022/T026; T028 after T022–T027.
- Within US2: T030 and T031 in parallel; T032 after T027.
- US3: T033 before T034.
- US4: T035 before T036.
- US5: T037 and T038 run in parallel.
- Polish: T039, T040, T041 in parallel; T042 and T043 can run in parallel; T044 last.
- **Note**: tasks that edit the same file (e.g. `custom-keyword-define.spec.ts`, `snacks.component.ts`, `keyword-definition-card.component.*`) are conceptually independent blocks but must be serialized on disk; the `[P]` marker reflects independence of the work, not concurrent writes.

---

## Parallel Example: User Story 1

```bash
# Write the US1 tests together (must fail first):
Task: "Create frontend/e2e/custom-keyword-define.spec.ts (hold/cancel/success)"
Task: "Extend frontend/e2e/helpers.ts with setupDefinitionsMock"

# Then the independent M3 files (different files) in parallel:
Task: "Create frontend/src/app/models/definition.model.ts"
Task: "Create selection-define.directive.ts"
Task: "Create selection-define-affordance.component.ts"
Task: "Create selection-define-affordance.component.html"
Task: "Create selection-define-affordance.component.scss"
```

## Parallel Example: Foundational

```bash
# Backend tests first (must fail first):
Task: "Create GenerateDefinitionPromptTest"
Task: "Extend OpenAiLlmProviderTest with definition parsing"
Task: "Extend RetryingLlmProviderTest StubProvider"
Task: "Create DefinitionServiceTest"
Task: "Create DefinitionContractTest"

# Then the independent M1/M2 files in parallel:
Task: "Create generate-definition.md"
Task: "Create InvalidSelectionException"
Task: "Create DefinitionGenerateApiRequest"
Task: "Create DefinitionGenerateApiResponse"
```

---

## Implementation Strategy

### MVP First (US1 + US2)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (M1 + M2 backend capability) — the pure frontend M3 files may proceed in parallel.
3. Complete Phase 3: US1 (selection + affordance + card feed).
4. Complete Phase 4: US2 (loading + single-flight + safe failure).
5. **STOP and VALIDATE**: run `frontend/e2e/custom-keyword-define.spec.ts` and inspect the trigger/generation/error flow.
6. Deploy/demo the MVP.

### Incremental Delivery

1. Setup + Foundational → backend capability ready.
2. US1 → hold trigger + affordance + card display → validate → demo.
3. US2 → loading + duplicate guard + safe failure proven.
4. US3 → security hardening proven.
5. US4 → reachability + scope + non-interference proven.
6. US5 → non-regression proven.
7. Polish → full gates green.

### Notes

- `[P]` tasks touch different files with no incomplete dependency.
- Foundational (M1+M2) blocks US1's activation and US2; M3 is parallelizable with Foundational.
- No new runtime dependency, no new layer, no new endpoint group, no schema change.
- **Highest risk**: T022 (selection/timer directive) — keep one debounce timer and never intercept keyword clicks.
- Commit after each task or logical group; verify tests fail before implementing.

---

## FR Traceability Matrix

Every functional requirement (FR-001–FR-019) maps to at least one task. No gaps.

| FR | Description (short) | User Story | Task(s) |
|----|---------------------|------------|---------|
| FR-001 | Held ~2s selection shows the "Define" affordance | US1 | T018, T022, T023, T024, T027, T028 |
| FR-002 | Selection changed/cleared before 2s cancels the timer | US1 | T018, T022 |
| FR-003 | Transient, selection-anchored; no interference with single-card rule/keyword clicks | US1, US4 | T022, T023, T024, T025, T026, T027, T028, T035, T036 |
| FR-004 | Generate a short definition (≤20 words) via the backend | Foundational, US1 | T003, T004, T006, T007, T008, T011, T014, T015, T016, T017, T018, T019, T020, T021, T027 |
| FR-005 | Externalized, provider-agnostic flow; no provider logic in services/controllers | Foundational | T003, T004, T005, T008, T009, T010, T011, T012, T014, T017 |
| FR-006 | Loading/in-progress state during generation | US2 | T029, T030, T031, T032 |
| FR-007 | At most one request; in-flight guard against duplicates | US2 | T029, T032 |
| FR-008 | Success opens the card for the term showing the meaning | US1 | T018, T020, T021, T026, T027, T028 |
| FR-009 | Failure shows a meaningful error; nothing broken/empty/placeholder added | US2 | T029, T030, T031, T032 |
| FR-010 | Term normalized (trim + collapse) and bounded to ≤10 tokens | Foundational, US2 | T006, T014, T015, T029 |
| FR-011 | Blank/oversized selection rejected with an error; never submitted | Foundational, US2 | T006, T007, T013, T014, T017, T029 |
| FR-012 | Whitespace-separated token count; hyphenated = one | Foundational | T006, T014 |
| FR-013 | Escaped text only — no `[innerHTML]`, no `DomSanitizer` | US1, US2, US3 | T020, T026, T030, T033, T034 |
| FR-014 | Markup/handlers/URLs never executed or injected | US3 | T033, T034 |
| FR-015 | feature-012/013/014/015 sanitization model preserved unchanged | US3 | T033, T034 |
| FR-016 | Reachable via pointer and programmatic/keyboard selection | US1, US4 | T022, T035, T036 |
| FR-017 | Trigger offered only on the snacks reading page; results/history unchanged | US4 | T035, T036 |
| FR-018 | No change to keyword/card/meaning-snack/rich-text/results/history; suites pass unchanged | US4, US5 | T035, T037, T038, T042, T043 |
| FR-019 | Task 12 ("Create snack" from the custom meaning) not implemented | US2 | T032 |

## SC Traceability Matrix

Every success criterion (SC-001–SC-007) maps to at least one task. No gaps.

| SC | Description (short) | Task(s) |
|----|---------------------|---------|
| SC-001 | 100% of held selections show the affordance; 0 before 2s / after change-clear | T018, T022, T039 |
| SC-002 | 100% of activations show loading→meaning or a meaningful error; 0 broken/empty/placeholder | T029, T032, T039 |
| SC-003 | 0 duplicate requests; at most one in-flight per selection | T029, T032, T039 |
| SC-004 | 0 executable/injected markup from term/definition | T033, T034, T039 |
| SC-005 | 100% valid terms normalized; 0 blank/oversized submitted | T006, T007, T014, T029, T039 |
| SC-006 | Pointer + programmatic reachable; 0 triggers off the snacks page; 0 keyword-click/single-card regressions | T035, T036, T039 |
| SC-007 | Existing backend/frontend/E2E suites pass unchanged; 012/013/014/015 intact | T001, T002, T019, T037, T038, T040, T042, T043, T044 |

**Coverage summary**: 19/19 FRs covered; 7/7 SCs covered. FR/SC gaps: **none**.
