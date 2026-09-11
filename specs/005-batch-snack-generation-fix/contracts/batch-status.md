# API Contract: GET /api/batches/{batchId}

**Purpose**: Poll the status of a batch created via `POST /api/batches`. Returns per-subtopic snack and quiz generation status so the frontend can render snacks **one at a time** and know when the quiz is ready. Also carries the actual snack content when `READY` and the specific error cause when `FAILED`.

> **Feature `005-batch-snack-generation-fix` update.** The HTTP request/response **shape is unchanged** from `002-snack-retry-bound`. This contract is restated here because the server-side generation defect fixed by `005` made the terminal statuses below unreachable in practice (the worker deadlocked and every snack failed with a detached-data error). The canonical `002` semantics remain in force:
>
> 1. **Terminality** is defined negatively: `IN_PROGRESS` is the **only** non-terminal status; `PARTIAL`, `COMPLETE`, and `FAILED` are all terminal.
> 2. **`retrying`** reflects *active* item generation/reattempt activity, not the whole lifetime of an `IN_PROGRESS` batch.
> 3. **Polling window**: the client bounds polling to a configurable maximum lifetime (default 120 s) and surfaces a terminal error if the window elapses.
>
> `005` changes **no field, type, or value**. It only ensures the backend reaches a terminal status and persists `READY` snacks.

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
| `status` | `string` | `IN_PROGRESS` / `PARTIAL` / `COMPLETE` / `FAILED` (see terminality below) |
| `retrying` | `boolean` | `true` **only while at least one item is actively generating/reattempting** (`snackStatus` or `quizStatus` is `GENERATING`); `false` when all items are `PENDING`/`READY`/`FAILED`. Drives the spinner. |
| `items` | `array` | Per-subtopic status |
| `items[].subtopicId` | `string (UUID)` | Subtopic |
| `items[].subtopicName` | `string` | Subtopic name (display label) |
| `items[].snackStatus` | `string` | `PENDING` / `GENERATING` / `READY` / `FAILED` |
| `items[].snack` | `object \| null` | The snack card, present only when `READY` |
| `items[].snackCause` | `string \| null` | Error cause when `snackStatus = FAILED` (`UNAVAILABLE`/`TIMEOUT`/`NETWORK`/`QUOTA`/`MALFORMED`) |
| `items[].quizStatus` | `string` | `PENDING` / `GENERATING` / `READY` / `FAILED` |
| `items[].quizCause` | `string \| null` | Error cause when quiz failed |

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

## Terminality (FR-012)

| `status` | Terminal? | Client behavior |
|----------|-----------|-----------------|
| `IN_PROGRESS` | **No** (the only non-terminal state) | Continue polling on the ~1.5 s interval |
| `PARTIAL` | **Yes** | Stop polling; render `READY` snacks and failed-item errors together |
| `COMPLETE` | **Yes** | Stop polling |
| `FAILED` | **Yes** | Stop polling |

**Rule**: terminal = `status !== 'IN_PROGRESS'`. The check MUST NOT be an allow-list of only `COMPLETE`/`FAILED`. The poll observable completes as soon as a terminal status is observed and unsubscribes (no further requests). `005` guarantees the worker writes one of these terminal statuses (see `batch-generation-behavior.md`).

## `retrying` semantics (FR-014)

`retrying` is `true` **only while at least one item is actively generating/being retried** — i.e. some item has `snackStatus === 'GENERATING'` or `quizStatus === 'GENERATING'`. It is `false`:

- immediately after batch creation, while all items are `PENDING` (worker not yet active);
- between active generations if items are `PENDING`;
- once the batch is terminal (all items `READY`/`FAILED`).

It is **not** `true` for the entire lifetime of an `IN_PROGRESS` batch. The frontend additionally ORs its own app-level re-initiation-in-flight state into the user-visible indicator (`002`).

## Polling window (FR-015)

The client bounds polling to a **configurable maximum lifetime**, default **120 seconds**:

- Configuration seam: Angular `MAX_POLLING_WINDOW_MS` injection token (default `120000`); `pollStatus` accepts an override for tests.
- On terminal status: the observable emits the terminal payload and completes (normal path).
- On window elapse while still `IN_PROGRESS`: the observable **errors** with a client-side `BatchPollingTimeoutError`, the in-flight request is cancelled, and the UI shows a terminal, user-visible error and stops progress. Polling does not continue.

Unchanged by `005`.

## Implementation Notes

- Safe to call repeatedly; the endpoint is read-only and cheap.
- `005` changes no controller, DTO, or aggregation code; it fixes the async worker so that `READY` snacks are persisted and a terminal aggregate status is written. See `batch-generation-behavior.md`.
- Progressive rendering: render only `READY` snacks; a `FAILED` item renders an error banner with `snackCause` and a retry affordance without blocking `READY` ones.
- `snackCause`/`quizCause` use the `LlmErrorCause` vocabulary (see `001`'s `error-response.md`).
- `005` adds a frontend-only `polling` flag that suppresses the empty state until the batch is terminal; it does not affect this response.
