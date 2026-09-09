# Research: Endia MVP — Batch Enhancements

**Phase 0/1 output** for `/specs/001-batch-mvp-enhancements`. Each section records the Decision, Rationale, and Alternatives considered. Decisions ground in the real codebase (Spring MVC + sync RestClient; Angular 21 standalone + signals; MySQL 8 JPA).

---

## R1. Modeling Drag-and-Drop Questions (data model + grading)

**Decision**: Add a `type` discriminator (`MULTIPLE_CHOICE` | `DRAG_DROP`) to `QuizQuestion`, plus a nullable `variant` enum and a single JSON column carrying a structured `DragDropContent`. Grading is per-variant against a server-side canonical arrangement stored in that JSON.

`DragDropContent` is a container value object:
- `variant` — one of `SEQUENCING`, `CLASSIFICATION`, `MATCHING`
- `items` — draggable tokens (ordering / classification objects / matching left-column keys)
- `categories` — only for `CLASSIFICATION` (drop targets)
- `pairs` — only for `MATCHING` (canonical left→right mapping, source of truth)
- `correctOrder` — only for `SEQUENCING` (canonical order, source of truth)

Grading rules (server-side, in `QuizService`, against the **stored** arrangement — never the client's):
- `SEQUENCING`: correct iff submitted item-id order equals `correctOrder`.
- `CLASSIFICATION`: correct iff every item-id's submitted category equals its stored category.
- `MATCHING`: correct iff every left key's submitted right value equals `pairs[left]`.

**Rationale**:
- Distinct `type` keeps MCQ (existing `options`/`correctIndex`) and drag-drop in one table, sharing `subtopic`, `prompt`, `explanation`, `answerAttempts`, and the same quiz flow. FR-008 requires drag-drop to be graded and included in the same score/XP summary — a single table + a type switch is the smallest change.
- Storing the correct arrangement server-side (JSON column) mirrors the existing convention where `correctIndex` is the server-side source of truth and is never sent to the client pre-answer (already documented in `quiz-generate.md`/`quiz-answer.md`).
- JSON column is consistent with the existing `options` and `related` `List<String>` JSON approach via `JpaJsonConverter`; we add a general `JpaJsonObjectConverter` for the structured object.

**Alternative — new `drag_drop_question` table**: rejected. Would split question identity, need union joins in `quiz-summary`, and not meet the "smallest change" bar. No benefit for single-user MVP.

**Alternative — store drag-drop as name/value or 4 separate columns**: rejected. Rigid; `MATCHING` pairs don't fit flat columns; JSON is the established convention here.

**Random variant**: picked once per quiz by `QuizService` (`SecureRandom`) and passed into `LlmProvider.generateQuiz(theme, subtopic, count, variant)`. Keeps variant constant per quiz (FR-006) and lets the prompt be told which variant to emit.

**Quality gate**: `QuizQualityGate` gains drag-drop validation — prompt non-blank, `items` non-empty and distinct, canonical arrangement set and composed of exactly `items`, categories/pairs valid; malformed → re-roll (max 1) per existing convention, never serve invalid content (Constitution IV).

---

## R2. Async Progressive Snack Generation

**Decision**: Background worker + client polling. The frontend issues **one** `POST /api/batches` (FR-001); the backend persists a `StudySession`, then via a dedicated `@Async` `TaskExecutor`:

1. For each selected subtopic, generates its snack **in sequence**, updating `StudySessionItem.generationStatus` (PENDING→GENERATING→READY|FAILED) as each completes, so an already-ready snack is never blocked by a later one (FR-004).
2. In **parallel**, generates quiz questions per subtopic (FR-003), updating `StudySessionItem.quizStatus`.
3. Aggregates `BatchStatus` (IN_PROGRESS/PARTIAL/COMPLETE/FAILED).

Each snack/quiz failure is recorded per item with an `LlmErrorCause`; the batch continues for the others. The frontend polls `GET /api/batches/{batchId}` (~1.5s) and renders snack cards **one at a time**: it presents the current snack, and only advances to the next once the user finishes reading AND the next is READY (showing a `GENERATING` spinner meanwhile).

**Rationale**:
- The provider is **synchronous** (`RestClient`, `LlmConfig` builds a `SimpleClientHttpRequestFactory` with 30s timeouts). Streaming from a caller thread would require blocking a servlet thread per snack — the resource cost a background job avoids.
- Polling gives per-item partial-failure and retry visibility (spec edge cases) that a single blocking response cannot express — a `200` with one failed snack among successful ones is impossible if one call fails.
- The existing stack is Spring MVC (no WebFlux, no WebClient). Polling avoids adding a reactive runtime, keeps `RestClient`, and reuses the established JPA persistence.

**Spring side**: define a bounded `ThreadPoolTaskExecutor` bean in `LlmConfig` (name it `batchExecutor`) rather than the default `SimpleAsyncTaskExecutor` (one new thread per task, unbounded). Start both snack-seq and quiz-parallel work with `CompletableFuture.runAsync(..., batchExecutor)` from `BatchService`; wrap the batch transaction so a fast `return` of the batch id happens before generation (the worker commits its own saves).

**Angular side**: `BatchService.createBatch()` → `HttpClient.post`; `BatchService.pollStatus(batchId)` via `Observable.interval(1500).pipe(switchMap(...)) + takeWhile(status != terminal)`. Maintain a `currentIndex` signal in `SnacksComponent`; ready snacks beyond it form a queue. This is the RxJS `interval`+`switchMap` pattern; state is signal-based. Requirement FR-002 says "one snack displayed at a time", so the component renders `snacks()[currentIndex()]` and provides a "Next →" control that only advances when that snack is READY (or shows the spinner).

**Alternative — SSE (`SseEmitter`)**: rejected. Requires a long-lived async thread per client + reconnection logic; overkill for single-user MVP and harder to reason about with the sync provider.

**Alternative — one `POST` that blocks until all snacks are done and streams nothing**: rejected outright — violates FR-002/FR-004 (no progressive delivery).

---

## R3. Dark-Mode CSS Inversion (tokens + attribute + persistence)

**Decision**: Frontend-only. Keep `:root` for the light palette; add a `[data-theme="dark"]` block that overrides **the exact same custom properties** with an inverted warm palette; set `color-scheme: dark`. A `ThemePreferenceService` persists `localStorage["endia-ui-theme"]` ("light"|"dark") and applies it by setting `document.documentElement.dataset.theme`. A toggle button in the app header switches it; on boot an init reads storage (default light) and applies. All pages/components continue to reference `var(--canvas)`, `var(--ink)`, `var(--accent-deep)`, etc., so the whole UI adapts automatically (FR-014).

**Rationale**:
- The design system already funnels **every** color through `:root` custom properties (`styles.scss`): `--canvas`, `--surface-card`, `--surface-soft`, `--ink`, `--body`, `--muted`, `--hairline*`, `--accent*`, `--success*`, `--error*`, `--gold*`, plus shadow/glow variables. Overriding those in one `[data-theme="dark"]` block is a single, generic inversion (FR-013) with zero selector churn.
- `data-theme` on `<html>` (not a class on a wrapper) guarantees the metadata/theme-color, scrollbars, and the sticky header all follow.
- Persistence via `localStorage` silently degrades when blocked (spec Assumption): catch read/write errors, default light, don't crash.

**Derived dark palette** (inverted warm family — not pure `filter: invert()`):
- `--canvas: #211d18` (inverse of warm paper), `--surface-card: #2a2620`, `--surface-soft: #34302a`
- `--ink: #f2efe7`, `--body: #d6d0c3`, `--muted: #a49c8d`, `--hairline: #3a352e`, `--hairline-strong: #4c463a`
- `--accent: #ef7a5a` (brightened tomato for dark bg), `--accent-deep: #ff8a66`, `--accent-soft: #3c241c`, `--accent-ink: #ffb49c`
- semantic: `--success: #6fce9e`, `--success-deep: #58c08a`, `--success-soft: #1d3a2a`, `--error: #ff8a80`, `--error-soft: #3c1f1c`, `--gold: #e6b45a`, `--gold-soft: #3a2f16`
- shadows become darker warm; `--canvas-glow-*` rebased. `color-scheme: dark` flips native scrollbars/inputs.

**Alternative — `filter: invert(1) hue-rotate(180deg)`** on `html`: rejected. Inverts brand accent and images incorrectly, breaks dropped-shadow rendering, and is globally heavy.

**Alternative — hand-authored second stylesheet re-declaring every selector**: rejected. Duplicates the entire architecture; token overriding is the intended, generic inversion in the spec.

---

## R4. Retry Pattern (Spring Retry vs manual) + Backoff

**Decision**: Manual retry in an infrastructure `RetryingLlmProvider` decorator (forbidden from the domain). It implements the same `LlmProvider` port and wraps `OpenAiLlmProvider`; on a `LlmGenerationException` with a retryable cause it retries up to **3 total attempts** (FR-016) using exponential backoff with jitter, then rethrows the final typed exception.

Backoff configuration (new, in `application.yml` under `batch.retry`):
```
batch:
  retry:
    max-attempts: 3
    initial-backoff-ms: 1000
    backoff-factor: 2.0
    max-backoff-ms: 10000
    jitter-ratio: 0.2
```

**Rationale**:
- One decorator covers **all** port methods — `suggestSubtopics`, `generateSnack`, `generateQuiz`. This satisfies FR-018 (snack AND quiz) with a single cross-cutting implementation, and it's wired via a `@Bean(primary)` in `LlmConfig`, so services/controllers see no retry logic.
- All 5 causes are **transient/retryable by design** (unavailable, timeout, network, quota, malformed) per the spec (SC-003: recover transient failures; edge case: malformed is retried). So the decorator retries every `LlmGenerationException` cause; the only non-retryable outcome is configuration absence (missing API key) which the provider still surfaces as `unavailable`.
- Manual retry avoids a dependency and keeps control of the typed cause/backoff in one testable class. `RetryingLlmProviderTest` asserts exactly 3 attempts and no retry after final.

**Alternative — `@Retryable` from `spring-retry`**: rejected. Adds a dependency; annotation-driven retry is less explicit about cause filtering and unit-testing than a decorator; also `spring-retry` is not currently bom-managed.

**Alternative — retry inside `BatchService`/`QuizService`**: rejected. Would duplicate retry across three services and mix transport-recovery with orchestration.

**Retry vs quality-gate coexistence**: retry is a transport budget (3 attempts), quality-gate re-roll is a content budget (1 attempt) — they stack per spec note. To avoid pathological 4+ calls, `QuizService` re-rolls a question only for content failures (not for exhausted transport retries), and the gate-re-roll threshold remains 1.

---

## R5. Error Typing Strategy (cause vocabulary + mapping)

**Decision**: A single `LlmErrorCause` enum: `UNAVAILABLE`, `TIMEOUT`, `NETWORK`, `QUOTA`, `MALFORMED`. A new `LlmGenerationException extends DomainException` carries the cause and a `retryable` flag. `OpenAiLlmProvider` maps concrete failures to causes:

| Failure | Cause | HTTP | Message |
|---------|-------|------|---------|
| LLM returns 5xx / 503 / "model down" | `UNAVAILABLE` | 503 | "The AI service is temporarily unavailable." |
| Read/connect timeout | `TIMEOUT` | 503 | "The AI service took too long to respond." |
| `ResourceAccessException` / connect refused / DNS | `NETWORK` | 503 | "Could not reach the AI service. Check your connection." |
| 429 / rate-limit / quota | `QUOTA` | 429 | "The AI service quota or rate limit was reached. Try again shortly." |
| Missing/empty fields or unparsable JSON | `MALFORMED` | 400 | "The AI returned an unexpected response." |

`GlobalExceptionHandler` gains a handler for `LlmGenerationException` returning `ErrorResponse(error = cause.name(), message = friendly, cause = cause.name())`. The existing `ErrorResponse` record (`error`, `message`, `field`) is extended with a nullable `cause`.

**Rationale**:
- The requirement (FR-015/SC-004) demands the *user* knows the specific cause. Exposing a machine-readable `cause` enum + a human message covers both the UI (frontend switches on cause) and the API contract.
- A dedicated subclass keeps the existing `ServiceUnavailableException` path working (it becomes effectively an `UNAVAILABLE` without cause) so nothing breaks; `OpenAiLlmProvider` currently throws it on every failure — we upgrade it to the typed exception.
- Frontend `ErrorService.getMessage` maps `cause` → the exact friendly string above (FR-015), and the snack/quiz pages render it in place of the old generic banner.

**Alternative — one exception per cause**: rejected. Proliferation; a single typed exception with an enum discriminates causes cleanly and is easy to switch on.

**Alternative — reuse `ServiceUnavailableException` only**: rejected. Cannot express quota (429) or malformed (400) with correct status codes; causes must be distinguishable.

---

## R6. Deduplication Strategy (user-proposed vs AI subs)

**Decision**: Normalize to a canonical key: `trim().toLowerCase()` with internal whitespace collapsed to single spaces. In `SubtopicService.addSubtopics(themeId, names)`, dedup against all existing subtopics of that theme (`JpaSubtopicRepository.findByThemeId`) **and** within the incoming list; keep the first occurrence. A name matching an existing AI-suggested subtopic is skipped (only one entry kept, FR-021). Empty/blank names rejected (400) before dedup.

Persisted subtopic gets `source` (`AI_SUGGESTED` | `USER_PROPOSED`), defaulting `AI_SUGGESTED` for suggestion-driven creations and `USER_PROPOSED` for additions. `SubtopicService` reuses `ThemeService`'s existing per-name persistence path so parity (snack+quiz) is automatic: user subtopics live in the same `subtopic` table as suggestions, so `BatchService`'s snack/quiz workers process them identically (FR-020).

**Rationale**:
- Spec Assumption says dedup matches on normalized text (case/whitespace), deferring semantic dedup to post-MVP. A canonical normalized key is the smallest correct implementation.
- Because dedup happens at `SubtopicService` (on write), duplicates never reach `BatchService` — so no duplicate snack/quiz is generated (FR-021), and the UI reflects the single retained entry. `user-proposed` vs `ai-suggested` duplicates collapse to whichever is persisted first.

**Alternative — semantic/embedding dedup**: rejected per spec assumption (post-MVP). Overkill now.

**Alternative — dedup only in the frontend**: rejected. Backend is the source of truth; frontend-only dedup would let a duplicate get persisted and generate a duplicate snack/quiz.

---

## Cross-cutting: what is NOT changed
- No auth, no `User` entity (anonymous, stub user context).
- No reactive stack added; `RestClient` stays.
- No new external JS drag-drop library (native HTML5 DnD + keyboard fallback).
- `LlmProvider` port shape preserved (records extended inside, not replaced with provider-specific types).
- Prompts stay in `resources/prompts/*.md`.
