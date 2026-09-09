# Tasks: Endia MVP — E2E Learning Loop

**Input**: Design documents from `/specs/mvp-e2e/`

**Prerequisites**: plan.md (required), spec.md (required), data-model.md, contracts/

**Organization**: Tasks are grouped by user story. Each story can be implemented and tested independently after Phase 2 (Foundational) is complete.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US6)
- Exact file paths per `plan.md`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization, monorepo structure, and basic configuration.

- [x] T001 Create monorepo structure: `backend/` + `frontend/` directories at repository root
- [x] T002 Initialize Spring Boot 3.4.x project with Maven: `backend/pom.xml` (Spring Web, Spring Data JPA, Spring Validation, MySQL connector, Jackson, Lombok), `EndiaApplication.java`, `backend/src/main/resources/application.yml` (MySQL datasource, JPA config with `ddl-auto=update`, server port 8080)
- [x] T003 [P] Initialize Angular 19+ project with Angular CLI in `frontend/` (standalone components, signals, HttpClient module, SCSS, routing enabled)
- [x] T004 [P] Configure MySQL schema: create `application-dev.yml` with MySQL 8.x datasource config; add `.env.example` with `LLM_API_KEY`, `LLM_BASE_URL`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- [x] T005 [P] Add `.gitignore` for Java (Maven `target/`, IDE files, `.class`), Node (`node_modules/`, `dist/`, `.angular/`), and env files

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core domain model, JPA persistence, LLM abstraction, DTOs, and global config. ALL user stories depend on this phase.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T006 Create JPA entities in `backend/src/main/java/com/endia/domain/model/`: `Theme.java`, `Subtopic.java`, `Snack.java`, `QuizQuestion.java`, `AnswerAttempt.java` — per data-model.md (UUID PKs, cascades, JSON columns, indexes, unique constraints)
- [x] T007 [P] Create JPA repositories in `backend/src/main/java/com/endia/infrastructure/persistence/`: `JpaThemeRepository.java`, `JpaSubtopicRepository.java`, `JpaSnackRepository.java`, `JpaQuizQuestionRepository.java`, `JpaAnswerAttemptRepository.java` — all extending `JpaRepository<Entity, UUID>`
- [x] T008 [P] Create `LlmProvider` interface in `backend/src/main/java/com/endia/application/port/LlmProvider.java` with methods: `suggestSubtopics(String theme)`, `generateSnack(String theme, String subtopicName)`, `generateQuiz(String theme, String subtopicName, int count)`
- [x] T009 [P] Create application-layer DTOs in `backend/src/main/java/com/endia/application/dto/`: `ThemeSuggestRequest.java`, `ThemeSuggestResponse.java`, `SnackGenerateRequest.java`, `SnackGenerateResponse.java`, `QuizGenerateRequest.java`, `QuizGenerateResponse.java`, `QuizAnswerRequest.java`, `QuizAnswerResponse.java`, `QuizSummaryResponse.java`
- [x] T010 Create `JpaJsonConverter.java` in `backend/src/main/java/com/endia/infrastructure/persistence/` — `AttributeConverter<List<String>, String>` using Jackson `ObjectMapper` for MySQL JSON columns (`related`, `options`)
- [x] T011 [P] Create `GlobalExceptionHandler.java` in `backend/src/main/java/com/endia/infrastructure/exception/` — handle `MethodArgumentNotValidException` → 400, `EntityNotFoundException` → 404, custom `QuizAlreadyAnsweredException` → 409, LLM failures → 503; create domain exceptions in `backend/src/main/java/com/endia/domain/exception/`: `DomainException.java`, `QuizAlreadyAnsweredException.java`, `InvalidOptionIndexException.java`
- [x] T012 [P] Configure CORS for development (allow `localhost:4200`) and global validation in `application.yml`; create `LlmConfig.java` in `backend/src/main/java/com/endia/infrastructure/llm/` for env var configuration (`LLM_API_KEY`, `LLM_BASE_URL`)

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 — Theme-to-Subtopics Discovery (Priority: P1) 🎯 MVP

