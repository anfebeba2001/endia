# Data Model: Endia MVP — Batch Enhancements

**Database**: MySQL 8.x
**ORM**: Spring Data JPA + Hibernate 6.x
**DDL Strategy**: `spring.jpa.hibernate.ddl-auto=update` (dev). New tables created by Hibernate; extended tables get new columns via `update`. `AnswerAttempt` unique-constraint change is the one schema-breaking edit (see Notes) — it will not recreate the table automatically; in dev it requires dropping the old constraint (or a manual SQL migration).

> **Compatible with** `/specs/mvp-e2e/data-model.md`. New/extended entities below; unchanged entities (Theme, Snack) are summarized only.

---

## Entity Relationship Diagram

```
Theme 1──< * Subtopic (source: AI_SUGGESTED | USER_PROPOSED)
Subtopic 1──< * Snack
Subtopic 1──< * QuizQuestion (type: MULTIPLE_CHOICE | DRAG_DROP)
QuizQuestion 1──< * AnswerAttempt (chosenIndex nullable; +arrangement JSON)

StudySession 1──< * StudySessionItem
StudySessionItem >── 1 Subtopic
StudySessionItem ?── 1 Snack      (snackId, set when READY)
StudySessionItem 1 : 1 quiz status (per-subtopic; quiz stored under Subtopic)
```

---

## Entity Definitions

### Subtopic (extended)

Adds a provenance discriminator so user-proposed subtopics are persisted and processed exactly like AI-suggested ones.

| Field | Type | Column | Constraints | Notes |
|-------|------|--------|-------------|-------|
| `id` | `UUID` | `id` | PK | unchanged |
| `theme` | `Theme` | `theme_id` | FK, NOT NULL | unchanged |
| `name` | `String` | `name` | `VARCHAR(200)`, NOT NULL | unchanged |
| `selected` | `boolean` | `selected` | NOT NULL, default `true` | unchanged |
| `source` | `SubtopicSource` | `source` | `VARCHAR(20)`, NOT NULL | **NEW**. `AI_SUGGESTED` (default) or `USER_PROPOSED` |

Enum `SubtopicSource { AI_SUGGESTED, USER_PROPOSED }` stored as `@Enumerated(EnumType.STRING)`.

---

### Snack (unchanged)

Core fields unchanged: `id`, `subtopic` (FK), `concept` (TEXT), `metaphor` (TEXT), `whyItMatters` (TEXT), `related` (`List<String>` JSON). Used by the results recap (FR-009) via `JpaSnackRepository.findBySubtopicId`.

---

### QuizQuestion (extended)

Becomes a polymorphic question: `MULTIPLE_CHOICE` uses `options`+`correctIndex`; `DRAG_DROP` uses `variant`+`dragDropContent`.

| Field | Type | Column | Constraints | Notes |
|-------|------|--------|-------------|-------|
| `id` | `UUID` | `id` | PK | unchanged |
| `subtopic` | `Subtopic` | `subtopic_id` | FK, NOT NULL | unchanged |
| `prompt` | `String` | `prompt` | `TEXT`, NOT NULL | unchanged |
| `type` | `QuestionType` | `question_type` | `VARCHAR(20)`, NOT NULL | **NEW**. `MULTIPLE_CHOICE` (default) or `DRAG_DROP`. |
| `options` | `List<String>` | `options` | JSON, NOT NULL | MCQ only. |
| `correctIndex` | `Integer` | `correct_index` | nullable | MCQ only; null for drag-drop. |
| `explanation` | `String` | `explanation` | `TEXT`, NOT NULL | unchanged; explains the correct answer (both types). |
| `variant` | `DragDropVariant` | `variant` | `VARCHAR(20)`, nullable | **NEW**. Drag-drop only: `SEQUENCING`/`CLASSIFICATION`/`MATCHING`. |
| `dragDropContent` | `DragDropContent` | `drag_drop_content` | JSON, nullable | **NEW**. Drag-drop only. Contains items + canonical arrangement (source of truth for grading). |

Enums:
```
QuestionType  { MULTIPLE_CHOICE, DRAG_DROP }
DragDropVariant { SEQUENCING, CLASSIFICATION, MATCHING }
```

**JSON Conversion**: `dragDropContent` uses `@Convert(converter = JpaJsonObjectConverter.class)`. A general purpose converter serializing an object to/from a JSON String (unlike the `List<String>`-only `JpaJsonConverter`).

```java
@Convert(converter = JpaJsonObjectConverter.class)
@Column(name = "drag_drop_content", columnDefinition = "JSON")
private DragDropContent dragDropContent;
```

