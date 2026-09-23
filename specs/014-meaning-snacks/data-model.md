# Data Model: "Generate snack" from a Keyword Definition into the Snack Backbone (No Quiz)

**Feature**: `014-meaning-snacks`

**Persistence**: **ONE additive schema change** — a **new table `meaning_snack`**. No existing table
is altered, no existing column is added/renamed/dropped, and no existing row is rewritten. Pre-existing
`theme`, `subtopic`, `snack`, `quiz_question`, `answer_attempt`, `study_session` and
`study_session_item` data remain valid and unchanged (FR-019, SC-007). The project has **no
Flyway/Liquibase**; the table is created by Hibernate `ddl-auto=update` (see §5).

---

## 1. New domain enum — `SnackKind`

`com.endia.domain.enums.SnackKind`

| Value | Meaning |
|-------|---------|
| `MEANING` | A snack derived from a keyword definition; **never** quiz-bearing. |

The kind marker is explicit in storage (column `kind`) and in the API (`kind: "MEANING"`) so no
surface can conflate a meaning snack with a regular `Snack` (FR-016). A regular `Snack` has no kind
column and is unchanged; the discriminator is the entity/table boundary itself plus this marker.

---

## 2. New entity — `MeaningSnack`

`com.endia.domain.model.MeaningSnack` — `@Entity @Table(name = "meaning_snack", indexes = { ... })`.

