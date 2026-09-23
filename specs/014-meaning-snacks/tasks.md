---
description: "Task list for feature 014-meaning-snacks"
---

# Tasks: "Generate snack" from a Keyword Definition into the Snack Backbone (No Quiz)

**Input**: Design documents from `/specs/014-meaning-snacks/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: Tests are **required** for this feature (the spec's Success Criteria and the plan's Testing Strategy explicitly demand them). Test tasks are written first, before the corresponding implementation, per TDD ordering.

**Organization**: Tasks are grouped by user story (US1–US4) so each story can be implemented and verified independently, and map to the plan's milestones **M1** (domain + persistence), **M2** (prompt + provider), **M3** (service + API), **M4** (frontend model + card action), **M5** (backbone integration + distinct rendering), **M6** (security + regression + E2E).

**⚠️ HIGHEST-RISK TASK**: the `snacks.component` backbone refactor (T036) — renaming `items` → `batchItems` and adding the `entries` interleave. Every existing batch/poll/retry/progressive-delivery code path MUST remain byte-for-byte unchanged; only the index/navigation projection changes. See the task and the Dependencies section.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Which user story this task belongs to (US1–US4)
- Every task includes exact file path(s) and the FR/SC references it satisfies

## Path Conventions

- **Backend**: `backend/src/main/...`, `backend/src/test/...`
- **Frontend**: `frontend/src/app/...`, `frontend/e2e/...`
- **Feature docs**: `specs/014-meaning-snacks/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm constraints and record the pre-change baseline before any story work.

- [X] T001 [P] Confirm no new runtime dependency is required (no `backend/pom.xml` and no `frontend/package.json` change) per plan.md §Technical Context / research.md R9/R10; record the pre-change baseline by running `mvn test` in `backend/` and `npx playwright test` in `frontend/` (baseline for FR-026, SC-009).
- [X] T002 [P] Confirm the additive-schema baseline: verify `backend/src/main/resources` has no migration tool, that Hibernate `ddl-auto=update` will only add the new `meaning_snack` table, and record the pre-existing `theme`/`subtopic`/`snack`/`quiz_question`/`study_session` tables and representative rows to prove backward compatibility (FR-019, SC-007, data-model.md §5).

---

## Phase 2: Foundational (Blocking Prerequisites) — Milestone M1

**Purpose**: The domain enum, the new `MeaningSnack` aggregate and its persistence layer that US1's service (T019) and US3's reload behaviour both depend on. **Additive new table only; no existing entity/column touched.**

**⚠️ CRITICAL**: US1's service/API tasks (T019–T024) and US3 cannot complete until this phase is done. **M2 (prompt + provider, T014–T018) is independent of this phase and may run in parallel** (plan.md: M1 and M2 parallelizable).

- [X] T003 [P] Create `backend/src/main/java/com/endia/domain/enums/SnackKind.java` as `public enum SnackKind { MEANING }` — the typed kind marker persisted on the entity and carried in the API so no surface can conflate a meaning snack with a regular one (FR-016, FR-017, data-model.md §1).
- [X] T004 [P] Create `backend/src/test/java/com/endia/application/integration/MeaningSnackPersistenceIntegrationTest.java` (Testcontainers MySQL 8, tagged `integration`, `ddl-auto=create-drop`) — **write first, must FAIL (compilation)**: a saved `MeaningSnack` reloads with identical `sourceTerm`/`sourceDefinition` and `related` JSON; the `findBySourceSubtopicIdAndSourceTermIgnoreCase` query matches case-insensitively; the `ux_meaning_snack_source` unique index rejects a duplicate `(source_subtopic_id, source_term)`; pre-existing `snack`/`theme`/`subtopic` rows are unaffected (FR-017, FR-019, SC-007, data-model.md §5).
- [X] T005 Create `backend/src/main/java/com/endia/domain/model/MeaningSnack.java` as `@Entity @Table(name = "meaning_snack", indexes = {...})` with `id`, `@ManyToOne(LAZY, optional=false) Theme theme`, `@ManyToOne(LAZY, optional=false) Subtopic sourceSubtopic`, `sourceTerm` (VARCHAR 500), `sourceDefinition` (VARCHAR 200), `concept`/`metaphor`/`whyItMatters` (TEXT), `@Convert(JpaJsonConverter) List<String> related`, `@Enumerated(STRING) SnackKind kind = MEANING`, `@PrePersist createdAt`; one-directional `@ManyToOne` only (no back-reference on `Theme`/`Subtopic`); **no quiz fields** (FR-007, FR-008, FR-014, FR-016, FR-017, FR-019, data-model.md §2). **Depends on T003.**
- [X] T006 Create `backend/src/main/java/com/endia/infrastructure/persistence/JpaMeaningSnackRepository.java` extending `JpaRepository<MeaningSnack, UUID>` with `List<MeaningSnack> findByThemeIdOrderByCreatedAtAsc(UUID themeId)` and `Optional<MeaningSnack> findBySourceSubtopicIdAndSourceTermIgnoreCase(UUID subtopicId, String term)` (FR-006, FR-017, FR-018, data-model.md §2/§4.4). **Depends on T005.**

