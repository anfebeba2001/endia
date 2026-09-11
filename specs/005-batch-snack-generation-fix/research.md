# Research: Batch Snack Generation Fix

**Phase 0 output** for `/specs/005-batch-snack-generation-fix`. Each section records the Decision, Rationale, and Alternatives considered, grounded in the real code (`backend/.../application/service/BatchService.java`, `backend/.../application/service/SnackService.java`, `backend/.../infrastructure/llm/LlmConfig.java`, `frontend/src/app/pages/snacks/snacks.component.ts`).

The spec is a defect fix over the `001`/`002` batch flow. The three root causes are treated as confirmed inputs. The governing constraint is the smallest correct change set: no schema change, no endpoint/DTO change, no new statuses, no LLM-algorithm change, no snack-page redesign, and preservation of the `002` bounded-retry and bounded-polling behavior.

---

## R1. How to make the theme context available at generation time

**Decision**: Add a transactional by-id overload and have the batch worker call it:

```java
@Transactional
public Snack generateSingleSnack(UUID subtopicId) {
    Subtopic subtopic = subtopicRepository.findById(subtopicId)
            .orElseThrow(() -> new EntityNotFoundException("Subtopic", subtopicId));
    return generateAndPersist(subtopic);
}
```

`generateSingleSnack(Subtopic)` and `generateSingleSnack(Subtopic, Consumer)` are retained and all three delegate to a private `generateAndPersist(Subtopic)`. `BatchService.runBatch` resolves `item.getSubtopic().getId()` and calls the by-id overload.

**Rationale**:
- The failing line is `subtopic.getTheme().getPrompt()` inside `@Transactional generateSingleSnack(Subtopic)`. The transaction is open, but the passed `Subtopic` is a **detached** proxy from `runBatch`'s earlier repository read, so `getTheme()` has no session to initialize against. Re-fetching the subtopic *inside* the transaction guarantees an open session for the lazy theme access (FR-001).
- No detached entity crosses the transactional boundary (FR-002), so the generic catch no longer records `UNAVAILABLE` for every item (FR-003).
- Keeping the entity overload preserves the single-shot path exactly (FR-017). A private helper avoids the `@Transactional` self-invocation trap (calling an annotated method on `this` does not start a new transaction) and keeps one copy of the generation/validation logic.
- `item.getSubtopic().getId()` is safe on an uninitialized Hibernate proxy: the identifier getter is intercepted and returns the id without initializing. This avoids initializing the theme on the detached instance.

**Alternatives**:
- **Make `runBatch` `@Transactional`**: rejected. It would hold a database connection for the entire LLM sequence (potentially minutes), violating connection-pool hygiene and the async design.
- **`@EntityGraph`/`join fetch` on the item query to load `subtopic.theme`**: rejected as the primary fix. It eagerly loads theme data for the whole batch even though only one item is processed at a time, and it still leaves the entity detached at use time; the transactional re-fetch is the correct scope. Retained only as optional hardening for the subtopic reference (see below).
- **Fetch the subtopic in `BatchService` without a transaction**: rejected. The fetch would close before the LLM call, reproducing the same lazy failure.
- **Change the `Subtopic.theme` mapping to `EAGER`**: rejected. It would change fetch behavior globally, contradicting "smallest correct change" and potentially causing N+1 queries elsewhere.

**Optional hardening**: `@EntityGraph(attributePaths = {"subtopic"})` on `JpaStudySessionItemRepository.findBySessionId`. This makes the "resolve related data before detach" half of FR-001 explicit and removes any reliance on Hibernate proxy-id behavior. It is a read-shape optimization, not a schema change.

---

## R2. How to eliminate the executor self-deadlock

**Decision**: Introduce a **separate** `quizExecutor` bean for the nested quiz prefetch and keep `batchExecutor` for top-level workers only:

```java
@Bean(name = "quizExecutor")
public TaskExecutor quizExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(16);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("quiz-worker-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
}
```

`BatchService` injects `@Qualifier("quizExecutor")` and uses it for `CompletableFuture.runAsync(...)`. Additionally, the join is bounded:

