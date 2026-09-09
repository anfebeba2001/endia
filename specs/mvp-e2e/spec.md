# Feature Specification: Endia MVP — E2E Learning Loop

**Feature Branch**: `feature/endia-mvp`

**Created**: 2026-09-09

**Status**: Draft

**Input**: User description: "Validate the core learning loop end-to-end: user enters a theme → gets subtopic suggestions → reads knowledge snacks → answers a quiz with plausible distractors → sees results + XP stub. No auth, no gamification beyond XP stub."

## User Scenarios & Testing

### User Story 1 — Theme-to-Subtopics Discovery (Priority: P1)

A user types a free-text theme (e.g. "Photosynthesis", "The 90s", "Rust borrow checker") and the system returns a list of 4–6 curated subtopic suggestions. The user sees the suggestions immediately and can pick the ones they want to study.

**Why this priority**: This is the entry point of the entire learning loop. Without subtopic discovery, nothing else can happen. P1 because it is the first interaction that delivers value — the user sees the system understands their request.

**Independent Test**: Can be fully tested by calling `POST /api/themes/suggest` with a valid theme string and verifying the response contains 4–6 non-empty subtopic strings. Delivers the value of "system understands what I want to learn."

**Acceptance Scenarios**:

1. **Given** the user is on the home screen, **When** they type "Photosynthesis" and submit, **Then** the system returns a list of 4–6 subtopic suggestions related to photosynthesis within 10 seconds.
2. **Given** the user is on the home screen, **When** they type a theme with fewer than 2 characters, **Then** the system returns a validation error indicating the theme is too short.
3. **Given** the user is on the home screen, **When** they type "The 90s" and submit, **Then** the system returns a list of subtopic suggestions about the 1990s decade.
4. **Given** the LLM provider is temporarily unavailable, **When** the user submits a theme, **Then** the system returns a user-friendly error message indicating the service is temporarily unavailable.

---

### User Story 2 — Snack Card Generation (Priority: P1)

After selecting subtopics, the user triggers snack generation. For each selected subtopic, the system produces a knowledge snack card containing: a one-paragraph concept definition, a real-life metaphor or analogy, a "why it matters" section with stakes or common pitfalls, and 2–3 related concept links. The user reads these before taking the quiz.

**Why this priority**: Snack cards are the core educational content of the product. This is the "learning" part of the learning loop. Without quality snacks, the quiz has no foundation. P1 because it is the primary value delivery — the user actually learns something.

**Independent Test**: Can be fully tested by calling `POST /api/snacks/generate` with a theme and a list of subtopics, and verifying each snack contains all four fields (concept, metaphor, whyItMatters, related) with non-empty content. Delivers the value of "I learned something in bite-size form."

**Acceptance Scenarios**:

1. **Given** the user has selected subtopics ["Light reactions", "Calvin cycle"], **When** they trigger snack generation, **Then** the system returns one snack card per subtopic, each containing concept, metaphor, whyItMatters, and related fields.
2. **Given** a snack is generated for "Light reactions", **When** the user reads it, **Then** the concept field is a clear paragraph definition (not empty, not a single sentence), and the metaphor is a relatable real-life analogy (not abstract jargon).
3. **Given** the user selected 3 subtopics, **When** snack generation completes, **Then** exactly 3 snack cards are returned and all are persisted in the database.
4. **Given** the LLM returns a snack with an empty metaphor field, **When** the system processes the response, **Then** it either re-rolls that snack or returns a validation error rather than serving empty content.

---

### User Story 3 — Quiz Generation with Distractor Quality (Priority: P1)

After reading snacks, the user starts a quiz for a specific subtopic. The system generates a multiple-choice quiz of 4–5 questions. Each question has exactly 4 options: 1 correct answer and 3 plausible distractors. Distractors are semantically close to the correct answer — a student who half-remembers the concept should genuinely hesitate. A quality gate validates every question before serving.

**Why this priority**: The quiz is the assessment and engagement hook of the learning loop. Plausible distractors are the differentiator — trivial wrong answers destroy educational value. P1 because without quality quizzes, the learning loop is broken.

