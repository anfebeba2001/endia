# Implementation Plan: Batch Snack Generation Fix

**Branch**: `005-batch-snack-generation-fix` | **Date**: 2026-09-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-batch-snack-generation-fix/spec.md`

## Summary

Defect fix over the existing async batch flow. Three confirmed, independent root causes:

1. **Detached related-data access.** `BatchService.runBatch` is `@Async` and non-transactional. It loads `StudySessionItem`s (and their lazy `Subtopic` proxies) in a repository transaction, then passes a detached `Subtopic` into `@Transactional SnackService.generateSingleSnack(Subtopic)`, which dereferences `subtopic.getTheme().getPrompt()` (`SnackService.java:66`) after the persistence context has closed → `LazyInitializationException`, swallowed by the generic catch and recorded as `FAILED`/`UNAVAILABLE` for **every** item.
2. **Worker self-deadlock on a shared bounded executor.** `batchExecutor` (`core=2, max=8, queue=50`) runs `runBatch`, which submits nested per-subtopic quiz tasks back to the *same* executor and blocks on `CompletableFuture.allOf(...).join()`. With the pool held by batch workers, the queued quiz tasks can never run, so the worker waits forever and the batch never reaches a terminal status.
3. **Premature empty-state flash + non-terminating polling.** `SnacksComponent` sets `creating=false` immediately after `createBatch` succeeds, before the first poll returns and while `items()` is still empty, so the template renders "No snacks were prepared…". The stuck backend keeps `status = IN_PROGRESS`, so polling/retry never settles.

The fix is intentionally small and surgical:

- **Persistence scope (US1 / FR-001..003)**: add a transactional `SnackService.generateSingleSnack(UUID subtopicId)` that resolves the `Subtopic` (and its `Theme`) **inside** the transaction and delegates to the existing generation logic. `BatchService.runBatch` calls it by id and no longer passes a detached entity. The existing `generateSingleSnack(Subtopic)` overload is kept for the single-shot path.
- **Executor separation (US2 / FR-004..006)**: introduce a **separate** `quizExecutor` bean for the nested quiz prefetch so the batch worker can never be starved by its own children. Add a bounded join (`orTimeout`) as a liveness safety valve so the worker always proceeds to aggregate a terminal status.
- **Presentation state (US3 / FR-009..014)**: add a `polling` signal set on create success and cleared on the first terminal/error; gate the empty state on `!polling()` so it only appears for a genuinely terminal, empty batch. The backend fix now lets the existing negative-terminality check (from `002`) actually observe a terminal status, so polling/retry terminate.

No API shape change, no schema change, no new statuses, and the bounded-retry (`002`) and single-shot/quiz contracts are preserved.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.x (frontend), Angular 21

**Primary Dependencies**:
- Backend: Spring Boot 3.4.x, Spring MVC, Spring Data JPA/Hibernate 6.x, `@Async`/`ThreadPoolTaskExecutor`. **No new dependencies.** The `RetryingLlmProvider` decorator from `001` is reused verbatim.
- Frontend: Angular 21 standalone + signals, `HttpClient`, RxJS (`interval`, `race`, `timer`, `switchMap`, `takeWhile`). **No new dependencies.** No change to `batch.service.ts` or `snack-retry.service.ts` — the `002` terminality and bounded-window logic already exist and only need the backend to actually terminate.

**Storage**: MySQL 8.x — **no schema change**. No new entity, column, index, or enum. The fix is a read-scope/executor/UI-state change. `ddl-auto=update` produces no delta. No migration.

**Testing**: JUnit 5 + Mockito (backend unit), Spring Boot Test + Testcontainers MySQL (backend integration; see `BatchFlowIntegrationTest`), Angular Playwright (`frontend/e2e/`, `helpers.ts`). No Karma/Jest runner exists under `frontend/src/app`.

**Target Platform**: Linux server (backend), modern browsers Chrome/Firefox/Safari latest 2 versions (frontend).

**Project Type**: Web application (monorepo: `backend/` + `frontend/`).

**Performance Goals**: A batch always reaches a terminal aggregate status within a bounded time; the batch worker never waits on work that requires its own threads. A successful model response yields a persisted `READY` snack returned by `GET /api/batches/{id}`. The snacks screen shows no empty state before the first poll and stops polling within one interval of any non-`IN_PROGRESS` status.

**Constraints**: No auth. No schema change. No endpoint/DTO shape change. No new statuses. Must not change `POST /api/snacks/generate`, `POST /api/batches`, `RetryingLlmProvider`, or the quiz generation contract. Must preserve the `002` bounded-retry behavior (≤3 cycles per target) and the 120 s polling window. Must keep the existing Playwright suite green and `BatchServiceTest`/`SnackServiceTest` passing (updated for the by-id overload).

**Scale/Scope**: Single-user MVP. 3 modified backend main files (+1 optional repository hardening) + 3 backend test files + 2 frontend files + 1 new E2E spec. No DB, no controller, no DTO, no prompt changes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Evidence |
|---|-----------|--------|----------|
| I | **Spec-Driven Development** | PASS | spec.md defines 4 user stories, 20 FRs, 8 SCs, and the confirmed root causes. Every FR traces to a concrete file/item (see Mapping). |
| II | **Clean Architecture (Layered)** | PASS | Persistence-scope handling lives in the **application** layer (`SnackService`); executor configuration lives in the **infrastructure** layer (`LlmConfig`); UI polling state lives in the **interface** layer (`SnacksComponent`). Domain and DTOs are untouched. No framework leakage into domain. |
| III | **LLM Abstraction First** | PASS | No `LlmProvider` / prompt / provider change. `RetryingLlmProvider` is preserved verbatim (FR-016/FR-018). |
| IV | **Quality Gate for Generated Content** | PASS | `QuizQualityGate` and `QuizService` untouched; quiz work is only re-homed onto a different executor (FR-018). |
| V | **MVP-First, Iterate** | PASS | Smallest correct change set: no persistence, no new statuses, no endpoint change, no UI redesign, no new dependencies. Reuses existing overloads, beans, and the `002` polling logic. |

**All gates PASS. No violations to justify.**

## Complexity Tracking

> No constitution violations. No complexity justifications needed.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | N/A | N/A |

## Project Structure

### Documentation (this feature)

```text
specs/005-batch-snack-generation-fix/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Phase 0 — decisions + rationale
├── data-model.md        # Phase 1 — conceptual model (no schema change)
├── quickstart.md        # Phase 1 — local run + failure/hang verification
├── contracts/           # Phase 1 — API + behavioral contracts
│   ├── batch-status.md            # UPDATED: restates unchanged shape + terminality/retrying
│   └── batch-generation-behavior.md  # NEW: backend execution contract (scope + executors)
└── tasks.md             # Phase 2 — NOT created by planner
```

### Source Code (repository root)

Only the marked files change. The rest of the batch flow is untouched.

```text
endia/
├── backend/
│   └── src/
│       ├── main/java/com/endia/
│       │   ├── domain/…                                  # unchanged (no entity/enum/schema change)
│       │   ├── application/service/
│       │   │   ├── BatchService.java                     # MOD: call generateSingleSnack(UUID); use quizExecutor; bounded join
│       │   │   └── SnackService.java                     # MOD: add @Transactional generateSingleSnack(UUID)
│       │   ├── infrastructure/
│       │   │   ├── llm/LlmConfig.java                    # MOD: add separate `quizExecutor` bean (batchExecutor unchanged)
│       │   │   └── persistence/
│       │   │       └── JpaStudySessionItemRepository.java  # MOD (hardening, optional): @EntityGraph subtopic
│       │   └── interfaces/rest/…                         # unchanged (BatchController, DTOs)
│       └── test/java/com/endia/
│           ├── application/service/
│           │   ├── BatchServiceTest.java                 # MOD: quizExecutor ctor arg; by-id overload stubs; regression assertions
│           │   └── SnackServiceTest.java                 # MOD: generateSingleSnack(UUID) loads subtopic+theme
│           └── application/integration/
│               └── BatchGenerationIntegrationTest.java   # NEW: real executors + stub LLM → READY + terminal in bounded time
│
└── frontend/
    ├── e2e/
    │   └── batch-empty-state.spec.ts                     # NEW: no empty-state flash; terminal-only empty state
    └── src/app/pages/snacks/
        ├── snacks.component.ts                           # MOD: `polling` signal; clear on terminal/error
        └── snacks.component.html                         # MOD: gate empty state on !polling(); add progress block
