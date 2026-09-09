# Feature Specification: Retry Topic Suggestion on Transient Service Unavailability & Surface a Meaningful Error

**Feature Folder**: `specs/004-topic-suggest-retry/`

**Created**: 2026-09-09

**Status**: Draft

**Input**: User description: "Retry topic-suggestion on transient SERVICE_UNAVAILABLE and surface a meaningful error. The landing page that suggests study topics via an LLM sometimes shows the generic error 'The AI service is temporarily unavailable. Please try again later.' instead of topic suggestions. Desired: transient LLM failures are automatically retried (up to 2 more attempts, 3 total) before giving up; on a transient failure that eventually succeeds the user sees the topics as if the first attempt had succeeded; after all retries are exhausted the user sees a specific, meaningful error reflecting the actual failure (e.g. quota / timeout / network / malformed) rather than the generic message; and a manual test reproduces the temporary SERVICE_UNAVAILABLE condition and confirms retries occur and a meaningful error surfaces on persistent failure."

## Problem Statement

On the landing (home) page the user enters a theme and the app asks an LLM to suggest study topics. When the LLM is transiently unavailable, the user currently sees the generic message **"The AI service is temporarily unavailable. Please try again later."** instead of topic suggestions — even when the failure is recoverable and a retry would have succeeded.

The root cause of the *generic, unhelpful* message is a loss of typed failure information on the topic-suggestion path:

- The LLM provider layer already throws a **typed** exception (`LlmGenerationException`) carrying a machine-readable cause category (`QUOTA`, `TIMEOUT`, `NETWORK`, `UNAVAILABLE`, `MALFORMED`) and a retryability flag.
- The `ThemeService.suggestSubtopics(...)` path currently catches any exception and replaces it with a non-typed `ServiceUnavailableException` whose `cause` is discarded (`null`).
- That null cause then propagates to the API error response (`cause: null`), so the frontend can only fall back to the generic server message.

Meanwhile, the underlying **automatic retry already exists and already covers topic suggestion** (`RetryingLlmProvider` decorates the `LlmProvider`, retries `suggestSubtopics` up to 3 total attempts with exponential backoff + jitter). The retry is therefore **already present on the backend**, but the failure surfaced to the user does not reflect the actual cause, and the retry behavior is not explicitly verified/documented as the guaranteed mechanism.

This spec scopes a focused, self-contained improvement on the topic-suggestion path only: make the retry behavior the documented/verified guarantee, and make the error surfaced to the user specific and meaningful on persistent failure. It does **not** redesign the snack/quiz retry paths, which are out of scope (already covered by the existing `001-batch-mvp-enhancements` feature).

## User Scenarios & Testing

### User Story 1 — Transient LLM failure automatically retried, topics shown transparently (Priority: P1)

When the theme-suggestion call to the LLM fails transiently (e.g. the service returns a 503/unavailable status), the app automatically retries the call before giving up. If a later attempt succeeds, the user is shown the suggested topics **exactly as if the first attempt had succeeded** — no error is shown and no manual retry is required.

**Why this priority**: This is the primary corrective behavior. It turns an annoying, blocking failure into an invisible, transparent success and dramatically reduces the perceived failure rate of the core suggestion flow.

**Independent Test**: Can be tested by forcing the LLM provider to return a SERVICE_UNAVAILABLE on the first attempt(s) and then succeed, and verifying the user simply sees the topics with no error (and, if observable via the loading indicator, that the retries happened transparently). This delivers the value of "suggestion just works even when the service hiccups."

**Acceptance Scenarios**:

1. **Given** the LLM topic-suggestion call fails with a SERVICE_UNAVAILABLE-type transient error, **When** the call is retried, **Then** the request is automatically retried up to 2 more times (3 total attempts) before giving up.
2. **Given** the transient failure resolves on a later attempt, **When** the retried attempt succeeds, **Then** the user sees the suggested topics as if the first attempt had succeeded — **no error is shown** and no manual retry is required.
3. **Given** the retry budget can be exhausted, **When** retries are in progress, **Then** the retry logic is bounded (no infinite retries) and the attempt count/timing matches documented behavior (2 retries, 3 total attempts).

