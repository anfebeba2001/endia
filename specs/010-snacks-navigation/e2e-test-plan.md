# Return to Snacks from Results + Snacks Navigation — E2E Test Plan

## Application Overview

Feature `010-snacks-navigation` closes the learning loop (`snacks → quiz → results → snacks`) and turns
the snack set into browsable content. The results footer gains a "Back to snacks" action that returns to
`/snacks` **without** resetting state or creating a new batch, activating the snack whose `subtopicId`
matches the quizzed subtopic. The snacks page gains a left-side index of every snack (active entry
highlighted with `aria-current="true"` + active class, non-READY entries disabled, click to jump) and
Previous/Next controls that move exactly one position and are boundary-safe. The active position lives in
`AppStateService.currentSnackIndex` (clamped on read), so it survives the quiz/results round trip.

The E2E suite runs against the Angular dev server with all backend endpoints mocked through Playwright
route handlers (see `frontend/e2e/helpers.ts`). Tests use the `mockPage` fixture (installs
`setupAllMocks`) and, where a specific batch shape is needed, `setupThemesSuggestMock` +
`setupConfigurableBatchMock`.

**Seed:** `frontend/e2e/helpers.ts` — `mockPage` fixture (installs all route mocks) plus the
`goToSubtopics` / `goToSnacks` navigation helpers. Scenarios start from a fresh page and drive the real
UI; no scenario depends on another.

## Test Scenarios

### 1. US1 — Return to snacks from results

**Seed:** `frontend/e2e/helpers.ts` (`mockPage` + `goToSnacksFromHome`)

#### 1.1. quiz-complete-renders-back-to-snacks-and-returns-with-no-new-batch

**File:** `frontend/e2e/results-back-to-snacks.spec.ts` (existing)

**Steps:**
  1. Complete the flow home → subtopics → snacks → quiz → results; count `POST /api/batches`.
  2. Assert `.results-footer [data-testid="back-to-snacks"]` is visible and labelled "Back to snacks";
     `.results-footer .primary-btn` is uniquely "Start a new theme".
  3. Click "Back to snacks".
    - expect: URL is `/snacks`; `POST /api/batches` count is unchanged (0 new).
    - expect: no `.loading` / `.empty-state`; `.snack-index__item` count is 3.

#### 1.2. perfect-score-renders-and-returns-with-no-new-batch

**File:** `frontend/e2e/results-back-to-snacks.spec.ts` (extended — adds click-through)

**Steps:**
  1. Override quiz summary so `correctAnswers === totalQuestions` (perfect score).
  2. Complete the flow to results.
    - expect: title is "Perfect score!"; "Back to snacks" and "Start a new theme" are both visible.
  3. Record `POST /api/batches` count, click "Back to snacks".
    - expect: URL is `/snacks`; batch count unchanged; existing snacks render (3 index entries).

#### 1.3. returns-to-the-quizzed-subtopic-snack-not-always-the-first

**File:** `frontend/e2e/results-back-to-snacks.spec.ts` (existing)

**Steps:**
  1. On `/snacks`, click the second index entry ("Calvin cycle") and Start Quiz.
  2. Complete the quiz to results, click "Back to snacks".
    - expect: `.snack-card__chip` is "Calvin cycle" (FR-003/SC-003).
    - expect: exactly one `.snack-index__item[aria-current="true"]`, text "Calvin cycle".

#### 1.4. keeps-the-current-index-when-quizzed-subtopic-has-no-ready-snack

**File:** `frontend/e2e/results-back-to-snacks.spec.ts` (added)

**Steps:**
  1. On `/snacks`, activate the second snack ("Calvin cycle") and complete the quiz to results.
  2. Simulate a quizzed subtopic with no matching snack (`selectedSubtopic`/`quizSubTopicId` set to an
     unknown id via the Angular debug API).
  3. Click "Back to snacks".
    - expect: the active snack is still "Calvin cycle" (index preserved, FR-018); not reset to first.

#### 1.5. back-to-snacks-is-keyboard-reachable-and-activatable

**File:** `frontend/e2e/results-back-to-snacks.spec.ts` (added)

**Steps:**
  1. Complete the flow to results.
  2. Focus the "Back to snacks" button and press Enter.
    - expect: focus is on the button; URL becomes `/snacks`; a snack card is visible.

#### 1.6. start-a-new-theme-still-resets-state-and-lands-on-home

**File:** `frontend/e2e/results-back-to-snacks.spec.ts` (existing)

**Steps:**
  1. Complete the flow to results, click `.results-footer .primary-btn`.
    - expect: URL is `/`; hero title "Learn anything, bite by bite.".

### 2. US2 — Left-side snack index

**Seed:** `frontend/e2e/helpers.ts` (`mockPage` + `goToSubtopics` / `goToSnacks`)

#### 2.1. lists-every-snack-by-subtopic-name-with-exactly-one-active-entry

**File:** `frontend/e2e/snacks-navigation.spec.ts` (existing)

**Steps:**
  1. Reach `/snacks` with a 3-snack set.
    - expect: 3 `.snack-index__item`s labelled by `subtopicName`.
    - expect: exactly one `.snack-index__item--active[aria-current="true"]`, the first.

