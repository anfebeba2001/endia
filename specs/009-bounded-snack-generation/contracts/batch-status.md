# API Contract: POST /api/batches + GET /api/batches/{batchId}

**Purpose**: Start a batch and poll its status. Starting a batch returns immediately; status reads are
cheap and never wait on background generation. The response shape is **unchanged** from
`002-snack-retry-bound` / `005-batch-snack-generation-fix`; this contract restates the **liveness
guarantees** added/confirmed by `009-bounded-snack-generation`.

> **Feature `009-bounded-snack-generation` update.** No field, type, or value changes. `009` guarantees
> that the async worker always reaches a terminal aggregate status even when a snack provider never
> returns, and that no synchronous HTTP request in this path is left unanswered.

---

## POST /api/batches

**Method**: `POST`
**Path**: `/api/batches`
**Content-Type**: `application/json`

### Request

```json
{
  "themeId": "550e8400-e29b-41d4-a716-446655440000",
  "subtopicIds": [
    "660e8400-e29b-41d4-a716-446655440001",
    "660e8400-e29b-41d4-a716-446655440002"
  ]
}
```

| Field | Type | Constraints |
|-------|------|-------------|
| `themeId` | `string (UUID)` | required |
| `subtopicIds` | `string[]` | required, 1–10 |

### 202 Accepted — Batch Created

```json
{
  "batchId": "aa0e8400-e29b-41d4-a716-4466554400aa",
  "themeId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "IN_PROGRESS",
  "items": [
    { "subtopicId": "660e8400-e29b-41d4-a716-446655440001", "snackStatus": "PENDING", "quizStatus": "PENDING" },
    { "subtopicId": "660e8400-e29b-41d4-a716-446655440002", "snackStatus": "PENDING", "quizStatus": "PENDING" }
  ]
}
```

**Guarantees (FR-015 / FR-017 / SC-005)**:
- The response is `202` and is produced **immediately** after the batch and its items are persisted.
- The endpoint MUST NOT wait for, or execute, any LLM generation on the request thread.
- The async worker is triggered only **after** the creating transaction commits (005), so it can never
  start on a warm executor before the batch is visible.
- A hanging provider MUST NOT delay this response. The request thread is never held by generation.

---

## GET /api/batches/{batchId}

**Method**: `GET`
**Path**: `/api/batches/{batchId}`
**Content-Type**: `application/json`

### 200 OK — Batch Status

```json
{
  "batchId": "aa0e8400-e29b-41d4-a716-4466554400aa",
  "themeId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PARTIAL",
  "retrying": true,
  "items": [
    {
      "subtopicId": "660e8400-e29b-41d4-a716-446655440001",
      "subtopicName": "Light reactions",
      "snackStatus": "READY",
      "snackPhase": null,
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
      "quizStatus": "READY",
      "quizCause": null,
      "quizQuestions": []
    },
    {
      "subtopicId": "660e8400-e29b-41d4-a716-446655440002",
      "subtopicName": "Calvin cycle",
      "snackStatus": "FAILED",
      "snackPhase": null,
      "snack": null,
      "snackCause": "TIMEOUT",
      "quizStatus": "FAILED",
      "quizCause": "TIMEOUT",
      "quizQuestions": null
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `batchId` | `string (UUID)` | The batch |
| `themeId` | `string (UUID)` | Theme of the batch |
| `status` | `string` | `IN_PROGRESS` / `PARTIAL` / `COMPLETE` / `FAILED` |
| `retrying` | `boolean` | `true` only while at least one item is actively generating |
| `items` | `array` | Per-subtopic status |
| `items[].snackStatus` | `string` | `PENDING` / `GENERATING` / `READY` / `FAILED` |
| `items[].snackPhase` | `string \| null` | Non-null only while `snackStatus = GENERATING` (008) |
| `items[].snack` | `object \| null` | The snack card, present only when `READY` |
| `items[].snackCause` | `string \| null` | `UNAVAILABLE` / `TIMEOUT` / `NETWORK` / `QUOTA` / `MALFORMED` |
| `items[].quizStatus` | `string` | `PENDING` / `GENERATING` / `READY` / `FAILED` |
| `items[].quizCause` | `string \| null` | Quiz failure cause |
| `items[].quizQuestions` | `array \| null` | Prefetched quiz, present only when `quizStatus = READY` |

### 404 Not Found — Batch Not Found

```json
{
  "error": "NOT_FOUND",
  "message": "Batch not found with ID: aa0e8400-e29b-41d4-a716-4466554400aa",
  "cause": null
}
```

### Status Codes

| Code | Condition |
|------|-----------|
| `200` | Status retrieved (may still be in-progress) |
| `404` | Batch ID not found |

**Guarantees (FR-016 / FR-017 / SC-005)**:
- The endpoint is **read-only** and MUST NOT block on background generation, including while a
  generation is slow or hung. It returns the currently persisted item states.
- Repeated calls are safe and cheap.

---

## Terminality (FR-012 / FR-013 / FR-014)

| `status` | Terminal? | Client behavior |
|----------|-----------|-----------------|
| `IN_PROGRESS` | **No** (the only non-terminal state) | Continue polling on the ~1.5 s interval |
| `PARTIAL` | **Yes** | Stop polling; render `READY` snacks and failed-item errors together |
| `COMPLETE` | **Yes** | Stop polling |
| `FAILED` | **Yes** | Stop polling |

**Rule**: terminal = `status !== 'IN_PROGRESS'`. The check MUST NOT be an allow-list of only
`COMPLETE`/`FAILED`.

**Terminal guarantee (`009`)**: even against a provider that never returns, the worker always writes a
terminal status within a bounded time. A single hung subtopic is bounded by
`batch.snack-timeout-seconds` (default 240 s) and is marked `FAILED`/`TIMEOUT`; the worker proceeds.
Any item still non-terminal after the bounded quiz join is finalized to `FAILED`/`TIMEOUT` before
aggregation.

**No-pending rule (FR-014)**: when `status` is terminal, **no** item may have `snackStatus` or
`quizStatus` equal to `PENDING`/`GENERATING`.

---

## `retrying` semantics (preserved from 002/005)

`retrying` is `true` **only while at least one item is actively generating** — i.e. some item has
`snackStatus === 'GENERATING'` or `quizStatus === 'GENERATING'`. It is `false` immediately after
creation (all `PENDING`), between active generations, and once the batch is terminal. The frontend ORs
its own app-level re-initiation-in-flight state into the user-visible indicator.

---

## Polling window (preserved from 002/005, FR-018)

The client bounds polling to a **configurable maximum lifetime**, default **120 seconds**
(`MAX_POLLING_WINDOW_MS` injection token, default `120000`). On terminal status the poll observable
emits the terminal payload and completes; on window elapse while still `IN_PROGRESS` it errors with a
client-side `BatchPollingTimeoutError`, cancels the in-flight request, and shows a terminal,
user-visible error. **Unchanged by `009`.**

---

## Implementation Notes

- No controller, DTO, or aggregation-shape change in `009`; only the async worker's bounded behavior
  changes. See `snack-generation-bounds.md`.
- `snackCause`/`quizCause` use the existing `LlmErrorCause` vocabulary; `TIMEOUT` now also covers the
  per-subtopic bound and the quiz-join finalization.
- Progressive rendering: render only `READY` snacks; a `FAILED` item renders an error banner with its
  cause and a retry affordance without blocking `READY` ones.
- Logs and error messages on timeout/failure paths carry only typed causes and ids — no prompt bodies,
  API keys, provider payloads, or personal data (FR-019/FR-020).