| Field | Type | Column | Nullable | Notes |
|-------|------|--------|----------|-------|
| `id` | `UUID` | `id` | no | `@GeneratedValue(UUID)`. |
| `theme` | `Theme` | `theme_id` | no | `@ManyToOne(LAZY, optional=false)`. The theme the snack belongs to (Clarification #2/#3). |
| `sourceSubtopic` | `Subtopic` | `source_subtopic_id` | no | `@ManyToOne(LAZY, optional=false)`. Placement anchor in the backbone; **not** a quiz target. |
| `sourceTerm` | `String` | `source_term` (`VARCHAR(500)`) | no | The keyword `term` the snack was generated from (FR-008). Plain, untrusted text. |
| `sourceDefinition` | `String` | `source_definition` (`VARCHAR(200)`) | no | The ≤3-word keyword `definition` (normalized at the service boundary). Plain, untrusted text. |
| `concept` | `String` | `concept` (`TEXT`) | no | Generated content; feature-012 rich text; validated non-empty. |
| `metaphor` | `String` | `metaphor` (`TEXT`) | no | Generated content; validated non-empty. |
| `whyItMatters` | `String` | `why_it_matters` (`TEXT`) | no | Generated content; validated non-empty. |
| `related` | `List<String>` | `related` (`JSON`) | no | `JpaJsonConverter` (reused); plain labels; validated 1..3. |
| `kind` | `SnackKind` | `kind` (`VARCHAR(20)`) | no | `@Enumerated(STRING)`; default/always `MEANING`. |
| `createdAt` | `LocalDateTime` | `created_at` | no | `@PrePersist`; used for stable ordering in the listing. |

```java
@Entity
@Table(name = "meaning_snack",
       indexes = {
           @Index(name = "idx_meaning_snack_theme", columnList = "theme_id"),
           @Index(name = "idx_meaning_snack_subtopic", columnList = "source_subtopic_id"),
           @Index(name = "ux_meaning_snack_source", columnList = "source_subtopic_id, source_term", unique = true)
       })
@Getter @Setter @NoArgsConstructor
public class MeaningSnack {
    // fields as above; @PrePersist sets createdAt = LocalDateTime.now()
}
```

**Relationship direction**: one-directional `@ManyToOne` only. `Theme` and `Subtopic` are **not**
modified (no new `@OneToMany` collection), so their fetch/cascade/orphan behaviour is untouched and
no regression risk is introduced (FR-024/FR-026). All reads go through the repository.

**No quiz fields.** The entity has no `subtopic` quiz association, no questions, no `quizStatus`. A
meaning snack is structurally incapable of carrying a quiz (FR-014..FR-016).

---

## 3. JSON converter — reuse `JpaJsonConverter`

`related` reuses the existing
`com.endia.infrastructure.persistence.JpaJsonConverter implements AttributeConverter<List<String>, String>`
(the same converter used by `Snack.related`). **No new converter class is introduced.** Its
null → `"[]"` / blank → `List.of()` policy is already proven by feature 013's persistence tests.

A converter for a structured `List<SnackKeyword>` is **not** needed: the meaning snack's source is a
single `term`+`definition` pair stored as two scalar columns, and its generated content has no
keyword list (research.md R1/R2).

---

## 4. Validation / normalization policy (application boundary)

`MeaningSnackService` is the single boundary. `SnackService` is **not** modified.

### 4.1 Request validation (controller + service)

| Rule | Behaviour |
|------|-----------|
| `themeId`, `subtopicId` non-null | Bean validation → 400 `VALIDATION_ERROR` (field named). |
| `term`, `definition` non-blank | Bean validation → 400 `VALIDATION_ERROR`. |
| `themeId` resolves to a `Theme` | Else 404 `NOT_FOUND`. |
| `subtopicId` resolves to a `Subtopic` | Else 404 `NOT_FOUND`. |

### 4.2 Source normalization

1. Trim `sourceTerm`; if blank → 400/503 (never persisted).
2. Trim `sourceDefinition`; truncate to the first **3 whitespace-separated tokens**
   (`split("\\s+")`; hyphenated compounds count as one token) — mirrors `SnackService.normalizeKeywords`
   step 4; if empty after truncation → rejected.
3. These normalized values are stored verbatim and returned unchanged (no HTML escaping at rest;
   escaping happens at render time only, FR-021).

### 4.3 Generated-content validation (mirrors the snack gate)

- `concept`, `metaphor`, `whyItMatters` must be non-blank;
- `related` must be non-null, non-empty and at most 3 items;
- on failure, re-roll **once**; a second failure → `ServiceUnavailableException` (503);
- `keywords` (if the model emits any) is ignored — a meaning snack has no keyword list.

### 4.4 Idempotency (FR-006)

The uniqueness key is `(sourceSubtopicId, sourceTerm)` compared **case-insensitively**. The primary
guard is `JpaMeaningSnackRepository.findBySourceSubtopicIdAndSourceTermIgnoreCase(...)`; the unique
index `ux_meaning_snack_source` is defence-in-depth. When an existing row matches, `generate` returns
it with `alreadyExisted = true` and **does not call the LLM**, so repeated/rapid activation and
re-opening the card cannot create a duplicate (FR-005/FR-006, SC-002).

---

## 5. Migration / rollback

**Mechanism**: `spring.jpa.hibernate.ddl-auto=update` (`backend/src/main/resources/application.yml`).
No migration directory exists in `backend/src/main/resources`.

**Generated DDL** (MySQL 8, on next backend start):

```sql
create table meaning_snack (
  id binary(16) not null,
  theme_id binary(16) not null,
  source_subtopic_id binary(16) not null,
  source_term varchar(500) not null,
  source_definition varchar(200) not null,
  concept TEXT not null,
  metaphor TEXT not null,
  why_it_matters TEXT not null,
  related JSON not null,
  kind varchar(20) not null,
  created_at datetime(6) not null,
  primary key (id),
  constraint ux_meaning_snack_source unique (source_subtopic_id, source_term)
);
create index idx_meaning_snack_theme on meaning_snack (theme_id);
create index idx_meaning_snack_subtopic on meaning_snack (source_subtopic_id);
```

**Manual equivalent** (authoritative for any environment where `ddl-auto` is disabled or pinned):

```sql
CREATE TABLE meaning_snack (
  id BINARY(16) NOT NULL,
  theme_id BINARY(16) NOT NULL,
  source_subtopic_id BINARY(16) NOT NULL,
  source_term VARCHAR(500) NOT NULL,
  source_definition VARCHAR(200) NOT NULL,
  concept TEXT NOT NULL,
  metaphor TEXT NOT NULL,
  why_it_matters TEXT NOT NULL,
  related JSON NOT NULL,
  kind VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY ux_meaning_snack_source (source_subtopic_id, source_term)
);
CREATE INDEX idx_meaning_snack_theme ON meaning_snack (theme_id);
CREATE INDEX idx_meaning_snack_subtopic ON meaning_snack (source_subtopic_id);
```

**Backward compatibility**:
- A brand-new table: no existing table/column/row is touched, so pre-existing snacks, quizzes,
  sessions and history load and behave exactly as before (FR-019, SC-007).
- The unique index is created on an empty table, so there is no duplicate-conflict risk at creation.
- No data is rewritten, so the change is fully additive and reversible.

**Rollback**:

```sql
DROP TABLE meaning_snack;
```

(Then revert the entity, enum, repository, service, controller, DTOs, prompt, port/provider changes
and the frontend changes.)

**Integration test**: `MeaningSnackPersistenceIntegrationTest` runs against MySQL 8 Testcontainers
with `spring.jpa.hibernate.ddl-auto=create-drop`, exercising the same mapping, converter, unique
index and idempotency query.

---

## 6. API contract shapes (new endpoints)

Types are new; no existing DTO changes (feature 013's DTOs are untouched).

| Endpoint | Response record | Fields |
|----------|-----------------|--------|
| `POST /api/meaning-snacks/generate` | `MeaningSnackGenerateApiResponse` | `alreadyExisted: boolean`, `meaningSnack: MeaningSnackItem` |
| `GET /api/meaning-snacks?themeId=` | `MeaningSnackListApiResponse` | `themeId: UUID`, `meaningSnacks: MeaningSnackItem[]` |

`MeaningSnackItem`:

| Field | Type | Notes |
|-------|------|-------|
| `id` | `UUID` | |
| `kind` | `String` | Always `"MEANING"` (the discriminator). |
| `themeId` | `UUID` | |
| `sourceSubtopicId` | `UUID` | Placement anchor in the backbone (not a quiz target). |
| `sourceSubtopicName` | `String` | Display name for the index/badge. |
| `sourceTerm` | `String` | The keyword term (FR-008). |
| `sourceDefinition` | `String` | The ≤3-word definition (FR-008). |
| `concept` | `String` | Generated rich text. |
| `metaphor` | `String` | Generated rich text. |
| `whyItMatters` | `String` | Generated rich text. |
| `related` | `List<String>` | Generated labels. |
| `createdAt` | `LocalDateTime` | Stable ordering. |

**Deliberately absent**: `subtopicId` (as a quiz target), `quizStatus`, `quizQuestions`, `keywords`.
Their absence is the API-level expression of the no-quiz invariant (FR-016, SC-005) and is asserted by
`MeaningSnackContractTest`.

`GET /api/meaning-snacks` returns `{ "themeId": "...", "meaningSnacks": [] }` when the theme has no
meaning snacks (never `null`, never omitted). An unknown `themeId` returns 404 `NOT_FOUND`; the
frontend treats any listing error as an empty list (FR-020).

`GET /api/themes` (history) is **unchanged**: meaning snacks are not part of the history read model,
so the history data shape is preserved and no surface can treat a meaning snack as a regular
quiz-bearing snack (FR-016, Non-Goals).

---

## 7. Frontend view models

TypeScript interfaces mirror the API; all new types are additive.

```ts
// models/meaning-snack.model.ts
export type MeaningSnackKind = 'MEANING';
export interface MeaningSnack {
  id: string;
  kind: MeaningSnackKind;
  themeId: string;
  sourceSubtopicId: string;
  sourceSubtopicName: string;
  sourceTerm: string;
  sourceDefinition: string;
  concept: string;
  metaphor: string;
  whyItMatters: string;
  related: string[];
  createdAt: string;
}
export interface MeaningSnackGenerateResponse { alreadyExisted: boolean; meaningSnack: MeaningSnack; }
export interface MeaningSnackListResponse { themeId: string; meaningSnacks: MeaningSnack[]; }
```

```ts
// models/batch.model.ts (additive)
export interface RegularEntry {
  entryId: string;                 // = subtopicId
  kind: 'regular';
  subtopicId: string;
  subtopicName: string;
  snackStatus: GenerationStatus;
  snackPhase?: SnackGenerationPhase | null;
  snack: Snack | null;
  snackCause: LlmErrorCause | null;
  quizStatus: GenerationStatus;
  quizCause: LlmErrorCause | null;
  quizQuestions?: QuizQuestion[] | null;
}
export interface MeaningEntry {
  entryId: string;                 // = `meaning:${meaning.id}`
  kind: 'meaning';
  sourceSubtopicId: string;
  meaning: MeaningSnack;
}
export type BackboneEntry = RegularEntry | MeaningEntry;
```

The existing `BatchItem` interface remains for the batch/status payload and for `AppStateService`
regular-snack hydration; `RegularEntry` is its backbone projection. `AppStateService` gains
`meaningSnacks = signal<MeaningSnack[]>([])` (cache only; the backend listing is the source of truth
on load).

---

## 8. Key entities (spec cross-reference)

- **MeaningSnack** (new) → §2.
- **SnackKind / meaning-snack kind marker** (new) → §1, §6.
- **Snack** (existing, unchanged) → not modified; regular snacks remain subtopic-bound and quiz-bearing.
- **SnackKeyword** (feature 013) → the `term`+`definition` source; read by the client and sent to the
  generate endpoint; not persisted on the meaning snack as a keyword list.
- **Main topics backbone** (UI concept) → the `/snacks` reading surface; `BackboneEntry` union → §7.
- **Retrieval path** (concept) → `GET /api/meaning-snacks?themeId=` → §6.
- **No-quiz invariant** → §2 (no quiz fields), §6 (no quiz fields in the API), plan.md M3/M5.
