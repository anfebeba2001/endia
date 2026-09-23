# Feature 013 — Colored Keywords with Definition Cards: E2E Test Plan

## Application Overview

Feature 013 makes the snack output identify its key terms and give each a definition of at most 3
words, renders every term that appears in the snack body in a distinct highlight color, and opens a
short, dismissible definition card when the user clicks/taps a highlighted term. The keyword set
travels with the snack through the API on every snack-returning surface: the snack card, the
results recap, and the read-only history view.

This plan covers the **frontend, browser-level** behavior of the feature. The frontend renders
snacks from the batch endpoints (`POST /api/batches` + `GET /api/batches/{id}`), the results recap
(`GET /api/quiz/summary/{subtopicId}`), and the history view (`GET /api/themes`). All E2E specs
route-mock those endpoints (the real render path); `/api/snacks/generate` is unused by the frontend
and is therefore not a render source. The API/persistence contract (keyword presence, ≤3-word
normalization, dedupe, legacy null/empty rows) is owned by the backend test suites and is out of
frontend E2E scope; see **Coverage Map** for the mapping.

Security model (feature 012 preserved): the shared renderer and the definition card use escaped
Angular interpolation only — no `[innerHTML]`, no `DomSanitizer`. Every keyword term/definition is
untrusted model output and must stay inert.

## Test Scenarios

Seed / fixtures: the repo has no `tests/seed.spec.ts`. Specs live in `frontend/e2e/` and import the
shared route-mocking helpers from `frontend/e2e/helpers.ts` (`setupAllMocks`,
`setupThemesHistoryMock`, `angularSubmitHome`) and the existing navigation patterns. The
snack-keywords spec installs its own per-test `**/api/batches/*` and `**/api/quiz/summary/*`
overrides with keyword-bearing fixtures, mirroring `snack-rich-formatting.spec.ts`.

### 1. US2 — Key terms are highlighted in a distinct color

**Seed:** `frontend/e2e/helpers.ts` (`setupAllMocks` + per-test batch override)

#### 1.1. every-occurrence-present-term-highlighted
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** US2.1, US2.5, FR-013, FR-017, SC-002
**Steps:**
  1. Mock a batch whose snack body contains a keyword term twice with different casing.
    - expect: two `.rich-text__keyword` controls exist for that term; original casing is preserved.
  2. Read the computed color of a highlight and of the surrounding paragraph.
    - expect: the highlight color differs from the body text color.

#### 1.2. absent-term-produces-no-artifact
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** US2.2, FR-014, SC-002
**Steps:**
  1. Mock a snack whose keyword list includes a term that does not appear in the body text.
    - expect: no `.rich-text__keyword` for that term in any body field.

#### 1.3. overlapping-terms-longest-wins
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** FR-016, §Edge Cases
**Steps:**
  1. Mock keywords `light` and `light reactions` with text containing `light reactions`.
    - expect: exactly one highlight, for the longer term; no nested highlight controls.

#### 1.4. term-inside-bold
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** US2.4, FR-015, FR-029
**Steps:**
  1. Mock a snack with `**Photosynthesis**` and keyword `photosynthesis`.
    - expect: a `<strong>` exists and contains the keyword control; feature-012 formatting is intact.

#### 1.5. regex-like-terms-are-literal
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** §Edge Cases (literal terms), FR-015
**Steps:**
  1. Mock keywords `C++`, `(x)`, `a.b` present in the body.
    - expect: each is highlighted exactly once and rendering is not broken.

#### 1.6. related-chips-never-highlighted
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** US2.2, FR-014, §Edge Cases (related scope)
**Steps:**
  1. Mock a keyword whose term only appears in `related`.
    - expect: `related` contains no keyword controls and still renders plain labels.

### 2. US3 — Clicking/tapping a term opens a dismissible definition card

**Seed:** `frontend/e2e/helpers.ts` (`setupAllMocks` + per-test batch override)