**Independent Test**: Can be fully tested by calling `POST /api/quiz/generate` and verifying each question has exactly 4 options, the correct index points to a valid option, and no two options are identical. Delivers the value of "I can test what I learned with meaningful questions."

**Acceptance Scenarios**:

1. **Given** the user is viewing snacks for "Light reactions", **When** they start the quiz, **Then** the system returns 4–5 questions, each with exactly 4 options and a correctIndex.
2. **Given** a quiz question is generated, **When** the quality gate runs, **Then** it rejects questions with duplicate options, empty options, fewer than 4 options, or options shorter than 5 characters.
3. **Given** a question fails the quality gate, **When** the re-roll is triggered, **Then** the system generates a replacement question (max 1 retry per question) and serves the best result.
4. **Given** a quiz question is generated, **When** the correct answer is placed among options, **Then** the correct answer is shuffled to a random position (not always first or last).
5. **Given** the user requests a quiz for "Light reactions", **When** the quiz is served, **Then** all questions are specifically about Light reactions, not generic photosynthesis content.

---

### User Story 4 — Quiz Answering and Results with XP Stub (Priority: P1)

The user answers each quiz question by selecting an option. After each answer, they see whether they were correct, the correct answer, an explanation, and XP earned. After completing the quiz, they see a summary: total score, XP earned (including perfect-quiz bonus), and a breakdown per question.

**Why this priority**: Results and feedback close the learning loop. The user needs to know what they got right and wrong to actually learn. XP stub demonstrates the gamification foundation without building the full system. P1 because this completes the E2E flow.

**Independent Test**: Can be fully tested by submitting answers via `POST /api/quiz/answer` and verifying the response includes correct flag, correctIndex, explanation, and xpEarned. Delivers the value of "I know how I did and I see my XP."

**Acceptance Scenarios**:

1. **Given** the user is taking a quiz, **When** they select option index 2 for a question, **Then** the system responds with whether it was correct, the correctIndex, an explanation, and xpEarned (+10 if correct, +0 if wrong).
2. **Given** the user answers all 4 questions correctly, **When** the quiz completes, **Then** they receive 40 base XP + 50 bonus XP for a perfect round = 90 total XP.
3. **Given** the user answers 3 of 4 questions correctly, **When** the quiz completes, **Then** they receive 30 base XP with no perfect bonus, and see a summary showing 3/4 correct.
4. **Given** the user submits an answer for a question that doesn't exist or was already answered, **When** the system processes the request, **Then** it returns an appropriate error (not a crash).
5. **Given** the quiz is complete, **When** the user views the summary, **Then** they see each question's prompt, their chosen option, whether it was correct, and the explanation.

---

### User Story 5 — Distractor Quality Gate (Priority: P2)

The system validates every generated quiz question through an automated quality gate before serving it. The gate checks: exactly 4 options per question, no duplicate options, no empty or trivially short options (< 5 chars), and basic semantic plausibility (options are roughly similar length, contain relevant keywords). Failed questions are re-rolled once.

**Why this priority**: Distractor quality is the product differentiator but is an internal validation mechanism, not a user-facing feature. P2 because the user story 3 already describes the user experience; this story focuses on the gate's correctness and reliability as a standalone concern.

**Independent Test**: Can be tested by feeding the quality gate intentionally bad questions (duplicates, empty options, too-short options) and verifying they are rejected, and valid questions pass. Delivers the value of "quiz quality is consistently high."

**Acceptance Scenarios**:

1. **Given** a generated question has two identical options, **When** the quality gate runs, **Then** the question is flagged as failed and a re-roll is triggered.
2. **Given** a generated question has an option with fewer than 5 characters, **When** the quality gate runs, **Then** the question is flagged as failed.
3. **Given** a generated question has fewer than 4 options, **When** the quality gate runs, **Then** the question is flagged as failed immediately (no partial fix).
4. **Given** a question fails the quality gate on the first attempt, **When** the re-roll is attempted, **Then** the system generates one replacement question and serves it regardless of quality (max 1 retry).
5. **Given** a question passes all quality checks, **When** it is served to the user, **Then** it has exactly 4 unique, non-empty, plausible options with the correct answer at a random position.

