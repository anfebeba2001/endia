# Implementation Plan: Retry Topic Suggestion on Transient Service Unavailability & Surface a Meaningful Error

**Branch**: `004-topic-suggest-retry` | **Date**: 2026-09-09 | **Spec**: [`spec.md`](spec.md)

**Input**: Feature specification from `/specs/004-topic-suggest-retry/spec.md`

## Summary

The topic-suggestion path (`POST /api/themes/suggest`, landing page "suggest a theme") currently collapses every failure into a generic `ServiceUnavailableException` whose `cause` is `null`, so the UI always shows *"The AI service is temporarily unavailable. Please try again later."* even when a typed, retryable LLM failure occurred.

The backend **already retries** transient LLM failures (`RetryingLlmProvider` decorates the `LlmProvider` bean as `@Primary`, retrying `suggestSubtopics` up to 3 total attempts with exponential backoff + jitter). The **only** change of behavior is to **stop discarding the typed cause**: on the `ThemeService.suggestSubtopics` path, let the typed `LlmGenerationException` (carrying `LlmErrorCause` and a retryability flag) propagate instead of wrapping it into a null-cause `ServiceUnavailableException`. That single change lets the existing `GlobalExceptionHandler.handleLlmGeneration` emit the correct HTTP status and a **non-null `cause`**, which the existing frontend `ErrorService` already maps to a cause-specific, user-friendly message.

Because `RetryingLlmProvider` rethrows the final typed exception only after the retry budget is exhausted, and because `GlobalExceptionHandler` + frontend `ErrorService` already do the right thing once the cause survives, **this feature is effectively a one-file backend fix plus verification/doc artifacts**. No new retry mechanism is added (FR-007: single, bounded, backend-owned mechanism).

## Technical Context

**Project Type**: Full-stack monorepo — Spring Boot backend + Angular frontend
**Language/Version**: Java 21 (backend), TypeScript 5.x / Angular 21 (frontend)
**Primary Dependencies**: Spring Boot (Web, Data JPA, Validation), Spring `RestClient` for OpenAI-style LLM calls; Angular (standalone components, `HttpClient`, signals)
**Storage**: MySQL 8.x via JPA repositories (schema unchanged by this feature)
**Testing**: JUnit 5 + Mockito + Testcontainers (backend); no frontend tests change expected
**Target Platform**: Linux server (backend) / modern browser (frontend)
**Performance Goals**: Bounded retry budget (3 total attempts, no infinite loop). Worst-case additional latency on persistent failure ≈ backoff chain (~1000ms + ~2000ms + jitter); ≤ 10s max backoff per attempt.
**Constraints**: Must not multiply LLM call budget; must not leak stack traces/provider payloads; must not change the API contract shape beyond ensuring `cause` is populated for typed LLM failures.
**Scale/Scope**: Single endpoint path, single behavior change.

## Root Cause (verified in code)

`ThemeService.suggestSubtopics` (`backend/src/main/java/com/endia/application/service/ThemeService.java`, lines 30–39):

```java
try {
    names = llmProvider.suggestSubtopics(prompt);
} catch (ServiceUnavailableException e) {
    throw e;
} catch (Exception e) {
    throw new ServiceUnavailableException("The AI service is temporarily unavailable. Please try again later.", e);
}
```

`LlmGenerationException extends DomainException` and is **not** a `ServiceUnavailableException`, so it falls into `catch (Exception e)` and is replaced by a `ServiceUnavailableException`. Although the wrapper `initCause(e)` keeps the cause on the exception object, `GlobalExceptionHandler.handleDomain` serializes a **`cause: null`** (it only emits `cause` for `LlmGenerationException`). Result: typed cause discarded end-to-end → generic message.

## Constitution Check

- **Single bounded mechanism / no duplicated retry**: satisfied — this feature does not add a frontend retry; the existing `RetryingLlmProvider` is the sole mechanism (FR-007).
- **No security/leak regression**: satisfied — rethrowing `LlmGenerationException` leaks only the cause category + message, not stack traces or provider payloads.
- **Backward compatibility**: satisfied — the API envelope shape (error/message/field/cause) and the success `200` body are unchanged; only the `cause` field changes from `null` to a real value for typed LLM failures on this path.
- **No schema/data-model change**: satisfied — no new columns/tables.

No constitution violations requiring Complexity Tracking.

## Proposed Change (minimal fix)

**Single backend file changed**: `backend/src/main/java/com/endia/application/service/ThemeService.java`.

Insert a rethrow for the typed exception **before** the generic catch:

