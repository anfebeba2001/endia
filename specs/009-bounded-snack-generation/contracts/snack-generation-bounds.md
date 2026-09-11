# Behavioral Contract: Bounded Snack Generation (Backend)

**Scope**: The backend batch worker (`BatchService.runBatch`), the transactional snack generation
(`SnackService`), the retry decorator (`RetryingLlmProvider`), the outbound HTTP client
(`LlmConfig.llmRestClient`), and the bounded executor configuration (`LlmConfig.snackExecutor`). This
is a **backend-behavioral** contract — it constrains time/attempt bounds and failure classification;
it does not change any HTTP request/response shape (see `batch-status.md`).

**Feature**: `009-bounded-snack-generation` (US1–US5, US7; FR-001..FR-017, FR-019..FR-023).

---

## B1. Per-call outbound bound (FR-001 / FR-002 / FR-003)

- Every outbound LLM call made while generating a snack MUST be bounded by a configured
  **connect-plus-read** timeout (`llm.timeout-seconds`, default **30 s**).
- The timeout MUST apply to both connection establishment and response reading and MUST be
  configurable via properties/environment with a documented default.
- Implementation: `LlmConfig.llmRestClient()` builds a `SimpleClientHttpRequestFactory` with
  `setConnectTimeout` and `setReadTimeout` both set to `llm.timeout-seconds`; `OpenAiLlmProvider`
  classifies the resulting `SocketTimeoutException`/`"timed out"` as `LlmErrorCause.TIMEOUT`.
- A hanging provider call MUST fail at or before the bound rather than remaining in flight.
- **Preserved from `001`/`005`; not re-implemented by `009`.** Verified by a config-driven test.

## B2. Bounded retries (FR-004)

- Automatic retries MUST remain bounded by `batch.retry.max-attempts` (default **3**); no retry may be
  issued beyond that maximum.
- Only **retryable** `LlmGenerationException`s are retried; non-retryable causes surface terminally
  after one attempt.
- Backoff is exponential with jitter, capped by `batch.retry.max-backoff-ms`.
- Implementation: `RetryingLlmProvider` (unchanged). **Preserved from `001`; not redesigned by `009`.**

## B3. Bounded re-roll (FR-005)

- The content re-roll in `SnackService.generateValidSnack` MUST remain limited to a **single** re-roll.
- If the second response is also invalid, generation MUST fail with a typed cause
  (`ServiceUnavailableException`) and MUST NOT issue a third call.
- **Preserved; not redesigned by `009`.** Verified by a call-count test.

## B4. Per-subtopic wall-clock bound (FR-007 / FR-008 / FR-009 / FR-010 / FR-011)

- A single subtopic's snack generation MUST be bounded by `batch.snack-timeout-seconds`
  (default **240 s**), a wall-clock timeout. (A timeout is the primary mechanism because a
  never-returning provider cannot be interrupted by an attempt counter alone.)
- The bound MUST be applied **around** the whole per-subtopic operation — subtopic/theme resolution,
  all retries, and the single re-roll — not merely around one HTTP attempt.
- Implementation: `BatchService.runBatch` runs `snackService.generateSingleSnack(subtopicId, listener)`
  on the **separate** `snackExecutor` pool and applies `CompletableFuture.orTimeout(...)`; the batch
  worker awaits the bounded future and is never the thread that hangs.
- On timeout, the item MUST be marked `FAILED` with a typed cause (`LlmErrorCause.TIMEOUT`), and the
  worker MUST proceed to the remaining items (FR-009/FR-010).
- The combined worst-case per-subtopic cost MUST be finite and no greater than the bound:
  `2 × (batch.retry.max-attempts × llm.timeout-seconds + backoff) ≈ 186 s ≤ 240 s` (FR-007).
- `batch.snack-timeout-seconds <= 0` MUST be normalized to the documented default (safe, predictable).
- The bound MUST be configurable via properties/environment with a documented default
  (`BATCH_SNACK_TIMEOUT_SECONDS`, FR-011/FR-023).