**Goal**: User enters a free-text theme, system returns 4–6 curated subtopic suggestions via LLM.

**Independent Test**: `POST /api/themes/suggest` with `"Photosynthesis"` returns 4–6 non-empty subtopic strings within 10 seconds.

- [x] T013 [P] [US1] Create prompt template in `backend/src/main/resources/prompts/suggest-subtopics.md` — instructs LLM to return JSON array of 4–6 subtopic names for a given theme
- [x] T014 [P] [US1] Create API DTOs in `backend/src/main/java/com/endia/interfaces/rest/dto/`: `ThemeSuggestApiRequest.java` (prompt field with `@Size(min=2, max=500)`), `ThemeSuggestApiResponse.java` (themeId + list of subtopic objects)
- [x] T015 [US1] Implement `ThemeService.java` in `backend/src/main/java/com/endia/application/service/` — orchestrate: validate input → call `LlmProvider.suggestSubtopics()` → persist `Theme` + `Subtopic` entities → return response DTO
- [x] T016 [US1] Implement `ThemeController.java` in `backend/src/main/java/com/endia/interfaces/rest/` — `POST /api/themes/suggest`, map request → service → response, validate input with `@Valid`
- [x] T017 [US1] Implement `OpenAiLlmProvider.java` in `backend/src/main/java/com/endia/infrastructure/llm/` — use Java 21 `RestClient` to call OpenAI-compatible API, parse JSON responses, read prompt template from classpath, handle timeouts (30s) and errors → throw `DomainException(SERVICE_UNAVAILABLE)`

**Checkpoint**: Theme suggestion works end-to-end. User can type a theme and get subtopic suggestions.

---

## Phase 4: User Story 2 — Snack Card Generation (Priority: P1)

**Goal**: User selects subtopics, system generates knowledge snack cards (concept, metaphor, whyItMatters, related) via LLM.

**Independent Test**: `POST /api/snacks/generate` with themeId + subtopicIds returns one snack per subtopic, all four fields non-empty.

- [x] T018 [P] [US2] Create prompt template in `backend/src/main/resources/prompts/generate-snack.md` — instructs LLM to return JSON with concept, metaphor, whyItMatters, related for a given subtopic within a theme
- [x] T019 [P] [US2] Create API DTOs in `backend/src/main/java/com/endia/interfaces/rest/dto/`: `SnackGenerateApiRequest.java` (themeId + subtopicIds), `SnackGenerateApiResponse.java` (list of snack objects with all fields)
- [x] T020 [US2] Implement `SnackService.java` in `backend/src/main/java/com/endia/application/service/` — orchestrate: validate theme/subtopics exist → for each subtopic call `LlmProvider.generateSnack()` → validate all fields non-empty (re-roll once if empty) → persist `Snack` entities → return response DTO
- [x] T021 [US2] Implement `SnackController.java` in `backend/src/main/java/com/endia/interfaces/rest/` — `POST /api/snacks/generate`, map request → service → response, handle 404 if theme/subtopic not found

**Checkpoint**: Snack generation works. User can generate and read knowledge cards for selected subtopics.

---

## Phase 5: User Story 3 — Quiz Generation with Distractor Quality (Priority: P1)

**Goal**: System generates 4–5 multiple-choice questions per subtopic, each with 4 options (1 correct + 3 plausible distractors), validated by quality gate.

**Independent Test**: `POST /api/quiz/generate` returns questions with exactly 4 unique non-empty options each; no `correctIndex` or `explanation` in response.

