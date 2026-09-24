# Contract: Keyword-Definition Length Policy (3 → 20 words)

**Feature**: `015-keyword-meaning-20-words`

This document records the **API contracts touched** by this feature and, crucially, what does **not**
change. It builds on feature 013's keyword contract
(`specs/013-snack-keywords/contracts/snack-keywords-contract.md`) and feature 014's meaning-snack
contract (`specs/014-meaning-snacks/contracts/meaning-snack-contract.md`) without changing any of
their shapes.

**Headline: no response/request schema changes.** Only the **allowed content length** of a keyword
`definition` (and its copy `sourceDefinition` on a meaning snack) changes from **at most 3
whitespace-separated tokens** to **at most 20**. Every field name, type, nullability, status code and
error shape is unchanged.

---

## 1. The length rule (single source of truth)

| Aspect | Value |
|--------|-------|
| Field | `definition` on a snack keyword; `sourceDefinition` on a meaning snack. |
| Type | `string`, plain text, untrusted. |
| Bound (both) | At most **20** whitespace-separated tokens (**inclusive**); previously 3. |
| Over-limit (both) | Truncated to the first 20 tokens at the service boundary (never rejected, never re-roll). |
| **Char bound (source only)** | The meaning-snack `sourceDefinition` is additionally clamped to **≤200 characters on a word boundary** (never splits a word; FR-019), so it always fits the `VARCHAR(200)` column. |
| **Effective source bound** | `min(20 tokens, ≤200 chars ending on a word boundary)`. |
| Blank | A blank/whitespace-only definition is still dropped (snack keywords) or rejected (meaning-snack request). |
| Tokenization | `split("\\s+")`; hyphenated compounds and punctuation-bearing words count as one token. |
| Applies to | Keyword `definition` and meaning-snack `sourceDefinition` **only**. |

This is enforced by `SnackService.normalizeKeywords` and `MeaningSnackService.normalizeDefinition`
(plan.md M1). The prompt (`generate-snack.md`) instructs the model accordingly; the service is the
authoritative boundary. `SnackKeyword.definition` (JSON column) is **not** character-clamped.

---

## 2. Snack-generation response — keyword definitions (shape unchanged)

Endpoint: `POST /api/snacks/generate` → `SnackGenerateApiResponse`.

The embedded `SnackItem` carries a `keywords` array of `SnackKeywordDto`
(`{ "term": string, "definition": string }`). The same `SnackKeywordDto` is embedded in
`BatchStatusApiResponse.SnackDto` (batch/backbone source), `QuizSummaryApiResponse` (results recap)
and `ThemeHistoryApiResponse` (history).

```jsonc
{
  "snacks": [
    {
      "id": "snack-1",
      "subtopicId": "sub-001",
      "subtopicName": "Light reactions",
      "concept": "**Photosynthesis** converts light into chemical energy.",
      "metaphor": "Think of a leaf as a solar kitchen.",
      "whyItMatters": "Nearly every food chain starts here.",
      "related": ["Chlorophyll", "Glucose"],
      "keywords": [
        {
          "term": "photosynthesis",
          "definition": "the process plants use to convert light energy into stored chemical energy"
        },
        { "term": "chlorophyll", "definition": "the green pigment that captures light" }
      ]
    }
  ]
}
```

**Contract deltas:**

| Before | After |
|--------|-------|
| `definition` ≤ 3 whitespace tokens | `definition` ≤ 20 whitespace tokens (inclusive) |
| Over-limit truncated to 3 | Over-limit truncated to 20 |

Everything else — field names, types, order, nullability, the `related` 2–3 item rule, the 2–5
keyword-count guideline, the term-verbatim matching rule — is unchanged (FR-007, FR-016).

---

## 3. Meaning-snack create request/response (shape unchanged)

Endpoint: `POST /api/meaning-snacks/generate` →
`MeaningSnackGenerateApiResponse { alreadyExisted: boolean, meaningSnack: MeaningSnackItem }`.

### 3.1 Request

