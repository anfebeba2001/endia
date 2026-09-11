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

### Task 6: Fall back to an alternate LLM provider after retries are exhausted

- Status: no iniciado
- Ready: yes
- Sources: item 5
- Description: After the 2 automatic reattempts for topic suggestion fail (SERVICE_UNAVAILABLE per the backend), use an alternate LLM provider as a fallback so the suggestion flow can still succeed.

**Acceptance criteria:**
- [ ] The backend/API topic-suggestion path attempts a primary LLM provider and, after the retries are exhausted (or on persistent SERVICE_UNAVAILABLE), switches to an alternate configured LLM provider.
- [ ] When the primary provider is unavailable but the alternate provider responds, the topic-suggestion returns a successful result (topics are suggested), with the alternate provider clearly used/logged.
- [ ] The alternate provider is configurable (via configuration/env) and does not require code changes to switch providers.
- [ ] The failure path is logged/traced showing which provider was used, including provider fallback decisions.
- [ ] If both primary and alternate providers fail, a meaningful error is returned (consistent with Task 5 surface behaviour).
- [ ] Manual test simulates a primary-provider SERVICE_UNAVAILABLE and confirms the alternate provider is used and the flow still succeeds.

### Task 8: Fix batch snack generation hang (detached-proxy error + executor deadlock + empty-state flash)

- Status: no iniciado
- Ready: yes
- Sources: item 8
- Description: Batch flow stalls after "suggest subtopics is working": `BatchService.runBatch` (async, non-transactional) passes detached `Subtopic` lazy proxies into `SnackService.generateSingleSnack`, raising `LazyInitializationException` at `SnackService.java:66`, and the nested quiz tasks deadlock the same `batchExecutor` so the batch never reaches a terminal status. The frontend then flashes the "No snacks were prepared..." empty state and polls/retries forever.

**Acceptance criteria:**
- [ ] `BatchService.runBatch` no longer passes detached lazy `Subtopic` proxies into `SnackService.generateSingleSnack`; the snack path loads the required `Subtopic`/`Theme` data (or takes an id) so `subtopic.getTheme().getPrompt()` never triggers a detached-proxy dereference.
- [ ] No `org.hibernate.LazyInitializationException` is logged from the batch snack path (`SnackService.java:66` / `BatchService.java:109`) during a full batch run.
- [ ] The nested quiz tasks no longer block on the same `batchExecutor` that runs `runBatch` (separate executor or equivalent non-blocking design), so `runBatch` always terminates instead of waiting on `CompletableFuture.allOf(...).join()`.
- [ ] An end-to-end batch (`POST /api/batches` then poll) reaches a terminal status (`COMPLETE`, `PARTIAL`, or `FAILED`) within a bounded time; it never remains `IN_PROGRESS` with `snackStatus`/`quizStatus: PENDING` indefinitely.
- [ ] When the LLM responds successfully, at least one snack is persisted and returned with status `READY` for the batch's selected subtopics.
- [ ] The frontend (`snacks.component.ts` / `.html`) does not show the "No snacks were prepared. Head back and generate them from your selected subtopics." empty state during normal polling (i.e. it is suppressed while a batch is still `IN_PROGRESS` or before the first poll response, between `creating.set(false)` and the first items arriving).
- [ ] The polling/retry UI terminates: it stops polling once a terminal batch status is reached and does not display an infinite "retrying" state.
- [ ] The existing Task 7 bounded-retry behaviour (max 3 attempts) is preserved and not exceeded in the snack/quiz batch flow.
- [ ] Existing single-shot snack and quiz generation flows still work (no regression).

## Current status

- Task 3: Ready (awaiting implementation)
- Task 4: Ready (awaiting implementation)
- Task 6: Ready (awaiting implementation)
- Task 8: Ready (awaiting implementation)
