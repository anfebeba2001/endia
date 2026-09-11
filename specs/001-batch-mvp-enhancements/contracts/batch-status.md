# API Contract: GET /api/batches/{batchId}

> **SUPERSEDED (partial) by `002-snack-retry-bound`.** The canonical contract for terminality,
> `retrying` semantics, and the client polling window is now
> [`specs/002-snack-retry-bound/contracts/batch-status.md`](../../002-snack-retry-bound/contracts/batch-status.md)
> (FR-016). Two statements in this document are stale and MUST NOT be followed:
> 1. Terminal states are **not** limited to `COMPLETE`/`FAILED`; `PARTIAL` is terminal too, and
>    terminality is defined negatively (`status !== 'IN_PROGRESS'`).
> 2. `retrying` is **not** true for the whole `IN_PROGRESS` lifetime; it reflects active item
>    generation (`any item GENERATING`), plus the frontend's own re-initiation-in-flight state.
>
> The response shape is unchanged; only these field/behavior semantics are superseded.

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
| `retrying` | `boolean` | **Superseded by 002**: true only while any item is actively `GENERATING` (FR-011), not for the whole `IN_PROGRESS` lifetime |
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
- **Superseded by 002**: terminal = `status !== 'IN_PROGRESS'`, so `PARTIAL`, `COMPLETE`, and `FAILED` all stop polling. Polling is additionally bounded by a configurable client window (default 120 s). See the `002` contract.
- Progressive rendering (FR-002/004): render only READY snacks; a FAILED item renders an error banner with `snackCause` and a retry affordance without blocking READY ones.
- **Superseded by 002**: `retrying` drives the spinner only during active generation/reattempt activity (any item `GENERATING`), OR-ed with the frontend's re-initiation-in-flight state (FR-011).
