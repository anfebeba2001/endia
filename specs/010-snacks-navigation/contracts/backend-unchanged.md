# Backend Contract: Unchanged

**Feature**: `010-snacks-navigation`
**Statement**: The backend API, DTOs, persistence, generation, provider, and prompts are **unchanged**
by this feature (FR-022/SC-010).

This is a frontend/UI-only feature. The return-to-snacks flow relies entirely on in-memory Angular
state and the existing batch short-circuit; it issues **no** new HTTP request. No backend contract
file from prior features is superseded, so this document only records the freeze.

---

## 1. HTTP API — no change

| Endpoint | Method | Change |
|----------|--------|--------|
| `/api/themes/suggest` | POST | None |
| `/api/subtopics` | POST | None |
| `/api/batches` | POST | None (still returns `202` immediately; still short-circuited by the frontend when snacks already exist) |
| `/api/batches/{batchId}` | GET | None |
| `/api/snacks/generate` | POST | None (retained; not used by the return path) |
| `/api/quiz/generate` | POST | None |
| `/api/quiz/answer` | POST | None |
| `/api/quiz/summary/{subtopicId}` | GET | None |

No request body, response shape, status code, or error code changes. No new endpoint is added.

---

## 2. Persistence — no change

- No JPA entity, field, column, index, constraint, or enum change.
- No change to `StudySession`, `StudySessionItem`, `Snack`, `Subtopic`, `Theme`, `GenerationStatus`,
  `BatchStatus`, or `LlmErrorCause`.
- No Flyway/Liquibase migration (`ddl-auto=update` produces no delta).
- The active snack index is **not** persisted (session-scoped UI state only; spec Assumptions).

---

## 3. Generation / LLM — no change

- No change to `BatchService`, `SnackService`, `QuizService`, `QuizQualityGate`, `RetryingLlmProvider`,
  `OpenAiLlmProvider`, `LlmConfig`, or prompts.
- No provider-selection or quality-gate change.
- No change to progressive-delivery semantics (`PENDING`/`GENERATING`/`READY`/`FAILED`).

---

## 4. What the frontend does instead

- **Return path**: `ResultsComponent.backToSnacks()` sets the shared index and navigates; it makes no
  HTTP call. `SnacksComponent.init()` hydrates its view model from `AppStateService.snacks()` and
  skips `createBatch` (FR-002/FR-004).
- **Navigation**: Next/Previous/index clicks only mutate `AppStateService.currentSnackIndex`; no
  server round trip.

---

## 5. Verification of the freeze

- The existing backend test suites are untouched and must remain green if run
  (`cd backend && mvn test`).
- No new backend tests are required by this feature.
- The frontend E2E suite asserts **0** additional `POST /api/batches` on return (SC-002), which is the
  observable proof that the backend contract is exercised unchanged.
