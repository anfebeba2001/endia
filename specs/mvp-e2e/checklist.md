# Quality Checklist: Endia MVP — E2E Learning Loop

**Purpose**: Verify completeness, quality, and correctness of the Endia MVP implementation against spec, plan, and constitution.

**Created**: 2026-09-09

**Feature**: [spec.md](./spec.md)

---

## Spec Completeness

- [ ] CHK001 All 6 user stories from spec.md have corresponding task groups in tasks.md
- [ ] CHK002 Every functional requirement FR-001 through FR-018 maps to at least one task in tasks.md
- [ ] CHK003 All 5 entities (Theme, Subtopic, Snack, QuizQuestion, AnswerAttempt) are defined in data-model.md and implemented in T006
- [ ] CHK004 All 4 API contracts (theme-suggest, snack-generate, quiz-generate, quiz-answer) are documented in `contracts/` and implemented as controller tasks
- [ ] CHK005 All edge cases from spec.md (gibberish theme, LLM down, already-answered question, out-of-range index, all-same options, quality gate re-roll failure) have corresponding implementation or error handling
- [ ] CHK006 All HTTP status codes (200, 400, 404, 409, 503) are documented in contracts and handled in GlobalExceptionHandler (T011)
- [ ] CHK007 Success criteria SC-001 through SC-008 from spec.md are verifiable against the task deliverables

## Architecture (Constitution Compliance)

- [ ] CHK008 Backend follows layered architecture: domain → application → infrastructure → interfaces (Principle II)
- [ ] CHK009 Domain entities (`domain/model/`) have zero imports from infrastructure or interfaces packages
- [ ] CHK010 Application layer services depend only on domain entities and port interfaces (no JPA, no RestClient imports)
- [ ] CHK011 `LlmProvider` interface exists in `application/port/` (T008) with no OpenAI-specific references in service or controller layers (Principle III)
- [ ] CHK012 `OpenAiLlmProvider` is the only class importing OpenAI/RestClient-specific classes (Principle III)
- [ ] CHK013 `QuizQualityGate` is a standalone application service, not embedded in `QuizService` (Principle IV, SRP)
- [ ] CHK014 Prompts are externalized as `.md` files in `resources/prompts/`, not hardcoded in Java strings (Principle III)
- [ ] CHK015 Two layers of DTOs exist: API DTOs (`interfaces/rest/dto/`) and Application DTOs (`application/dto/`) — per Architecture Decision #3
- [ ] CHK016 No User entity exists, no authentication, no gamification beyond XP stub (Principle V: MVP-First)
- [ ] CHK017 `LlmConfig.java` reads `LLM_API_KEY` and `LLM_BASE_URL` from environment variables only (no hardcoded keys)

## Implementation Completeness

- [ ] CHK018 All 52 tasks in tasks.md are implemented and marked complete
- [ ] CHK019 No orphaned code: every Java/TypeScript file maps to at least one task
- [ ] CHK020 All files in `plan.md` project structure exist on disk (verify `backend/src/`, `frontend/src/` match plan)
- [ ] CHK021 `application.yml` and `application-dev.yml` are configured with MySQL datasource, JPA settings, and server port
- [ ] CHK022 `.env.example` contains all required environment variables (`LLM_API_KEY`, `LLM_BASE_URL`, DB credentials)
- [ ] CHK023 `JpaJsonConverter` handles `List<String>` → MySQL JSON for `Snack.related` and `QuizQuestion.options` fields
- [ ] CHK024 UUID primary keys used on all entities with `@GeneratedValue(strategy = GenerationType.UUID)`
- [ ] CHK025 `AnswerAttempt` has unique constraint on `(quiz_question_id, chosen_index)` per data-model.md

## Quality Gate (US5 — Distractor Validation)

- [ ] CHK026 `QuizQualityGate.validate()` checks: exactly 4 options → reject if not
- [ ] CHK027 `QuizQualityGate.validate()` checks: no duplicate options (case-insensitive, trimmed) → reject if found
- [ ] CHK028 `QuizQualityGate.validate()` checks: no option shorter than 5 characters → reject if found
- [ ] CHK029 Re-roll mechanism: max 1 retry per failed question, serves best available after retry
- [ ] CHK030 Correct answer is shuffled to random position before persisting; `correctIndex` updated to match shuffled position
- [ ] CHK031 `correctIndex` is derived server-side after shuffle, NOT trusted from LLM output (FR-018)
- [ ] CHK032 Quiz generation API response does NOT include `correctIndex` or `explanation` (anti-cheat per contract)
- [ ] CHK033 Unit tests exist for quality gate with bad inputs (duplicates, empty, too short, too few options)
- [ ] CHK034 Re-roll integration test exists: mock LLM returns bad → then valid → verify retry works