- [x] T022 [P] [US3] Create prompt template in `backend/src/main/resources/prompts/generate-quiz.md` — instructs LLM to return JSON array of questions with prompt, options (4 strings), correctIndex, explanation for a given subtopic
- [x] T023 [P] [US3] Create API DTOs in `backend/src/main/java/com/endia/interfaces/rest/dto/`: `QuizGenerateApiRequest.java` (themeId + subtopicId + count), `QuizGenerateApiResponse.java` (subtopicId + list of questions with id, prompt, options only — NO correctIndex/explanation)
- [x] T024 [US3] Implement `QuizQualityGate.java` in `backend/src/main/java/com/endia/application/service/` — validate: exactly 4 options, no duplicate options (case-insensitive trimmed), no option < 5 chars, basic length similarity check; return pass/fail
- [x] T025 [US3] Implement `QuizService.java` in `backend/src/main/java/com/endia/application/service/` — orchestrate generate: validate subtopic exists → call `LlmProvider.generateQuiz()` → for each question: run `QuizQualityGate`, if fail re-roll once (max 1 retry) → shuffle correct answer to random position → update `correctIndex` to match shuffled position → persist `QuizQuestion` entities → return response DTO (stripping `correctIndex` and `explanation`)

**Checkpoint**: Quiz generation works with quality gate. Questions are plausible and validated.

---

## Phase 6: User Story 4 — Quiz Answering and Results (Priority: P1) 🎯 MVP

**Goal**: User answers quiz questions, sees correct/incorrect feedback, explanation, XP earned, and final summary.

**Independent Test**: `POST /api/quiz/answer` returns correct flag, correctIndex, explanation, xpEarned; `GET /api/quiz/summary/{subtopicId}` returns aggregated results.

- [x] T026 [US4] Create API DTOs in `backend/src/main/java/com/endia/interfaces/rest/dto/`: `QuizAnswerApiRequest.java` (questionId + chosenIndex), `QuizAnswerApiRequest.java`, `QuizAnswerApiResponse.java` (questionId, correct, correctIndex, explanation, xpEarned), `QuizSummaryApiResponse.java` (subtopicId, totalQuestions, correctAnswers, totalXp, per-question breakdown)
- [x] T027 [US4] Add answer logic to `QuizService.java` — `answerQuestion()`: validate question exists → check not already answered (throw `QuizAlreadyAnsweredException` → 409) → validate chosenIndex 0–3 (throw `InvalidOptionIndexException` → 400) → grade against `correctIndex` → calculate XP (+10 correct, +0 wrong) → persist `AnswerAttempt` → return response DTO
- [x] T028 [US4] Add `calculateQuizXp()` method to `QuizService.java` — sum all answer XPs for a subtopic, add +50 bonus if all correct (perfect quiz)
- [x] T029 [US4] Add `getQuizSummary()` method to `QuizService.java` — aggregate all `AnswerAttempt`s for questions in a subtopic → return totalQuestions, correctAnswers, totalXp, per-question breakdown
- [x] T030 [US4] Add endpoints to `QuizController.java`: `POST /api/quiz/answer` and `GET /api/quiz/summary/{subtopicId}`

**Checkpoint**: Full quiz answering flow works. User answers questions, gets feedback, sees summary with XP.

---

## Phase 7: User Story 5 — Distractor Quality Gate Validation (Priority: P2)

**Goal**: Quality gate is robust, well-tested, and handles edge cases (all-same options, very short options, partial failures).

**Independent Test**: Feed quality gate intentionally bad questions → verify rejection; valid questions pass; max 1 retry works.

- [x] T031 [US5] Add unit tests for `QuizQualityGate` in `backend/src/test/java/com/endia/application/service/QuizQualityGateTest.java` — test: duplicate options → reject, empty options → reject, < 5 char options → reject, < 4 options → reject, valid options → pass, case-insensitive duplicate detection
- [x] T032 [US5] Add re-roll integration test in `QuizServiceTest.java` — mock `LlmProvider` to return bad questions first, then valid → verify re-roll triggers once and serves best result; verify warning logged on second failure
- [x] T033 [US5] Add edge case test: LLM returns all 4 identical options → quality gate rejects → re-roll produces valid → question served correctly

**Checkpoint**: Quality gate handles all documented edge cases correctly.

---

## Phase 8: User Story 6 — XP Score Stub (Priority: P3)

**Goal**: XP calculation is correct, returned in responses, not persisted to any profile.