```java
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .orTimeout(quizJoinTimeoutSeconds, TimeUnit.SECONDS)
        .exceptionally(ex -> null);
```

**Rationale**:
- The defect is a **cyclic wait**: the batch worker occupies `batchExecutor` and waits on quiz tasks that are queued on that same pool. Moving the children to a distinct pool removes the cycle by construction — the worker's own threads can never be required to complete its children.
- **Sizing**: `batchExecutor` stays core=2/max=8/queue=50 (`batch-worker-`). No `CallerRunsPolicy` on `batchExecutor`, because it is invoked from `createBatch`'s `@Transactional` method — a caller-runs fallback would run the entire batch synchronously on the HTTP request thread *inside the create transaction*. `quizExecutor` is sized for the fan-out: core=4 (several concurrent batches), max=16 (bounds provider concurrency), queue=100 (absorbs bursts). Its `CallerRunsPolicy` is the liveness valve: if the quiz pool and queue saturate, the submitting batch-worker executes the quiz itself, so `allOf()` always completes. Distinct prefixes (`batch-worker-` / `quiz-worker-`) make both pools observable in logs and thread dumps.
- **Bounded join**: even a pathologically hung quiz task cannot hold the worker forever; `orTimeout` makes the worker proceed to aggregate a terminal status (FR-005/FR-006). The property `batch.quiz-join-timeout-seconds` (default 240) keeps it configurable.

**Nuance (flagged)**: With `batchExecutor` `core=2`, a **single** batch worker blocks on `join()` while the second core thread drains the queue, so one batch can still complete. The deadlock manifests when all core threads are held by batch workers — i.e. **≥2 concurrent batches**, or any other saturation of the core pool. The separate pool removes the cycle unconditionally, so the fix is correct either way; the regression test must run concurrent batches (or otherwise saturate the core pool) to be a meaningful guard.

**Alternatives**:
- **Increase `batchExecutor` core/max**: rejected. It does not remove the cycle; `max` is only reached after the queue fills, so the same starvation can recur at a higher threshold.
- **Use a shared `ForkJoinPool.commonPool()`**: rejected. It is process-wide and shared, so nested tasks can still contend with the worker; explicit pools are clearer and observable.
- **Generate quizzes sequentially on the batch worker**: rejected. It removes the prefetch parallelism and is a behavioral regression (FR-018 spirit).
- **Use `CompletableFuture` with an unbounded cached thread pool**: rejected. Unbounded thread creation under provider latency is a resource risk; a bounded pool with `CallerRunsPolicy` is safer and still guarantees liveness.

---

## R3. How to prevent the empty-state flash and terminate the polling UI

**Decision**: Add an explicit `polling` signal to `SnacksComponent` and gate the empty state on it:

- `polling` is set `true` when a generation cycle starts (initial `createBatch` success and each retry cycle start).
- It is set `false` on a terminal poll emission (`status.status !== 'IN_PROGRESS'`) and on any poll error (including `BatchPollingTimeoutError`).
- `showEmptyState = !creating() && !polling() && items().length === 0 && batchStatus() !== 'IN_PROGRESS' && !errorMessage()`.
- The template renders a progress block while `!creating() && polling() && items().length === 0`, and the empty state only when `showEmptyState()` is true.

**Rationale**:
- The flash window is precisely "create succeeded, first poll has not returned, `items()` empty". `creating` currently flips to `false` at that instant, so the empty state renders. A dedicated `polling` flag keeps a progress state visible across that window (FR-009/FR-010).
- Clearing `polling` only on terminal/error means the empty state cannot render while a batch is non-terminal (FR-011); the additional `batchStatus() !== 'IN_PROGRESS'` gate covers the retry path where `batchStatus` may briefly hold a prior value.
- The terminality check itself is already negative and polling already completes on any non-`IN_PROGRESS` status from `002` (FR-012/FR-013). The only reason polling appeared perpetual was the backend never reaching a terminal status; with R1/R2 fixed, `pollStatus` completes and the retry indicator (`status.retrying || hasInFlight()`) settles (FR-014). Therefore `batch.service.ts` and `snack-retry.service.ts` need no change.

