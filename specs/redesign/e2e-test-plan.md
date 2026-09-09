# Endia Redesign E2E Test Plan

## Application Overview

Endia is an Angular 19 learning app with a "Learning Menu" visual redesign (warm paper background, ink text, tomato accent, menu-card metaphor). The app follows a 5-step flow: Home → Subtopics → Snacks → Quiz → Results. The existing 17-test E2E suite (11 spec files) covers all user-facing flows using route mocks to isolate from the AI backend.

## Existing Test Coverage (all green)

The suite at `frontend/e2e/` uses a `mockPage` fixture that installs route mocks for all API endpoints (`/api/themes/suggest`, `/api/snacks/generate`, `/api/quiz/generate`, `/api/quiz/answer`, `/api/quiz/summary/*`). Tests run against `http://localhost:4200` with system Chromium via Playwright symlinks.

### 1. Core Happy Path (P0)

**Seed:** `e2e/helpers.ts` → `mockPage` fixture

#### 1.1. Full happy path
**File:** `e2e/happy-path.spec.ts`
**Steps:**
  1. Navigate to home, verify hero title and submit button
    - expect: `h1.hero__title` = "Learn anything, bite by bite."
    - expect: `button.search__button` = "Suggest subtopics"
  2. Submit theme via Angular debug API, navigate to /subtopics
    - expect: URL contains `/subtopics`
    - expect: `h1.page__title` = "Pick what to study"
    - expect: 3 subtopic cards rendered
  3. Click "Generate snacks", navigate to /snacks
    - expect: URL contains `/snacks`
    - expect: `h1.page__title` = "Your Snacks"
    - expect: 3 snack cards with concept/metaphor/why sections
  4. Click "Start Quiz", navigate to /quiz
    - expect: URL contains `/quiz`
    - expect: 4 quiz options visible
  5. Answer all 4 questions (option 0 each time)
    - expect: Feedback visible after each answer
    - expect: Last question shows "See results" button
  6. Navigate to /results
    - expect: Results header and score card visible
    - expect: 4 breakdown cards rendered

#### 1.2. Input validation
**File:** `e2e/input-validation.spec.ts`
**Steps:**
  1. Submit with 1-char input
    - expect: Stays on home, error banner with "at least 2 characters"
    - expect: API not called
  2. Submit with empty input
    - expect: Stays on home, error banner visible

#### 1.3. Subtopic deselection
**File:** `e2e/subtopic-deselection.spec.ts`
**Steps:**
  1. Navigate to subtopics, verify 3 selected
    - expect: Button shows "Generate snacks (3)"
  2. Deselect/reselect cards
    - expect: Button count updates (2 → 1 → 2)
  3. Generate with 2 selected
    - expect: API receives correct subtopic IDs (sub-001, sub-003)

#### 1.4. Quiz answer feedback (correct)
**File:** `e2e/quiz-feedback.spec.ts`
**Steps:**
  1. Answer correctly (option 0)
    - expect: Green feedback bar with "Correct"
    - expect: Explanation visible

#### 1.5. Quiz answer feedback (wrong)
**File:** `e2e/quiz-feedback.spec.ts`
**Steps:**
  1. Answer incorrectly (option 1)
    - expect: Feedback bar with "Not quite"
    - expect: Correct option highlighted

#### 1.6. Results accuracy
**File:** `e2e/results-accuracy.spec.ts`
**Steps:**
  1. Complete quiz, verify results
    - expect: Score card visible
    - expect: 4 breakdown cards with explanations
    - expect: 4 indicator elements (✓/✗)

#### 1.7. Restart clears state
**File:** `e2e/restart-clears-state.spec.ts`
**Steps:**
  1. Complete full flow to results
  2. Click "Start a new theme"
    - expect: Returns to home with fresh state

### 2. Navigation & Direct Access (P1)

#### 2.1. Back button subtopics → home
**File:** `e2e/back-navigation.spec.ts`

#### 2.2. Back button snacks → subtopics
**File:** `e2e/back-navigation.spec.ts`

#### 2.3. Direct URL /quiz redirects
**File:** `e2e/direct-url-access.spec.ts`

#### 2.4. Direct URL /results redirects
**File:** `e2e/direct-url-access.spec.ts`

#### 2.5. Direct URL /subtopics empty state
**File:** `e2e/direct-url-access.spec.ts`

#### 2.6. Error recovery (500 → retry)
**File:** `e2e/error-recovery.spec.ts`

#### 2.7. Loading states (home + snacks)
**File:** `e2e/loading-states.spec.ts`

### 3. Edge Cases (P2)

#### 3.1. Perfect score with trophy
**File:** `e2e/perfect-score.spec.ts`

## Redesign Verification

The visual redesign (warm paper background, ink text, tomato accent, menu-card metaphor) touched:
- `src/styles.scss`, `src/index.html`, `src/app/app.html`, `app.scss`
- All 5 page pairs: home, subtopics, snacks, quiz, results (.html/.scss)

**Key finding:** The redesign preserved all test-critical selectors and user-facing texts. New classes/attributes (e.g. `aria-pressed`, `sr-only h1`) were added but existing BEM class names (`hero__title`, `page__title`, `subtopic-card`, `snack-card`, `quiz-btn`, `option`, `feedback`, `score-card`, `breakdown-card`) remain unchanged.

## Visual Evidence

Screenshots captured at `frontend/e2e/screenshots/`:
- `home-desktop.png` — Home page at 1280×900
- `home-mobile.png` — Home page at 375×812
- `subtopics-desktop.png` — Subtopics page at 1280×900
- `subtopics-desktop-full.png` — Subtopics page full layout
- `subtopics-mobile.png` — Subtopics page at 375×812

**Note:** Snacks/Quiz/Results pages require the AI backend (LLM API) which is unavailable in this environment. These pages are tested via mocks in the E2E suite (all 17 tests pass), confirming the DOM structure and selectors are correct.
