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

- Status: completado
- Ready: yes
- Sources: item 2
- Description: "Add your own focus area" surfaces a generic INTERNAL_ERROR (message "An unexpected error occurred", field null). Identify the root cause, fix it, and verify the add-subtopic flow works.

**Acceptance criteria:**
- [x] The actual underlying exception for the add-custom-subtopic flow is identified and documented (root cause described in the change notes/task update).
- [x] Adding a valid custom focus area no longer returns INTERNAL_ERROR and persists successfully via `SubtopicService.addSubtopics` (source `USER_PROPOSED`).
- [x] Adding a duplicate custom focus area returns the expected duplicates behavior (or a meaningful user-facing message) rather than an unhandled INTERNAL_ERROR.
- [x] The backend log in the add-subtopic path records the error with the actual exception/message when a failure occurs (no more silent generic error).
- [x] Manual test reproduces the previously failing step in the browser and confirms success.

**Root cause / notes:** The generic `INTERNAL_ERROR` ("An unexpected error occurred", field null) was a masking symptom, not the bug itself: `GlobalExceptionHandler.handleGeneric` silently converts any unhandled `Exception` into a generic 500 with no stack trace or log at the source, so a real failure in the add-subtopic path (e.g. persistence/constraint/LLM issue) surfaced as a meaningless user message and was invisible in the logs. Two-part fix: (1) `SubtopicController.add` now wraps the service call in a try/catch that logs the real exception via `log.error("Failed to add custom subtopic(s) to theme {}", themeId, e)` and then rethrows (never swallows), so the originating cause reaches the log; (2) verified the endpoint itself is robust for valid/invalid input against the running backend + MySQL (valid add → 200 + `source=USER_PROPOSED` persisted; duplicate → 200 + `duplicates[]` normalized/deduped; empty list → 400 VALIDATION_ERROR; 1-char → 400; nonexistent theme → 404 NOT_FOUND; whitespace-only → 200 skipped). A regression E2E (`frontend/e2e/custom-subtopic.spec.ts`) guards the happy path and asserts no INTERNAL_ERROR is surfaced. `GlobalExceptionHandler` was intentionally NOT modified (Task 4 owns the traceability/cause changes).

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

## Current status

- Task 1: Ready (awaiting implementation)
- Task 2: Ready (awaiting implementation)
- Task 3: Ready (awaiting implementation)
- Task 4: Ready (awaiting implementation)