**Checkpoint**: The `MEANING` kind, the `MeaningSnack` entity (no quiz fields) and its repository exist; the new table is additive and pre-existing data is untouched.

---

## Phase 3: User Story 1 — Generate a meaning snack from a keyword definition card (Priority: P1) 🎯 MVP

**Goal**: The definition card gains a "Generate snack" action with loading/error/done states; a new externalized prompt and provider-agnostic port method generate the `concept/metaphor/whyItMatters/related` content from the keyword `term`+`definition`; a new `MeaningSnackService` normalizes, idempotently generates and persists it; a new `/api/meaning-snacks` endpoint group exposes generate + list; the frontend model, service and card action are wired on the snacks, results and history hosts. This phase covers milestones **M2 (prompt + provider)**, **M3 (service + API)** and **M4 (frontend model + card action)**.

**Independent Test**: Render the prompt and assert the meaning-snack instructions; feed a meaning-snack model response through the provider and service and assert normalization, one re-roll and idempotency; call the generate/list endpoints via MockMvc and assert the 12-field no-quiz shape; open a keyword definition card on all three surfaces, activate "Generate snack", and observe loading → done (or error with no entry added).

### Tests for User Story 1 (write first, must FAIL) ⚠️

- [X] T007 [P] [US1] Create `backend/src/test/java/com/endia/infrastructure/llm/GenerateMeaningSnackPromptTest.java` asserting the rendered `generate-meaning-snack.md` substitutes `{{theme}}`/`{{term}}`/`{{definition}}`, requests exactly the `{concept, metaphor, whyItMatters, related}` JSON shape, forbids quiz questions/options/answers and a `keywords` array, and preserves the feature-012 formatting/forbidden-markup rules (FR-002, FR-007, FR-008, SC-001).
- [X] T008 [P] [US1] Extend `backend/src/test/java/com/endia/infrastructure/llm/OpenAiLlmProviderTest.java` asserting a well-formed meaning-snack response parses into `GeneratedMeaningSnack`, a missing `related` yields an empty list, a malformed overall response throws `MALFORMED`, and an emitted `keywords` field is ignored (FR-002, FR-007, FR-026, SC-001).
- [X] T009 [P] [US1] Extend `backend/src/test/java/com/endia/infrastructure/llm/RetryingLlmProviderTest.java`: implement `generateMeaningSnack` on `StubProvider` and assert a retryable failure retries and rethrows the typed cause (FR-002, FR-026).
- [X] T010 [P] [US1] Create `backend/src/test/java/com/endia/application/service/MeaningSnackServiceTest.java` (Mockito): blank term/definition rejected; definition truncated to the first 3 whitespace tokens; an existing meaning snack is returned with `alreadyExisted=true` and the provider is **never** called; invalid core re-rolls exactly once then 503; a valid generation persists a `MEANING` entity bound to theme + source subtopic; the service never references `QuizService`/batch (FR-002, FR-004, FR-005, FR-006, FR-007, FR-008, FR-014, SC-001, SC-002, SC-003, SC-005).
- [X] T011 [P] [US1] Create `backend/src/test/java/com/endia/interfaces/rest/MeaningSnackContractTest.java` (MockMvc standalone): `POST /api/meaning-snacks/generate` returns `alreadyExisted` + `meaningSnack` with `kind:"MEANING"`, the source fields and **exactly the 12 expected fields (no quiz fields, no `keywords`)**; `GET /api/meaning-snacks?themeId=` returns `{themeId, meaningSnacks}`; validation → 400 `VALIDATION_ERROR`; unknown theme/subtopic → 404 `NOT_FOUND`; LLM failure → 503 `SERVICE_UNAVAILABLE` with `cause` (FR-001, FR-004, FR-005, FR-006, FR-008, FR-016, SC-001, SC-003, SC-005).
- [X] T012 [P] [US1] Create `frontend/e2e/meaning-snacks.spec.ts` with route-mocked fixtures covering the action/loading/failure/duplicate blocks: the `Generate snack` button is present on an open card; a delayed `POST` mock shows the loading state; a success adds one entry; a 503/error mock shows a meaningful error and adds **no** entry (index length unchanged); a rapid double-click issues one request; reopening a card for an already-generated keyword shows the done state and does not duplicate (FR-001, FR-003, FR-004, FR-005, FR-006, SC-001, SC-002, SC-003).
- [X] T013 [P] [US1] Extend `frontend/e2e/helpers.ts` with `setupMeaningSnacksMock(page, { list?, generate? })` and add a default `GET /api/meaning-snacks` → `{themeId, meaningSnacks: []}` to `setupAllMocks` so every existing spec remains deterministic (FR-020, SC-006, SC-009).

### Implementation for User Story 1 — M2: Prompt + provider

