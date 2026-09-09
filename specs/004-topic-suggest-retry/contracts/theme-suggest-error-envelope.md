# API Contract: POST /api/themes/suggest — Error Envelope (non-null cause)

**Purpose**: Scope the error contract for the **topic-suggestion path** under this feature. This feature guarantees that an LLM generation failure on `POST /api/themes/suggest` is emitted with a **non-null `cause`** reflecting the actual failure mode (FR-004/FR-005), instead of the prior generic `cause: null`.

> **Canonical envelope**: the full error envelope and cause vocabulary is defined in the canonical contract
> [`specs/001-batch-mvp-enhancements/contracts/error-response.md`](../001-batch-mvp-enhancements/contracts/error-response.md).
> This file does **not** redefine the envelope — it references the canonical definition and states the guarantee that this feature adds on the *suggest* path only.

## Envelope (unchanged shape, see canonical contract)

```json
{
  "error": "string",
  "message": "string",
  "field": "string|null",
  "cause": "string|null"
}
```

| Field | Type | Applies on this path |
|-------|------|----------------------|
| `error` | `string` | Machine-readable code (`UNAVAILABLE`, `TIMEOUT`, `NETWORK`, `QUOTA`, `MALFORMED`, ...) |
| `message` | `string` | Human-friendly message |
| `field` | `string \| null` | Always `null` for LLM errors on this path |
| `cause` | `string \| null` | **Non-null** for a typed LLM generation failure on this path |

## Scope Guarantee

This feature guarantees **only** that the `POST /api/themes/suggest` path emits a **real, non-null `cause`** for a typed LLM failure. Non-LLM/unexpected errors on this path may still return `cause: null` (generic fallback), per the spec edge case — but a typed LLM failure must never be emitted as `cause: null` here (SC-003).

## Cause → Status → Message Vocabulary (typed LLM failures on this path)

These are the values the frontend expects to map to a specific, user-friendly message (`ErrorService.fromCause`). Backed by `com.endia.domain.enums.LlmErrorCause` and `LlmGenerationException`:

| `cause` | HTTP status | `error` | `message` shown to user (frontend mapping) |
|---------|-------------|---------|--------------------------------------------|
| `UNAVAILABLE` | `503` | `UNAVAILABLE` | "The AI service is temporarily unavailable. Please try again shortly." |
| `TIMEOUT` | `503` | `TIMEOUT` | "The AI service took too long to respond. Please try again." |
| `NETWORK` | `503` | `NETWORK` | "Could not reach the AI service. Check your internet connection and try again." |
| `QUOTA` | `429` | `QUOTA` | "The AI service quota or rate limit was reached. Try again shortly." |
| `MALFORMED` | `400` | `MALFORMED` | "The AI returned an unexpected response. Please try again." |

## Examples

### QUOTA — 429 (all retries exhausted)

```json
{
  "error": "QUOTA",
  "message": "The AI service quota or rate limit was reached. Try again shortly.",
  "field": null,
  "cause": "QUOTA"
}
```

### TIMEOUT — 503 (all retries exhausted)

```json
{
  "error": "TIMEOUT",
  "message": "The AI service took too long to respond. Please try again.",
  "field": null,
  "cause": "TIMEOUT"
}
```

### NETWORK — 503

```json
{
  "error": "NETWORK",
  "message": "Could not reach the AI service. Check your internet connection and try again.",
  "field": null,
  "cause": "NETWORK"
}
```

### UNAVAILABLE — 503 (prior buggy behavior for comparison)

Prior behavior on this path was a **null cause** with a generic message:

```json
{
  "error": "SERVICE_UNAVAILABLE",
  "message": "The AI service is temporarily unavailable. Please try again later.",
  "field": null,
  "cause": null
}
```

After this feature, the same scenario is emitted with the actual cause (e.g. `UNAVAILABLE`) and the cause-specific message.

### Validation error — 400 (no cause, unchanged)

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Theme must be between 2 and 500 characters",
  "field": "prompt",
  "cause": null
}
```

## Status Codes (this path)

| Code | Cause / condition |
|------|-------------------|
| `200` | Subtopics returned (success; also the outcome when a transient failure recovered via retry) |
| `400` | Validation error, or `MALFORMED` LLM cause |
| `429` | `QUOTA` |
| `503` | `UNAVAILABLE`, `TIMEOUT`, `NETWORK` |

## Retry Semantics (this path)

- Retry lives in **one place**: the backend `RetryingLlmProvider` decorator (FR-007). It retries `suggestSubtopics` for **retryable** causes up to **3 total attempts** with exponential backoff + jitter; non-retryable causes (e.g. `MALFORMED`) are surfaced immediately without retry.
- The frontend adds **no** second, overlapping retry.
- Only after all attempts fail does the final typed error reach the client with a non-null `cause`. On a recovered attempt, the client receives the normal `200` and the topics are shown with no error.

## Contractual Note

No change to the success `200` body or request shape of `POST /api/themes/suggest` (see the full contract in `specs/001-batch-mvp-enhancements/contracts/theme-suggest.md`). This feature only repairs the **error response** so a typed LLM failure carries a non-null `cause`.
