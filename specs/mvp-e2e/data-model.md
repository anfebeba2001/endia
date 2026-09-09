# Data Model: Endia MVP — E2E Learning Loop

**Database**: MySQL 8.x
**ORM**: Spring Data JPA + Hibernate 6.x
**DDL Strategy**: `spring.jpa.hibernate.ddl-auto=update` (dev), manual migrations (production)

---

## Entity Relationship Diagram

```
Theme 1──< * Subtopic
Subtopic 1──< * Snack
Subtopic 1──< * QuizQuestion
QuizQuestion 1──< * AnswerAttempt
```

---

## Entity Definitions

### Theme

Represents a user's study topic. The entry point of the learning loop.

| Field | Type | Column | Constraints | Notes |
|-------|------|--------|-------------|-------|
| `id` | `java.util.UUID` | `id` | PK, `CHAR(36)`, auto-generated | Primary key |
| `prompt` | `String` | `prompt` | `VARCHAR(500)`, NOT NULL | User's free-text theme input |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL, default `CURRENT_TIMESTAMP` | Creation timestamp |

**Indexes**:
- `PK` on `id`

**Relationships**:
- `Theme 1──< * Subtopic` — `Theme.subtopics` (`@OneToMany`, cascade `ALL`, orphanRemoval `true`, fetch `LAZY`)

```java
@Entity
@Table(name = "theme")
public class Theme {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String prompt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "theme", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Subtopic> subtopics = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // constructors, getters, setters
}
```

---

### Subtopic

A curated slice of a theme suggested by the LLM. Bridges themes to content (snacks and quizzes).

| Field | Type | Column | Constraints | Notes |
|-------|------|--------|-------------|-------|
| `id` | `java.util.UUID` | `id` | PK, `CHAR(36)`, auto-generated | Primary key |
| `theme` | `Theme` | `theme_id` | FK, NOT NULL | Parent theme |
| `name` | `String` | `name` | `VARCHAR(200)`, NOT NULL | Subtopic name from LLM |
| `selected` | `boolean` | `selected` | NOT NULL, default `true` | Whether user chose this subtopic |

**Indexes**:
- `PK` on `id`
- `IDX_SUBTOPIC_THEME` on `theme_id`

**Relationships**:
- `Subtopic >── 1 Theme` — `Subtopic.theme` (`@ManyToOne`, fetch `LAZY`)
- `Subtopic 1──< * Snack` — `Subtopic.snacks` (`@OneToMany`, cascade `ALL`, orphanRemoval `true`, fetch `LAZY`)
- `Subtopic 1──< * QuizQuestion` — `Subtopic.quizQuestions` (`@OneToMany`, cascade `ALL`, orphanRemoval `true`, fetch `LAZY`)

```java
@Entity
@Table(name = "subtopic")
public class Subtopic {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "theme_id", nullable = false)
    private Theme theme;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private boolean selected = true;

    @OneToMany(mappedBy = "subtopic", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Snack> snacks = new ArrayList<>();

    @OneToMany(mappedBy = "subtopic", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<QuizQuestion> quizQuestions = new ArrayList<>();

    // constructors, getters, setters
}
```

---

### Snack

A knowledge card for a subtopic. Contains the educational content that the user reads before taking the quiz.

| Field | Type | Column | Constraints | Notes |
|-------|------|--------|-------------|-------|
| `id` | `java.util.UUID` | `id` | PK, `CHAR(36)`, auto-generated | Primary key |
| `subtopic` | `Subtopic` | `subtopic_id` | FK, NOT NULL | Parent subtopic |
| `concept` | `String` | `concept` | `TEXT`, NOT NULL | One-paragraph concept definition |
| `metaphor` | `String` | `metaphor` | `TEXT`, NOT NULL | Real-life analogy or metaphor |
| `whyItMatters` | `String` | `why_it_matters` | `TEXT`, NOT NULL | Stakes, pitfalls, importance |
| `related` | `List<String>` | `related` | `JSON`, NOT NULL | 2–3 related concept names |

**Indexes**:
- `PK` on `id`
- `IDX_SNACK_SUBTOPIC` on `subtopic_id`

**Relationships**:
- `Snack >── 1 Subtopic` — `Snack.subtopic` (`@ManyToOne`, fetch `LAZY`)

**JSON Conversion**: `related` field uses `@Convert(converter = JpaJsonConverter.class)` to store `List<String>` as a MySQL JSON column.

```java
@Entity
@Table(name = "snack")
public class Snack {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subtopic_id", nullable = false)
    private Subtopic subtopic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String concept;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String metaphor;

    @Column(name = "why_it_matters", nullable = false, columnDefinition = "TEXT")
    private String whyItMatters;

    @Convert(converter = JpaJsonConverter.class)
    @Column(nullable = false, columnDefinition = "JSON")
    private List<String> related = new ArrayList<>();

    // constructors, getters, setters
}
```

---

### QuizQuestion

A multiple-choice question for a subtopic. The `correctIndex` is the source of truth for grading — it is set after shuffling and must not be trusted from LLM output.

| Field | Type | Column | Constraints | Notes |
|-------|------|--------|-------------|-------|
| `id` | `java.util.UUID` | `id` | PK, `CHAR(36)`, auto-generated | Primary key |
| `subtopic` | `Subtopic` | `subtopic_id` | FK, NOT NULL | Parent subtopic |
| `prompt` | `String` | `prompt` | `TEXT`, NOT NULL | Question text |
| `options` | `List<String>` | `options` | `JSON`, NOT NULL | Exactly 4 option strings |
| `correctIndex` | `int` | `correct_index` | NOT NULL | Index of correct answer (0–3) |
| `explanation` | `String` | `explanation` | `TEXT`, NOT NULL | Why the correct answer is correct |

