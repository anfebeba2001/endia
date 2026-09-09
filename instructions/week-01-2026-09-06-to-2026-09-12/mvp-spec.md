# Endia MVP — E2E Spec (Week 1)

> Scope: **Prompt → Subtopics → Snacks → Quiz → Results**
> No auth, no gamification beyond XP stub.

## 1. Goal

Validate the core learning loop end-to-end:
user enters a theme → gets subtopic suggestions → reads knowledge snacks →
answers a quiz with plausible distractors → sees results + XP stub.

## 2. User Flow

```
[1] POST /api/themes/suggest  { "prompt": "Photosynthesis" }
    → { "subtopics": ["Light reactions", "Calvin cycle", ...] }

[2] POST /api/snacks/generate  { "theme": "Photosynthesis", "subtopics": ["Light reactions"] }
    → { "snacks": [{ "concept": "...", "metaphor": "...", "whyItMatters": "...", "related": [...] }] }

[3] POST /api/quiz/generate  { "theme": "Photosynthesis", "subtopic": "Light reactions", "count": 4 }
    → { "questions": [{ "id": "...", "prompt": "...", "options": ["A","B","C","D"] }] }

[4] POST /api/quiz/answer  { "questionId": "...", "chosenIndex": 2 }
    → { "correct": false, "correctIndex": 0, "explanation": "...", "xpEarned": 0 }
```

## 3. Tech Stack

| Layer    | Choice | Notes |
|----------|--------|-------|
| Frontend | **Angular 19+** | Latest stable; standalone components, signals |
| Backend  | **Spring Boot 3.4.x**, Java 21 | REST API; latest stable line |
| DB       | **MySQL 8.x** | Primary database |
| ORM      | Spring Data JPA + Hibernate | Auto DDL for dev |
| LLM      | OpenAI-compatible via RestClient | Interface + provider adapter |
| Build    | Maven (backend) · Angular CLI (frontend) | Monorepo: `backend/` + `frontend/` |

## 4. Data Model (JPA Entities)

```
Theme
  id: UUID (PK)
  prompt: String
  createdAt: LocalDateTime

Subtopic
  id: UUID (PK)
  theme_id: FK → Theme
  name: String
  selected: boolean (default true)

Snack
  id: UUID (PK)
  subtopic_id: FK → Subtopic
  concept: String (TEXT)
  metaphor: String (TEXT)
  whyItMatters: String (TEXT)
  related: JSON (List<String>)

QuizQuestion
  id: UUID (PK)
  subtopic_id: FK → Subtopic
  prompt: String (TEXT)
  options: JSON (List<String>, 4 items)
  correctIndex: int
  explanation: String (TEXT)

AnswerAttempt
  id: UUID (PK)
  question_id: FK → QuizQuestion
  chosenIndex: int
  correct: boolean
  xpEarned: int
  createdAt: LocalDateTime
```

## 5. LLM Abstraction (Java Interface)

```java
public interface LlmProvider {
    List<String> suggestSubtopics(String theme, int count);
    SnackDto generateSnack(String theme, String subtopic);
    List<QuizQuestionDto> generateQuiz(String theme, String subtopic, int count);
}
```

OpenAiLlmProvider implements via RestClient calling OpenAI-compatible chat completions.
Configured via `LLM_API_KEY` and `LLM_BASE_URL` env vars.

## 6. Quality Gate (Distractors)

After generating quiz questions, a validation pass checks:
- 4 options per question
- No duplicate options
- No option is empty or < 5 chars
- Correct answer shuffled to random position
- All options are plausible (basic length/keyword check)

Failed questions are re-rolled once.

## 7. XP Stub

- Correct answer: +10 XP
- Perfect quiz (all correct): +50 bonus XP
- Returned in response only (no user persistence in MVP).

## 8. Out of Scope (this MVP)

- Authentication / user accounts
- Gamification UI (cards, pets, streaks, power-ups)
- Card collection
- Daily challenges
- PostgreSQL (MySQL from day one)
