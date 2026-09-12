# Data Model: Read-Only History of Created Themes

**Feature**: `011-theme-history`

**Persistence**: **NO SCHEMA CHANGE.** This feature is read-only. No MySQL table, column, index,
constraint, JPA entity, enum, converter, or migration is added or modified. History is derived from
the existing `theme`, `subtopic`, `snack`, and `quiz_question` tables (`ddl-auto=update` produces no
delta). This satisfies FR-008/FR-022 and the spec Non-Goals.

The document models (1) the existing entities that are read, (2) the new backend read model + API DTO,
and (3) the new frontend model, plus the **loading strategy** that prevents N+1 and lazy-initialization
failures.

---

## 1. Existing Entities (read-only, UNCHANGED)

### `Theme` — `com.endia.domain.model.Theme`

| Field | Type | Column | Notes |
|-------|------|--------|-------|
| `id` | `UUID` | `id` | `@GeneratedValue(UUID)`; exposed as `themeId`. |
| `prompt` | `String` (500) | `prompt` | User-entered theme. |
| `createdAt` | `LocalDateTime` | `created_at` | Set by `@PrePersist`, `nullable=false`, `updatable=false`; **sort key, descending**. |
| `subtopics` | `List<Subtopic>` | — | `@OneToMany(mappedBy="theme", cascade=ALL, orphanRemoval=true, fetch=LAZY)` — a **bag**. |

### `Subtopic` — `com.endia.domain.model.Subtopic`

| Field | Type | Column | Notes |
|-------|------|--------|-------|
| `id` | `UUID` | `id` | |
| `theme` | `Theme` | `theme_id` | `@ManyToOne(fetch=LAZY, optional=false)`. |
| `name` | `String` (200) | `name` | |
| `selected` | `boolean` | `selected` | Not part of the history contract. |
| `source` | `SubtopicSource` | `source` | Not part of the history contract. |
| `snacks` | `List<Snack>` | — | `@OneToMany(mappedBy="subtopic", ..., fetch=LAZY)` — a **bag**. |
| `quizQuestions` | `List<QuizQuestion>` | — | `@OneToMany(mappedBy="subtopic", ..., fetch=LAZY)` — a **bag**. |

### `Snack` — `com.endia.domain.model.Snack`

| Field | Type | Column | Notes |
|-------|------|--------|-------|
| `id` | `UUID` | `id` | |
| `subtopic` | `Subtopic` | `subtopic_id` | `@ManyToOne(fetch=LAZY, optional=false)`. |
| `concept` | `String` (TEXT) | `concept` | |
| `metaphor` | `String` (TEXT) | `metaphor` | |
| `whyItMatters` | `String` (TEXT) | `why_it_matters` | |
| `related` | `List<String>` | `related` (JSON) | Converted by `JpaJsonConverter`. |

### `QuizQuestion` — `com.endia.domain.model.QuizQuestion`

| Field | Type | Column | Notes |
|-------|------|--------|-------|
| `id` | `UUID` | `id` | |
| `subtopic` | `Subtopic` | `subtopic_id` | `@ManyToOne(fetch=LAZY, optional=false)`. |
| `type` | `QuestionType` | `question_type` | `MULTIPLE_CHOICE` \| `DRAG_DROP`; exposed as its name. |
| `prompt` | `String` (TEXT) | `prompt` | |
| `options` | `List<String>` | `options` (JSON) | **Not** in the history contract. |
| `correctIndex` | `Integer` | `correct_index` | **Not** in the history contract. |
| `variant` | `DragDropVariant` | `variant` | **Not** in the history contract. |
| `dragDropContent` | `DragDropContent` | `drag_drop_content` (JSON) | **Not** in the history contract. |
| `explanation` | `String` (TEXT) | `explanation` | |
| `answerAttempts` | `List<AnswerAttempt>` | — | LAZY; **not** read by this feature. |

---

## 2. Backend Read Model — `ThemeService` nested records (NEW, application layer)

The service returns its **own** read model so the application layer never imports an
`interfaces/rest/dto` type (constitution Principle II). This follows the existing
`ThemeService.ThemeSuggestResult` nested-record convention.