**Indexes**:
- `PK` on `id`
- `IDX_QUIZ_QUESTION_SUBTOPIC` on `subtopic_id`

**Relationships**:
- `QuizQuestion >── 1 Subtopic` — `QuizQuestion.subtopic` (`@ManyToOne`, fetch `LAZY`)
- `QuizQuestion 1──< * AnswerAttempt` — `QuizQuestion.answerAttempts` (`@OneToMany`, cascade `ALL`, orphanRemoval `true`, fetch `LAZY`)

**JSON Conversion**: `options` field uses `@Convert(converter = JpaJsonConverter.class)` to store `List<String>` as a MySQL JSON column.

```java
@Entity
@Table(name = "quiz_question")
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subtopic_id", nullable = false)
    private Subtopic subtopic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Convert(converter = JpaJsonConverter.class)
    @Column(nullable = false, columnDefinition = "JSON")
    private List<String> options = new ArrayList<>();

    @Column(name = "correct_index", nullable = false)
    private int correctIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @OneToMany(mappedBy = "quizQuestion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AnswerAttempt> answerAttempts = new ArrayList<>();

    // constructors, getters, setters
}
```

---

### AnswerAttempt

A record of a user answering a quiz question. Tracks correctness and XP earned. In MVP, XP is not persisted to a user profile — it lives only in this record and in API responses.

| Field | Type | Column | Constraints | Notes |
|-------|------|--------|-------------|-------|
| `id` | `java.util.UUID` | `id` | PK, `CHAR(36)`, auto-generated | Primary key |
| `quizQuestion` | `QuizQuestion` | `quiz_question_id` | FK, NOT NULL | The question answered |
| `chosenIndex` | `int` | `chosen_index` | NOT NULL | Index of user's chosen option (0–3) |
| `correct` | `boolean` | `correct` | NOT NULL | Whether the answer was correct |
| `xpEarned` | `int` | `xp_earned` | NOT NULL | XP for this answer (0 or 10) |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL, default `CURRENT_TIMESTAMP` | When the answer was submitted |

**Indexes**:
- `PK` on `id`
- `IDX_ANSWER_ATTEMPT_QUESTION` on `quiz_question_id`
- `IDX_ANSWER_ATTEMPT_QUESTION_INDEX` on `quiz_question_id`, `chosen_index` (for duplicate-answer detection)

**Unique Constraint**: `(quiz_question_id, chosen_index)` — prevents the same user from answering the same question twice (MVP: single anonymous user, so this enforces one attempt per question).

**Relationships**:
- `AnswerAttempt >── 1 QuizQuestion` — `AnswerAttempt.quizQuestion` (`@ManyToOne`, fetch `LAZY`)

```java
@Entity
@Table(name = "answer_attempt",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"quiz_question_id", "chosen_index"}
       ))
public class AnswerAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_question_id", nullable = false)
    private QuizQuestion quizQuestion;

    @Column(name = "chosen_index", nullable = false)
    private int chosenIndex;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "xp_earned", nullable = false)
    private int xpEarned;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // constructors, getters, setters
}
```

---

## Supporting Infrastructure

### JpaJsonConverter

JPA `AttributeConverter` that serializes `List<String>` to/from MySQL JSON strings using Jackson.

```java
@Converter
public class JpaJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        try {
            return mapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert list to JSON", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isBlank()) return List.of();
            return mapper.readValue(dbData, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert JSON to list", e);
        }
    }
}
```

---

## Schema Summary (MySQL DDL equivalent)

```sql
CREATE TABLE theme (
    id CHAR(36) PRIMARY KEY,
    prompt VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE subtopic (
    id CHAR(36) PRIMARY KEY,
    theme_id CHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    selected BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (theme_id) REFERENCES theme(id)
);
CREATE INDEX idx_subtopic_theme ON subtopic(theme_id);

CREATE TABLE snack (
    id CHAR(36) PRIMARY KEY,
    subtopic_id CHAR(36) NOT NULL,
    concept TEXT NOT NULL,
    metaphor TEXT NOT NULL,
    why_it_matters TEXT NOT NULL,
    related JSON NOT NULL,
    FOREIGN KEY (subtopic_id) REFERENCES subtopic(id)
);
CREATE INDEX idx_snack_subtopic ON snack(subtopic_id);

CREATE TABLE quiz_question (
    id CHAR(36) PRIMARY KEY,
    subtopic_id CHAR(36) NOT NULL,
    prompt TEXT NOT NULL,
    options JSON NOT NULL,
    correct_index INT NOT NULL,
    explanation TEXT NOT NULL,
    FOREIGN KEY (subtopic_id) REFERENCES subtopic(id)
);
CREATE INDEX idx_quiz_question_subtopic ON quiz_question(subtopic_id);

CREATE TABLE answer_attempt (
    id CHAR(36) PRIMARY KEY,
    quiz_question_id CHAR(36) NOT NULL,
    chosen_index INT NOT NULL,
    correct BOOLEAN NOT NULL,
    xp_earned INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (quiz_question_id) REFERENCES quiz_question(id),
    UNIQUE KEY uk_answer_attempt_question_index (quiz_question_id, chosen_index)
);
CREATE INDEX idx_answer_attempt_question ON answer_attempt(quiz_question_id);
```
