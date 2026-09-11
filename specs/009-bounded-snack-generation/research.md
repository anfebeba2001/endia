# Research: Guarantee Bounded Snack Generation

**Phase 0 output** for `/specs/009-bounded-snack-generation`. Each section records the Decision,
Rationale, and Alternatives considered, grounded in the real code
(`backend/.../application/service/BatchService.java`, `backend/.../application/service/SnackService.java`,
`backend/.../infrastructure/llm/LlmConfig.java`, `backend/.../infrastructure/llm/RetryingLlmProvider.java`,
`backend/.../infrastructure/llm/OpenAiLlmProvider.java`).

The spec is a hardening/liveness feature over the `001`/`002`/`005` batch flow. The existing per-call
HTTP timeout, retry bound, and single re-roll are **confirmed inputs**. The governing constraint is the
smallest correct change set: no schema change, no endpoint/DTO/status change, no LLM-algorithm change,
no UI change, and preservation of the 002/005 behavior.

---

## R1. How to enforce a per-subtopic wall-clock bound

**Decision**: Wrap each per-item snack generation in
`CompletableFuture.supplyAsync(() -> snackService.generateSingleSnack(id, listener), snackExecutor)`
followed by `.orTimeout(snackTimeoutSeconds, SECONDS).join()`, on a **separate** bounded
`snackExecutor` pool. Translate a `TimeoutException` (and executor rejection) into a typed
`LlmGenerationException` (`TIMEOUT` / `UNAVAILABLE`) so the existing `runBatch` catch marks the item
`FAILED` and the loop continues.

**Rationale**:
- The snack loop in `BatchService.runBatch` is sequential and currently calls the provider directly on
  the `batch-worker-` thread. A provider that accepts but never responds blocks that thread and the
  entire batch. Moving the call to a distinct pool makes the *worker* wait only on a future, and
  `orTimeout` bounds that wait. This is the only mechanism that stops a **never-returning** provider:
  an attempt counter cannot interrupt a hang (spec Clarifications).
- A **separate** pool is mandatory. Using `batchExecutor` for the nested call and then awaiting it is
  exactly the 005 cyclic-wait defect; using `quizExecutor` would mix snack and quiz work and add
  contention. `snack-worker-` keeps the pool observable and independent.
- Reusing `LlmErrorCause.TIMEOUT` keeps the failure on the existing typed path: `BatchService` already
  catches `LlmGenerationException`, writes `snackCause = e.getCauseType()`, and the DTO/contract already
  exposes `TIMEOUT`. No new status, enum value, field, or schema change.
- `orTimeout` is idiomatic Java 21 and already used in the codebase for the quiz join (005), so the
  approach is consistent and requires no new dependency.

**Alternatives**:
- **Interrupt-based cancellation** (`ExecutorService.submit` + `Future.get(timeout)` + `cancel(true)`):
  rejected. A thread blocked in a socket read or a non-interruptible provider does not reliably unblock,
  so it does not strengthen the guarantee over `orTimeout`, and it adds code. The required guarantee is
  "the worker proceeds", which `orTimeout` provides.
- **Virtual-thread-per-task executor** (`Executors.newVirtualThreadPerTaskExecutor()`): considered for
  Java 21. It handles I/O-bound blocking cheaply and avoids platform-thread leaks. Rejected for this
  iteration: it introduces a novel runtime model inconsistent with the existing bounded
  `ThreadPoolTaskExecutor` pools, and the MVP scale does not need it. Candidate future hardening.
- **Per-call timeout inside `SnackService`/`RetryingLlmProvider`**: rejected. The per-call bound already
  exists at the HTTP layer (`llm.timeout-seconds`); adding another there duplicates it and does not
  bound the whole per-subtopic operation (attempts + re-roll).
- **A batch-level watchdog thread**: rejected. More machinery than `orTimeout` for the same effect.

**Known limitation**: `orTimeout` completes the future but does not interrupt the running task. A
pathological never-returning provider leaves one `snack-worker-` thread and its transaction parked.
This is a bounded resource cost (the pool is bounded and daemon), not a liveness gap. In production the
real provider's 30 s HTTP timeout ensures tasks do not actually hang.

---

## R2. Why the per-subtopic default is 240 seconds

**Decision**: `batch.snack-timeout-seconds` defaults to **240**.

**Rationale**:
- It must cover the finite worst case: `2 × (batch.retry.max-attempts × llm.timeout-seconds + backoff)`
  = `2 × (3 × 30 s + ~3 s)` ≈ **186 s**. 240 s is comfortably above 186 s and matches the existing
  `batch.quiz-join-timeout-seconds` default, so the two bounds are consistent and easy to reason about.