```
ThemeService.ThemeHistory
├── themeId      : UUID
├── prompt       : String
├── createdAt    : LocalDateTime
└── subtopics    : List<SubtopicHistory>
    └── SubtopicHistory
        ├── id             : UUID
        ├── name           : String
        ├── snacks         : List<SnackHistory>
        │   └── SnackHistory { id: UUID, concept, metaphor, whyItMatters: String, related: List<String> }
        └── quizQuestions  : List<QuestionHistory>
            └── QuestionHistory { id: UUID, type: QuestionType, prompt, explanation: String }
```

`QuestionHistory.type` carries the domain enum; the controller serializes it via `.name()`.

---

## 3. API DTO — `ThemeHistoryApiResponse` (NEW, `com.endia.interfaces.rest.dto`)

Per-theme entry returned inside the JSON array by `GET /api/themes`. Record-with-nested-records, in
the style of `ThemeSuggestApiResponse`/`SnackGenerateApiResponse` (FR-006).

```
ThemeHistoryApiResponse
├── themeId    : UUID
├── prompt     : String
├── createdAt  : LocalDateTime   (Jackson ISO-8601 string)
└── subtopics  : List<SubtopicItem>
    └── SubtopicItem { id: UUID, name: String, snacks: List<SnackItem>, quizQuestions: List<QuestionItem> }
        ├── SnackItem    { id: UUID, concept, metaphor, whyItMatters: String, related: List<String> }
        └── QuestionItem { id: UUID, type: String, prompt, explanation: String }
```

The controller returns `ResponseEntity<List<ThemeHistoryApiResponse>>`, so the HTTP body is the
required **array** (FR-001). The empty case is `[]` with HTTP 200.

### Field mapping (domain → DTO)

