# API Contract: Error Response (typed cause)

**Purpose**: Standardize how the API reports failures so the frontend can surface **specific** causes (FR-015) instead of a generic "LLM problem". All error responses share the same envelope; the `cause` field discriminates the failure mode.

## Envelope

Every error response is a JSON object with this shape:

```json
{
  "error": "string",
  "message": "string",
  "field": "string|null",
  "cause": "string|null"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `error` | `string` | Machine-readable error code (`VALIDATION_ERROR`, `NOT_FOUND`, `CONFLICT`, `UNAVAILABLE`, `TIMEOUT`, `NETWORK`, `QUOTA`, `MALFORMED`, `INTERNAL_ERROR`, etc.). |
| `message` | `string` | Human-friendly message shown to the user. |
| `field` | `string \| null` | The offending field, for validation errors; null otherwise. |
| `cause` | `string \| null` | **NEW.** The specific LLM failure cause (see below); null for non-LLM errors. |

> Backward-compatible: the existing `{ error, message, field }` shape is preserved; `cause` is added as a nullable field. Existing clients that ignore `cause` keep working.

## LLM Error Causes (`LlmErrorCause`)

| `cause` | Meaning | HTTP status | Example `message` |
|---------|---------|-------------|-------------------|
| `UNAVAILABLE` | LLM provider down / 5xx / model unavailable | `503` | "The AI service is temporarily unavailable. Please try again shortly." |
| `TIMEOUT` | LLM call exceeded the timeout | `503` | "The AI service took too long to respond. Please try again." |
| `NETWORK` | Connect/DNS/reset failure reaching the provider | `503` | "Could not reach the AI service. Check your internet connection and try again." |
| `QUOTA` | 429 / rate-limit / quota exceeded | `429` | "The AI service quota or rate limit was reached. Try again shortly." |
| `MALFORMED` | Missing/empty required fields or unparsable response | `400` | "The AI returned an unexpected response. Please try again." |

### Example: QUOTA (429)

```json
{
  "error": "QUOTA",
  "message": "The AI service quota or rate limit was reached. Try again shortly.",
  "field": null,
  "cause": "QUOTA"
}
```

### Example: NETWORK (503)

```json
{
  "error": "NETWORK",
  "message": "Could not reach the AI service. Check your internet connection and try again.",
  "field": null,
  "cause": "NETWORK"
}
```

### Example: validation error (no cause)

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Theme must be between 2 and 500 characters",
  "field": "prompt",
  "cause": null
}
```

## Status Codes (LLM-related)

| Code | Cause |
|------|-------|
| `429` | `QUOTA` |
| `503` | `UNAVAILABLE`, `TIMEOUT`, `NETWORK` |
| `400` | `MALFORMED` (returned as a validation-style response) |

## Retry semantics (FR-016)

- If a request fails with a **retryable** cause (`UNAVAILABLE`, `TIMEOUT`, `NETWORK`, `QUOTA`, `MALFORMED`), the backend auto-retries up to **3 total attempts** with exponential backoff + jitter.
- While a snack/quiz is being retried, `GET /api/batches/{batchId}` returns `retrying: true` so the frontend shows a spinner (FR-017).
- Only after all 3 attempts fail does the final typed error reach the user. No partial/corrupt content is ever served.
- Retry is distinct from the quality-gate re-roll (max 1) and may stack (spec note). Backend caps combined attempts per item to prevent pathologies.

## Non-LLM errors (unchanged)

| `error` | HTTP | Trigger |
|---------|------|---------|
| `VALIDATION_ERROR` | `400` | Bean Validation / invalid field |
| `NOT_FOUND` | `404` | Entity not found |
| `CONFLICT` | `409` | Duplicate answer / already answered |
| `INTERNAL_ERROR` | `500` | Unexpected server error |

## Implementation Notes

- `OpenAiLlmProvider` maps failures to `LlmGenerationException(LlmErrorCause, retryable)`; `GlobalExceptionHandler` serializes it into this envelope with the matching status.
- The frontend `ErrorService.getMessage` switches on `cause` to render the exact cause-specific message (FR-015), falling back to `message` and then to the existing status-based text.