### User Story 2 — Persistent LLM failure surfaces a specific, meaningful error (Priority: P1)

When every retry attempt fails, the app must NOT fall back to the generic "Please try again later." message. Instead the user sees a specific, meaningful error that reflects the actual failure cause, including the underlying cause surfaced to the UI (e.g. quota exceeded, timeout, network unreachable, service unavailable, or malformed response).

**Why this priority**: The generic message hides the real failure mode and frustrates users. A specific cause lets the user (and support) understand and act appropriately (e.g. quota needs waiting; network needs connectivity; malformed is a bug).

**Independent Test**: Can be tested by forcing a persistent failure for each cause category and verifying the user receives a distinct, cause-specific message (not the generic text) in every case. This delivers the value of "the app explains what actually went wrong."

**Acceptance Scenarios**:

1. **Given** an LLM failure on the topic-suggestion path, **When** all retries are exhausted, **Then** the user sees a specific, meaningful error message reflecting the actual failure cause (e.g. quota / timeout / network / unavailable / malformed) rather than the generic message.
2. **Given** the API error response is produced, **When** the failure is an LLM generation failure, **Then** the response **must** convey the machine-readable cause (non-null), and must NOT emit `cause: null` for a typed LLM failure.
3. **Given** the backend exposes the cause, **When** the frontend receives the error response, **Then** the frontend surfaces a user-friendly, cause-specific message (its existing cause→message mapping) and does not fall back to the generic text.
4. **Given** a manual test reproduces the temporary SERVICE_UNAVAILABLE condition, **When** the flow is exercised, **Then** retries are observed to occur and, on persistent failure, a meaningful error surfaces.

---

### Edge Cases

- **Persistent generic vs. typed failure**: What happens when the failure is *not* an LLM-generated typed exception (e.g. an unexpected infrastructure error)?
  - The typed-cause-carrying path surfaces the specific cause as above. For genuinely unexpected, non-LLM errors the system falls back to an appropriate general error — but it must never masquerade a typed LLM failure as a null-cause generic "unavailable" message.
- **Retry budget exhausted vs. non-retryable error**: What happens when the error is flagged as non-retryable (e.g. malformed response)?
  - The system does NOT retry a non-retryable error beyond what the existing semantics allow; it surfaces the typed cause immediately rather than retrying fruitlessly.
- **Double retry risk**: What happens if the frontend also retries the same call?
  - To keep the budget bounded and predictable, retries are centralized in **one place** (the backend). The frontend does not add an overlapping retry; it consumes the backend's final outcome and surfaces the appropriate message.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST automatically retry a transient LLM topic-suggestion failure up to **2 additional attempts (3 total attempts)** before giving up, with the retry count and timing matching documented behavior (bounded retries, no infinite loop).
- **FR-002**: On a transient failure that eventually succeeds, the system MUST present the suggested topics to the user as if the first attempt had succeeded — **no error message** is shown and no manual retry is required.
- **FR-003**: After all retries are exhausted, the system MUST surface a **specific, meaningful** error that reflects the actual failure cause (quota / timeout / network / unavailable / malformed) rather than the generic "The AI service is temporarily unavailable. Please try again later." message.
- **FR-004**: On the topic-suggestion (theme suggest) path, the system MUST **not discard the typed failure cause**: a typed LLM failure MUST be propagated with its machine-readable cause intact end-to-end, and MUST NOT be collapsed into a null-cause generic error (`cause: null`) for a typed LLM failure.
- **FR-005**: The API error response on an LLM generation failure MUST carry a non-null machine-readable cause value that the frontend can map to a specific, user-friendly message.
- **FR-006**: The frontend MUST display the cause-specific, user-friendly message when the error response contains a cause, and MUST fall back to the generic message only when no specific cause is available.
- **FR-007**: Retry behavior on the topic-suggestion path MUST reside in a single, unambiguous mechanism (the backend provider-level retry) so that the retry budget is bounded and not unpredictably multiplied. The frontend MUST NOT add a second, overlapping retry for the same call.
- **FR-008**: A manual test MUST be able to reproduce the temporary SERVICE_UNAVAILABLE condition and confirm (a) retries occur (up to 3 total attempts) and (b) a meaningful, cause-specific error surfaces on persistent failure.
- **FR-009**: Retry attempt count and timing MUST be externally observable/configurable and documented so the acceptance criteria (2 retries, 3 total) are verifiable.

