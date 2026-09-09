# API Contract: POST /api/quiz/answer

**Purpose**: Submit an answer to a quiz question. Returns whether the answer was correct, the correct answer index, an explanation, and XP earned. Also supports retrieving a quiz summary after all questions are answered.

## Part 1: Submit Answer

### Request

**Method**: `POST`
**Path**: `/api/quiz/answer`
**Content-Type**: `application/json`

#### Request Body

```json
{
  "questionId": "string",
  "chosenIndex": 2
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `questionId` | `string (UUID)` | Yes | Valid UUID | ID of the question being answered |
| `chosenIndex` | `integer` | Yes | `min: 0`, `max: 3` | Index of the selected option (0-based) |

#### Example Request

```json
{
  "questionId": "880e8400-e29b-41d4-a716-446655440001",
  "chosenIndex": 1
}
```

### Response

#### 200 OK — Answer Result

```json
{
  "questionId": "880e8400-e29b-41d4-a716-446655440001",
  "correct": true,
  "correctIndex": 1,
  "explanation": "Photosystem II is responsible for absorbing light energy and using it to split water molecules (photolysis), which generates ATP through chemiosmosis via the electron transport chain.",
  "xpEarned": 10
}
```

| Field | Type | Description |
|-------|------|-------------|
| `questionId` | `string (UUID)` | ID of the answered question |
| `correct` | `boolean` | Whether the chosen answer was correct |
| `correctIndex` | `integer` | Index of the correct answer (0–3) |
| `explanation` | `string` | Explanation of why the correct answer is correct |
| `xpEarned` | `integer` | XP earned for this answer (0 or 10) |

#### 400 Bad Request — Invalid Option Index

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Option index must be between 0 and 3",
  "field": "chosenIndex"
}
```

#### 404 Not Found — Question Not Found

```json
{
  "error": "NOT_FOUND",
  "message": "Question not found with ID: 880e8400-e29b-41d4-a716-446655440001"
}
```

#### 409 Conflict — Already Answered

```json
{
  "error": "CONFLICT",
  "message": "This question has already been answered"
}
```

### Status Codes

| Code | Condition |
|------|-----------|
| `200` | Success — answer processed |
| `400` | Validation error (invalid chosenIndex) |
| `404` | Question ID not found |
| `409` | Question was already answered |

---

## Part 2: Quiz Summary

After all questions in a quiz have been answered, the frontend can retrieve a summary.

### Request

**Method**: `GET`
**Path**: `/api/quiz/summary/{subtopicId}`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `subtopicId` | `string (UUID)` | ID of the subtopic whose quiz summary to retrieve |

### Response

#### 200 OK — Quiz Summary

```json
{
  "subtopicId": "660e8400-e29b-41d4-a716-446655440001",
  "totalQuestions": 4,
  "correctAnswers": 3,
  "totalXp": 30,
  "questions": [
    {
      "questionId": "880e8400-e29b-41d4-a716-446655440001",
      "prompt": "What is the primary role of Photosystem II in the light reactions?",
      "options": [
        "Fixing CO₂ into glucose using ATP energy",
        "Absorbing light to split water and generate ATP via electron transport",
        "Producing NADPH by transferring electrons to Photosystem I",
        "Converting glucose into chemical energy for cellular use"
      ],
      "chosenIndex": 1,
      "correct": true,
      "correctIndex": 1,
      "explanation": "Photosystem II is responsible for absorbing light energy and using it to split water molecules..."
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `subtopicId` | `string (UUID)` | ID of the subtopic |
| `totalQuestions` | `integer` | Total number of questions in the quiz |
| `correctAnswers` | `integer` | Number of correctly answered questions |
| `totalXp` | `integer` | Total XP earned (base + bonus) |
| `questions` | `array` | Per-question breakdown |
| `questions[].questionId` | `string (UUID)` | Question ID |
| `questions[].prompt` | `string` | Question text |
| `questions[].options` | `array of string` | The 4 options |
| `questions[].chosenIndex` | `integer` | User's chosen option index |
| `questions[].correct` | `boolean` | Whether the answer was correct |
| `questions[].correctIndex` | `integer` | Index of the correct answer |
| `questions[].explanation` | `string` | Explanation of the correct answer |

#### 404 Not Found — No Quiz Found

```json
{
  "error": "NOT_FOUND",
  "message": "No quiz found for subtopic: 660e8400-e29b-41d4-a716-446655440001"
}
```

### Status Codes

| Code | Condition |
|------|-----------|
| `200` | Success — summary returned |
| `404` | No quiz questions found for this subtopic |

---

## XP Calculation Logic

| Condition | XP |
|-----------|-----|
| Correct answer | +10 XP |
| Incorrect answer | +0 XP |
| Perfect quiz (all correct) | +50 bonus XP added to total |

**Examples**:
- 4/4 correct → 4 × 10 + 50 = **90 XP**
- 3/4 correct → 3 × 10 = **30 XP** (no bonus)
- 0/4 correct → 0 XP

## Implementation Notes

- The `correctIndex` and `explanation` are NOT sent in the quiz generation response — they are revealed only after the user submits an answer.
- Duplicate answers to the same question return 409 Conflict.
- XP is calculated server-side and included in the response. It is NOT persisted to any user profile in MVP.
- The quiz summary endpoint aggregates all answer attempts for questions in the given subtopic.