```jsonc
{
  "themeId": "9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
  "subtopicId": "6f1c2a9e-1b2c-4d3e-8f90-0a1b2c3d4e5f",
  "term": "photosynthesis",
  "definition": "the process plants use to convert light energy into stored chemical energy"
}
```

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| `themeId` | UUID | yes | Must resolve to an existing `Theme` (else 404 `NOT_FOUND`). |
| `subtopicId` | UUID | yes | Must resolve to an existing `Subtopic`; source/placement anchor (else 404). |
| `term` | string | yes | Non-blank after trim (else 400 `VALIDATION_ERROR`). |
| `definition` | string | yes | Non-blank after trim; **truncated to the first 20 whitespace-separated tokens** (was 3), then clamped to **≤200 characters on a word boundary** (FR-019). The stored/echoed `sourceDefinition` is therefore guaranteed ≤200 chars. |

### 3.2 Response — 200 OK

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
    "sourceDefinition": "the process plants use to convert light energy into stored chemical energy",
    "concept": "**Photosynthesis** converts light energy into stored chemical energy...",
    "metaphor": "Think of a leaf as a **solar kitchen**...",
    "whyItMatters": "Nearly every food chain starts here...",
    "related": ["Chlorophyll", "Glucose"],
    "createdAt": "2026-09-23T12:00:00"
  }
}
```

**Contract deltas:**

| Before | After |
|--------|-------|
| `sourceDefinition` echoed the first ≤ 3 tokens of the request `definition` | `sourceDefinition` echoes the first ≤ 20 tokens (inclusive), then ≤200 chars on a word boundary (FR-019) |
| The response carried no quiz fields and no `keywords` | **Unchanged** — still no `subtopicId` quiz target, `quizStatus`, `quizQuestions` or `keywords` |

Idempotency, status codes and error shapes are unchanged (see the 014 contract): a repeat
`(sourceSubtopicId, sourceTerm)` returns the existing item with `alreadyExisted: true` and does not
call the LLM; 400 `VALIDATION_ERROR`, 404 `NOT_FOUND`, 503 `SERVICE_UNAVAILABLE` are unchanged.

### 3.3 Theme-scoped listing

`GET /api/meaning-snacks?themeId=<uuid>` → `{ themeId, meaningSnacks: MeaningSnackItem[] }`. **Shape
unchanged**; each item's `sourceDefinition` may now be up to 20 tokens and is guaranteed ≤200
characters.

---

## 4. Explicitly unchanged contracts

| Contract | Status |
|----------|--------|
| Snack content fields (`concept`, `metaphor`, `whyItMatters`) | Unchanged (no length change). |
| `related` 2–3 **item** cap | Unchanged (a different rule; not to be conflated with the 20-word cap). |
| Keyword count guideline (2–5) | Unchanged. |
| Term-verbatim matching / highlight | Unchanged (`keyword-highlighter.ts` unaffected by definition length). |
| Quiz generation/content/scoring, batch/progressive delivery, XP/accuracy | Unchanged. |
| Error envelope `{ error, message, field, cause }` | Unchanged. |
| Rendering | Escaped interpolation only; no `[innerHTML]`, no `DomSanitizer` (FR-011..FR-014). |
| Persistence schema | Unchanged; no new endpoint, entity or migration (FR-017). The `VARCHAR(200)` source-definition column is honored by the word-boundary clamp (FR-019), not by DDL. |

---

## 5. Verification hooks

- Backend unit tests assert 19/20/21-token boundaries for both `SnackService.normalizeKeywords` and
  `MeaningSnackService.normalizeDefinition`, and that `GenerateSnackPromptTest` sees "20 words"/"twenty
  words" (FR-015). `MeaningSnackServiceTest` additionally asserts the ≤200-char word-boundary clamp
  (FR-019): an over-200-char source definition is cut at a space and never mid-word.
- Frontend E2E asserts a 20-word definition renders in `.keyword-card__definition` with no horizontal
  overflow (FR-015 / SC-004).
- The existing MockMvc contract and Testcontainers integration tests pass unchanged (no DTO shape
  change).