- [X] T014 [P] [US1] Create `backend/src/main/resources/prompts/generate-meaning-snack.md` — provider-agnostic; asks for exactly `{concept, metaphor, whyItMatters, related}` (no `keywords`), expanded from the supplied term and ≤3-word definition; keeps the feature-012 formatting rules (bold, short paragraphs, `- ` bullets) and the forbidden-markup list; explicitly forbids quiz questions/options/answers (FR-002, FR-007, FR-008). **(M2 — may run in parallel with Phase 2.)**
- [X] T015 [US1] Extend `backend/src/main/java/com/endia/application/port/LlmProvider.java`: add `GeneratedMeaningSnack generateMeaningSnack(String theme, String term, String definition)` and `record GeneratedMeaningSnack(String concept, String metaphor, String whyItMatters, List<String> related) {}` (FR-002, FR-007, FR-008, FR-026). **(M2 — may run in parallel with Phase 2.)**
- [X] T016 [P] [US1] Extend `backend/src/main/java/com/endia/infrastructure/llm/LlmPromptTemplates.java` with `generateMeaningSnack(theme, term, definition)` loading `prompts/generate-meaning-snack.md` and replacing `{{theme}}`/`{{term}}`/`{{definition}}` (FR-002, FR-007, FR-008). **Depends on T014.**
- [X] T017 [US1] Extend `backend/src/main/java/com/endia/infrastructure/llm/OpenAiLlmProvider.java` to implement `generateMeaningSnack` using the same defensive parse style as `generateSnack` (missing fields → `null`/empty; malformed overall → `MALFORMED`; emitted `keywords` ignored) (FR-002, FR-007, FR-008). **Depends on T015, T016.**
- [X] T018 [US1] Extend `backend/src/main/java/com/endia/infrastructure/llm/RetryingLlmProvider.java` to delegate `generateMeaningSnack` through its existing `retry(...)` (FR-002, FR-026). **Depends on T015.**

### Implementation for User Story 1 — M3: Service + API

- [X] T019 [US1] Create `backend/src/main/java/com/endia/application/service/MeaningSnackService.java`: resolve theme/subtopic (404); normalize term and truncate definition to the first 3 whitespace tokens; idempotency via `findBySourceSubtopicIdAndSourceTermIgnoreCase` (return existing, `alreadyExisted=true`, **no LLM call**); else call `llmProvider.generateMeaningSnack(...)`, validate non-empty core + `related` 1..3, re-roll once then 503; persist a `MEANING` entity; **never** call `QuizService`/`BatchService`/`StudySessionItem`/`QuizQuestion` (FR-001, FR-002, FR-004, FR-005, FR-006, FR-007, FR-008, FR-014, FR-015, FR-016, FR-017, data-model.md §4). **Depends on T005, T006, T015.**
- [X] T020 [P] [US1] Create `backend/src/main/java/com/endia/interfaces/rest/dto/MeaningSnackItem.java` with the 12 fields (`id`, `kind`, `themeId`, `sourceSubtopicId`, `sourceSubtopicName`, `sourceTerm`, `sourceDefinition`, `concept`, `metaphor`, `whyItMatters`, `related`, `createdAt`) and `static MeaningSnackItem from(MeaningSnack, String subtopicName)`; **deliberately no `subtopicId` quiz target, no `quizStatus`, no `quizQuestions`, no `keywords`** (FR-008, FR-016, SC-005, data-model.md §6). **Depends on T005.**
- [X] T021 [P] [US1] Create `backend/src/main/java/com/endia/interfaces/rest/dto/MeaningSnackGenerateApiRequest.java` as `record(@NotNull UUID themeId, @NotNull UUID subtopicId, @NotBlank String term, @NotBlank String definition)` (FR-001, FR-004).
- [X] T022 [P] [US1] Create `backend/src/main/java/com/endia/interfaces/rest/dto/MeaningSnackGenerateApiResponse.java` as `record(boolean alreadyExisted, MeaningSnackItem meaningSnack)` (FR-005, FR-006).
- [X] T023 [P] [US1] Create `backend/src/main/java/com/endia/interfaces/rest/dto/MeaningSnackListApiResponse.java` as `record(UUID themeId, List<MeaningSnackItem> meaningSnacks)` (FR-018, FR-020).
- [X] T024 [US1] Create `backend/src/main/java/com/endia/interfaces/rest/MeaningSnackController.java` (`@RequestMapping("/api/meaning-snacks")`): `POST /generate` → 200 `MeaningSnackGenerateApiResponse` (idempotent), `GET ?themeId=` → 200 `MeaningSnackListApiResponse` (empty list allowed); errors use the existing `GlobalExceptionHandler` shapes (400/404/503) (FR-001, FR-004, FR-005, FR-006, FR-014, FR-015, FR-016, FR-018, FR-020). **Depends on T019, T020, T021, T022, T023.**

### Implementation for User Story 1 — M4: Frontend model + card action

