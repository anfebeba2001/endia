# Feature Specification: Bounded Snack Reattempts and Polling Termination

**Feature Branch**: `002-snack-retry-bound`

**Created**: 2026-09-11

**Status**: Draft

**Input**: User description: "Generate snacks stands in an infinite loop of retrying. Implement a counter of how many reattempts have been made, only reattempt after an explicit error, and stop when the 3rd reattempt is reached."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Automatic Reattempts Are Bounded to 3 and Fail Terminally (Priority: P1)

When snack generation keeps failing, the app automatically reattempts the generation a bounded number of times and then stops. A total of **3 automatic generation attempts** are made (the initial attempt plus up to 2 reattempts); a 4th automatic attempt is never started. When the 3rd attempt fails, the user is shown a clear, terminal error naming the cause and the loading state ends — the app does not keep spinning or retrying in the background.

**Why this priority**: The unbounded retry loop is the reported defect. It drains resources, hides the real failure from the user, and makes the app appear permanently stuck. Bounding the loop and surfacing a terminal error is the minimum viable fix and the core value of this feature.

**Independent Test**: Can be fully tested by forcing snack generation to fail every time and counting the automatic generation attempts for a target: exactly 3 attempts are made, no 4th is issued, and the UI settles into a terminal error state (spinner stopped, message shown). Delivers the value of "a broken generation fails fast and visibly instead of looping forever."

**Acceptance Scenarios**:

1. **Given** snack generation fails on every attempt, **When** the automatic attempts run, **Then** exactly 3 total generation attempts are made and no 4th automatic attempt is started.
2. **Given** the 3rd automatic attempt fails, **When** the failure is recorded, **Then** the generation enters a terminal error state and the loading/spinner state stops.
3. **Given** the 3rd automatic attempt fails, **When** the terminal error is shown, **Then** the message names the failure cause and is user-visible (not a silent failure and not an indefinite spinner).
4. **Given** an earlier attempt fails but a later attempt succeeds (within the 3-attempt budget), **When** generation succeeds, **Then** the user sees the snack with no error and the attempt counter is not consumed further.
5. **Given** the automatic budget is exhausted, **When** the user observes the app, **Then** no background generation activity continues for that target.

---

### User Story 2 — Batch Polling Terminates on Every Terminal State, Including PARTIAL (Priority: P1)

The frontend polls batch status while generation is in progress and must stop polling as soon as the batch reaches **any** terminal state. A batch that finishes with a mix of ready and failed items is reported as `PARTIAL`; `PARTIAL` is a terminal state and MUST stop the poll loop, exactly like `COMPLETE` and `FAILED`. The rule is defined negatively: every status other than `IN_PROGRESS` is terminal.

**Why this priority**: The reported infinite loop is caused directly by `PARTIAL` being treated as non-terminal, so the polling observable never completes. This is a distinct, independently testable root cause from the bounded reattempt counter, and fixing it alone removes the infinite spinner. P1 because the app is unusable while polling never ends.

**Independent Test**: Can be tested by returning each batch status in turn (`IN_PROGRESS`, `PARTIAL`, `COMPLETE`, `FAILED`) and asserting the polling observable emits and then completes for every non-`IN_PROGRESS` status. Delivers the value of "the screen always settles once the backend is done, even on partial success."

**Acceptance Scenarios**:

1. **Given** the backend reports batch status `PARTIAL` (some items ready, some failed), **When** the client evaluates the status, **Then** polling stops and the ready snacks and failed-item errors are rendered.
2. **Given** the backend reports `COMPLETE`, **When** the client evaluates the status, **Then** polling stops.
3. **Given** the backend reports `FAILED`, **When** the client evaluates the status, **Then** polling stops.
4. **Given** the backend reports any status other than `IN_PROGRESS`, **When** the client evaluates terminality, **Then** the status is treated as terminal (the terminal check is not an allow-list of only `COMPLETE`/`FAILED`).
5. **Given** the backend stays in `IN_PROGRESS` for longer than the allowed polling window, **When** the window elapses, **Then** polling completes with a terminal, user-visible error rather than continuing indefinitely.

---

### User Story 3 — Reattempts Fire Only on an Explicit Error, Never on a Blind Timer (Priority: P2)

An automatic reattempt is triggered only when there is an explicit failure signal: a failed generation item, a typed generation error, or a failed batch creation request. No automatic generation attempt is started merely because time passed, because a poll returned `IN_PROGRESS`, or because a call is still in flight. This prevents the app from launching redundant work on top of an already-running generation.

