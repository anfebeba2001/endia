# Accurate Step-by-Step Snack Waiting Messages E2E Test Plan

## Application Overview

Feature `008-snack-wait-messages` replaces the single generic `Preparing your snack…` text shown
while a batch item is generating with a distinct, plain-language message per **real** backend
generation phase (`RESOLVING_CONTEXT`, `AWAITING_MODEL`, `ASSEMBLING`, `PERSISTING`). The Angular 21
SPA (`http://localhost:4200`, proxying `/api` to the Spring Boot backend on `:8080`) drives the flow
home → subtopics → snacks. The phase is exposed as a nullable `snackPhase` on each `GET
/api/batches/{batchId}` item, present only while the item is `GENERATING`; the frontend maps the
static token to static copy in `models/batch.model.ts` and renders it in the per-item in-progress
branch. The `frontend/e2e/helpers.ts` seam (`BatchMockItem.snackPhase`, `batchItemGenerating(id,
phase?)`) lets the mock drive each phase deterministically.

Because the UI renders only the current snack item, per-item isolation is verified by asserting the
rendered (current) item shows its own phase and the other item's phase text is absent from the DOM.
Settled (`READY`/`FAILED`) items, empty-state gating, bounded polling, and retry behavior are
covered by the feature spec plus the existing `002`/`005` suites.

## Test Scenarios

### 1. Per-phase accurate messages (US1/US2)

**Seed:** `tests/seed.spec.ts` (implemented inline via `goToSnacksPage`)

#### 1.1. renders-each-phase-specific-message-across-polls

**File:** `e2e/snack-wait-messages.spec.ts` (`renders each phase-specific message across successive polls (FR-005/FR-007/SC-002)`)

**Steps:**
  1. Mock one subtopic; mock `GET /api/batches/{id}` to return a `GENERATING` item whose `snackPhase`
     advances through the four phases across successive polls (`poll 1..4`).
  2. Navigate to the snacks screen.
  3. Sample the in-progress text continuously; capture distinct exact mapped messages in order.
  4. Assert the captured sequence equals `[Finding the topic context…, Waiting for the AI
     response…, Assembling your snack…, Saving your snack…]`.

#### 1.2. replaces-generic-preparing-message-once-phase-reported

**File:** `e2e/snack-wait-messages.spec.ts` (`replaces the generic preparing message once a phase is reported (FR-005)`)

**Steps:**
  1. Mock one subtopic; mock a `GENERATING` item with `snackPhase = AWAITING_MODEL`.
  2. Navigate to the snacks screen.
  3. Assert the in-progress text is exactly `Waiting for the AI response…`.
  4. Assert no `.loading.state p` contains the generic `Preparing your snack…`.

#### 1.3. per-item-isolation

**File:** `e2e/snack-wait-messages.spec.ts` (`tracks the phase per item; one item never shows another item’s phase (FR-008)`)

**Steps:**
  1. Mock two subtopics; item 0 `GENERATING` with `AWAITING_MODEL`, item 1 `GENERATING` with
     `ASSEMBLING`.
  2. Navigate to the snacks screen.
  3. Assert the rendered in-progress text is exactly `Waiting for the AI response…`.
  4. Assert no in-progress text matches the other item's `Assembling your snack…` (no bleed).

### 2. Settled items show no phase message (US3)

**Seed:** `tests/seed.spec.ts`

#### 2.1. ready-item-shows-no-phase-message

**File:** `e2e/snack-wait-messages.spec.ts` (`shows no phase message for a READY item (FR-010/SC-004)`)

**Steps:**
  1. Mock one subtopic; `COMPLETE` status with a `READY` item.
  2. Navigate to the snacks screen and wait for the `.snack-card`.
  3. Assert no `.loading.state p` exists and no phase message text is present.

#### 2.2. failed-item-shows-no-phase-message

**File:** `e2e/snack-wait-messages.spec.ts` (`shows no phase message for a FAILED item (error banner instead) (FR-010/SC-004)`)

**Steps:**
  1. Mock one subtopic; `FAILED` status with a failed item.
  2. Navigate to the snacks screen and wait for the `.error-banner`.
  3. Assert no phase message text is present.

### 3. Retry re-entry (US3)

**Seed:** `tests/seed.spec.ts`

#### 3.1. retried-item-re-enters-from-context-phase

**File:** `e2e/snack-wait-messages.spec.ts` (`a retried item re-enters the phases from RESOLVING_CONTEXT (FR-009)`)

**Steps:**
  1. Mock one subtopic; the first batch (`test-batch-001`) returns `FAILED`, the retry batch returns
     `GENERATING` with the phases advancing from `RESOLVING_CONTEXT`.
  2. Navigate to the snacks screen.
  3. Assert the retried item's in-progress text becomes `Finding the topic context…`.

### 4. No regression (US3)

**Seed:** existing suite

#### 4.1. existing-batch-empty-state-polling-retry-suites

**File:** `e2e/batch-empty-state.spec.ts`, `e2e/polling-termination.spec.ts`, `e2e/retry-bound.spec.ts`, `e2e/progressive-delivery.spec.ts`, `e2e/loading-states.spec.ts`, and the rest of `e2e/`.

**Steps:**
  1. Run the full Playwright suite.
  2. Assert every existing spec stays green (SC-005): empty-state gating, bounded polling, retry
     bound, progressive delivery, and loading states unchanged.
