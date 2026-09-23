# Contract: Snack Rich-Text Format & Snack-Returning API Surfaces

**Feature**: `012-snack-rich-formatting`

**Status**: The JSON shape is **unchanged**. This document records the one behavioral addition to the
payload contents: the three text fields `concept`, `metaphor`, and `whyItMatters` may now contain a
small, whitelisted formatting convention (`**bold**`, `- ` bullet lines, blank-line paragraph breaks).
`related` remains an array of plain concept-name strings.

This is a **content convention on existing string fields**, not a contract-shape change: no new fields,
no new endpoints, no changed types, no schema change (FR-009, SC-007).

---

## 1. Snack-returning surfaces

All surfaces carry the same snack text fields and are subject to the same rendering/sanitization rules
(FR-008, FR-013, FR-023/FR-024). None of them changes shape.

| Surface | Endpoint / source | Snack fields |
|---------|-------------------|--------------|
| Fresh generation | `POST /api/snacks/generate` | `concept`, `metaphor`, `whyItMatters`, `related` |
| Batch status (progressive delivery) | `GET /api/batches/{batchId}` → `items[].snack` | `concept`, `metaphor`, `whyItMatters`, `related` |
| Results recap | `GET /api/quiz/{subtopicId}/summary` → `recap.snack` | `concept`, `metaphor`, `whyItMatters`, `related` |
| History (feature 011) | `GET /api/themes` → `[].subtopics[].snacks[]` | `concept`, `metaphor`, `whyItMatters`, `related` |

---

## 2. Text-field value grammar (whitelist)

A `concept` / `metaphor` / `whyItMatters` value is a UTF-8 string. The frontend renderer recognizes
**only** the following constructs; everything else is displayed as inert text.

```text
text-field  := block*
block       := paragraph | bullet-list
paragraph   := line ( "\n" line )*          ; rendered as <p>; internal \n → <br>
bullet-list := bullet-item+                  ; consecutive bullet lines → one <ul>
bullet-item := /^[ \t]*[-*•][ \t]+/ inline   ; marker consumed, not displayed
inline      := ( text | bold | line-break )*
bold        := "**" non-space-initial inline non-space-final "**"
line-break  := "\n"
```

Rules:

- **Bold marker is `**...**`.** Recognized only when the opening `**` is immediately followed by a
  non-space and the closing `**` is immediately preceded by a non-space. An unclosed `**` is literal
  text. Stray single `*` (e.g. `5 * 3 = 15`) is never emphasis.
- **Bullets** accept `-`, `*`, or `•` followed by whitespace. The prompt emits `- `; the renderer is
  tolerant of the others (Clarification 2).
- **Paragraphs** are separated by blank lines. A single newline inside a paragraph is preserved as a
  line break.
- **Empty / whitespace-only** values produce no visible block (no empty paragraph/list).
- **Everything else** (HTML tags, `<script>`, `onerror=`, `javascript:` URLs, `<iframe>`, `<img>`,
  `<style>`, links, headings, tables, code blocks, inline styles) is **not** part of the grammar and is
  neutralized at render time — rendered as inert, escaped text (readable text preserved, never
  silently dropped), never executed or injected (FR-015..FR-019).

### Example field values

```text
concept: "**Photosynthesis** is how plants convert light into chemical energy.\n\nIt happens in two stages:\n- **Light reactions** capture energy.\n- **Calvin cycle** fixes carbon."
```

Renders as: a paragraph with bold "Photosynthesis", a soft break, then a bullet list with two items.

---

## 3. Round-trip guarantee

- Backend stores and returns text **unmodified** (no stripping, collapsing, or HTML-escaping).
- The API response is a normal JSON document; control characters are JSON-encoded on the wire
  (`\n`, `\"`) and decode back to the identical string. The decoded value is byte-for-byte the value
  the model produced (FR-007, FR-008, SC-006).
- The frontend is the **only** place that interprets the grammar, at render time, through an allowlist
  (FR-017/FR-018).

---

## 4. Non-goals / explicitly out of contract

- No new fields, endpoints, query parameters, or response types.
- No schema or migration change.
- No server-side sanitization or transformation of stored text.
- No rich-text interpretation of `related`.
- No full Markdown/HTML dialect (see spec Non-Goals).

---

## 5. Example response (unchanged shape, markup-bearing values)

See [`snack-response.sample.json`](./snack-response.sample.json).
