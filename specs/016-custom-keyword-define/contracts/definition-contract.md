# Contract: Custom Definition Generation — `POST /api/definitions/generate`

**Feature**: `016-custom-keyword-define`

This document is the authoritative contract for the **new provider-agnostic endpoint** that turns a
user-selected term into a **short definition** (≤20 words) for display in the existing definition
card. It adds a **new endpoint group** and **new request/response types**; **no existing snack,
meaning-snack, quiz, results or history DTO changes**, and **no persistence** is involved.

It builds on feature 013's keyword contract, feature 014's meaning-snack contract and feature 015's
≤20-word meaning policy without changing any of their shapes.

Error shapes are those produced by
`com.endia.infrastructure.exception.GlobalExceptionHandler`:
`{ "error": string, "message": string, "field": string|null, "cause": string|null }`.

---

## 1. Generate a definition

### 1.1 Request

`POST /api/definitions/generate`

```jsonc
{
  "themeId": "9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
  "term": "photosynthesis"
}
```

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| `themeId` | UUID | yes | Must resolve to an existing `Theme` (else 404 `NOT_FOUND`). Supplies the prompt context (`theme.getPrompt()`). |
| `term` | string | yes | Non-blank after trim (bean validation → 400). Normalized at the service boundary (trimmed, whitespace-collapsed) and bounded to at most **10** whitespace-separated tokens (else 400 `VALIDATION_ERROR`). Hyphenated compounds count as one token. |

`subtopicId` is **not** part of the request: a term's definition is theme-scoped, not
subtopic-scoped, and the selected term may be inside a meaning-snack card that has no quiz subtopic.

### 1.2 Response — 200 OK

```jsonc
{
  "term": "photosynthesis",
  "definition": "the process plants use to convert light energy into stored chemical energy"
}
```

| Field | Type | Notes |
|-------|------|-------|
| `term` | string | The normalized selected term (echoed back). |
| `definition` | string | The generated short definition, non-blank and bounded to at most **20** whitespace-separated tokens. Plain, untrusted text. |

Exactly two fields. The response carries **no** `id`, **no** persisted-entity fields, **no** quiz
fields and **no** `keywords`.

### 1.3 Validation / normalization (service boundary)

1. The term is trimmed and whitespace-collapsed (`trim().replaceAll("\\s+", " ")`).
2. A blank term → `InvalidSelectionException` (400 `VALIDATION_ERROR`); a term of more than **10**
   whitespace-separated tokens → `InvalidSelectionException` (400 `VALIDATION_ERROR`). **No LLM call
   is made** for a rejected term.
3. The generated definition is validated non-blank; on failure it is re-rolled **once**; a second
   failure → 503 `SERVICE_UNAVAILABLE`.
4. The accepted definition is truncated to the first **20** whitespace-separated tokens, so the
   response is always bounded (FR-004).

### 1.4 Status codes and error shapes

| Status | `error` | When | Example body |
|--------|---------|------|--------------|
| 200 | — | Definition generated. | see §1.2 |
| 400 | `VALIDATION_ERROR` | Missing/blank `term`, or a term exceeding 10 whitespace tokens. | `{"error":"VALIDATION_ERROR","message":"Select no more than 10 words to define.","field":null,"cause":null}` |
| 404 | `NOT_FOUND` | `themeId` does not exist. | `{"error":"NOT_FOUND","message":"Theme not found with ID: <id>","field":null,"cause":null}` |
| 503 | `SERVICE_UNAVAILABLE` | LLM unavailable, or the generated definition still blank after one re-roll. | `{"error":"SERVICE_UNAVAILABLE","message":"The AI service is temporarily unavailable. Please try again later.","field":null,"cause":"UNAVAILABLE"}` |
| 500 | `INTERNAL_ERROR` | Unexpected server error. | `{"error":"INTERNAL_ERROR","message":"An unexpected error occurred","field":null,"cause":null}` |

The frontend surfaces `cause`-derived, user-safe text via `ErrorService.getMessage(...)` and adds
**nothing** to the reading flow on failure (FR-009, SC-002).

---

## 2. No persistence

The endpoint is **stateless**: it does not read or write any table beyond resolving the `Theme`, and
it creates no entity. Repeated calls for the same term are independent and produce no stored row
(contrast with feature 014's idempotent meaning-snack generate). The definition exists only in the
response and in the card until dismissed.

---

## 3. Frontend consumption contract

### 3.1 Trigger

- On the snacks reading page only (`SnacksComponent`), a `SelectionDefineDirective` listens to
  `document:selectionchange` on the reading container.
- A non-collapsed selection whose range is contained in the reading container starts a ~2s timer; any
  selection change or clear cancels the timer (FR-001/FR-002).
- When the timer fires, the directive emits `{ term, rect }`; the page shows exactly one transient,
  selection-anchored `SelectionDefineAffordanceComponent` (FR-003).
- The trigger is reachable via pointer selection and via programmatic/keyboard selection
  (`document.getSelection()`), both of which fire `selectionchange` (FR-016).

### 3.2 Generation

- Activating `Define` normalizes and locally validates the term; blank or >10 tokens shows a
  meaningful error and issues **no** request (FR-011).
- Otherwise the page calls §1 with the current `themeId` and the normalized term, guards against a
  duplicate in-flight request (at most one — FR-007), and opens the existing definition card for the
  term with a **loading** state (FR-006).
- On success the card shows the generated definition (FR-008); on failure the card shows the
  `ErrorService`-derived message and renders no empty/placeholder definition (FR-009).

### 3.3 Display / security

- The selected term and the generated definition are rendered as **escaped interpolation only** —
  no `[innerHTML]`, no `DomSanitizer` (FR-013/FR-014/FR-015).
- The affordance renders only a static `Define` label.
- The single-definition-card rule is preserved: the custom-define card and a highlighted-keyword card
  are the same page-level component, so opening one replaces the other (FR-003/FR-018).

### 3.4 Scope

- The trigger is offered **only** on the snacks reading page. The results recap and read-only history
  surfaces get no directive and no affordance, and their rendering is unchanged (FR-017/FR-018).
- Task 12 ("Create snack" from the custom meaning) is **out of scope**; the custom-define card offers
  no "Generate snack" action.

### 3.5 Example

See [`definition.sample.json`](./definition.sample.json) for a success response and error bodies.
