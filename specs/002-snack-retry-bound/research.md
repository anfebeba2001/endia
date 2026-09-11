# Research: Bounded Snack Reattempts and Polling Termination

**Phase 0 output** for `/specs/002-snack-retry-bound`. Each section records the Decision, Rationale, and Alternatives considered, grounded in the real code (`frontend/src/app/services/batch.service.ts`, `frontend/src/app/pages/snacks/snacks.component.ts`, `backend/.../BatchService.java`, `backend/.../RetryingLlmProvider.java`).

The spec is a bug-fix over the `001-batch-mvp-enhancements` batch flow. The governing constraint is the smallest correct change set: no DB change, no endpoint change, no LLM-algorithm change, no snack-page redesign.

---

## R1. How to bound app-level reattempts in the Angular snacks flow

**Decision**: Introduce a small root-provided `SnackRetryService` that owns a per-generation-target attempt budget of **3 user-visible cycles** plus an in-flight guard, and drive re-initiation from the poll handler only when an explicit failure is observed.

State, keyed by `subtopicId`:
- `attempts` (1..3): number of generation cycles started for the target.
- `inFlight`: targets with a cycle currently in flight.
- Derived: `canRetry(target) = !inFlight.has(target) && attempts.get(target) < 3`.

Operations:
- `resetAll()` — clear state for a brand-new initial batch.
- `registerAttempt(target)` — increment the cycle counter (initial cycle = attempt 1).
- `markInFlight(target)` / `clearInFlight(target)` — the duplicate guard.
- `isInFlight(target)`, `hasInFlight()`.
- `resetBudget(target)` — set attempts to 0 for a manual-retry cycle.

The component:
1. On `init()`, calls `resetAll()`, creates the initial batch for all selected subtopics, then `registerAttempt` for each target (attempt 1).
2. On every poll emission, scans `items` for `snackStatus === 'FAILED'`. For each failed target: if `canRetry`, `registerAttempt` + `markInFlight`, then issue `createBatch(themeId, [targetId])` (a **single-target** batch). If not retryable (exhausted), set a terminal per-target error.
3. Merges the retry batch's item back into the displayed list by `subtopicId`.

**Rationale**:
- FR-001/FR-002 require a per-target counter capped at 3 (initial + at most 2 reattempts). A service is the single source of truth consulted by the poll handler, the automatic retry path, and the manual retry button.
- FR-007 (one in-flight cycle per target) needs a guard that is checked before any re-initiation; a `Set` is the simplest correct structure.
- The existing `retry()` → `resetFailed()` blindly re-created the batch for *all* subtopics and ignored the `subtopicId` argument — the exact defect. Single-target batches fix it and prevent healthy snacks from being regenerated.
- `SnackRetryService` is dependency-free (no `HttpClient`), so it is unit-testable if a frontend unit runner is later added.

**Alternatives**:
- **Component-local fields**: rejected. The counter/guard are consulted from three places (poll, auto-retry, manual button); local fields invite drift and are lost on component re-creation.
- **Backend-owned counter**: rejected. The spec's resolved clarification says the application-level counter counts user-visible re-initiation *cycles*, and re-initiation is a frontend concern (a failed `POST /api/batches` is itself an explicit failure signal per FR-003). The backend has no notion of app-level cycles. Adding backend reattempt tracking would duplicate state and expand scope.
- **Whole-batch re-creation with only failed targets counted**: rejected. It regenerates healthy targets and violates the per-target scoping assumption; also it is what the defect already does.

---

## R2. How to terminate polling on PARTIAL / any non-IN_PROGRESS status

**Decision**: Change the terminal predicate to the negative form and keep the existing `takeWhile`:

```ts
private isTerminal(status: BatchStatusResponse): boolean {
  return status.status !== 'IN_PROGRESS';
}

return interval(intervalMs).pipe(
  switchMap(() => this.http.get<BatchStatusResponse>(`${this.baseUrl}/${batchId}`)),
  takeWhile((s) => !this.isTerminal(s), true)
);
```

`takeWhile(pred, true)` includes the terminal value in the output and then completes the observable, so subscribers receive the `PARTIAL`/`COMPLETE`/`FAILED` payload and the subscription unsubscribes — no further HTTP requests.

**Rationale**:
- FR-008 explicitly requires the negative definition ("every status other than `IN_PROGRESS` is terminal") and forbids an allow-list of only `COMPLETE`/`FAILED`.
- `BatchStatus` already contains `PARTIAL` (`models/batch.model.ts`), and the backend `aggregateStatus` legitimately returns `PARTIAL` for mixed success/failure, so the old predicate guaranteed an infinite poll on any partial batch.
- The negative form is future-proof: any new non-`IN_PROGRESS` status is automatically terminal.