- [X] T025 [P] [US1] Create `frontend/src/app/models/meaning-snack.model.ts` with `MeaningSnackKind`, `MeaningSnack`, `MeaningSnackGenerateResponse` and `MeaningSnackListResponse` mirroring the API (FR-007, FR-008, FR-016, data-model.md §7).
- [X] T026 [US1] Create `frontend/src/app/services/meaning-snack.service.ts`: `generate(themeId, subtopicId, term, definition)`, `listByTheme(themeId)`, `generatedKeys = signal<ReadonlySet<string>>(...)`, `hasGenerated(subtopicId, term)`, `markGenerated(snack)` keyed by `${subtopicId}::${term.toLowerCase()}` (FR-001, FR-005, FR-006, FR-018, FR-020). **Depends on T025.**
- [X] T027 [US1] Extend `frontend/src/app/services/app-state.service.ts` with `meaningSnacks = signal<MeaningSnack[]>([])`, reset in `reset()`; **do not** route meaning snacks into `snacks()` (FR-018, FR-024). **Depends on T025.**
- [X] T028 [US1] Extend `frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.ts` with `generateEnabled = input<boolean>(true)`, `generateState = input<'idle'|'loading'|'done'>('idle')`, `generateError = input<string|null>(null)`, `generateRequested = output<SnackKeyword>()`; leave feature-013 open/dismiss/focus code untouched (FR-001, FR-003, FR-004, FR-005, FR-006, FR-025).
- [X] T029 [P] [US1] Extend `frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.html` with an action row below the definition: a `Generate snack` button, a spinner + `Generating…` while loading (disabled/guarded), a disabled `Added to your snacks ✓` when done, and an inline `role="alert"` error when `generateError` is set; keyword text remains escaped interpolation, **no `[innerHTML]`** (FR-001, FR-003, FR-004, FR-006, FR-021).
- [X] T030 [P] [US1] Extend `frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.scss` with the action/loading/done/error styles (FR-001, FR-003).
- [X] T031 [US1] Wire the shared card action in `frontend/src/app/pages/results/results.component.ts` + `.html` using `recap.subtopicId` + `AppState.theme()?.themeId` through the shared `MeaningSnackService`, with the same loading/error/done state (FR-001, FR-003, FR-004, FR-005, FR-006, FR-025). **Depends on T026, T028, T029, T030.**
- [X] T032 [US1] Wire the shared card action in `frontend/src/app/pages/history/history.component.ts` + `.html` using `theme.themeId` + `subtopic.id`, leaving the read-only history data shape untouched (FR-001, FR-003, FR-004, FR-005, FR-006, FR-016, FR-025). **Depends on T026, T028, T029, T030.**

**Checkpoint**: A keyword definition card on the snacks, results and history surfaces shows "Generate snack"; activating it generates and persists a meaning snack (or idempotently returns the existing one) and exposes it through `POST/GET /api/meaning-snacks`.

---

## Phase 4: User Story 2 — Meaning snacks are visually distinct and offer no quiz (Priority: P1)

**Goal**: The snacks backbone holds regular `BatchItem[]` and meaning entries together via a pure `entries` interleave, renders meaning entries with a distinct card variant + always-present badge, and offers no quiz affordance for them, while regular snacks keep their existing behaviour. This phase covers milestone **M5 (backbone integration + distinct rendering)**.

**Independent Test**: Place a meaning snack and a regular snack in the backbone and assert the meaning entry carries `snack-card--meaning` + a badge and **no** `.quiz-btn`, the regular entry keeps its `.quiz-btn`, selecting a meaning entry shows content (never blank), and Previous/Next and disabled states stay correct across a mixed list at first/middle/last positions.

### Tests for User Story 2 (write first, must FAIL) ⚠️

- [X] T033 [P] [US2] Extend `frontend/e2e/meaning-snacks.spec.ts` with the distinct/no-quiz block: a mixed backbone renders the meaning entry with `snack-card--meaning` + a text badge and a colour distinct from a regular card, and **no** `.quiz-btn`; the regular card keeps its `.quiz-btn`; selecting a meaning entry shows its content and never a blank card (FR-012, FR-013, FR-015, SC-004, SC-005).
- [X] T034 [P] [US2] Extend `frontend/e2e/meaning-snacks.spec.ts` with the ordering block: a meaning snack for the first/middle/last source subtopic keeps Previous/Next and their disabled states correct; unmatched meaning entries append at the end; selecting any entry never lands on a blank card (FR-010, FR-011, FR-013, SC-004).

### Implementation for User Story 2