**Independent Test**: 4/4 correct = 90 XP (40 + 50 bonus); 3/4 correct = 30 XP; 0/4 correct = 0 XP; XP NOT in any database table.

- [x] T034 [US6] Add unit tests for XP calculation in `QuizServiceTest.java` — test: 1/4 correct = 10 XP, 4/4 correct = 90 XP, 0/4 correct = 0 XP, XP NOT persisted (verify no XP column in any entity)
- [x] T035 [US6] Verify `AnswerAttempt` entity stores per-answer XP (0 or 10) but NO user profile or aggregate XP table exists in data model

**Checkpoint**: XP math is correct and matches spec requirements.

---

## Phase 9: Frontend (Angular)

**Goal**: Angular 19+ frontend implements the full E2E user flow with standalone components and signals.

- [x] T036 [P] Create Angular models in `frontend/src/app/models/`: `theme.model.ts` (ThemeSuggestResponse, Subtopic), `snack.model.ts` (SnackGenerateResponse, Snack), `quiz.model.ts` (QuizGenerateResponse, QuizQuestion, QuizAnswerResponse, QuizSummaryResponse), `api-response.model.ts` (ErrorResponse)
- [x] T037 [P] Create Angular services in `frontend/src/app/services/`: `theme.service.ts` (POST /api/themes/suggest), `snack.service.ts` (POST /api/snacks/generate), `quiz.service.ts` (POST /api/quiz/generate, POST /api/quiz/answer, GET /api/quiz/summary/{subtopicId}) — use `HttpClient`, return `Observable`/`Signal`, handle errors
- [x] T038 [P] Create `proxy.conf.json` in `frontend/` — proxy `/api/*` to `http://localhost:8080` for development
- [x] T039 [US1] Create `HomeComponent` in `frontend/src/app/pages/home/home.component.ts` — theme input form with `signal()` state, submit button, loading indicator, calls `ThemeService`, navigates to subtopics page on success
- [x] T040 [US1] Create `SubtopicsComponent` in `frontend/src/app/pages/subtopics/subtopics.component.ts` — display subtopic list from theme response, allow selection/deselection with signals, "Generate Snacks" button, navigates to snacks page
- [x] T041 [US2] Create `SnacksComponent` in `frontend/src/app/pages/snacks/snacks.component.ts` — display snack cards (concept, metaphor, whyItMatters, related links) with loading states, "Start Quiz" button per subtopic
- [x] T042 [US3/US4] Create `QuizComponent` in `frontend/src/app/pages/quiz/quiz.component.ts` — display one question at a time with 4 option buttons, submit answer via API, show immediate feedback (correct/incorrect, explanation, XP), advance to next question, loading states
- [x] T043 [US4] Create `ResultsComponent` in `frontend/src/app/pages/results/results.component.ts` — display quiz summary (total score, total XP, per-question breakdown with correct/incorrect indicators and explanations)
- [x] T044 Wire up `AppComponent` and routing in `frontend/src/app/app.component.ts` + `frontend/src/app/app.routes.ts` — configure routes: `/` → Home, `/subtopics` → Subtopics, `/snacks` → Snacks, `/quiz` → Quiz, `/results` → Results; share state between components via signals/services
- [x] T045 [P] Add global styles in `frontend/src/styles.scss` — base typography, card styles, button styles, loading spinner, error message styling; ensure responsive layout

**Checkpoint**: Full E2E flow works in the browser.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Error handling, validation, loading states, documentation, and final cleanup.

