# Keyword Meaning 3→20 Words E2E Test Plan

## Application Overview

Feature 015 raises the keyword-definition ("meaning") length cap from 3 to 20 words. The user-facing
surface is the existing feature-013 keyword flow: keywords in a snack are highlighted as colored
term controls; activating one opens a dismissible definition card that shows the term and its full
definition (escaped interpolation only, no `[innerHTML]`/`DomSanitizer`). Feature 014 adds a
"Generate snack" action on the card that POSTs the term + definition to
`POST /api/meaning-snacks/generate`. The E2E suite is frontend-only: it boots the Angular dev server
via `playwright.config.ts` (`npx ng serve --proxy-config proxy.conf.json --port 4200`) and installs
Playwright `page.route` mocks for every API endpoint (`frontend/e2e/helpers.ts`). Server-side
normalization (20-token truncation, 200-char word-boundary clamp) happens behind the mocked API and
is therefore **not** observable in E2E; it is covered by the backend unit tests
(`SnackServiceTest`, `MeaningSnackServiceTest`). E2E covers the display/readability and the
frontend-side "no truncation before sending" guarantees.

**Seed / entry point:** every scenario self-seeds through the existing mocked snack-card navigation
(`setupAllMocks` + `overrideBatchWithSnack` + `goToSnackCard`) in
`frontend/e2e/snack-keywords.spec.ts` / `frontend/e2e/meaning-snacks.spec.ts`. There is no shared
`seed.spec.ts` in this repo; that is the established convention.

## Test Scenarios

### 1. Definition card shows the full definition (US1 AS3, US3 AS1, FR-004/FR-011/FR-012, SC-001/SC-004)

**Seed:** `frontend/e2e/snack-keywords.spec.ts` (`setupFixtures` + `goToSnackCard`)

#### 1.1. twenty-word-definition-wraps-in-card
**File:** `frontend/e2e/snack-keywords.spec.ts` (existing test: "a 20-word definition wraps in the card with no horizontal overflow")

**Steps:**
  1. Set viewport to 1280×800 (desktop), load a snack whose keyword `photosynthesis` has a 20-token definition, and open its definition card.
    - expect: `.keyword-card__definition` is visible and its text equals the full 20-word definition (no truncation to 3 words).
    - expect: the definition's token count is ≤ 20.
    - expect: `document.documentElement.scrollWidth <= window.innerWidth + 1` (no horizontal overflow).
  2. Repeat at 375×720 (narrow/mobile).
    - expect: the card uses its narrow-viewport presentation, the full definition is shown, and there is no horizontal overflow.

#### 1.2. long-single-token-definition-wraps-without-overflow
**File:** `frontend/e2e/snack-keywords.spec.ts` (NEW — added by this pass)

**Steps:**
  1. At desktop width, load a snack whose keyword definition is a single very long token and open the definition card.
    - expect: `.keyword-card__definition` shows the full token (`overflow-wrap: anywhere`), `scrollWidth <= clientWidth` and `scrollHeight <= clientHeight` (wrapped, not clipped).
    - expect: no page-level horizontal overflow.
  2. Repeat at 375×720 (card opens as the bottom sheet).
    - expect: the card has `keyword-card--sheet`, the full token wraps inside it, and there is no page-level horizontal overflow.

#### 1.3. click-opens-card-with-up-to-20-word-definition
**File:** `frontend/e2e/snack-keywords.spec.ts` (existing test: "click opens a card showing the definition (up to 20 words); outside-click closes it")

**Steps:**
  1. Open the card for a short definition and assert the definition text and token count.
    - expect: the definition is shown and its token count is ≤ 20 (the old ≤ 3 assertion is gone).
    - expect: outside-click dismisses with no navigation.

### 2. The full 4–20-word definition reaches meaning-snack generation (US1 AS4, FR-004)

**Seed:** `frontend/e2e/meaning-snacks.spec.ts` (`setupAllMocks` + `overrideBatchWithSnack` + `goToSnackCard`)

#### 2.1. generate-snack-sends-full-twenty-word-definition
**File:** `frontend/e2e/meaning-snacks.spec.ts` (NEW — added by this pass)

**Steps:**
  1. Load a snack whose keyword has a 20-token definition, open its card, and activate "Generate snack" while capturing the POST body.
    - expect: `POST /api/meaning-snacks/generate` body `definition` equals the full 20-word definition (frontend performs no truncation).
    - expect: the action reaches the done state and one meaning entry is listed.

### 3. Untrusted longer definitions stay inert (US3 AS4, FR-013/FR-014, SC-005)

**Seed:** `frontend/e2e/snack-keywords.spec.ts` / `frontend/e2e/meaning-snacks.spec.ts`

#### 3.1. definition-card-escapes-injection-payloads
**File:** `frontend/e2e/snack-keywords.spec.ts` (existing tests in "US4 — untrusted keyword text is escaped")

**Steps:**
  1. Load a snack whose keyword term/definition contain `<script>`, `onerror`, `javascript:`, `<iframe>`, `<style>` payloads and open the card.
    - expect: no `script`/`img`/`iframe`/`style`/`a` node inside the card, `window.__xss` undefined, and the payload is shown as readable text.

### 4. Feature 013/014 behavior is not regressed (FR-016, SC-006)

**Seed:** the existing spec suites.

**Steps:**
  1. Run the full `frontend/e2e` suite.
    - expect: highlight, click-to-open, dismiss (outside-click/close/Escape), focus return, Generate snack, meaning-snack listing/ordering/security, and all other suites pass unchanged.

## Out of E2E scope (covered by backend unit tests)

- **US2 / FR-002 / FR-003 / SC-002/SC-003**: server-side truncation of >20-token definitions (both
  `SnackKeyword.definition` and the meaning-snack source definition) — hidden behind the mocked API;
  covered by `SnackServiceTest` / `MeaningSnackServiceTest` 19/20/21 boundary tests.
- **FR-019 / SC-008**: the 200-char word-boundary clamp on the meaning-snack source definition —
  backend-only; covered by `MeaningSnackServiceTest`.
- **FR-008 / FR-018 / SC-007**: prompt wording (20 words; no "very short") — covered by
  `GenerateSnackPromptTest` and prompt-file assertions.
