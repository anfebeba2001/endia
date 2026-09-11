# Implementation Plan: Bounded Snack Reattempts and Polling Termination

**Branch**: `002-snack-retry-bound` | **Date**: 2026-09-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-snack-retry-bound/spec.md`

## Summary

Bug-fix feature over the existing async snack batch flow. Two independent defects cause the reported "infinite retrying loop":

1. **Polling never terminates on `PARTIAL`** — `BatchService.pollStatus` (frontend) uses an allow-list terminal check (`COMPLETE`/`FAILED`) so a batch that settles as `PARTIAL` is polled forever, and there is no upper bound on the polling lifetime.
2. **Unbounded, blind, whole-batch reattempts** — `SnacksComponent.retry()` calls `resetFailed()` which blindly re-creates the batch for *all* subtopics, ignores the failed subtopic id, has no attempt counter, and has no in-flight guard.

The fix is intentionally small and frontend-heavy:

- **Terminality (US2 / FR-008..010)**: define terminal **negatively** — every status other than `IN_PROGRESS` is terminal — and give `pollStatus` a bounded maximum lifetime (configurable, default **120 s**) that surfaces a terminal, user-visible error when it elapses.
- **Bounded app-level reattempts (US1 / FR-001..005)**: a per-generation-target attempt budget of **3 cycles** (initial + at most 2 automatic reattempts), held in a small frontend service; when the 3rd cycle fails the target enters a terminal error state and progress stops.
- **Explicit-error precondition (US3 / FR-003..004, FR-007)**: an automatic reattempt is issued only when an explicit failure is observed (item `FAILED` / typed cause / failed batch-create request), never on a timer and never while `IN_PROGRESS`; an in-flight guard guarantees at most one cycle per target.
- **Manual retry (US4 / FR-006)**: the existing retry affordance resets that target's budget to a fresh 3-attempt cycle and starts exactly one new cycle.
- **Backend `retrying` correctness (FR-011)**: `BatchService.getStatus` currently sets `retrying = (status == IN_PROGRESS)`, i.e. true for the whole batch lifetime. Change it to reflect *active* item generation (any item in `GENERATING`), which is the only observable proxy the backend has for active work/retries.

`POST /api/snacks/generate`, the `RetryingLlmProvider` retry algorithm, the `BatchStatus` vocabulary, the LLM prompts, and the DB schema are **unchanged**. `snack-generate.md` is retained as an unchanged reference contract.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.x (frontend), Angular 21

**Primary Dependencies**:
- Backend: Spring Boot 3.4.x, Spring MVC, Spring Data JPA/Hibernate 6.x. No new dependencies. The `@Async` `batchExecutor` and `RetryingLlmProvider` decorator from `001-batch-mvp-enhancements` are reused as-is.
- Frontend: Angular 21 standalone + signals, `HttpClient`, RxJS (`interval`, `timer`, `switchMap`, `takeWhile`, `race`, `finalize`). No new dependencies.

**Storage**: MySQL 8.x — **no schema change**. The attempt budget is frontend in-memory state (per session/cycle); the `StudySession` / `StudySessionItem` / `GenerationStatus` / `BatchStatus` model is unchanged. No migration.

**Testing**: JUnit 5 + Mockito (backend unit), Spring Boot Test + Testcontainers MySQL (backend integration), Angular Playwright (`frontend/e2e/`). No Karma/Jest unit specs exist under `frontend/src/app/**` today; the frontend behavior is verified via Playwright plus the pure-logic service being written to be trivially unit-testable if a runner is added.

**Target Platform**: Linux server (backend), modern browsers Chrome/Firefox/Safari latest 2 versions (frontend).

**Project Type**: Web application (monorepo: `backend/` + `frontend/`).

**Performance Goals**: Polling interval stays ~1.5 s. A failing target settles into a terminal error within 3 cycles and the poll observable unsubscribes within one poll interval of the terminal status. A stuck `IN_PROGRESS` batch terminates within the configured window (default 120 s).

**Constraints**: No auth. No persistence. No snack-page UI redesign (reuse the existing card / error banner / retry affordance). Must not change `POST /api/snacks/generate`. Must not change `RetryingLlmProvider` semantics. Must keep the existing Playwright suite (17 specs) green and `RetryingLlmProviderTest` / `BatchServiceTest` passing (with the one additive `retrying` assertion).

**Scale/Scope**: Single-user MVP. 3 modified frontend files + 1 new frontend service + 1 new injection token + 1 modified backend service. No DB, no controller, no port, no prompt changes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Evidence |
|---|-----------|--------|----------|
| I | **Spec-Driven Development** | PASS | spec.md defines 4 user stories, 16 FRs, 8 SCs. Every FR traces to a concrete file/item (see Mapping). |
| II | **Clean Architecture (Layered)** | PASS | The only backend change is in the **application** layer (`BatchService.getStatus`); domain, infrastructure, interfaces, and persistence are untouched. Frontend follows Angular standalone + signals; the new `SnackRetryService` is a framework-idiomatic, dependency-injected service. |
| III | **LLM Abstraction First** | PASS | No `LlmProvider` / prompt / provider change. `RetryingLlmProvider` is preserved verbatim (FR-013). |
| IV | **Quality Gate for Generated Content** | PASS | `QuizQualityGate` untouched; this feature does not alter generation or validation. |
| V | **MVP-First, Iterate** | PASS | Smallest correct change set: no persistence, no new statuses, no UI redesign, no new dependencies, no new endpoint. Manual retry reuses the existing affordance. |

**All gates PASS. No violations to justify.**

## Complexity Tracking

> No constitution violations. No complexity justifications needed.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | N/A | N/A |

## Project Structure

### Documentation (this feature)

```text
specs/002-snack-retry-bound/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Phase 0 — decisions + rationale
├── data-model.md        # Phase 1 — conceptual model (no schema change)
├── quickstart.md        # Phase 1 — local run + failure-injection verification
├── contracts/           # Phase 1 — API + behavioral contracts
│   ├── batch-status.md          # UPDATED: terminality, retrying semantics, polling window
│   ├── snack-generate.md        # UNCHANGED reference (single-shot endpoint)
│   └── snack-retry-behavior.md  # NEW: frontend retry/polling behavioral contract
└── tasks.md             # Phase 2 — NOT created by planner
```

### Source Code (repository root)

Only the marked files change. The rest of the batch flow is untouched.

```text
endia/
├── backend/
│   └── src/
│       ├── main/java/com/endia/
│       │   ├── domain/…                         # unchanged (no entity/enum/schema change)
│       │   ├── application/
│       │   │   └── service/BatchService.java    # MOD: getStatus() retrying = any item GENERATING
│       │   ├── infrastructure/…                 # unchanged (RetryingLlmProvider, JPA, LLM)
│       │   └── interfaces/rest/…                # unchanged (BatchController, DTOs)
│       └── test/java/com/endia/application/service/BatchServiceTest.java  # MOD/ADD: retrying assertions
│
└── frontend/src/app/
    ├── app.config.ts                            # MOD: provide MAX_POLLING_WINDOW_MS (default 120000)
    ├── models/batch.model.ts                    # unchanged shape (BatchStatus already includes PARTIAL)
    ├── services/
    │   ├── batch.service.ts                     # MOD: terminal = status !== IN_PROGRESS; bounded lifetime
    │   └── snack-retry.service.ts               # NEW: per-target budget (max 3) + in-flight guard
    └── pages/snacks/
        ├── snacks.component.ts                  # MOD: explicit-error auto-retry, per-target merge, manual reset
        └── snacks.component.html                # MOD (minimal): terminal-error copy + disabled retry while in-flight
```

**Structure Decision**: Monorepo as-is. Backend keeps clean architecture — the single change lives in the application service, because `retrying` is a derived presentation of application-layer status and needs no domain/persistence support. Frontend keeps standalone components + signals; the retry budget is extracted into a small root-provided service so the counter and the in-flight guard are single-sourced, testable, and not duplicated across the poll handler, the automatic retry path, and the manual retry button.

## Architecture Decisions

### 1. Terminality is defined negatively, in one place (FR-008/FR-009)

`BatchService.isTerminal(status)` becomes `status.status !== 'IN_PROGRESS'`. This removes the `COMPLETE`/`FAILED` allow-list that made `PARTIAL` non-terminal and caused the infinite poll. `takeWhile((s) => !isTerminal(s), true)` already emits the terminal value and then completes, so the poll observable now unsubscribes on `PARTIAL`/`COMPLETE`/`FAILED`. No new statuses are introduced; the existing `BatchStatus` union already contains `PARTIAL`.

### 2. Bounded polling lifetime via `race(polls$, deadline$)` (FR-010 / SC-004)

`pollStatus(batchId, intervalMs = 1500, maxWindowMs = injected)` builds the poll stream as today and races it against `timer(maxWindowMs)` that maps to `throwError(() => new BatchPollingTimeoutError(maxWindowMs))`. `race` unsubscribes the loser, so on timeout the in-flight HTTP request is cancelled and the component's `error` handler surfaces a terminal, user-visible message and stops the spinner. This gives a true **total-lifetime** bound (the RxJS `timeout` operator resets per emission and cannot express a total window). The window is configurable through an Angular `InjectionToken` (`MAX_POLLING_WINDOW_MS`, `providedIn: 'root'`, default `120_000`) so tests can override it and deployments can tune it without touching code. Rejected: `takeUntil(timer(...))` (completes silently with no error to show the user); `timeout()` (per-emission, not total).

### 3. App-level attempt budget lives in a dedicated frontend service, scoped per generation target (FR-001/FR-002/FR-006/FR-007)

A new root-provided `SnackRetryService` owns three pieces of state keyed by `subtopicId`:
- `attempts: Map<target, number>` — number of user-visible generation cycles started for the target (1–3).
- `inFlight: Set<target>` — targets with a cycle currently in flight (the duplicate guard).
- `exhausted`/`terminalError` derived from `attempts >= 3`.

API: `registerAttempt(target)`, `attemptsFor(target)`, `canRetry(target)` (`!inFlight && attempts < 3`), `markInFlight(target)`, `clearInFlight(target)`, `isInFlight(target)`, `hasInFlight()`, `resetBudget(target)`, `resetAll()`. The initial cycle registers attempt 1 for every selected target; each observed explicit failure that is retryable registers the next attempt and starts a new cycle; a manual retry calls `resetBudget(target)` then registers attempt 1 of the fresh cycle. Rejected: component-local fields — the counter/guard are consulted from the poll handler, the automatic retry path, and the manual button, and a service avoids three copies and survives re-render.

### 4. Re-initiation is per-target and only on an explicit error (FR-003/FR-004)

The frontend observes a failure only from poll data (`snackStatus === 'FAILED'` / non-null `snackCause`) or from a failed `createBatch` request. While a target is `PENDING`/`GENERATING` nothing is triggered; no timer ever triggers generation. A failed, non-exhausted, non-in-flight target is retried by issuing `createBatch(themeId, [targetId])` — a **single-target** batch, fixing the current "re-create the whole batch, ignore the subtopic id" defect. The returned item is merged back into the component's item list by `subtopicId`, so healthy targets are never regenerated and the one-at-a-time UI is preserved. An `ownerBatchId` map ensures a stale poll of the original batch cannot re-trigger a target that has already been handed to a retry batch.

### 5. `retrying` is corrected to reflect active work, not batch lifetime (FR-011)

`BatchService.getStatus` changes `boolean retrying = session.getStatus() == BatchStatus.IN_PROGRESS` to `retrying = items.stream().anyMatch(i -> i.getSnackStatus() == GENERATING || i.getQuizStatus() == GENERATING)`. The backend cannot distinguish an LLM-level retry from first-pass generation (that detail is inside `RetryingLlmProvider`), so the only truthful, observable proxy is "an item is actively generating right now". This is strictly narrower than the whole `IN_PROGRESS` lifetime (a just-created batch with all `PENDING` items and a finished batch both report `false`), which is exactly the defect FR-011 targets. The frontend additionally ORs this with its own retry-in-flight state (`retrying = status.retrying || retryService.hasInFlight()`) so the indicator also covers frontend-driven re-initiation cycles. This is backward compatible with the existing `BatchServiceTest.getStatusReturnsSnackContentAndCause` (item quiz status is `GENERATING` → still `true`) and with the Playwright mocks (they set `retrying` explicitly).

### 6. `POST /api/snacks/generate` and `RetryingLlmProvider` are frozen (FR-012/FR-013/FR-014)

No changes to the single-shot endpoint, the LLM port, the prompt templates, or the retry decorator. The application-level budget counts only frontend re-initiation **cycles**; the decorator's internal retryable-only, 3-attempt, backoff+jitter semantics are preserved, so the worst case remains 3 cycles × 3 internal tries with only 3 attempts user-visible, exactly as the spec resolves.

## Phase-by-Phase Implementation Plan

### Phase 0 — Polling termination and bounded window (US2, P1)

| Step | File | Why |
|------|------|-----|
| 0.1 | `frontend/src/app/services/batch.service.ts` | `isTerminal` → `status !== 'IN_PROGRESS'` (FR-008/009). |
| 0.2 | `frontend/src/app/services/batch.service.ts` | Add `BatchPollingTimeoutError`; bound `pollStatus` with `race(polls$, timer(maxWindowMs) → throw)` (FR-010). |
| 0.3 | `frontend/src/app/services/batch.service.ts` + `app.config.ts` | Add `MAX_POLLING_WINDOW_MS` injection token, default `120_000`; optional override in `app.config.ts`. |
| 0.4 | `frontend/src/app/pages/snacks/snacks.component.ts` | Handle `BatchPollingTimeoutError` in the poll error path: terminal message, stop spinner/creating, set `batchStatus` terminal (FR-010/SC-004). |

### Phase 1 — Bounded, explicit-error reattempts (US1 + US3, P1/P2)

| Step | File | Why |
|------|------|-----|
| 1.1 | `frontend/src/app/services/snack-retry.service.ts` (NEW) | Per-target attempt budget (max 3) + in-flight guard + reset (FR-001/002/006/007). |
| 1.2 | `frontend/src/app/pages/snacks/snacks.component.ts` | Register the initial cycle (attempt 1) per target on `init()`; reset budget for a fresh batch. |
| 1.3 | `frontend/src/app/pages/snacks/snacks.component.ts` | On poll, scan items for explicit `FAILED`; auto-retry each failed, non-exhausted, non-in-flight target via a single-target batch; merge item by `subtopicId` (FR-003/004, edge cases). |
| 1.4 | `frontend/src/app/pages/snacks/snacks.component.ts` | `ownerBatchId` guard so stale original-batch polls cannot double-trigger a handed-off target (FR-007). |
| 1.5 | `frontend/src/app/pages/snacks/snacks.component.ts` | On create-batch failure, treat as an explicit error, consume one attempt, and respect the bound (FR-003 scenario 3). |
| 1.6 | `frontend/src/app/pages/snacks/snacks.component.ts` + `.html` | On exhaustion, set a terminal per-target error naming the cause and stop the spinner; retry button disabled while in-flight (FR-005, FR-007). |

### Phase 2 — Manual retry resets the budget (US4, P2)

| Step | File | Why |
|------|------|-----|
| 2.1 | `frontend/src/app/pages/snacks/snacks.component.ts` | `retry(subtopicId)` uses the passed id: reset that target's budget, clear its terminal error, start exactly one new cycle; guarded against rapid double-invocation (FR-006, US4.2/US4.3). |
| 2.2 | `frontend/src/app/pages/snacks/snacks.component.html` | Reuse the existing "Retry" link; ensure it is present after exhaustion and disabled while a cycle is in flight (US4.1). No layout redesign. |

### Phase 3 — Backend `retrying` correctness (FR-011)

| Step | File | Why |
|------|------|-----|
| 3.1 | `backend/src/main/java/com/endia/application/service/BatchService.java` | `getStatus`: `retrying` = any item `GENERATING` (snack or quiz) instead of `session.status == IN_PROGRESS`. |
| 3.2 | `backend/src/test/java/com/endia/application/service/BatchServiceTest.java` | Keep the existing `GENERATING → true` assertion; add `PENDING → false` and terminal → `false` cases. |

### Phase 4 — Tests, contracts, and docs

| Step | File | Why |
|------|------|-----|
| 4.1 | `frontend/e2e/` | Add a spec: `PARTIAL` terminates polling; stuck `IN_PROGRESS` terminates within a short configured window; failing target is attempted exactly 3 times then shows a terminal error; no retry while `IN_PROGRESS`; manual retry resets the budget; duplicate rapid retry starts one cycle. |
| 4.2 | `contracts/batch-status.md` | Update terminality (`IN_PROGRESS` only non-terminal), `retrying` semantics, and polling-window behavior (FR-016). |
| 4.3 | `contracts/snack-generate.md` | Unchanged reference contract for `POST /api/snacks/generate` (FR-012). |
| 4.4 | `contracts/snack-retry-behavior.md` | New frontend behavioral contract (budget, precondition, guard, reset, window). |
| 4.5 | `quickstart.md` | Failure-injection recipes to observe the 3-attempt bound and polling termination. |
| 4.6 | Existing E2E suite | Verify the 17 existing Playwright specs remain green (SC-007). |

## Milestones

- **M0** (P1, frontend): polling terminality + bounded window (Phase 0). Independently removes the infinite spinner for `PARTIAL` and stuck batches (US2).
- **M1** (P1/P2, frontend): `SnackRetryService` + explicit-error per-target reattempts + in-flight guard + terminal error on exhaustion (Phase 1). Delivers US1 and US3.
- **M2** (P2, frontend): manual retry resets the budget and starts one cycle (Phase 2). Delivers US4.
- **M3** (P1, backend): `retrying` semantics corrected + unit assertions (Phase 3). Delivers FR-011.
- **M4** (P1, tests/docs): new E2E specs + contract/quickstart docs; full existing suite green (Phase 4). Delivers SC-007/SC-008.

## Risks

- **Backend cannot distinguish first-pass generation from an LLM-level retry.** `retrying` is therefore derived as "any item `GENERATING`", which is the narrowest truthful proxy available without adding retry telemetry. This satisfies "not the whole batch lifetime" but is broader than "reattempt only". Documented as an assumption in `research.md`; the frontend ORs its own re-initiation state to cover app-level cycles. **Flagged for orchestrator.**
- **Per-target retry batches create concurrent poll loops.** Up to N targets may each have a single-target retry batch in flight. Mitigation: each retry is gated by the in-flight guard, each poll terminates on terminal status, and the `ownerBatchId` map prevents stale-trigger duplicates. For the MVP scale (≤10 subtopics) this is bounded and acceptable. **Flagged as a complexity tradeoff.**
- **`resetFailed` semantics change.** The current manual retry re-creates the whole batch; the new behavior re-creates only the failed target and resets its budget. Existing E2E specs that exercise the retry button may need updated mocks/expectations (the `setupBatchMock` helper returns all subtopics for any `GET`). Check `progressive-delivery.spec.ts` and `error-recovery.spec.ts` during Phase 4. No UI markup is removed.
- **No frontend unit-test runner.** The repo has no `*.spec.ts` under `frontend/src/app`; the pure `SnackRetryService` logic is only covered by Playwright. If a Karma/Jest setup is later added, the service is deliberately dependency-free so it can be unit-tested directly. Not blocking.
- **Playwright timing.** The bounded-window test must inject a short `MAX_POLLING_WINDOW_MS` override; relying on the 120 s default would make the test slow. The token exists precisely for this.
- **`retrying` contract drift.** `001`'s `batch-status.md` documents `retrying` as "true while any item is being auto-retried". The updated `002` contract redefines it as active item generation/retry and must be the canonical reference going forward (FR-016).

## Mapping: Spec Requirements → Plan Items

| Spec ID | Requirement | Plan Item |
|---------|-------------|-----------|
| FR-001 | Per-target attempt counter | `SnackRetryService` (1.1) |
| FR-002 | Cap at 3 total, no 4th | `canRetry` / `registerAttempt` (1.1, 1.3) |
| FR-003 | Retry only on explicit failure | Poll-failure scan + create-failure handling (1.3, 1.5) |
| FR-004 | No retry while IN_PROGRESS / no blind timer | Poll-driven only (1.3); no timer source |
| FR-005 | Terminal error after 3rd failure | Exhaustion path + banner (1.6) |
| FR-006 | Manual retry = fresh 3-attempt cycle | `retry()` + `resetBudget` (2.1) |
| FR-007 | One in-flight cycle per target | `inFlight` guard + `ownerBatchId` (1.1, 1.4) |
| FR-008 | Terminal = any non-IN_PROGRESS | `isTerminal` fix (0.1) |
| FR-009 | Poll completes on terminal | `takeWhile` + negative predicate (0.1) |
| FR-010 | Configurable bounded window (120 s) | `race` + `MAX_POLLING_WINDOW_MS` (0.2, 0.3) |
| FR-011 | `retrying` = active reattempt activity | `BatchService.getStatus` fix + frontend OR (3.1, 0.4) |
| FR-012 | Preserve `POST /api/snacks/generate` | Frozen; `snack-generate.md` reference (4.3) |
| FR-013 | Preserve LLM retry semantics | `RetryingLlmProvider` untouched |
| FR-014 | No multiplicative/unbounded retries | Frontend cycles (max 3) distinct from internal 3 (1.1, 1.3) |
| FR-015 | No sensitive data in messages/logs | Reuse typed-cause messages; no payload logging added |
| FR-016 | Update batch-status contract | `contracts/batch-status.md` (4.2) |
| SC-001..008 | Measurable outcomes | M0–M4 + E2E specs (4.1, 4.6) |

## Testing Strategy

### Backend unit tests (`BatchServiceTest`)
- `retrying` is `true` when any item is `GENERATING` (existing assertion retained).
- `retrying` is `false` when all items are `PENDING` (batch just created) and when all items are terminal (`READY`/`FAILED`).
- Aggregation unchanged: `COMPLETE`/`PARTIAL`/`FAILED`/`IN_PROGRESS` behavior covered by the existing tests.

### Backend integration
- No new integration test required (no schema/endpoint change). Existing `BatchFlowIntegrationTest` must remain green.

### Frontend behavior (Playwright `frontend/e2e/`)
- **Terminal on `PARTIAL`**: mock `GET /api/batches/{id}` returning `PARTIAL`; assert no further polls (request count stops) and the page renders ready snacks plus the failed item.
- **Terminal on `COMPLETE`/`FAILED`**: assert the poll observable stops.
- **Stuck `IN_PROGRESS`**: override `MAX_POLLING_WINDOW_MS` to a short value; assert a terminal error appears and polling stops.
- **3-attempt bound**: mock `POST /api/batches` to count calls and always return a failed snack; assert exactly 3 cycles, no 4th, terminal error with cause, spinner stopped.
- **No blind-timer retry**: hold a batch `IN_PROGRESS` with no failed item; advance time; assert zero extra `POST /api/batches`.
- **Manual retry**: after exhaustion, click retry once; assert exactly one new cycle and progress shown; double-click → one cycle.
- **Regression**: existing 17 specs remain green.

### Manual verification
- Point `LLM_BASE_URL` at an unreachable host / a mock returning 503/429 to force failures; watch the network tab for the `POST /api/batches` count (≤3 per target) and confirm polling stops and the terminal message names the cause. See `quickstart.md`.

### What is explicitly not tested / not changed
- No DB migration tests (no schema change). No LLM retry algorithm tests beyond the existing `RetryingLlmProviderTest` (unchanged). No new backend endpoint tests.