- The executor MUST use a **non-caller-runs** rejection policy: with `CallerRunsPolicy`, `supplyAsync`
  would run the hanging supplier synchronously on the batch worker before returning a future, defeating
  the bound. On rejection the item is marked `FAILED`/`UNAVAILABLE` and the worker continues.

## B5. Batch terminal guarantee (FR-012 / FR-013 / FR-014)

- Every batch MUST reach a terminal aggregate status (`COMPLETE`, `PARTIAL`, or `FAILED`); it MUST NOT
  remain `IN_PROGRESS` with items pending indefinitely, even if one or all snack generations hang.
- The quiz prefetch join MUST be bounded (`batch.quiz-join-timeout-seconds`, default 240 s); on timeout
  the worker proceeds rather than blocking.
- After the bounded join, **any item still `PENDING`/`GENERATING` (snack or quiz) MUST be finalized to
  `FAILED` with `LlmErrorCause.TIMEOUT` before aggregation**, so `aggregateStatus` can never fall
  through to `IN_PROGRESS` for a completed worker run.
- When the batch is terminal, **no** item may remain `PENDING`/`GENERATING` (FR-014).
- A batch with no items MUST aggregate to a terminal status (empty-list guard).
- The worker is triggered only after the creating transaction commits (preserved from 005).

## B6. No unanswered synchronous request (FR-015 / FR-016 / FR-017)

- `POST /api/batches` MUST return `202` immediately and MUST NOT wait for background snack generation.
- `GET /api/batches/{id}` MUST complete without waiting on background generation, including while
  generation is slow or hung.
- No synchronous HTTP request in the snack/batch path may be left unanswered indefinitely. (The
  synchronous single-shot `POST /api/snacks/generate` remains bounded per-call by `llm.timeout-seconds`
  and the retry bound; its contract is not changed by `009`.)

## B7. Preserved bounded polling (FR-018)

- Frontend status polling MUST remain bounded (default **120 s**) and MUST terminate on any terminal
  status or on timeout, surfacing a terminal, user-visible error (preserved from 002/005).
- `009` changes no frontend source; it only ensures the backend actually reaches a terminal status so
  the existing client bound is exercised normally.

## B8. Bounded, safe logging (FR-019 / FR-020)

- Timeout and failure paths MUST log the bounded outcome together with the **actual** typed cause
  (subtopic id + configured bound + cause).
- Logs and user-facing error messages MUST NOT expose prompt bodies, API keys, provider
  request/response payloads, or personal data.
- The typed `LlmGenerationException` path is caught before the generic handler, so no provider internals
  or payloads are logged for a timeout.

## B9. Architecture and non-regression (FR-021 / FR-022 / FR-023)

- The feature MUST respect the layered architecture: the bound is **application** orchestration
  (`BatchService`); the executor bean is **infrastructure** (`LlmConfig`); controllers/DTOs are
  untouched. No provider-specific logic leaks into services or controllers.
- The feature MUST preserve 002 (bounded reattempts, bounded polling) and 005 (non-starving worker
  execution, terminal aggregate status, no premature empty state) and MUST NOT redesign the UI,
  prompts, provider selection, or the LLM retry algorithm.
- All new bounds MUST be configurable via properties/environment with documented defaults.

---

## Frozen surfaces

`POST /api/snacks/generate`, `POST /api/batches`, all DTO shapes, `RetryingLlmProvider`, the LLM port
and prompts, `SnackService` generation/validation logic, `QuizService`, `QuizQualityGate`, the
frontend `batch.service.ts` / `snack-retry.service.ts`, `batchExecutor`, and `quizExecutor` sizing are
unchanged.

## Known limitation (not a contract violation)

`orTimeout` completes the future but does not interrupt the running task; a pathological provider that
ignores the HTTP timeout leaves one `snack-worker-` thread and its transaction parked. The pool is
bounded and daemon, so this cannot grow without limit or block JVM shutdown, and the batch still
terminates. In production the real provider's `llm.timeout-seconds` bound prevents an actual hang.
