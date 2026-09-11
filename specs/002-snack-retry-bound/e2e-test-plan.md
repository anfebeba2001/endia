# Bounded Snack Reattempts and Polling Termination — E2E Test Plan

**Feature**: `002-snack-retry-bound` (US1–US4, FR-001..016, SC-001..008)
**Runner**: Playwright (`frontend/e2e/`), deterministic — all backend/LLM traffic is mocked.
**Seed**: `frontend/e2e/helpers.ts` (`goToSnacksPage` navigates home → subtopics → snacks; `setupConfigurableBatchMock` mocks `POST/GET /api/batches` and counts cycles).

## Application Overview

The snacks page creates a batch (`POST /api/batches`), polls its status (`GET /api/batches/{id}`) on a ~1.5 s interval, and renders ready snacks one at a time. Feature 002 fixes the reported infinite retry loop by (a) treating every batch status other than `IN_PROGRESS` as terminal (so `PARTIAL` stops polling) and bounding the total polling lifetime (`MAX_POLLING_WINDOW_MS`, default 120 s), and (b) bounding automatic per-target reattempts to 3 user-visible cycles, triggered only by an explicit failure (failed item / typed cause / failed create request), with an in-flight guard. After exhaustion a terminal error is shown and a manual Retry resets the budget and starts a fresh cycle. All E2E scenarios mock the batch endpoints; no LLM/backend is contacted.

## Coverage Matrix (scenario → US / SC / FR)

| # | Scenario | File | US | SC | FR |
|---|----------|------|----|----|----|
| 1.1 | stops polling on PARTIAL | `polling-termination.spec.ts` | US2 | SC-003 | FR-008/009 |
| 1.2 | stops polling on COMPLETE | `polling-termination.spec.ts` | US2 | SC-003 | FR-008/009 |
| 1.3 | stops polling on FAILED | `polling-termination.spec.ts` | US2 | SC-003 | FR-008/009 |
| 1.4 | stuck IN_PROGRESS terminates with terminal error on window elapse | `polling-termination.spec.ts` | US2 | SC-004 | FR-010 |
| 2.1 | exactly 3 attempts, terminal error, no 4th | `retry-bound.spec.ts` | US1 | SC-001/002 | FR-001/002/005 |
| 2.2 | **(new)** succeeds on a later attempt within the budget, stops retrying | `retry-bound.spec.ts` | US1 | SC-001 | FR-001/002/005 |
| 3.1 | no reattempt while IN_PROGRESS; one on explicit failure; no overlap | `retry-explicit-error.spec.ts` | US3 | SC-005 | FR-003/004/007 |
| 3.2 | **(new)** failed create request is explicit, bounded to 3, no timer retry | `retry-explicit-error.spec.ts` | US3 | SC-001/005 | FR-003/004/007 |
| 4.1 | manual retry starts one cycle, clears error, renders snack | `manual-retry.spec.ts` | US4 | SC-006 | FR-006 |
| 4.2 | rapid double-click starts at most one cycle | `manual-retry.spec.ts` | US4 | SC-006 | FR-006/007 |
| 4.3 | **(new)** manual retry resets budget to a fresh 3 automatic attempts | `manual-retry.spec.ts` | US4 | SC-006 | FR-006 |
| R.1 | regression: 22 pre-existing specs (happy path, progressive delivery, drag-drop, etc.) | `*.spec.ts` | — | SC-007 | FR-012 |

Gaps identified in the implementer's original 8 specs and now closed: **2.2** (fail-then-succeed within budget), **3.2** (create-request failure bound), **4.3** (fresh 3-attempt budget after manual reset — the original only verified a successful single cycle).

## Test Scenarios

### 1. Polling Termination (US2)
**Seed:** `frontend/e2e/helpers.ts`

#### 1.1. stops-polling-on-partial
**File:** `frontend/e2e/polling-termination.spec.ts`
**Steps:**
1. Mock a two-subtopic batch that settles as `PARTIAL` (one ready snack, one failed quiz).
   - expect: the ready snack card renders and no further `GET /api/batches/{id}` occurs after the terminal `PARTIAL` payload.
2. Wait beyond one poll interval (~3.5 s).
   - expect: the poll count for the original batch is unchanged.

