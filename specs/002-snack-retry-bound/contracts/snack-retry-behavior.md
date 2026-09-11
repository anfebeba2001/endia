# Behavioral Contract: Snack Reattempts & Polling (Frontend)

**Scope**: The Angular snacks flow (`SnackRetryService`, `BatchService.pollStatus`, `SnacksComponent`). This is a **client-behavioral** contract — it constrains the user-visible retry and polling behavior; it does not change any HTTP request/response shape.

**Feature**: `002-snack-retry-bound` (US1–US4, FR-001–FR-011).

---

## C1. Terminal batch status (FR-008 / FR-009)

`BatchService.isTerminal(status)` MUST be:

```
status.status !== 'IN_PROGRESS'
```

- `IN_PROGRESS` is the only non-terminal status.
- `PARTIAL`, `COMPLETE`, and `FAILED` are terminal.
- The poll observable MUST emit the terminal payload and then complete (`takeWhile(pred, true)`), unsubscribing within one poll interval of the terminal status being returned.
- No further `GET /api/batches/{id}` requests after a terminal status.

**Must NOT**: use an allow-list of only `COMPLETE`/`FAILED`.

---

## C2. Bounded polling window (FR-010 / SC-004)

- `pollStatus(batchId, intervalMs = 1500, maxWindowMs = MAX_POLLING_WINDOW_MS)` races the poll stream against a `timer(maxWindowMs)`.
- `MAX_POLLING_WINDOW_MS` is an Angular `InjectionToken<number>` with a root default factory of `120_000` (120 s), overridable in `app.config.ts` and in tests.
- If the window elapses while the batch is still `IN_PROGRESS`, the observable MUST error with a client-side `BatchPollingTimeoutError`; the in-flight request is cancelled and the UI MUST show a terminal, user-visible error and stop progress.
- Polling MUST NOT continue indefinitely.

---

## C3. Attempt budget (FR-001 / FR-002 / FR-006)

- `SnackRetryService` tracks a per-generation-target (`subtopicId`) count of user-visible generation cycles.
- Maximum **3** cycles per target per budget: the initial cycle plus at most 2 automatic reattempts.
- A 4th automatic cycle MUST NOT start.
- `canRetry(target)` is `true` iff the target is not in flight **and** its cycle count is `< 3`.
- The budget is **per cycle**, not lifetime. A manual retry resets the target's count to 0 and starts a fresh 3-attempt cycle.

**Layering**: this counter counts only application-level (user-visible) re-initiation cycles. It does not count, replace, or interfere with the `RetryingLlmProvider` internal retry (retryable-only, up to 3 internal attempts with backoff/jitter), which is preserved unchanged (FR-013/FR-014).

---

## C4. Explicit-error precondition (FR-003 / FR-004)

An automatic reattempt MUST be triggered **only** by an explicit failure signal:

1. A poll emission in which a target's `snackStatus === 'FAILED'` (equivalently a non-null `snackCause`), or
2. A failed `POST /api/batches` request (network / non-2xx).

It MUST NOT be triggered:

- while a target or batch is `IN_PROGRESS` (`PENDING`/`GENERATING`);
- by any timer, interval tick, elapsed time, or a poll that merely returned `IN_PROGRESS`;
- while a generation call is still in flight.

---

## C5. Duplicate / in-flight guard (FR-007)

- At most **one generation cycle may be in flight per target** at any time.
- Before any re-initiation (automatic or manual), the client checks `canRetry(target)` and immediately marks the target in flight; the mark is cleared when the cycle's request/poll settles (`finalize`).
- Rapid repeated user actions (double-clicks) MUST start at most one new cycle.
- A stale poll of a batch that no longer owns a target (ownership tracked by `ownerBatchId`) MUST NOT start a duplicate cycle for that target.

---

## C6. Per-target re-initiation (FR-003)

- A reattempt for target `T` issues `createBatch(themeId, [T])` — a **single-target** batch. It MUST NOT re-create the batch for all subtopics, and it MUST use the failed target's `subtopicId`.
- The resulting item is merged back into the displayed item list keyed by `subtopicId`; healthy targets are not regenerated.
- A failed `createBatch` request counts as one explicit failure and consumes one attempt for the target.

---

## C7. Terminal error on exhaustion (FR-005)

- When a target's 3rd cycle fails, the target enters a **terminal error state**: progress stops, no background activity continues for that target, and a user-visible message **names the failure cause** (typed `LlmErrorCause`).
- The message MUST NOT expose sensitive data (credentials, API keys, raw prompts, provider request/response payloads, personal data) — FR-015.

---

## C8. Manual retry (FR-006 / US4)

- After exhaustion, the existing retry affordance MUST be available for the failed target.
- Invoking it MUST be an explicit user action that:
  1. resets that target's automatic attempt counter to 0 (fresh 3-attempt budget),
  2. clears the target's terminal error,
  3. starts **exactly one** new generation cycle for that target and shows progress again.
- Duplicate rapid invocations MUST start at most one new cycle (C5).
- If the manual retry succeeds, the terminal error is cleared and the snack is displayed.

---

## C9. Preserved behavior (FR-012 / FR-013)

- `POST /api/snacks/generate` is not called by this flow and its contract is unchanged.
- The `RetryingLlmProvider` retry algorithm and the LLM port/prompts are unchanged.
- The `GET /api/batches/{id}` response shape is unchanged; only the client's terminality and window rules and the backend `retrying` semantics change.
- The snack page UI is not redesigned; the existing card, error banner, and retry affordance are reused.

---

## C10. `retrying` indicator (FR-011)

- The user-visible `retrying` state is `status.retrying || retryService.hasInFlight()`.
- It is `true` only during active generation/retry activity and `false` once idle (`PENDING`) or terminal.
- It MUST NOT remain `true` for the entire lifetime of an `IN_PROGRESS` batch.