#### 2.1. click-opens-card-outside-click-closes
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** US3.1, US3.2, US3.3, US3.6, FR-018, FR-019, FR-020, SC-003, SC-004
**Steps:**
  1. Click a highlighted term.
    - expect: a `.keyword-card` is visible showing the term and only its ≤3-word definition.
  2. Click outside the card.
    - expect: the card is gone and the URL still ends in `/snacks` (no navigation).

#### 2.2. close-control-dismisses
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** US3.4, US3.6, FR-020, SC-004
**Steps:**
  1. Open a card, activate the visible close control.
    - expect: the card is gone and no navigation occurred.

#### 2.3. escape-dismisses-and-returns-focus
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** US3.5, US3.8, FR-020, FR-023
**Steps:**
  1. Click a term, press Escape.
    - expect: the card closes and focus returns to the term control.

#### 2.4. opening-different-term-replaces-card
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** US3.9, FR-021
**Steps:**
  1. Open the card for one term, then activate a second highlighted term.
    - expect: exactly one card exists and it shows the second term's definition.

#### 2.5. keyboard-open-and-escape
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** US3.8, FR-023
**Steps:**
  1. Tab to a highlighted term, press Enter.
    - expect: the card opens.
  2. Press Escape.
    - expect: the card closes and focus is on a keyword control.

#### 2.6. desktop-and-mobile-no-overflow
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** US3.7, FR-022, SC-005
**Steps:**
  1. At 1280×800 and again at 375×720, open a term's card.
    - expect: the card is visible and the document has no horizontal overflow at either width.

#### 2.7. card-near-viewport-bottom-stays-inside
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** §Edge Cases (card near edge), FR-022, SC-005
**Steps:**
  1. With a short viewport, scroll a term to the bottom edge and open it.
    - expect: the card's top ≥ 0 and bottom ≤ viewport height (fully inside).

### 3. US4 — Keyword text is always safe, escaped text

**Seed:** `frontend/e2e/helpers.ts` (`setupAllMocks` + per-test batch override)

#### 3.1. snack-card-keyword-payloads-are-inert
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** US4.1–US4.3, FR-026, FR-027, FR-028, SC-007
**Steps:**
  1. Mock keyword term/definition payloads (`<script>`, `<img onerror>`, `javascript:`, `<iframe>`,
     `<style>`).
    - expect: `window.__xss` stays undefined, no `script/img/iframe/style/a` nodes exist, and the
      readable text is preserved.

#### 3.2. definition-card-escapes-payloads
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** US4.5, FR-026, FR-027, SC-007
**Steps:**
  1. Open the card for the payload term.
    - expect: the definition is shown as escaped text (`<img` visible as text), nothing executes, no
      injected nodes.

#### 3.3. results-recap-keyword-payloads-are-inert
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** US4.5, FR-025, FR-027, SC-007
**Steps:**
  1. Complete the quiz with the payload snack mocked on both the batch and the summary recap.
    - expect: the recap executes nothing, injects no nodes, and its definition card shows escaped
      text.

#### 3.4. history-keyword-payloads-are-inert
**File:** `frontend/e2e/theme-history.spec.ts`
**Covers:** US4.5, FR-025, FR-027, SC-007
**Steps:**
  1. Mock `GET /api/themes` with keyword payloads, expand the theme, open the payload term's card.
    - expect: nothing executes, no injected nodes, and the card shows escaped text.

### 4. US5 — Empty/absent keywords degrade gracefully

**Seed:** `frontend/e2e/helpers.ts` (`setupAllMocks` + per-test batch override)

#### 4.1. empty-keyword-list-renders-as-before
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** US2.3, US5.3, FR-024, SC-006
**Steps:**
  1. Mock a snack with `keywords: []`.
    - expect: no highlight controls, no card affordance, no error banner, body text intact.

#### 4.2. missing-keywords-field-renders-as-before
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** US5.3, US5.4 (legacy rows), FR-024, SC-006
**Steps:**
  1. Mock a snack with no `keywords` field at all (legacy/pre-feature shape).
    - expect: renders exactly as before with no highlight, no card, no artifacts.

