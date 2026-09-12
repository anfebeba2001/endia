# Read-Only Theme History E2E Test Plan

## Application Overview

Feature `011-theme-history` adds a read-only `/history` page (Angular 21 standalone,
`ChangeDetectionStrategy.OnPush`, signals) reachable from a **History** link in the app header. The page
calls `GET /api/themes` through `ThemeService.listHistory()` and renders every theme newest-first with
its `prompt` and locally formatted `createdAt`, expanding client-side to show each subtopic's snacks
(`concept`, `metaphor`, `whyItMatters`, `related`) and quiz questions (`type`, `prompt`, `explanation`).
It is strictly read-only: it issues no generation/edit/delete request. The page has four exclusive
states — loading → error (message from `ErrorService.getMessage`) → empty → populated — and the route
renders on a direct deep link with no guard/redirect (unlike `/quiz` and `/results`).

The E2E suite runs with Playwright (`frontend/playwright.config.ts`) against `http://localhost:4200`
via `npx ng serve --proxy-config proxy.conf.json --port 4200` (webServer, `reuseExistingServer: true`),
single worker, `channel: chrome`, headless. The `GET /api/themes` response is mocked per test by
`setupThemesHistoryMock(page, themes, opts)` in `frontend/e2e/helpers.ts`, which is deliberately **not**
part of `setupAllMocks`. The mock only intercepts GET requests to `**/api/themes`, leaving
`POST /api/themes/suggest` untouched.

## Test Scenarios

### 1. US1 — Browse created themes from the History page

**Seed:** `frontend/e2e/helpers.ts` (page fixture; `setupThemesHistoryMock` installed per test)

#### 1.1. header-history-link-navigates
**File:** `frontend/e2e/theme-history.spec.ts`
**Steps:**
1. Install the populated `GET /api/themes` mock and `page.goto('/')`.
   - expect: a visible `History` link in `.app-shell__header` whose text matches `/History/`.
2. Click the header link.
   - expect: URL matches `/\/history$/` and `app-history` is visible.

#### 1.2. deep-link-renders-without-redirect
**File:** `frontend/e2e/theme-history.spec.ts`
**Steps:**
1. Install the populated mock and `page.goto('/history')` directly (no prior app state).
   - expect: `app-history` is visible, URL stays `/history` (no redirect), and `.history-card` count is 2.

#### 1.3. lists-themes-newest-first
**File:** `frontend/e2e/theme-history.spec.ts`
**Steps:**
1. Install the populated mock (newest-first array) and `page.goto('/history')`.
   - expect: exactly 2 `.history-card` entries.
2. Read the first and second cards.
   - expect: card 0 `.history-card__prompt` = `Photosynthesis`; card 1 = `Older theme` (newest first).
3. Read card 0 `.history-card__date`.
   - expect: contains `2026` and does not contain the raw ISO separator `T19:52` (locally formatted).

### 2. US2 — Read-only nested subtopics, snacks and quiz questions

**Seed:** `frontend/e2e/helpers.ts`

#### 2.1. nested-content-and-read-only-guarantee
**File:** `frontend/e2e/theme-history.spec.ts`
**Steps:**
1. Record every POST request URL; install the populated mock and `page.goto('/history')`.
   - expect: 2 theme cards render.
2. Click the first card's `.history-card__toggle`.
   - expect: `.history-subtopic__name` first = `Light reactions`.
3. Inspect the first subtopic's snack.
   - expect: `.history-snack__concept` = `Chlorophyll absorbs light`; `.history-snack__metaphor` contains
     `solar kitchen`; `.history-snack__why` contains `Prevents a misconception`; `.history-snack__related`
     contains `ATP synthase`.
4. Inspect the first subtopic's quiz question.
   - expect: `.history-question__type` contains `MULTIPLE_CHOICE`; `.history-question__prompt` =
     `What absorbs light?`; `.history-question__explanation` contains `Chlorophyll drives it.`.
5. Inspect the empty second subtopic (`Calvin cycle`).
   - expect: `.history-subtopic__empty` first contains `/nothing generated yet/i`; `.history .error-banner`
     count is 0.
6. Filter the recorded POST URLs for `/api/batches` or `/api/snacks/generate`.
   - expect: the filtered list is `[]` (read-only guarantee).

### 3. US3 — Loading, error and empty states

**Seed:** `frontend/e2e/helpers.ts`

#### 3.1. loading-state-clears
**File:** `frontend/e2e/theme-history.spec.ts`
**Steps:**
1. Install the mock with `delayMs: 1500` and `page.goto('/history')`.
   - expect: `.history .loading` is visible while the request is in flight.
2. Wait for the response to settle.
   - expect: `.history-card` count is 2 and `.history .loading` count is 0.

#### 3.2. error-state-from-errorservice
**File:** `frontend/e2e/theme-history.spec.ts`
**Steps:**
1. Install the mock with `status: 500` and `body: { error: 'INTERNAL_ERROR' }`; `page.goto('/history')`.
   - expect: `.history .error-banner` contains `Something went wrong on the server` (the
     `ErrorService.getMessage` 5xx text); `.history-card` count is 0; `.history .loading` count is 0.

#### 3.3. empty-state
**File:** `frontend/e2e/theme-history.spec.ts`
**Steps:**
1. Install the mock with `[]` and `page.goto('/history')`.
   - expect: `.history-empty` is visible; `.history-card` count is 0; `.history .error-banner` count is 0.

### 4. US4 — Keyboard accessibility

**Seed:** `frontend/e2e/helpers.ts`

#### 4.1. history-header-link-keyboard-focus
**File:** `frontend/e2e/theme-history.spec.ts`
**Steps:**
1. Install the populated mock and `page.goto('/')`.
2. Press `Tab` until `document.activeElement` matches `.header-link`.
   - expect: focus reached within 25 tabs; computed `outlineStyle` != `none` and `outlineWidth` > 0.
3. Press `Enter`.
   - expect: URL matches `/\/history$/`.

#### 4.2. theme-detail-control-keyboard-focus
**File:** `frontend/e2e/theme-history.spec.ts`
**Steps:**
1. Install the populated mock and `page.goto('/history')`.
   - expect: the first `.history-card` is visible.
2. Press `Tab` until `document.activeElement` matches `.history-card__toggle`.
   - expect: focus reached; computed `outlineStyle` != `none` and `outlineWidth` > 0.
3. Press `Enter`.
   - expect: `.history-subtopic__name` first is visible (detail expanded via keyboard).

## Coverage Map (spec → scenario)

| Requirement | Scenario(s) |
|-------------|-------------|
| FR-011 header link | 1.1, 4.1 |
| FR-015 prompt + createdAt | 1.3 |
| FR-017 deep link no redirect | 1.2 |
| FR-016 nested read-only detail | 2.1 |
| FR-008/SC-007 read-only (0 POST) | 2.1 |
| FR-012 loading state | 3.1 |
| FR-013 ErrorService message | 3.2 |
| FR-014 empty state | 3.3 |
| FR-018 keyboard reachability/focus | 4.1, 4.2 |

## Notes / Environment

- No `tests/seed.spec.ts` was added: the suite uses the shared `mockPage` fixture in `helpers.ts`, and the
  history mock is installed per test to avoid changing the shared fixture used by the other 67 specs.
- All assertions use role/class locators already established by the component template
  (`app-history`, `.history-card*`, `.history-subtopic*`, `.history-snack*`, `.history-question*`,
  `.history-empty`, `.history .error-banner`, `.history .loading`, `.header-link`).
- The full suite is `frontend/e2e/**` (76 tests). Baseline before this feature was 67.
