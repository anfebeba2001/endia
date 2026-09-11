---
description: "Task list for the topic-suggest-retry feature (preserve typed LLM cause so the frontend surfaces a meaningful error)"
---

# Tasks: Topic Suggest — Retry & Surface a Meaningful Error

**Input**: Design documents from `/specs/004-topic-suggest-retry/` (`plan.md` required, `spec.md` required, `research.md`, `contracts/theme-suggest-error-envelope.md`)

**Branch**: `004-topic-suggest-retry`

## Organization

This is a **small, focused** feature. Per `plan.md`, the backend **already retries** transient LLM failures (`RetryingLlmProvider`, 3 total attempts) and the frontend **already maps** the 5 causes to specific messages. The **only behavioral change** is in `ThemeService.suggestSubtopics`: stop discarding the typed `LlmGenerationException` so the existing `GlobalExceptionHandler` emits the correct status + a **non-null `cause`**. Everything else is verification/doc.

Because of that, this task list is deliberately lean (6 tasks) and uses the Setup / Core / Tests / Polish phase structure. No new retry mechanism is added (FR-007: single, bounded, backend-owned mechanism).

Each task carries the FR(s) it satisfies for traceability to `spec.md` (FR-001..FR-009).

Base paths used below (verified against the repo):

- Backend: `backend/src/main/java/com/endia/`
- Backend tests: `backend/src/test/java/com/endia/`
- Frontend: `frontend/src/app/`
- Specs: `specs/004-topic-suggest-retry/`

---

## Phase 1: Setup (verify & document baseline — no code change)

**Purpose**: Confirm the environment is green and the already-produced design/doc artifacts exist before any change. **No behavior change.**

- [X] T001 Verify the documentation & retry baseline (FR-001, FR-009, SC-001, SC-004):
  - Confirm `specs/004-topic-suggest-retry/research.md` documents the retry config (`batch.retry.*`: max-attempts 3, initial-backoff-ms 1000, backoff-factor 2.0, max-backoff-ms 10000, jitter-ratio 0.2) and the cause→message vocabulary — **exists, verify only, do not regenerate**.
  - Confirm `specs/004-topic-suggest-retry/contracts/theme-suggest-error-envelope.md` documents the non-null `cause` envelope — **exists, verify only, do not regenerate**.
  - Confirm `backend/src/test/java/com/endia/infrastructure/llm/RetryingLlmProviderTest.java` already covers "retries then succeeds within 3" and "exhausts after 3" (FR-001/FR-009/SC-004) — **verify only, no change expected**.
  - Confirm the backend baseline passes (`cd backend && mvn -q test` green) **before any change**.

**Checkpoint**: Baseline green and artifacts present — implementation can begin.

---

## Phase 2: Core (the single behavioral fix)

**Purpose**: Make the typed LLM failure propagate so its cause is preserved end-to-end (FR-003/FR-004/FR-005, SC-002/SC-003). This is the only production-code change.

- [X] T002 Modify `backend/src/main/java/com/endia/application/service/ThemeService.java` — insert `catch (LlmGenerationException e) { throw e; }` **before** the existing `catch (ServiceUnavailableException e)` in `suggestSubtopics`, so an `LlmGenerationException` (carrying `LlmErrorCause` + retryability flag) rethrows unchanged instead of falling into the generic `catch (Exception e)` and being replaced by a null-cause `ServiceUnavailableException`. Add the `import com.endia.domain.exception.LlmGenerationException;`.
  - The generic `catch (Exception e)` fallback (non-LLM/unexpected errors → `ServiceUnavailableException`) stays as-is.
  - Keep `catch (ServiceUnavailableException e) { throw e; }`.
  - Depends on T001.

**Note (no change needed)**: `GlobalExceptionHandler`, `ThemeController`, `RetryingLlmProvider`, `LlmConfig`, `LlmErrorCause`, `error.service.ts`, `home.component.ts` are all verified correct and **unchanged** — see T004.

---

## Phase 3: Tests (behavioral regression for the fix)

**Purpose**: Lock in the new propagation behavior and guard the existing fallback (FR-003/FR-004/FR-005, SC-003, spec edge case).

