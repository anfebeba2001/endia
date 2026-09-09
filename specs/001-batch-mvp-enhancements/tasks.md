---
description: "Task list for the Endia Batch Enhancements feature (async snack delivery, quiz drag-drop, dark mode, resilience, AI subtopics)"
---

# Tasks: Endia MVP — Batch Enhancements

**Input**: Design documents from `/specs/001-batch-mvp-enhancements/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Branch**: `001-batch-mvp-enhancements`

## Organization

This feature makes **cross-cutting** changes to an existing monorepo (Spring Boot backend + Angular frontend), so the phases below follow the plan.md **Phase-by-Phase Implementation Plan (Phase 0 → Phase 8)** as the authoritative task breakdown. Every phase is independently testable.

Each task carries a story label (`[US1]`..`[US5]`) for traceability to `spec.md` user stories, OR **no label** when it is setup / foundational (cross-cutting resilience) / polish (tests & docs). Mapping to spec.md user stories: the spec declares six stories, of which two (drag-drop questions and snack recap) are merged here into one story group because they share the same `QuizQuestion`/`QuizService`/`quiz` artifacts. Story-label correspondence (tasks.md label ↔ spec.md user story):

| tasks.md label | spec.md user story | FR coverage |
|----------------|--------------------|-------------|
| US1 | User Story 1 — async snack delivery + quiz prefetch | FR-001..004 |
| US2 (merged) | User Story 3 (drag-drop questions) + User Story 4 (snack recap) | FR-005..010 |
| US3 | User Story 6 — dark mode | FR-011..014 |
| US4 | User Story 2 — resilience (specific errors + auto-retry) | FR-015..018 |
| US5 | User Story 5 — user-proposed subtopics | FR-019..021 |

### Phase Map (plan phase → conceptual phase)

| Plan phase | Conceptual phase | Story label(s) |
|-----------|------------------|----------------|
| Setup (pre-flight) | Setup | none |
| Phase 0 | Foundational (resilience backend) | none |
| Phase 1 | Domain & Persistence | US1 / US2 / US5 |
| Phase 2 | LLM Port & Prompts | US2 |
| Phase 3 | Application Services | US1 / US2 / US5 |
| Phase 4 | REST Interfaces | US1 / US2 / US5 |
| Phase 5 | Frontend Services & Models | US1 / US2 / US4 / US5 |
| Phase 6 | Frontend Pages & Components | US1 / US2 / US4 / US5 |
| Phase 7 | Dark Mode | US3 |
| Phase 8 | Tests & Docs (Polish) | none |

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US5); absent for setup/foundational/polish

Base paths used below (verified against the repo):

- Backend: `backend/src/main/java/com/endia/`
- Backend tests: `backend/src/test/java/com/endia/`
- Frontend: `frontend/src/app/`
- Frontend E2E: `frontend/e2e/`
- Contracts: `specs/001-batch-mvp-enhancements/contracts/`

> **Note on dark-mode styles file**: the plan references `styles.scss`, but the project's shipped global stylesheet is `frontend/src/app/app.scss`. All dark-mode token overrides target `frontend/src/app/app.scss` (the existing `:root` design tokens live there).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify the project already builds/runs and the environment is correct before any changes. No code is implemented here.

- [X] T001 Verify the project baseline: confirm `backend/pom.xml` + `backend/src/main/resources/application.yml` build and `mvn -q test` passes green, confirm `frontend/package.json` + `frontend/src/app/app.config.ts` build with `npm run build`, and confirm the required env vars (`LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`) are documented in `.env` at repo root. Also confirm the Playwright baseline (`cd frontend && npx playwright test`) is green so SC-012 can be verified later.

**Checkpoint**: Baseline confirmed — implementation can begin.

---

## Phase 0: Foundation & Typed Errors (backend) — Foundational

**Purpose**: Shared backend resilience foundation used by every other phase. **No story label** — these are cross-cutting (FR-015/FR-016/FR-018). **Blocks all user stories.**

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] Create `LlmErrorCause` enum with values `UNAVAILABLE`, `TIMEOUT`, `NETWORK`, `QUOTA`, `MALFORMED` in `backend/src/main/java/com/endia/domain/enums/LlmErrorCause.java` (new package `domain/enums`).
- [X] T003 Create `LlmGenerationException extends DomainException` carrying an `LlmErrorCause` and a `retryable` flag in `backend/src/main/java/com/endia/domain/exception/LlmGenerationException.java` (depends on T002).
- [X] T004 Map concrete failures to typed causes in `backend/src/main/java/com/endia/infrastructure/llm/OpenAiLlmProvider.java`: 429→`QUOTA`, read/connect timeout→`TIMEOUT`, `ResourceAccessException`/connect-refused/DNS→`NETWORK`, 5xx/503→`UNAVAILABLE`, parse/missing-field→`MALFORMED`; throw `LlmGenerationException` (depends on T002, T003).
- [X] T005 Add a `cause` field to the `ErrorResponse` record and a new `@ExceptionHandler(LlmGenerationException.class)` in `backend/src/main/java/com/endia/infrastructure/exception/GlobalExceptionHandler.java` that maps the cause to the matching status (QUOTA→429, MALFORMED→400, else 503) and populates `cause` (depends on T003).
- [X] T006 [P] Add retry config properties and beans to `backend/src/main/java/com/endia/infrastructure/llm/LlmConfig.java`: `batch.retry.*` (`max-attempts`, `initial-backoff-ms`, `backoff-factor`, `max-backoff-ms`, `jitter-ratio`), a `batchExecutor` bounded `ThreadPoolTaskExecutor` bean with a bounded queue, and a `@Bean(primary)` wiring `RetryingLlmProvider` around `OpenAiLlmProvider`.
- [X] T007 Add the `batch.retry.*` config block (with the defaults from `research.md` R4: max-attempts 3, initial-backoff-ms 1000, backoff-factor 2.0, max-backoff-ms 10000, jitter-ratio 0.2) to `backend/src/main/resources/application.yml`.
- [X] T008 Create `RetryingLlmProvider` infrastructure decorator implementing `LlmProvider` in `backend/src/main/java/com/endia/infrastructure/llm/RetryingLlmProvider.java` that catches `LlmGenerationException` with a retryable cause, retries up to `max-attempts` (3 total) with exponential backoff + jitter, then rethrows the final typed exception (depends on T003, T006).
- [X] T009 [P] Document the typed error envelope and the 5 `LlmErrorCause` values in `specs/001-batch-mvp-enhancements/contracts/error-response.md` (add `cause` field, status codes, retry semantics FR-016/FR-017).

**Checkpoint**: Backend resilience foundation ready — typed causes flow through `ErrorResponse.cause` and transient failures auto-retry (≤3 attempts). All user stories can now start.

---

## Phase 1: Domain & Persistence (data model)

### Tests-adjacent note
No new unit tests are required here beyond the persistence-level behavior verified by the service tests in Phase 8 (JPA entities are exercised by the integration tests). Models are written before services.

### User Story 5 — AI Subtopics (FR-019..021) — model

- [X] T010 [P] [US5] Create `SubtopicSource` enum with values `AI_SUGGESTED`, `USER_PROPOSED` in `backend/src/main/java/com/endia/domain/enums/SubtopicSource.java`.
- [X] T011 [US5] Add `private SubtopicSource source` (default `AI_SUGGESTED`, `@Enumerated(EnumType.STRING)`, column `source VARCHAR(20) NOT NULL`) to `backend/src/main/java/com/endia/domain/model/Subtopic.java` (depends on T010).

### User Story 2 — Quiz Enhancements (FR-005..008) — model

- [X] T012 [P] [US2] Create `QuestionType` enum with values `MULTIPLE_CHOICE`, `DRAG_DROP` in `backend/src/main/java/com/endia/domain/enums/QuestionType.java`.
- [X] T013 [P] [US2] Create `DragDropVariant` enum with values `SEQUENCING`, `CLASSIFICATION`, `MATCHING` in `backend/src/main/java/com/endia/domain/enums/DragDropVariant.java`.
- [X] T014 [US2] Create the `DragDropContent` record (fields `variant`, `items` List, `categories` List (CLASSIFICATION), `pairs` Map (MATCHING), `correctOrder` List (SEQUENCING)) in `backend/src/main/java/com/endia/domain/model/DragDropContent.java` (depends on T013).
- [X] T015 [US2] Extend `backend/src/main/java/com/endia/domain/model/QuizQuestion.java` with `question_type` (`QuestionType`, default `MULTIPLE_CHOICE`), `variant` (`DragDropVariant`, nullable), and `drag_drop_content` (`DragDropContent`, nullable, `@Convert(JpaJsonObjectConverter.class)`, `columnDefinition="JSON"`); make `correctIndex` nullable (depends on T012, T013, T014).
- [X] T016 [P] [US2] Create the general-purpose `JpaJsonObjectConverter` (`AttributeConverter<Object,String>`, Jackson-based, rendered for `DragDropContent`) in `backend/src/main/java/com/endia/infrastructure/persistence/JpaJsonObjectConverter.java` (keep `JpaJsonConverter` for `List<String>`).
- [X] T017 [US2] Modify `backend/src/main/java/com/endia/domain/model/AnswerAttempt.java`: make `chosenIndex` nullable, add `arrangement` (`List<String>`, JSON, nullable) for drag-drop, and relax the unique constraint from `(quiz_question_id, chosen_index)` to `(quiz_question_id)` only via the `@Table` `uniqueConstraints` (depends on T015).

### User Story 1 — Async Snack Delivery (FR-001..004) — model

- [X] T018 [P] [US1] Create `GenerationStatus` enum with values `PENDING`, `GENERATING`, `READY`, `FAILED` in `backend/src/main/java/com/endia/domain/enums/GenerationStatus.java`.
- [X] T019 [P] [US1] Create `BatchStatus` enum with values `IN_PROGRESS`, `PARTIAL`, `COMPLETE`, `FAILED` in `backend/src/main/java/com/endia/domain/enums/BatchStatus.java`.
- [X] T020 [US1] Create the `StudySession` entity (fields: `id` UUID, `theme` Theme FK, `status` `BatchStatus` default `IN_PROGRESS`, `createdAt`, `items` `@OneToMany` cascade ALL + orphanRemoval) in `backend/src/main/java/com/endia/domain/model/StudySession.java` (depends on T019).
- [X] T021 [US1] Create the `StudySessionItem` entity (fields: `id`, `session` FK, `subtopic` FK, `snackStatus`/`quizStatus` `GenerationStatus`, `snack` nullable FK, `snackCause`/`quizCause` `LlmErrorCause` nullable) in `backend/src/main/java/com/endia/domain/model/StudySessionItem.java` (depends on T018, T020).
- [X] T022 [P] [US1] Create `JpaStudySessionRepository extends JpaRepository<StudySession, UUID>` in `backend/src/main/java/com/endia/infrastructure/persistence/JpaStudySessionRepository.java`.
- [X] T023 [US1] Create `JpaStudySessionItemRepository extends JpaRepository<StudySessionItem, UUID>` (needed to reload per-item status) in `backend/src/main/java/com/endia/infrastructure/persistence/JpaStudySessionItemRepository.java` (depends on T021).

### User Story 2 — persistence queries for drag-drop + recap

- [X] T024 [US2] Add `List<QuizQuestion> findBySubtopicIdAndType(UUID subtopicId, QuestionType type)` to `backend/src/main/java/com/endia/infrastructure/persistence/JpaQuizQuestionRepository.java`.
- [X] T025 [US2] Add `Optional<Snack> findBySubtopicId(UUID subtopicId)` to `backend/src/main/java/com/endia/infrastructure/persistence/JpaSnackRepository.java` (used by the end-of-test recap).

---

## Phase 2: LLM Port & Prompts

### User Story 2 — Quiz Enhancements (FR-005..007)

- [X] T026 [US2] Extend the `LlmProvider` port in `backend/src/main/java/com/endia/application/port/LlmProvider.java`: add `type`, `variant`, `dragDropContent` to the `GeneratedQuestion` record, and change `generateQuiz(...)` to accept a `DragDropVariant variant` parameter (depends on T012, T013, T014).
- [X] T027 [US2] Rewrite `backend/src/main/resources/prompts/generate-quiz.md` to instruct a JSON-schema mix of MCQ + drag-drop questions of the given variant (sequencing/classification/matching items + canonical arrangement), including the exact `DragDropContent` JSON shape and the "never send the correct arrangement pre-answer" rule (depends on T013, T014).
- [X] T028 [US2] Modify `backend/src/main/java/com/endia/infrastructure/llm/LlmPromptTemplates.java` to render the `{{variant}}` placeholder and the variant-specific drag-drop instruction block from the prompt (depends on T027).
- [X] T029 [US2] Modify `OpenAiLlmProvider.generateQuiz(...)` in `backend/src/main/java/com/endia/infrastructure/llm/OpenAiLlmProvider.java` to accept the `variant`, parse the mixed-type JSON (MCQ + drag-drop with `items`/`categories`/`pairs`/`correctOrder`) into typed `GeneratedQuestion` records, and treat unparsable/missing drag-drop fields as `MALFORMED` (depends on T026, T027, T028).

---

## Phase 3: Application Services

### User Story 2 — Quiz enhancements (FR-005..010)

- [X] T030 [US2] Extend `backend/src/main/java/com/endia/application/service/QuizQualityGate.java` with drag-drop validation: `variant` in allowed set, `items` non-empty + distinct, canonical arrangement set and composed of exactly `items`, `CLASSIFICATION`→categories non-empty + every item categorized, `MATCHING`→every left key has a value, `SEQUENCING`→`correctOrder` is a permutation of `items`; reject invalid (re-roll max 1) (depends on T014).
- [X] T031 [US2] Modify `backend/src/main/java/com/endia/application/service/QuizService.java`: pick one `DragDropVariant` via `SecureRandom` per quiz, generate mixed MCQ+drag-drop, grade drag-drop against the stored arrangement per variant, and include the snack `recap` (subtopicName + snack) in `getQuizSummary` (depends on T024, T025, T026, T029, T030).

### User Story 1 — Async snack delivery (FR-001..004)

- [X] T032 [US1] Refactor `backend/src/main/java/com/endia/application/service/SnackService.java` to extract `generateSingleSnack(Subtopic, statusCallback)` (returns/persists one snack and notifies a status callback) so the batch worker reuses it per subtopic without a whole-array API.

### User Story 5 — AI subtopics (FR-019..021)

- [X] T033 [US5] Create `backend/src/main/java/com/endia/application/service/SubtopicService.java` with `addSubtopics(UUID themeId, List<String> names)`: reject blank names, normalize (`trim().toLowerCase()`, collapse internal whitespace), dedupe against `JpaSubtopicRepository.findByThemeId` (any source) and within the request, persist remaining with `source = USER_PROPOSED`, return added + duplicates (depends on T010, T011).

### User Story 1 — Batch orchestration (FR-001..004)

- [X] T034 [US1] Create `backend/src/main/java/com/endia/application/service/BatchService.java`: `createBatch` persists `StudySession` + `StudySessionItem`s and returns immediately, `@Async` worker on `batchExecutor` generates snacks sequentially (updating per-item `snackStatus` + cause) and quizzes in parallel (updating `quizStatus`), then aggregates `BatchStatus` (COMPLETE/PARTIAL/FAILED/IN_PROGRESS); `getStatus(batchId)` returns per-item snack content + cause + the `retrying` flag (depends on T020, T021, T023, T024, T025, T032).

---

## Phase 4: REST Interfaces

### User Story 1 — batch endpoints (FR-001..004)

- [X] T035 [US1] Create the batch DTOs `BatchCreateApiRequest` (`themeId`, `subtopicIds` max 10), `BatchCreateApiResponse` (batchId, themeId, status, items[]), `BatchStatusApiResponse` (batchId, themeId, status, `retrying`, items[] with subtopicId/name/snack/snackStatus/snackCause/quizStatus/quizCause) in `backend/src/main/java/com/endia/interfaces/rest/dto/` (new files `BatchCreateApiRequest.java`, `BatchCreateApiResponse.java`, `BatchStatusApiResponse.java`) (depends on T034).
- [X] T036 [US1] Create `BatchController` in `backend/src/main/java/com/endia/interfaces/rest/BatchController.java` with `POST /api/batches` (returns `202` + batchId, quickly) and `GET /api/batches/{batchId}` (returns aggregate `BatchStatusApiResponse`) (depends on T034, T035).

### User Story 5 — subtopic add endpoint (FR-019..021)

- [X] T037 [US5] Create the `SubtopicAddApiRequest` (`names` array, each 2–200 chars) and `SubtopicAddApiResponse` (themeId, `added[]` with id/name/source, `duplicates[]`) records in `backend/src/main/java/com/endia/interfaces/rest/dto/SubtopicAddApiRequest.java` and `SubtopicAddApiResponse.java` (depends on T033).
- [X] T038 [US5] Create `SubtopicController` in `backend/src/main/java/com/endia/interfaces/rest/SubtopicController.java` with `POST /api/themes/{themeId}/subtopics` delegating to `SubtopicService.addSubtopics` (returns `200` + `SubtopicAddApiResponse`) (depends on T033, T037).

### User Story 2 — quiz answer + summary (FR-008..010)

- [X] T039 [US2] Modify `backend/src/main/java/com/endia/interfaces/rest/dto/QuizAnswerApiRequest.java`: make `chosenIndex` optional/nullable and add a nullable `arrangement` (`List<String>`) with validation that exactly one of the two is supplied per the question `type`.
- [X] T040 [US2] Modify `backend/src/main/java/com/endia/interfaces/rest/dto/QuizSummaryApiResponse.java`: add drag-drop fields to `QuestionBreakdown` (`variant`, `correctArrangement`, `submittedArrangement`, nullable `chosenIndex`) and add a nullable nested `recap` (subtopicId, subtopicName, snack object) record.
- [X] T041 [US2] Modify `backend/src/main/java/com/endia/interfaces/rest/QuizController.java`: pass `arrangement` to `answerQuestion` (branch by type), include drag-drop breakdown + `recap` in `summary`, and keep `/api/quiz/generate` backward-compatible (depends on T039, T040).

### Docs (contracts)

- [X] T042 [P] [US1] Document `specs/001-batch-mvp-enhancements/contracts/batch-create.md` (POST /api/batches request/response, 202/400/404).
- [X] T043 [P] [US1] Document `specs/001-batch-mvp-enhancements/contracts/batch-status.md` (GET /api/batches/{batchId}, per-item snack content/cause, `retrying` flag, stop-polling on terminal states).
- [X] T044 [P] [US5] Document `specs/001-batch-mvp-enhancements/contracts/subtopic-add.md` (POST /api/themes/{themeId}/subtopics, dedup normalization, `duplicates` echo).
- [X] T045 [P] [US2] Update `specs/001-batch-mvp-enhancements/contracts/quiz-generate.md` for the mixed MCQ + drag-drop types, random variant, variant-specific `dragDrop` payload (client never receives the correct arrangement), and quality-gate rules.
- [X] T046 [P] [US2] Update `specs/001-batch-mvp-enhancements/contracts/quiz-answer.md` for the `arrangement` submit + per-variant grading + `correctArrangement` reveal, and the summary `recap` (FR-009/FR-010).
- [X] T047 [P] [US2] Update `specs/001-batch-mvp-enhancements/contracts/snack-generate.md` to note the endpoint stays backward-compatible for E2E while the live flow uses the batch endpoint.

---

## Phase 5: Frontend Services & Models

### User Story 4 — resilience frontend (FR-015, FR-017)

- [X] T048 [P] [US4] Add a nullable `cause?: string;` field to the `ErrorResponse` interface in `frontend/src/app/models/api-response.model.ts`.
- [X] T049 [US4] Modify `frontend/src/app/services/error.service.ts` to map `cause` (`UNAVAILABLE`/`TIMEOUT`/`NETWORK`/`QUOTA`/`MALFORMED`) to the specific friendly message per `error-response.md`, falling back to `message` then status-based text (depends on T048).

### User Story 1 — async snack delivery (FR-001..004)

- [X] T050 [P] [US1] Create `frontend/src/app/models/batch.model.ts` typing `BatchStatusResponse`/`BatchItem` (subtopicId, subtopicName, snackStatus, snack, snackCause, quizStatus, quizCause) and the batch-status enum values.
- [X] T051 [US1] Create `frontend/src/app/services/batch.service.ts` with `createBatch(themeId, subtopicIds)` → `POST /api/batches` and `pollStatus(batchId)` → RxJS `interval(1500).pipe(switchMap(...), takeWhile(status != terminal))` on `GET /api/batches/{id}` (depends on T050).
- [X] T052 [US1] Add batch/progressive-state signals to `frontend/src/app/services/app-state.service.ts`: `batchId`, `currentSnackIndex`, `quizPrefetched`, plus setters/reset (depends on T050).

### User Story 5 — AI subtopics (FR-019..021)

- [X] T053 [P] [US5] Modify `frontend/src/app/models/theme.model.ts`: add `source` to `Subtopic` and add a `SubtopicAddResponse` interface (`themeId`, `added[]`, `duplicates[]`).
- [X] T054 [US5] Modify `frontend/src/app/services/theme.service.ts` to add `addSubtopics(themeId, names)` → `POST /api/themes/{themeId}/subtopics` and merge `added` into the current `subtopics` list (depends on T053).

### User Story 2 — quiz enhancements (FR-008)

- [X] T055 [P] [US2] Modify `frontend/src/app/models/quiz.model.ts`: add `type`/`variant`/`dragDrop` to `QuizQuestion`, add `arrangement` to the answer, and add drag-drop fields (`correctArrangement`, `submittedArrangement`, nullable `chosenIndex`) + `recap` to the summary types.
- [X] T056 [US2] Modify `frontend/src/app/services/quiz.service.ts` to send `arrangement` for drag-drop answers and type the summary with the recap (depends on T055).

---

## Phase 6: Frontend Pages & Components

### User Story 1 — snacks page rewrite (FR-001..004) + spinner (FR-017)

- [X] T057 [US1] Rewrite `frontend/src/app/pages/snacks/snacks.component.{ts,html,scss}` for progressive one-at-a-time delivery: on subtopic confirm trigger one `batch.service.createBatch`, poll status, render `snacks[currentIndex()]` only when `READY`, advance via a "Next →" control only when the next snack is READY (else show a `GENERATING`/retry spinner), and render a per-item FAILED banner with `snackCause` + retry without blocking READY snacks (depends on T051, T052).

### User Story 4 — retry spinner (FR-017)

- [X] T058 [US4] Add the `retrying`-driven spinner: in `frontend/src/app/pages/snacks/snacks.component.{ts,html,scss}` and `frontend/src/app/pages/quiz/quiz.component.{ts,html,scss}`, render a spinner/loading indicator whenever `batchStatus.retrying === true` (or a quiz generation retry is in flight) so the user sees progress during auto-retry (depends on T057).

### User Story 2 — quiz enhancements (FR-005..010)

- [X] T059 [US2] Create the reusable `drag-drop-question` standalone component at `frontend/src/app/components/drag-drop-question/drag-drop-question.component.{ts,html,scss}` (new `components/` dir) rendering sequencing/classification/matching via the native HTML5 Drag-and-Drop API plus a keyboard reorder fallback (Tab + arrow keys), emitting a submitted `arrangement` (depends on T055, T056).
- [X] T060 [US2] Modify `frontend/src/app/pages/quiz/quiz.component.{ts,html,scss}` to branch on `question.type`: render the `drag-drop-question` component for `DRAG_DROP` (submitting `arrangement`) and the existing options UI for `MULTIPLE_CHOICE`, preserving the MCQ flow (depends on T059).
- [X] T061 [US2] Modify `frontend/src/app/pages/results/results.component.{ts,html,scss}` to render the `recap` snack at the end of the results screen, labeled with `recap.subtopicName`, degrading gracefully to the label only when `recap.snack` is null (depends on T055, T056).

### User Story 5 — AI subtopics (FR-019..021)

- [X] T062 [US5] Modify `frontend/src/app/pages/subtopics/subtopics.component.{ts,html,scss}` to add a free-text "add custom subtopic" input that calls `theme.service.addSubtopics`, merges `added` into the selected list, and shows dedup feedback from `duplicates` ("already covered") (depends on T053, T054).

---

## Phase 7: Dark Mode

### User Story 3 — Dark mode (FR-011..014)

- [X] T063 [P] [US3] Create `frontend/src/app/services/theme-preference.service.ts` that reads/writes `localStorage["endia-ui-theme"]` ("light"|"dark"), applies `document.documentElement.dataset.theme`, defaults to light, and silently degrades (no crash) when storage is blocked.
- [X] T064 [US3] Modify `frontend/src/app/app.ts` and `frontend/src/app/app.html` to add a dark-mode toggle button in the header and a handler that calls `theme-preference.service` to switch and persist the theme (depends on T063).
- [X] T065 [US3] Modify `frontend/src/app/app.config.ts` to register an init provider that applies the persisted theme on boot (depends on T063).
- [X] T066 [US3] Modify `frontend/src/app/app.scss` to add a `[data-theme="dark"]` block overriding the existing `:root` custom properties (`--canvas`, `--surface-card`, `--ink`, `--body`, `--muted`, `--hairline*`, `--accent*`, `--success*`, `--error*`, `--gold*`, shadows/glows) with the inverted warm palette from `research.md` R3, plus `color-scheme: dark` (depends on T064).

---

## Phase 8: Tests, Docs & Polish

**Purpose**: Full test coverage, docs, and no-regression verification. **No story label** (cross-cutting).

### Backend unit tests

- [X] T067 [P] Extend `backend/src/test/java/com/endia/application/service/QuizServiceTest.java` with: variant-selection randomness, mixed MCQ+drag-drop generation, per-variant drag-drop grading (sequencing/classification/matching), recap inclusion, and XP parity between types.
- [X] T068 [P] Extend `backend/src/test/java/com/endia/application/service/QuizQualityGateTest.java` with drag-drop validation rules (empty items, incomplete arrangement, dup items, bad variant).
- [X] T069 [P] Extend `backend/src/test/java/com/endia/application/service/SnackServiceTest.java` for the extracted `generateSingleSnack` single-snack path.
- [X] T070 [P] Create `backend/src/test/java/com/endia/application/service/SubtopicServiceTest.java`: dedup normalized (case/whitespace), dedup vs AI-suggested + within request, empty rejection, `source=USER_PROPOSED` persistence.
- [X] T071 [P] Create `backend/src/test/java/com/endia/infrastructure/llm/RetryingLlmProviderTest.java`: success within 3 attempts, exhaustion after 3, and pass-through for non-retryable cause.
- [X] T072 [P] Extend `backend/src/test/java/com/endia/infrastructure/llm/OpenAiLlmProviderTest.java` with cause mapping (429→QUOTA, timeout→TIMEOUT, connect→NETWORK, 503→UNAVAILABLE, parse→MALFORMED).
- [X] T073 [P] Create `backend/src/test/java/com/endia/application/service/BatchServiceTest.java`: status transitions (PENDING→GENERATING→READY/FAILED) and partial-failure isolation.

### Backend integration tests

- [X] T074 [P] Create `backend/src/test/java/com/endia/application/integration/BatchFlowIntegrationTest.java` (Testcontainers MySQL) covering full batch → progressive status → prefetched quiz ready.
- [X] T075 [P] Extend `backend/src/test/java/com/endia/application/integration/QuizFlowIntegrationTest.java` with mixed MCQ+drag-drop generation → drag-drop answer → summary with recap.
- [X] T076 [P] Extend `backend/src/test/java/com/endia/application/integration/ThemeFlowIntegrationTest.java` with user-proposed subtopic persisted with `source` + dedup.

### Frontend Playwright E2E

- [X] T077 [P] Add a Playwright spec in `frontend/e2e/` for progressive delivery: first snack appears before all are ready, and snacks render one card at a time.
- [X] T078 [P] Add a Playwright spec in `frontend/e2e/` for the drag-drop answer flow + results recap labeled with subtopic/snack name.
- [X] T079 [P] Add a Playwright spec in `frontend/e2e/` for dark mode: toggle → reload persists; all pages readable.
- [X] T080 [P] Add a Playwright spec in `frontend/e2e/` for error-cause messages per cause.

### Docs & no-regression

- [X] T081 [P] Update `specs/001-batch-mvp-enhancements/quickstart.md` with the batch run/test flow, the `answer_attempt` unique-constraint manual migration, the `BATCH_RETRY_*` env vars, and the `POST /api/batches` + poll curl flow.
- [X] T082 Run the SC-012 no-regression verification: confirm the existing Playwright suite (`cd frontend && npx playwright test`) still passes unchanged (all 17 existing tests green) and run `cd backend && mvn test` for the full backend suite.
- [X] T083 [P] Clean up feature registry/checklists: request a spec-kit `checklists` pass for this feature and reconcile any generated checklist items against the implemented FR-001..021, and remove any non-applicable cross-check items flagged under `specs/001-batch-mvp-enhancements/`.

**Checkpoint**: All 5 feature groups covered; SC-012 no-regression confirmed.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately; a pre-flight verification before any change.
- **Foundational (Phase 0)**: Depends on Setup completion — **BLOCKS all user stories.** Because the typed error + retry foundation (LlmErrorCause, LlmGenerationException, RetryingLlmProvider) is consumed by US1 and US2, it must land first.
- **User Stories (Phases 1–7)**: All depend on Foundational (Phase 0) completion. The model/persistence sub-tasks for US1 (T018–T023) and US2 (T012–T017) are independent of each other and can run in parallel once Foundational is done. Different stories can then proceed in parallel (if staffed) or in priority order.
- **Polish (Phase 8)**: Depends on all desired user stories being complete.

### Within each story (TDD ordering)

- **Models before services**: entity/enum tasks (Phase 1) precede service tasks (Phase 3/4).
- **Port/prompt before provider parse**: LlmProvider + prompt (Phase 2) precede `OpenAiLlmProvider` parse.
- **Service before REST**: services (Phase 3) precede controllers (Phase 4).
- **Frontend model before service before page**: models (Phase 5) precede services (Phase 5) precede pages/components (Phase 6).
- Services reference only completed models; tests in Phase 8 are written after their implementation target exists (they are executable verification of already-shipped behavior in this cross-cutting batch).

### Inter-story dependency notes (flagged)

- **US4 (resilience frontend)** depends on the Foundational backend typing (`ErrorResponse.cause`, `retrying` flag) and on the pages it augments (snacks/quiz). It has no standalone backend build — it consumes Phase 0 + the Phase 6 spinner surfaces.
- **US2 recap (FR-009/010)** depends on `JpaSnackRepository.findBySubtopicId` (T025) and the snack persistence produced by US1's `generateSingleSnack` path; keep `findBySubtopicId` available.
- **US5** depends on `Subtopic.source` (T010/T011) before `SubtopicService` (T033); the batch worker (T034/US1) treats user-proposed subtopics identically (FR-020) once they share the `subtopic` table.

### Parallel opportunities

- Foundational: T002/T006/T007/T009 are different files and can run in parallel once compiled against the shared enums understand the contract.
- Phase 1 models: T010/T012/T013/T016/T018/T019/T022/T024/T025 are separate files and parallel; T014/T015/T017/T020/T021/T023 chain on their enums.
- Contracts docs T042–T047 are independent doc files — all parallel.
- Frontend models T048/T049/T050/T051 are separate files — parallel.
- Phase 8 tests T067–T080 are separate test files — all parallel.

---

## Parallel Example: User Story wiring

```bash
# Phase 1 models (independent files, parallel):
Task: "T012 QuestionType.java"  →  Task: "T013 DragDropVariant.java"  →  Task: "T016 JpaJsonObjectConverter.java"

