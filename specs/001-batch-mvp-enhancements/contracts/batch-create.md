# API Contract: POST /api/batches

**Purpose**: One logical batch trigger that requests generation of all snacks for the selected subtopics AND their quiz questions together. The request returns immediately with a `batchId`; the frontend polls `GET /api/batches/{batchId}` (see `batch-status.md`) to consume snacks progressively and a prefetched quiz.

> Replaces the previous sequential `snack-generate` + `quiz-generate` flow for the live app. Existing `/api/snacks/generate` and `/api/quiz/generate` are retained (backward-compat / E2E).

## Request

**Method**: `POST`
**Path**: `/api/batches`
**Content-Type**: `application/json`

### Request Body

```json
{
  "themeId": "string",
  "subtopicIds": ["string"]
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `themeId` | `string (UUID)` | Yes | Valid UUID | Session theme (from `/api/themes/suggest`) |
| `subtopicIds` | `array of string (UUID)` | Yes | `minItems: 1`, `maxItems: 10` | Selected subtopics (AI-suggested + user-proposed) to generate snacks + quizzes for |

### Example Request

```json
{
  "themeId": "550e8400-e29b-41d4-a716-446655440000",
  "subtopicIds": [
    "660e8400-e29b-41d4-a716-446655440001",
    "660e8400-e29b-41d4-a716-446655440002",
    "660e8400-e29b-41d4-a716-446655440003"
  ]
}
```

## Response

### 202 Accepted — Batch Created

```json
{
  "batchId": "aa0e8400-e29b-41d4-a716-4466554400aa",
  "themeId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "IN_PROGRESS",
  "items": [
    {
      "subtopicId": "660e8400-e29b-41d4-a716-446655440001",
      "snackStatus": "PENDING",
      "quizStatus": "PENDING"
    },
    {
      "subtopicId": "660e8400-e29b-41d4-a716-446655440002",
      "snackStatus": "PENDING",
      "quizStatus": "PENDING"
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `batchId` | `string (UUID)` | ID of the persisted batch to poll |
| `themeId` | `string (UUID)` | Theme of the batch |
| `status` | `string` | `IN_PROGRESS` immediately (see `BatchStatus`) |
| `items` | `array` | One status entry per selected subtopic |
| `items[].subtopicId` | `string (UUID)` | Subtopic being generated |
| `items[].snackStatus` | `string` | `PENDING` initial |
| `items[].quizStatus` | `string` | `PENDING` initial |

### 400 Bad Request — Validation Error

```json
{
  "error": "VALIDATION_ERROR",
  "message": "At least one subtopic ID is required",
  "field": "subtopicIds",
  "cause": null
}
```

### 404 Not Found — Theme Not Found

```json
{
  "error": "NOT_FOUND",
  "message": "Theme not found with ID: 550e8400-e29b-41d4-a716-446655440000",
  "cause": null
}
```

## Status Codes

| Code | Condition |
|------|-----------|
| `202` | Batch created and generation started (returns immediately) |
| `400` | Validation error (empty `subtopicIds`, missing `themeId`) |
| `404` | Theme or a subtopic ID not found |

## Implementation Notes

- Returns `202` **before** any LLM generation completes — the response is nearly instant.
- Generation runs in a background `@Async` worker (`batchExecutor`): snacks sequentially (so one READY snack is never blocked by a later one), quizzes in parallel per subtopic (prefetch, FR-003).
- `batchId` is the handle for `GET /api/batches/{batchId}` polling.
- A single batch may include subtopics of any source (AI-suggested or user-proposed) — all are processed identically.
