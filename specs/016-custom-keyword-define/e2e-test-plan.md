# Custom Keyword "Define" (2-Second Held Selection) — E2E Test Plan

## Application Overview

Feature 016 lets a learner select one or more words inside a snack's reading content on the `/snacks`
reading page and, after holding the selection for about 2 seconds, shows a transient,
selection-anchored **Define** affordance. Activating it generates a short definition (≤20 words)
through `POST /api/definitions/generate` and shows it in the existing single definition card. The
affordance is reachable via pointer and programmatic/keyboard selection, is cancelled when the
selection changes/clears, and is offered **only** on the snacks reading page (the results recap and
read-only history surfaces are unchanged and offer no trigger).

Security model (feature 012/013/014/015 preserved): the selected term is read as plain text and the
generated definition is rendered via escaped Angular interpolation only — no `[innerHTML]`, no
`DomSanitizer`. The affordance renders a static label only.

This plan covers the **frontend, browser-level** behaviour. The frontend boots the Angular dev server
via `playwright.config.ts` and installs `page.route` mocks for every API endpoint
(`frontend/e2e/helpers.ts`). The ≤10-token term bound, the ≤20-token definition bound, the re-roll
policy and the error shapes are enforced behind the mocked API and are therefore **not** observable in
E2E; they are covered by the backend unit/contract tests (`DefinitionServiceTest`,
`DefinitionContractTest`, `GenerateDefinitionPromptTest`). E2E covers the DOM-level selection/timer
behaviour, the trigger, the generation UX, the frontend-side bound feedback, scope and injection.

**Seed / entry point:** every scenario self-seeds through the existing mocked snack-card navigation
(`setupAllMocks` + `overrideBatchWithSnack` + `goToSnackCard`) in
`frontend/e2e/custom-keyword-define.spec.ts`. There is no shared `seed.spec.ts` in this repo; that is
the established convention.

**Selection helpers:** pointer selection is created with `page.mouse.move/down/move/up` over a text
node inside `.snack-card__concept`; programmatic selection is created with `page.evaluate` setting a
`Range` over a text node and `window.getSelection().addRange(...)` (which fires `selectionchange`).
The ~2s hold is exercised with `page.waitForTimeout(2200)`; the negative path changes/clears the
selection before that.

## Test Scenarios

### 1. Hold trigger (US1, FR-001..FR-003, SC-001)

**Seed:** `frontend/e2e/custom-keyword-define.spec.ts` (`setupAllMocks` + `overrideBatchWithSnack` +
`goToSnackCard`)

#### 1.1. held-pointer-selection-shows-define-affordance
**File:** `frontend/e2e/custom-keyword-define.spec.ts` (NEW)

**Steps:**
  1. Select a term in `.snack-card__concept` by pointer drag.
  2. Wait ~2s without changing the selection.
    - expect: the `.selection-define` affordance is visible, labelled `Define`, anchored near the
      selection.

#### 1.2. no-affordance-before-two-seconds
**File:** `frontend/e2e/custom-keyword-define.spec.ts` (NEW)

**Steps:**
  1. Select a term and immediately (well under 2s) assert the affordance.
    - expect: the affordance is not present.
  2. Hold to ~2s.
    - expect: the affordance appears.

#### 1.3. changing-or-clearing-selection-cancels
**File:** `frontend/e2e/custom-keyword-define.spec.ts` (NEW)

**Steps:**
  1. Select a term, then before ~2s select a different term (or clear the selection).
  2. Wait past ~2s.
    - expect: no affordance for the old selection.
  3. Hold the new selection ~2s.
    - expect: the affordance appears for the new selection.

#### 1.4. release-without-activation-does-not-generate
**File:** `frontend/e2e/custom-keyword-define.spec.ts` (NEW)

**Steps:**
  1. Select a term, hold ~2s (affordance appears), then click elsewhere to clear the selection without
     activating `Define`.
    - expect: the affordance disappears, no `POST /api/definitions/generate` is issued, the reading
      flow is unaffected.

#### 1.5. escape-dismisses-the-affordance
**File:** `frontend/e2e/custom-keyword-define.spec.ts` (NEW)

**Steps:**
  1. Hold a selection until the affordance appears, press Escape.
    - expect: the affordance is gone with no navigation and no generation.

