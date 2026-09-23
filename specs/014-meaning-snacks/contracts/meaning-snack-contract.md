# Contract: Meaning Snack — Generation, Theme-Scoped Listing, and Backbone Consumption

**Feature**: `014-meaning-snacks`

This document is the authoritative contract for three things:

1. the **generate** endpoint that turns a keyword `term`+`definition` into a persisted meaning snack
   (§1),
2. the **theme-scoped listing** endpoint that reloads persisted meaning snacks for the backbone (§2),
3. how the **snacks page** consumes both — the `kind` marker, ordering, distinct rendering, and the
   **absence of quiz fields / quiz affordances** (§3).

It builds on feature 013's contract (`specs/013-snack-keywords/contracts/snack-keywords-contract.md`)
without changing it: the keyword `term`/`definition` shape, the rich-text grammar, the JSON transport
and the render-time allowlist remain as specified there. It adds a **new** endpoint group and a
**new** response type; no existing snack-returning DTO changes.

Error shapes are those produced by
`com.endia.infrastructure.exception.GlobalExceptionHandler`:
`{ "error": string, "message": string, "field": string|null, "cause": string|null }`.

---

## 1. Generate a meaning snack

### 1.1 Request

`POST /api/meaning-snacks/generate`

```jsonc
{
  "themeId": "9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
  "subtopicId": "6f1c2a9e-1b2c-4d3e-8f90-0a1b2c3d4e5f",
  "term": "photosynthesis",
  "definition": "light to sugar"
}
```

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| `themeId` | UUID | yes | Must resolve to an existing `Theme`. |
| `subtopicId` | UUID | yes | Must resolve to an existing `Subtopic`; used as the **source/placement** anchor. |
| `term` | string | yes | Non-blank after trim; plain, untrusted text. |
| `definition` | string | yes | Non-blank after trim; truncated to the first **3 whitespace-separated tokens** before use. |

### 1.2 Response — 200 OK

```jsonc
{
  "alreadyExisted": false,
  "meaningSnack": {
    "id": "c0ffee00-1111-2222-3333-444455556666",
    "kind": "MEANING",
    "themeId": "9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
    "sourceSubtopicId": "6f1c2a9e-1b2c-4d3e-8f90-0a1b2c3d4e5f",
    "sourceSubtopicName": "Light reactions",
    "sourceTerm": "photosynthesis",
    "sourceDefinition": "light to sugar",
    "concept": "**Photosynthesis** converts light energy into stored chemical energy...",
    "metaphor": "Think of a leaf as a **solar kitchen**...",
    "whyItMatters": "Nearly every food chain starts here...",
    "related": ["Chlorophyll", "Glucose"],
    "createdAt": "2026-09-23T12:00:00"
  }
}
```

- `kind` is always `"MEANING"` — the discriminator that prevents any surface from treating this as a
  regular quiz-bearing snack.
- `sourceTerm`/`sourceDefinition` are the retained origin of the snack (FR-008).
- The response carries **no** `subtopicId` quiz target, **no** `quizStatus`, **no** `quizQuestions`,
  and **no** `keywords`.

### 1.3 Idempotency (FR-006)

If a meaning snack already exists for `(sourceSubtopicId, sourceTerm)` (case-insensitive), the
endpoint returns the existing item with `alreadyExisted: true`, **does not call the LLM**, and creates
**no** new row. This makes repeated/rapid activation and card re-opening safe (FR-005/FR-006).

### 1.4 Status codes and error shapes

| Status | `error` | When | Example body |
|--------|---------|------|--------------|
| 200 | — | Generated, or existing returned (`alreadyExisted: true`). | see §1.2 |
| 400 | `VALIDATION_ERROR` | Missing/blank `term` or `definition`, or missing `themeId`/`subtopicId`. | `{"error":"VALIDATION_ERROR","message":"term must not be blank","field":"term","cause":null}` |
| 404 | `NOT_FOUND` | `themeId` or `subtopicId` does not exist. | `{"error":"NOT_FOUND","message":"Theme not found with ID: <id>","field":null,"cause":null}` |
| 503 | `SERVICE_UNAVAILABLE` | LLM unavailable, or generated content still invalid after one re-roll. | `{"error":"SERVICE_UNAVAILABLE","message":"The AI service is temporarily unavailable. Please try again later.","field":null,"cause":"UNAVAILABLE"}` |

The frontend surfaces `cause`-derived, user-safe text via `ErrorService.getMessage(...)` and adds
**nothing** to the backbone on failure (FR-004, SC-003).

---

## 2. List persisted meaning snacks for a theme

### 2.1 Request

`GET /api/meaning-snacks?themeId=9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d`

### 2.2 Response — 200 OK

```jsonc
{
  "themeId": "9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
  "meaningSnacks": [
    { /* MeaningSnackItem — same shape as §1.2.meaningSnack */ }
  ]
}
```