**Validation** (in `QuizQualityGate`, before persist): prompt non-blank ≥5 chars; explanation non-blank; if `MULTIPLE_CHOICE`: options == 4, no dupes, correctIndex in range (existing rules). If `DRAG_DROP`: `variant` in the allowed set; `items` non-empty and distinct; correct arrangement set and composed of exactly `items`; `CLASSIFICATION` → every item has a category, `categories` non-empty; `MATCHING` → every left key has a right value; `SEQUENCING` → `correctOrder` is a permutation of `items`.

---

### DragDropContent (value object, JSON column)

```java
public record DragDropContent(
        DragDropVariant variant,
        List<String> items,          // draggable tokens
        List<String> categories,     // only CLASSIFICATION (drop targets)
        Map<String, String> pairs,   // only MATCHING  (canonical left→right)
        List<String> correctOrder    // only SEQUENCING (canonical order)
) {}
```
The canonical arrangement (`pairs` / `correctOrder`) is the **source of truth** for grading and is never serialized to the client pre-answer.

---

### AnswerAttempt (extended)

Supports grading both question types. `chosenIndex` becomes nullable (MCQ only); drag-drop submits an ordering/assignment via `arrangement`.

| Field | Type | Column | Constraints | Notes |
|-------|------|--------|-------------|-------|
| `id` | `UUID` | `id` | PK | unchanged |
| `quizQuestion` | `QuizQuestion` | `quiz_question_id` | FK, NOT NULL | unchanged |
| `chosenIndex` | `Integer` | `chosen_index` | nullable | **MOD**. MCQ only; null for drag-drop. |
| `arrangement` | `List<String>` | `arrangement` | JSON, nullable | **NEW**. Drag-drop answer (ordered item ids for sequencing; item-id, then category/right-value for classification/matching). |
| `correct` | `boolean` | `correct` | NOT NULL | unchanged |
| `xpEarned` | `int` | `xp_earned` | NOT NULL | unchanged (+10 correct / 0 incorrect, same as MCQ — FR-008). |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL | unchanged |

**Unique constraint change (breaking)**: the current `uk_answer_attempt_question_index (quiz_question_id, chosen_index)` is **invalid** when `chosen_index` is null (MySQL treats `(qid, null)` as distinct, allowing duplicate drag-drop answers and breaking the one-attempt guarantee). Change to `uk_answer_attempt_question (quiz_question_id)` only. Service already guards with `existsByQuizQuestionId` — this preserves the one-attempt invariant.

```java
@Table(
    name = "answer_attempt",
    indexes = {
        @Index(name = "idx_answer_attempt_question", columnList = "quiz_question_id"),
    },
    uniqueConstraints = @UniqueConstraint(
        name = "uk_answer_attempt_question",
        columnNames = {"quiz_question_id"}
    )
)
```

**Detecting duplicates on drag-drop answer**: `existsByQuizQuestionId` (single anonymous user → one attempt per question), consistent with existing behavior.

---

### StudySession (new) — batch aggregate

Represents **one** batch trigger tying a theme's selected subtopics to a single generation run.

| Field | Type | Column | Constraints | Notes |
|-------|------|--------|-------------|-------|
| `id` | `UUID` | `id` | PK | generated |
| `theme` | `Theme` | `theme_id` | FK, NOT NULL | theme being studied |
| `status` | `BatchStatus` | `status` | `VARCHAR(20)`, NOT NULL | `IN_PROGRESS` (default) / `PARTIAL` / `COMPLETE` / `FAILED` |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL | trigger timestamp |
| `items` | `List<StudySessionItem>` | – | `@OneToMany` cascade ALL, orphanRemoval | per-subtopic status rows |

```java
public enum BatchStatus { IN_PROGRESS, PARTIAL, COMPLETE, FAILED }
```

---

### StudySessionItem (new) — per-subtopic generation status

| Field | Type | Column | Constraints | Notes |
|-------|------|--------|-------------|-------|
| `id` | `UUID` | `id` | PK | generated |
| `session` | `StudySession` | `session_id` | FK, NOT NULL | parent batch |
| `subtopic` | `Subtopic` | `subtopic_id` | FK, NOT NULL | target subtopic |
| `snackStatus` | `GenerationStatus` | `snack_status` | `VARCHAR(20)`, NOT NULL | `PENDING`/`GENERATING`/`READY`/`FAILED` |
| `snack` | `Snack` | `snack_id` | FK, nullable | set when READY (FR-002 render) |
| `snackCause` | `LlmErrorCause` | `snack_cause` | `VARCHAR(20)`, nullable | set on FAILED (FR-015) |
| `quizStatus` | `GenerationStatus` | `quiz_status` | `VARCHAR(20)`, NOT NULL | per-subtopic quiz prefetch (FR-003) |
| `quizCause` | `LlmErrorCause` | `quiz_cause` | `VARCHAR(20)`, nullable | set on quiz FAILED |

