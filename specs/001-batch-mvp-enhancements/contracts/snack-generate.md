# API Contract: POST /api/snacks/generate

**Purpose**: Generate knowledge snack cards for selected subtopics (single-shot form). Each snack contains a concept definition, real-life metaphor, "why it matters" section, and related concept links.

> **Relation to batch flow**: The live progressive flow uses `POST /api/batches` + `GET /api/batches/{batchId}`. This endpoint remains for backward-compatibility, unit testing, and the existing E2E suite (SC-012). It generates **all** requested snacks synchronously and returns them together.

## Request

**Method**: `POST`
**Path**: `/api/snacks/generate`
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
| `themeId` | `string (UUID)` | Yes | Valid UUID | Theme ID (from `/api/themes/suggest`) |
| `subtopicIds` | `array of string (UUID)` | Yes | `minItems: 1`, `maxItems: 10` | Selected subtopics (AI-suggested or user-proposed) |

### Example Request

```json
{
  "themeId": "550e8400-e29b-41d4-a716-446655440000",
  "subtopicIds": [
    "660e8400-e29b-41d4-a716-446655440001",
    "660e8400-e29b-41d4-a716-446655440002"
  ]
}
```

## Response

### 200 OK — Generated Snacks

```json
{
  "snacks": [
    {
      "id": "770e8400-e29b-41d4-a716-446655440001",
      "subtopicId": "660e8400-e29b-41d4-a716-446655440001",
      "subtopicName": "Light reactions",
      "concept": "…",
      "metaphor": "…",
      "whyItMatters": "…",
      "related": ["Calvin cycle", "Chloroplast structure", "Photophosphorylation"]
    },
    {
      "id": "770e8400-e29b-41d4-a716-446655440002",
      "subtopicId": "660e8400-e29b-41d4-a716-446655440002",
      "subtopicName": "Calvin cycle",
      "concept": "…",
      "metaphor": "…",
      "whyItMatters": "…",
      "related": ["Light reactions", "Carbon fixation", "Photorespiration"]
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `snacks` | `array` | One snack card per requested subtopic |
| `snacks[].id` | `string (UUID)` | Persisted snack ID |
| `snacks[].subtopicId` | `string (UUID)` | Subtopic this snack belongs to |
| `snacks[].subtopicName` | `string` | Subtopic name (display) |
| `snacks[].concept` | `string` | Concept definition |
| `snacks[].metaphor` | `string` | Analogy / metaphor |
| `snacks[].whyItMatters` | `string` | Stakes / pitfalls |
| `snacks[].related` | `array of string` | 2–3 related concept names |

### 400 Bad Request — Validation Error

```json
{ "error": "VALIDATION_ERROR", "message": "At least one subtopic ID is required", "field": "subtopicIds", "cause": null }
```

### 404 Not Found

```json
{ "error": "NOT_FOUND", "message": "Theme not found with ID: 550e8400-e29b-41d4-a716-446655440000", "cause": null }
```

### Typed LLM errors (FR-015)

```json
{ "error": "QUOTA", "message": "The AI service quota or rate limit was reached. Try again shortly.", "cause": "QUOTA" }
```

## Status Codes

| Code | Condition |
|------|-----------|
| `200` | Snacks generated and returned |
| `400` | Validation error |
| `404` | Theme / subtopic not found |
| `429` | LLM quota/rate-limit reached |
| `503` | LLM unavailable / timeout / network |

## Implementation Notes

- Each snack generated independently (one LLM call per subtopic); auto-retried up to 3 attempts (FR-016) — if any snack still fails after retries, this synchronous endpoint returns a typed 5xx error (the batch endpoint handles partial failures gracefully instead).
- All snack fields validated non-empty before return; empty field → re-roll once, per existing convention.
- Snacks persisted before returning.
- The retry/spinner UI is driven by the batch-status `retrying` flag in the live flow (FR-017).