**Why this priority**: The original loop re-created the entire batch repeatedly with no error precondition. Requiring an explicit error signal makes the retry behavior intentional and prevents resource waste. P2 because the cap in US1 already bounds damage; this makes the behavior correct rather than merely bounded.

**Independent Test**: Can be tested by holding a batch in `IN_PROGRESS` and advancing time: zero automatic generation attempts are issued. Then by injecting an explicit failure (failed item/typed cause/failed create request): exactly one reattempt is scheduled. Delivers the value of "retries happen for a reason, not on a timer."

**Acceptance Scenarios**:

1. **Given** a batch is `IN_PROGRESS` with no failed item, **When** the client polls and time passes, **Then** no automatic generation attempt is started.
2. **Given** a generation target reports an explicit failure (failed status or typed error cause), **When** the failure is observed, **Then** exactly one automatic reattempt is triggered.
3. **Given** a batch creation request itself fails, **When** the failure is observed, **Then** the failure is handled as an explicit error (subject to the same 3-attempt bound) and no timer-based retry occurs.
4. **Given** a generation call is still in flight, **When** the client waits, **Then** no overlapping/duplicate attempt is started for the same target.
5. **Given** the user rapidly triggers the same action more than once, **When** the requests are processed, **Then** at most one in-flight generation cycle exists for the target (no duplicate batch creation).

---

### User Story 4 — Manual Retry Remains Available After the Automatic Budget Is Exhausted (Priority: P2)

After the automatic 3-attempt budget is exhausted and the terminal error is shown, the user can still retry deliberately. A manual retry is an explicit user action that starts exactly one new generation cycle for the failed target and shows progress again. This preserves user control when the failure was transient (for example, a temporary outage or quota reset).

**Why this priority**: The bound must not trap the user in a dead end. Manual retry is the escape hatch that keeps the feature usable after an automatic failure. P2 because the automatic path already delivers a coherent terminal state; manual retry restores recoverability.

**Independent Test**: Can be tested by exhausting the automatic budget, confirming a retry affordance is visible, invoking it once, and verifying exactly one new generation cycle starts and progress is shown. Delivers the value of "I can try again myself when I'm ready."

**Acceptance Scenarios**:

1. **Given** the automatic budget is exhausted and the terminal error is shown, **When** the user views the failed item, **Then** a manual retry affordance is available.
2. **Given** the user invokes manual retry, **When** the action is processed, **Then** exactly one new generation cycle starts for the failed target and a progress/loading indicator is shown.
3. **Given** the user invokes manual retry twice in quick succession, **When** the requests are processed, **Then** only one new generation cycle is started.
4. **Given** a manual retry succeeds, **When** the snack is returned, **Then** the terminal error is cleared and the snack is displayed.

---

### Edge Cases