| DTO field | Source |
|-----------|--------|
| `themeId` | `ThemeHistory.themeId()` |
| `prompt` | `ThemeHistory.prompt()` |
| `createdAt` | `ThemeHistory.createdAt()` (ISO-8601 via Spring Boot's default Jackson config) |
| `subtopics[].id` / `.name` | `SubtopicHistory.id()` / `.name()` |
| `snacks[].id/.concept/.metaphor/.whyItMatters/.related` | `SnackHistory.*` |
| `quizQuestions[].id/.prompt/.explanation` | `QuestionHistory.*` |
| `quizQuestions[].type` | `QuestionHistory.type().name()` |

---

## 4. Read / Loading Strategy (the core decision)

`Theme.subtopics`, `Subtopic.snacks`, and `Subtopic.quizQuestions` are all **bags** (`List`). A single
query that `JOIN FETCH`es two or more bags raises
`org.hibernate.loader.MultipleBagFetchException` (or a row-multiplying cartesian product). A single
`@EntityGraph` spanning `subtopics.snacks` **and** `subtopics.quizQuestions` has the same problem.

**Chosen strategy (3 queries, no N+1, no lazy exceptions):**

```
1. themeRepository.findAllByOrderByCreatedAtDesc()          @EntityGraph(attributePaths = "subtopics")
   └─ one bag (subtopics) → safe; ORDER BY created_at DESC → FR-001 ordering
2. subtopicIds = every Subtopic.id across all returned themes
3. snackRepository.findBySubtopicIdIn(subtopicIds)          (skipped when subtopicIds is empty)
4. quizQuestionRepository.findBySubtopicIdIn(subtopicIds)   (skipped when subtopicIds is empty)
5. group by snack.getSubtopic().getId() / question.getSubtopic().getId()
   (proxy id access does not initialize the association)
6. assemble ThemeHistory read model inside the @Transactional(readOnly = true) method
```

- **N+1 avoided**: the two child collections are loaded with one `IN (...)` query each, not per
  subtopic.
- **`LazyInitializationException` avoided**: every field the read model exposes is materialized while
  the persistence context is open; the controller receives plain records, not managed entities, so
  mapping after the transaction cannot touch a lazy proxy.
- **No entity mutation**: the service never calls `setSnacks`/`setQuizQuestions`, so `orphanRemoval`
  and dirty-checking are irrelevant even though the transaction is read-only.
- **Empty history**: step 1 returns `[]`; steps 3-4 are skipped; the method returns `[]` → HTTP 200
  (FR-005/SC-003).

### Repository additions

| Repository | Method | Purpose |
|------------|--------|---------|
| `JpaThemeRepository` | `@EntityGraph(attributePaths = "subtopics") List<Theme> findAllByOrderByCreatedAtDesc();` | Fetch themes + subtopics in one query, newest-first. |
| `JpaSnackRepository` | `List<Snack> findBySubtopicIdIn(Collection<UUID> subtopicIds);` | Batch-load snacks for all subtopics. |
| `JpaQuizQuestionRepository` | `List<QuizQuestion> findBySubtopicIdIn(Collection<UUID> subtopicIds);` | Batch-load quiz questions for all subtopics. |

`@EntityGraph` on a single collection path is used elsewhere in the project
(`JpaStudySessionItemRepository.findBySessionId`), so this matches existing conventions.

### Rejected alternatives

| Alternative | Why rejected |
|-------------|--------------|
| 3-level `JOIN FETCH` (`t.subtopics` + `s.snacks` + `s.quizQuestions`) | `MultipleBagFetchException` (two bags) / cartesian row explosion. |
| Single `@EntityGraph(attributePaths = {"subtopics", "subtopics.snacks", "subtopics.quizQuestions"})` | Same multiple-bag problem. |
| Lazy-load snacks/questions per subtopic inside the transaction | Works but is N+1; rejected on the explicit requirement to avoid N+1. |
| Rely on OSIV (`spring.jpa.open-in-view` default `true`) to lazily map in the controller | Fragile and environment-dependent; would hide a real initialization bug. Rejected. |
| Mutate entities (`subtopic.setSnacks(...)`) and return `List<Theme>` | Works under read-only (no flush) but is a code smell and unsafe if `readOnly` is ever removed. Rejected in favour of a service read model. |
| Change bags to `Set` to allow multi-fetch | Domain models must not change (FR-022) and would still risk cartesian products. Rejected. |

---

## 5. Frontend Model — `frontend/src/app/models/history.model.ts` (NEW)

Mirrors the endpoint payload; consumed by `ThemeService.listHistory()` and `HistoryComponent`
(FR-009). Reuses `QuestionType` from `models/quiz.model.ts`.

| Interface | Fields | Spec link |
|-----------|--------|-----------|
| `ThemeHistory` | `themeId: string`, `prompt: string`, `createdAt: string` (ISO-8601), `subtopics: HistorySubtopic[]` | FR-009/FR-015 |
| `HistorySubtopic` | `id: string`, `name: string`, `snacks: HistorySnack[]`, `quizQuestions: HistoryQuestion[]` | FR-016 |
| `HistorySnack` | `id: string`, `concept: string`, `metaphor: string`, `whyItMatters: string`, `related: string[]` | FR-003/FR-016 |
| `HistoryQuestion` | `id: string`, `type: QuestionType`, `prompt: string`, `explanation: string` | FR-004/FR-016 |

### UI state model — `HistoryComponent`

| Member | Kind | Type | Role |
|--------|------|------|------|
| `loading` | signal | `boolean` | `true` while the request is in flight, cleared in `next`/`error` (FR-012). |
| `errorMessage` | signal | `string \| null` | Set from `ErrorService.getMessage(err)`; when non-null the list is not rendered (FR-013). |
| `themes` | signal | `ThemeHistory[]` | The loaded history; empty array drives the empty state (FR-014). |
| `expanded` | signal | `Set<string>` | Client-side expansion of a theme's subtopics (read-only UI state). |

Derived display rules:

1. `loading()` → show loading, show nothing else (FR-012).
2. `errorMessage()` → show error banner, hide the list (FR-013).
3. `!loading() && !errorMessage() && themes().length === 0` → show empty-state message, no entries
   (FR-014).
4. Otherwise render each theme's `prompt` + formatted `createdAt`; expanded themes render subtopics
   with snacks/questions, with a "nothing generated yet" note when a subtopic's arrays are empty
   (FR-015/FR-016, US2 AS5).

`createdAt` is stored as the raw ISO-8601 string and formatted for display with
`Intl.DateTimeFormat`/`Date` in the browser locale (Assumptions/Open Question 2).

---

## 6. Persistence / Schema Change Statement

**No persistence or schema change is required or made by this feature.**

- No entity, field, column, index, constraint, enum, converter, or DTO persistence change.
- No migration; `ddl-auto=update` produces no delta.
- The endpoint performs zero writes (`@Transactional(readOnly = true)`); the frontend performs no
  mutation and issues only the single `GET /api/themes` read.