- [x] T046 [P] Add error handling + loading states to all frontend components — show loading spinners during API calls, display user-friendly error messages (from API error responses), disable buttons during loading
- [x] T047 [P] Add form validation to `HomeComponent` — client-side validation for theme input (min 2 chars, max 500 chars), show validation errors before submitting
- [x] T048 Add backend unit tests: `ThemeServiceTest.java`, `SnackServiceTest.java`, `QuizServiceTest.java` in `backend/src/test/java/com/endia/application/service/` — mock `LlmProvider`, verify orchestration logic, test error paths
- [x] T049 [P] Add integration tests: `ThemeFlowIntegrationTest.java`, `QuizFlowIntegrationTest.java` in `backend/src/test/java/com/endia/application/integration/` — use Testcontainers MySQL, test full flows without mocking DB
- [x] T050 [P] Add `OpenAiLlmProviderTest.java` in `backend/src/test/java/com/endia/infrastructure/llm/` — test HTTP client against MockWebServer, verify request format, response parsing, error handling
- [ ] T051 Run quickstart.md validation — follow local dev setup steps, verify full E2E flow works: start MySQL → start backend → start Angular → enter theme → get subtopics → read snacks → take quiz → see results
- [x] T052 Final review + cleanup — verify all plan.md file paths exist, remove unused imports, ensure consistent code style, verify no orphaned code, check all user story acceptance scenarios from spec.md are satisfied

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 completion — **BLOCKS all user stories**
- **User Stories (Phase 3–8)**: All depend on Phase 2 completion
  - US1 (Phase 3): First to implement (entry point of learning loop)
  - US2 (Phase 4): Depends on US1 (needs theme + subtopic entities)
  - US3 (Phase 5): Depends on US2 (quiz uses snack content context)
  - US4 (Phase 6): Depends on US3 (answers questions generated in US3)
  - US5 (Phase 7): Depends on US3 (tests quality gate from US3)
  - US6 (Phase 8): Depends on US4 (tests XP calculation from US4)
- **Frontend (Phase 9)**: Depends on backend Phases 3–6 being functional
- **Polish (Phase 10)**: Depends on all prior phases

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2 — no dependencies on other stories
- **US2 (P1)**: Depends on US1 (requires Theme + Subtopic entities persisted)
- **US3 (P1)**: Depends on US2 (quiz content references snack context)
- **US4 (P1)**: Depends on US3 (answers questions generated in US3)
- **US5 (P2)**: Depends on US3 (tests quality gate from quiz generation)
- **US6 (P3)**: Depends on US4 (tests XP calculation from answering)

### Within Each User Story

- Prompt templates and API DTOs (marked [P]) can be created in parallel
- Service implementation before controller implementation
- Controller depends on service being complete
- LLM provider implementation can run in parallel with US1 service/controller (only depends on interface from Phase 2)

### Parallel Opportunities

- T003 + T004 + T005 in Phase 1 (all [P])
- T007 + T008 + T009 + T011 + T012 in Phase 2 (all [P])
- T013 + T014 in Phase 3 (both [P])
- T018 + T019 in Phase 4 (both [P])
- T022 + T023 in Phase 5 (both [P])
- T036 + T037 + T038 + T045 in Phase 9 (all [P])
- T046 + T047 in Phase 10 (both [P])
- T049 + T050 in Phase 10 (both [P])

---

## Task Summary

| Phase | Tasks | Parallel |
|-------|-------|----------|
| Phase 1: Setup | 5 | 3 |
| Phase 2: Foundational | 7 | 5 |
| Phase 3: US1 — Theme-to-Subtopics | 5 | 2 |
| Phase 4: US2 — Snack Generation | 4 | 2 |
| Phase 5: US3 — Quiz Generation | 4 | 2 |
| Phase 6: US4 — Quiz Answering | 5 | 0 |
| Phase 7: US5 — Quality Gate Tests | 3 | 0 |
| Phase 8: US6 — XP Stub Tests | 2 | 0 |
| Phase 9: Frontend | 10 | 4 |
| Phase 10: Polish | 7 | 4 |
| **Total** | **52** | **22** |

## Notes

- [P] tasks = different files, no dependencies — can be parallelized
- [Story] label maps task to user story for traceability to spec.md acceptance scenarios
- Each user story is independently completable and testable after Phase 2
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same-file conflicts, cross-story dependencies that break independence
- `correctIndex` is NEVER returned in quiz generation response (anti-cheat per contract)
- XP is calculated server-side only, never persisted to a user profile (MVP scope)