**Alternatives**:
- **Keep `creating=true` until the first poll**: viable but conflates "creating the batch" with "polling for progress"; the spec distinguishes the phases (FR-009), and a dedicated flag is clearer.
- **Use a timeout/debounce to suppress the empty state**: rejected. Timing-based and racy; the spec requires the empty state to be tied to terminality, not elapsed time.
- **Show the empty state only when `status === 'FAILED'`**: rejected. A genuinely complete/partial batch with zero ready snacks must also surface a terminal outcome (FR-011 / US3.4).
- **Rely solely on `batchStatus()` (no `polling` flag)**: rejected. It does not cover the window before the first poll sets `batchStatus` (which defaults to `IN_PROGRESS`) and is less explicit about the first-response boundary FR-010 calls out.

---

## R4. Preserving the `002` bounded retry and the single-shot / quiz contracts

**Decision**: Do not touch `SnackRetryService`, `batch.service.ts`, `RetryingLlmProvider`, `QuizService`, `QuizQualityGate`, `POST /api/snacks/generate`, or the DTOs.

**Rationale**:
- FR-016 requires the ≤3 automatic attempts bound to be preserved; it lives entirely in `SnackRetryService` and the component's retry paths, which this feature does not modify (the `polling` flag is additive and does not alter budget/guard logic).
- FR-017 requires the single-shot snack contract to be unchanged; keeping `generateSingleSnack(Subtopic)` and routing the batch through the new by-id overload satisfies this.
- FR-018 requires quiz generation to be unchanged; only the *executor* the prefetch runs on changes, not `QuizService` or its contract.
- FR-015 (120 s polling window) and FR-012/FR-013 (negative terminality, stop-on-terminal) are already implemented in `batch.service.ts` from `002`; this feature only needs the backend to actually terminate.

**Alternatives**:
- **Re-implement retry/polling in this feature**: rejected. It would duplicate `002` and risk regressions.
- **Move the by-id resolution into `SnackService.generateSnacks(themeId, ids)`**: not applicable; the batch worker processes items individually to update per-item status progressively.

---

## R5. Persistence / schema impact

**Decision**: **No persistence or schema change.**

**Rationale**:
- No new entity, column, index, constraint, or enum value. The `StudySession` / `StudySessionItem` / `Snack` / `Subtopic` / `Theme` model is reused as-is.
- The `@EntityGraph` on `findBySessionId` is a read-shape annotation, not a schema change; `ddl-auto=update` produces no delta.
- No Flyway/Liquibase migration is used by the project (`ddl-auto=update`); there is nothing to apply.

**Alternatives**:
- **Persist a batch-level retry/terminal marker**: rejected as out of scope; the spec explicitly preserves the existing status vocabulary and the bounded-retry state is frontend in-memory.

---

## R6. Observability and sensitive-data safety (FR-019)

**Decision**: Reuse the existing typed `LlmErrorCause` handling and add no new payload logging. Audit the batch failure paths in Phase 4.

**Rationale**:
- LLM failures are caught as `LlmGenerationException` and mapped to a typed cause without logging the provider payload.
- The generic catch logs a subtopic id and the exception; in this path the exception is a Hibernate lazy-init error (entity name) or a generic service error. No credentials, API keys, raw prompts, or provider payloads are logged.
- The `orTimeout`/`exceptionally` path logs only a generic message, not child exceptions.

**Alternatives**:
- **Log full LLM request/response for diagnosis**: rejected. Explicitly forbidden by FR-019.

---

## Cross-cutting: what is NOT changed

- No DB schema / entity / enum change; no migration.
- No change to `POST /api/snacks/generate`, `POST /api/batches`, or any DTO shape.
- No change to `RetryingLlmProvider`, the LLM port, or prompts.
- No new status vocabulary; `BatchStatus` already contains `PARTIAL`.
- No change to `SnackRetryService` or `batch.service.ts` (the `002` bounded-retry and bounded-window logic is reused).
- No snack-page redesign; the existing card, error banner, retry affordance, and spinner styles are reused.
- No new npm/Maven dependency.
