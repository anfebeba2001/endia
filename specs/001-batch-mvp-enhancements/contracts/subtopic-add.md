# API Contract: POST /api/themes/{themeId}/subtopics

**Purpose**: Let the user propose their own free-text subtopics to add to a theme's subtopic set (FR-019). Added subtopics are persisted with `source = USER_PROPOSED` and processed exactly like AI-suggested ones (snack + quiz). Deduplicated against existing theme subtopics (AI-suggested and already-added user ones) and within the request (FR-021).

> This is the add-after-suggest flow: the theme and its AI suggestions already exist; the user enriches them on the subtopics screen.

## Request

**Method**: `POST`
**Path**: `/api/themes/{themeId}/subtopics`
**Content-Type**: `application/json`

### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `themeId` | `string (UUID)` | Theme the subtopics belong to |

### Request Body

```json
{
  "names": ["string"]
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `names` | `array of string` | Yes | `minItems: 1`, `maxItems: 10`; each `minLength: 2`, `maxLength: 200` | Free-text subtopic names the user proposes |

### Example Request

```json
{
  "names": [
    "Photorespiration",
    "C4 photosynthesis pathway"
  ]
}
```

## Response

### 200 OK — Added Subtopics

```json
{
  "themeId": "550e8400-e29b-41d4-a716-446655440000",
  "added": [
    {
      "id": "660e8400-e29b-41d4-a716-446655440009",
      "name": "C4 photosynthesis pathway",
      "source": "USER_PROPOSED"
    }
  ],
  "duplicates": ["Photorespiration"]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `themeId` | `string (UUID)` | The theme |
| `added` | `array` | Newly persisted subtopics (deduped; duplicates excluded here) |
| `added[].id` | `string (UUID)` | Persisted subtopic ID |
| `added[].name` | `string` | Subtopic name |
| `added[].source` | `string` | `USER_PROPOSED` |
| `duplicates` | `array of string` | Names that matched an existing subtopic (AI-suggested or user-proposed) or another name in this request and were **not** persisted |

**Dedup reported**: duplicate names are echoed in `duplicates` so the frontend can show "already covered" feedback (FR-021).

### 400 Bad Request — Validation / Empty Name

```json
{ "error": "VALIDATION_ERROR", "message": "Subtopic name must be between 2 and 200 characters", "field": "names", "cause": null }
```
Blank/empty names are rejected and never persisted.

### 404 Not Found — Theme Not Found

```json
{ "error": "NOT_FOUND", "message": "Theme not found with ID: 550e8400-e29b-41d4-a716-446655440000", "cause": null }
```

## Status Codes

| Code | Condition |
|------|-----------|
| `200` | Subtopics added (some may be reported as duplicates) |
| `400` | Validation error (empty name, too short/long, missing `names`) |
| `404` | Theme not found |

## Implementation Notes

- **Dedup normalization** (FR-021): `trim().toLowerCase()`, internal whitespace collapsed to single spaces. A proposed name matching an existing subtopic (any source) or another name in the request is skipped and echoed in `duplicates` — only one entry is kept/shown.
- Persisted subtopics get `source = USER_PROPOSED`; they are ordinary subtopics thereafter, so the batch flow (snacks + then quiz) processes them identically to AI-suggested ones (FR-020).
- The frontend should merge `added` into its current selected-subtopy list and reflect `duplicates`.
- No LLM call is involved in this request — the dedup/value validation is deterministic; the LLM is only later consulted for snack/quiz generation via the batch flow.
