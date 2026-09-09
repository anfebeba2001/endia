# API Contract: POST /api/snacks/generate

**Purpose**: Generate knowledge snack cards for selected subtopics. Each snack contains a concept definition, real-life metaphor, "why it matters" section, and related concept links.

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
| `themeId` | `string (UUID)` | Yes | Valid UUID | ID of the theme (from `/api/themes/suggest`) |
| `subtopicIds` | `array of string (UUID)` | Yes | `minItems: 1`, `maxItems: 10` | IDs of selected subtopics to generate snacks for |

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
      "concept": "The light reactions are the first stage of photosynthesis, occurring in the thylakoid membranes of chloroplasts. They convert light energy into chemical energy (ATP and NADPH) by splitting water molecules and releasing oxygen as a byproduct.",
      "metaphor": "Think of the light reactions as a solar-powered factory floor. Sunlight hits the solar panels (chlorophyll), which power machines (enzymes) that split water bottles (H₂O) into hydrogen and oxygen. The hydrogen gets packaged into energy batteries (NADPH) and currency coins (ATP) for the next factory stage.",
      "whyItMatters": "Without light reactions, there's no ATP or NADPH to power the Calvin cycle — the entire food chain depends on this conversion of light to chemical energy. Common pitfall: confusing light reactions with the Calvin cycle, which happens in the stroma, not the thylakoid.",
      "related": ["Calvin cycle", "Chloroplast structure", "Photophosphorylation"]
    },
    {
      "id": "770e8400-e29b-41d4-a716-446655440002",
      "subtopicId": "660e8400-e29b-41d4-a716-446655440002",
      "subtopicName": "Calvin cycle",
      "concept": "The Calvin cycle is the second stage of photosynthesis, occurring in the stroma of chloroplasts. It uses ATP and NADPH from the light reactions to fix atmospheric CO₂ into organic molecules (G3P), which are then used to build glucose.",
      "metaphor": "Imagine the Calvin cycle as a 3D printer in a factory. The raw material is CO₂ (like plastic filament), and the energy batteries (ATP) and currency (NADPH) from the light reactions power the printer to assemble sugar molecules layer by layer.",
      "whyItMatters": "The Calvin cycle is how plants turn air into food. It's the foundation of nearly all life on Earth — every calorie you've ever consumed traces back to carbon fixed by this cycle. Common pitfall: thinking the Calvin cycle needs light directly (it doesn't — it needs the products of light reactions).",
      "related": ["Light reactions", "Carbon fixation", "Photorespiration"]
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `snacks` | `array` | One snack card per requested subtopic |
| `snacks[].id` | `string (UUID)` | ID of the persisted snack entity |
| `snacks[].subtopicId` | `string (UUID)` | ID of the subtopic this snack belongs to |
| `snacks[].subtopicName` | `string` | Name of the subtopic (for display) |
| `snacks[].concept` | `string` | One-paragraph concept definition |
| `snacks[].metaphor` | `string` | Real-life analogy or metaphor |
| `snacks[].whyItMatters` | `string` | Stakes, pitfalls, importance |
| `snacks[].related` | `array of string` | 2–3 related concept names |

### 400 Bad Request — Validation Error

```json
{
  "error": "VALIDATION_ERROR",
  "message": "At least one subtopic ID is required",
  "field": "subtopicIds"
}
```

### 404 Not Found — Theme or Subtopic Not Found

```json
{
  "error": "NOT_FOUND",
  "message": "Theme not found with ID: 550e8400-e29b-41d4-a716-446655440000"
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
| `200` | Success — snacks generated and returned |
| `400` | Validation error (missing themeId, empty subtopicIds) |
| `404` | Theme or subtopic ID not found in database |
| `503` | LLM provider unavailable or timed out |

## Implementation Notes

- Each snack is generated independently via LLM (one call per subtopic).
- All snack fields are validated for non-empty content before returning. If LLM returns an empty field, the system re-rolls that snack once.
- Snacks are persisted in the database before returning the response.
- LLM timeout per snack: 30 seconds. If any snack generation fails, the entire request returns 503.
