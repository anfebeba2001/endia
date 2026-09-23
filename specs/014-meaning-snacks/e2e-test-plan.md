# Meaning Snacks E2E Test Plan

## Application Overview

Feature 014 adds a **"Generate snack"** action to the keyword definition card (feature 013). Activating
it turns a keyword `term` + ≤3-word `definition` into a persisted **meaning snack** that is added to the
snack reading surface on `/snacks` (the confirmed "main topics backbone"), rendered **visually distinct**
(text badge + colour, in both light and dark themes) and with **no quiz** of any kind. Meaning snacks are
persisted by the backend and reloaded per theme via `GET /api/meaning-snacks?themeId=`, so they survive a
reload/new session. The action is offered on all three definition-card hosts: the snacks page, the results
recap, and the history view. All untrusted keyword/meaning text is rendered as escaped text through the
feature-012 `RichTextComponent` (no `[innerHTML]`, no `DomSanitizer`).

All scenarios are deterministic route-mocked Playwright tests; no backend or real LLM is required. The
suite lives in `frontend/e2e/` and shares the `helpers.ts` mock conventions (`setupAllMocks` installs a
default empty `GET /api/meaning-snacks` so every existing spec stays deterministic).

**Seed:** `frontend/e2e/meaning-snacks.spec.ts` (navigates home → `/subtopics` → `/snacks` with the
shared mocks). There is no separate fixture file; each test installs its own mocks and navigates through
the seed helper `goToSnackCard`.

## Coverage assessment

The implementer's original 19 tests already covered US1 (action/loading/success/failure/rapid-click/done),
US2 (distinct card + badge, colour distinction, no `.quiz-btn`, blank-card, mixed ordering + Previous/Next),
US3 (reload persistence, failing listing, empty listing) and US4 (XSS on the snacks/results/history cards,
unicode/RTL, escaped entities, long content). The following acceptance criteria were **not** covered and
were added (6 tests):

| Gap | Added scenario |
|-----|----------------|
| Card action on the **results** surface (FR-001/SC-001) | 1.1 |
| Card action on the **history** surface (FR-001/SC-001) | 1.2 |
| Idempotency: re-generating issues no duplicate request and reflects `alreadyExisted` (FR-005/FR-006/SC-002) | 2.1 |
| Idempotency: an existing listing entry reflects done with **zero** POSTs | 2.2 |
| Distinct treatment in **dark mode** (FR-012/SC-004) | 3.1 |
| Meaning entry is not quiz-bearing while the regular snack keeps its quiz (FR-015/FR-016/SC-005) | 3.2 |

## Test Scenarios

### 1. US1 — Generate snack action

**Seed:** `frontend/e2e/meaning-snacks.spec.ts`

#### 1.1. every-open-definition-card-shows-a-labelled-generate-snack-action
**File:** `frontend/e2e/meaning-snacks.spec.ts`
**Status:** pre-existing
**Steps:**
  1. Open the snacks page with a keyword-bearing snack and open the definition card.
    - expect: `.keyword-card__generate` is visible and labelled `Generate snack`.

#### 1.2. shows-a-loading-state-then-adds-exactly-one-meaning-entry-on-success
**File:** `frontend/e2e/meaning-snacks.spec.ts`
**Status:** pre-existing
**Steps:**
  1. Open the card and activate Generate snack against a delayed 200 mock.
    - expect: the action shows `Generating…` while in flight.
    - expect: the action becomes `Added to your snacks`.
    - expect: exactly one `.snack-index__item--meaning` appears.

#### 1.3. a-failed-generation-shows-an-error-and-adds-no-entry
**File:** `frontend/e2e/meaning-snacks.spec.ts`
**Status:** pre-existing
**Steps:**
  1. Activate Generate snack against a 503 mock.
    - expect: `.keyword-card__error` is visible.
    - expect: zero meaning entries and the index length unchanged.

#### 1.4. rapid-double-activation-issues-one-request-and-creates-no-duplicate
**File:** `frontend/e2e/meaning-snacks.spec.ts`
**Status:** pre-existing

#### 1.5. reopening-the-card-for-an-already-generated-keyword-shows-the-done-state
**File:** `frontend/e2e/meaning-snacks.spec.ts`
**Status:** pre-existing

#### 1.6. the-results-recap-card-offers-generate-snack-and-generates
**File:** `frontend/e2e/meaning-snacks.spec.ts`
**Status:** ADDED
**Steps:**
  1. Complete a quiz to reach `/results` and open the recap keyword's definition card.
    - expect: `.keyword-card__generate` is visible and labelled `Generate snack`.
  2. Activate it.
    - expect: the action becomes `Added to your snacks`.

#### 1.7. the-history-card-offers-generate-snack-and-generates
**File:** `frontend/e2e/meaning-snacks.spec.ts`
**Status:** ADDED
**Steps:**
  1. Open `/history`, expand a theme and open a snack keyword's definition card.
    - expect: `.keyword-card__generate` is visible and labelled `Generate snack`.
  2. Activate it.
    - expect: the action becomes `Added to your snacks`.

### 2. US1 — Idempotency

**Seed:** `frontend/e2e/meaning-snacks.spec.ts`