- [X] T035 [P] [US2] Extend `frontend/src/app/models/batch.model.ts` with `RegularEntry`, `MeaningEntry` and `BackboneEntry` (`entryId` = `subtopicId` for regular, `meaning:<id>` for meaning); keep the existing `BatchItem` interface for the batch payload/`AppStateService` (FR-010, FR-011, FR-013, data-model.md §7).
- [X] T036 [US2] **⚠️ HIGHEST-RISK REFACTOR** — edit `frontend/src/app/pages/snacks/snacks.component.ts`: rename the current `items` signal to `batchItems` (regular `BatchItem[]`) and **keep every existing batch/poll/retry/progressive-delivery code path byte-for-byte unchanged**; add `meaningSnacks` + `generationState`; add the pure `entries = computed<BackboneEntry[]>` interleave (meaning entries immediately after their source subtopic's regular entry, unmatched appended at the end, deduped by `id`); clamp `currentIndex` against `entries().length`; `currentEntry = entries()[currentIndex]`; `hasPrev`/`hasNext` use `entries`; `canNext` allows a meaning next entry; `showEmptyState` requires `entries().length === 0`; `selectIndex` allows meaning entries unconditionally and regular entries only when `READY`; `startQuiz()` early-returns unless `currentEntry.kind === 'regular'`; `init()` fetches `meaningSnackService.listByTheme(themeId)` in **both** the return and fresh-batch paths and on error sets `[]`; `syncAppStateSnacks()` continues to store **only** regular snacks; add `onGenerateRequested(keyword)` with at most one in-flight request per keyword (FR-010, FR-011, FR-012, FR-013, FR-015, FR-018, FR-020, SC-004, SC-005, SC-006). **Depends on T026, T028, T035.**
- [X] T037 [US2] Edit `frontend/src/app/pages/snacks/snacks.component.html`: key the index list by `entry.entryId`; a meaning entry shows its source term + `snack-index__item--meaning` badge; branch the card so `kind === 'regular'` renders the existing card unchanged and `kind === 'meaning'` renders `<article class="snack-card snack-card--meaning">` with a `Meaning snack` badge, the source term/definition and the concept/metaphor/why/related content via `<app-rich-text>` (**no keywords**, **no `.quiz-btn`**); add the card generation bindings (FR-010, FR-012, FR-013, FR-015, FR-021). **Depends on T036.**
- [X] T038 [P] [US2] Edit `frontend/src/app/pages/snacks/snacks.component.scss` and `frontend/src/styles.scss`: add `--meaning-accent`, `--meaning-accent-soft`, `--meaning-ink` tokens for light and dark plus `.snack-card--meaning` / `.snack-index__item--meaning` / `.snack-card__badge--meaning` rules; the distinction is not colour-only (an always-present text badge) to satisfy WCAG 1.4.1 (FR-012, SC-004).

**Checkpoint**: Meaning snacks render distinctly in the backbone alongside regular snacks with no quiz affordance, correct ordering, and unchanged regular batch/poll/retry behaviour.

---

## Phase 5: User Story 3 — Meaning snacks persist and survive a reload/new session (Priority: P2)

**Goal**: A generated meaning snack is persisted (M1) and re-loaded through the theme-scoped listing on every page load (M5), remaining present after a reload/new session for the same theme, with graceful degradation when the listing fails; regular snacks and the existing snack/quiz flow are unregressed. This phase covers the reload/degradation half of milestone **M6**.

**Independent Test**: Generate a meaning snack, reload `/snacks`, and assert it is re-listed from `GET /api/meaning-snacks` with its treatment intact; with the listing failing/returning `[]`, assert regular snacks still render with no crash/broken entry; run the existing suites unchanged.

### Tests for User Story 3 (write first, must FAIL) ⚠️

- [X] T039 [P] [US3] Extend `frontend/e2e/meaning-snacks.spec.ts` with the persistence/reload block: generate, reload `/snacks`, and assert the entry is re-listed from the theme-listing mock with its distinct treatment and no-quiz behaviour intact (FR-018, SC-006).
- [X] T040 [P] [US3] Extend `frontend/e2e/meaning-snacks.spec.ts` with the graceful-degradation block: a failing/empty `GET /api/meaning-snacks` still renders regular snacks with no crash and no broken entry (FR-020, SC-006).

### Implementation for User Story 3

- [X] T041 [US3] Verify and harden the reload wiring in `frontend/src/app/pages/snacks/snacks.component.ts`: `init()` fetches `listByTheme` on both the return and fresh-batch paths, caches results in `AppStateService.meaningSnacks`, marks `generatedKeys`, and treats any listing error as `[]` without affecting the regular batch flow (FR-018, FR-020, SC-006). **Depends on T036, T039, T040.**
- [X] T042 [US3] Non-regression verification: confirm `syncAppStateSnacks()` stores only regular snacks and the meaning listing never touches the regular quiz flow; add the default empty meaning-listing mock to every existing spec's setup (via T013) and confirm the existing suites pass unchanged (FR-024, FR-026, SC-009). **Depends on T013, T036.**

**Checkpoint**: Persisted meaning snacks survive reload/new sessions for the same theme; a failing listing degrades to regular snacks only; no regression.

---

## Phase 6: User Story 4 — Meaning snack and keyword content are always rendered as safe, escaped text (Priority: P1)

**Goal**: Keyword `term`/`definition` and generated meaning-snack content are untrusted model output; they render through the feature-012 `RichTextComponent` (typed AST, no `[innerHTML]`, no `DomSanitizer`) and escaped interpolation only, on every surface. This phase covers the security portion of milestone **M6**.

**Independent Test**: Feed `<script>`, `<img onerror=...>`, `javascript:` URLs, `<iframe>`, `<style>` and inline styles into the source term/definition and generated content on all surfaces; assert nothing executes, no nodes are injected, and readable text is preserved.

### Tests for User Story 4 (write first, must FAIL) ⚠️

- [X] T043 [P] [US4] Extend `frontend/e2e/meaning-snacks.spec.ts` with a security block: payloads (`<script>window.__xss=1</script>`, `<img src=x onerror="window.__xss=1">`, `javascript:` URLs, `<iframe>`, `<style>`, inline styles) in the source term/definition and generated content; assert `window.__xss` stays `undefined`, no injected `<script>`/`<img>`/`<iframe>`/`<style>`/`<a>` nodes exist, and readable text is preserved (FR-021, FR-022, FR-023, SC-008).
- [X] T044 [P] [US4] Extend the same security block to the definition card on the results and history surfaces, asserting escaped card text and the same neutralization guarantees (FR-021, FR-025, SC-008).

### Implementation for User Story 4

- [X] T045 [US4] Audit the meaning-snack rendering paths (`frontend/src/app/pages/snacks/snacks.component.html`, `frontend/src/app/components/keyword-definition-card/*`, `frontend/src/app/components/rich-text/*`) to confirm there is no `[innerHTML]`/`DomSanitizer`, that meaning content is emitted through `<app-rich-text>` (typed AST) and term/definition as escaped interpolation, and add a short boundary comment documenting this (FR-009, FR-021, FR-022, FR-023, SC-008).

**Checkpoint**: Meaning-snack and keyword text are safe; injection payloads are inert on every surface.

---

## Phase 7: Polish & Cross-Cutting Concerns — Milestone M6 close-out

**Purpose**: Final validation and cleanup across all stories.

- [X] T046 [P] Run the quickstart.md manual verification checklist (`specs/014-meaning-snacks/quickstart.md` §5) against all surfaces, including the security payload table and the mixed-ordering/desktop/mobile checks (SC-001..SC-009).
- [X] T047 [P] Confirm the rollback path: dropping `meaning_snack` (`DROP TABLE meaning_snack;`) and reverting the entity/enum/repository/service/controller/DTO/prompt/port/provider and frontend changes requires no data rewrite and is lossless (FR-019, SC-007, data-model.md §5, quickstart.md §6).
- [X] T048 [P] Verify checklist-to-task traceability: every FR-001..FR-026 and SC-001..SC-009 is covered by at least one task, every task maps to a requirement (no orphans), and `checklists/requirements.md`, `checklists/security.md` and `checklists/ux.md` remain consistent with tasks.md.
- [X] T049 Run the full backend suite: `cd backend && ./mvnw test` (fall back to `mvn test` if no wrapper is present) and, where Docker is available, `./mvnw verify -Pintegration` (FR-024, SC-009).
- [X] T050 Run the full frontend gate: `cd frontend && npm run build` and `npx playwright test`; confirm the new spec passes and all existing suites pass unchanged (FR-024, SC-009).
- [X] T051 Final completion validation: run `cd backend && ./mvnw test` (fallback `mvn test`) and the frontend build/lint (`cd frontend && npm run build` and `npx prettier --check .` — the repo has no ESLint script), confirming all tasks above are complete and every checklist item is checked (FR-026, SC-009).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2, M1)**: Depends on Setup — **blocks US1's service/API tasks (T019–T024) and US3**.
- **US1 (Phase 3, M2 + M3 + M4)**: M2 (T014–T018) depends on Setup only and is **independent of Foundational** (may run in parallel with Phase 2). M3 (T019–T024) depends on M1 (T005/T006) + M2 (T015). M4 (T025–T032) depends on the M3 contract (it can be built against the frozen contract while M3 lands).
- **US2 (Phase 4, M5)**: Depends on US1 M4 (model/service/card) — the backbone consumes the `MeaningSnack` model and the card output.
- **US3 (Phase 5, M6)**: Depends on M1 (persistence) + US2 (the snacks-page reload fetch).
- **US4 (Phase 6, M6)**: Depends on US1 M4 + US2 (the surfaces it audits/tests).
- **Polish (Phase 7, M6 close)**: Depends on all desired stories being complete.