## XP Calculation (US6)

- [ ] CHK035 XP math: correct answer = +10 XP, incorrect = +0 XP
- [ ] CHK036 XP math: perfect quiz (all correct) = base XP + 50 bonus (e.g., 4/4 = 90 XP)
- [ ] CHK037 XP is included in API response but NOT persisted to any user profile or database table
- [ ] CHK038 Unit tests verify XP calculation for 0/4, 1/4, 3/4, 4/4 correct scenarios

## Frontend (Angular)

- [ ] CHK039 Angular project uses standalone components (no NgModules)
- [ ] CHK040 Angular state management uses signals (`signal()`, `computed()`, `effect()`)
- [ ] CHK041 All 5 page components exist: HomeComponent, SubtopicsComponent, SnacksComponent, QuizComponent, ResultsComponent
- [ ] CHK042 All 3 services exist: ThemeService, SnackService, QuizService
- [ ] CHK043 API models match backend contracts (request/response shapes are consistent)
- [ ] CHK044 `proxy.conf.json` is configured to proxy `/api/*` to `http://localhost:8080`
- [ ] CHK045 Routing is configured for all pages: `/`, `/subtopics`, `/snacks`, `/quiz`, `/results`
- [ ] CHK046 Loading states (spinners/indicators) present on all API call triggers
- [ ] CHK047 Error states display user-friendly messages from API error responses
- [ ] CHK048 Form validation on HomeComponent: min 2 chars, max 500 chars enforced client-side
- [ ] CHK049 Buttons are disabled during loading states to prevent duplicate submissions
- [ ] CHK050 QuizComponent shows immediate per-question feedback (correct/incorrect + explanation + XP)

## Integration (E2E Flow)

- [ ] CHK051 Full flow works: enter theme → get subtopics → select subtopics → generate snacks → read snacks → start quiz → answer questions → see results + XP
- [ ] CHK052 Backend starts successfully on port 8080 with MySQL connection
- [ ] CHK053 Frontend starts successfully on port 4200 with proxy to backend
- [ ] CHK054 Theme suggestion returns 4–6 subtopics within 10 seconds (excluding LLM latency)
- [ ] CHK055 Snack generation returns all 4 fields (concept, metaphor, whyItMatters, related) non-empty per snack
- [ ] CHK056 Quiz generation returns questions with exactly 4 unique, non-empty, plausible options
- [ ] CHK057 Quiz answering returns correct flag, correctIndex, explanation, and xpEarned per answer
- [ ] CHK058 Quiz summary shows total score, total XP, and per-question breakdown
- [ ] CHK059 Duplicate answer to same question returns 409 Conflict
- [ ] CHK060 LLM provider unavailable returns 503 with user-friendly message (no partial results)
- [ ] CHK061 Integration tests pass with Testcontainers MySQL (ThemeFlowIntegrationTest, QuizFlowIntegrationTest)

## Testing Coverage

- [ ] CHK062 Unit tests exist for ThemeService (mock LlmProvider)
- [ ] CHK063 Unit tests exist for SnackService (mock LlmProvider)
- [ ] CHK064 Unit tests exist for QuizService (mock LlmProvider, test XP, shuffle, re-roll)
- [ ] CHK065 Unit tests exist for QuizQualityGate (bad inputs, valid inputs, edge cases)
- [ ] CHK066 Unit tests exist for OpenAiLlmProvider (MockWebServer)
- [ ] CHK067 Frontend component unit tests exist for each page component (mock services)
- [ ] CHK068 Frontend service unit tests exist (HttpClient mocked, response mapping verified)

---

## Notes

- Check items off as completed: `[x]`
- Add comments or findings inline
- Items are numbered sequentially for easy reference
- This checklist should be reviewed after each phase completion
- Gate review: all items in "Quality Gate" and "Integration" categories must pass before considering the MVP complete