#### 2.1. re-generating-the-same-keyword-never-duplicates-or-re-requests
**File:** `frontend/e2e/meaning-snacks.spec.ts`
**Status:** ADDED
**Steps:**
  1. Generate once; the mock returns the same item id and reports `alreadyExisted` on repeats.
    - expect: one meaning entry, `Added to your snacks`, exactly one POST.
  2. Escape, reopen the card.
    - expect: the action is disabled/done, still one entry, still one POST.

#### 2.2. a-meaning-snack-already-in-the-listing-reflects-done-without-generating
**File:** `frontend/e2e/meaning-snacks.spec.ts`
**Status:** ADDED
**Steps:**
  1. Open a card whose keyword is already in the theme listing.
    - expect: the action shows `Added to your snacks` and is disabled.
    - expect: zero POSTs were issued and exactly one meaning entry is listed.

### 3. US2 — Distinct rendering and no quiz

**Seed:** `frontend/e2e/meaning-snacks.spec.ts`

#### 3.1. a-meaning-entry-renders-a-distinct-card-badge-with-no-quiz-button
**File:** `frontend/e2e/meaning-snacks.spec.ts`
**Status:** pre-existing

#### 3.2. a-meaning-card-is-colour-distinct-from-a-regular-card-which-keeps-its-quiz-button
**File:** `frontend/e2e/meaning-snacks.spec.ts`
**Status:** pre-existing (light theme)

#### 3.3. selecting-a-meaning-entry-never-renders-a-blank-card
**File:** `frontend/e2e/meaning-snacks.spec.ts`
**Status:** pre-existing

#### 3.4. interleaves-after-the-source-subtopic-appends-unmatched-and-never-lands-blank
**File:** `frontend/e2e/meaning-snacks.spec.ts`
**Status:** pre-existing

#### 3.5. a-meaning-entry-stays-visually-distinct-and-quiz-free-in-dark-mode
**File:** `frontend/e2e/meaning-snacks.spec.ts`
**Status:** ADDED
**Steps:**
  1. Open the snacks page and toggle the dark theme.
    - expect: `html[data-theme=dark]`.
  2. Compare the regular card's border and the meaning card's border/badge.
    - expect: a visible `Meaning snack` badge, no `.quiz-btn`, and a border colour different from the
      regular card's.

#### 3.6. a-meaning-entry-offers-no-quiz-while-the-regular-snack-keeps-its-start-quiz
**File:** `frontend/e2e/meaning-snacks.spec.ts`
**Status:** ADDED
**Steps:**
  1. Select the meaning entry.
    - expect: no `.quiz-btn` anywhere on the page.
  2. Select the regular entry and click Start Quiz.
    - expect: the regular `.quiz-btn` is visible and routes to `/quiz`.

### 4. US3 — Persistence and graceful degradation

**Seed:** `frontend/e2e/meaning-snacks.spec.ts`

#### 4.1. a-generated-meaning-snack-is-re-listed-in-a-new-session-for-the-same-theme
**File:** `frontend/e2e/meaning-snacks.spec.ts`
**Status:** pre-existing

#### 4.2. a-failing-listing-still-renders-regular-snacks-with-no-crash-or-broken-entry
**File:** `frontend/e2e/meaning-snacks.spec.ts`
**Status:** pre-existing

#### 4.3. an-empty-listing-renders-exactly-the-regular-snacks
**File:** `frontend/e2e/meaning-snacks.spec.ts`
**Status:** pre-existing

### 5. US4 — Security and robustness

**Seed:** `frontend/e2e/meaning-snacks.spec.ts`

#### 5.1. meaning-snack-content-payloads-are-inert-and-readable-text-is-preserved
#### 5.2. the-definition-card-on-the-snacks-surface-escapes-its-term-definition
#### 5.3. the-results-recap-definition-card-escapes-the-same-payloads
#### 5.4. the-history-definition-card-escapes-the-same-payloads
#### 5.5. unicode-emoji-and-rtl-text-render-readably
#### 5.6. escaped-entities-are-shown-as-intended-characters-without-double-escaping
#### 5.7. very-long-generated-content-keeps-the-layout-stable-with-no-horizontal-overflow

**Status:** all pre-existing (payloads: `<script>`, `onerror`, `javascript:`, `<iframe>`, `<style>`,
inline styles; assert `window.__xss` undefined and no injected `script/img/iframe/style/a` nodes).

### 6. Non-regression (whole suite)

**Seed:** `frontend/e2e/` shared helpers

The full existing Playwright suite (`happy-path`, `progressive-delivery`, `snacks-navigation`,
`snack-keywords`, `snack-rich-formatting`, `theme-history`, `results-*`, `dark-mode`, …) passes unchanged,
confirming FR-024/FR-026/SC-009: regular snack/quiz/results/history flows and feature-013
highlight/definition-card behaviour are intact with meaning snacks present.

## How to run

```bash
cd frontend
PLAYWRIGHT_HTML_OPEN=never npx playwright test e2e/meaning-snacks.spec.ts   # focused
PLAYWRIGHT_HTML_OPEN=never npx playwright test                              # full suite
```

## Results

- `meaning-snacks.spec.ts`: **25/25 passed** (19 pre-existing + 6 added).
- Full suite: **138/138 passed** (132 baseline + 6 added).
- No app defects found; all scenarios green.