### Notes on the Existing Architecture (context, non-normative)

- The backend **already auto-retries** topic suggestion: `RetryingLlmProvider` decorates the `LlmProvider` bean (wired `@Primary` in `LlmConfig`) and retries transient `LlmGenerationException`s for `suggestSubtopics` (as well as `generateSnack` and `generateQuiz`) up to `maxAttempts` (default 3) with exponential backoff + jitter. **The backend retry concern is therefore already satisfied**; this spec treats it as the primary (and only) retry mechanism and focuses on verifying/documenting it and fixing the error surface.
- The **actual root cause** of the generic `cause: null` error is in `ThemeService`: it catches and wraps ANY exception into a `ServiceUnavailableException`, losing the typed `LlmErrorCause` that `LlmGenerationException` carried. The typed exception, if allowed to propagate, is mapped by the global exception handler with the correct status and non-null `cause`.
- The frontend `ErrorService` already maps the cause vocabulary (`UNAVAILABLE`, `TIMEOUT`, `NETWORK`, `QUOTA`, `MALFORMED`) to specific, user-friendly messages; once the backend emits a non-null cause, the frontend surfaces it correctly without additional alarming work.

### Key Entities

- **LlmErrorCause**: The canonical vocabulary of failure causes surfaced to the user (quota, timeout, network, unavailable, malformed). Carried end-to-end and reflected in the error message.
- **RetryPolicy / RetryConfiguration**: Bounded retry parameters — total max attempts (3), initial backoff, backoff factor, max backoff, jitter ratio — applied to transient LLM failures on the suggestion path.
- **ErrorResponse**: The API error contract carrying, at minimum, an error code, a human-readable message, and a machine-readable `cause` for LLM failures.

## Success Criteria

### Measurable Outcomes

- **SC-001**: A transient SERVICE_UNAVAILABLE failure that resolves retries within a bounded budget (3 total attempts, 2 retries) and the user sees the suggested topics with **no error** in 100% of tested transient-recovery cases.
- **SC-002**: For each LLM cause category (quota, timeout, network, unavailable, malformed) the user receives a **cause-specific** message in 100% of persistent-failure tests; the generic message is never shown when a specific cause is available.
- **SC-003**: No typed LLM failure on the topic-suggestion path is emitted with a null cause (`cause: null`) — verified in 100% of tested LLM generation failures.
- **SC-004**: Retries are bounded — the system never retries beyond 3 total attempts (no infinite loop), verified by observing the attempt count in tests.
- **SC-005**: A reproducible manual test exists that forces a temporary SERVICE_UNAVAILABLE and confirms retries occur and a meaningful error surfaces on persistent failure.

## Assumptions

- **Retry lives on the backend** (`RetryingLlmProvider`) and is the single, authoritative retry mechanism. The frontend does not add a second, overlapping retry for the topic-suggestion call; doing so would multiply the LLM call budget unpredictably and could exceed the documented "3 total attempts". (Default chosen to keep behavior bounded, predictable, and non-buffered.)
- **Retry parameters defaults** are: max attempts 3, initial backoff 1000ms, backoff factor 2.0, max backoff 10000ms, jitter ratio 0.2 — consistent with the existing configuration. These are configuration, not normative scope; the spec only requires that the behavior (2 retries / 3 total) is verifiable and documented.
- **The snack/quiz generation and batch retry paths are out of scope** for this feature; they are already covered by the existing `001-batch-mvp-enhancements` capability. This spec applies to the topic-suggestion (`suggestSubtopics`) path only.
- Typed LLM failures carry a machine-readable cause; genuinely unexpected, non-LLM errors may still fall back to a general error, but must not mask a typed LLM failure as a null-cause generic "unavailable" message.
- Underlying technical cause details are surfaced up to the cause category semantics and a user-friendly message; raw stack traces / sensitive provider payloads must not be leaked to the end user.
- No authentication, data-model, or API-contract-shape changes are introduced beyond ensuring the `cause` field is populated for LLM failures.
