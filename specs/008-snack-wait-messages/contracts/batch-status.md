# API Contract: GET /api/batches/{batchId}

**Purpose**: Poll the status of a batch created via `POST /api/batches`. Returns per-subtopic snack and quiz generation status so the frontend can render snacks **one at a time** and know when the quiz is ready. Also carries the actual snack content when `READY`, the specific error cause when `FAILED`, and — while a snack is generating — the **current generation phase** so the screen can show an accurate step-by-step waiting message.

> **Feature `008-snack-wait-messages` update.** This contract adds one additive, nullable per-item field: **`snackPhase`**. It is present (non-null) only while `snackStatus == 'GENERATING'` and is `null` for `PENDING`/`READY`/`FAILED` items. No existing field, type, or value changes. The canonical `002`/`005` semantics for terminality, `retrying`, and the polling window remain in force and are restated below.

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
      "snackStatus": "GENERATING",
      "snackPhase": "AWAITING_MODEL",
      "snack": null,
      "snackCause": null,
      "quizStatus": "GENERATING",
      "quizCause": null
    },
    {
      "subtopicId": "660e8400-e29b-41d4-a716-446655440002",
      "subtopicName": "Calvin cycle",
      "snackStatus": "READY",
      "snackPhase": null,
      "snack": {
        "id": "770e8400-e29b-41d4-a716-446655440002",
        "subtopicId": "660e8400-e29b-41d4-a716-446655440002",
        "subtopicName": "Calvin cycle",
        "concept": "…",
        "metaphor": "…",
        "whyItMatters": "…",
        "related": ["RuBP", "ATP", "NADPH"]
      },
      "snackCause": null,
      "quizStatus": "GENERATING",
      "quizCause": null
    },
    {
      "subtopicId": "660e8400-e29b-41d4-a716-446655440003",
      "subtopicName": "Chloroplast structure",
      "snackStatus": "FAILED",
      "snackPhase": null,
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
| `items[].subtopicId` | `string (UUID)` | Subtopic (also the phase key) |
| `items[].subtopicName` | `string` | Subtopic name (display label) |
| `items[].snackStatus` | `string` | `PENDING` / `GENERATING` / `READY` / `FAILED` |
| **`items[].snackPhase`** | `string \| null` | **NEW.** Current real generation phase: `RESOLVING_CONTEXT` / `AWAITING_MODEL` / `ASSEMBLING` / `PERSISTING`. Non-null **only** while `snackStatus == 'GENERATING'`; `null` for `PENDING`/`READY`/`FAILED`. |
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

## `snackPhase` semantics (FR-001..FR-004)

The phase reflects the **real** snack generation step the item is currently in, advanced by the backend at the actual code point where the step begins — never on a timer, elapsed time, or poll count (FR-002).

| Phase | Real step (backend code point) | User-facing message |
|-------|--------------------------------|---------------------|
| `RESOLVING_CONTEXT` | Resolving the subtopic and its theme context (`subtopic.getTheme().getPrompt()`) | `Finding the topic context…` |
| `AWAITING_MODEL` | Issuing the LLM call (`llmProvider.generateSnack`), including the one re-roll | `Waiting for the AI response…` |
| `ASSEMBLING` | Validating the response and constructing the snack (`isValid` → `new Snack(...)`) | `Assembling your snack…` |
| `PERSISTING` | Persisting the snack (`snackRepository.save(snack)`) | `Saving your snack…` |

**Rules**:

- **Per item (FR-008)**: each item carries its own phase, keyed by `subtopicId`; one item's phase is never reported for another.
- **Settled items (FR-004)**: for `snackStatus` `PENDING`/`READY`/`FAILED`, `snackPhase` is `null`. A non-null phase always implies active generation.
- **Truthful only (FR-002/FR-006)**: the field carries the actual current phase. A phase shorter than the poll interval may be skipped; the client must never back-date or invent a phase and must show only the latest reported value.
- **Retry (FR-009)**: a retried item re-enters the phases from `RESOLVING_CONTEXT` in its new cycle.
- **Privacy (FR-011)**: the field carries only a static enum token. No prompts, API keys, provider payloads, or personal data are ever present in `snackPhase` or in the phase messages.

## Terminality (unchanged from `002`/`005`)

| `status` | Terminal? | Client behavior |
|----------|-----------|-----------------|
| `IN_PROGRESS` | **No** (the only non-terminal state) | Continue polling on the ~1.5 s interval |
| `PARTIAL` | **Yes** | Stop polling; render `READY` snacks and failed-item errors together |
| `COMPLETE` | **Yes** | Stop polling |
| `FAILED` | **Yes** | Stop polling |

**Rule**: terminal = `status !== 'IN_PROGRESS'`. The poll observable completes as soon as a terminal status is observed and unsubscribes.

## `retrying` semantics (unchanged from `002`/`005`)

`retrying` is `true` only while some item has `snackStatus === 'GENERATING'` or `quizStatus === 'GENERATING'`. It is `false` immediately after creation (all `PENDING`), between active generations, and once terminal.

## Polling window (unchanged from `002`/`005`)

The client bounds polling to a configurable maximum lifetime (default 120 s). On terminal status the observable completes; on window elapse while `IN_PROGRESS` it errors with a client-side `BatchPollingTimeoutError` and the UI shows a terminal error and stops.

## Implementation Notes

- Safe to call repeatedly; the endpoint is read-only and cheap.
- The phase is **in-memory** backend state; it is not persisted and is cleared when the item settles (FR-004).
- Progressive rendering (unchanged): render only `READY` snacks; a `FAILED` item renders an error banner with `snackCause` and a retry affordance without blocking `READY` ones.
- The frontend maps `snackPhase` to a static message. Before the first poll, or when the phase is `null` on a generating item (e.g. a skipped phase), it falls back to the existing generic `Preparing your snack…`; it never substitutes a message for a different phase.
- `snackCause`/`quizCause` use the `LlmErrorCause` vocabulary (see `001`'s `error-response.md`).
