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

### Task 9: "Generate snack" from a keyword definition into the main topics backbone (no quiz)

- Status: no iniciado
- Ready: yes
- Sources: item 12
- Description: Add a "Generate snack" button on the keyword definition card that creates a "meaning snack" for that keyword and adds it to the main topics backbone, visually distinct from normal snacks and without any quiz test.

**Acceptance criteria:**
- [ ] The keyword definition card shows a "Generate snack" button.
- [ ] Clicking "Generate snack" creates a "meaning snack" derived from that keyword definition and adds it to the main topics backbone.
- [ ] While generating, the UI shows a loading/in-progress state; on failure a meaningful error is shown and no broken/empty entry is added to the backbone.
- [ ] The meaning snack is persisted (backend) and remains present in the main topics backbone after a page reload/new session.
- [ ] The meaning snack is visually distinct from regular snacks (different color/badge) in the main topics backbone.
- [ ] The meaning snack has no quiz test: no quiz is generated for it and no quiz entry/action is offered for it, unlike the regular main snacks.
- [ ] Regular snacks and the existing snack/quiz flow are unaffected (no regression).

## Current status

- Task 3: Ready (awaiting implementation)
- Task 4: Ready (awaiting implementation)
- Task 6: Ready (awaiting implementation)
- Task 9: Ready (awaiting implementation)

12. Modify the prompt of the snacks to use bold and render it for important words and sentences, break the long paragraphs into bulletpoints and shorter paragrapsh, it is also needed that the key words are with a different color, so when the user clicks them, it shows a short card with definition of the word (max 3 words componning this keys meaning), also, this key meanings are going to have a "generate snack "button, when clicked, it adds a "meaning snack" into the main topics backbone, it has to have a different color and it does not have a quiz test like the other main snacks 