**Alternatives**:
- **Extend the allow-list to `COMPLETE`/`FAILED`/`PARTIAL`**: rejected. It satisfies the immediate symptom but contradicts FR-008's stated rule and would silently break if a status is added.
- **`filter` + `take(1)`**: rejected. `takeWhile` is already present and minimal; changing the predicate is a one-line fix.

---

## R3. How to define `retrying` correctly

**Decision (backend)**: In `BatchService.getStatus`, derive `retrying` from active item state rather than the batch lifetime:

```java
boolean retrying = items.stream().anyMatch(i ->
    i.getSnackStatus() == GenerationStatus.GENERATING
    || i.getQuizStatus() == GenerationStatus.GENERATING);
```

**Decision (frontend)**: The component's `retrying` signal is the OR of the backend flag and the local retry-in-flight state:

```ts
this.retrying.set(status.retrying || this.retryService.hasInFlight());
```

**Rationale**:
- FR-011: the indicator must reflect **active** automatic reattempt activity, not the entire lifetime of an `IN_PROGRESS` batch. `session.status == IN_PROGRESS` stays true from batch creation until the worker's final aggregate write, which is precisely the defect.
- The backend cannot distinguish an LLM-level retry (inside `RetryingLlmProvider`, synchronous and invisible to the poll) from first-pass generation. The only truthful observable proxy is "at least one item is `GENERATING`". This is strictly narrower than the whole `IN_PROGRESS` lifetime: a freshly created batch with all `PENDING` items reports `false`, and a finished batch reports `false`.
- The frontend knows when it has issued an app-level re-initiation cycle; OR-ing `hasInFlight()` makes the indicator cover those cycles too, giving the closest achievable approximation of "active reattempt activity" without adding backend retry telemetry.
- Backward compatible: `BatchServiceTest.getStatusReturnsSnackContentAndCause` sets `quizStatus = GENERATING` and asserts `retrying()` true — still passes. The Playwright mocks set `retrying` explicitly, so E2E is unaffected.

**Alternatives**:
- **Keep `status == IN_PROGRESS`**: rejected. It is the documented defect (FR-011, spec Assumption).
- **Backend tracks retry attempts per item**: rejected as out of scope. It would require new domain state/persistence or provider-level telemetry, contradicting "smallest correct change" and the "no persistence" constraint.
- **Remove the field and let the frontend compute it entirely**: rejected. It breaks the documented contract shape and the existing E2E mocks that assert the spinner from the response flag.

**Residual assumption (flagged)**: "any item `GENERATING`" includes first-pass generation, so `retrying` is broader than "reattempt only". The backend has no lower-level signal to narrow it further without scope expansion. Documented in `plan.md` Risks and the updated contract.

---

## R4. Where the counter lives — in-memory component state vs service

**Decision**: A dedicated root-provided **`SnackRetryService`** (in-memory, not persisted).

**Rationale**:
- The counter is per-target and per-cycle, and is consulted from the poll handler, the automatic retry path, and the manual retry button. A service makes these three call sites share one source of truth.
- It is framework-idiomatic for this codebase: `AppStateService` already holds cross-cutting flow state as a root service with signals.
- It survives component re-rendering and is trivially testable in isolation (no `HttpClient` dependency).
- The spec explicitly forbids introducing persistence for attempt counters (Out of Scope: "Introducing persistence/schema changes for attempt counters unless an implementation decision later requires it"). In-memory service state satisfies the behavior with zero schema change.

**Alternatives**:
- **Component-local `Map`/signal**: viable and slightly smaller, but the guard/counter are logically cross-cutting and the component is already the largest orchestrator; rejected for maintainability and testability, not correctness.
- **Persisting counts in the backend**: rejected (see R1 and the Out-of-Scope clause).

---

## R5. Avoiding duplicate in-flight cycles

**Decision**: Two complementary guards:
1. **Per-target in-flight set** in `SnackRetryService`. Before any re-initiation (automatic or manual), check `canRetry(target)` (which includes `!inFlight`); immediately `markInFlight(target)`; clear it in `finalize(...)` when the cycle's request/poll settles. This prevents rapid double-clicks (US4.3) and overlapping automatic retries (US3.4/US3.5).
2. **Owner-batch map** (`Map<targetId, batchId>`) in the component. A poll handler only evaluates/retries a target if the polled `batchId` is the current owner of that target. When a target is handed off to a single-target retry batch, ownership moves to the new `batchId`, so a stale emission from the original batch cannot start a duplicate cycle for the same target.