#### 1.2. stops-polling-on-complete / 1.3. stops-polling-on-failed
**File:** `frontend/e2e/polling-termination.spec.ts`
**Steps:**
1. Mock the original batch to settle `COMPLETE` (1.2) / `FAILED` (1.3).
   - expect: the observable completes and the poll count stops increasing.

#### 1.4. stuck-in-progress-times-out
**File:** `frontend/e2e/polling-termination.spec.ts`
**Steps:**
1. Override `window.__MAX_POLLING_WINDOW_MS__` to 1000 ms via `setPollingWindow`, mock a batch that stays `IN_PROGRESS`.
   - expect: a terminal `.error-banner` appears with a timeout message, and the poll count stops increasing.

### 2. Bounded Automatic Reattempts (US1)
**Seed:** `frontend/e2e/helpers.ts`

#### 2.1. exactly-3-attempts-then-terminal
**File:** `frontend/e2e/retry-bound.spec.ts`
**Steps:**
1. Mock every batch to `FAILED` with a `QUOTA` snack cause.
   - expect: the terminal `.error-banner` names the cause and "3 attempts"; exactly 3 `POST /api/batches`; no 4th after 4 s; `.retry-spinner` hidden.

#### 2.2. succeeds-within-budget
**File:** `frontend/e2e/retry-bound.spec.ts`
**Steps:**
1. Mock the original batch `FAILED`; mock the first retry batch `COMPLETE` with a ready snack.
   - expect: the snack card renders, no terminal error remains, exactly 2 create cycles, and no further cycle after 4 s.

### 3. Explicit-Error Precondition (US3)
**Seed:** `frontend/e2e/helpers.ts`

#### 3.1. no-reattempt-while-in-progress
**File:** `frontend/e2e/retry-explicit-error.spec.ts`
**Steps:**
1. Hold a two-subtopic batch `IN_PROGRESS` with no failed item; wait 4.5 s.
   - expect: exactly 1 create cycle (no blind-timer retry).
2. Inject a failed item for one target.
   - expect: exactly 1 new single-target cycle; the in-flight guard prevents an overlapping cycle while the original batch still reports the stale failure.

#### 3.2. create-failure-is-bounded
**File:** `frontend/e2e/retry-explicit-error.spec.ts`
**Steps:**
1. Allow the initial create to succeed; make every reattempt create fail (HTTP 500); poll returns `FAILED`.
   - expect: exactly 3 create attempts total (1 success + 2 failed), a terminal "after 3 attempts" error, and no further create after 4 s.

### 4. Manual Retry (US4)
**Seed:** `frontend/e2e/helpers.ts`

#### 4.1. manual-retry-succeeds
**File:** `frontend/e2e/manual-retry.spec.ts`
**Steps:**
1. Exhaust the budget (3 failures), assert the Retry affordance is visible, then click Retry with the next cycle mocked `COMPLETE`.
   - expect: exactly one new cycle, snack rendered, terminal error cleared.

#### 4.2. rapid-double-click
**File:** `frontend/e2e/manual-retry.spec.ts`
**Steps:**
1. Exhaust the budget, click Retry twice rapidly while the create is delayed.
   - expect: at most one new cycle (4 create calls total).

#### 4.3. manual-retry-resets-budget
**File:** `frontend/e2e/manual-retry.spec.ts`
**Steps:**
1. Exhaust the budget (3 failures) with an always-failing batch.
   - expect: terminal "after 3 attempts" error and 3 create cycles.
2. Click Retry; keep the batch failing.
   - expect: a fresh 3-attempt cycle runs (6 create cycles total), the terminal error returns, and no 7th cycle occurs.

## Execution

```bash
cd frontend
npx playwright test                 # full suite (webServer: ng serve on :4200, reuseExistingServer)
npx playwright test e2e/retry-bound.spec.ts e2e/retry-explicit-error.spec.ts e2e/manual-retry.spec.ts
```

## Notes / Assumptions

- Tests are deterministic: `setupConfigurableBatchMock` records `POST`/`GET` counts; no real network/LLM is used.
- The polling window is injected through the documented `window.__MAX_POLLING_WINDOW_MS__` seam so the 120 s default is never waited.
- `retrying` (FR-011) backend derivation is covered by backend unit tests (`BatchServiceTest`); the E2E specs assert the user-visible spinner behaviour.
- FR-015 (no sensitive data) is a message-content audit, not an E2E interaction.
