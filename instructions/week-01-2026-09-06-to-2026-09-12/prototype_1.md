# Prototype 1 — Endia MVP Summary

> **Period:** Week 1 · 2026-09-06 → 2026-09-12
> **Status:** MVP complete — full learning loop validated end-to-end

---

## What We Built

A working web app that turns any topic into a bite-sized learning experience: user enters a theme → gets subtopic suggestions → reads knowledge snacks → answers a quiz → sees results with XP.

**Live at:** `http://localhost:4200` (Frontend) · `http://localhost:8080` (Backend)

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Angular 21 Frontend (localhost:4200)                    │
│  • Standalone components, signals-first                  │
│  • Lazy routes: / → /subtopics → /snacks → /quiz → /results │
│  • Proxy: /api/* → localhost:8080                        │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│  Spring Boot 3.4 Backend (localhost:8080)               │
│  • Java 21, layered architecture                        │
│  • domain → application → infrastructure → interfaces   │
│  • Spring Data JPA + MySQL 8.x                          │
│  • OpenAI-compatible LLM abstraction                    │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│  Gemini 3.5 Flash Lite (via OpenAI-compatible endpoint) │
│  • Configured via env vars (LLM_API_KEY, LLM_BASE_URL) │
│  • Swappable to any OpenAI-compatible provider          │
└─────────────────────────────────────────────────────────┘
```

---

## Backend Structure (47 Java files)

```
backend/src/main/java/com/endia/
├── domain/
│   ├── model/          Theme, Subtopic, Snack, QuizQuestion, AnswerAttempt
│   └── exception/      DomainException, ServiceUnavailableException, etc.
├── application/
│   ├── dto/            Request/Response DTOs for all endpoints
│   ├── port/           LlmProvider interface (abstraction)
│   └── service/        ThemeService, SnackService, QuizService, QuizQualityGate
├── infrastructure/
│   ├── llm/            OpenAiLlmProvider, LlmConfig, LlmPromptTemplates
│   ├── persistence/    JPA repositories for all entities
│   ├── config/         WebConfig (CORS)
│   └── exception/      GlobalExceptionHandler
└── interfaces/rest/
    ├── ThemeController     POST /api/themes/suggest
    ├── SnackController     POST /api/snacks/generate
    ├── QuizController      POST /api/quiz/generate, POST /api/quiz/answer, GET /api/quiz/summary/{id}
    └── dto/                API request/response wrappers
```

---

## Frontend Structure (17 TypeScript files)

```
frontend/src/app/
├── app.ts, app.html, app.scss     Root shell with router-outlet
├── app.routes.ts                   Lazy-loaded routes
├── app.config.ts                   Providers (router, http)
├── pages/
│   ├── home/          Theme input + suggest subtopics
│   ├── subtopics/     Subtopic picker (toggle selection)
│   ├── snacks/        Knowledge snack cards (concept, metaphor, why)
│   ├── quiz/          Multiple-choice quiz with feedback
│   └── results/       Score breakdown, XP, restart
├── services/
│   ├── theme.service.ts       API: /api/themes/suggest
│   ├── snack.service.ts       API: /api/snacks/generate
│   ├── quiz.service.ts        API: /api/quiz/*
│   ├── app-state.service.ts   Shared state (signals)
│   └── error.service.ts       Error message formatting
└── models/            TypeScript interfaces for API contracts
```

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/themes/suggest` | Theme prompt → subtopic suggestions |
| POST | `/api/snacks/generate` | Selected subtopics → knowledge snack cards |
| POST | `/api/quiz/generate` | Subtopic → quiz questions (4 options each) |
| POST | `/api/quiz/answer` | Submit answer → correct/wrong + XP |
| GET | `/api/quiz/summary/{subtopicId}` | Full quiz results breakdown |

---

## E2E Test Suite (17 tests, 11 spec files)

| Priority | # | Scenario | Status |
|----------|---|----------|--------|
| P0 | 1 | Full happy path (Home→Subtopics→Snacks→Quiz→Results) | ✅ |
| P0 | 2 | Input validation (short + empty) | ✅ |
| P0 | 3 | Subtopic deselection (count + API payload) | ✅ |
| P0 | 4 | Quiz correct answer feedback (+10 XP) | ✅ |
| P0 | 5 | Quiz wrong answer feedback (Not quite + highlight) | ✅ |
| P0 | 6 | Results accuracy (score, breakdown) | ✅ |
| P0 | 7 | Restart clears state | ✅ |
| P1 | 8 | Back navigation (2 tests) | ✅ |
| P1 | 9 | Loading states (home + snacks) | ✅ |
| P1 | 10 | Error recovery (500 → retry) | ✅ |
| P1 | 11 | Direct URL access (3 tests: quiz, results, subtopics) | ✅ |
| P2 | 12 | Perfect score (trophy + bonus XP) | ✅ |

**Mock strategy:** All API calls intercepted — no LLM needed for tests.

**Handover doc:** A comprehensive `e2e-handover.md` (650 lines) was produced alongside the test suite — covers architecture overview, startup commands, full happy-path walkthrough with exact selectors, mock strategy with intercept templates, API contract quick reference, and a common failure modes table. Designed so a Playwright agent can regenerate the entire E2E suite from it alone.

---

## Bugs Found & Fixed

### 1. `(ngSubmit)` Page Refresh (Critical)
- **Symptom:** Clicking "Suggest subtopics" refreshed the page instead of calling the API
- **Cause:** `HomeComponent` imported `ReactiveFormsModule` but used `(ngSubmit)`, which requires `FormsModule` (provides `NgForm` directive). Without it, Angular ignored the binding and the form did a native GET submit.
- **Fix:** Added `FormsModule` to `HomeComponent.imports`

### 2. MySQL Credential Mismatch
- **Symptom:** Backend failed with `Access denied for user 'root'@'localhost'`
- **Cause:** Host had a native MySQL using `auth_socket` (no TCP password). Docker container couldn't bind port 3306.
- **Fix:** Stopped native MySQL, configured Docker MySQL with `MYSQL_ROOT_PASSWORD=root`

### 3. Gemini Model Deprecation
- **Symptom:** `gemini-2.0-flash` returned 404 "no longer available"
- **Cause:** Google deprecated older models
- **Fix:** Discovered available models via API, settled on `gemini-3.5-flash-lite`

---

## Infrastructure

### `scripts/init.sh`
One-command launcher for the full stack:
```bash
./scripts/init.sh           # start everything
./scripts/init.sh stop      # stop everything
./scripts/init.sh restart   # stop + start
./scripts/init.sh status    # show component status
```
- Handles MySQL (Docker), backend (Maven), frontend (Angular)
- Auto-detects and stops native MySQL if conflicting
- Always recreates Docker container for clean state
- Logs to `.logs/`

### `.env`
```env
LLM_API_KEY=<gemini-key>
LLM_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai
LLM_MODEL=gemini-3.5-flash-lite
DB_HOST=localhost
DB_PORT=3306
DB_NAME=endia
DB_USERNAME=root
DB_PASSWORD=root
```

---

## Decisions Made

| Decision | Choice | Rationale |
|----------|--------|-----------|
| LLM Provider | Gemini 3.5 Flash Lite | Fast, cheap, OpenAI-compatible endpoint |
| MySQL setup | Docker container | Isolated, reproducible, no host conflicts |
| Frontend state | Angular signals (no localStorage) | MVP scope, stateless per session |
| Auth | None (MVP) | YAGNI — core loop first |
| Gamification | XP stub only (+10 correct, +50 perfect) | Enough to validate, real gamification next |
| Quality gate | Quiz distractor validation + re-roll | Ensures plausible options |

---

## What's Next (Week 2 candidates)

1. **Persistence** — Store themes, quizzes, and results in MySQL (currently ephemeral)
2. **User identity** — Stub user → real auth (OAuth or magic link)
3. **Gamification UI** — XP display, levels, streaks, card collection
4. **Quiz quality** — Better distractor strategy, difficulty adaptation
5. **Error handling** — Retry logic, timeout handling, offline fallback
6. **Mobile responsive** — Current UI is desktop-first

---

## File Count

| Layer | Files | LOC (approx) |
|-------|-------|---------------|
| Backend (Java) | 47 | ~1,800 |
| Frontend (TS/HTML/SCSS) | 17 | ~1,750 |
| E2E Tests | 11 | ~560 |
| Config/Scripts | 5 | ~300 |
| **Total** | **80** | **~4,400** |

---

*Prototype 1 complete. Core learning loop validated. Ready for iteration.*
