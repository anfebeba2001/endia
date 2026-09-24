# Contract: Custom "Create snack" — Reuse of the Meaning-Snack API (UNCHANGED)

**Feature**: `017-custom-keyword-create-snack`

This feature introduces **no new endpoint, no request/response type, no field, and no status code**.
It reuses the feature-014 meaning-snack contract **exactly as specified** in
[`../../014-meaning-snacks/contracts/meaning-snack-contract.md`](../../014-meaning-snacks/contracts/meaning-snack-contract.md).

This document records **which parts of that contract this feature exercises** and how the custom term +
generated meaning map onto them, so the implementer/tasker has an authoritative reference without
re-deriving it. Any change here would be a contract change and is explicitly out of scope (spec FR-016,
SC-007, Non-Goals).

Error shapes are those produced by `com.endia.infrastructure.exception.GlobalExceptionHandler`:
`{ "error": string, "message": string, "field": string|null, "cause": string|null }`.

---

## 1. Generate — `POST /api/meaning-snacks/generate` (reused, unchanged)

### 1.1 Request (custom-keyword caller)

```jsonc
{
  "themeId": "9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
  "subtopicId": "6f1c2a9e-1b2c-4d3e-8f90-0a1b2c3d4e5f",
  "term": "chlorophyll",
  "definition": "the green pigment that absorbs light energy"
}
```

| Field | Custom-keyword value | Rules (unchanged) |
|-------|----------------------|-------------------|
| `themeId` | the current theme | `@NotNull`; must resolve to an existing `Theme` (else 404 `NOT_FOUND`) |
| `subtopicId` | the **captured source subtopic**: regular entry's `subtopicId` **or** meaning entry's `sourceSubtopicId` (Option A) | `@NotNull`; must resolve to an existing `Subtopic` (else 404 `NOT_FOUND`) |
| `term` | the custom keyword term (a feature-016 selection, ≤10 tokens) | `@NotBlank`; trimmed by the service; stored in `VARCHAR(500)` |
| `definition` | the feature-016 generated meaning (≤20 words, non-blank) | `@NotBlank`; normalized to ≤20 tokens / ≤200 chars by the service |

**Difference from the feature-014 keyword caller**: only the *origin* of `term`/`definition`
(user-selected + generated vs. an LLM-provided keyword) and of `subtopicId` (the active entry's source
subtopic) — the wire shape, validation, persistence, and idempotency are identical.

### 1.2 Response — 200 OK (unchanged)

```jsonc
{
  "alreadyExisted": false,
  "meaningSnack": {
    "id": "c0ffee00-1111-2222-3333-444455556666",
    "kind": "MEANING",
    "themeId": "9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
    "sourceSubtopicId": "6f1c2a9e-1b2c-4d3e-8f90-0a1b2c3d4e5f",
    "sourceSubtopicName": "Light reactions",
    "sourceTerm": "chlorophyll",
    "sourceDefinition": "the green pigment that absorbs light energy",
    "concept": "**Chlorophyll** is the pigment ...",
    "metaphor": "Think of it as a **solar panel** ...",
    "whyItMatters": "It captures the light energy ...",
    "related": ["Photosynthesis", "Thylakoid"],
    "createdAt": "2026-09-24T12:00:00"
  }
}
```

- `kind` is always `"MEANING"` — the discriminator that keeps the created snack out of every
  quiz-bearing path (FR-011/FR-013).
- The response carries **no** `subtopicId` quiz target, **no** `quizStatus`, **no** `quizQuestions`,
  **no** `keywords`.

### 1.3 Idempotency (unchanged) — the dedup guard

If a meaning snack already exists for `(sourceSubtopicId, sourceTerm)` (case-insensitive), the endpoint
returns the existing item with `alreadyExisted: true`, **does not call the LLM**, and creates **no** new
row (unique index `ux_meaning_snack_source` + a race-safe re-query). This is what makes rapid/repeated
"Create snack" activation and card re-opening safe (FR-007/FR-010, SC-002).

### 1.4 Status codes and error shapes (unchanged)

| Status | `error` | When | Example body |
|--------|---------|------|--------------|
| 200 | — | Generated, or existing returned (`alreadyExisted: true`). | see §1.2 |
| 400 | `VALIDATION_ERROR` | Missing/blank `term` or `definition`, or missing `themeId`/`subtopicId`. | `{"error":"VALIDATION_ERROR","message":"definition must not be blank","field":"definition","cause":null}` |
| 404 | `NOT_FOUND` | `themeId` or `subtopicId` does not exist. | `{"error":"NOT_FOUND","message":"Subtopic not found with ID: <id>","field":null,"cause":null}` |
| 503 | `SERVICE_UNAVAILABLE` | LLM unavailable, or generated content still invalid after one re-roll. | `{"error":"SERVICE_UNAVAILABLE","message":"The AI service is temporarily unavailable. Please try again later.","field":null,"cause":"UNAVAILABLE"}` |

The frontend surfaces `cause`-derived, user-safe text via `ErrorService.getMessage(...)` and adds
**nothing** to the backbone on failure (FR-008, SC-003).

---

## 2. List — `GET /api/meaning-snacks?themeId=` (reused, unchanged)

### 2.1 Request

`GET /api/meaning-snacks?themeId=9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d`

### 2.2 Response — 200 OK (unchanged)

```jsonc
{
  "themeId": "9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
  "meaningSnacks": [
    { /* MeaningSnackItem — same shape as §1.2.meaningSnack, including custom-created rows */ }
  ]
}
```

- `meaningSnacks` is always present; `[]` when none — never `null`, never omitted.
- Ordering is `createdAt` ascending (stable across reloads).
- **This is the persistence/reload path** (FR-014/FR-015, SC-005): the snacks page loads it on every
  entry, so a custom-created snack reappears after a reload/new session for the same theme.
- **Graceful degradation**: any non-200/network error is treated as "no meaning snacks"; regular snacks
  still render (FR-015).

---

## 3. Frontend consumption contract (this feature)

### 3.1 Action availability

- The action is shown only after feature 016's "Define" completes successfully with a non-empty
  definition and a captured source subtopic exists (FR-001/FR-002, SC-001).
- It reuses/relabels the feature-014 action slot: exactly one snack-creation action per card
  (FR-003). Label: `Create snack` (idle), `Creating…` (loading), `Added to your snacks` (done).

### 3.2 Generate call

- The page calls §1 with the current `themeId`, the **captured** `activeKeywordSubtopicId`, the custom
  `term`, and the generated `definition`.
- At most one in-flight request per `(subtopicId, term.toLowerCase())` (FR-007).
- On success the returned `meaningSnack` is appended to the page's list (deduped by `id`), marked
  generated, and the card reflects `done` (FR-009/FR-010).
- On failure the card shows the `ErrorService`-derived message and nothing is added (FR-008).

### 3.3 Display / security

- The custom term and generated meaning are rendered as **escaped interpolation only** in the card; the
  created content is rendered through `RichTextComponent` in the backbone. No `[innerHTML]`, no
  `DomSanitizer` (FR-017–FR-019).

### 3.4 Scope

- Offered only on the snacks reading page (continuation of the feature-016 Define flow). Results and
  history are unchanged.

### 3.5 Example

See [`meaning-snack.sample.json`](./meaning-snack.sample.json) for a custom-create request/response and
an idempotent `alreadyExisted` response.
