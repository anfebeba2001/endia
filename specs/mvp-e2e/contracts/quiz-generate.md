# API Contract: POST /api/quiz/generate

**Purpose**: Generate a multiple-choice quiz (4–5 questions) for a specific subtopic. Each question has exactly 4 options (1 correct + 3 plausible distractors). All questions pass through a quality gate before serving.

## Request

**Method**: `POST`
**Path**: `/api/quiz/generate`
**Content-Type**: `application/json`

### Request Body

```json
{
  "themeId": "string",
  "subtopicId": "string",
  "count": 4
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `themeId` | `string (UUID)` | Yes | Valid UUID | ID of the theme (for LLM context) |
| `subtopicId` | `string (UUID)` | Yes | Valid UUID | ID of the subtopic to quiz on |
| `count` | `integer` | No | `min: 4`, `max: 5`, default `4` | Number of questions to generate |

### Example Request

```json
{
  "themeId": "550e8400-e29b-41d4-a716-446655440000",
  "subtopicId": "660e8400-e29b-41d4-a716-446655440001",
  "count": 4
}
```

## Response

### 200 OK — Generated Quiz

```json
{
  "subtopicId": "660e8400-e29b-41d4-a716-446655440001",
  "questions": [
    {
      "id": "880e8400-e29b-41d4-a716-446655440001",
      "prompt": "What is the primary role of Photosystem II in the light reactions?",
      "options": [
        "Fixing CO₂ into glucose using ATP energy",
        "Absorbing light to split water and generate ATP via electron transport",
        "Producing NADPH by transferring electrons to Photosystem I",
        "Converting glucose into chemical energy for cellular use"
      ]
    },
    {
      "id": "880e8400-e29b-41d4-a716-446655440002",
      "prompt": "Which molecule is the final electron acceptor in the light-dependent reactions?",
      "options": [
        "Water (H₂O)",
        "Carbon dioxide (CO₂)",
        "NADP⁺",
        "Oxygen (O₂)"
      ]
    },
    {
      "id": "880e8400-e29b-41d4-a716-446655440003",
      "prompt": "Where does the Calvin cycle take place in the chloroplast?",
      "options": [
        "Inner membrane",
        "Thylakoid lumen",
        "Stroma",
        "Outer membrane"
      ]
    },
    {
      "id": "880e8400-e29b-41d4-a716-446655440004",
      "prompt": "What is the primary function of ATP synthase in photosynthesis?",
      "options": [
        "Splitting water molecules into hydrogen and oxygen",
        "Synthesizing ATP from ADP and inorganic phosphate using proton gradient",
        "Capturing light energy in chlorophyll molecules",
        "Fixing carbon dioxide into three-carbon sugar molecules"
      ]
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `subtopicId` | `string (UUID)` | ID of the subtopic the quiz is for |
| `questions` | `array` | 4–5 question objects |
| `questions[].id` | `string (UUID)` | ID of the persisted question entity |
| `questions[].prompt` | `string` | The question text |
| `questions[].options` | `array of string` | Exactly 4 option strings (shuffled) |

**Note**: The response does NOT include `correctIndex` or `explanation` — these are server-side only until the user submits an answer. This prevents cheating via browser dev tools.

### 400 Bad Request — Validation Error

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Count must be between 4 and 5",
  "field": "count"
}
```

### 404 Not Found — Subtopic Not Found

```json
{
  "error": "NOT_FOUND",
  "message": "Subtopic not found with ID: 660e8400-e29b-41d4-a716-446655440001"
}
```

### 503 Service Unavailable — LLM Unavailable

```json
{
  "error": "SERVICE_UNAVAILABLE",
  "message": "The AI service is temporarily unavailable. Please try again later."
}
```

## Status Codes

| Code | Condition |
|------|-----------|
| `200` | Success — quiz questions generated and returned |
| `400` | Validation error (invalid count, missing fields) |
| `404` | Subtopic ID not found in database |
| `503` | LLM provider unavailable or timed out |

## Quality Gate

Before returning, each question passes through `QuizQualityGate`:

1. **Option count**: Exactly 4 options. If not → reject.
2. **Duplicate options**: No two options are identical (case-insensitive trimmed comparison). If duplicates → reject.
3. **Minimum length**: No option shorter than 5 characters. If any → reject.
4. **Shuffle**: Correct answer is placed at a random position among the 4 options. `correctIndex` is updated accordingly.

If a question fails the gate, the system re-rolls it once (max 1 retry). If the re-roll also fails, the best available result is served and a warning is logged.

## Implementation Notes

- `correctIndex` is NOT in the API response. It is stored server-side and returned only via `POST /api/quiz/answer`.
- The `explanation` is also stored server-side and revealed only after the user answers.
- Questions are persisted in the database before returning the response.
- LLM timeout: 30 seconds per question generation batch.