```java
try {
    names = llmProvider.suggestSubtopics(prompt);
} catch (LlmGenerationException e) {
    throw e;                       // NEW: preserve typed cause -> GlobalExceptionHandler emits cause + status
} catch (ServiceUnavailableException e) {
    throw e;
} catch (Exception e) {
    throw new ServiceUnavailableException("The AI service is temporarily unavailable. Please try again later.", e);
}
```

Rationale:
- `RetryingLlmProvider.suggestSubtopics` already runs 3 attempts and rethrows the final typed `LlmGenerationException` on exhaustion (verified). Non-retryable causes are rethrown immediately (no fruitless retry), matching the spec edge case.
- `GlobalExceptionHandler.handleLlmGeneration` maps `LlmGenerationException` → correct status (`QUOTA`→429, `MALFORMED`→400, else 503) and `cause: causeType.name()` — no handler change needed.
- The generic `catch (Exception e)` remains as the fallback for genuinely unexpected, non-LLM errors (spec edge case), so it never masks a typed LLM failure but still covers non-typed infra errors.

### Why the controller needs no change

`ThemeController.suggest` (`backend/.../interfaces/rest/ThemeController.java`) only delegates to `themeService.suggestSubtopics` and builds the `200` success body. Exception propagation is handled entirely by `@RestControllerAdvice`; the controller performs no exception handling of its own. The success contract (`ThemeSuggestApiResponse`) and the 200 body are unchanged. The contract stays stable.

### Why the frontend needs no change

- `ErrorService.getMessage` (`frontend/src/app/services/error.service.ts`) **already** prioritizes `body.cause` → `fromCause(...)` mapping for `UNAVAILABLE`, `TIMEOUT`, `NETWORK`, `QUOTA`, `MALFORMED`, falling back to `body.message` then status text (FR-006 satisfied).
- `HomeComponent` (`frontend/src/app/pages/home/home.component.ts`, line 62) already renders `errorService.getMessage(err)`; no second/overlapping retry exists (FR-007 satisfied).
- `ErrorResponse` model already has `cause?: string` (`frontend/.../models/api-response.model.ts`).

**No frontend code change is required.** The only possibly-desirable (non-blocking) change is a doc note; it is omitted to keep the change minimal and within the feature. If during implementation the team wants an explicit regression test, that is a test-only addition (see Testing Strategy).

## Project Structure

### Documentation (this feature)

```text
specs/004-topic-suggest-retry/
├── plan.md                              # This file
├── research.md                          # Retry config + cause->message vocabulary
├── contracts/
│   └── theme-suggest-error-envelope.md  # Error envelope showing non-null cause on LLM failure
└── spec.md                              # Existing feature spec (input)
```

### Source Code (repository root) — files changed/consulted

```text
backend/
├── src/main/java/com/endia/
│   ├── application/service/ThemeService.java          # CHANGED (the fix)
│   ├── domain/
│   │   ├── exception/LlmGenerationException.java       # unchanged (typed cause carrier)
│   │   ├── exception/ServiceUnavailableException.java  # unchanged
│   │   ├── exception/DomainException.java              # unchanged
│   │   └── enums/LlmErrorCause.java                    # unchanged (vocabulary)
│   ├── infrastructure/
│   │   ├── exception/GlobalExceptionHandler.java       # unchanged (already emits cause)
│   │   └── llm/
│   │       ├── RetryingLlmProvider.java                # unchanged (retry mechanism)
│   │       └── LlmConfig.java                          # unchanged (retry config)
│   └── interfaces/rest/ThemeController.java            # unchanged (contract stable)
└── src/test/java/com/endia/
    ├── application/service/ThemeServiceTest.java       # ADD assertion (see tests)
    └── infrastructure/llm/RetryingLlmProviderTest.java # unchanged (already verifies 3x)

frontend/src/app/
├── services/error.service.ts            # unchanged (already maps causes)
├── pages/home/home.component.ts         # unchanged (already renders cause message)
└── models/api-response.model.ts         # unchanged (already has cause)
```

**Structure Decision**: Follow the classic layered architecture (domain / application / infrastructure / interfaces) already in place. The change is confined to the application layer (`ThemeService`); domain exception types, infrastructure retry/decorator, exception handler, and interfaces remain untouched. This keeps the fix minimal and the behavior change traceable to one location.

## Milestones / Phases

### Phase 0 — Verify & Document (setup, no code behavior change)

1. Confirm current state: run existing unit tests (`ThemeServiceTest`, `RetryingLlmProviderTest`) — baseline green.
2. Document the retry config and cause→message vocabulary in `research.md`.
3. Write `contracts/theme-suggest-error-envelope.md` aligning with the canonical `specs/001-batch-mvp-enhancements/contracts/error-response.md`.