### Milestone Mapping

| Milestone | Phase(s) | Key tasks |
|-----------|----------|-----------|
| **M1** Domain + persistence | Foundational | T003, T004, T005, T006 |
| **M2** Prompt + provider | US1 | T007, T008, T009, T014, T015, T016, T017, T018 |
| **M3** Service + API | US1 | T010, T011, T019, T020, T021, T022, T023, T024 |
| **M4** Frontend model + card action | US1 | T012, T025, T026, T027, T028, T029, T030, T031, T032 |
| **M5** Backbone integration + distinct rendering | US2 | T033, T034, T035, T036, T037, T038 |
| **M6** Security + regression + E2E | US3, US4, Polish | T013, T039–T051 |

### User Story Dependencies

- **US1 (P1)**: Can start after Setup; M2 is independent of Foundational; M3 needs M1 + M2; M4 needs the M3 contract.
- **US2 (P1)**: Starts after US1 M4 (needs the `MeaningSnack` model, the service and the card output).
- **US3 (P2)**: Starts after M1 + US2 (persistence and the reload fetch must exist).
- **US4 (P1)**: Starts after US1 M4 + US2 (asserts the card and backbone surfaces).

### Within Each User Story

- Test tasks MUST be written and FAIL before the corresponding implementation task.
- Enum before entity; entity before repository; repository before service.
- Prompt + port before provider implementation; port before retry decorator.
- Service before controller; DTOs before controller.
- Model before service; service before card wiring; card before page wiring.
- `BackboneEntry` union before the `snacks.component` refactor.
- Story complete and validated before moving to the next priority.