### 2. Generation UX (US2, FR-006..FR-009, SC-002/SC-003)

**Seed:** `frontend/e2e/custom-keyword-define.spec.ts` (`setupDefinitionsMock` with a delayed/echoing
reply)

#### 2.1. activate-shows-loading-then-card-with-definition
**File:** `frontend/e2e/custom-keyword-define.spec.ts` (NEW)

**Steps:**
  1. Hold a selection to show the affordance and click `Define` while capturing the POST body.
    - expect: `POST /api/definitions/generate` body is `{ themeId, term }` with the normalized term;
      the card opens for the term and shows a loading state ("Defining…").
  2. Let the delayed mock resolve.
    - expect: the card shows the generated definition (escaped text); the loading state ends.

#### 2.2. rapid-double-activation-issues-one-request
**File:** `frontend/e2e/custom-keyword-define.spec.ts` (NEW)

**Steps:**
  1. Show the affordance and activate `Define` twice rapidly (or click twice before the mock resolves).
    - expect: exactly one `POST /api/definitions/generate` call and one card.

#### 2.3. failure-shows-meaningful-error-and-adds-nothing
**File:** `frontend/e2e/custom-keyword-define.spec.ts` (NEW)

**Steps:**
  1. Hold a selection and activate `Define` with a `503`/error mock.
    - expect: the card shows a meaningful, user-safe error (`role="alert"`); no empty/placeholder
      definition paragraph is rendered; the reading content is unaffected.

### 3. Bounds feedback (FR-010..FR-012, SC-005)

**Seed:** `frontend/e2e/custom-keyword-define.spec.ts` (`setupDefinitionsMock` tracking call count)

#### 3.1. blank-selection-is-rejected-without-a-request
**File:** `frontend/e2e/custom-keyword-define.spec.ts` (NEW)

**Steps:**
  1. Produce a whitespace-only selection and hold it ~2s, then activate `Define` (if shown).
    - expect: a meaningful error is shown and **no** `POST /api/definitions/generate` is issued.

#### 3.2. over-ten-token-selection-is-rejected-without-a-request
**File:** `frontend/e2e/custom-keyword-define.spec.ts` (NEW)

**Steps:**
  1. Select an 11-token span and hold it ~2s, then activate `Define`.
    - expect: a meaningful error is shown and **no** request is issued.

#### 3.3. ten-token-selection-is-accepted
**File:** `frontend/e2e/custom-keyword-define.spec.ts` (NEW)

**Steps:**
  1. Select a 10-token span, hold ~2s, activate `Define` with a success mock.
    - expect: one request is issued with the normalized 10-token term and the card shows the
      definition.

### 4. Reachability & non-interference (US4, FR-003/FR-016..FR-018, SC-006)

**Seed:** `frontend/e2e/custom-keyword-define.spec.ts`

#### 4.1. programmatic-selection-shows-affordance
**File:** `frontend/e2e/custom-keyword-define.spec.ts` (NEW)

**Steps:**
  1. Create a selection programmatically via `document.getSelection()` over reading text.
  2. Wait ~2s.
    - expect: the `Define` affordance appears.

#### 4.2. existing-keyword-click-still-opens-the-card
**File:** `frontend/e2e/custom-keyword-define.spec.ts` (NEW)

**Steps:**
  1. Click an existing highlighted `.rich-text__keyword`.
    - expect: the existing definition card opens unchanged; no selection-Define flow is triggered.

#### 4.3. single-card-rule-preserved
**File:** `frontend/e2e/custom-keyword-define.spec.ts` (NEW)

**Steps:**
  1. Open the card for a highlighted keyword, then run a selection-Define for another term (or vice
     versa).
    - expect: exactly one `.keyword-card` is present and it shows the latest term/definition.

#### 4.4. results-and-history-offer-no-trigger
**File:** `frontend/e2e/custom-keyword-define.spec.ts` (NEW)

**Steps:**
  1. On the results recap, select text in `.recap-card__concept` and wait ~2s.
    - expect: no `Define` affordance.
  2. On the history surface, select text in `.history-snack__concept` and wait ~2s.
    - expect: no `Define` affordance; both surfaces render unchanged.

### 5. Security (US3, FR-013..FR-015, SC-004)