- It is deliberately larger than the per-call budget so it never fires during healthy-but-slow
  generation, while still bounding a provider that ignores HTTP timeouts.
- It is configurable (`BATCH_SNACK_TIMEOUT_SECONDS`), and invalid values (`<= 0`) normalize to the
  default so the edge case cannot hang or fail unpredictably.

**Alternatives**:
- **A smaller default (e.g. 60 s)**: rejected. It is below the worst case (186 s), so legitimate
  retry+re-roll sequences would be falsely timed out.
- **A hard requirement of exactly 240 s**: rejected. The spec calls the value an informed default and
  an implementation/config decision (spec Assumptions/Clarifications).

---

## R3. How the never-returning provider test double is built

**Decision**: In `BoundedSnackGenerationIntegrationTest`, use a `@MockitoBean LlmProvider` whose
`generateSnack`/`generateQuiz` `thenAnswer` blocks on a `CountDownLatch` that is never counted down
during the test, with `batch.snack-timeout-seconds=2` and `batch.quiz-join-timeout-seconds=2` supplied
via `@DynamicPropertySource`. Release the latch in `@AfterEach` / rely on Spring's `shutdownNow`
(interrupt) at context close.

**Rationale**:
- A latch `await()` is **interruptible**, so when the `snackExecutor`/`quizExecutor` pools are shut down
  the abandoned tasks unblock and the test JVM exits promptly. A non-interruptible busy spin would hang
  JVM shutdown.
- The test must **not** sleep for the production default (240 s). Driving the bound from properties
  keeps the test in the low seconds and also proves SC-010 (a changed bound changes behavior).
- Because the stub is injected as the primary `LlmProvider`, it bypasses the HTTP layer entirely. That
  is intentional: it exercises the per-subtopic bound rather than the per-call HTTP timeout, which is
  the exact gap this feature closes.
- The stub is kept in its own test class so the `@MockitoBean` context reload does not disturb the
  existing `BatchGenerationIntegrationTest` context (005 lesson).

**Alternatives**:
- **A real hanging HTTP server (MockWebServer with a body delay)**: rejected for the per-subtopic test.
  It would primarily exercise the per-call HTTP timeout (already covered in `OpenAiLlmProviderTest`),
  not a provider that ignores timeouts.
- **`Thread.sleep(Long.MAX_VALUE)`**: rejected — only interruptible if the interrupt lands, and less
  explicit than a latch.

---

## R4. Interaction of the per-subtopic bound with `RetryingLlmProvider`'s internal retries

**Decision**: Keep `RetryingLlmProvider` unchanged and treat the per-subtopic bound as an **outer
backstop**, not a replacement for the per-call/retry bounds. Do not add `batch.snack-max-attempts`.

**Rationale**:
- `RetryingLlmProvider` retries only retryable `LlmGenerationException`s, up to `maxAttempts` (3), with
  backoff+jitter; `SnackService.generateValidSnack` calls the provider once and re-rolls at most once on
  invalid content. The combined call count per subtopic is therefore finite:
  `2 × maxAttempts = 6` worst case (invalid + retryable on both passes), or `2` when content is merely
  invalid, or `3` when only retries fail.
- The per-subtopic timeout does not double-count these: it only fires if the whole operation exceeds
  240 s, which the finite worst case (186 s) does not. So healthy retry/re-roll sequences complete
  normally; the timeout catches a provider that ignores the HTTP timeout.
- A `batch.snack-max-attempts` cap would duplicate `batch.retry.max-attempts` and the single re-roll,
  adding config surface with no new guarantee. The spec's "wall-clock timeout **and/or** attempts" is
  satisfied by the timeout alone because the attempt count is already finite by construction.

**Alternatives**:
- **Cap total per-subtopic attempts separately**: rejected as redundant (above).
- **Make the timeout interrupt `RetryingLlmProvider`'s backoff sleep**: unnecessary; the backoff is
  short and bounded, and the timeout is a backstop.

---

## R5. How to keep the tests fast

**Decision**: Drive every bound from configuration in tests: `batch.snack-timeout-seconds=2`,
`batch.quiz-join-timeout-seconds=2`, a small `llm.timeout-seconds` where the HTTP layer is exercised,
and an always-failing (not sleeping) stub where only call-count matters.

**Rationale**:
- The never-returning integration test must fail at the configured bound, not the 240 s default, so a
  short property value is both a correctness and a speed requirement (SC-010).
- The unit per-subtopic-timeout test uses `snackTimeoutSeconds=1` and a real single-thread executor, so
  it completes in ~1 s.
- The finite-call-count tests use fast stubs that throw/return immediately, so they assert bounded call
  counts without any wall-clock cost.
- Existing integration tests already use `@DynamicPropertySource` to shorten the quiz join; the new
  test follows the same pattern.