- `meaningSnacks` is always present; it is `[]` when the theme has none — never `null`, never omitted.
- Ordering is `createdAt` ascending (stable across reloads).
- This is the **retrieval path** (Clarification #2): the snacks page reads it on load so persisted
  meaning snacks reappear after a reload / new session for the same theme (FR-018/FR-020, SC-006).

### 2.3 Status codes and error shapes

| Status | `error` | When | Example body |
|--------|---------|------|--------------|
| 200 | — | Theme exists (possibly with an empty list). | see §2.2 |
| 400 | `VALIDATION_ERROR` | `themeId` missing or not a UUID. | `{"error":"VALIDATION_ERROR","message":"Invalid value for parameter: themeId","field":"themeId","cause":null}` |
| 404 | `NOT_FOUND` | `themeId` does not exist. | `{"error":"NOT_FOUND","message":"Theme not found with ID: <id>","field":null,"cause":null}` |
| 500 | `INTERNAL_ERROR` | Unexpected server error. | `{"error":"INTERNAL_ERROR","message":"An unexpected error occurred","field":null,"cause":null}` |

**Graceful degradation**: the frontend treats any non-200 (including 404/500) or a network error as
"no meaning snacks" and still renders regular snacks normally (FR-020, US3 AS5).

---

## 3. Snacks-page consumption contract (the backbone)

The `/snacks` page (`SnacksComponent`) is the confirmed **main topics backbone** (Clarification #1).
It keeps its regular `BatchItem[]` flow and adds meaning entries from §2/§1.

### 3.1 Load

- On every page load (return path **and** fresh batch), the page calls `GET /api/meaning-snacks`
  for the current `themeId`; on error/absence it uses an empty list.
- Regular snacks continue to arrive via `POST /api/batches` + `GET /api/batches/{id}` exactly as
  before. The two sources never share a payload type.

### 3.2 Ordering / placement

The page builds a `BackboneEntry[]` by a pure interleave:

1. for each regular entry (in batch order), emit the regular entry, then any meaning entries whose
   `sourceSubtopicId` equals that entry's `subtopicId` (in `createdAt` order);
2. append any remaining meaning entries (source subtopic not present in the batch) at the end.

`entryId` is `subtopicId` for a regular entry and `meaning:<id>` for a meaning entry, so a subtopic
that has both yields two stable, collision-free keys. Ordering, index clamping, Previous/Next and
progressive-delivery/retry behaviour of regular entries are unchanged (FR-011).

### 3.3 Distinct rendering

- A meaning entry renders with a distinct card variant (`snack-card--meaning`) and an always-present
  text badge (e.g. `Meaning snack`), in a colour token distinct from regular cards, in both light and
  dark themes (FR-012, SC-004). The distinction is not colour-only (WCAG 1.4.1).
- The meaning card shows its retained origin (`sourceTerm` / `sourceDefinition`) and its generated
  `concept` / `metaphor` / `whyItMatters` / `related`, all through `RichTextComponent` (typed AST, no
  `[innerHTML]`, no `DomSanitizer`) — FR-009/FR-021..FR-023.
- Selecting a meaning entry always shows content, never a blank card (FR-013).
- Meaning content carries **no keywords**, so it exposes no highlight controls / nested definition
  card.

### 3.4 No quiz (hard invariant)

For a meaning entry the page:

- renders **no** "Start Quiz" button (`.quiz-btn` absent) and no quiz entry of any kind (FR-015);
- never routes to `/quiz`, never captures a prefetched quiz, and never stores the entry in
  `AppStateService.snacks()` (which drives the regular quiz flow);
- the API item exposes **no** quiz fields (§1.2/§2.2), and the backend never calls `QuizService`,
  `BatchService` or the phase registry for a meaning snack (FR-014/FR-016, SC-005).

### 3.5 Generate action from the definition card

- The keyword definition card shows a `Generate snack` action (FR-001). Activating it emits the
  selected keyword; the hosting page calls §1 with the active entry's `sourceSubtopicId` and the
  current `themeId`.
- While in flight the card shows a loading state and the action is guarded (one in-flight request per
  keyword; FR-003/FR-005).
- On success the returned item is appended to the page's `meaningSnacks` and marked generated; the
  card reflects the `done` state (FR-006).
- On failure the card shows the `ErrorService`-derived message and adds nothing (FR-004).
- Feature 013's open/dismiss/focus behaviour is unchanged (FR-025).

### 3.6 Explicit exclusions

- Meaning snacks are **not** part of `GET /api/themes` history (no history data-shape change), the
  results recap, XP/accuracy, or the batch status payload (FR-016, Non-Goals).
- A meaning snack is never a quiz target: `sourceSubtopicId` is a placement anchor only.

### 3.7 Example

See [`meaning-snack.sample.json`](./meaning-snack.sample.json) for a generate response, a listing
response, and a mixed backbone payload (regular + meaning).