---

### User Story 6 — XP Score Stub (Priority: P3)

The system calculates and returns XP after each quiz answer: +10 XP per correct answer, +50 bonus XP for a perfect quiz (all correct). XP is returned in API responses only — no persistence to a user profile, no level calculation, no streak tracking.

**Why this priority**: XP stub is the minimal gamification proof-of-concept. It demonstrates the scoring foundation that future gamification features will build on. P3 because the learning loop works without XP — it's a nice-to-have signal, not essential functionality.

**Independent Test**: Can be tested by submitting correct/incorrect answers and verifying the XP math: 10 per correct, +50 bonus if all correct. Delivers the value of "XP system foundation works."

**Acceptance Scenarios**:

1. **Given** a user answers 1 of 4 questions correctly, **When** the quiz response is returned, **Then** xpEarned for that answer is 10, and the total quiz XP is 10 (no bonus).
2. **Given** a user answers all 4 questions correctly, **When** the last answer response is returned, **Then** the total XP for the quiz is 90 (4 × 10 + 50 bonus).
3. **Given** a user answers 0 of 4 questions correctly, **When** the quiz response is returned, **Then** xpEarned for each answer is 0, and total quiz XP is 0.
4. **Given** XP is calculated, **When** the response is sent, **Then** XP is included in the response payload but is NOT persisted to any user profile or database table (MVP scope).

---

### Edge Cases

- What happens when the user submits a theme that is gibberish or completely nonsensical (e.g., "asdfghjkl")?
  - System should still attempt LLM subtopic generation; if the LLM returns empty/low-quality results, return a message suggesting the user try a clearer theme.
- What happens when the LLM provider is down or times out mid-flow?
  - System returns a 503 Service Unavailable with a user-friendly message. No partial results are served.
- What happens when the user submits a theme that is extremely broad (e.g., "science")?
  - LLM generates subtopics; system serves them as-is. No content filtering in MVP.
- What happens when the user submits a theme that is extremely narrow (e.g., "the proton spin of hydrogen-1 in magnetic fields below 0.5 Tesla")?
  - LLM generates what it can; if fewer than 4 subtopics are returned, serve whatever was generated (no minimum enforced).
- What happens when the database is unavailable?
  - System returns 503. Theme/snack/quiz generation requires DB persistence; without it, the flow cannot proceed.
- What happens when the user tries to answer a quiz question that was already answered?
  - Return 409 Conflict with a message indicating the question was already attempted.
- What happens when the user submits an answer with an out-of-range chosenIndex?
  - Return 400 Bad Request with a message indicating the option index is invalid.
- What happens when the LLM generates a quiz where all 4 options are the same?
  - Quality gate catches duplicate options and triggers a re-roll.
- What happens when the quality gate re-roll also produces bad questions?
  - Serve the best available result after 1 retry. Log a warning. Do not block the user flow.

## Requirements

### Functional Requirements