### ⚠️ Highest-Risk Task

- **T036 (`snacks.component` refactor)**: the `items` → `batchItems` + `entries` change is the single riskiest task. Every existing batch/poll/retry/progressive-delivery code path MUST stay unchanged; the interleave is a pure `computed`; mixed-ordering E2E (T034) plus the full existing suite (T050) are the guards. Do not alter `syncAppStateSnacks()` semantics.

### Parallel Opportunities

- T001 and T002 (Setup) run in parallel.
- T003 and T004 (Foundational) run in parallel; T005 after T003; T006 after T005.
- **US1 M2 (T014–T018) can run in parallel with the Foundational phase.**
- Within US1 tests: T007, T008, T009, T010, T011, T012, T013 in parallel.
- Within US1 M2: T014 and T016 in parallel; T015 before T017/T018; T017 after T016.
- Within US1 M3: T020, T021, T022, T023 in parallel; T024 after all of them.
- Within US1 M4: T025 in parallel with T028; T029 and T030 in parallel; T031 and T032 in parallel (different pages).
- Within US2: T033 and T034 in parallel; T035 and T038 in parallel; T036 after T035; T037 after T036.
- Within US3: T039 and T040 in parallel; T041 after T036/T039/T040; T042 after T013/T036.
- Within US4: T043 and T044 in parallel; T045 after.
- Polish: T046, T047, T048 in parallel; T049 and T050 can run in parallel; T051 last.

---

## Parallel Example: User Story 1

```bash
# Write all US1 tests together (must fail first):
Task: "Create GenerateMeaningSnackPromptTest"
Task: "Extend OpenAiLlmProviderTest with meaning-snack parsing"
Task: "Extend RetryingLlmProviderTest StubProvider"
Task: "Create MeaningSnackServiceTest"
Task: "Create MeaningSnackContractTest"
Task: "Create frontend/e2e/meaning-snacks.spec.ts"
Task: "Extend frontend/e2e/helpers.ts with meaning-snack mocks"

# Then the M3 DTOs (different files) in parallel:
Task: "Create MeaningSnackItem"
Task: "Create MeaningSnackGenerateApiRequest"
Task: "Create MeaningSnackGenerateApiResponse"
Task: "Create MeaningSnackListApiResponse"
```

## Parallel Example: User Story 2

```bash
# Backbone model and styles can proceed together:
Task: "Extend batch.model.ts with BackboneEntry"
Task: "Add meaning colour tokens to styles.scss + snacks.component.scss"

# Then the refactor and its template:
Task: "Refactor snacks.component.ts (items -> batchItems + entries interleave)"
Task: "Update snacks.component.html (index badge + meaning card variant)"
```

---

## Implementation Strategy

### MVP First (US1 + US2)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (domain + persistence) — can proceed in parallel with M2 (T014–T018).
3. Complete Phase 3: US1 (prompt + provider + service/API + model/card action).
4. Complete Phase 4: US2 (backbone integration + distinct rendering).
5. **STOP and VALIDATE**: Run `frontend/e2e/meaning-snacks.spec.ts` and inspect generated snacks — meaning snacks are generated, persisted, rendered distinctly and quiz-free.
6. Deploy/demo the MVP.

### Incremental Delivery

1. Setup + Foundational → domain/persistence ready.
2. US1 → generate action + endpoint group → validate → demo.
3. US2 → distinct, quiz-free backbone entry → validate → demo.
4. US3 → reload persistence + graceful degradation proven.
5. US4 → security hardening proven.
6. Polish → full gates green.

### Notes

- `[P]` tasks touch different files with no incomplete dependency.
- M1 (domain+persistence) and M2 (prompt+provider) are parallelizable; M3 needs both; M4 needs the M3 contract; M5 needs M4; M6 closes.
- No new runtime dependency, no new layer, one new table, one new endpoint group.
- **Highest risk**: T036 (`snacks.component` refactor) — keep every batch/poll/retry path untouched.
- Commit after each task or logical group; verify tests fail before implementing.

