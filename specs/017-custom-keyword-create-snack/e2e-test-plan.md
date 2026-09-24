# "Create snack" from a Custom Keyword's Generated Meaning — E2E Test Plan

## Application Overview

Feature 017 completes the custom-keyword journey: after a learner defines an arbitrary selected term on
the `/snacks` reading page (feature 016's ~2s-hold **Define** flow), the definition card offers a
**Create snack** action. Activating it turns the custom **term + generated meaning** into a **meaning
snack** in the main topics backbone, reusing the feature-014 generate/persistence/append/retrieval
path. The created snack is **quiz-free** and uses the feature-014 **distinct** meaning-snack treatment,
and it **persists** across a reload/new session for the same theme.

**Source subtopic (Option A, confirmed):** the created snack attaches to the currently active backbone
entry's source subtopic — a regular entry's `subtopicId`, or a meaning entry's `sourceSubtopicId`.

Security model (feature 012/013/014/015/016 preserved): the custom term and generated meaning are read
as plain text and rendered via escaped Angular interpolation only — no `[innerHTML]`, no
`DomSanitizer`. The action label is static text.

This plan covers the **frontend, browser-level** behavior. The frontend boots the Angular dev server via
`playwright.config.ts` and installs `page.route` mocks for every API endpoint
(`frontend/e2e/helpers.ts`). The backend is **unchanged**; the reused feature-014 validation/idempotency/
error shapes are already covered by the existing backend suites
(`MeaningSnackServiceTest`, `MeaningSnackContractTest`) and are therefore not re-observable in E2E
beyond the mocked responses.

**Seed / entry point:** every scenario self-seeds through the existing mocked navigation
(`setupAllMocks` + `overrideBatchWithSnack` + `goToSnackCard`) and then runs the feature-016 Define flow
(`selectByMouse`/`selectProgrammatically` + ~2.2s hold + click `.selection-define__button`) with
`setupDefinitionsMock`. There is no shared `seed.spec.ts` in this repo; that is the established
convention (mirrors `custom-keyword-define.spec.ts` and `meaning-snacks.spec.ts`).

**Mocks used:**
- `setupAllMocks(page)` — themes/suggest, batch, quiz, meaning-snacks (empty), definitions (benign).
- `overrideBatchWithSnack(page, snack)` — a single READY regular snack (or a custom fixture).
- `setupDefinitionsMock(page, { generate })` — feature 016 `POST /api/definitions/generate`.
- `setupMeaningSnacksMock(page, { list, generate })` — feature 014 `GET /api/meaning-snacks` +
  `POST /api/meaning-snacks/generate`.

**Helper reuse:** `selectByMouse`, `selectProgrammatically`, `clearSelection`, `goToSnackCard`,
`overrideBatchWithSnack`, `meaningSnack`, `setupMeaningSnacksMock`, `setupDefinitionsMock` already exist
in `custom-keyword-define.spec.ts`/`meaning-snacks.spec.ts`/`helpers.ts`; the new spec reuses the
`helpers.ts` ones and mirrors the local selection helpers.

## Test Scenarios

### 1. Action availability (US1, FR-001..FR-003, SC-001)

**Seed:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (`setupAllMocks` + `overrideBatchWithSnack`
+ `setupDefinitionsMock` + `goToSnackCard` + Define flow)

#### 1.1. create-action-appears-only-after-define-completes
**File:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (NEW)

**Steps:**
  1. Hold a selection ~2s and click **Define** with a **delayed** definitions mock.
     - expect: while defining (`.keyword-card__loading` visible) the card offers **no**
       `.keyword-card__generate` action.
  2. Let the delayed mock resolve.
     - expect: the card shows the definition and exactly **one** action labelled `Create snack`.

#### 1.2. no-create-action-on-definition-error
**File:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (NEW)

**Steps:**
  1. Hold a selection and click **Define** with a 503 definitions mock.
     - expect: the card shows a meaningful error (`role="alert"`) and **no** Create snack action; no
       meaning entry is added.

#### 1.3. regular-keyword-card-still-shows-generate-snack
**File:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (NEW)

**Steps:**
  1. Click a highlighted `.rich-text__keyword` on a regular snack.
     - expect: the card shows the feature-014 action labelled `Generate snack` (not `Create snack`);
       behavior unchanged.

### 2. Creation & backbone integration (US1, FR-004..FR-009, SC-002/SC-003)

**Seed:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (`setupMeaningSnacksMock` + Define flow)

#### 2.1. create-adds-exactly-one-meaning-snack-with-captured-subtopic
**File:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (NEW)

**Steps:**
  1. Define a custom term on a **regular** active entry (`sub-001`); assert the Create snack action.
  2. Click it, capturing the `POST /api/meaning-snacks/generate` body.
     - expect: body is `{ themeId, subtopicId: 'sub-001', term, definition }` where `definition` is the
       generated meaning; the button shows the loading state, then `Added to your snacks`; exactly one
       `.snack-index__item--meaning` appears in the backbone.

#### 2.2. loading-state-while-creating
**File:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (NEW)

**Steps:**
  1. Define a term, then click **Create snack** with a **delayed** meaning-snacks mock.
     - expect: a loading state is shown (`.keyword-card__action-status` / label `Creating…`), then the
       `done` state.

#### 2.3. rapid-double-activation-creates-one-snack
**File:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (NEW)

**Steps:**
  1. Define a term, then double-click the action via `evaluate` (two synchronous clicks) with a
     delayed mock.
     - expect: exactly **one** generate call and exactly **one** meaning entry.

#### 2.4. failure-shows-error-and-adds-nothing
**File:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (NEW)

**Steps:**
  1. Define a term; click **Create snack** with a 503 meaning-snacks mock.
     - expect: `.keyword-card__error` visible with `role="alert"` and non-empty user-safe text; **no**
       meaning entry added; backbone entry count unchanged.

#### 2.5. reopen-reflects-done-without-duplicate
**File:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (NEW)

**Steps:**
  1. Define a term and create the snack; dismiss the card (Escape).
  2. Re-define the **same** term on the same entry.
     - expect: the action shows the `done` state (disabled), exactly one meaning entry remains, and no
       extra generate request is issued.
  3. **Server-side idempotent branch (CHK029):** configure the mock so a *fresh* create request (e.g. in
     a separate context/session where the client `hasGenerated` set is empty) returns
     `{ alreadyExisted: true, meaningSnack: <the same snack> }`.
     - expect: the card still resolves to the `done` state, the backbone still shows exactly one entry
       for that `(sourceSubtopicId, term)`, and no duplicate is appended — proving the flow reflects the
       server's idempotent response and not only the local `hasGenerated` key.

#### 2.6. source-subtopic-is-meaning-entry-sourceSubtopicId
**File:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (NEW)

**Steps:**
  1. Seed a meaning snack (`sourceSubtopicId: 'sub-003'`) via `setupMeaningSnacksMock`.
  2. Select the meaning entry in the backbone (`.snack-index__item--meaning`).
  3. Select text inside `.snack-card--meaning .snack-card__concept`, hold ~2s, click **Define**, then
     **Create snack**, capturing the generate body.
     - expect: `subtopicId` equals the meaning entry's `sourceSubtopicId` (`sub-003`); the new snack
       appears next to that source entry (Option A).

### 3. Quiz-free & visual distinction (US2, FR-011..FR-013, SC-004)

**Seed:** `frontend/e2e/custom-keyword-create-snack.spec.ts`

#### 3.1. created-snack-is-distinct-and-quiz-free
**File:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (NEW)

**Steps:**
  1. Create a meaning snack from a custom keyword.
  2. Select the created entry in the backbone.
     - expect: it renders `.snack-card--meaning` with the `Meaning snack` badge; its top border color
       differs from a regular card; it exposes **no** `.quiz-btn`.

#### 3.2. regular-snack-keeps-its-quiz-affordance
**File:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (NEW)

**Steps:**
  1. With the custom meaning snack present, select the regular entry.
     - expect: `.snack-card .quiz-btn` is visible and clicking it routes to `/quiz` (unchanged).

#### 3.3. no-quiz-generated-or-offered-for-the-created-snack
**File:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (NEW)

**Steps:**
  1. Create the snack and walk every backbone entry.
     - expect: the meaning entry never triggers a quiz request/route and never shows a quiz affordance;
       selecting it always shows content (never a blank card).

### 4. Persistence & retrieval (US3, FR-014/FR-015, SC-005)

**Seed:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (`stored[]` listing mock, mirroring
`meaning-snacks.spec.ts` US3)

#### 4.1. created-snack-persists-across-a-new-session
**File:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (NEW)

**Steps:**
  1. Define a term and create the snack with a listing mock whose `stored[]` is appended on generate.
  2. Re-enter the theme (full page load → `/subtopics` → `/snacks`).
     - expect: the custom meaning snack is re-listed in the backbone with its term, content, distinct
       badge, and no quiz.

#### 4.2. failing-listing-degrades-gracefully
**File:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (NEW)

**Steps:**
  1. Mock `GET /api/meaning-snacks` with a 500.
     - expect: regular snacks still render; no crash, no broken entries, no error banner.

### 5. Security (US4, FR-017..FR-019, SC-006)

**Seed:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (payload definitions + payload meaning
content)

#### 5.1. custom-term-and-generated-meaning-are-inert-in-the-card
**File:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (NEW)

**Steps:**
  1. Define a term/mock a definition containing `<script>`, `<img onerror>`, `javascript:`, `<iframe>`,
     `<style>`.
     - expect: `window.__xss` stays `undefined`; no `script/img/iframe/style/a` nodes inside
       `.keyword-card`; readable escaped text preserved.

#### 5.2. created-content-payloads-are-inert-in-the-backbone
**File:** `frontend/e2e/custom-keyword-create-snack.spec.ts` (NEW)

**Steps:**
  1. Create a snack whose mocked `meaningSnack` content (`concept`/`metaphor`/`whyItMatters`) contains
     the payloads.
  2. Select the created entry.
     - expect: `window.__xss` stays `undefined`; no `script/img/iframe/style/a` nodes inside
       `.snack-card--meaning`; readable text preserved.

### 6. Non-regression (US5, FR-020..FR-024, SC-008)

**Seed:** full existing suite

#### 6.1. feature-016-define-flow-unchanged
**File:** `frontend/e2e/custom-keyword-define.spec.ts` (existing — run unchanged)

#### 6.2. feature-014-keyword-generate-flow-unchanged
**File:** `frontend/e2e/meaning-snacks.spec.ts` (existing — run unchanged)

#### 6.3. remaining suites unchanged
**Files:** `happy-path`, `progressive-delivery`, `snack-wait-messages`, `snacks-navigation`,
`snack-keywords`, `snack-rich-formatting`, `results-*`, `theme-history` (existing — run unchanged)

## Coverage Map

| Spec requirement | Scenario(s) | File |
| --- | --- | --- |
| FR-001 | 1.1 | `custom-keyword-create-snack.spec.ts` |
| FR-002 | 1.1, 1.2 | `custom-keyword-create-snack.spec.ts` |
| FR-003 | 1.1, 1.3 | `custom-keyword-create-snack.spec.ts` |
| FR-004 | 2.1 | `custom-keyword-create-snack.spec.ts` |
| FR-005 | 2.1, 2.6 | `custom-keyword-create-snack.spec.ts` |
| FR-006 | 2.2 | `custom-keyword-create-snack.spec.ts` |
| FR-007 | 2.3 | `custom-keyword-create-snack.spec.ts` |
| FR-008 | 2.4 | `custom-keyword-create-snack.spec.ts` |
| FR-009 | 2.1 | `custom-keyword-create-snack.spec.ts` |
| FR-010 | 2.5 | `custom-keyword-create-snack.spec.ts` |
| FR-011 | 3.1, 3.3 | `custom-keyword-create-snack.spec.ts` |
| FR-012 | 3.1 | `custom-keyword-create-snack.spec.ts` |
| FR-013 | 3.2, 3.3 | `custom-keyword-create-snack.spec.ts` |
| FR-014 | 4.1 | `custom-keyword-create-snack.spec.ts` |
| FR-015 | 4.2 | `custom-keyword-create-snack.spec.ts` |
| FR-016 | Plan finding (no new entity/schema/endpoint); backend suites unchanged | `plan.md`, `backend/src/test/...` |
| FR-017 | 5.1, 5.2 | `custom-keyword-create-snack.spec.ts` |
| FR-018 | 5.1, 5.2 | `custom-keyword-create-snack.spec.ts` |
| FR-019 | 5.1, 5.2 + no `[innerHTML]`/`DomSanitizer` review | `custom-keyword-create-snack.spec.ts` |
| FR-020 | 6.1–6.3 + full suite | `frontend/e2e/*.spec.ts` |
| FR-021 | 6.2, 1.3 | `meaning-snacks.spec.ts`, `custom-keyword-create-snack.spec.ts` |
| FR-022 | 6.1 | `custom-keyword-define.spec.ts` |
| FR-023 | 6.3 | `snack-keywords.spec.ts` |
| FR-024 | 6.2, 6.3 | `frontend/e2e/*.spec.ts` |
| SC-001 | 1.1, 1.2 | `custom-keyword-create-snack.spec.ts` |
| SC-002 | 2.1, 2.3, 2.5 | `custom-keyword-create-snack.spec.ts` |
| SC-003 | 2.2, 2.4 | `custom-keyword-create-snack.spec.ts` |
| SC-004 | 3.1–3.3 | `custom-keyword-create-snack.spec.ts` |
| SC-005 | 4.1, 4.2 | `custom-keyword-create-snack.spec.ts` |
| SC-006 | 5.1, 5.2 | `custom-keyword-create-snack.spec.ts` |
| SC-007 | Plan §Backend finding; backend suites unchanged | `plan.md`, `backend/src/test/...` |
| SC-008 | 6.1–6.3 + full suite | `frontend/e2e/*.spec.ts`, `backend/src/test/...` |

## Out of E2E scope (covered by backend suites / plan)

- **FR-016 / SC-007 (no new entity/schema/endpoint)**: confirmed by the plan's backend finding; the
  reused feature-014 contract is unchanged and the backend suites (`MeaningSnackServiceTest`,
  `MeaningSnackContractTest`) pass as-is.
- **Backend idempotency / error shapes**: hidden behind the mocked API; covered by the existing backend
  unit/contract tests.
- **FR-017–FR-019 rendering guarantee**: E2E asserts no execution/injection; the absence of
  `[innerHTML]`/`DomSanitizer` in the new code is a code-review check (the diff is two frontend files).