- **All items succeed**: batch status is `COMPLETE`; polling stops and no reattempt is ever triggered.
- **All items fail**: batch status is `FAILED`; polling stops and a terminal error is shown.
- **Mixed success and failure**: batch status is `PARTIAL`; polling stops, ready snacks render, failed items show their typed cause and a manual retry affordance.
- **A single target fails while others are still generating**: the failed target is marked terminal for itself, but the batch remains `IN_PROGRESS` until every item settles; no reattempt is started for items still in progress.
- **The 3rd attempt fails and the user never acts**: the app stays in the terminal error state with no background activity (no silent timer-driven retry).
- **Manual retry after exhaustion**: starts exactly one new cycle with a fresh per-target automatic 3-attempt budget; the manual retry resets the per-target automatic attempt counter (see FR-006).
- **Non-retryable failure cause**: a cause that is not retryable is surfaced terminally without consuming repeated automatic attempts; only retryable causes are automatically reattempted (consistent with the existing LLM retry semantics).
- **Batch creation itself fails (e.g. network)**: treated as an explicit error, bounded by the same 3-attempt rule, and never retried on a blind timer.
- **Backend never leaves `IN_PROGRESS`**: the polling window elapses (configurable, default 120 seconds) and a terminal error is surfaced instead of polling forever.
- **Duplicate/rapid user actions**: only one in-flight generation cycle per target; repeated clicks do not multiply attempts.
- **Legacy single-shot endpoint used**: `POST /api/snacks/generate` keeps its existing synchronous behavior; this feature does not change its contract.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST maintain an explicit counter of automatic generation attempts per generation target (a snack for a subtopic), incremented each time an automatic attempt is made.
- **FR-002**: System MUST cap automatic generation attempts at 3 total (the initial attempt plus at most 2 automatic reattempts) and MUST NOT start a 4th automatic attempt for the same target.
- **FR-003**: System MUST trigger an automatic reattempt ONLY in response to an explicit failure signal — a failed generation item, a typed generation error cause, or a failed batch-creation request.
- **FR-004**: System MUST NOT trigger an automatic reattempt while a batch or item is `IN_PROGRESS` (or `PENDING`/`GENERATING`), and MUST NOT trigger one on a blind timer.
- **FR-005**: When the 3rd automatic attempt fails, the system MUST enter a terminal error state for that target, stop showing progress, and display a user-visible error message that names the failure cause.
- **FR-006**: After the automatic budget is exhausted, the system MUST expose a manual retry affordance; invoking it MUST be an explicit user action, MUST start exactly one new generation cycle for the failed target, and MUST reset that target's automatic attempt counter to a fresh 3-attempt budget (each cycle — initial or user-initiated — allows up to 3 total automatic attempts).
- **FR-007**: The system MUST guard against duplicate/repeated invocation so that at most one generation cycle is in flight per target at any time.
- **FR-008**: The frontend terminal-status check MUST treat every batch status other than `IN_PROGRESS` as terminal — specifically `PARTIAL`, `COMPLETE`, and `FAILED` — and MUST NOT be limited to an allow-list of only `COMPLETE`/`FAILED`.
- **FR-009**: The status-polling observable MUST complete as soon as a terminal batch status is observed, and MUST NOT continue polling after a terminal status.
- **FR-010**: Polling MUST have a bounded maximum lifetime that is configurable (default 120 seconds) so that it terminates even if the backend never leaves `IN_PROGRESS`, surfacing a terminal, user-visible error rather than polling indefinitely.
- **FR-011**: The `retrying` indicator exposed to the user MUST reflect only active automatic reattempt activity, not the entire lifetime of an `IN_PROGRESS` batch.
- **FR-012**: The system MUST preserve the existing single-shot `POST /api/snacks/generate` behavior and contract unchanged (no regression).
- **FR-013**: The system MUST preserve the existing LLM-level retry semantics: only retryable `LlmGenerationException`s are retried, up to 3 total attempts with backoff/jitter, after which the typed error is re-thrown. This feature MUST NOT duplicate or replace that mechanism.
- **FR-014**: The application-level attempt bound and the LLM-level retry MUST NOT combine into unbounded or multiplicative retries; the number of user-visible automatic generation attempts per target MUST remain bounded at 3 per cycle (the application-level counter counts only explicit application-level, user-visible re-initiation cycles, max 3; the `RetryingLlmProvider` internal retry semantics — retryable-only, up to 3 internal attempts with backoff/jitter — are preserved unchanged, so the worst case is 3 application cycles × 3 internal LLM tries while only 3 attempts are user-visible).
- **FR-015**: Error messages and logs MUST NOT expose sensitive data (credentials, API keys, raw prompts, provider request/response payloads, or personal data).
- **FR-016**: The batch status contract documentation MUST be updated to classify `PARTIAL`/`COMPLETE`/`FAILED` as terminal and to state the intended `retrying` semantics, so the documented contract matches the behavior.

### Key Entities *(include if feature involves data)*

- **Generation Target**: A single unit of generation tracked for reattempts — a snack for one subtopic within a batch. Key attributes (conceptual): target identity (batch + subtopic), attempt count (0–3), current state (idle / in-progress / failed / exhausted / ready), last error cause.
- **Batch Status**: The aggregate state of a batch — `IN_PROGRESS`, `PARTIAL`, `COMPLETE`, `FAILED`. `IN_PROGRESS` is the only non-terminal state; all others are terminal.
- **Generation Error**: A typed failure surfaced to the user. Key attributes: cause category, human-readable message, associated target. The message is user-safe (no sensitive data).
- **Attempt Budget**: The bound applied to a generation target per cycle — maximum 3 automatic attempts, after which the target is exhausted and only manual retry is possible; a manual retry resets the per-target counter to a fresh 3-attempt budget and starts a new cycle.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A generation target that fails every attempt makes at most 3 automatic generation attempts and no 4th, verified by counting attempts in 100% of failing-target tests.
- **SC-002**: When the 3rd automatic attempt fails, the UI reaches a terminal error state and stops showing progress within a bounded time (no indefinite spinner), verified in 100% of failing-target tests.
- **SC-003**: The polling observable completes for `PARTIAL`, `COMPLETE`, and `FAILED`, emitting a terminal value and unsubscribing within one poll interval of the terminal status being returned, in 100% of status-sequence tests.
- **SC-004**: Polling never runs unbounded: for a batch that stays `IN_PROGRESS`, the observable completes within the configured maximum polling window (default 120 seconds) and surfaces a terminal error, verified in 100% of stuck-batch tests.
- **SC-005**: While a batch is `IN_PROGRESS` with no failed item, zero automatic generation attempts are issued across the observed interval (no blind-timer retries).
- **SC-006**: After the automatic budget is exhausted, a manual retry affordance is present and one user action starts exactly one new generation cycle; duplicate rapid actions start at most one cycle, verified in 100% of tests.
- **SC-007**: No regression: `POST /api/snacks/generate` behaves as before and the LLM provider still retries only retryable exceptions up to 3 total attempts with backoff, verified by existing unit/E2E tests passing unchanged.
- **SC-008**: Error messages and logs contain no sensitive data, verified by inspection/automated checks of the failure-path messages and log output.

