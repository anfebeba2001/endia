# Endia — Project Manifest

> **Week:** 1 · 2026-09-06 → 2026-09-12
> **Status:** Living document — record decisions, revise freely.

## 1. Vision

Endia is a web app that turns studying into a game. The user types a
theme (any topic), and the app turns it into a short, playful learning
loop: bite-size **snacks** of knowledge and a quick quiz, wrapped in
RPG-style progression.

The guiding rule: **concepts are explained with real-life examples and
metaphors**, and quiz options are **plausible, non-obvious distractors**.

## 2. Core User Flow

1. **Prompt** — user enters a theme (e.g. "Photosynthesis", "The 90s",
   "Rust borrow checker").
2. **Subtopic suggestions** — an LLM proposes 4–6 subtopics; user picks
   the ones they want (or takes all).
3. **Snacks** — for each subtopic, the app generates an `Endia Snack`:
   a short concept card built around a real-life metaphor/example.
4. **Quick quiz** — a short multiple-choice round (4–5 questions). Each
   question has a correct answer + 3 **plausible distractors** (same
   category, semantically close, no trivial giveaways).

### Snack card shape
- **Concept** — one-punch definition (≤ 1 paragraph)
- **Real-life metaphor** — relatable analogy ("RAM is the kitchen counter")
- **Why it matters** — stakes / common pitfall
- **Related concepts** — 2–3 links for branching into more snacks

### Distractor strategy (non-obvious options)
- Wrong options are same-category, close-but-wrong ("all of the above is
  true EXCEPT…" style decayed; instead: near-correct definitions, common
  misconceptions, plausible numbers/deadlines).
- No option should scream "wrong"; a villain option is one step off, not
  two.
- Rule: distractors must be **semantically plausible** — a student who
  half-remembers the concept should genuinely hesitate.
- LLM prompt ships with these rules baked in. A **quality gate** grades
  each generated question (re-roll if a distractor is too obvious, too
  absurd, or duplicates the correct answer).

## 3. Tech Stack

| Layer     | Choice | Notes |
|-----------|--------|-------|
| Frontend  | **Angular 19+** | Latest stable; standalone components, signals-first. |
| Backend   | **Spring Boot 3.4.x**, Java 21 | REST API; latest stable line. |
| Database  | **MySQL 8.x** | Primary database from day one. |
| LLM       | **OpenAI-compatible abstraction** | Interface + provider adapters; swappable via config/env (OpenAI default, others later). |

### LLM abstraction
- `LlmProvider` interface with `generateThemes`, `generateSnacks`,
  `generateQuiz`, `generateDistractors`.

Enough for a clean swap: no provider logic leaking into services.
Provider + model configured through environment variables.

## 4. Gamification Systems (chosen)

1. **XP + Levels + Streaks**
   - XP for finishing snacks, answering quizzes, perfect rounds.
   - Levels unlock new cards / cosmetic rewards.
   - Daily streak with a **streak freeze** power-up.
2. **RPG quest path**
   - Theme = **zone** · Subtopic = **mission** · Quiz = **boss battle**.
   - Boss battle = 5 questions, 3 hearts; win = big XP + loot.
3. **Card collection**
   - Mastering a concept mints a collectible **Concept Card**.
   - Perfect quiz = chance at a **rare/golden card**.
4. **Power-ups & gadgets**
   - 50/50, hint reveal, shield (extra heart), double-XP token.
   - Earned via streaks / level-ups (no pay-to-win for v1).
5. **Virtual pet / garden**
   - A creature that levels up with study; neglect (no activity) makes it
     sad. Low-stakes emotional hook, not punishment.
6. **Daily challenges + badges**
   - Rotating daily quiz (today's theme), calendar milestones, badges for
     streaks, first-perfect, 10 themes mastered, etc.

> Note: PvP duels and skill-tree were options considered and **not
> selected** for v1. Revisit later if wanted.

## 5. Initial Domain Sketch

- **User** — auth identity, XP, level, streak, coins.
- **Zone** — a theme; contains missions; boss-quiz config.
- **Mission** — a subtopic; contains snacks + quiz.
- **Snack** — concept card (fielded per §2).
- **QuizQuestion** — prompt + correct answer + 3 distractors (quality-gated).
- **AnswerAttempt** — per-user answer log (feeds XP and quality stats).
- **ConceptCard** — minted on mastery; rarity.
- **Pet** — virtual companion state (level, mood).
- **Strength** — daily challenge marker.
- **Badge** — achievement definitions + user grants.

## 6. API Outline (initial)

```
POST /api/llm/themes/suggest            # prompt -> subtopic suggestions
POST /api/llm/snacks/generate           # subtopic -> snack card(s)
POST /api/llm/quiz/generate             # subtopic -> quiz (with distractors)
GET/POST /api/zones                     # quest zones
GET/POST /api/zones/{id}/missions       # missions
POST /api/missions/{id}/attempts        # answer attempt; returns XP/grading
GET  /api/user/progress                 # XP, level, streak, badges
GET/POST /api/user/pet                  # pet state
GET  /api/cards                        # owned cards
GET  /api/daily-challenge               # today's challenge
```

## 7. Week 1 Roadmap (06–12 Sep)

1. Scaffold monorepo: `frontend/` (Angular 19+) + `backend/` (Spring Boot 3.4, Java 21).
2. `LlmProvider` abstraction + one OpenAI-compatible adapter; config via env.
3. PoC flow: prompt theme → subtopics → one snack → 4-question quiz
   (raw LLM, no auth yet).
4. Quiz quality gate (basic distractor rules → regrade/re-roll).
5. Minimal XP + streak wiring behind a stub "current user".
6. Decide next week's scope (persistence, real auth, first gamification UI).

## 8. Open Questions / Decision Log

- **Auth**: phase 1 anonymous/stub user vs real accounts? (→ likely stub first)
- **Persistence timeline**: MySQL from day one (no H2/SQLite phase).
- **RAG vs direct generation**: start direct; add retrieval only if themes need grounding.
- **LLM provider default**: which OpenAI-compatible model as default? (confirm with budget)
- **Mono/multi repo**: monorepo assumed; confirm.
- **UI style**: playful/illustrated vs minimal dark-app dev vibe (affects component work).
- **Naming**: folder convention `week-NN-YYYY-MM-DD-to-YYYY-MM-DD` for
  future weekly instruction docs.

---
*Log major decisions here as they land.*