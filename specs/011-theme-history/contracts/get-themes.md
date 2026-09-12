# Contract: `GET /api/themes`

**Feature**: `011-theme-history` | **Requirement**: FR-001..FR-008, SC-001..SC-004

Read-only endpoint returning the complete history of created themes, newest first, with their
subtopics, snacks, and quiz questions. This is the only new HTTP surface in the feature; it is
additive and does not alter any existing endpoint.

---

## Request

```http
GET /api/themes
```

- **Method / path**: `GET /api/themes` (on `ThemeController`, base `@RequestMapping("/api/themes")`).
- **Path/query parameters**: none.
- **Request body**: none.
- **Headers**: standard (`Accept: application/json`). No auth in the MVP (global history).

---

## Success Response — `200 OK`

Content-Type: `application/json`. The body is a **JSON array** (not a wrapper object) of theme
entries ordered by `createdAt` **descending**.

```json
[
  {
    "themeId": "3f1c9a52-7b6e-4c1a-9d2f-0e5b8a1c4d77",
    "prompt": "Photosynthesis",
    "createdAt": "2026-09-11T19:52:00",
    "subtopics": [
      {
        "id": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
        "name": "Light reactions",
        "snacks": [
          {
            "id": "5b6c7d8e-9f0a-4b1c-8d2e-3f4a5b6c7d8e",
            "concept": "Chlorophyll absorbs light energy to drive the photosynthetic process.",
            "metaphor": "Imagine the chloroplast as a solar-powered kitchen.",
            "whyItMatters": "Understanding this prevents the misconception that plants only photosynthesize during the day.",
            "related": ["ATP synthase", "Electron transport", "Photophosphorylation"]
          }
        ],
        "quizQuestions": [
          {
            "id": "9c8d7e6f-5a4b-4c3d-8e2f-1a0b9c8d7e6f",
            "type": "MULTIPLE_CHOICE",
            "prompt": "What is the primary role of chlorophyll in photosynthesis?",
            "explanation": "Chlorophyll absorbs light energy to drive the photosynthetic process."
          }
        ]
      }
    ]
  }
]
```

### Field semantics

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| `themeId` | string (UUID) | no | `Theme.id`. |
| `prompt` | string | no | `Theme.prompt` (user-entered). |
| `createdAt` | string (ISO-8601 local date-time) | no | `Theme.createdAt`, e.g. `2026-09-11T19:52:00`. |
| `subtopics` | array | no | May be empty (`[]`) when a theme has no subtopics. |
| `subtopics[].id` | string (UUID) | no | `Subtopic.id`. |
| `subtopics[].name` | string | no | `Subtopic.name`. |
| `subtopics[].snacks` | array | no | May be empty when nothing was generated. |
| `subtopics[].quizQuestions` | array | no | May be empty when nothing was generated. |
| `snacks[].id` | string (UUID) | no | |
| `snacks[].concept` | string | no | |
| `snacks[].metaphor` | string | no | |
| `snacks[].whyItMatters` | string | no | Serialized from `why_it_matters`. |
| `snacks[].related` | array of string | no | JSON-backed; may be empty. |
| `quizQuestions[].id` | string (UUID) | no | |
| `quizQuestions[].type` | enum string | no | `MULTIPLE_CHOICE` or `DRAG_DROP`. |
| `quizQuestions[].prompt` | string | no | |
| `quizQuestions[].explanation` | string | no | |

Intentionally **omitted** (Non-Goals): question `options`, `correctIndex`, `variant`,
`dragDrop`/`dragDropContent`, answer attempts, `Subtopic.selected`, `Subtopic.source`.

### Ordering guarantee

Entries are ordered by `createdAt` **descending** (`JpaThemeRepository.findAllByOrderByCreatedAtDesc()`).
Ties (equal/very close timestamps) are acceptable and may appear in any relative order (Edge Cases).

---

## Empty Response — `200 OK`

When no themes exist, the endpoint returns HTTP **200** with an empty JSON array. It is **not** a 404
and must not error (FR-005/SC-003).

```json
[]
```

On the frontend this drives the empty-state message and renders zero entries.

---

## Error Responses

No feature-specific error handling is added. Failures flow through the existing
`GlobalExceptionHandler` and use its `ErrorResponse` shape:

```json
{
  "error": "INTERNAL_ERROR",
  "message": "An unexpected error occurred",
  "field": null,
  "cause": null
}
```

| Status | `error` | When |
|--------|---------|------|
| `500 Internal Server Error` | `INTERNAL_ERROR` | Unexpected server failure while reading history. |
| `500 Internal Server Error` | `INTERNAL_ERROR` | Any unhandled `RuntimeException` (e.g. a JSON conversion failure). |

There is no `400`/`404` path: the request has no parameters and the resource (the global history
collection) always exists.

The frontend does not parse this body directly; it passes the error to
`ErrorService.getMessage(error)`, which maps HTTP status/cause to a user-facing message (FR-013).

---

## Read-Only Guarantee

- The handler is backed by `ThemeService.listHistory()` annotated
  `@Transactional(readOnly = true)` and performs only repository finder calls.
- No `POST`/`PUT`/`PATCH`/`DELETE` mapping exists on this path, and no writes occur on a `GET`.
- Existing endpoints (`POST /api/themes/suggest`, `POST /api/themes/{themeId}/subtopics`,
  `POST /api/snacks/generate`, `/api/batches`, quiz) keep their current contracts (FR-007).

---

## Status Code Summary

| Scenario | Status | Body |
|----------|--------|------|
| Themes exist | `200` | Non-empty JSON array, newest-first |
| No themes exist | `200` | `[]` |
| Unexpected server failure | `500` | `GlobalExceptionHandler.ErrorResponse` |