```

**Structure Decision**: Monorepo as-is. The persistence-scope fix belongs in the application service (`SnackService`) because resolving lazy associations is a transactional concern and must stay out of the async orchestration (`BatchService`) and out of the domain. The executor split belongs in the infrastructure LLM/config layer where the existing `batchExecutor` is defined. The empty-state fix is pure presentation state and stays in the interface component. No new packages, ports, or dependencies.

## Architecture Decisions

### 1. Resolve related data inside a valid persistence context — by id, not by detached entity (FR-001/FR-002/FR-003)

Add a transactional overload to `SnackService`:

```java
@Transactional
public Snack generateSingleSnack(UUID subtopicId) {
    Subtopic subtopic = subtopicRepository.findById(subtopicId)
            .orElseThrow(() -> new EntityNotFoundException("Subtopic", subtopicId));
    return generateAndPersist(subtopic);   // private helper holding the existing logic
}
```

`generateSingleSnack(Subtopic)` and `generateSingleSnack(Subtopic, Consumer)` are retained (the latter delegates) for the single-shot path (FR-017), and all three funnel into one private `generateAndPersist(Subtopic)`. `BatchService.runBatch` changes from `snackService.generateSingleSnack(item.getSubtopic())` to:

```java
UUID subtopicId = item.getSubtopic().getId();          // id access on a lazy proxy is safe; no init
Snack snack = snackService.generateSingleSnack(subtopicId);
```

**Rationale**: The theme is fetched lazily *within* the new transaction, so `subtopic.getTheme().getPrompt()` always has an open session (FR-001). The detached `Subtopic` never crosses the transactional boundary (FR-002), so no lazy failure can be logged (FR-003). Keeping the entity overload means the single-shot path is byte-for-byte compatible (FR-017). Funnelling into a private helper avoids the `@Transactional` self-invocation trap and keeps one copy of the generation logic.

**Rejected**: (a) making `runBatch` itself `@Transactional` — it would hold a DB connection for the entire (potentially minutes-long) LLM sequence; (b) eagerly fetching `subtopic.theme` via `EntityGraph` on the item query — it would load theme data for the whole batch up front and still leaves the entity detached at use time; (c) passing the id but re-fetching inside `BatchService` — same as (a) without a transaction, so the fetch would close before the LLM call.

**Hardening (optional)**: annotate `JpaStudySessionItemRepository.findBySessionId` with `@EntityGraph(attributePaths = {"subtopic"})` so `item.getSubtopic()` is initialized before detach and `getId()` is trivially safe. Not required (Hibernate returns the identifier from an uninitialized proxy without initializing), but it makes the "resolve before detach" half of FR-001 explicit. Included as a recommended, one-line change.

### 2. Separate `quizExecutor` for nested quiz prefetch; `batchExecutor` reserved for top-level workers (FR-004/FR-005/FR-006)

Add to `LlmConfig`:

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

`BatchService` injects it with `@Qualifier("quizExecutor")` and uses it for `CompletableFuture.runAsync(..., quizExecutor)`. `batchExecutor` is unchanged (`batch-worker-`, core=2/max=8/queue=50) and still runs only the top-level `runBatch` worker.

**Rationale**: The deadlock is a *cyclic wait* — the batch worker waits on tasks that need the same pool it occupies. Putting the children on a distinct pool removes the cycle by construction, regardless of pool sizing. Sizing rationale:
- `batchExecutor` stays small (top-level batches are I/O-bound and long-running); **no** `CallerRunsPolicy` here, because it is invoked from `createBatch`'s `@Transactional` method and a caller-runs fallback would execute the whole batch on the request thread inside the create transaction.
- `quizExecutor` is sized for the nested fan-out: `core=4` handles several concurrent batches, `max=16` bounds provider concurrency, `queue=100` absorbs bursts. `CallerRunsPolicy` is the **liveness safety valve**: if the quiz pool *and* queue saturate, the submitting (batch-worker) thread executes the quiz itself, so `allOf().join()` can always complete and the batch always reaches a terminal status (FR-005). Distinct thread-name prefixes (`batch-worker-` / `quiz-worker-`) make the two pools observable in logs/thread dumps.

**Bounded join (recommended)**: wrap the join so a pathologically hung quiz task cannot hold the worker forever:

```java
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .orTimeout(quizJoinTimeoutSeconds, TimeUnit.SECONDS)
        .exceptionally(ex -> { log.warn("Quiz prefetch join timed out; aggregating with current item states"); return null; });