**Seed:** `frontend/e2e/custom-keyword-define.spec.ts` (`setupDefinitionsMock` returning payload
definitions; the selected term itself may be a payload)

#### 5.1. generated-definition-payloads-are-inert
**File:** `frontend/e2e/custom-keyword-define.spec.ts` (NEW)

**Steps:**
  1. Hold a selection and activate `Define` with a mock whose `definition` contains `<script>`,
     `<img onerror>`, `javascript:`, `<iframe>` and `<style>` payloads.
    - expect: `window.__xss` stays undefined, no `script/img/iframe/style/a` nodes exist inside
      `.keyword-card`, and the payload is shown as readable escaped text.

#### 5.2. selected-term-payload-is-inert
**File:** `frontend/e2e/custom-keyword-define.spec.ts` (NEW)

**Steps:**
  1. Select a term containing markup-like text and define it with a benign mock.
    - expect: the term is shown as escaped text in `.keyword-card__term`; nothing executes; no node is
      injected.

## Coverage Map

| Spec requirement | Scenario(s) | File |
| --- | --- | --- |
| FR-001 | 1.1, 1.2, 4.1 | `custom-keyword-define.spec.ts` |
| FR-002 | 1.3, 1.4 | `custom-keyword-define.spec.ts` |
| FR-003 | 1.1, 1.4, 1.5, 4.3 | `custom-keyword-define.spec.ts` |
| FR-004 (≤20-word bound) | Backend suites (`DefinitionServiceTest`, `DefinitionContractTest`, `GenerateDefinitionPromptTest`) | `backend/src/test/...` |
| FR-005 | Backend suites (port-only service; prompt externalized) | `backend/src/test/...` |
| FR-006 | 2.1 | `custom-keyword-define.spec.ts` |
| FR-007 | 2.2 | `custom-keyword-define.spec.ts` |
| FR-008 | 2.1 | `custom-keyword-define.spec.ts` |
| FR-009 | 2.3 | `custom-keyword-define.spec.ts` |
| FR-010 | 3.3 + backend suites (normalization) | `custom-keyword-define.spec.ts`, `backend/src/test/...` |
| FR-011 | 3.1, 3.2 + backend suites | `custom-keyword-define.spec.ts`, `backend/src/test/...` |
| FR-012 | Backend suites (whitespace tokenization) | `backend/src/test/...` |
| FR-013, FR-014, FR-015 | 5.1, 5.2 | `custom-keyword-define.spec.ts` |
| FR-016 | 1.1, 4.1 | `custom-keyword-define.spec.ts` |
| FR-017 | 4.4 | `custom-keyword-define.spec.ts` |
| FR-018 | 4.2, 4.3 + full existing suite | `custom-keyword-define.spec.ts`, `frontend/e2e/*.spec.ts` |
| FR-019 (Task 12 out of scope) | 2.1 (no "Generate snack" action on the custom card) | `custom-keyword-define.spec.ts` |
| SC-001 | 1.1–1.3 | `custom-keyword-define.spec.ts` |
| SC-002 | 2.1, 2.3 | `custom-keyword-define.spec.ts` |
| SC-003 | 2.2 | `custom-keyword-define.spec.ts` |
| SC-004 | 5.1, 5.2 | `custom-keyword-define.spec.ts` |
| SC-005 | 3.1–3.3 + backend suites | `custom-keyword-define.spec.ts`, `backend/src/test/...` |
| SC-006 | 4.1–4.4 | `custom-keyword-define.spec.ts` |
| SC-007 | full existing suite | `frontend/e2e/*.spec.ts`, `backend/src/test/...` |

## Out of E2E scope (covered by backend unit/contract tests)

- **FR-004 / FR-010 / FR-011 / FR-012**: the ≤10-token term bound, the ≤20-token definition bound,
  whitespace-collapse and hyphenated-token counting — hidden behind the mocked API; covered by
  `DefinitionServiceTest` (unit) and `DefinitionContractTest` (MockMvc).
- **FR-005**: provider-agnosticism and the externalized prompt — covered by
  `GenerateDefinitionPromptTest` and the `DefinitionServiceTest` port interaction.
- **Error shapes** (400/404/503 envelopes): covered by `DefinitionContractTest`.
- Task 12 ("Create snack" from the custom meaning): explicitly not built (FR-019 / Non-Goals).