---

## FR Traceability Matrix

Every functional requirement (FR-001–FR-026) maps to at least one task. No gaps.

| FR | Description (short) | User Story | Task(s) |
|----|---------------------|------------|---------|
| FR-001 | Card presents a "Generate snack" action | US1 | T011, T012, T021, T024, T026, T028, T029, T030, T031, T032 |
| FR-002 | Action generates a meaning snack from term+definition | US1 | T007, T008, T009, T010, T014, T015, T016, T017, T018, T019 |
| FR-003 | Loading/in-progress state while generating | US1 | T012, T028, T029, T030, T031, T032 |
| FR-004 | Failure shows a meaningful error; adds nothing | US1 | T010, T011, T012, T019, T021, T024, T028, T029, T031, T032 |
| FR-005 | No concurrent duplicate generation/entries | US1 | T010, T011, T012, T019, T022, T024, T026, T028, T031, T032 |
| FR-006 | Existing meaning snack reflected, no silent duplicate | US1 | T005, T006, T010, T011, T019, T022, T024, T026, T028, T029, T031, T032 |
| FR-007 | Same concept/metaphor/why-it-matters/related shape | US1 | T007, T010, T014, T015, T016, T017, T019, T025 |
| FR-008 | Retains the source term+definition | US1 | T004, T007, T010, T011, T014, T015, T016, T017, T019, T020, T025 |
| FR-009 | Content treated as untrusted, safe allowlist | US4 | T045 |
| FR-010 | Meaning snack is a selectable first-class backbone entry | US2 | T034, T035, T036, T037 |
| FR-011 | Mixed rendering preserves ordering/selection/prev-next/delivery | US2 | T034, T035, T036, T037 |
| FR-012 | Meaning snacks visually distinct (colour and/or badge) | US2 | T033, T036, T037, T038 |
| FR-013 | Selecting a meaning snack never renders a blank card | US2 | T033, T035, T036, T037 |
| FR-014 | No quiz generation/prefetch/storage for a meaning snack | US1 | T010, T019, T024 |
| FR-015 | No quiz entry / "Start Quiz" for a meaning snack | US1, US2 | T019, T024, T033, T036, T037 |
| FR-016 | Never treated as a regular quiz-bearing snack anywhere | US1, US2 | T003, T011, T019, T020, T024, T025, T032 |
| FR-017 | Successfully generated meaning snack is persisted | Foundational | T003, T004, T005, T006, T019 |
| FR-018 | Present after reload / new session for the theme | US1, US2, US3 | T005, T023, T024, T026, T027, T036, T039, T041 |
| FR-019 | Additive/backward-compatible persistence | Foundational, US3 | T002, T004, T006, T047 |
| FR-020 | Retrieval path; graceful degradation on failure/absence | US1, US2, US3 | T013, T023, T024, T026, T036, T040, T041 |
| FR-021 | Escaped text via the safe allowlist; no HTML binding | US1, US4 | T029, T037, T043, T044, T045 |
| FR-022 | Markup/handlers/URLs not executed or injected | US4 | T043, T044, T045 |
| FR-023 | Feature-012/013 sanitization model preserved | US4 | T043, T044, T045 |
| FR-024 | Existing flow/E2E unchanged | US1, US3, Polish | T027, T042, T049, T050 |
| FR-025 | Feature-013 highlight/definition-card behaviour unchanged | US1, US4 | T028, T029, T030, T031, T032, T044 |
| FR-026 | No change to regular generation/provider/batch/quiz | US1, Polish | T001, T008, T009, T015, T018, T042, T051 |

## SC Traceability Matrix

Every success criterion (SC-001–SC-009) maps to at least one task. No gaps.

| SC | Description (short) | Task(s) |
|----|---------------------|---------|
| SC-001 | 100% of cards show the action; 0 render failures | T007, T008, T010, T011, T012, T046 |
| SC-002 | 100% one-entry-per-activation; 0 duplicates | T010, T012, T046 |
| SC-003 | 100% loading state; 0 broken entries on failure | T010, T011, T012, T046 |
| SC-004 | 100% meaning snacks visually distinct; 0 indistinguishable | T033, T034, T038, T046 |
| SC-005 | 0 quizzes generated/stored/offered for meaning snacks | T010, T011, T033, T046 |
| SC-006 | 100% survival after reload/new session; 0 lost/corrupted | T013, T039, T040, T041, T046 |
| SC-007 | 0 existing data invalidated (backward compatible) | T002, T006, T047 |
| SC-008 | 0 executions / 0 injected nodes from content | T043, T044, T045, T046 |
| SC-009 | Non-regression; feature-012/013 intact | T001, T013, T042, T049, T050, T051 |

**Coverage summary**: 26/26 FRs covered; 9/9 SCs covered. FR/SC gaps: **none**.
