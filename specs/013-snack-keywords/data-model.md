# Data Model: Colored Keywords with Click-to-Show Short Definition Cards

**Feature**: `013-snack-keywords`

**Persistence**: **ONE additive, nullable schema change** — a `keywords JSON NULL` column on the
`snack` table. No table is created, dropped, renamed, or rewritten; no existing column, index, or
constraint changes; no existing data is lost or invalidated (FR-012, SC-006). The project has **no
Flyway/Liquibase**; the column is applied by Hibernate `ddl-auto=update` (see §5).

---

## 1. New domain value object — `SnackKeyword`

`com.endia.domain.model.SnackKeyword`

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| `term` | `String` | no (record component may be `null` before normalization) | A key term of the snack; plain, untrusted text. |
| `definition` | `String` | no (same caveat) | At most 3 whitespace-separated tokens; plain, untrusted text. |

```java
public record SnackKeyword(String term, String definition) {
    public SnackKeyword {
        term = term == null ? "" : term;
        definition = definition == null ? "" : definition;
    }
}
```

The record is a **pure domain value object**: no JPA annotations, no framework imports, Jackson
serializable (required by the JSON converter). It performs **no** truncation, dropping, or de-duplication
— that policy lives at the service boundary (§4) so the entity stays a faithful representation of what
was stored.

---

## 2. Extended entity — `Snack`

`com.endia.domain.model.Snack` — existing fields unchanged; one field added.

| Field | Type | Column | Nullable | Notes |
|-------|------|--------|----------|-------|
| `id` | `UUID` | `id` | no | `@GeneratedValue(UUID)`. |
| `subtopic` | `Subtopic` | `subtopic_id` | no | `@ManyToOne(LAZY)`; `idx_snack_subtopic`. |
| `concept` | `String` | `concept` (`TEXT`) | no | Feature-012 rich text; unchanged. |
| `metaphor` | `String` | `metaphor` (`TEXT`) | no | Feature-012 rich text; unchanged. |
| `whyItMatters` | `String` | `why_it_matters` (`TEXT`) | no | Feature-012 rich text; unchanged. |
| `related` | `List<String>` | `related` (`JSON`) | no | `JpaJsonConverter`; plain labels; never a highlight source. |
| **`keywords`** | **`List<SnackKeyword>`** | **`keywords` (`JSON`)** | **yes** | **NEW.** `JpaSnackKeywordListConverter`; ordered; may be empty. |

```java
@Convert(converter = JpaSnackKeywordListConverter.class)
@Column(nullable = true, columnDefinition = "JSON")
private List<SnackKeyword> keywords = new ArrayList<>();
```

Constructors:
- keep the existing `Snack(Subtopic, String concept, String metaphor, String whyItMatters, List<String> related)`
  (delegates with `List.of()`) so existing tests and call sites compile unchanged (FR-030);
- add `Snack(Subtopic, concept, metaphor, whyItMatters, related, List<SnackKeyword> keywords)`, used by
  `SnackService`.

---

## 3. New converter — `JpaSnackKeywordListConverter`

`com.endia.infrastructure.persistence.JpaSnackKeywordListConverter`
`implements AttributeConverter<List<SnackKeyword>, String>`

| Direction | Input | Output |
|-----------|-------|--------|
| to DB | `null` or empty list | `"[]"` |
| to DB | non-empty list | Jackson `writeValueAsString` (array of `{term, definition}`) |
| to entity | `null` / blank | `List.of()` |
| to entity | JSON array | `readValue(dbData, new TypeReference<List<SnackKeyword>>(){})` |

It deliberately mirrors `JpaJsonObjectConverter` (feature 001) in structure, error handling
(`RuntimeException` on `JsonProcessingException`), and the empty/null → empty-list policy. It is a **new
class** because an `AttributeConverter<X, String>` is statically bound to one attribute type; the
existing converter is bound to `DragDropContent` and cannot be reused for `List<SnackKeyword>`
(research.md R1).

---

## 4. Normalization policy (application boundary)

`SnackService.normalizeKeywords(List<SnackKeyword> raw) -> List<SnackKeyword>` — pure, order-preserving:

1. Drop `null` entries.
2. Trim `term` and `definition`.
3. Drop any keyword whose trimmed `term` or trimmed `definition` is blank.
4. Truncate `definition` to the first **3 whitespace-separated tokens** (`split("\\s+")`; hyphenated
   compounds count as one token).