## Out of Scope

- A general overhaul of error handling, error taxonomy, or user-facing error UX beyond the terminal error required here.
- Changes to LLM prompt content, provider selection, backoff strategy, or the LLM-level retry algorithm (`RetryingLlmProvider` semantics are preserved, not redesigned).
- Changes to the single-shot `POST /api/snacks/generate` contract or the per-subtopic quiz generation flow.
- Introducing persistence/schema changes for attempt counters unless an implementation decision later requires it (this spec defines behavior, not storage).
- Reworking progressive snack delivery, quiz prefetch, drag-and-drop, dark mode, or other features from `001-batch-mvp-enhancements`.
- Defining the exact numeric value of the maximum polling window beyond making it configurable with a default of 120 seconds (see FR-010).

## Assumptions

- The primary infinite-loop root cause is the frontend treating `PARTIAL` as non-terminal while the backend legitimately returns `PARTIAL`; `PARTIAL` is therefore defined as a terminal state for polling.
- "3 attempts" means 3 total automatic generation attempts per cycle (initial + at most 2 reattempts), per the feature description's "capped at 3 (total attempts), no 4th attempt". Automatic reattempts beyond 3 per cycle are forbidden; a manual retry starts a new cycle with a fresh per-target automatic 3-attempt budget (see FR-006).
- The automatic attempt counter is scoped per generation target (a snack for a subtopic), not per whole batch, so a single failing item does not consume the budget of healthy items. The counter is per-cycle and is reset to a fresh 3-attempt budget when the user performs a manual retry.
- LLM-level retries inside `RetryingLlmProvider` are transparent retries within a single application-level generation attempt and are not counted as separate application-level attempts; the application-level counter counts only explicit application-level, user-visible re-initiation cycles (max 3). The provider's internal semantics (retryable-only, up to 3 internal attempts with backoff/jitter) are preserved unchanged, so the worst case is 3 application cycles × 3 internal LLM tries while only 3 attempts are user-visible.
- Only retryable typed causes are eligible for automatic reattempt, consistent with the existing `LlmGenerationException.isRetryable()` semantics; non-retryable causes surface terminally without repeated automatic attempts.
- `retrying` should indicate active reattempt activity only; the current behavior of setting it for the whole `IN_PROGRESS` batch is considered a defect to correct within this feature.
- The backend status vocabulary (`IN_PROGRESS`, `PARTIAL`, `COMPLETE`, `FAILED`) and the frontend model already support `PARTIAL`; no new statuses are introduced.
- Existing terminal-item rendering (ready snacks plus failed items with typed cause and retry affordance) is reused; this feature does not redesign the snack page UI.
- No authentication changes; requests remain anonymous (stub user context) as in the MVP.
- The batch status contract document is expected to be updated alongside this feature (FR-016) to remove the stale "only COMPLETE/FAILED are terminal" note.

## Clarifications

- **Manual retry budget (resolved)**: A manual retry after the automatic budget is exhausted starts a NEW cycle with a FRESH automatic 3-attempt budget. The counter is per-cycle: each cycle (initial or user-initiated) allows up to 3 total automatic attempts. See FR-006.
- **Maximum polling window (resolved)**: The maximum polling lifetime is CONFIGURABLE with a DEFAULT of 120 seconds. See FR-010 and SC-004.
- **Attempt-counting layering (resolved)**: The application-level counter counts only explicit application-level (user-visible) re-initiation CYCLES (max 3). `RetryingLlmProvider`'s existing internal retry semantics (retryable-only, up to 3 internal attempts, backoff/jitter) are PRESERVED unchanged. Worst case is 3 application cycles × 3 internal LLM tries, but only 3 attempts are user-visible. See FR-013 and FR-014.
