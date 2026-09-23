# Data Model: Snack Rich Formatting (Bold Emphasis, Bullets, Short Paragraphs)

**Feature**: `012-snack-rich-formatting`

**Persistence**: **NO SCHEMA CHANGE.** This feature does not add, remove, or modify any MySQL table,
column, index, constraint, JPA entity, enum, converter, or migration (`ddl-auto=update` produces no
delta). The markup is stored as ordinary characters inside the existing `TEXT` columns. This satisfies
FR-009, FR-022, SC-007, and the spec Non-Goals.

The only production data-layer change in the whole feature is the wording of the externalized prompt
resource `backend/src/main/resources/prompts/generate-snack.md`; it changes *what characters the model
writes into existing columns*, not the columns themselves.

---

## 1. Existing Entities (UNCHANGED)

### `Snack` — `com.endia.domain.model.Snack`

| Field | Type | Column | Nullable | Notes |
|-------|------|--------|----------|-------|
| `id` | `UUID` | `id` | no | `@GeneratedValue(UUID)`. |
| `subtopic` | `Subtopic` | `subtopic_id` | no | `@ManyToOne(LAZY)`; indexed by `idx_snack_subtopic`. |
| `concept` | `String` | `concept` (`TEXT`) | no | **Now may contain `**bold**`, `- ` bullet lines, and blank-line paragraph breaks.** Stored verbatim. |
| `metaphor` | `String` | `metaphor` (`TEXT`) | no | Same markup semantics. |
| `whyItMatters` | `String` | `why_it_matters` (`TEXT`) | no | Same markup semantics. |
| `related` | `List<String>` | `related` (`JSON`) | no | **Plain labels only.** `JpaJsonConverter` (Jackson). Markup-like characters are never interpreted as rich text (FR-013). |

**Why no change is needed**: MySQL `TEXT` stores arbitrary UTF-8, including `*`, `-`, `•`, and newline
characters, with no transformation. The entity maps the column to `String` with no `@Lob`, no trimming
hook, and no charset override.

### Related entities (read-only for this feature, UNCHANGED)

- `Subtopic`, `Theme` — navigation/parentage; untouched.
- `QuizQuestion`, `AnswerAttempt`, `StudySession*` — untouched; quiz content and scoring are explicitly
  out of scope (Non-Goals, FR-020).

---

## 2. The stored text format (a convention on existing columns, not a schema)

Snack text fields (`concept`, `metaphor`, `whyItMatters`) hold plain text that **may** use a small,
whitelisted inline convention:

| Construct | Stored representation | Rendered as |
|-----------|-----------------------|-------------|
| Bold emphasis | `**important phrase**` | `<strong>important phrase</strong>` |
| Bullet list | consecutive lines each starting with `- ` (renderer also accepts `* ` and `• `) | `<ul><li>…</li></ul>` |
| Paragraph break | one or more blank lines between text blocks | separate `<p>` elements |
| Soft line break | a single `\n` inside a paragraph | `<br>` (line break preserved, not collapsed) |
| Plain text | any other characters | text nodes (escaped by Angular) |
| Anything else (HTML, links, images, scripts, event handlers, inline styles, headings, tables, code) | stored verbatim, **not interpreted** | inert visible text (neutralized at render time) |

`related` entries remain plain strings and are rendered as labels/chips exactly as before.

---

## 3. Round-trip guarantees (no new model, no new DTO)

The existing DTOs are unchanged in shape and continue to expose the raw strings:

| Surface | DTO / record | Rich-text fields |
|---------|--------------|------------------|
| Fresh generation | `SnackGenerateResponse.SnackItem` | `concept`, `metaphor`, `whyItMatters` (raw `String`) |
| Batch status | `BatchStatusApiResponse` items → snack fields | `concept`, `metaphor`, `whyItMatters` |
| Results recap | `QuizSummaryApiResponse` recap snack | `concept`, `metaphor`, `whyItMatters` |
| History (feature 011) | `ThemeHistoryApiResponse.SnackItem` | `concept`, `metaphor`, `whyItMatters` |

All of these pass the stored string through with no transformation. JSON encoding of control characters
(`\n`, `\"`) on the wire is standard transport encoding and decodes back to the identical string on the
client, so FR-007/FR-008/SC-006 hold.

---

## 4. Frontend view models (UNCHANGED shapes)

The TypeScript interfaces already carry the fields as `string` and need no change:

- `frontend/src/app/models/snack.model.ts` — `Snack`
- `frontend/src/app/models/batch.model.ts` — `BatchItem.snack`
- `frontend/src/app/models/quiz.model.ts` — `Recap.snack`
- `frontend/src/app/models/history.model.ts` — `HistorySnack`

The **new** types introduced by this feature are purely client-side, in-memory rendering types (no
persistence, no API surface):

```ts
// frontend/src/app/components/rich-text/rich-text.model.ts
export type RichInline =
  | { kind: 'text'; text: string }
  | { kind: 'bold'; text: string }
  | { kind: 'break' };

export type RichBlock =
  | { kind: 'paragraph'; nodes: RichInline[] }
  | { kind: 'list'; items: RichInline[][] };
```

These are produced by `parseRichText(text)` and consumed only by `RichTextComponent`.

---

## 5. Migration / rollback

- **Migration**: none. Deploying the prompt change and the frontend renderer requires no database step.
- **Backward compatibility**: snacks generated before this feature contain plain or lightly marked text
  and render correctly through the same parser (plain text → single paragraph; no artifacts). This
  satisfies US5 and the "old snacks" edge case, including in the history view.
- **Rollback**: revert the prompt file and the frontend renderer; no data to unwind because no schema or
  stored value is transformed by this feature.
