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

### Task 9: Guarantee bounded snack generation — no infinite loops, no unanswered request

- Status: no iniciado
- Ready: yes
- Sources: item 8
- Description: Harden the snack-generation path so every generation step, retry, and batch worker run is time/count bounded and always reaches a terminal outcome, with no loop or request able to run forever.

**Acceptance criteria:**
- [ ] Every outbound LLM call in the snack path is bounded by a configured connect+read timeout (`llm.timeout-seconds`, default 30s), verified by a test where a hanging provider call cannot exceed that bound.
- [ ] Automatic LLM retries remain bounded by `batch.retry.max-attempts` (default 3) and the re-roll in `generateValidSnack` cannot create an unbounded loop; a test asserts a failing/hanging provider produces a finite, bounded number of calls.
- [ ] A single subtopic's snack generation is bounded by a configurable maximum (timeout and/or max attempts); exceeding it marks the item `FAILED` with a typed cause instead of blocking the batch worker.
- [ ] The batch worker always reaches a terminal aggregate status (`COMPLETE`/`PARTIAL`/`FAILED`) within a bounded time even if one or all snack generations hang; verified by a test using a never-returning provider.
- [ ] No synchronous HTTP request in the snack/batch path can be left unanswered indefinitely: `POST /api/batches` returns 202 immediately and `GET /api/batches/{id}` completes without waiting on background generation.
- [ ] Frontend polling remains bounded (default 120s) and terminates on any terminal status or on timeout, surfacing a terminal error (preserved from 002/005).
- [ ] Timeout/failure paths log the bounded outcome with the actual cause and no sensitive data.

## Current status

- Task 3: Ready (awaiting implementation)
- Task 4: Ready (awaiting implementation)
- Task 6: Ready (awaiting implementation)
- Task 9: Ready (awaiting implementation)


8. the wait for the snacks feels eternal, let's add waiting messages "waiting for llm response" "assembling response" but it is important that the messages are accurate with every step, not generical messages, also let's make sure there are no infinite loops possible in the way of generating snacks, not any request not being responden infinitely.