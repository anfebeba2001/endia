# Implementation Plan: Guarantee Bounded Snack Generation

**Branch**: `009-bounded-snack-generation` | **Date**: 2026-09-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-bounded-snack-generation/spec.md`

## Summary

Hardening/liveness feature over the existing async snack/batch path. Earlier features already
delivered most of the bounds; this feature closes the remaining gaps and **proves** the whole path
finite. Confirmed current state (inputs, not re-investigated):

- The outbound HTTP client is built with connect+read timeouts from `llm.timeout-seconds` (default 30).
- `RetryingLlmProvider` retries retryable `LlmGenerationException` up to `batch.retry.max-attempts`
  (default 3) with backoff+jitter.
- `SnackService.generateValidSnack` re-rolls **at most once** on invalid content.
- `BatchService.runBatch` generates snacks **sequentially** with **no per-item timeout**, then runs
  quizzes in parallel with a bounded join (`batch.quiz-join-timeout-seconds`, default 240).
- `createBatch` triggers an `@Async` worker and returns 202; `getStatus` is read-only.
- Frontend polling is bounded (default 120 s) and terminates on any non-`IN_PROGRESS` status (002/005).

The remaining liveness gaps are:

1. **No per-subtopic wall-clock bound.** A provider that accepts a connection and never responds —
   or a custom `LlmProvider` that blocks indefinitely without honoring the HTTP timeout — blocks the
   sequential snack loop and therefore the whole batch worker forever.
2. **Bounded quiz join does not finalize items.** On quiz-join timeout, items whose quiz task hung
   remain `quizStatus = GENERATING` in the DB; `aggregateStatus` then returns `IN_PROGRESS`, so the
   batch never reaches a terminal status even though the worker proceeded.
3. **Empty-batch aggregation** returns `IN_PROGRESS` (defensive edge case).

The fix is intentionally small and surgical:

- **Per-subtopic bound (US1/US2/US3, FR-003/FR-006/FR-007/FR-008..FR-011)**: run each per-item
  `SnackService.generateSingleSnack(...)` on a **separate bounded `snackExecutor`** and apply
  `CompletableFuture.orTimeout(batch.snack-timeout-seconds)` (default **240**). On timeout, translate
  to the existing typed failure path (`LlmErrorCause.TIMEOUT`) so the item is marked `FAILED` and the
  worker proceeds to the next item.
- **Terminal guarantee (US4, FR-012..FR-014)**: after the bounded quiz join, **finalize** any item
  still `PENDING`/`GENERATING` (snack or quiz) to `FAILED` with a typed cause before aggregation, so
  `aggregateStatus` can always reach `COMPLETE`/`PARTIAL`/`FAILED`. Guard `aggregateStatus` for the
  empty-batch edge case.
- **Preserve & verify (US1/US2/US6, FR-001/002/004/005/018)**: keep `llm.timeout-seconds` and
  `batch.retry.max-attempts` and the single re-roll exactly as-is; add tests that *verify* them rather
  than replacing them. Frontend bounded polling is untouched.
- **No unanswered HTTP request (US5, FR-015..FR-017)**: no controller/DTO change; lock the immediate
  `202` and non-blocking `GET` with a real-context integration test against a hanging provider.
- **Safe logging (US7, FR-019/FR-020)**: log the bounded outcome and real typed cause only (subtopic
  id + bound + cause); never prompt bodies, keys, or provider payloads.

No API shape change, no schema change, no new statuses, no new enum values, no UI/prompt/provider
change, no `RetryingLlmProvider` redesign.

## Technical Context

**Language/Version**: Java 21 (backend). Frontend is untouched (TypeScript 5.x / Angular 21).

**Primary Dependencies**:
- Backend: Spring Boot 3.4.x, Spring MVC, Spring Data JPA/Hibernate 6.x, `@Async`/`ThreadPoolTaskExecutor`,
  `java.util.concurrent.CompletableFuture` / `orTimeout`. **No new dependencies.** The `RetryingLlmProvider`
  decorator is reused verbatim.
- Frontend: **no change.** `batch.service.ts` already bounds polling (`MAX_POLLING_WINDOW_MS`, default
  120 s) and defines terminality negatively (`status !== 'IN_PROGRESS'`); `SnackRetryService` already
  bounds automatic reattempts to 3.

**Storage**: MySQL 8.x — **no schema change**. No new entity, column, index, or enum value. A timeout
is surfaced through the existing `LlmErrorCause.TIMEOUT`, already persisted on `StudySessionItem`.
`ddl-auto=update` produces no delta. No migration.

**Testing**: JUnit 5 + Mockito (backend unit), Spring Boot Test + Testcontainers MySQL + MockMvc /
`TestRestTemplate` (backend integration, `@Tag("integration")`, run with `mvn test -Pintegration`).
Playwright E2E (`frontend/e2e/`) for non-regression only. No Karma/Jest runner exists.

**Target Platform**: Linux server (backend), modern browsers (frontend).

**Project Type**: Web application (monorepo: `backend/` + `frontend/`).

**Performance Goals**: A batch always reaches a terminal aggregate status within a bounded, computable
time even against a never-returning provider. Worst-case per-subtopic snack generation is finite:
`2 × (batch.retry.max-attempts × llm.timeout-seconds + backoff)` ≈ **186 s**, which fits inside the
default per-subtopic bound of **240 s**. The batch worker never blocks on a single item longer than the
per-subtopic bound.

**Constraints**: No auth. No schema change. No endpoint/DTO shape change. No new statuses or enum
values. Must not change `POST /api/snacks/generate`, `POST /api/batches`, the DTOs,
`RetryingLlmProvider`, the LLM port/prompts, `QuizService`, `QuizQualityGate`, or the 002 frontend
retry/polling logic. Must keep `BatchServiceTest`, `SnackServiceTest`, `RetryingLlmProviderTest`,
`OpenAiLlmProviderTest`, and the existing Playwright suite green.

**Scale/Scope**: Single-user MVP. 1 application service (`BatchService`), 1 infrastructure config
(`LlmConfig`), 1 resource file (`application.yml`), + unit/integration tests. No DB, no controller,
no DTO, no frontend source change.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Evidence |
|---|-----------|--------|----------|
| I | **Spec-Driven Development** | PASS | spec.md defines 7 user stories, 23 FRs, 10 SCs. Every FR/SC traces to a concrete file/item (see Mapping). |
| II | **Clean Architecture (Layered)** | PASS | The bound lives in the **application** layer (`BatchService` orchestration) using a `TaskExecutor` port; the executor bean is **infrastructure** (`LlmConfig`); controllers/DTOs are untouched. No provider-specific logic leaks into services/controllers. |
| III | **LLM Abstraction First** | PASS | No `LlmProvider`/prompt/provider change. `RetryingLlmProvider` is preserved verbatim (FR-004/FR-022); the bound is applied above the port, not inside a provider. |
| IV | **Quality Gate for Generated Content** | PASS | `QuizQualityGate` and `QuizService` are untouched. The bounded quiz join only changes what the worker does after the join, not quiz generation/validation. |
| V | **MVP-First, Iterate** | PASS | Smallest correct change set: one new config value, one new pool, one finalization step, plus tests. No persistence, no new statuses, no endpoint change, no UI redesign, no new dependency. Reuses `CompletableFuture.orTimeout` and the existing typed-cause path. |

**All gates PASS. No violations to justify.**

## Complexity Tracking

> No constitution violations. No complexity justifications needed.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | N/A | N/A |

## Project Structure

### Documentation (this feature)

```text
specs/009-bounded-snack-generation/
├── spec.md              # Feature specification (exists)
├── plan.md              # This file
├── research.md          # Phase 0 — decisions + rationale
├── data-model.md        # Phase 1 — config values + per-item status/cause (no schema change)
├── quickstart.md        # Phase 1 — local run + hang verification + tests
├── contracts/           # Phase 1 — API + bounded-behavior contracts
│   ├── batch-status.md            # HTTP contract: 202 immediate, non-blocking status, terminality
│   └── snack-generation-bounds.md # NEW: per-call / retry / re-roll / per-subtopic bounds → typed FAILED
└── tasks.md             # Phase 2 — NOT created by planner
```

### Source Code (repository root)

Only the marked files change. The rest of the batch flow is untouched.

```text
endia/
├── backend/
│   ├── src/main/java/com/endia/
│   │   ├── domain/…                                  # unchanged (no entity/enum/schema change)
│   │   ├── application/service/
│   │   │   └── BatchService.java                     # MOD: bounded per-item snack call + finalize + empty-batch guard
│   │   ├── infrastructure/llm/
│   │   │   └── LlmConfig.java                        # MOD: add separate `snackExecutor` bean (batchExecutor/quizExecutor unchanged)
│   │   └── interfaces/rest/…                         # unchanged (BatchController, DTOs)
│   ├── src/main/resources/
│   │   └── application.yml                           # MOD: add batch.snack-timeout-seconds (+ quiz-join-timeout-seconds) with env overrides
│   └── src/test/java/com/endia/
│       ├── application/service/
│       │   ├── BatchServiceTest.java                 # MOD: new ctor args; per-subtopic timeout; finalization; empty batch
│       │   └── SnackServiceTest.java                 # MOD/ADD: re-roll is exactly one (call-count assertion)
│       ├── infrastructure/llm/
│       │   └── OpenAiLlmProviderTest.java            # MOD/ADD: config-driven `llm.timeout-seconds` bound test
│       └── application/integration/
│           └── BoundedSnackGenerationIntegrationTest.java  # NEW: never-returning provider → per-item FAILED,
│                                                           #      terminal batch, prompt 202/GET, fast (short props)
└── frontend/…                                        # unchanged (002/005 bounded polling + retry budget preserved; E2E non-regression)
```

**Structure Decision**: Monorepo as-is. The per-subtopic bound is orchestration, so it belongs in the
**application** service (`BatchService`); the bounded executor it runs on is configuration and belongs
in the **infrastructure** LLM config (`LlmConfig`), consistent with the existing `batchExecutor` /
`quizExecutor` pattern. Translating a timeout into a typed failure is a service concern that must not
leak into controllers or the domain. The quiz-join finalization is also orchestration and stays in
`BatchService`. No new packages, ports, or dependencies; `SnackService` is unchanged because the bound
wraps its call rather than modifying its logic.

## Architecture Decisions

### 1. Per-subtopic wall-clock bound via `CompletableFuture.orTimeout` on a separate `snackExecutor` (FR-007/FR-008/FR-009/FR-010/FR-011)

Add a `snackExecutor` bean in `LlmConfig`:

```java
@Bean(name = "snackExecutor")
public TaskExecutor snackExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("snack-worker-");
    executor.setDaemon(true);                 // a truly stuck task cannot block JVM shutdown
    // default AbortPolicy: never CallerRunsPolicy (it would run the hang on the batch worker)
    executor.initialize();
    return executor;
}
```

`BatchService` injects it with `@Qualifier("snackExecutor")` plus
`@Value("${batch.snack-timeout-seconds:240}") int snackTimeoutSeconds`, and replaces the direct call in
`runBatch` with a bounded helper:

```java
private Snack generateSnackBounded(UUID subtopicId, SnackPhaseListener listener) {
    CompletableFuture<Snack> future;
    try {
        future = CompletableFuture.supplyAsync(
                () -> snackService.generateSingleSnack(subtopicId, listener), snackExecutor);
    } catch (RejectedExecutionException e) {
        throw new LlmGenerationException(LlmErrorCause.UNAVAILABLE, false,
                "Snack generation could not be scheduled.", e);
    }
    try {
        return future.orTimeout(snackTimeoutSeconds, TimeUnit.SECONDS).join();
    } catch (CompletionException e) {
        if (e.getCause() instanceof TimeoutException) {
            log.warn("Snack generation for subtopic {} exceeded the {}s per-subtopic bound; "
                    + "marking FAILED with cause TIMEOUT", subtopicId, snackTimeoutSeconds);
            throw new LlmGenerationException(LlmErrorCause.TIMEOUT, false,
                    "Snack generation exceeded the per-subtopic time bound.", e);
        }
        if (e.getCause() instanceof LlmGenerationException lge) {
            throw lge;                       // preserve the typed cause (QUOTA/TIMEOUT/…)
        }
        throw e;
    }
}
```

The existing `runBatch` `try`/`catch (LlmGenerationException)` then sets
`snackStatus = FAILED` and `snackCause = e.getCauseType()` (`TIMEOUT`), and the `finally` clears the
phase. The next loop iteration proceeds, so one hung item cannot block the worker (FR-010).

**Rationale**:
- `orTimeout` gives a **per-operation wall-clock** bound. The existing `llm.timeout-seconds` bounds each
  HTTP attempt and `RetryingLlmProvider` bounds the attempts; the new bound is the outer backstop that
  also covers providers that ignore HTTP timeouts and the re-roll (FR-007).
- Running on a **distinct** pool is mandatory: applying the bound on `batchExecutor` would be the 005
  cyclic-wait pattern, and the batch worker must not itself be the thread that hangs.
- Reusing `LlmErrorCause.TIMEOUT` keeps the failure on the existing typed path with no new status,
  enum value, DTO field, or schema change (FR-009, FR-021).
- **AbortPolicy, not CallerRunsPolicy**: with caller-runs, `supplyAsync` would execute the hanging
  supplier synchronously on the batch worker before returning a future, defeating the bound. On
  rejection the item is marked `FAILED`/`UNAVAILABLE` and the worker continues — still terminal.

**Rejected**:
- **Interrupt-based cancellation** (`Future.get(timeout)` + `cancel(true)`): a thread blocked in a
  socket read or a non-interruptible provider does not reliably unblock, so it does not strengthen the
  guarantee; it also complicates the code. `orTimeout` delivers the required *worker-proceeds*
  guarantee with less machinery.
- **Virtual-thread-per-task executor**: elegant for I/O-bound blocking, but introduces a novel runtime
  model inconsistent with the existing bounded platform pools; deferred (noted in `research.md`).
- **Applying the bound inside `SnackService`/`RetryingLlmProvider`**: would mix orchestration into the
  generation/retry layers and duplicate the existing per-call HTTP bound.
- **`@Transactional` + `runBatch`**: already rejected by 005 (holds a connection for the whole run).
- **Adding `batch.snack-max-attempts`**: redundant — `batch.retry.max-attempts` (3) × one re-roll is
  already finite. The wall-clock timeout is the only missing mechanism (see `research.md` R4/R8).

**Known limitation (flagged)**: `orTimeout` completes the future but does **not** interrupt the running
task, so a truly never-returning custom provider leaves one `snack-worker-` thread and its open
transaction parked. This is a resource cost, not a liveness gap: the batch worker still proceeds and
terminates. In production the real `OpenAiLlmProvider` always has the 30 s HTTP timeout, so tasks do
not actually hang; the leak only occurs with a pathological provider. The pool is bounded and daemon,
so it cannot grow without limit or block JVM shutdown. See Risks.

### 2. Finalize non-terminal items after the bounded quiz join so aggregation is always terminal (FR-012/FR-013/FR-014)

`runBatch` currently does `allOf(...).orTimeout(quizJoinTimeoutSeconds, SECONDS).exceptionally(...).join()`
and then aggregates. On timeout, a hung quiz task leaves its item `quizStatus = GENERATING`, and
`aggregateStatus` sees `GENERATING` → returns `IN_PROGRESS`. Add a finalization step between the join and
the aggregation:

```java
List<StudySessionItem> settled = itemRepository.findBySessionId(sessionId);
for (StudySessionItem item : settled) {
    boolean changed = false;
    if (isNonTerminal(item.getSnackStatus())) {
        item.setSnackStatus(GenerationStatus.FAILED);
        item.setSnackCause(LlmErrorCause.TIMEOUT);
        changed = true;
    }
    if (isNonTerminal(item.getQuizStatus())) {
        item.setQuizStatus(GenerationStatus.FAILED);
        item.setQuizCause(LlmErrorCause.TIMEOUT);
        changed = true;
    }
    if (changed) {
        itemRepository.save(item);
    }
}
session.setStatus(aggregateStatus(settled));
```

`isNonTerminal(s)` is `s == PENDING || s == GENERATING`. Additionally guard `aggregateStatus` for an
empty item list to return a terminal status (`FAILED`) rather than falling through to `IN_PROGRESS`.

**Rationale**: This is the missing half of the terminal guarantee. The bounded join makes the *worker*
proceed; finalization makes the *data* consistent with a terminal batch and satisfies FR-014 ("no snack
or quiz item remains pending/generating at a terminal status"). Snacks always settle before the quiz
phase (each is wrapped and caught), so the snack branch is defensive. `TIMEOUT` is the accurate typed
cause for an item that exceeded its bound. No schema/status/enum change.

**Rejected**:
- **Only bounding the join and trusting `aggregateStatus`**: leaves the batch stuck `IN_PROGRESS` with
  `GENERATING` items — the exact defect.
- **Adding a per-quiz-item timeout**: out of scope (spec bounds snacks); the bounded join plus
  finalization already yields a terminal batch. Flagged as a possible future hardening.
- **Setting the session status in the `exceptionally` handler**: would duplicate the aggregation logic
  and race the item reload.

### 3. Preserve and verify the existing per-call and retry bounds (FR-001/FR-002/FR-004/FR-005/FR-006)

No change to `LlmConfig.llmRestClient()` (`SimpleClientHttpRequestFactory` connect+read =
`llm.timeout-seconds`) or `RetryingLlmProvider` (`maxAttempts`, retryable-only, backoff+jitter), or
`SnackService.generateValidSnack` (single re-roll). Tests are added/kept to *verify* them:
- an `OpenAiLlmProviderTest` case that builds the `RestClient` from a `LlmConfig` with a small
  `timeout-seconds` and a delayed `MockWebServer` body, asserting a `TIMEOUT` failure within the bound;
- a `RetryingLlmProviderTest` case (existing) asserting exactly `maxAttempts` calls;
- a `SnackServiceTest` case asserting invalid content twice produces exactly two calls and then throws
  (no third re-roll);
- a `BatchServiceTest` case with a fast always-failing snack stub asserting the total call count is
  finite (`≤ maxAttempts × re-rolls`).

**Rationale**: FR-004/FR-005/FR-022 require the existing bounds to be preserved, and SC-009 requires the
002/005 behavior to pass unchanged. Verifying is cheaper and safer than re-implementing.

### 4. No frontend change; bounded polling is verified, not redesigned (FR-018)

`batch.service.ts` already defines terminality negatively and bounds the poll lifetime with
`race(polls$, timer(maxWindowMs))` and `MAX_POLLING_WINDOW_MS` (default 120 s); `SnackRetryService`
already caps automatic reattempts at 3. This feature changes nothing in `frontend/`. Verification is via
the existing Playwright specs (`polling-termination.spec.ts`, `retry-bound.spec.ts`,
`batch-empty-state.spec.ts`, `progressive-delivery.spec.ts`, `loading-states.spec.ts`). A new E2E spec
is added only if a gap is found during Phase 4.

## Phase-by-Phase Implementation Plan

### Phase 0 — Per-subtopic bounded snack generation (US1/US2/US3, P1)

| Step | File | Why |
|------|------|-----|
| 0.1 | `backend/.../infrastructure/llm/LlmConfig.java` | Add the separate bounded `snackExecutor` bean (`snack-worker-`, core=2/max=8/queue=50, daemon, AbortPolicy); leave `batchExecutor`/`quizExecutor` unchanged (FR-008, FR-021). |
| 0.2 | `backend/.../application/service/BatchService.java` | Inject `@Qualifier("snackExecutor")` and `@Value("${batch.snack-timeout-seconds:240}")`; add `generateSnackBounded(...)` using `supplyAsync(...).orTimeout(...)`; translate timeout/rejection to `LlmGenerationException` (`TIMEOUT`/`UNAVAILABLE`) (FR-007/FR-009/FR-011). |
| 0.3 | `backend/.../application/service/BatchService.java` | Replace the direct `snackService.generateSingleSnack(subtopicId, listener)` call in `runBatch` with `generateSnackBounded(...)`; keep the per-item `try`/`catch` and phase `finally` so the worker proceeds (FR-010). |
| 0.4 | `backend/.../application/service/BatchService.java` | Normalize an invalid `snack-timeout-seconds` (`<= 0` → default 240) so the edge case cannot hang or fail unpredictably (spec edge case, FR-011). |
| 0.5 | `backend/src/main/resources/application.yml` | Add `batch.snack-timeout-seconds: ${BATCH_SNACK_TIMEOUT_SECONDS:240}` and make `batch.quiz-join-timeout-seconds: ${BATCH_QUIZ_JOIN_TIMEOUT_SECONDS:240}` explicit (FR-011/FR-023). |

### Phase 1 — Terminal aggregate guarantee (US4, P1)

| Step | File | Why |
|------|------|-----|
| 1.1 | `backend/.../application/service/BatchService.java` | After the bounded quiz join, finalize any `PENDING`/`GENERATING` snack or quiz item to `FAILED` with `LlmErrorCause.TIMEOUT` before aggregating (FR-012/FR-014). |
| 1.2 | `backend/.../application/service/BatchService.java` | Guard `aggregateStatus` for an empty item list → terminal (`FAILED`), so a batch with no subtopics terminates immediately (spec edge case). |
| 1.3 | `backend/.../application/service/BatchService.java` | Log the bounded quiz-join outcome with the real cause and no payload (FR-019/FR-020). |

### Phase 2 — Tests (all stories)

| Step | File | Why |
|------|------|-----|
| 2.1 | `backend/.../service/BatchServiceTest.java` | Update the constructor (new `snackExecutor` + `snackTimeoutSeconds` args); add: per-subtopic timeout → item `FAILED`/`TIMEOUT` and the next item still generated; quiz-join timeout finalization → terminal status and no `GENERATING`; empty batch → terminal; invalid config normalized. |
| 2.2 | `backend/.../service/SnackServiceTest.java` | Assert the re-roll is exactly one: two invalid responses → exactly two provider calls, then a typed `ServiceUnavailableException` (FR-005/FR-006). |
| 2.3 | `backend/.../llm/OpenAiLlmProviderTest.java` | Assert `llm.timeout-seconds` drives the connect+read bound: a `LlmConfig`-built `RestClient` with a small timeout fails a delayed response with `TIMEOUT` (FR-001/FR-002/FR-003/SC-001). Keep the existing retry/cause tests. |
| 2.4 | `backend/.../llm/RetryingLlmProviderTest.java` | Keep/confirm exactly-`maxAttempts` calls and non-retryable pass-through (FR-004). |
| 2.5 | `backend/.../integration/BoundedSnackGenerationIntegrationTest.java` (NEW) | Real Spring context + Testcontainers + a **never-returning** `@MockitoBean LlmProvider` (interruptible latch); short `batch.snack-timeout-seconds`/`batch.quiz-join-timeout-seconds` via `@DynamicPropertySource`. Assert: each item `FAILED`/`TIMEOUT`, the batch reaches a terminal status within the bound, no item is `PENDING`/`GENERATING` at terminal, and `POST /api/batches` returns 202 promptly while `GET /api/batches/{id}` returns promptly (FR-012/FR-013/FR-014/FR-015/FR-016/SC-003/SC-004/SC-005). |
| 2.6 | Existing suites | `mvn test` stays green (`BatchFlowIntegrationTest`, `BatchStatusContractTest`, `Quiz*`, `Theme*`, `Subtopic*`); `mvn test -Pintegration` green; `npx playwright test` green (FR-022/SC-009). |

### Phase 3 — Contracts, docs, verification

| Step | File | Why |
|------|------|-----|
| 3.1 | `contracts/batch-status.md` | Restate the unchanged HTTP shape with the 202-immediate / non-blocking-status / terminal-status and no-pending-item guarantees (FR-012/FR-015/FR-016/FR-017). |
| 3.2 | `contracts/snack-generation-bounds.md` (NEW) | Bounded-behavior contract: per-call HTTP timeout, retry bound, re-roll bound, per-subtopic bound → typed `FAILED`; config properties and defaults (FR-001/FR-004/FR-005/FR-007/FR-008/FR-009/FR-011/FR-023). |
| 3.3 | `quickstart.md` | Manual hang reproduction and verification plus the exact test commands (US1–US7). |
| 3.4 | FR-019/FR-020 inspection | Confirm timeout/failure logs and user-facing messages contain only typed causes and ids — no prompts, keys, provider payloads, or personal data. |
| 3.5 | Frontend non-regression | Run the existing bounded-polling/retry E2E specs; add a spec only if a gap is found (FR-018/SC-006). |

## Milestones

- **M0** (P1, backend): `snackExecutor` + bounded per-item generation + config (Phase 0). Delivers US1/US2/US3 and FR-003/FR-006/FR-007/FR-008..FR-011.
- **M1** (P1, backend): quiz-join finalization + empty-batch guard (Phase 1). Delivers US4 and FR-012..FR-014.
- **M2** (P1, tests): unit + integration coverage, including the never-returning provider (Phase 2). Delivers SC-001..SC-005, SC-008.
- **M3** (P2, docs/verification): contracts, quickstart, sensitive-log inspection, frontend non-regression (Phase 3). Delivers US5/US6/US7, SC-006/SC-007/SC-009/SC-010.

## Risks

- **Abandoned hung task holds a thread + DB transaction.** `orTimeout` does not interrupt the running
  task, so a pathological never-returning provider leaves one `snack-worker-` thread and its
  transaction parked. Mitigations: the pool is bounded and daemon; the real provider always has the
  30 s HTTP timeout; the test double is interruptible so context shutdown reclaims it. **Flagged for
  the orchestrator** as a resource-cost, not liveness, limitation.
- **Quiz tasks can still hang and leak.** Only the *join* is bounded and items are finalized; the hung
  quiz task itself is not interrupted. Acceptable for MVP scale; a per-quiz-item bound is a candidate
  future hardening. **Flagged.**
- **`CallerRunsPolicy` on `quizExecutor` under saturation can run a quiz inline on the batch worker.**
  With a hanging provider, an inline quiz could block the worker past the join bound before
  `allOf().orTimeout(...)` is even reached. The `quizExecutor` queue is 100 and the API caps a batch at
  10 subtopics, so saturation is not reachable at MVP scale; preserved from 005 (FR-022). **Flagged as
  a theoretical hole.**
- **Finalization/quiz-task race.** A quiz task that completes just after the join timeout could
  overwrite the finalized `FAILED` with `READY`, leaving an item `READY` while the batch is terminal.
  Only pathological hangs hit this; the join default (240 s) is far above normal quiz latency. Documented.
- **`@Async` self-invocation in tests.** Integration tests must use the proxied `BatchService` bean and
  poll `getStatus`; calling `runBatch` on a raw instance bypasses `@Async`/transaction proxies.
- **`@MockitoBean LlmProvider` context reload.** The never-returning stub replaces the primary
  `RetryingLlmProvider`; keep it in its own test class so the existing `BatchGenerationIntegrationTest`
  context is unaffected.
- **Constructor-signature change.** Adding `snackExecutor`/`snackTimeoutSeconds` to `BatchService`
  changes the constructor; `BatchServiceTest` must be updated. Spring wiring is unaffected.
- **Test wall-clock.** The never-returning integration test must set short `snack-timeout-seconds` /
  `quiz-join-timeout-seconds` (e.g. 2 s) via `@DynamicPropertySource`; relying on the 240 s defaults
  would make the suite unusable.

## Mapping: Spec Requirements → Plan Items

| Spec ID | Requirement | Plan Item |
|---------|-------------|-----------|
| FR-001 | Per-call connect+read timeout (`llm.timeout-seconds`, default 30) | Preserved; verified by 2.3 |
| FR-002 | Timeout applies to connect+read, configurable | Preserved (`LlmConfig.llmRestClient`); 2.3 |
| FR-003 | Test that a hanging call cannot exceed the bound | 2.3 (config-driven) + 2.5 (provider-level) |
| FR-004 | Retries bounded by `batch.retry.max-attempts` (default 3) | Preserved (`RetryingLlmProvider`); 2.4 |
| FR-005 | Re-roll limited to a single bounded re-roll, then typed failure | Preserved (`generateValidSnack`); 2.2 |
| FR-006 | Test: finite, bounded number of LLM calls | 2.2 + 2.4 + 2.5 |
| FR-007 | Worst-case single-subtopic cost finite and ≤ per-subtopic bound | 0.2 + 0.4; worst case 186 s ≤ 240 s (Summary/Performance) |
| FR-008 | Per-subtopic bound (wall-clock and/or attempts), configurable | 0.1/0.2/0.5 (`batch.snack-timeout-seconds`, default 240) |
| FR-009 | Exceeding the bound → item `FAILED` with typed cause | 0.2/0.3 (`LlmErrorCause.TIMEOUT`); 2.1 |
| FR-010 | Bound does not block the worker; it proceeds | 0.3 (per-item try/catch) + 0.1 (separate pool); 2.1 |
| FR-011 | Per-subtopic bound configurable with documented default | 0.2/0.4/0.5; 2.1 (config change observed) |
| FR-012 | Batch always reaches a terminal status within bounded time | 1.1/1.2; 2.5 |
| FR-013 | Test: never-returning provider → terminal within bound | 2.5 |
| FR-014 | No item PENDING/GENERATING at terminal | 1.1; 2.5 |
| FR-015 | `POST /api/batches` returns 202 immediately | 2.5 (real-context HTTP) |
| FR-016 | `GET /api/batches/{id}` does not wait on generation | 2.5 |
| FR-017 | No synchronous HTTP request left unanswered | 2.5 + existing per-call bound (FR-001) |
| FR-018 | Frontend bounded polling (default 120 s) preserved | Unchanged (`batch.service.ts`); 3.5 |
| FR-019 | Timeout/failure paths log the real bounded cause | 0.2 + 1.3; 3.4 |
| FR-020 | No sensitive data in logs/errors | 3.4 (inspection) |
| FR-021 | Layered architecture; no provider logic in services/controllers | 0.1 (infra) + 0.2/0.3 (application); no controller change |
| FR-022 | Preserve 002/005; no UI/prompt/provider/retry-algorithm redesign | Frozen surfaces (Phase 0–1); 2.6/3.5 |
| FR-023 | All new bounds configurable with documented defaults | 0.5 + contracts 3.1/3.2 |
| SC-001 | 100% of hanging calls terminate at/before the bound | 2.3 + 2.5 |
| SC-002 | Finite bounded call count, 0 unbounded loops | 2.2 + 2.4 + 2.5 |
| SC-003 | Exceeded item `FAILED` with typed cause; worker proceeds | 0.2/0.3 + 2.1 |
| SC-004 | 100% of batches terminal; 0 remain IN_PROGRESS | 1.1/1.2 + 2.5 |
| SC-005 | 202 immediate + non-blocking GET, incl. hanging provider | 2.5 |
| SC-006 | Frontend polling terminates on terminal/window | Unchanged; 3.5 |
| SC-007 | 100% of timeout/failure logs carry the real cause, 0% sensitive | 3.4 |
| SC-008 | 0 tests observe an unbounded loop/unanswered request | 2.1–2.5 |
| SC-009 | No regression in 002/005 behavior | 2.6 + 3.5 |
| SC-010 | Every new bound configurable + observed to change behavior | 0.4/0.5 + 2.1 |

## Testing Strategy

### Backend unit tests
- `BatchServiceTest` (updated constructor: `snackExecutor`, `snackTimeoutSeconds`):
  - **Per-subtopic timeout**: a `snackService` stub that blocks on a latch, a real single-thread
    `TaskExecutor`, `snackTimeoutSeconds = 1`; assert the item is `FAILED` with `TIMEOUT`, the phase is
    cleared, and a second (healthy) item still becomes `READY` — proving the worker is not blocked.
  - **Quiz-join finalization**: a hung quiz stub + short join; assert the item's quiz is `FAILED`/
    `TIMEOUT` and the session status is terminal (`FAILED`/`PARTIAL`), never `IN_PROGRESS` with a
    `GENERATING` item.
  - **Empty batch**: `aggregateStatus(empty)` yields a terminal status.
  - **Config normalization**: a `<= 0` `snackTimeoutSeconds` falls back to the documented default.
  - Existing `retrying`/phase/partial-failure assertions stay green.
- `SnackServiceTest`: two invalid responses → exactly two provider calls, then a typed throw (single
  re-roll, FR-005/FR-006).
- `RetryingLlmProviderTest`: existing exactly-`maxAttempts` and non-retryable pass-through assertions
  retained (FR-004).
- `OpenAiLlmProviderTest`: a `LlmConfig`-built `RestClient` with a small `llm.timeout-seconds` fails a
  delayed `MockWebServer` response with `TIMEOUT` within the bound (FR-001/FR-002/FR-003).

### Backend integration tests (`@Tag("integration")`, `mvn test -Pintegration`)
- `BoundedSnackGenerationIntegrationTest` (NEW): real Spring context + Testcontainers MySQL +
  `@MockitoBean LlmProvider` whose `generateSnack`/`generateQuiz` block on an interruptible latch
  (never return). `@DynamicPropertySource` sets `batch.snack-timeout-seconds=2`,
  `batch.quiz-join-timeout-seconds=2` (and a small `llm.timeout-seconds`). Assertions:
  - `POST /api/batches` returns **202** promptly (well under the snack bound) and does not wait for
    generation;
  - `GET /api/batches/{id}` returns promptly while the batch is still non-terminal;
  - every item settles to `FAILED` with `TIMEOUT`;
  - the batch reaches `COMPLETE`/`PARTIAL`/`FAILED` within the bounded window and never remains
    `IN_PROGRESS` with `PENDING`/`GENERATING` items;
  - the latch is released in `@AfterEach`/context shutdown so the test JVM exits promptly.
- Existing `BatchGenerationIntegrationTest` and `BatchFlowIntegrationTest` remain green.

### Frontend (Playwright, non-regression only)
- `polling-termination.spec.ts` (terminal on `PARTIAL`/`COMPLETE`/`FAILED`; stuck `IN_PROGRESS`
  terminates at the overridden window), `retry-bound.spec.ts` (exactly 3 attempts, no 4th),
  `batch-empty-state.spec.ts`, `progressive-delivery.spec.ts`, `loading-states.spec.ts` stay green.
- No frontend source change; a new E2E spec is added only if Phase 4 reveals a gap.

### Manual verification
See `quickstart.md`: point the app at a provider that hangs (or a mock that accepts and never
responds), create a batch, and confirm each item settles `FAILED`/`TIMEOUT` within
`batch.snack-timeout-seconds`, the batch reaches a terminal status, `POST`/`GET` stay prompt, and the
logs show the bound and cause with no sensitive data.

### What is explicitly not tested / not changed
- No DB migration tests (no schema change). No new endpoint/DTO tests. No changes to `QuizServiceTest`,
  `QuizQualityGateTest`, `Theme*`/`Subtopic*` tests. No frontend unit runner is introduced.
