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

### Task 10: Expand keyword meaning limit from 3 to 20 words

- Status: no iniciado
- Ready: yes
- Sources: item 13
- Description: Raise the keyword-definition (meaning) length cap from at most 3 words to at most 20 words across generation, normalization, and documentation, so keyword meanings can be richer without changing snack behavior.

**Acceptance criteria:**
- [ ] `SnackService.normalizeKeywords` caps keyword definitions at 20 whitespace-separated tokens (no longer 3); tests that asserted the 3-word cap are updated to the 20-word rule.
- [ ] `MeaningSnackService.normalizeDefinition` caps the source definition at 20 tokens (no longer 3), consistent with `SnackService`.
- [ ] The snack-generation prompt (`backend/src/main/resources/prompts/generate-snack.md`) instructs the model to produce keyword definitions of at most 20 words (example JSON and the explicit rule updated), so definitions are not pre-shortened to 3 words.
- [ ] Comments/javadoc stating "at most 3 words" are updated to "at most 20 words" (`SnackKeyword`, `SnackKeywordDto`, frontend `snack.model.ts`).
- [ ] A generated definition longer than 3 words (up to 20) survives generation → API → display without being truncated to 3 words.
- [ ] A definition longer than 20 words is still safely capped at 20 tokens (no unbounded text persisted or returned).
- [ ] The definition card and highlighted-keyword rendering stay readable with up-to-20-word definitions: no horizontal overflow, no clipping, and feature 013/014 behavior is not regressed.
- [ ] Tests cover the boundary (19, 20, 21 words) and the existing backend/frontend suites pass.

### Task 11: Custom keyword "Define" from a text selection with a 2-second trigger

- Status: no iniciado
- Ready: yes
- Sources: item 13
- Description: Let the user select words in the snack reading content; after the selection is held for 2 seconds a "Define" option appears; activating it generates the meaning for the selected term and shows it in the definition card.

**Acceptance criteria:**
- [ ] Selecting text (one or more words) inside a snack's reading content starts a 2-second timer; only after the selection has been held for ~2 seconds does a clearly labeled "Define" affordance appear for that selection.
- [ ] If the selection changes or is cleared before 2 seconds elapse, no "Define" affordance appears (the timer is cancelled).
- [ ] Activating "Define" sends the selected term to the backend, which returns a generated meaning/definition for that term via the externalized, provider-agnostic LLM flow (no provider-specific logic in services).
- [ ] While the meaning is generating, a loading/in-progress state is shown, and rapid repeated activation starts at most one request.
- [ ] On success, the definition card opens for the selected term showing the generated meaning, rendered as escaped text only (no `[innerHTML]`, no `DomSanitizer`), preserving the feature 012/013 security model.
- [ ] On failure, a meaningful user-safe error is shown and nothing broken/empty/placeholder is added.
- [ ] The selected term is normalized (trimmed, bounded word count) and blank or oversized selections are rejected with a meaningful error.
- [ ] The affordance is reachable via pointer text selection and via programmatic/keyboard selection (`document.getSelection()`), without breaking existing highlighted-keyword clicks or the single-definition-card rule.
- [ ] Non-regression: existing keyword highlight/definition-card and snack reading flows are unchanged and the existing E2E suites pass.

### Task 12: "Create snack" from a custom keyword's generated meaning

- Status: no iniciado
- Ready: yes
- Sources: item 13
- Description: After a custom selected keyword's meaning has been generated and shown, offer a "Create snack" action that turns that term + meaning into a meaning snack in the main topics backbone, reusing the feature-014 flow.

**Acceptance criteria:**
- [ ] After a custom keyword's meaning is generated and displayed, the definition card offers a "Create snack" action for that keyword (reusing/relabeling the feature-014 "Generate snack" action).
- [ ] Activating it generates a meaning snack derived from the custom term + generated meaning and adds it to the main topics backbone via the existing feature-014 persistence/retrieval path.
- [ ] The resulting snack is quiz-free and visually distinct, per feature 014 (no quiz is generated, prefetched, stored, or offered).
- [ ] While generating, a loading/in-progress state is shown; repeated/rapid activation creates at most one snack and no duplicate entry for the same (subtopic, term).
- [ ] On failure, a meaningful user-safe error is shown and nothing broken/empty/placeholder is added.
- [ ] The custom keyword's meaning snack survives a page reload and a new session for the same theme, consistent with feature 014 persistence.
- [ ] The "Create snack" action is offered only when a valid generated meaning for the selected term exists (not before "Define" completes).
- [ ] Non-regression: regular snack generation and existing meaning-snack generation from LLM-provided keywords behave unchanged, and the existing E2E suites pass.

## Current status

- Task 3: Ready (awaiting implementation)
- Task 4: Ready (awaiting implementation)
- Task 6: Ready (awaiting implementation)
- Task 10: Ready (awaiting implementation)
- Task 11: Ready (awaiting implementation)
- Task 12: Ready (awaiting implementation)

_Completed: Task 9 — "Generate snack" from a keyword definition into the main topics backbone (no quiz) → feature `014-meaning-snacks` (merged to main)._

_item 13 grouped into Tasks 10–12 (follow-up enhancement to feature 014); awaiting user approval before item 13 is removed._

13. expand the meaning from key word from 3 to 20 max words, also let's generate custom key words, when an user select words , after 2 seconds it shall appear the option "Define" and then generate the meaning and then again the option of create a snack for that key word


