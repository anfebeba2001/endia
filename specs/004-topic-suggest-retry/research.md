# Research: Retry Configuration & Cause→Message Vocabulary

**Feature**: `topic-suggest-retry` | **Date**: 2026-09-09

This file documents the (already-existing) retry configuration and the cause→message vocabulary that this feature relies on and guarantees.

## 1. Retry mechanism (already present, single source of truth)

- **Decorator**: `backend/src/main/java/com/endia/infrastructure/llm/RetryingLlmProvider.java`
- **Wiring**: `LlmConfig.retryingLlmProvider(...)` — bean `@Primary`, wraps `OpenAiLlmProvider`. Every services/controller depends on this primary `LlmProvider`.
- **Coverage**: `suggestSubtopics`, `generateSnack`, `generateQuiz` (this feature only asserts/guarantees the `suggestSubtopics` path).
- **Behavior**:
  - Loops up to `maxAttempts` total attempts (default **3** → 2 retries).
  - On a **retryable** `LlmGenerationException` and attempt < maxAttempts: sleeps `backoff(attempt)`, retries.
  - On a **non-retryable** cause, or attempt == maxAttempts: rethrows the final typed exception immediately.
  - Returns the supplier result as soon as an attempt succeeds (transparent success).

### Default retry parameters (from `LlmConfig` / `application.yml` `batch.retry.*`)

| Property | Default | Meaning |
|----------|---------|---------|
| `batch.retry.max-attempts` | `3` | Total attempts (2 retries) |
| `batch.retry.initial-backoff-ms` | `1000` | Initial backoff before first retry |
| `batch.retry.backoff-factor` | `2.0` | Exponential factor |
| `batch.retry.max-backoff-ms` | `10000` | Backoff cap |
| `batch.retry.jitter-ratio` | `0.2` | ±20% jitter around each backoff |

Backoff formula (per attempt): `min(maxBackoff, initialBackoff * factor^(attempt-1))`, with ±`jitterRatio` randomization.

**Implication**: retry concern (FR-001, FR-007, FR-009, SC-001, SC-004) is **already satisfied** by existing code. This feature documents/verifies it rather than adding a new mechanism.

## 2. Cause→message vocabulary (frontend `ErrorService.fromCause`)

Source: `frontend/src/app/services/error.service.ts`; enum `com.endia.domain.enums.LlmErrorCause`; status logic in `LlmGenerationException.statusFor`.

| `cause` | HTTP | Frontend message |
|---------|------|------------------|
| `UNAVAILABLE` | 503 | The AI service is temporarily unavailable. Please try again shortly. |
| `TIMEOUT` | 503 | The AI service took too long to respond. Please try again. |
| `NETWORK` | 503 | Could not reach the AI service. Check your internet connection and try again. |
| `QUOTA` | 429 | The AI service quota or rate limit was reached. Try again shortly. |
| `MALFORMED` | 400 | The AI returned an unexpected response. Please try again. |

Frontend precedence (`getMessage`): `cause` (mapped via `fromCause`) → `body.message` → status text. So once the backend emits a non-null `cause`, the cause-specific message wins.

## 3. Root cause summary

`ThemeService.suggestSubtopics` catches **any** `Exception` and wraps it in `ServiceUnavailableException`. Because `LlmGenerationException` is not a `ServiceUnavailableException`, it is swallowed by the generic catch and re-emitted as `ServiceUnavailableException`. `GlobalExceptionHandler.handleDomain` (mapping `DomainException`/`ServiceUnavailableException`) always serializes **`cause: null`** — only `handleLlmGeneration` (for `LlmGenerationException`) emits `cause = causeType.name()`. Hence the generic message and `cause: null`.

**Fix**: rethrow `LlmGenerationException` in `ThemeService.suggestSubtopics` so the typed cause reaches `GlobalExceptionHandler.handleLlmGeneration`, which produces the correct status and non-null `cause`.

## 4. Rejected alternatives

- **Frontend retry**: multiplies the LLM budget, violates the documented 3-total bound (FR-007). Rejected.
- **Emit cause from the DomainException handler**: less surgical and requires unwrapping logic; the clean provenance is at `ThemeService`. Rejected.
- **New cause vocabulary / new exception type**: existing `LlmErrorCause` + `LlmGenerationException` already cover all cases. Rejected.
- **Modify `ThemeController`**: no exception handling there; unnecessary churn. Rejected.