#### 4.3. large-keyword-list-stable
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** §Edge Cases (large list), FR-024
**Steps:**
  1. Mock 40 keywords.
    - expect: highlights render, no layout break, no horizontal overflow.

### 5. Cross-surface consistency (snack card, results recap, history)

**Seed:** `frontend/e2e/helpers.ts` (`setupAllMocks` + per-test batch/summary override)

#### 5.1. snack-card-and-recap-highlight-consistently
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** US2.6, FR-025, SC-009
**Steps:**
  1. Mock the same keyword snack on the batch and the summary recap; view the snack card.
    - expect: two highlights for the term on the card.
  2. Complete the quiz to the results page.
    - expect: the recap shows the same two highlights with the same casing.

#### 5.2. recap-card-opens-and-dismisses
**File:** `frontend/e2e/snack-keywords.spec.ts`
**Covers:** US3.1, US3.5, FR-025
**Steps:**
  1. On the recap, click a highlighted term, then press Escape.
    - expect: the card opens with the correct definition and closes on Escape.

#### 5.3. history-highlights-present-term
**File:** `frontend/e2e/theme-history.spec.ts`
**Covers:** US2.6, FR-025, SC-009
**Steps:**
  1. Mock `GET /api/themes` with keywords; expand the theme.
    - expect: the present term is highlighted and no literal markers appear.

#### 5.4. history-card-opens-and-dismisses
**File:** `frontend/e2e/theme-history.spec.ts`
**Covers:** US3.1, US3.5, FR-025
**Steps:**
  1. Expand the theme, click a highlighted term, press Escape.
    - expect: the card shows the correct definition and closes on Escape.

## Coverage Map

| Spec requirement | Scenario(s) | File |
| --- | --- | --- |
| US1 (API emits keywords, ≤3 words, normalization) | Backend suites: `GenerateSnackPromptTest`, `OpenAiLlmProviderTest`, `SnackServiceTest`, `SnackMarkupRoundTripTest`, `SnackKeywordsPersistenceIntegrationTest` (T006–T009, T043) | `backend/src/test/...` |
| US2.1, US2.2, US2.4, US2.5, US2.6 | 1.1, 1.2, 1.4, 5.1, 5.3 | `snack-keywords.spec.ts`, `theme-history.spec.ts` |
| US2.3 | 4.1, 4.2 | `snack-keywords.spec.ts` |
| US3.1–US3.9 | 2.1–2.7, 5.2, 5.4 | `snack-keywords.spec.ts`, `theme-history.spec.ts` |
| US4.1–US4.5 | 3.1–3.4 | `snack-keywords.spec.ts`, `theme-history.spec.ts` |
| US5.3, US5.4 | 4.1, 4.2 | `snack-keywords.spec.ts` |
| US5.6 (non-regression) | full suite: existing snack/quiz/results/progressive-delivery/history specs | `frontend/e2e/*.spec.ts` |
| SC-001 | Backend suites (keyword presence + ≤3-word) | `backend/src/test/...` |
| SC-002 | 1.1, 1.2, 1.6 | `snack-keywords.spec.ts` |
| SC-003, SC-004 | 2.1–2.5 | `snack-keywords.spec.ts` |
| SC-005 | 2.6, 2.7 | `snack-keywords.spec.ts` |
| SC-006 | 4.1–4.3 | `snack-keywords.spec.ts` |
| SC-007 | 3.1–3.4 | `snack-keywords.spec.ts`, `theme-history.spec.ts` |
| SC-008 | Backend suites (`SnackMarkupRoundTripTest`, `SnackKeywordsPersistenceIntegrationTest`) | `backend/src/test/...` |
| SC-009 | 5.1–5.4 + full suite | `snack-keywords.spec.ts`, `theme-history.spec.ts` |

## Out of Scope (E2E)

- API/persistence contract details (prompt output, ≤3-word normalization, dedupe, legacy null rows):
  these require the backend + DB and are covered by the backend suites above.
- Task 9 "Generate snack" card action and the "meaning snack" backbone entry: explicitly not built
  (FR-031).
- `/api/snacks/generate`: not a render path used by the frontend.