```java
public enum GenerationStatus { PENDING, GENERATING, READY, FAILED }
```

**State transitions**: snack/quiz: `PENDING → GENERATING → READY | FAILED`. Batch: derives `COMPLETE` (all items snack=quiz=READY), `PARTIAL` (some READY, some FAILED), `FAILED` (all items FAILED with no READY), else `IN_PROGRESS`.

---

### Theme (unchanged)

`id`, `prompt` (`VARCHAR(500)`), `createdAt`, `List<Subtopic>` cascade. No changes.

---

## Schema Summary (MySQL DDL delta)

```sql
-- Subtopic: add source
ALTER TABLE subtopic ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'AI_SUGGESTED';

-- QuizQuestion: add type, variant, drag_drop_content
ALTER TABLE quiz_question ADD COLUMN question_type VARCHAR(20) NOT NULL DEFAULT 'MULTIPLE_CHOICE';
ALTER TABLE quiz_question ADD COLUMN variant VARCHAR(20) NULL;
ALTER TABLE quiz_question ADD COLUMN drag_drop_content JSON NULL;
ALTER TABLE quiz_question MODIFY COLUMN correct_index INT NULL;

-- AnswerAttempt: chosen_index nullable + arrangement; drop/recreate unique constraint
ALTER TABLE answer_attempt MODIFY COLUMN chosen_index INT NULL;
ALTER TABLE answer_attempt ADD COLUMN arrangement JSON NULL;
ALTER TABLE answer_attempt DROP INDEX uk_answer_attempt_question_index;
ALTER TABLE answer_attempt ADD CONSTRAINT uk_answer_attempt_question UNIQUE (quiz_question_id);

-- StudySession / StudySessionItem (new)
CREATE TABLE study_session (
    id CHAR(36) PRIMARY KEY,
    theme_id CHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (theme_id) REFERENCES theme(id)
);

CREATE TABLE study_session_item (
    id CHAR(36) PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    subtopic_id CHAR(36) NOT NULL,
    snack_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    snack_id CHAR(36) NULL,
    snack_cause VARCHAR(20) NULL,
    quiz_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    quiz_cause VARCHAR(20) NULL,
    FOREIGN KEY (session_id) REFERENCES study_session(id),
    FOREIGN KEY (subtopic_id) REFERENCES subtopic(id),
    FOREIGN KEY (snack_id) REFERENCES snack(id)
);
CREATE INDEX idx_study_session_item_session ON study_session_item(session_id);
```

> **Note**: `ddl-auto=update` will add new columns and create the two new tables automatically, but it will **not** drop the old `uk_answer_attempt_question_index` unique constraint (Hibernate `update` does not remove constraints/indexes). In dev, run the manual `DROP INDEX`/re-add statement once, or drop and recreate the `answer_attempt` table. Flagged in plan Risks.

---

## Supporting Infrastructure (new)

### JpaJsonObjectConverter
A general `AttributeConverter<Object, String>` (Jackson-based) for the structured `DragDropContent`. Unlike `JpaJsonConverter` (typed to `List<String>`), this serializes the record to a JSON String and reads it back with a target class.

```java
@Converter
public class JpaJsonObjectConverter implements AttributeConverter<Object, String> {
    private static final ObjectMapper mapper = new ObjectMapper();
    // convertToDatabaseColumn(obj) -> writeValueAsString
    // convertToEntityAttribute(db) -> readValue(db, DragDropContent.class)
    // Mapping of the target class is configured per-entity attribute or via a
    // small @Converter(autoApply=false) parameterized for DragDropContent.
}
```
Simplest correct form: parameterize the converter for `DragDropContent` (autoApply=false) and apply it on the `dragDropContent` attribute; keep `JpaJsonConverter` for `List<String>`.

---

## Domain rules captured by the model

1. **One attempt per question** (single anonymous user) enforced by unique `(quiz_question_id)` + `existsByQuizQuestionId`.
2. **Drag-drop correct arrangement is server-side stored**, never served to the client pre-answer.
3. **Subtopics are typed by source**; user-proposed get identical down-stream processing.
4. **Snack/quiz status tracking** on `StudySessionItem` enables progressive delivery and partial-failure isolation.