#### 2.2. clicking-a-ready-entry-updates-the-card-and-highlight-together

**File:** `frontend/e2e/snacks-navigation.spec.ts` (existing)

**Steps:**
  1. Click the third index entry.
    - expect: `.snack-card__chip` and the single active entry both become "Chloroplast structure".

#### 2.3. non-ready-entries-are-disabled-and-inert

**File:** `frontend/e2e/snacks-navigation.spec.ts` (existing)

**Steps:**
  1. Load a batch with item 0 READY, item 1 GENERATING, item 2 FAILED.
    - expect: entry 0 enabled; entries 1 and 2 disabled (FR-010).
    - expect: force-clicking a disabled entry does not change the active card; Next is disabled.

#### 2.4. all-entries-disabled-while-every-snack-is-generating

**File:** `frontend/e2e/snacks-navigation.spec.ts` (added)

**Steps:**
  1. Load a batch where all three items are GENERATING.
    - expect: 3 index entries, all disabled.
    - expect: exactly one `aria-current="true"` entry (index 0) and no `.snack-card` rendered.
    - expect: force-clicking a disabled entry leaves the active entry unchanged.

### 3. US3 — Next/Previous and a persistent active index

**Seed:** `frontend/e2e/helpers.ts` (`mockPage` + `goToSubtopics` / `goToSnacks`)

#### 3.1. next-and-previous-move-exactly-one-and-disable-at-the-boundaries

**File:** `frontend/e2e/snacks-navigation.spec.ts` (existing)

**Steps:**
  1. At index 0: Previous disabled, Next enabled.
  2. Click Next twice → "Calvin cycle" then "Chloroplast structure"; Next disabled at last.
  3. Click Previous → "Calvin cycle"; card/highlight agree.

#### 3.2. repeated-next-reaches-every-snack-with-no-blank-card

**File:** `frontend/e2e/snacks-navigation.spec.ts` (existing)

**Steps:**
  1. Click Next until the last snack; each step renders exactly one card with the expected chip.

#### 3.3. active-index-survives-navigating-to-the-quiz-and-back

**File:** `frontend/e2e/snacks-navigation.spec.ts` (existing)

**Steps:**
  1. Activate the second snack, Start Quiz, go back.
    - expect: the same snack is active (FR-014/SC-006).

#### 3.4. start-quiz-uses-the-active-snack-subtopic

**File:** `frontend/e2e/snacks-navigation.spec.ts` (existing)

**Steps:**
  1. Activate the second snack and Start Quiz.
    - expect: `POST /api/quiz/generate` body `subtopicId === 'sub-002'` (FR-017).

#### 3.5. single-snack-disables-both-previous-and-next

**File:** `frontend/e2e/snacks-navigation.spec.ts` (added)

**Steps:**
  1. Load a 1-snack set.
    - expect: one index entry (active); Previous and Next both disabled; exactly one card.

#### 3.6. out-of-range-stored-index-clamps-to-a-valid-snack

**File:** `frontend/e2e/snacks-navigation.spec.ts` (added)

**Steps:**
  1. On `/snacks`, set `AppStateService.currentSnackIndex` to an out-of-range value (99).
    - expect: exactly one card, showing the last snack; the active entry matches (FR-012, no blank card).

### 4. US4 — Accessible, consistent navigation controls

**Seed:** `frontend/e2e/helpers.ts` (`mockPage` + `goToSubtopics` / `goToSnacks`)

#### 4.1. index-and-nav-controls-are-keyboard-reachable-with-visible-focus

**File:** `frontend/e2e/snacks-navigation.spec.ts` (existing)

**Steps:**
  1. Tab until an index entry is focused.
    - expect: `:focus-visible` matches and outline is not `none`; Enter activates the entry.

#### 4.2. previous-and-next-are-keyboard-activatable-with-accessible-names

**File:** `frontend/e2e/snacks-navigation.spec.ts` (added)

**Steps:**
  1. Assert accessible names for Next ("Next →"), Previous ("← Previous"), and an index entry.
  2. Focus Next and press Enter → card advances one.
  3. Focus Previous and press Enter → card returns.

#### 4.3. card-highlight-and-enabled-states-stay-consistent-after-mixed-interactions

**File:** `frontend/e2e/snacks-navigation.spec.ts` (existing)

**Steps:**
  1. Mix Next/Next/Previous/index clicks; after each, card text == active entry text and enabled
     states match the boundary (FR-019/SC-007).

### 5. Non-regression

**Seed:** `frontend/e2e/helpers.ts` (`mockPage`)

#### 5.1. restart-clears-state

**File:** `frontend/e2e/restart-clears-state.spec.ts` (existing, unchanged)

#### 5.2. progressive-delivery

**File:** `frontend/e2e/progressive-delivery.spec.ts` (existing, unchanged)

#### 5.3. back-navigation

**File:** `frontend/e2e/back-navigation.spec.ts` (existing, unchanged)

#### 5.4. loading-states

**File:** `frontend/e2e/loading-states.spec.ts` (existing, unchanged)