```

with `@Value("${batch.quiz-join-timeout-seconds:240}")`. The worker then proceeds to `aggregateStatus`, guaranteeing a terminal status even under a stuck child (FR-006 / SC-002). The overall terminal window remains naturally bounded by per-call LLM timeouts × bounded retries × subtopic count; the value is configurable (see Risks).

**Rejected**: (a) raising `batchExecutor` core/max so the pool can grow — it does not remove the cycle, only widens it, and `max` is only reached when the queue is full; (b) switching to a common `ForkJoinPool` — the children would still contend with the worker and the pool is shared process-wide; (c) executing quizzes sequentially — a needless behavior/perf regression to the prefetch design.

### 3. Explicit `polling` state; empty state only for a terminal, empty batch (FR-009/FR-010/FR-011/FR-012/FR-013/FR-014)

In `SnacksComponent`:

- Add `readonly polling = signal(false);`.
- On `createBatch` success: `this.creating.set(false); this.polling.set(true);` before starting the poll.
- In `applyStatus`, when the status is terminal (`status.status !== 'IN_PROGRESS'`) clear `polling`. Since `002`'s `pollStatus` completes on the terminal emission, this is the last `next`.
- In `handlePollError` (including `BatchPollingTimeoutError`) clear `polling` alongside `creating`/`retrying`.
- When a retry cycle starts (`retryTarget` success), set `polling.set(true)` and clear it on that cycle's terminal/error so the invariant holds for every generation cycle.
- Expose a computed `showEmptyState = !creating() && !polling() && items().length === 0 && batchStatus() !== 'IN_PROGRESS' && !errorMessage()`.

In `snacks.component.html`:
- Add a progress block for `!creating() && polling() && items().length === 0` (spinner + "Preparing your snack…").
- Change the empty-state condition from `!creating() && items().length === 0 && !errorMessage()` to `showEmptyState()`.

**Rationale**: The flash window is exactly "create succeeded, first poll has not returned". A dedicated `polling` flag keeps the loading state visible across that window (FR-009/FR-010) and, because it is cleared only on terminal/error, the empty state cannot render while a batch is non-terminal (FR-011). Gating additionally on `batchStatus() !== 'IN_PROGRESS'` is defense-in-depth for the retry path. The terminal check itself is already negative (FR-012, from `002`) and polling already completes on any terminal status (FR-013); with the backend no longer deadlocking, the retry indicator settles (FR-014). `batch.service.ts` and `snack-retry.service.ts` are **not** modified.

**Rejected**: (a) keeping `creating=true` until the first poll — it conflates two phases and delays the create-vs-poll distinction; (b) rendering the empty state behind a `setTimeout` — timing-based and racy; (c) an allow-list terminal check — already fixed in `002` and explicitly forbidden by FR-012.

### 4. Frozen surfaces (FR-016/FR-017/FR-018/FR-019)

No changes to `POST /api/snacks/generate`, `POST /api/batches`, the DTO shapes, `RetryingLlmProvider`, the LLM port/prompts, `QuizService`, `QuizQualityGate`, `SnackRetryService`, `batch.service.ts`, or `BatchStatus`/`GenerationStatus`. The bounded-retry behavior from `002` (≤3 cycles per target, single-target retry batches, in-flight guard) is untouched. Failure messages continue to use the existing typed `LlmErrorCause` vocabulary; no prompt/provider payload is logged. The existing generic `log.warn` calls log a subtopic id and the exception; the exception in this path is a Hibernate lazy-init error (entity name only) or a generic service error — no credentials, keys, raw prompts, or provider payloads (FR-019). This is verified by inspection in Phase 4.

## Phase-by-Phase Implementation Plan

### Phase 0 — Persistence scope: generate by id inside the transaction (US1, P1)

| Step | File | Why |
|------|------|-----|
| 0.1 | `backend/.../application/service/SnackService.java` | Extract private `generateAndPersist(Subtopic)`; add `@Transactional generateSingleSnack(UUID)` that loads the subtopic and delegates; keep the existing overloads (FR-001/FR-002/FR-017). |
| 0.2 | `backend/.../application/service/BatchService.java` | In `runBatch`, resolve `item.getSubtopic().getId()` and call `snackService.generateSingleSnack(subtopicId)` instead of passing the detached `Subtopic` (FR-002/FR-003). |
| 0.3 | `backend/.../persistence/JpaStudySessionItemRepository.java` | Recommended hardening: `@EntityGraph(attributePaths = {"subtopic"})` on `findBySessionId` so the subtopic is initialized before detach. |

### Phase 1 — Executor separation and liveness (US2, P1)

| Step | File | Why |
|------|------|-----|
| 1.1 | `backend/.../infrastructure/llm/LlmConfig.java` | Add the separate `quizExecutor` bean (`quiz-worker-`, core=4/max=16/queue=100, `CallerRunsPolicy`); leave `batchExecutor` unchanged (FR-004). |
| 1.2 | `backend/.../application/service/BatchService.java` | Inject `@Qualifier("quizExecutor")`; submit nested quiz tasks to it; add the bounded `orTimeout` on the join so aggregation always runs (FR-005/FR-006). |

### Phase 2 — Frontend progress state and empty-state gating (US3, P1)

| Step | File | Why |
|------|------|-----|
| 2.1 | `frontend/src/app/pages/snacks/snacks.component.ts` | Add `polling` signal; set on create/retry success; clear on terminal poll and on poll error; add `showEmptyState` computed (FR-009/FR-010/FR-011/FR-014). |
| 2.2 | `frontend/src/app/pages/snacks/snacks.component.html` | Add the in-flight progress block; switch the empty-state condition to `showEmptyState()` (FR-009/FR-011). |

### Phase 3 — Tests (all stories)

| Step | File | Why |
|------|------|-----|
| 3.1 | `backend/.../service/SnackServiceTest.java` | Add `generateSingleSnack(UUID)` test: repositories resolve subtopic/theme, LLM called with the theme prompt, snack persisted (would fail if the by-id path skipped the fetch). |
| 3.2 | `backend/.../service/BatchServiceTest.java` | Add the `quizExecutor` constructor arg; stub the by-id overload; assert `runBatch` calls `generateSingleSnack(UUID)` and **never** `generateSingleSnack(Subtopic)` (regression guard for FR-002). |
| 3.3 | `backend/.../integration/BatchGenerationIntegrationTest.java` (NEW) | Real Spring context + Testcontainers + stub `LlmProvider`; run concurrent batches on the real executors and assert snacks become `READY` and the aggregate reaches a terminal status within a bounded time — the test that fails pre-fix with `LazyInitializationException` and/or a hang (FR-001/FR-005/FR-007). |
| 3.4 | `frontend/e2e/batch-empty-state.spec.ts` (NEW) | Delay the first poll; assert the empty state is never shown while `IN_PROGRESS` and that it appears only for a terminal, zero-ready batch (FR-009/FR-010/FR-011). |
| 3.5 | Existing suites | `mvn test` + `npx playwright test` stay green, including `polling-termination.spec.ts`, `retry-bound.spec.ts`, `progressive-delivery.spec.ts`, `loading-states.spec.ts` (FR-016/FR-017/FR-018 / SC-007). |

### Phase 4 — Contracts, docs, verification

| Step | File | Why |
|------|------|-----|
| 4.1 | `contracts/batch-status.md` | Restate the unchanged shape and the `002` terminality/`retrying`/window semantics; note the server-side generation fix makes terminal statuses reachable. |
| 4.2 | `contracts/batch-generation-behavior.md` (NEW) | Backend behavioral contract: persistence scope, executor separation, terminality/bounded-time guarantee, no sensitive logging. |
| 4.3 | `quickstart.md` | Manual create-theme → select-subtopics → generate → expect `READY`, no hang; how to observe thread pools and the empty-state behavior. |
| 4.4 | FR-019 inspection | Confirm failure logs/messages contain only typed causes and ids. |

## Milestones

- **M0** (P1, backend): by-id transactional generation + `runBatch` call-site change (Phase 0). Delivers US1 / FR-001..003.
- **M1** (P1, backend): separate `quizExecutor` + bounded join (Phase 1). Delivers US2 / FR-004..006.
- **M2** (P1, frontend): `polling` state + empty-state gating (Phase 2). Delivers US3 / FR-009..014.
- **M3** (P1, tests): unit + integration + E2E coverage for the three root causes (Phase 3). Delivers SC-001..005.
- **M4** (P2, docs/verification): contracts, quickstart, no-regression + sensitive-log inspection (Phase 4). Delivers SC-006..008.

## Risks

- **The self-deadlock requires concurrency to manifest.** With `batchExecutor` `core=2`, a single batch worker blocks on `join()` while the second core thread drains the queued quiz tasks, so one batch can complete. The cyclic wait deadlocks when all core threads are held by batch workers (i.e. ≥2 concurrent batches) or when the pool is otherwise saturated. The separate pool removes the cycle unconditionally; the integration test must therefore run **concurrent** batches (or otherwise saturate the core pool) to be a meaningful pre-fix/fail-post-fix guard. **Flagged for the orchestrator.**
- **Terminal window default (5 min) vs sequential snacks.** Each snack is generated sequentially and may consume the full LLM timeout × bounded retries; for large batches the natural terminal time can exceed the spec's illustrative 5 min default. The fix guarantees *termination* (no unbounded wait) but not a hard 5-minute wall. Recommendation: keep the value configurable and treat SC-002's 5 min as a target, not a hard SLA; optionally enforce it with the `quizExecutor` join timeout plus a future batch-level watchdog. **Flagged for the orchestrator.**
- **`@Async` self-invocation in tests.** `BatchService.createBatch` calls `self.runBatch`, so integration tests must obtain the proxied `BatchService` bean and poll `getStatus` to observe terminality; calling `runBatch` reflectively or on a raw instance would bypass `@Async`/transaction proxies and give a false result.
- **`@MockBean LlmProvider` context reload.** The integration test replaces the `@Primary RetryingLlmProvider` with a stub; this reloads the context. Keep it in its own test class so the existing `BatchFlowIntegrationTest` context is unaffected.
- **Existing E2E timing.** `setupBatchMock` returns all three subtopics for any `GET`; the new empty-state spec must use `setupConfigurableBatchMock` and a delayed first response to reliably observe the pre-first-poll window. Verify `loading-states.spec.ts` and `progressive-delivery.spec.ts` still pass.
- **`item.getSubtopic().getId()` on a detached proxy.** Hibernate returns the identifier from an uninitialized proxy without initializing, so this is safe; the recommended `@EntityGraph` hardening removes even that reliance. If a future mapping changes the id access strategy, the EntityGraph keeps the code correct.

## Mapping: Spec Requirements → Plan Items

| Spec ID | Requirement | Plan Item |
|---------|-------------|-----------|
| FR-001 | Generate using subtopic+theme in a valid persistence context | `SnackService.generateSingleSnack(UUID)` (0.1) |
| FR-002 | Do not pass detached/lazy data into a post-close step | `runBatch` by-id call (0.2); EntityGraph (0.3) |
| FR-003 | No detached/lazy failure logged from batch snack path | By-id path (0.1, 0.2) |
| FR-004 | Nested quiz work cannot starve the batch worker | `quizExecutor` (1.1, 1.2) |
| FR-005 | Worker always reaches terminal; never blocks indefinitely | Separate pool + `CallerRunsPolicy` + bounded join (1.1, 1.2) |
| FR-006 | Terminal within bounded time | Bounded join `orTimeout` + bounded LLM calls (1.2) |
| FR-007 | Successful model → persisted READY snack returned | By-id transactional generation (0.1, 0.2) |
| FR-008 | One item failure does not block others | Existing per-item try/catch preserved (0.2) |
| FR-009 | Progress state while creating/in-progress with empty list | `polling` + template progress block (2.1, 2.2) |
| FR-010 | No empty state before the first poll response | `polling` set on create success (2.1) |
| FR-011 | Empty state only for terminal, zero-ready batch | `showEmptyState` gate (2.1, 2.2) |
| FR-012 | Terminal = any non-IN_PROGRESS | Already in `batch.service.ts` (002); unchanged, verified |
| FR-013 | Polling stops within one interval of terminal | Already in `pollStatus` (002); backend now terminates |
| FR-014 | Retry UI terminates; no infinite retrying state | `polling` cleared on terminal/error; `retrying` derived from active work (2.1) |
| FR-015 | Bounded polling window (default 120 s) | Already in `batch.service.ts` (002); unchanged, verified |
| FR-016 | Preserve ≤3 automatic attempts | `SnackRetryService` untouched; regression suite (3.5) |
| FR-017 | Preserve single-shot snack contract | Keep `generateSingleSnack(Subtopic)` overload (0.1) |
| FR-018 | Preserve quiz generation contract | `QuizService` untouched; only re-homed executor (1.1, 1.2) |
| FR-019 | No sensitive data in messages/logs | Existing typed causes; Phase 4 inspection (4.4) |
| FR-020 | Respect layered architecture | Application scope (0.x), infrastructure executors (1.x), interface state (2.x) |
| SC-001 | No lazy failure in 100% of runs | Integration test (3.3) |
| SC-002 | 100% terminal within bounded time | Bounded join + integration test (1.2, 3.3) |
| SC-003 | READY snack returned on success | Integration test (3.3) |
| SC-004 | Empty state never during run/before first poll | E2E spec (3.4) |
| SC-005 | Polling stops within one interval; no infinite retry | E2E (3.4) + existing `polling-termination.spec.ts` (3.5) |
| SC-006 | ≤3 attempts preserved | Existing `retry-bound.spec.ts` (3.5) |
| SC-007 | No regression in single-shot/quiz flows | Existing backend + E2E suites (3.5) |
| SC-008 | No sensitive data in failure output | Phase 4 inspection (4.4) |

## Testing Strategy

### Backend unit tests
- `SnackServiceTest`: `generateSingleSnack(UUID)` resolves the subtopic via the repository, uses the theme prompt, persists the snack, and throws `EntityNotFoundException` for an unknown id. This would fail if the by-id path did not actually load the related data.
- `BatchServiceTest`: constructor gains the `quizExecutor`; `runBatch` is verified to call `generateSingleSnack(UUID)` and **never** the `Subtopic` overload; the existing partial-failure/aggregation and `retrying` assertions remain.

### Backend integration tests (`BatchGenerationIntegrationTest`, `@Tag("integration")`)
- Real Spring context + Testcontainers MySQL + a stub `LlmProvider` (valid snack/quiz).
- Start **multiple concurrent** batches via the proxied `BatchService.createBatch`, each with several subtopics, using the **real** `batchExecutor` and `quizExecutor`.
- Poll `getStatus` and assert: every item reaches `READY`; the aggregate reaches `COMPLETE` within a bounded timeout; no `LazyInitializationException` is recorded (`snackCause == null`).
- This is the regression test for both the detached-proxy failure and the self-deadlock (it hangs/fails pre-fix, passes post-fix).

### Frontend E2E (Playwright, `frontend/e2e/`)
- **No empty-state flash**: with a delayed first `GET /api/batches/{id}`, assert `.empty-state` is hidden and a progress indicator is visible until the first items arrive.
- **Empty state only when terminal and empty**: mock a terminal `FAILED` batch with zero ready snacks; assert `.empty-state` becomes visible and polling has stopped.
- **No empty state while `IN_PROGRESS` with empty items**: mock `IN_PROGRESS` with an empty `items` array; assert `.empty-state` is not shown.
- **Regression**: `polling-termination.spec.ts`, `retry-bound.spec.ts`, `progressive-delivery.spec.ts`, `loading-states.spec.ts`, and the rest of the suite stay green.

### Manual verification
See `quickstart.md`: create a theme, select subtopics, generate snacks, and confirm `READY` snacks appear with no hang and no empty-state flash; observe `batch-worker-`/`quiz-worker-` thread names; force an LLM failure to confirm typed causes with no sensitive payloads.

### What is explicitly not tested / not changed
- No DB migration tests (no schema change). No new endpoint/DTO tests. No changes to `RetryingLlmProviderTest`, `QuizServiceTest`, or `QuizQualityGateTest`. No frontend unit runner is introduced.