**Rationale**:
- FR-007 requires at most one generation cycle in flight per target at any time. The in-flight set is the direct enforcement; the owner map prevents a subtler double-trigger caused by the original batch continuing to report the target's old `FAILED` state after a retry has begun.
- Both are pure in-memory bookkeeping; no backend coordination is needed.

**Alternatives**:
- **Debounce/throttle on the button only**: rejected. It does not protect the automatic path or the stale-poll path.
- **Ignore poll emissions after starting a retry for a target**: equivalent to the owner map but less explicit; the owner map also handles multiple retry generations (attempt 2 → attempt 3) cleanly.

---

## R6. Configurable polling window

**Decision**: A root-provided Angular `InjectionToken<number>` with a default factory, and a `maxWindowMs` parameter on `pollStatus`:

```ts
export const MAX_POLLING_WINDOW_MS = new InjectionToken<number>('MAX_POLLING_WINDOW_MS', {
  providedIn: 'root',
  factory: () => 120_000,
});

pollStatus(batchId: string, intervalMs = 1500, maxWindowMs = this.maxWindowMs): Observable<BatchStatusResponse> {
  const polls$ = interval(intervalMs).pipe(
    switchMap(() => this.http.get<BatchStatusResponse>(`${this.baseUrl}/${batchId}`)),
    takeWhile((s) => !this.isTerminal(s), true)
  );
  const deadline$ = timer(maxWindowMs).pipe(
    switchMap(() => throwError(() => new BatchPollingTimeoutError(maxWindowMs)))
  );
  return race(polls$, deadline$);
}
```

`app.config.ts` can override the token (e.g. from an environment value) without code changes elsewhere; Playwright can override it to a few hundred milliseconds to test the timeout quickly.

**Rationale**:
- FR-010/SC-004 require a **configurable** maximum lifetime with a **default of 120 seconds**. An injection token is the Angular-idiomatic configuration seam and keeps the default in one place.
- `race` gives a true total-lifetime bound: whichever stream first emits/completes/errors wins and the other is unsubscribed, so the in-flight HTTP request is cancelled on timeout. On terminal completion, `polls$` completes first and `race` completes normally.
- The timeout errors with a typed `BatchPollingTimeoutError` so the component can distinguish "backend stuck" from a transport error and show a terminal, user-visible message (FR-010).

**Alternatives**:
- **RxJS `timeout(maxWindowMs)`**: rejected. The numeric `timeout` resets on each emission (it bounds the *gap* between emissions), so a stream that emits every 1.5 s would never time out. It cannot express a total window.
- **`takeUntil(timer(maxWindowMs))`**: rejected. It completes silently; there is no error/value to drive the required user-visible terminal error.
- **Hard-coded constant**: rejected. FR-010 explicitly requires configurability.
- **Backend-enforced window**: rejected. The window protects the client from a stuck server; enforcing it server-side would not stop the client polling and adds scope.

---

## R7. Explicit-error precondition and per-target single-target batches

**Decision**: The only triggers for an automatic reattempt are:
1. A poll emission where an item's `snackStatus === 'FAILED'` (equivalently a non-null `snackCause`).
2. A failed `createBatch` HTTP request (network / 5xx).
No timer, interval tick, or `IN_PROGRESS` emission triggers generation.

On a valid failure for target `T`, issue `createBatch(themeId, [T])` and merge the returned item by `subtopicId`.

**Rationale**:
- FR-003 lists exactly these signals; FR-004 forbids timer-based or in-progress retries. Poll-driven evaluation is the only place failures are observable.
- The original defect re-created the whole batch; a single-target batch honors the `subtopicId` the UI already passes to `retry()`, and avoids regenerating successful snacks.
- The backend already accepts a list of subtopic ids, so no API change is required.

**Alternatives**:
- **Re-create for all failed targets in one batch**: acceptable and slightly fewer requests, but it couples independent targets' lifecycles and makes per-target attempt accounting harder to reason about. Per-target is clearer and matches the spec's "scoped per generation target" assumption.
- **Backend auto-reattempt on item failure**: rejected. The spec's resolved layering places app-level cycles in the frontend and explicitly preserves `RetryingLlmProvider` semantics; a backend reattempt loop would duplicate the mechanism.

---

## Cross-cutting: what is NOT changed

- No DB schema / entity / enum change; no migration (attempt budget is in-memory frontend state).
- No change to `POST /api/snacks/generate` (FR-012) or `POST /api/batches`.
- No change to `RetryingLlmProvider` (FR-013/FR-014) or the LLM port / prompts.
- No new status vocabulary; `BatchStatus` already includes `PARTIAL`.
- No snack-page UI redesign; the existing card, error banner, and retry affordance are reused.
- No new npm/Maven dependency.
