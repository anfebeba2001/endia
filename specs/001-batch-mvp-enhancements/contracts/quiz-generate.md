# API Contract: POST /api/quiz/generate

**Purpose**: Generate a quiz for a specific subtopic. The quiz now mixes **multiple-choice** questions (existing) with **drag-and-drop** questions (new). Both types coexist (FR-007); the drag-drop variant is chosen once per quiz (FR-006). All questions pass through the quality gate before serving.

## Request

**Method**: `POST`
**Path**: `/api/quiz/generate`
**Content-Type**: `application/json`

### Request Body

```json
{
  "themeId": "string",
  "subtopicId": "string",
  "count": 5
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `themeId` | `string (UUID)` | Yes | Valid UUID | Theme (LLM context) |
| `subtopicId` | `string (UUID)` | Yes | Valid UUID | Subtopic to quiz on |
| `count` | `integer` | No | `min: 4`, `max: 5`, default `4` | Number of questions total |

The server picks the drag-drop `variant` (see response) at random among `SEQUENCING`, `CLASSIFICATION`, `MATCHING` — it is **not** sent by the client.

### Example Request

```json
{
  "themeId": "550e8400-e29b-41d4-a716-446655440000",
  "subtopicId": "660e8400-e29b-41d4-a716-446655440001",
  "count": 5
}
```

## Response

### 200 OK — Generated Quiz (mixed types)

```json
{
  "subtopicId": "660e8400-e29b-41d4-a716-446655440001",
  "variant": "SEQUENCING",
  "questions": [
    {
      "id": "880e8400-e29b-41d4-a716-446655440001",
      "type": "MULTIPLE_CHOICE",
      "prompt": "What is the primary role of Photosystem II in the light reactions?",
      "options": [
        "Fixing CO₂ into glucose using ATP energy",
        "Absorbing light to split water and generate ATP via electron transport",
        "Producing NADPH by transferring electrons to Photosystem I",
        "Converting glucose into chemical energy for cellular use"
      ],
      "variant": null,
      "dragDrop": null
    },
    {
      "id": "880e8400-e29b-41d4-a716-446655440002",
      "type": "DRAG_DROP",
      "prompt": "Order the steps of the light-dependent reactions from start to finish.",
      "options": [],
      "variant": "SEQUENCING",
      "dragDrop": {
        "items": [
          "Photon absorbed by Photosystem II",
          "Water splits, releasing O₂ and electrons",
          "Electrons pass through the electron transport chain",
          "NADP⁺ reduced to NADPH"
        ]
      }
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `subtopicId` | `string (UUID)` | Subtopic the quiz is for |
| `variant` | `string \| null` | Drag-drop variant used for this quiz (`SEQUENCING`/`CLASSIFICATION`/`MATCHING`), null if no drag-drop question generated |
| `questions` | `array` | 4–5 question objects |
| `questions[].id` | `string (UUID)` | Persisted question ID |
| `questions[].type` | `string` | `MULTIPLE_CHOICE` or `DRAG_DROP` |
| `questions[].prompt` | `string` | Question text |
| `questions[].options` | `array of string` | MCQ only: exactly 4 options (shuffled). Empty for drag-drop. |
| `questions[].variant` | `string \| null` | Drag-drop variant (drag-drop only) |
| `questions[].dragDrop` | `object \| null` | Drag-drop payload (see below). Null for MCQ. |

### `dragDrop` payload by variant

The client receives only the **material to arrange/classify/match**, never the correct arrangement.

```json
// SEQUENCING — items to put in order
{ "items": ["Step A", "Step B", "Step C", "Step D"] }

// CLASSIFICATION — items to drop into categories
{
  "items": ["Earth", "Mars", "Jupiter", "Venus"],
  "categories": ["Rocky planet", "Gas giant"]
}

// MATCHING — left items to match to right items
{
  "items": ["H₂O", "CO₂", "NADP⁺", "ATP"],
  "rightItems": ["Electron donor", "Carbon source", "Electron acceptor", "Energy currency"]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `questions[].dragDrop.items` | `array of string` | Draggable tokens (for sequencing: items to order; classification: objects; matching: left column). |
| `questions[].dragDrop.categories` | `array of string` | Classification only (drop targets). |
| `questions[].dragDrop.rightItems` | `array of string` | Matching only (right column). |

**Security note**: The correct arrangement (`correctOrder`, per-item category assignments, matching pairs) is **server-side only** and is returned only via `POST /api/quiz/answer` after submission — never in this response. This mirrors the existing `correctIndex`/`explanation` concealment.

### 400 Bad Request — Validation Error

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Count must be between 4 and 5",
  "field": "count",
  "cause": null
}
```

### 404 Not Found — Subtopic Not Found

```json
{ "error": "NOT_FOUND", "message": "Subtopic not found with ID: 660e8400-e29b-41d4-a716-446655440001", "cause": null }
```

### Typed LLM errors (FR-015)

```json
{ "error": "QUOTA", "message": "The AI service quota or rate limit was reached. Try again shortly.", "cause": "QUOTA" }
```
(Quota → 429; unavailable/timeout/network → 503; malformed → 400. See `error-response.md`.)

## Status Codes

| Code | Condition |
|------|-----------|
| `200` | Quiz generated (mixed MCQ + drag-drop) |
| `400` | Validation error |
| `404` | Subtopic not found |
| `429` | LLM quota/rate-limit reached |
| `503` | LLM unavailable / timeout / network |

## Quality Gate

Each question passes `QuizQualityGate` before persistence:
- MCQ: 4 unique ≥5-char options, correctIndex in range, plausible length (existing).
- Drag-drop: variant in allowed set, non-empty distinct `items`, complete canonical arrangement composed of exactly `items`, valid categories/pairs. Missing/invalid structure → reject (FR-005, Constitution IV).
- Rejected question → re-roll (max 1) per existing convention; never serve invalid content.

## Implementation Notes

- This is the **backward-compatible** single-subtopic endpoint (kept for E2E/flexibility). The live flow uses the batch endpoint + `quiz-summary` for prefetched quizzes, but this endpoint remains functional.
- The server randomly selects the dash-variant for the quiz (FR-006). On a reshuffle the variant may differ per generation.
- `correctIndex`/`explanation`/correct arrangement are intentionally absent from `MULTIPLE_CHOICE`/drag-drop responses, revealed only on answer.