- **FR-001**: System MUST accept a free-text theme string (2–500 characters) and return 4–6 subtopic suggestions via LLM.
- **FR-002**: System MUST persist the theme prompt, its subtopics, and the user's selection in the database.
- **FR-003**: System MUST generate a knowledge snack card for each selected subtopic, containing: concept (paragraph), metaphor (real-life analogy), whyItMatters (stakes/pitfalls), and related (2–3 concept links).
- **FR-004**: System MUST persist each snack card in the database linked to its subtopic.
- **FR-005**: System MUST generate a quiz of 4–5 multiple-choice questions per subtopic, each with exactly 4 options (1 correct + 3 distractors).
- **FR-006**: System MUST validate every generated question through a quality gate before serving: check for duplicate options, empty/trivial options (< 5 chars), correct option count, and basic plausibility.
- **FR-007**: System MUST re-roll failed questions exactly once (max 1 retry per question) and serve the best result.
- **FR-008**: System MUST shuffle the correct answer to a random position among the 4 options for each question.
- **FR-009**: System MUST accept user answers (questionId + chosenIndex) and return: correct flag, correctIndex, explanation, and xpEarned.
- **FR-010**: System MUST calculate XP: +10 per correct answer, +50 bonus for a perfect quiz (all questions correct).
- **FR-011**: System MUST persist answer attempts in the database linked to the question.
- **FR-012**: System MUST return a quiz summary after all questions are answered: total score, total XP, and per-question breakdown.
- **FR-013**: System MUST expose all functionality through a REST API with JSON payloads.
- **FR-014**: System MUST persist all entities (Theme, Subtopic, Snack, QuizQuestion, AnswerAttempt) in MySQL 8.x using Spring Data JPA.
- **FR-015**: System MUST provide an Angular 19+ frontend with standalone components and signals that implements the full E2E user flow.
- **FR-016**: System MUST validate theme input: minimum 2 characters, maximum 500 characters.
- **FR-017**: System MUST return appropriate HTTP status codes: 200 for success, 400 for validation errors, 404 for not found, 409 for conflicts (already answered), 503 for service unavailability.
- **FR-018**: System MUST persist the correctIndex for each quiz question and use it as the source of truth for grading (not LLM-provided index).

### Key Entities

- **Theme**: Represents a user's study topic. Key attributes: prompt text, creation timestamp. Parent entity for subtopics.
- **Subtopic**: A curated slice of a theme suggested by the LLM. Key attributes: name, selection flag, foreign key to Theme. Parent entity for snacks and quiz questions.
- **Snack**: A knowledge card for a subtopic. Key attributes: concept definition, real-life metaphor, why-it-matters explanation, related concept links. Linked to one Subtopic.
- **QuizQuestion**: A multiple-choice question for a subtopic. Key attributes: prompt, options (list of 4 strings), correct index, explanation text. Linked to one Subtopic. Correct index is the source of truth for grading.
- **AnswerAttempt**: A record of a user answering a question. Key attributes: chosen index, correctness flag, XP earned, timestamp. Linked to one QuizQuestion.

## Success Criteria

### Measurable Outcomes

- **SC-001**: A user can complete the full E2E flow (theme → subtopics → snacks → quiz → results) in under 60 seconds of wall-clock time (excluding LLM latency).
- **SC-002**: Every quiz question served to the user passes the quality gate (4 unique, non-empty, plausible options).
- **SC-003**: 100% of generated snack cards contain all four fields (concept, metaphor, whyItMatters, related) with non-empty content.
- **SC-004**: The Angular frontend renders the complete flow without JavaScript errors on modern browsers (Chrome, Firefox, Safari latest 2 versions).
- **SC-005**: All API endpoints return responses within 500ms of backend processing time (excluding LLM call latency).
- **SC-006**: Answer attempts are persisted in MySQL and can be retrieved via database query after quiz completion.
- **SC-007**: XP calculations are mathematically correct: 10 per correct answer, +50 bonus for all correct, verified against 100% of quiz completions in integration tests.
- **SC-008**: The LLM abstraction layer (`LlmProvider` interface) has zero references to OpenAI-specific classes in the service or controller layers.

## Assumptions

- The user has a stable internet connection and a modern web browser.
- An OpenAI-compatible LLM API is available and configured via `LLM_API_KEY` and `LLM_BASE_URL` environment variables.
- MySQL 8.x is available and accessible from the Spring Boot application.
- The LLM generates content in the same language as the input theme (no explicit translation in MVP).
- No authentication is required — all requests are anonymous (stub user context only).
- The MVP runs as a monorepo with `backend/` (Spring Boot) and `frontend/` (Angular) directories.
- The frontend and backend run on separate ports during development (backend: 8080, frontend: 4200).
- The `LlmProvider` interface is designed to be swappable but only an OpenAI-compatible adapter is implemented in the MVP.
- Quality gate validation rules are basic (length, uniqueness, count checks) — full semantic analysis is deferred to post-MVP.
- The XP stub does not persist to any user profile; it is returned in API responses only for demonstration purposes.
