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

### Task 7: Return to the snacks from the quiz results screen + snacks navigation

- Status: no iniciado
- Ready: yes
- Sources: item 10
- Description: After finishing a quiz, the results screen only offers "Start a new theme" (which resets and goes home) with no way back to the knowledge snacks. Add a "Back to snacks" action in the results footer next to "Start a new theme" (both completion states) that returns to `/snacks` preserving the generated snacks and the quizzed subtopic's context. Additionally, expand the snacks page so the user can browse every snack: a left-side index/list of all snacks in the current set (active one highlighted, click any to jump), plus Next/Previous controls to move through all snacks. Navigation state (active index) must be wired through `AppStateService.currentSnackIndex` (currently unused; the component keeps a local `currentIndex`) so it stays in sync and survives re-entry from the results screen.

**Acceptance criteria:**

*(A) Return to snacks from results*
- [ ] `frontend/src/app/pages/results/results.component.html` renders a visible `<button>` labelled "Back to snacks" inside `.results-footer`, next to the existing "Start a new theme" button, in both the "Quiz complete" and "Perfect score!" completion states.
- [ ] Clicking "Back to snacks" navigates to the `/snacks` route (`frontend/src/app/pages/snacks/snacks.component`) and does not trigger a new batch: no additional `POST /api/batches` request is issued (verifiable via a Playwright route counter on `**/api/batches`) and no snack is regenerated.
- [ ] Returning to snacks preserves the quizzed subtopic's context: the snack for the subtopic that was just tested (`AppStateService.selectedSubtopic`, matched via `Snack.subtopicId`) is the active one shown, not always the first snack in the list.
- [ ] The already-generated snacks remain available on return: the snacks page renders the existing snack card(s) and does not show the "The counter is empty" empty state nor a creation/loading state for existing snacks.
- [ ] The existing "Start a new theme" button still resets state and navigates to home, and `frontend/e2e/restart-clears-state.spec.ts` continues to pass.

*(B) Snacks navigation*
- [ ] The snacks page renders a left-side index/list of ALL snacks in the current set (one entry per generated snack, labelled by `Snack.subtopicName`), with the active snack visually highlighted (e.g. `aria-current="true"` and an active CSS class).
- [ ] Clicking any entry in the left-side index makes that snack the active card: the active index and the displayed snack content (`.snack-card__chip` subtopic name / concept) update to the clicked snack.
- [ ] Next and Previous controls exist and move the active index by exactly one snack in each direction; the user can reach every snack in the set (including the last) using only these controls.
- [ ] Next/Previous are disabled or safely no-op at the first and last snack respectively (no out-of-range index, no blank card).
- [ ] The active index is stored in `AppStateService.currentSnackIndex` and the snacks component reads/writes it (replacing the local `currentIndex`), so index clicks and Next/Previous stay in sync; navigating away to quiz/results and back to `/snacks` preserves the active snack (e.g. the snack just quizzed is still active).
- [ ] Snack content, the highlighted index entry, and the Next/Previous enabled/disabled state all remain consistent after any combination of index click and Next/Previous (no desync).
- [ ] The "Start Quiz" action uses the currently active snack's `subtopicId` (not a stale/first snack).
- [ ] All navigation controls (index entries, Next, Previous, "Back to snacks") are real `<button>`/`<a>` elements with accessible labels, reachable and activatable via keyboard (Tab + Enter/Space), with a visible `:focus-visible` style.
- [ ] Existing snacks E2E behaviour is not regressed: `frontend/e2e/progressive-delivery.spec.ts`, `frontend/e2e/back-navigation.spec.ts`, and `frontend/e2e/loading-states.spec.ts` continue to pass (the Next control must still only advance to a READY snack during progressive delivery).

## Current status

- Task 3: Ready (awaiting implementation)
- Task 4: Ready (awaiting implementation)
- Task 6: Ready (awaiting implementation)
- Task 7: Ready (scope expanded — return-to-snacks + full snacks navigation; awaiting user approval of the criteria above)
- Item 11: deferred by the user (later; not grouped into a task)

10. When I finish the test there is no way back to the snacks, it gives me the option to go to the beginning but not to go back into the snacks 

11. I want a history of all the created themes with its questiosn 