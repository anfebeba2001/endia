### Task 1: Fix Endia logo visibility in the top bar across light and dark modes

- Status: no iniciado
- Ready: yes
- Sources: item 1
- Description: The Endia logo word in the top bar currently blends into the background in dark mode. Ensure it is clearly visible with good contrast in both light and dark themes.

**Acceptance criteria:**
- [ ] The "Endia" logo word in the top bar is visible with adequate contrast (WCAG AA, >= 4.5:1 for normal text) in light mode.
- [ ] The "Endia" logo word is visible with adequate contrast (>= 4.5:1) in dark mode — it no longer matches the background color.
- [ ] The contrast is verified by toggling the app between light and dark theme at least once and visually confirming the logo reads clearly in each mode.
- [ ] No other adjacent UI elements (icons, buttons) in the top bar change appearance or layout as a side effect.

### Task 2: Root-cause and fix "An unexpected error occurred" when adding a custom focus area

- Status: no iniciado
- Ready: yes
- Sources: item 2
- Description: "Add your own focus area" surfaces a generic INTERNAL_ERROR (message "An unexpected error occurred", field null). Identify the root cause, fix it, and verify the add-subtopic flow works.

**Acceptance criteria:**
- [ ] The actual underlying exception for the add-custom-subtopic flow is identified and documented (root cause described in the change notes/task update).
- [ ] Adding a valid custom focus area no longer returns INTERNAL_ERROR and persists successfully via `SubtopicService.addSubtopics` (source `USER_PROPOSED`).
- [ ] Adding a duplicate custom focus area returns the expected duplicates behavior (or a meaningful user-facing message) rather than an unhandled INTERNAL_ERROR.
- [ ] The backend log in the add-subtopic path records the error with the actual exception/message when a failure occurs (no more silent generic error).
- [ ] Manual test reproduces the previously failing step in the browser and confirms success.

### Task 3: Root-cause and fix "An unexpected error occurred" when generating snacks

- Status: no iniciado
- Ready: yes
- Sources: item 3
- Description: Generating snacks surfaces a generic INTERNAL_ERROR (message "An unexpected error occurred", field null). Identify the root cause, fix it, and verify snack generation works.

**Acceptance criteria:**
- [ ] The actual underlying exception for the snack-generation flow is identified and documented (root cause described in the change notes/task update).
- [ ] Generating snacks for a selected theme/subtopics no longer returns INTERNAL_ERROR and returns/persists the expected snacks via `SnackService`.
- [ ] Snack generation failure conditions (e.g. empty/incomplete LLM response) surface a meaningful, specific error message instead of the generic INTERNAL_ERROR.
- [ ] The backend log in the snack-generation path records the error with the actual exception/message when a failure occurs.
- [ ] Manual test reproduces the previously failing step in the browser and confirms success.

### Task 4: Add error detail, traceability, and logging across the backend

- Status: no iniciado
- Ready: yes
- Sources: item 4
- Description: Improve the backend error handling so clients get meaningful details of failures and the backend retains traceability. Log meaningful operations across backend services/methods.

**Acceptance criteria:**
- [ ] `GlobalExceptionHandler` generic handler (fallback for unhandled `Exception`) no longer returns only "An unexpected error occurred" without a traceable identifier — it includes `cause` and/or a traceability id in the `ErrorResponse` payload.
- [ ] Unhandled exceptions are logged server-side with the full stack trace and a correlation/trace id, and the response carries a reference to that id.
- [ ] Existing handled exception mappings (validation, conflict, LLM generation, domain) still return their same status/meaningful messages and are not regressed.
- [ ] Meaningful methods across the backend (services and controllers for themes, subtopics, snacks, quiz, batch) log their entry, key decisions, and outcome (success or error) at an appropriate level.
- [ ] The `ErrorResponse` response shape change does not break the frontend `error.service` message extraction (or is updated accordingly and verified).
- [ ] Logs do not leak sensitive data (no prompt bodies, LLM keys, or user PII beyond what is necessary).

### Task 7: Bound snack-generation reattempts and stop infinite retry/polling

- Status: no iniciado
- Ready: yes
- Sources: item 7
- Description: Snack generation can loop retrying/polling forever. Add an explicit reattempt counter capped at 3, only reattempt after a concrete error, stop at the 3rd attempt, and terminate polling on any non-`IN_PROGRESS` terminal state.

**Acceptance criteria:**
- [ ] A reattempt counter tracks snack-generation reattempts and never exceeds 3 total attempts; when the 3rd reattempt fails, no further reattempt is triggered.
- [ ] A reattempt is only triggered after an explicit generation error is received (e.g. `FAILED` item status / typed `snackCause`, or a failed `POST /api/batches` call) — never on a blind timer while status is `IN_PROGRESS`.
- [ ] After the 3rd failed reattempt the UI surfaces a clear terminal error state (message + no further automatic retry), and the user can still manually retry.
- [ ] `BatchService.pollStatus`/`isTerminal` treats every non-`IN_PROGRESS` state as terminal — in particular `PARTIAL` (and `COMPLETE`/`FAILED`) stops the polling loop instead of polling forever.
- [ ] The existing single-shot `POST /api/snacks/generate` flow still returns snacks/typed errors as before, and the `RetryingLlmProvider` LLM-level behavior (retry only on retryable `LlmGenerationException`, stop at `maxAttempts` = 3) is unchanged.
- [ ] Manual test confirms a failing snack generation stops after the 3rd reattempt with a visible error, and a succeeding run completes normally.

## Current status

- Task 1: Ready (awaiting implementation)
- Task 2: Ready (awaiting implementation)
- Task 3: Ready (awaiting implementation)
- Task 4: Ready (awaiting implementation)
- Task 5: COMPLETED (branch 004-topic-suggest-retry)
- Task 6: Ready (awaiting implementation)
- Task 7: Ready (awaiting implementation)