### Phase 1 — Implement the fix (behavior change)

4. Edit `ThemeService.suggestSubtopics` to rethrow `LlmGenerationException` before the generic catch.
5. Add/extend `ThemeServiceTest`:
   - Assert a `LlmGenerationException(UNAVAILABLE, ...)` from the provider propagates **unchanged** (same exception type and `getCauseType()`), instead of being wrapped.
   - Assert the existing `RuntimeException → ServiceUnavailableException` fallback still holds (non-LLM unexpected errors keep generic behavior).
6. Optional regression assertion on the full path: verify `GlobalExceptionHandler.handleLlmGeneration` produces a non-null `cause` (`= LlmErrorCause.name()`).

### Phase 2 — Verifiability & Acceptance (FR-008/FR-009)

7. Confirm/verify `RetryingLlmProviderTest` covers "retries then succeeds within 3" and "exhausts after 3" (already present — no change expected; verify only).
8. Verify the frontend maps each of the 5 causes (already present in `error.service.ts`; verify only, no code change).
9. Manual verification script: force `UNAVAILABLE`/5xx to persist, observe 3 attempts, then a cause-specific message; force a recovery after attempt 1 or 2, observe topics shown with no error.

### Effort ordering

Low — small, focused. Phase 0 is documentation-heavy; Phase 1 is the single code edit + test; Phase 2 is verification. No migration, no schema change, no frontend build change.

## Testing Strategy

- **Unit (`ThemeServiceTest`)**: the primary behavioral regression. The new test asserts typed `LlmGenerationException` preservation (FR-004, FR-005, SC-003). Keeps the non-typed fallback test (spec edge case).
- **Unit (`RetryingLlmProviderTest`)**: already covers 3-total-attempt bound and non-retryable passthrough (FR-001, FR-009, SC-004, edge case). Verify only.
- **Integration**: none required — no persistence/transactional behavior changes; retry is infra-decorator behavior already unit-tested. (Optional: a `@WebMvcTest`/slice asserting the JSON `cause` for a `LlmGenerationException`.)
- **Frontend**: no change → no test change. Verify `ErrorService` handles all causes (already unit-mappable). Document expected cause-specific messages in `research.md`.
- **Manual (FR-008, SC-005)**: reproducible manual test forcing temporary SERVICE_UNAVAILABLE confirming (a) retries occur up to 3 total and (b) a meaningful, cause-specific error surfaces on persistent failure.

## Traceability (Acceptance Criteria → Plan Item)

| Criteria | Plan Item |
|----------|-----------|
| FR-001 / SC-004 (bounded 3 total, no infinite loop) | Existing `RetryingLlmProvider` (verify-only, Phase 0/2) |
| FR-002 (transparent success after retry) | Existing `RetryingLlmProvider` + `ThemeService` success path unchanged (verify-only) |
| FR-003 (specific message, not generic) | Phase 1 fix + `error.service.ts` mapping (verify-only) |
| FR-004 / FR-005 (typed cause non-null end-to-end) | Phase 1 fix in `ThemeService` + `GlobalExceptionHandler` (unchanged) |
| FR-006 (frontend cause-specific message, fallback generic) | `error.service.ts` + `home.component.ts` verify-only |
| FR-007 (single backend retry, no frontend retry) | Design decision — no frontend retry added |
| FR-008 / SC-005 (reproducible manual test) | Phase 2 step 9 |
| FR-009 (observable/configurable retry documented) | `research.md` + `LlmConfig`/`application.yml` verify-only |

## Assumptions & Tradeoffs

- **Assumption**: rethrowing `LlmGenerationException` is the correct, minimal fix; the typed exception's own `message` (e.g. "The AI service is temporarily unavailable. Please try again shortly.") is acceptable to present, and the frontend's cause-mapped message takes precedence anyway (it does, per `ErrorService.getMessage`).
- **Assumption**: `RetryingLlmProvider` is already wired `@Primary` and covering `suggestSubtopics` — verified in `LlmConfig`; no re-wiring needed.
- **Rejected alternative — frontend retry**: would multiply the LLM call budget beyond the documented 3 total attempts and violate FR-007; rejected.
- **Rejected alternative — changing `GlobalExceptionHandler.handleDomain`** to emit cause from a wrapped exception: not needed once the wrapper no longer masks the typed exception, and is less surgical than the provenance at `ThemeService`.
- **Rejected alternative — adding a new exception type / new `cause` vocabulary**: the existing `LlmErrorCause` and envelope already cover all 5 causes; adding vocabulary is out of scope and unnecessary.
- **Rejected alternative — modifying `ThemeController`**: no exception handling there; unnecessary churn.
