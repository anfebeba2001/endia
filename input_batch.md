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

### Task 10: Read-only history of created themes with snacks and quiz questions

- Status: no iniciado
- Ready: yes
- Sources: item 11
- Description: Add a read-only history view of every created theme. Backend `GET /api/themes` returns all themes (newest first) with their subtopics, snacks, and quiz questions; frontend adds a `/history` page and header link to browse it.

**Acceptance criteria:**
- [ ] `GET /api/themes` (`ThemeController`) returns `200` with a JSON array of all themes ordered by `createdAt` descending; each entry has `themeId`, `prompt`, `createdAt`, and `subtopics`.
- [ ] Each subtopic entry has `id`, `name`, `snacks` (each with `id`, `concept`, `metaphor`, `whyItMatters`, `related`) and `quizQuestions` (each with `id`, `type`, `prompt`, `explanation`).
- [ ] `ThemeService.listHistory()` is `@Transactional(readOnly = true)` and returns an empty array with HTTP 200 when no themes exist, with no `LazyInitializationException`.
- [ ] New response DTO `ThemeHistoryApiResponse` is added under `interfaces/rest/dto`; existing endpoints (`POST /api/themes/suggest`, `POST /api/snacks/generate`, `/api/batches`, quiz) keep their current behaviour.
- [ ] Frontend adds a lazy-loaded `/history` route (`HistoryComponent`) and a "History" link in the app header (`app.html`); `ThemeService.listHistory()` calls `GET /api/themes` and maps it into a `ThemeHistory` model.
- [ ] The `/history` page shows a loading state, an error message via `ErrorService` on failure, and an empty-state message when there are no themes; for each theme it renders `prompt` and `createdAt` and lets the user view its subtopics with their snacks and quiz questions (read-only, no generation).
- [ ] Tests cover the backend ordering/nested data/empty case and the frontend empty + populated states; existing routes and the home→subtopics→snacks→quiz→results flow still pass.

## Current status

- Task 3: Ready (awaiting implementation)
- Task 4: Ready (awaiting implementation)
- Task 6: Ready (awaiting implementation)
- Task 10: Ready (awaiting implementation) — from item 11 (theme history with snacks and questions)

12. Modify the prompt of the snacks to use bold and render it for important words and sentences, break the long paragraphs into bulletpoints and shorter paragrapsh