# After enums: US1 + US2 + US5 domain in parallel
Task: "T020 StudySession.java"  |  Task: "T015 QuizQuestion.java"  |  Task: "T011 Subtopic.java"

# Phase 8 tests (all parallel):
Task: "T067 QuizServiceTest"  |  Task: "T070 SubtopicServiceTest"  |  Task: "T074 BatchFlowIntegrationTest"
```

---

## Implementation Strategy

### MVP-First Incremental

1. Complete Setup (T001).
2. Complete Foundational (Phase 0: T002–T009) — **CRITICAL**, blocks everything.
3. Build US1 (async snack delivery + quiz prefetch) end-to-end: models → SnackService/batch worker → BatchController → frontend batch model/service → snacks page rewrite. **STOP and VALIDATE**: batch trigger → progressive one-at-a-time snacks → instant quiz. This is the core MVP value.
4. Build US2 (quiz drag-drop + recap): models → LLM port/prompt → quality gate → QuizService → QuizController → drag-drop component → quiz/results pages. Validate mixed-type quiz + recap.
5. Build US5 (AI subtopics) — small, self-contained.
6. Build US4 (resilience frontend) — consumes Phase 0 + phase-6 spinners.
7. Build US3 (dark mode) — frontend only, no dependencies on other stories.
8. Complete Polish (Phase 8): tests, quickstart, SC-012 no-regression.

### Incremental Delivery

Each completed story adds value without breaking prior stories (existing `/api/snacks/generate` and `/api/quiz/generate` stay backward-compatible for SC-012). Deploy/demo after each story is independently validated.

---

## Summary

- **Total tasks: 83**
- **Per-story task counts:**
  - US1 (async snack delivery + quiz prefetch): **16**
  - US2 (quiz enhancements: drag-drop + recap): **25**
  - US3 (dark mode): **4**
  - US4 (resilience frontend: cause→message + spinner): **3**
  - US5 (AI subtopics): **9**
  - Unlabeled (Setup 1 + Foundational 8 + Polish 17): **26**

## Notes

- `[P]` tasks = different files, no dependency on incomplete tasks.
- Story labels map each task to a `spec.md` user story; setup/foundational/polish tasks carry no label.
- Every task includes exact file paths (create or modify) and is specific enough to execute without extra context.
- Every plan-phase row has at least one concrete task — no orphaned plan items.
- The dark-mode global stylesheet is `frontend/src/app/app.scss` (not `styles.scss` as the plan's prose states); verified against the repo.