**Alternatives**:
- **Relying on defaults and `@Timeout`**: rejected — the default is 240 s; tests would be unusably slow.

---

## R6. Why the quiz join needs a finalization step

**Decision**: After the bounded quiz join, mark any item still `PENDING`/`GENERATING` (snack or quiz)
as `FAILED` with `LlmErrorCause.TIMEOUT` before calling `aggregateStatus`; guard `aggregateStatus` for
an empty list.

**Rationale**:
- `generateQuizForItem` sets `quizStatus = GENERATING` and saves *before* the provider call. If the call
  hangs, the join times out, the task never reaches its `catch`, and the DB row stays `GENERATING`.
- `aggregateStatus` returns `IN_PROGRESS` if any item is `PENDING`/`GENERATING`. So without
  finalization, the batch is **not terminal** even though the worker proceeded — violating FR-012 and
  FR-014. This is the subtle gap the spec's "always aggregate and set a terminal status" targets.
- Finalizing to `FAILED`/`TIMEOUT` is truthful (the item exceeded its bound) and reuses the existing
  vocabulary. Snacks always settle before the quiz phase, so the snack branch is defensive.
- An empty item list currently falls through to `IN_PROGRESS`; returning `FAILED` makes the no-subtopic
  edge case terminate.

**Alternatives**:
- **Trust the bounded join alone**: rejected — leaves `GENERATING` items and an `IN_PROGRESS` batch.
- **Per-quiz-item timeout**: deferred. Out of scope (the spec bounds snacks); the join bound plus
  finalization already delivers the terminal guarantee. Noted as future hardening.
- **Write the terminal status in the `exceptionally` handler**: rejected — duplicates aggregation and
  races the item reload.

---

## R7. Preserving and verifying the existing bounds

**Decision**: Change nothing in `llmRestClient()`, `RetryingLlmProvider`, or
`SnackService.generateValidSnack`; add tests that verify them.

**Rationale**:
- FR-004/FR-005/FR-022 require preservation; SC-009 requires the 002/005 behavior to pass unchanged.
- The existing `OpenAiLlmProviderTest` already maps a read timeout to `TIMEOUT`; a new case built from a
  `LlmConfig` with a small `timeout-seconds` proves the *configured* property drives the bound
  (FR-001/FR-002).
- `RetryingLlmProviderTest` already asserts exactly `maxAttempts` calls and non-retryable pass-through;
  a `SnackServiceTest` case adds the exactly-one-re-roll assertion.

**Alternatives**:
- **Re-implement any of these bounds**: rejected — risk of regression with no new guarantee.

---

## R8. Persistence / schema impact

**Decision**: **No persistence or schema change.**

**Rationale**:
- The timeout is surfaced through the existing `LlmErrorCause.TIMEOUT`, already persisted on
  `StudySessionItem.snackCause`/`quizCause`. No new enum value, column, or entity.
- `batch.snack-timeout-seconds` is configuration, not storage.
- The project uses `ddl-auto=update` (no Flyway/Liquibase); there is no migration to write.

**Alternatives**:
- **Persist a per-item timeout marker/attempt count**: rejected — out of scope; the existing status +
  cause fully express the outcome.

---

## R9. Logging and sensitive-data safety

**Decision**: Log the bounded outcome with the real typed cause at the moment the bound is hit
(subtopic id + bound value + cause), and add no payload logging. Reuse the existing `LlmErrorCause`
handling.

**Rationale**:
- FR-019/FR-020 require diagnosable bounded outcomes with no sensitive data. A single WARN line naming
  the subtopic id, the configured bound, and `TIMEOUT` distinguishes "timed out" from "provider errored"
  without exposing prompts, keys, or provider payloads.
- The `LlmGenerationException` path is caught before the generic catch, so no stack trace with provider
  internals is logged for timeouts.
- The `exceptionally` path for the quiz join already logs a generic message; finalization adds a typed
  cause to the item rather than logging payloads.

**Alternatives**:
- **Log the provider request/response for diagnosis**: rejected — explicitly forbidden by FR-020.

---

## Cross-cutting: what is NOT changed

- No DB schema / entity / enum change; no migration.
- No change to `POST /api/snacks/generate`, `POST /api/batches`, or any DTO/response shape.
- No change to `RetryingLlmProvider`, the LLM port, or prompts.
- No new status vocabulary or enum value; `LlmErrorCause.TIMEOUT` already exists.
- No change to `SnackService` generation/validation logic (the bound wraps its call).
- No change to the frontend `batch.service.ts` or `snack-retry.service.ts` (002 bounded polling and
  retry budget are reused as-is).
- No change to `batchExecutor` / `quizExecutor` sizing or `CallerRunsPolicy`.
- No new npm/Maven dependency.