- [X] T003 Extend `backend/src/test/java/com/endia/application/service/ThemeServiceTest.java`:
  - **Add** a test that when `llmProvider.suggestSubtopics(...)` throws a typed `LlmGenerationException` (e.g. `cause = UNAVAILABLE`, retryable), `themeService.suggestSubtopics(...)` propagates it **unchanged** — assert the thrown exception is the **same type** (`LlmGenerationException`) and `getCauseType()` is preserved (NOT wrapped into `ServiceUnavailableException`).
  - **Adjust** the existing `suggestSubtopicsThrowsServiceUnavailableWhenLlmFails` test (currently a plain `RuntimeException`) to reflect that a **non-LLM** `RuntimeException` still maps to `ServiceUnavailableException` — rename/assert accordingly so it clearly documents the spec edge case (non-typed infra errors keep the generic fallback).
  - Depends on T002.

---

## Phase 4: Polish (verification & acceptance — no code change)

**Purpose**: Confirm the fix produces the intended end-to-end behavior and the manual/reproducible acceptance criteria hold (FR-005/FR-006/FR-007/FR-008, SC-002/SC-005).

- [X] T004 Verify the full error path needs no further code change (FR-005, FR-006, FR-007, SC-002, SC-003):
  - Confirm `backend/src/main/java/com/endia/infrastructure/exception/GlobalExceptionHandler.java` `handleLlmGeneration` emits the correct status (`QUOTA`→429, `MALFORMED`→400, else 503) and `cause = causeType.name()` (non-null) — **verify only, no change**.
  - Confirm `frontend/src/app/services/error.service.ts` maps all 5 causes (`UNAVAILABLE`/`TIMEOUT`/`NETWORK`/`QUOTA`/`MALFORMED`) via `fromCause` and falls back to `message` then status text — **verify only, no change**.
  - Confirm `frontend/src/app/pages/home/home.component.ts` renders `errorService.getMessage(err)` with **no** overlapping frontend retry — **verify only, no change** (FR-007 single backend mechanism).
  - Depends on T003.

- [ ] T005 Manual verification (FR-008, SC-005): reproduce the temporary `SERVICE_UNAVAILABLE` condition on the topic-suggestion path and confirm (a) retries occur (up to 3 total attempts — observe the attempt count/timing, e.g. via logs) and (b) after all attempts are exhausted a **meaningful, cause-specific** error surfaces (non-null `cause`, not the generic message). Also force a recovery after attempt 1 or 2 and confirm the suggested topics appear with **no error**. Depends on T003.
  - Optional supporting check: force each cause category and confirm a distinct cause-specific message (SC-002).

- [X] T006 Run the full backend test suite to confirm no regression: `cd backend && mvn -q test` green. Depends on T003, T005 (manual is best-effort; the automated suite is the gating check).

**Checkpoint**: Fix landed, tests green, manual scenario verified — feature complete.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001)**: No dependencies — runs first (pre-flight baseline + doc verification).
- **Core (T002)**: Depends on Setup completion — the only production edit.
- **Tests (T003)**: Depends on Core — must come after the fix exists.
- **Polish (T004)**: Depends on Tests — verify the path end-to-end.
- **Polish (T005, T006)**: Depends on Tests (and T005 best-effort) — final verification/acceptance.

### Parallel opportunities

- T004 (verification docs/frontend/handler) is independent file-read/verification and could run alongside T003 if desired, but depends on T002 to be meaningful. No `[P]` tasks required on this small feature.

---

## Summary

- **Total tasks: 6**
- Phase breakdown:
  - Setup (T001): **1**
  - Core (T002): **1**
  - Tests (T003): **1**
  - Polish (T004, T005, T006): **3**

## Notes

- `[P]` markers omitted — this feature has an inherent serial dependency chain (Setup → Core → Tests → Polish); no tasks are independently parallelizable without losing meaning.
- Every task includes the exact file path (create/change/verify) and is executable in isolation with a clear done-definition.
- Doc artifacts (`research.md`, `contracts/theme-suggest-error-envelope.md`) and `RetryingLlmProviderTest` are **verify-only** — they were produced by the planner; do not regenerate or modify them.

## FR Traceability

| FR | Task(s) |
|----|---------|
| FR-001 (retry up to 3 total) | T001, T006 (existing `RetryingLlmProvider` verify-only) |
| FR-002 (transparent success after retry) | T005 (existing mechanism, verify-only) |
| FR-003 (specific message, not generic) | T002, T003, T004 |
| FR-004 (typed cause not discarded / non-null) | T002, T003, T004 |
| FR-005 (API non-null cause) | T002, T003, T004 |
| FR-006 (frontend cause-specific message) | T004 (verify-only) |
| FR-007 (single backend retry, no frontend retry) | T004 (verify-only) |
| FR-008 (reproducible manual test) | T005 |
| FR-009 (observable/configurable retry documented) | T001, T006 (verify-only) |
