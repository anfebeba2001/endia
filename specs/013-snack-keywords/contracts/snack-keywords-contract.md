# Contract: Snack Keywords — API Shape, Highlight Matching, and Definition Card

**Feature**: `013-snack-keywords`

This document is the authoritative contract for three things:

1. the `keywords` field on every snack-returning API surface (§1),
2. the keyword-matching / highlight rules the frontend renderer must implement (§2),
3. the definition-card behavior (§3).

It extends feature 012's contract (`specs/012-snack-rich-formatting/contracts/snack-text-format.md`)
without changing it: the rich-text grammar, the JSON transport, and the render-time allowlist remain as
specified there.

---

## 1. API contract — `keywords` on every snack-returning surface

### 1.1 Shape

`keywords` is an array of objects, appended after the existing snack fields:

```jsonc
{
  "keywords": [
    { "term": "mitosis", "definition": "cell division" }
  ]
}
```

- `term`: `string`, plain text, untrusted. Non-empty after normalization.
- `definition`: `string`, plain text, untrusted. Non-empty after normalization and **at most 3
  whitespace-separated tokens** (FR-007).
- The array is **always present** on every snack object and is `[]` when there are no valid keywords —
  it is never `null` and never omitted (FR-006, FR-012).
- Order is the model's order after normalization (invalid entries dropped, duplicates removed, order
  otherwise preserved).

### 1.2 Surfaces

| Surface | Endpoint / source | Snack object path | New field |
|---------|-------------------|-------------------|-----------|
| Fresh generation | `POST /api/snacks/generate` | `snacks[]` | `keywords` |
| Batch status (progressive delivery) | `GET /api/batches/{batchId}` | `items[].snack` | `keywords` |
| Results recap | `GET /api/quiz/summary/{subtopicId}` | `recap.snack` | `keywords` |
| History (feature 011) | `GET /api/themes` | `[].subtopics[].snacks[]` | `keywords` |

The field is added in place; **no new endpoint, query parameter, or response type** is introduced
(spec Non-Goals).

### 1.3 Normalization guarantees (server-side, before serving/persisting)

Guaranteed for every value on every surface (FR-007..FR-010):

| Rule | Guarantee |
|------|-----------|
| ≤ 3-word definitions | No served/stored `definition` has more than 3 whitespace-separated tokens. |
| Blank removal | No served/stored `term` or `definition` is blank. |
| Duplicate removal | No two keywords share the same term (case-insensitive). |
| Plain text | Keyword values are returned as plain text; markup is not interpreted, stripped, or escaped into entity characters. |
| Empty is valid | An empty list is a valid, successful result — never a generation failure or re-roll. |

### 1.4 Round-trip guarantee

- The backend stores and returns keyword text **unmodified** except for the §1.3 normalization. No
  HTML-escaping of stored values (e.g. `<` is returned as `<`, not `&lt;`).
- JSON control characters are transport-encoded and decode back to the identical string.
- The frontend is the **only** place that interprets/escapes keyword text, at render time, through the
  feature-012 allowlist (FR-026..FR-029).

### 1.5 Example

See [`snack-keywords.sample.json`](./snack-keywords.sample.json).

---

## 2. Highlight contract (frontend renderer)

The renderer receives the raw `concept`/`metaphor`/`whyItMatters` string plus that snack's `keywords`
and produces a typed inline AST. `related` is never passed and is never a highlight source (FR-014).

### 2.1 Matching rules

| # | Rule | FR |
|---|------|----|
| M1 | A term is matched **case-insensitively** against the body text. | FR-015 |
| M2 | Matching is **literal**: term characters are never interpreted as regular-expression syntax (`C++`, `(x)`, `a.b`, `[1]` are matched verbatim). | FR-015 |
| M3 | Matching is **whole-word**: a candidate match is valid only when the characters immediately before and after it are not word characters. A term that itself begins/ends with a non-word character is exempt at that edge. | FR-015 |
| M4 | **Every** occurrence of a matched term in the body text is highlighted. | FR-017 |
| M5 | Matching is **non-overlapping** and **longest-term-wins**: when two terms overlap on a span, the longer term is highlighted and the shorter produces no separate/ nested highlight. | FR-016 |
| M6 | Matching is confined to a **single contiguous inline text run**; a term never crosses a paragraph, list-item, or bold boundary. | Edge Cases |
| M7 | A term that does not appear in the body text produces **no** highlight and **no** artifact. | FR-014 |
| M8 | A term appearing inside `**bold**` is both emphasized (`<strong>`) and highlighted (keyword control nested inside). | FR-014, US2 AS4 |
| M9 | With no keywords or an empty list, rendering is byte-identical to feature 012 (no highlight, no control, no artifact). | FR-024 |

### 2.2 Rendered output

A matched term renders as an inline, keyboard-focusable control inside the text flow:

```html
<button type="button" class="rich-text__keyword" ...>{{ originalCasedText }}</button>
```

- The **displayed text is the original-cased substring** from the body (not the term's casing).
- The control carries `data-term`/`data-definition` (or equivalent bindings) for the click handler.
- The highlight color is a distinct design token, visually distinct from body text in both light and dark
  themes; the surrounding feature-012 formatting (bold, bullets, paragraphs) is preserved.
- The output vocabulary remains closed: `<p>`, `<ul>`, `<li>`, `<strong>`, `<br>`, `<button>`. No
  `[innerHTML]`, no `DomSanitizer`, no HTML string is ever constructed (FR-029).

### 2.3 Algorithm (normative behavior, not implementation)

1. Build the candidate term list: trim, drop blanks, de-duplicate case-insensitively, sort by length
   descending (longest wins).
2. Walk each contiguous text run (including the text inside a bold run) with a left-to-right cursor.
3. At each position, try the longest candidate first; if it matches literally and satisfies M3, emit a
   `keyword` node and advance the cursor past the match (non-overlapping).
4. Otherwise emit the character as `text` and advance by one.
5. Recursively apply to bold children so M8 holds.

---

## 3. Definition card contract (frontend)

### 3.1 Content

- The card shows **only** the selected keyword's `definition`.
- It never displays more than 3 words (guaranteed by §1.3 and re-asserted by the card).
- The term may be shown as a heading/label; the definition text is escaped via Angular interpolation.

### 3.2 Open

- Triggered by click **or** tap on a highlighted term control (FR-018), and by keyboard activation
  (Enter/Space) of the focused control (FR-023).
- Opening the card for a term **closes any previously open card** (FR-021).
- On open, focus moves into the card (e.g. to the close control) so keyboard users can dismiss it.

### 3.3 Dismiss

All three of the following close the card, with **no navigation and no loss of reading position**
(FR-020, SC-004):

1. click/tap **outside** the card;
2. activating the visible **close control**;
3. pressing **Escape**.

On close, focus **returns to the term control that opened it** (FR-023).

### 3.4 Responsiveness

- The card is fully visible and usable at both desktop and mobile widths (FR-022, SC-005).
- Positioning is anchored to the term control and clamped to the viewport so the card never overflows
  off-screen; on narrow viewports it may present as a bottom sheet.
- No horizontal page overflow is introduced.

### 3.5 Security

- Keyword `term` and `definition` are untrusted and are rendered as escaped text through the same
  allowlist mechanism as body text (FR-026..FR-028). Markup in either value is inert visible text and is
  never executed or injected. Readable text is preserved (not silently dropped).

### 3.6 Explicit non-goals (FR-031)

- No "Generate snack" action on the card.
- No "meaning snack" backbone entry.
- No keyword authoring/editing.
