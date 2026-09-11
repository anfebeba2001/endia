# Batch Snack Generation Fix E2E Test Plan

## Application Overview

Feature `005-batch-snack-generation-fix` fixes the batch snack-generation hang, the detached-proxy
`LazyInitializationException`, the executor self-deadlock, and the frontend empty-state flash /
perpetual polling. The Angular 21 SPA (`http://localhost:4200`, proxying `/api` to the Spring Boot
backend on `:8080`) drives the flow home → subtopics → snacks → quiz → results. E2E coverage is
split between mocked specs (deterministic UI state) and a real-backend smoke check (executor +
persistence path).

The existing suite already covers the bounded-retry and polling-termination behaviour inherited
from `002`; the new `005` coverage targets the empty-state gating introduced by US3 (FR-009/010/011)
without regressing those specs.

## Test Scenarios

### 1. Empty-state gating (US3)

**Seed:** `tests/seed.spec.ts`

#### 1.1. no-empty-state-flash-before-first-poll

**File:** `tests/batch-empty-state.spec.ts` (`does not flash the empty state before the first poll response (FR-010)`)

**Steps:**
  1. Mock `POST /api/themes/suggest` with a single subtopic and `GET /api/batches/{id}` with the
     first poll delayed 2000 ms, then a `COMPLETE` status with one `READY` snack.
  2. Navigate home → subtopics → snacks (create returns 202 immediately).
  3. Before the first poll returns, assert `.empty-state` is hidden and `.loading.state` is visible.
  4. After the first poll returns, assert the `.snack-card` renders and `.empty-state` stays hidden.

#### 1.2. empty-state-suppressed-while-in-progress

**File:** `tests/batch-empty-state.spec.ts` (`suppresses the empty state while IN_PROGRESS with an empty item list (FR-009)`)

**Steps:**
  1. Set the bounded polling window to 5000 ms; mock every poll as `IN_PROGRESS`, `retrying: true`, `items: []`.
  2. Navigate to the snacks screen.
  3. Assert `.empty-state` is hidden and `.loading.state` is visible.
  4. Wait 2000 ms and re-assert `.empty-state` remains hidden while still in progress.

#### 1.3. empty-state-only-after-terminal-and-polling-stops

**File:** `tests/batch-empty-state.spec.ts` (`shows the empty state only for a terminal, zero-ready batch and stops polling (FR-011)`)

**Steps:**
  1. Mock every poll as `FAILED` with `items: []`.
  2. Navigate to the snacks screen.
  3. Assert `.empty-state` becomes visible only once the terminal status is observed.
  4. Record the poll count for the original batch, wait 3000 ms (> one 1500 ms interval), and assert
     no further `GET /api/batches/{id}` calls occurred (polling stopped on terminal).

### 2. Real-backend smoke (US1/US2/US3)

**Seed:** `tests/seed.spec.ts`

#### 2.1. live-batch-generates-snacks-and-settles

**File:** manual `playwright-cli` session against the running app (not committed as a spec — uses the real LLM).

**Steps:**
  1. Open `http://localhost:4200`, enter a theme, submit, and wait for `/subtopics`.
  2. Select a subset of subtopics and click "Generate snacks".
  3. Sample the DOM continuously from create until terminal: assert `.empty-state` never appears,
     `.loading.state` shows during creation/first poll, `.retry-spinner` reflects active generation,
     and ready `.snack-card`s render progressively.
  4. Confirm the batch reaches a terminal status, both snacks render, polling stops, and no console
     or backend detached-data errors occur.