5. Drop a keyword whose definition is empty after truncation.
6. De-duplicate by trimmed term **case-insensitively**, keeping the first occurrence.
7. The result may be empty — the snack is still valid.

Keywords are **never** consulted by `SnackService.isValid`; their presence, absence, or invalidity
cannot trigger a re-roll or a generation failure (FR-005, FR-008).

---

## 5. Migration / rollback

**Mechanism**: `spring.jpa.hibernate.ddl-auto=update` (`backend/src/main/resources/application.yml`).
No migration directory exists in `backend/src/main/resources`.

**Generated DDL** (MySQL 8, on next backend start):
```sql
alter table snack add column keywords JSON null;
```

**Manual equivalent** (authoritative for any environment where `ddl-auto` is disabled or pinned):
```sql
ALTER TABLE snack ADD COLUMN keywords JSON NULL;
```

**Backward compatibility**:
- Pre-existing rows get `keywords = NULL`.
- `JpaSnackKeywordListConverter.convertToEntityAttribute(null)` returns `List.of()`, so old snacks load
  with an empty keyword list and serialize as `"keywords": []` (FR-012, US5 AS3, SC-006).
- No data is rewritten, so the change is fully additive and reversible.

**Rollback**:
```sql
ALTER TABLE snack DROP COLUMN keywords;
```
(Then revert the entity, converter, port, DTO, prompt, and frontend changes.)

**Integration test**: `SnackKeywordsPersistenceIntegrationTest` runs against MySQL 8 Testcontainers with
`spring.jpa.hibernate.ddl-auto=create-drop`, exercising the same entity mapping and converter.

---

## 6. API contract shapes (extended in place)

`keywords` is appended as the last field of every snack object. Types are unchanged elsewhere.

| Surface | DTO / record | Snack object fields (new field in bold) |
|---------|--------------|------------------------------------------|
| Fresh generation | `SnackGenerateApiResponse.SnackItem` | `id, subtopicId, subtopicName, concept, metaphor, whyItMatters, related,` **`keywords`** |
| Batch status | `BatchStatusApiResponse.SnackDto` | same as above |
| Results recap | `QuizSummaryApiResponse.SnackDto` | same as above |
| History (feature 011) | `ThemeHistoryApiResponse.SnackItem` | `id, concept, metaphor, whyItMatters, related,` **`keywords`** |

`keywords` serializes as an array of `{ term, definition }` objects; an empty list is `[]` and is always
present (never omitted). See contracts/snack-keywords-contract.md for the full shape and examples.

`BatchService.BatchItemStatus` and `QuizService.Recap` carry the raw `Snack` entity and therefore need
no change; only the controller-level mapping to the DTOs gains `keywords`. `ThemeService.SnackHistory`
is an application-layer record and **does** gain `keywords`.

`application/dto/SnackGenerateResponse.java` exists but is **unused** (no references outside its own
file). It is intentionally left untouched by this feature.

---

## 7. Frontend view models

TypeScript interfaces mirror the API; `keywords` is typed **optional** for defensive graceful
degradation, and use sites coalesce with `?? []`.

```ts
// models/snack.model.ts
export interface SnackKeyword { term: string; definition: string; }

export interface Snack {
  id: string;
  subtopicId: string;
  subtopicName: string;
  concept: string;
  metaphor: string;
  whyItMatters: string;
  related: string[];
  keywords?: SnackKeyword[];
}
```
`batch.model.ts` (`BatchItem.snack`), `quiz.model.ts` (`Recap.snack`), and `history.model.ts`
(`HistorySnack`) gain the same optional `keywords?: SnackKeyword[]` field.

The **new client-only rendering types** introduced by this feature (no persistence, no API surface):

```ts
// components/rich-text/rich-text.model.ts
export type RichInline =
  | { kind: 'text'; text: string }
  | { kind: 'bold'; nodes: RichInline[] }                 // recursive (was { text: string })
  | { kind: 'break' }
  | { kind: 'keyword'; text: string; term: string; definition: string };
```
These are produced by `parseRichText(text, keywords)` (which calls the pure `highlightKeywords`) and
consumed only by `RichTextComponent`.

---

## 8. Key entities (spec cross-reference)

- **SnackKeyword** (new) → §1.
- **Snack** (extended) → §2.
- **Snack JSON contract** (extended) → §6.
- **Keyword highlight** (UI concept) → contracts/snack-keywords-contract.md §2.
- **Definition card** (UI concept) → contracts/snack-keywords-contract.md §3.
- **Word-count rule** (concept) → §4 step 4.
