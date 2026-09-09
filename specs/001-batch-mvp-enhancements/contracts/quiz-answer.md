# API Contract: POST /api/quiz/answer

**Purpose**: Submit an answer to a quiz question. Supports **both** question types — multiple-choice (`chosenIndex`) and drag-and-drop (`arrangement`). Returns correctness, the correct answer (correct index or canonical arrangement), an explanation, and XP earned. Also supports the quiz summary (with snack recap).

## Part 1: Submit Answer

### Request

**Method**: `POST`
**Path**: `/api/quiz/answer`
**Content-Type**: `application/json`

#### Request Body (multiple-choice)

```json
{
  "questionId": "string",
  "chosenIndex": 2
}
```

#### Request Body (drag-and-drop)

```json
{
  "questionId": "string",
  "arrangement": ["string"]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `questionId` | `string (UUID)` | Yes | ID of the question being answered |
| `chosenIndex` | `integer` | MCQ only (`min:0`,`max:3`) | Selected option index |
| `arrangement` | `array of string` | Drag-drop only | The user's arrangement, `type`-specific: |
| `arrangement[]` | `string` | – | **SEQUENCING**: item ids in the user-chosen order.<br>**CLASSIFICATION**: `["itemId:category", "itemId:category", …]` (each item assigned to a category).<br>**MATCHING**: `["leftItem:rightItem", "leftItem:rightItem", …]`. |

The payload must carry exactly one of `chosenIndex` (for `MULTIPLE_CHOICE`) or `arrangement` (for `DRAG_DROP`), matching the question's `type`. A mismatch is a `400`.

### Grading (FR-008)

Graded **server-side** against the stored canonical arrangement (source of truth):
- **SEQUENCING**: correct iff the submitted id order equals the stored `correctOrder`.
- **CLASSIFICATION**: correct iff every item-id's submitted category equals its stored category.
- **MATCHING**: correct iff every left's submitted right value equals the stored pair.

Same scoring as MCQ: `+10 XP` correct, `0` incorrect (FR-008 — included in the same score/XP summary).

#### Example Request (drag-drop / sequencing)

```json
{
  "questionId": "880e8400-e29b-41d4-a716-446655440002",
  "arrangement": [
    "Photon absorbed by Photosystem II",
    "Water splits, releasing O₂ and electrons",
    "Electrons pass through the electron transport chain",
    "NADP⁺ reduced to NADPH"
  ]
}
```

### Response

#### 200 OK — Answer Result

```json
{
  "questionId": "880e8400-e29b-41d4-a716-446655440002",
  "type": "DRAG_DROP",
  "correct": true,
  "correctAnswer": {
    "arrangement": [
      "Photon absorbed by Photosystem II",
      "Water splits, releasing O₂ and electrons",
      "Electrons pass through the electron transport chain",
      "NADP⁺ reduced to NADPH"
    ]
  },
  "explanation": "…",
  "xpEarned": 10
}
```

| Field | Type | Description |
|-------|------|-------------|
| `questionId` | `string (UUID)` | Answered question |
| `type` | `string` | `MULTIPLE_CHOICE` or `DRAG_DROP` |
| `correct` | `boolean` | Whether the answer was correct |
| `correctIndex` | `integer \| null` | MCQ only (existing field): correct option index |
| `correctAnswer` | `object \| null` | Drag-drop only: the canonical arrangement, revealed after answering |
| `explanation` | `string` | Explanation of the correct answer |
| `xpEarned` | `integer` | `10` correct / `0` incorrect |

For an MCQ answer, the response is unchanged from the prior contract (`correctIndex` returned; `correctAnswer` is null).

#### 400 Bad Request — Invalid Argument

```json
{ "error": "VALIDATION_ERROR", "message": "arrangement is required for drag-drop questions", "field": "arrangement", "cause": null }
```

#### 404 Not Found

```json
{ "error": "NOT_FOUND", "message": "Question not found with ID: 880e8400-e29b-41d4-a716-446655440002", "cause": null }
```

#### 409 Conflict — Already Answered

```json
{ "error": "CONFLICT", "message": "This question has already been answered. Try the full-test summary instead.", "cause": null }
```

### Status Codes

| Code | Condition |
|------|-----------|
| `200` | Answer processed |
| `400` | Validation error (invalid `chosenIndex`, missing/invalid `arrangement`, payload/type mismatch) |
| `404` | Question not found |
| `409` | Question already answered |

---

## Part 2: Quiz Summary (with snack recap)

After all questions in a quiz have been answered, retrieve the summary. **This response now includes an end-of-test snack recap** (FR-009/FR-010).

### Request

**Method**: `GET`
**Path**: `/api/quiz/summary/{subtopicId}`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `subtopicId` | `string (UUID)` | Subtopic whose quiz summary to retrieve |

### Response

#### 200 OK — Quiz Summary with Recap

```json
{
  "subtopicId": "660e8400-e29b-41d4-a716-446655440001",
  "totalQuestions": 5,
  "correctAnswers": 4,
  "totalXp": 40,
  "questions": [
    {
      "questionId": "880e8400-e29b-41d4-a716-446655440001",
      "type": "MULTIPLE_CHOICE",
      "prompt": "…",
      "options": ["…", "…", "…", "…"],
      "chosenIndex": 1,
      "correct": true,
      "correctIndex": 1,
      "explanation": "…",
      "variant": null,
      "dragDrop": null
    },
    {
      "questionId": "880e8400-e29b-41d4-a716-446655440002",
      "type": "DRAG_DROP",
      "prompt": "Order the steps…",
      "chosenIndex": null,
      "correct": false,
      "correctIndex": null,
      "explanation": "…",
      "variant": "SEQUENCING",
      "correctArrangement": ["Step A", "Step B", "Step C"],
      "submittedArrangement": ["Step A", "Step C", "Step B"]
    }
  ],
  "recap": {
    "subtopicId": "660e8400-e29b-41d4-a716-446655440001",
    "subtopicName": "Light reactions",
    "snack": {
      "id": "770e8400-e29b-41d4-a716-446655440001",
      "subtopicId": "660e8400-e29b-41d4-a716-446655440001",
      "subtopicName": "Light reactions",
      "concept": "…",
      "metaphor": "…",
      "whyItMatters": "…",
      "related": ["Calvin cycle", "Chloroplast structure"]
    }
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `recap` | `object \| null` | End-of-test snack recap (FR-009). Null if the snack is unavailable (graceful degradation, SC-011). |
| `recap.subtopicId` | `string (UUID)` | Subtopic the test was about |
| `recap.subtopicName` | `string` | **Label** identifying which subtopic/snack the test was about (FR-010) |
| `recap.snack` | `object \| null` | The snack card for re-reading; null → renders label only |

Per-question breakdown drag-drop fields: `correctArrangement` (canonical) and `submittedArrangement` (user's) are exposed here — after the test is complete, so no cheating risk.

#### 404 Not Found — No Quiz Found

```json
{ "error": "NOT_FOUND", "message": "No quiz found for subtopic: 660e8400-e29b-41d4-a716-446655440001", "cause": null }
```

### Status Codes

| Code | Condition |
|------|-----------|
| `200` | Summary returned with snack recap |
| `404` | No quiz questions found for this subtopic |

## XP Calculation (unchanged)

| Condition | XP |
|-----------|-----|
| Correct answer (either type) | +10 XP |
| Incorrect answer | +0 XP |
| Perfect quiz (all correct) | +50 bonus |

**Examples**: 5/5 → 5×10+50 =**100**; 4/5 → **40** (no bonus); 0/5 → 0.

## Implementation Notes

- `correctIndex`/`explanation` are still withheld until answer. For drag-drop, the canonical `correctArrangement` is withheld until answer.
- Duplicate answers to the same question return `409` (unique constraint moved to `quiz_question_id` only).
- The recap is derived from the subtopic's snack; degrades to label-only if the snack was not persisted.
