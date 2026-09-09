# API Contract: GET /api/batches/{batchId}

**Purpose**: Poll the status of a batch created via `POST /api/batches`. Returns per-subtopic snack and quiz generation status so the frontend can render snacks **one at a time** and know when the quiz is ready. Also carries the actual snack content when `READY` and the specific error cause when `FAILED`.

## Request

**Method**: `GET`
**Path**: `/api/batches/{batchId}`
**Content-Type**: `application/json`

### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `batchId` | `string (UUID)` | Batch ID from `POST /api/batches` |

## Response

### 200 OK — Batch Status

```json
{
  "batchId": "aa0e8400-e29b-41d4-a716-4466554400aa",
  "themeId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "IN_PROGRESS",
  "retrying": true,
  "items": [
    {
      "subtopicId": "660e8400-e29b-41d4-a716-446655440001",
      "subtopicName": "Light reactions",
      "snackStatus": "READY",
      "snack": {
        "id": "770e8400-e29b-41d4-a716-446655440001",
        "subtopicId": "660e8400-e29b-41d4-a716-446655440001",
        "subtopicName": "Light reactions",
        "concept": "…",
        "metaphor": "…",
        "whyItMatters": "…",
        "related": ["Calvin cycle", "Chloroplast structure"]
      },
      "snackCause": null,
      "quizStatus": "GENERATING",
      "quizCause": null
    },
    {
      "subtopicId": "660e8400-e29b-41d4-a716-446655440002",
      "subtopicName": "Calvin cycle",
      "snackStatus": "FAILED",
      "snack": null,
      "snackCause": "QUOTA",
      "quizStatus": "GENERATING",
      "quizCause": null
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `batchId` | `string (UUID)` | The batch |
| `themeId` | `string (UUID)` | Theme of the batch |
| `status` | `string` | `IN_PROGRESS` / `PARTIAL` / `COMPLETE` / `FAILED` |
| `retrying` | `boolean` | True while any item is being auto-retried (FR-016/017 — drives the spinner) |
| `items` | `array` | Per-subtopic status |
| `items[].subtopicId` | `string (UUID)` | Subtopic |
| `items[].subtopicName` | `string` | Subtopic name (display label) |
| `items[].snackStatus` | `string` | `PENDING` / `GENERATING` / `READY` / `FAILED` |
| `items[].snack` | `object \| null` | The snack card, present only when `READY` |
| `items[].snackCause` | `string \| null` | Error cause when `snackStatus = FAILED` (`UNAVAILABLE`/`TIMEOUT`/`NETWORK`/`QUOTA`/`MALFORMED`) |
| `items[].quizStatus` | `string` | `PENDING` / `GENERATING` / `READY` / `FAILED` |
| `items[].quizCause` | `string \| null` | Error cause when quiz failed |

**Notes**:
- `items[].snack` has the same shape as a `snack-generate.md` snack item.
- The prefetched **quiz** itself is **not** returned here. Once `quizStatus = READY` for a subtopic, the frontend fetches it with the existing `GET /api/quiz/summary/{subtopicId}` (or generates on demand via `/api/quiz/generate` as a fallback). Returning quiz questions here would leak a batch "completion" coupling; keeping quiz fetch separate preserves the existing per-subtopic quiz contract.
- `snackCause`/`quizCause` use the `LlmErrorCause` vocabulary (see `error-response.md`).

### 404 Not Found — Batch Not Found

```json
{
  "error": "NOT_FOUND",
  "message": "Batch not found with ID: aa0e8400-e29b-41d4-a716-4466554400aa",
  "cause": null
}
```

## Status Codes

| Code | Condition |
|------|-----------|
| `200` | Status retrieved (may still be in-progress) |
| `404` | Batch ID not found |

## Implementation Notes

- Safe to call repeatedly; the endpoint is read-only and cheap.
- Terminal states: `COMPLETE` and `FAILED` are reached when the worker finishes; the client can **stop polling** on `COMPLETE`/`FAILED`.
- Progressive rendering (FR-002/004): render only READY snacks; a FAILED item renders an error banner with `snackCause` and a retry affordance without blocking READY ones.
- `retrying` flag drives the FR-017 spinner during in-flight retries.
