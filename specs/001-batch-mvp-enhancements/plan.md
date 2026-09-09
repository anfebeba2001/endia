# Implementation Plan: Endia MVP — Batch Enhancements

**Branch**: `001-batch-mvp-enhancements` | **Date**: 2026-09-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-batch-mvp-enhancements/spec.md`

## Summary

Post-MVP batch of five enhancement groups over the existing learning loop (theme → subtopics → snacks → quiz → results):

1. **Async snack delivery (FR-001..004)** — greenfield rebuild. One logical batch request (`POST /api/batches`) triggers generation of all selected subtopics' snacks AND their quiz questions together. A background worker emits status per subtopic; the frontend polls `GET /api/batches/{batchId}` and renders snacks **one at a time** while the rest generate in the background. Quiz is prefetched alongside so "Start Quiz" is instant.
2. **Quiz enhancements (FR-005..010)** — `QuizQuestion` gains a `type` discriminator (`MULTIPLE_CHOICE` / `DRAG_DROP`). Drag-drop questions carry a variant (sequencing/classification/matching, picked once per quiz) and a server-side canonical arrangement used for grading. Both types coexist. Results screen appends a labeled snack recap.
3. **Dark mode (FR-011..014)** — frontend-only. A manual toggle sets `data-theme="dark"` on `<html>`, re-deriving the existing `:root` design tokens into an inverted warm palette; preference persisted in localStorage.
4. **Resilience (FR-015..018)** — typed error causes (unavailable/timeout/network/quota/malformed) exposed via `ErrorResponse.cause`; a `RetryingLlmProvider` decorator retries transient LLM calls up to 3 total attempts with backoff; spinner shows during retry; retry covers snack AND quiz generation.
5. **AI subtopics (FR-019..021)** — user-proposed subtopics added via `POST /api/themes/{themeId}/subtopics`, persisted with `source = USER_PROPOSED`, deduped (normalized) against suggestions and each other, and processed identically to AI-suggested subtopics.

Existing `/api/snacks/generate`, `/api/quiz/generate`, `/api/quiz/answer`, `/api/quiz/summary/{subtopicId}` remain available (SC-012 no-regression), but the live frontend flow now uses the batch endpoint.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.x (frontend), Angular 21

**Primary Dependencies**:
- Backend: Spring Boot 3.4.x, Spring Data JPA, Hibernate 6.x, Spring Web (REST), Spring Validation (Jakarta), Spring `@Async` (`TaskExecutor` via Spring Boot auto-config), RestClient (Java 21 built-in). **No Reactor/WebFlux** — async delivery is done via a background `@Async` worker + client polling, not streaming.
- Frontend: Angular 21 standalone + signals, Angular Router, HttpClient, RxJS. **No drag-and-drop library** — implemented with the native HTML5 Drag-and-Drop API plus a keyboard-accessible reorder fallback.
- Storage: MySQL 8.x (unchanged, `ddl-auto=update` in dev). New tables via Hibernate DDL.

**Storage**: MySQL 8.x. New entities: `StudySession`, `StudySessionItem`. Extended entities: `Subtopic` (+`source`), `QuizQuestion` (+`type`,`variant`,`drag_drop_json`), `AnswerAttempt` (+`arrangement` JSON, `chosen_index` now nullable). New converter: `JpaJsonObjectConverter` for structured drag-drop JSON.

**Testing**: JUnit 5 + Mockito (unit), Spring Boot Test + Testcontainers MySQL (integration), Angular Playwright (`frontend/e2e/`) — the existing E2E suite must still pass unchanged.

**Target Platform**: Linux server (backend), modern browsers Chrome/Firefox/Safari latest 2 versions (frontend).

**Project Type**: Web application (monorepo: `backend/` + `frontend/`).

**Performance Goals**: Progressive delivery — first snack rendered within ~2× the single-snack LLM latency after the batch trigger; quiz ready before/at last snack (SC-002). No whole-batch blank state (SC-001).

**Constraints**: No auth (anonymous, stub user context). LLM via OpenAI-compatible (`LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`). Quality gate must also validate drag-drop content (Constitution IV). Retry budget (3) is distinct from quality-gate re-roll budget (1) and may stack. Must not break existing 17 E2E tests (SC-012).

**Scale/Scope**: Single-user MVP validation. 5 new/modified JPA entities, ~4 new REST endpoints, 1 batch orchestration service, drag-drop rendering in the existing quiz page.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Evidence |
|---|-----------|--------|----------|
| I | **Spec-Driven Development** | PASS | spec.md defines 6 user stories, 21 FRs, 12 SCs. This plan traces every FR to a concrete file (see Mapping). |
| II | **Clean Architecture (Layered)** | PASS | All new logic sits in domain (entities/exceptions) → application (services/ports) → infrastructure (JPA/LLM) → interfaces (REST). Async belongs to application service orchestration; REST stays thin. Frontend uses standalone components + signals. |
| III | **LLM Abstraction First** | PASS | Drag-drop and typed errors are introduced **on the `LlmProvider` port** (no provider leaks). `RetryingLlmProvider` is an infrastructure decorator around the port — services remain provider-agnostic. Prompts externalized in `resources/prompts/*.md`. |
| IV | **Quality Gate for Generated Content** | PASS | `QuizQualityGate` is extended to validate drag-drop structure (variant, non-empty items, complete correct arrangement, no dupes). Malformed drag-drop triggers re-roll (max 1) per existing convention. |
| V | **MVP-First, Iterate** | PASS | No gamification/auth added. Dark mode is a token inversion, not a redesign. Dedup is exact/near-identical (normalized) text only — semantic dedup deferred per spec Assumptions. |

**All gates PASS. No violations to justify.**

## Complexity Tracking

> No constitution violations. No complexity justifications needed.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | N/A | N/A |

## Project Structure

### Documentation (this feature)

```text
specs/001-batch-mvp-enhancements/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Phase 0 — tech decisions
├── data-model.md        # Phase 1 — entity definitions
├── quickstart.md        # Phase 1 — local dev + batch run/test
├── contracts/           # Phase 1 — API contracts
│   ├── batch-create.md
│   ├── batch-status.md
│   ├── snack-generate.md        (updated)
│   ├── quiz-generate.md         (updated: types)
│   ├── quiz-answer.md           (updated: drag-drop answers)
│   ├── quiz-summary.md          (updated: snack recap)
│   ├── subtopic-add.md          (new: user-proposed subtopics)
│   └── error-response.md        (new: typed cause)
└── tasks.md             # Phase 2 — NOT created by planner
```

### Source Code (repository root)

```text
endia/
├── backend/
│   └── src/main/java/com/endia/
│       ├── domain/
│       │   ├── model/
│       │   │   ├── Theme.java                          # unchanged
│       │   │   ├── Subtopic.java                       # MOD: +source (AI_SUGGESTED | USER_PROPOSED)
│       │   │   ├── Snack.java                          # unchanged
│       │   │   ├── QuizQuestion.java                   # MOD: +type, +variant, +dragDropJson
│       │   │   ├── AnswerAttempt.java                  # MOD: chosenIndex nullable, +arrangement Json
│       │   │   ├── StudySession.java                   # NEW: batch aggregate (themeId, status)
│       │   │   ├── StudySessionItem.java               # NEW: per-subtopic snack/quiz status
│       │   │   └── DragDropContent.java                # NEW: value object (variant + canonical arrangement)
│       │   └── enums/
│       │       ├── SubtopicSource.java                 # NEW
│       │       ├── QuestionType.java                   # NEW
│       │       ├── DragDropVariant.java                # NEW
│       │       ├── GenerationStatus.java               # NEW (PENDING/GENERATING/READY/FAILED)
│       │       ├── BatchStatus.java                    # NEW (IN_PROGRESS/PARTIAL/COMPLETE/FAILED)
│       │       └── LlmErrorCause.java                  # NEW (UNAVAILABLE/TIMEOUT/NETWORK/QUOTA/MALFORMED)
│       ├── domain/exception/
│       │   ├── DomainException.java                    # unchanged
│       │   ├── LlmGenerationException.java             # NEW: typed cause + retryable flag
│       │   └── ... (Existing exceptions unchanged)
│       ├── application/
│       │   ├── port/
│       │   │   ├── LlmProvider.java                    # MOD: +drag-drop types in GeneratedQuestion, +variant param
│       │   │   └── ... (repos unchanged)
│       │   ├── service/
│       │   │   ├── BatchService.java                   # NEW: batch orchestration, @Async worker, status
│       │   │   ├── SubtopicService.java                # NEW: add user subtopics + dedup
│       │   │   ├── SnackService.java                   # MOD: extract single-snack generation (reused by batch)
│       │   │   ├── QuizService.java                    # MOD: mixed-type generation, variant, drag-drop grading, recap
│       │   │   └── QuizQualityGate.java                # MOD: +drag-drop validation
│       │   └── dto/
│       │       ├── BatchCreateRequest.java             # NEW
│       │       ├── BatchCreateResponse.java            # NEW
│       │       ├── BatchStatusResponse.java            # NEW
│       │       ├── SubtopicAddRequest.java             # NEW
│       │       ├── SubtopicAddResponse.java            # NEW
│       │       ├── QuizGenerateResponse.java           # MOD: type/variant/dragDrop in QuestionItem
│       │       ├── QuizAnswerRequest.java              # MOD: +arrangement (drag-drop)
│       │       └── QuizSummaryResponse.java            # MOD: +recap (snack)
│       ├── infrastructure/
│       │   ├── persistence/
│       │   │   ├── JpaStudySessionRepository.java      # NEW
│       │   │   ├── JpaStudySessionItemRepository.java  # NEW
│       │   │   ├── JpaSnackRepository.java             # MOD: +findBySubtopicId
│       │   │   ├── JpaQuizQuestionRepository.java      # MOD: +findBySubtopicIdAndType
│       │   │   ├── JpaJsonConverter.java               # unchanged (List<String>)
│       │   │   └── JpaJsonObjectConverter.java         # NEW: JSON object <-> DragDropContent/Map
│       │   ├── llm/
│       │   │   ├── OpenAiLlmProvider.java              # MOD: typed causes (429→QUOTA, timeout→TIMEOUT, ...)
│       │   │   ├── LlmPromptTemplates.java             # MOD: +variant placeholder in quiz prompt
│       │   │   ├── LlmConfig.java                      # MOD: +retry config (max-attempts, backoff)
│       │   │   └── RetryingLlmProvider.java            # NEW: decorator, 3-attempt retry + backoff
│       │   └── exception/
│       │       └── GlobalExceptionHandler.java         # MOD: +LlmGenerationException → ErrorResponse with cause
│       ├── interfaces/rest/
│       │   ├── BatchController.java                    # NEW: POST /api/batches, GET /api/batches/{id}
│       │   ├── SubtopicController.java                 # NEW: POST /api/themes/{themeId}/subtopics
│       │   ├── QuizController.java                     # MOD: answer accepts arrangement; summary incl. recap
│       │   └── dto/
│       │       ├── BatchCreateApiRequest.java          # NEW
│       │       ├── BatchCreateApiResponse.java         # NEW
│       │       ├── BatchStatusApiResponse.java         # NEW
│       │       ├── SubtopicAddApiRequest.java          # NEW
│       │       ├── SubtopicAddApiResponse.java         # NEW
│       │       ├── QuizAnswerApiRequest.java           # MOD: +arrangement
│       │       └── QuizSummaryApiResponse.java         # MOD: +recap
│       └── resources/
│           ├── application.yml                         # MOD: +batch.retry.*, async executor sizing
│           └── prompts/
│               └── generate-quiz.md                    # MOD: mixed MCQ + drag-drop (variant-instructed)
│   └── src/test/java/com/endia/... (new/updated tests, see Testing)

└── frontend/src/app/
    ├── app.html                                        # MOD: +dark-mode toggle button
    ├── app.ts                                          # MOD: +theme toggle handler + init
    ├── app.config.ts                                   # MOD: +theme init provider
    ├── app.scss                                        # MOD: +[data-theme="dark"] token overrides
    ├── models/
    │   ├── api-response.model.ts                       # MOD: +cause
    │   ├── theme.model.ts                              # MOD: +source on Subtopic; +SubtopicAddResponse
    │   ├── snack.model.ts                              # (unchanged, reused for recap)
    │   ├── quiz.model.ts                               # MOD: +type,+variant,+dragDrop; answer +arrangement
    │   └── batch.model.ts                              # NEW: batch status model
    ├── services/
    │   ├── app-state.service.ts                        # MOD: +batchId, +currentSnack index, +quizPrefetched
    │   ├── batch.service.ts                            # NEW: createBatch + pollStatus
    │   ├── theme.service.ts                            # MOD: +addSubtopics
    │   ├── theme-preference.service.ts                 # NEW: dark-mode persistence + apply
    │   ├── quiz.service.ts                             # MOD: +answer drag-drop arrangement
    │   ├── snack.service.ts                            # (unchanged; batch flow bypasses it)
    │   └── error.service.ts                            # MOD: map cause → specific message
    ├── pages/
    │   ├── subtopics/subtopics.component.{ts,html,scss} # MOD: +"add custom subtopic" input
    │   ├── snacks/snacks.component.{ts,html,scss}      # REWRITE: progressive one-at-a-time queue + poll
    │   ├── quiz/quiz.component.{ts,html,scss}          # MOD: +render drag-drop, submit arrangement
    │   └── results/results.component.{ts,html,scss}    # MOD: +snack recap block
    └── components/
        └── drag-drop-question/drag-drop-question.component.{ts,html,scss}  # NEW
```

**Structure Decision**: Monorepo as-is. Backend keeps clean architecture (domain/application/infrastructure/interfaces), which already cleanly absorbs the new orchestration. Async delivery does not require a reactive framework — an `@Async` Spring worker plus a thin polling endpoint is the lowest-complexity fit for the existing sync `RestClient` provider. Frontend adds one reusable `drag-drop-question` standalone component; the existing pages/routing are preserved.

## Architecture Decisions

### 1. Async delivery = background worker + polling (NOT SSE/WebFlux)
`POST /api/batches` persists a `StudySession`, then dispatches `@Async` work. The worker generates each subtopic's snack in sequence, flips `StudySessionItem.generationStatus` as it goes, and runs quiz generation for every subtopic in parallel. The frontend polls `GET /api/batches/{batchId}` on a ~1.5s interval and renders the current snack. Rationale: the provider is synchronous (`RestClient`), the stack is Spring MVC, and polling gives partial-failure visibility (per-item `FAILED` + `cause`) with no streaming infra. Rejected: SSE (`SseEmitter`) — nice push, but needs a long-lived async thread per client and more failure handling for a single-user MVP; WebFlux — requires adding a reactive stack and a reactive provider client, violating the "keep RestClient" constraint.

### 2. Drag-drop modeled as a `type` discriminator on `QuizQuestion` (no separate table)
Adds a `type` enum + nullable `variant` + a JSON column storing the structured `DragDropContent` (items, categories, pairs, and the server-side canonical arrangement). Rationale: drag-drop questions share `subtopic`, `prompt`, `explanation`, `answerAttempts`, and the same UI flow as MCQ. A single-table discriminator is the simplest model satisfying FR-005..008 and keeps `AnswerAttempt` FK intact. Rejected: a separate `DragDropQuestion` table (join+union queries for no benefit), and key-value name/value columns (JSON is already used for `options`/`related`).

### 3. Random variant selected once per quiz (service-layer, passed to prompt)
`QuizService` picks one `DragDropVariant` from the 3-value set with `SecureRandom` at generation time and passes it to `LlmProvider.generateQuiz(..., variant)`. This matches FR-006 ("one variant per quiz") and keeps the choice deterministic per quiz. Prompt instructs the LLM to emit a mix of MCQ + drag-drop questions of that variant.

### 4. Typed error causes on a dedicated exception + `ErrorResponse.cause`
`LlmGenerationException extends DomainException` carries `LlmErrorCause`. `OpenAiLlmProvider` maps HTTP/server outcomes to causes (429→QUOTA, read/connect timeout→TIMEOUT, connect failure→NETWORK, 5xx/503→UNAVAILABLE, parse failure→MALFORMED). `GlobalExceptionHandler` maps it to `ErrorResponse{error,message,cause}` with status 429 for quota (else 503/400). Frontend `ErrorService.getMessage` renders cause-specific text. Rejected: replacing the whole exception hierarchy — existing `ServiceUnavailableException` call sites are preserved and become a subclass scenario; a single typed exception is additive.

### 5. Retry = infrastructure `RetryingLlmProvider` decorator (no spring-retry dep)
A decorator implementing the same `LlmProvider` port wraps `OpenAiLlmProvider`, catching `LlmGenerationException` with a retryable cause and retrying up to 3 total attempts with exponential backoff + jitter. Rationale: keeps retry cross-cutting (snack AND quiz AND subtopics all benefit, satisfying FR-018), keeps the port signature unchanged, and avoids adding a dependency. Rejected: `spring-retry` (extra dependency, annotation config, less explicit for a decorator); retry logic inside each service (duplicated).

### 6. User-proposed subtopics = separate `POST /api/themes/{themeId}/subtopics` endpoint
Free-text names are added after suggestion, persisted with `source=USER_PROPOSED`, deduped against existing theme subtopics (normalized: lowercase+trim+collapse-whitespace) and within the request. Rationale: the subtopics screen already renders suggestions; adding a dedicated add endpoint keeps theme-suggest unchanged (back-compat, SC-012) and gives the user a clear add control. Rejected: extending `theme-suggest` request (forces clients to buffer user input pre-persist; worse fit for the add-after-suggest UX).

### 7. Snack recap returned inside `quiz-summary` response
`QuizSummaryResponse` gains an optional `recap` object (subtopicName + snack fields or null). `QuizService.getQuizSummary` loads the snack via `findBySubtopicId`. Rationale: the results screen already calls summary; no extra round-trip. Degrades gracefully (null recap) per SC-011 if the snack is unavailable. Rejected: a separate recap endpoint (extra call, no benefit).

### 8. Dark mode = `data-theme` attribute + token overrides (frontend-only)
`:root` defines the light palette. A `[data-theme="dark"]` block overrides the same custom properties with an inverted warm palette; `color-scheme: dark` is set. `ThemePreferenceService` reads/writes `localStorage["endia-ui-theme"]` and toggles `document.documentElement.dataset.theme`. Rejected: `filter: invert()` (breaks specificity, brand accent, images); a separate dark stylesheet (duplicate selectors). Generic inversion of tokens satisfies FR-013/FR-014.

## Phase-by-Phase Implementation Plan

### Phase 0 — Foundation & Typed Errors (backend)
| Step | File | Why |
|------|------|-----|
| 0.1 | `domain/enums/LlmErrorCause.java` (NEW) | Canonical 5-cause vocabulary (FR-015/SC-004). |
| 0.2 | `domain/exception/LlmGenerationException.java` (NEW) | Typed exception carrying cause + retryable flag. |
| 0.3 | `infrastructure/llm/OpenAiLlmProvider.java` (MOD) | Map exceptions to causes (429, timeout, connect, 5xx, parse). |
| 0.4 | `infrastructure/exception/GlobalExceptionHandler.java` (MOD) | `ErrorResponse.cause` for `LlmGenerationException`. |
| 0.5 | `interfaces/rest/dto/QuizAnswerApiRequest.java` etc. unaffected here | — |
| 0.6 | `infrastructure/llm/LlmConfig.java`, `application.yml` (MOD) | Add `batch.retry.max-attempts`, `base-backoff-ms`. |
| 0.7 | `infrastructure/llm/RetryingLlmProvider.java` (NEW) | 3-attempt retry + backoff (FR-016/018). |
| 0.8 | `contracts/error-response.md` | Document typed cause contract. |

### Phase 1 — Domain & Persistence (data model)
| Step | File | Why |
|------|------|-----|
| 1.1 | `domain/enums/SubtopicSource.java`, `domain/model/Subtopic.java` (MOD) | `source` field (FR-019/020). |
| 1.2 | `domain/enums/QuestionType.java`, `DragDropVariant.java`, `domain/model/DragDropContent.java`, `domain/model/QuizQuestion.java` (MOD) | Type discriminator + drag-drop content (FR-005..007). |
| 1.3 | `domain/model/AnswerAttempt.java` (MOD) | Nullable `chosenIndex` + `arrangement` JSON for drag-drop grading (FR-008). |
| 1.4 | `domain/enums/GenerationStatus.java`, `BatchStatus.java`, `domain/model/StudySession.java`, `StudySessionItem.java` (NEW) | Batch/status model (FR-001..004). |
| 1.5 | `infrastructure/persistence/JpaJsonObjectConverter.java` (NEW) | JSON object serialization. |
| 1.6 | `JpaStudySessionRepository`, `JpaStudySessionItemRepository` (NEW); `JpaSnackRepository.findBySubtopicId`, `JpaQuizQuestionRepository.findBySubtopicIdAndType` (MOD) | Persistence for new/typed queries. |
| 1.7 | `data-model.md` | Document all changes. |

### Phase 2 — LLM Port & Prompts
| Step | File | Why |
|------|------|-----|
| 2.1 | `application/port/LlmProvider.java` (MOD) | `GeneratedQuestion` gains `type`, `variant`, `dragDropContent`; `generateQuiz(..., DragDropVariant variant)`. |
| 2.2 | `resources/prompts/generate-quiz.md` (MOD) | Instruct mixed MCQ + variant-specific drag-drop with a JSON schema. |
| 2.3 | `infrastructure/llm/LlmPromptTemplates.java` (MOD) | Render `{{variant}}`. |
| 2.4 | `infrastructure/llm/OpenAiLlmProvider.generateQuiz` (MOD) | Parse mixed-type JSON into typed records. |

### Phase 3 — Application Services
| Step | File | Why |
|------|------|-----|
| 3.1 | `application/service/QuizQualityGate.java` (MOD) | Validate drag-drop structure (FR-005, Constitution IV). |
| 3.2 | `application/service/QuizService.java` (MOD) | Random variant; mixed-type generation; drag-drop grading; recap in summary (FR-006..010). |
| 3.3 | `application/service/SnackService.java` (MOD) | Extract `generateSingleSnack(subtopic, status-callback)` for batch reuse (FR-002/004). |
| 3.4 | `application/service/SubtopicService.java` (NEW) | Add + dedup user subtopics (FR-019..021). |
| 3.5 | `application/service/BatchService.java` (NEW) | Batch creation, `@Async` worker (snack sequence + parallel quiz), status aggregation (FR-001..004). |

### Phase 4 — REST Interfaces
| Step | File | Why |
|------|------|-----|
| 4.1 | `BatchController` + `Batch*ApiRequest/Response` (NEW) | `POST /api/batches`, `GET /api/batches/{batchId}` (FR-001..004). |
| 4.2 | `SubtopicController` + `Subtopic*ApiRequest/Response` (NEW) | User-proposed subtopics (FR-019). |
| 4.3 | `QuizController` (MOD) + `QuizAnswerApiRequest` (+arrangement), `QuizSummaryApiResponse` (+recap) | Drag-drop answers + recap (FR-008..010). |
| 4.4 | Docs: `contracts/batch-create.md`, `batch-status.md`, `subtopic-add.md`, and updated `quiz-generate.md`, `quiz-answer.md`, `quiz-summary.md`, `snack-generate.md` | Align API surface. |

### Phase 5 — Frontend Services & Models
| Step | File | Why |
|------|------|-----|
| 5.1 | `models/api-response.model.ts` (+cause), `models/theme.model.ts` (+source), `models/quiz.model.ts` (+type/variant/dragDrop, answer arrangement), `models/batch.model.ts` (NEW) | Type the new API contracts. |
| 5.2 | `services/error.service.ts` (MOD) | cause→specific message (FR-015). |
| 5.3 | `services/batch.service.ts` (NEW) | createBatch + pollStatus (FR-001..003). |
| 5.4 | `services/theme.service.ts` (MOD) | addSubtopics (FR-019). |
| 5.5 | `services/quiz.service.ts`, `services/app-state.service.ts` (MOD) | drag-drop answer + batch/quiz-prefetch state (FR-003/008). |

### Phase 6 — Frontend Pages & Components
| Step | File | Why |
|------|------|-----|
| 6.1 | `pages/snacks/*` (REWRITE) | Progressive one-at-a-time queue, poll, `GENERATING` spinner, per-item fail+retry (FR-001..004, 016/017, edge cases). |
| 6.2 | `components/drag-drop-question/*` (NEW) | Render sequencing/classification/matching; HTML5 DnD + keyboard reorder (FR-005/007). |
| 6.3 | `pages/quiz/*` (MOD) | Branch on `type`, render drag-drop, submit arrangement (FR-008). |
| 6.4 | `pages/results/*` (MOD) | Snack recap block labeled with subtopic/snack name (FR-009/010). |
| 6.5 | `pages/subtopics/*` (MOD) | "Add custom subtopic" input + dedup feedback (FR-019..021). |

### Phase 7 — Dark Mode
| Step | File | Why |
|------|------|-----|
| 7.1 | `services/theme-preference.service.ts` (NEW) | Persist + apply `data-theme` (FR-011/012). |
| 7.2 | `app.ts`, `app.html`, `app.config.ts`, `app.scss` (MOD) | Toggle button in header, init provider, `[data-theme="dark"]` token overrides (FR-011, 013, 014). |

### Phase 8 — Tests & Docs
| Step | File | Why |
|------|------|-----|
| 8.1 | Backend unit tests: `QuizServiceTest` (variant/drag-drop grading/recap), `QuizQualityGateTest` (drag-drop rules), `SnackServiceTest`, `SubtopicServiceTest` (dedup), `RetryingLlmProviderTest` (3 attempts), `OpenAiLlmProviderTest` (cause mapping) | Cover FR-005..010, 015..018, 019..021. |
| 8.2 | Backend integration: `BatchFlowIntegrationTest`, `QuizFlowIntegrationTest` (drag-drop + recap), `ThemeFlowIntegrationTest` (+user subtopics) | Testcontainers MySQL. |
| 8.3 | Frontend Playwright: extend `e2e/` with drag-drop, progressive delivery, dark-mode persistence, error-message suites; keep existing 17 green | SC-012. |
| 8.4 | `quickstart.md` | Batch run/test + env vars + retry/backoff config. |

## Milestones

- **M0** (P0, backend): typed errors + retry decorator wired through `OpenAiLlmProvider`. Contracts for `error-response.md`.
- **M1** (P0, backend): data model + LLM port/prompt + services (quality gate, quiz mixed-type, snack single-gen, subtopic service, batch service) + REST controllers.
- **M2** (P0, frontend): rebuild snacks page (progressive), quiz drag-drop renderer, results recap, subtopic add.
- **M3** (P1, frontend): dark mode toggle + persistence.
- **M4** (P1, tests/docs): full test coverage + quickstart; verify existing E2E suite still passes.

## Risks

- **Async executor sizing**: Spring `@Async` default executor has unbounded queue / single default thread. Batch fan-out (snack + quiz per subtopic) could starve tomcat worker threads. Mitigation: define a dedicated `TaskExecutor` bean (bounded pool) in `LlmConfig`; cap and document limits. (Risk if under-specified.)
- **Retry budget vs quality-gate budget stacking**: FR-016 (3 attempts) and quality-gate re-roll (1) can compound per question → worst case 4+ LLM calls/question. Mitigation: retry only retryable causes (not malformed after final parse attempt) and cap combined attempts in `BatchService`; documented.
- **`AnswerAttempt` unique constraint change**: existing `(quiz_question_id, chosen_index)` becomes invalid when `chosen_index` is nullable for drag-drop. Mitigation: relax to a unique constraint on `quiz_question_id` only (service already enforces one-attempt via `existsByQuizQuestionId`); document as a breaking dev-DDL change. **Flagged ambiguity — see below.**
- **Ambiguity: single quiz vs per-subtopic quiz.** The spec's recap (FR-009/010) and existing flow key quizzes per-subtopic, but the batch generates quizzes for *all* selected subtopics. Assumption: the live flow still quizzes **per subtopic** (user picks a subtopic to quiz), so "the quiz you just took" = one subtopic's quiz, and the recap is that subtopic's snack. This keeps `quiz-summary/{subtopicId}` semantics intact and SC-012 safe. **Flagged for orchestrator.**
- **Ambiguity: what quiz the batch prefetches when N subtopics are selected.** Assumption: batch prefetches one quiz per selected subtopic (parallel), stored under each subtopic's `StudySessionItem.quizStatus`; the frontend "Start Quiz" picks the currently-read subtopic's already-ready quiz. If the spec intended a single combined quiz, this needs reconsideration. **Flagged for orchestrator.**
- **Ambiguity: "one batch request" vs existing per-subtopic quiz generation.** Existing E2E mocks `/api/quiz/generate`; the batch endpoint is new and the live flow uses it, but `/api/quiz/generate` must remain working for those tests. Assumption: keep both endpoints; only the frontend switches. **Flagged.**
- **Inconsistency: spec context mentions `HistoryService`/`history.model` that do not exist in the codebase.** The recap is implemented via the existing `quiz-summary` snack association, not a history service. **Flagged — no history service/entity is created.**
- **Drag-drop accessibility**: HTML5 DnD is not keyboard/touch friendly on all browsers. Mitigation: provide a keyboard reorder fallback (list with arrow/up-down controls) in `drag-drop-question`. Risk: extra scope; noted as P2.
- **Provider returns unsupported/creatively-shaped drag-drop JSON**: parse failures handled as `MALFORMED` → retry → quality gate re-roll → best-effort; never serve invalid content (Constitution IV).

## Mapping: Spec Requirements → Plan Items

| Spec ID | Requirement | Plan Item |
|---------|-------------|-----------|
| FR-001 | Single batch trigger (snacks + quiz) | `BatchService.createBatch` + `batch-create.md` |
| FR-002 | Progressive one-at-a-time snacks | `BatchService` worker + `snacks` page rewrite (6.1) |
| FR-003 | Async quiz prefetch | `BatchService` parallel quiz worker + `batch-status.md` |
| FR-004 | Partial snack failure doesn't block rendering | `StudySessionItem.status` + per-item render (6.1) |
| FR-005 | Drag-drop type alongside MCQ | `QuizQuestion.type`, `LlmProvider.GeneratedQuestion`, `quiz-generate.md` |
| FR-006 | Random variant per quiz | `QuizService` variant pick (3.2) |
| FR-007 | Drag-drop complements MCQ | `QuizService` mixed generation (3.2) |
| FR-008 | Grade against stored arrangement, same score/XP | `QuizService` grading + `AnswerAttempt.arrangement` (3.2) |
| FR-009 | Snack recap at end of results | `QuizSummaryResponse.recap` + results page (6.4) |
| FR-010 | Recap labeled with snack/subtopic name | Recap `subtopicName` field (6.4) |
| FR-011 | Manual toggle | `theme-preference.service` + `app.html` (7.2) |
| FR-012 | Persist preference | `localStorage["endia-ui-theme"]` (7.1) |
| FR-013 | Dark palette derived from tokens | `[data-theme="dark"]` overrides (7.2) |
| FR-014 | Whole app adapts | `data-theme` on `<html>` (7.2) |
| FR-015 | Specific error cause | `LlmErrorCause` + `ErrorResponse.cause` (0.1..0.4) |
| FR-016 | Auto-retry ≤3 attempts | `RetryingLlmProvider` (0.7) |
| FR-017 | Spinner during retry | `GENERATING`/retrying render in snacks + quiz (6.1) |
| FR-018 | Retry for snack AND quiz | decorator wraps all port methods (0.7) |
| FR-019 | User-addable free-text subtopics | `SubtopicService` + `subtopic-add.md` + subtopics page (6.5) |
| FR-020 | Parity in processing/persistence | `Subtopic.source` + reuse of `generateSingleSnack`/quiz path |
| FR-021 | Dedup (match = keep one) | `SubtopicService` normalizer (3.4) |
| SC-001..012 | Success criteria | covered by M0..M4 + E2E suite extension |

## Testing Strategy

### Unit Tests
- **QuizServiceTest**: variant selection randomness; mixed-type generation; drag-drop grading per variant (sequencing order, classification assignment, matching pairs); recap inclusion; XP parity between types.
- **QuizQualityGateTest**: drag-drop validation (empty items, incomplete correct arrangement, dup items, bad variant).
- **SubtopicServiceTest**: dedup normalized (case/whitespace), dedup vs AI suggestions vs within request, empty rejection.
- **RetryingLlmProviderTest**: 3-attempt success, exhaustion after 3, non-retryable cause passes through.
- **OpenAiLlmProviderTest**: cause mapping (429→QUOTA, timeout→TIMEOUT, connect→NETWORK, 503→UNAVAILABLE, parse→MALFORMED).
- **BatchServiceTest**: status transitions (PENDING→GENERATING→READY/FAILED), partial failure isolation.

### Integration Tests
- **BatchFlowIntegrationTest**: full batch → progressive status → quiz ready (Testcontainers MySQL).
- **QuizFlowIntegrationTest**: mixed MCQ+drag-drop generation → drag-drop answer → summary with recap.
- **ThemeFlowIntegrationTest**: user-proposed subtopic persisted with source + dedup.

### Frontend Tests (Playwright `frontend/e2e/`)
- Progressive delivery: first snack appears before all ready; one card at a time.
- Drag-drop answer flow + results recap with label.
- Dark mode: toggle → reload persists; all pages readable.
- Error cause messages per cause.
- Existing 17 tests remain green (SC-012).

### Manual E2E
- Run backend + frontend, complete full flow, verify all acceptance scenarios (SC-001..012).

## Manual verification for backend
- `POST /api/batches` returns batchId; `GET /api/batches/{id}` shows progressive status.
- Simulate failures by pointing LLM at an unreachable base URL / a mock that returns 429/503/malformed.
