# E2E Playwright Handover — Endia MVP

> **Purpose**: Guide an automated Playwright E2E test suite through the full
> learning loop. Covers happy path, error scenarios, and mock strategies.
>
> **Last updated**: 2026-09-09

---

## 1. Architecture at a Glance

```
Browser (localhost:4200)
  └─ Angular 21 standalone app (lazy routes, signals)
       └─ proxy.conf.json → /api/* → localhost:8080
            └─ Spring Boot 3.4 REST API
                 ├─ POST /api/themes/suggest
                 ├─ POST /api/snacks/generate
                 ├─ POST /api/quiz/generate
                 ├─ POST /api/quiz/answer
                 └─ GET  /api/quiz/summary/{subtopicId}
```

- **Frontend**: `http://localhost:4200`
- **Backend**: `http://localhost:8080`
- **Proxy**: Angular dev server proxies `/api/*` to backend
- **State**: In-memory via `AppStateService` (Angular signals, no localStorage)

---

## 2. How to Start the App

### Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Java | 21+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Node.js | 20+ | `node -v` |
| Angular CLI | 21+ | `npx ng version` |
| MySQL | 8.x | `mysql --version` |
| LLM API Key | OpenAI-compatible | `echo $LLM_API_KEY` |

### Startup Commands

```bash
# Terminal 1 — MySQL (if not running)
docker run -d --name endia-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=endia \
  -p 3306:3306 mysql:8.0

# Terminal 2 — Backend
cd backend
export LLM_API_KEY="sk-..."          # Required
export LLM_BASE_URL="https://api.openai.com/v1"  # Or your proxy
mvn spring-boot:run
# Ready when: "Started EndiaApplication" appears

# Terminal 3 — Frontend
cd frontend
npx ng serve --proxy-config proxy.conf.json
# Ready when: "Local: http://localhost:4200/" appears
```

### Health Check

```bash
# Backend alive?
curl -s http://localhost:8080/api/themes/suggest \
  -H "Content-Type: application/json" \
  -d '{"prompt":"test"}' | head -c 200

# Frontend alive?
curl -s http://localhost:4200 | grep -o '<title>.*</title>'
# Expected: <title>Endia</title>
```

---

## 3. Full E2E Flow — Happy Path

### Step 1: Home Page (`/`)

**URL**: `http://localhost:4200/`

**What to verify**:
- Page title contains "Endia"
- `<h1>` text: "Learn anything, bite by bite."
- Input field with placeholder: "e.g. Photosynthesis, The 90s, Rust borrow checker"
- Button text: "Suggest subtopics"
- Button is disabled while loading, text changes to "Thinking…"

**Selectors**:
```typescript
const TITLE = 'h1.hero__title';
const INPUT  = 'input.search__input';     // aria-label: "Theme to study"
const BUTTON = 'button.search__button';   // text: "Suggest subtopics"
const ERROR  = '.error-banner';           // role="alert"
const LOADING = '.loading';              // appears during API call
```

**Action**: Type "Photosynthesis" into the input, click the button.

**API intercepted**:
```
POST /api/themes/suggest
Body: { "prompt": "Photosynthesis" }
Response: {
  "themeId": "uuid-here",
  "subtopics": [
    { "id": "uuid-1", "name": "Light reactions" },
    { "id": "uuid-2", "name": "Calvin cycle" },
    { "id": "uuid-3", "name": "Chloroplast structure" },
    { "id": "uuid-4", "name": "Carbon fixation" }
  ]
}
```

**Expected result**: Navigates to `/subtopics`.

---

### Step 2: Subtopic Picker (`/subtopics`)

**URL**: `http://localhost:4200/subtopics`

**What to verify**:
- `<h1>` text: "Pick what to study"
- Subtitle: "We broke your theme into these focus areas."
- All subtopic cards are pre-selected (checkmark visible)
- Button text: "Generate snacks (N)" where N = number of selected
- "← Back" link navigates to `/`

**Selectors**:
```typescript
const TITLE     = 'h1.page__title';
const CARDS     = '.subtopic-card';           // multiple
const CHECK     = '.subtopic-card__check';    // visible when selected
const CARD_NAME = '.subtopic-card__name';
const BUTTON    = 'button.primary-btn';       // text includes count
const BACK      = 'button.link-btn';          // "← Back"
```

**Action**: Toggle one subtopic OFF (click it), verify count drops.
Then toggle it back ON. Click "Generate snacks (N)".

**API intercepted**: None (state-only navigation).

**Expected result**: Navigates to `/snacks`.

---

### Step 3: Snacks Page (`/snacks`)

**URL**: `http://localhost:4200/snacks`

**What to verify**:
- Loading state shows: "Warming up the knowledge oven…"
- After load: snack cards appear, one per selected subtopic
- Each card shows: subtopic name chip, Concept, Metaphor, Why it matters, Related tags
- Each card has a "Start Quiz" button
- "← Back to subtopics" link works

**Selectors**:
```typescript
const TITLE      = 'h1.page__title';           // "Your Snacks"
const LOADING    = '.loading';                  // "Warming up…"
const CARD       = '.snack-card';
const CHIP       = '.snack-card__chip';         // subtopic name
const CONCEPT    = '.snack-card__concept';
const METAPHOR   = '.snack-card__metaphor';
const WHY        = '.snack-card__why';
const RELATED    = '.snack-card__related .tag';
const QUIZ_BTN   = '.quiz-btn';                // "Start Quiz"
const BACK       = 'button.link-btn';
```

**API intercepted**:
```
POST /api/snacks/generate
Body: { "themeId": "uuid", "subtopicIds": ["uuid-1", "uuid-2", ...] }
Response: {
  "snacks": [
    {
      "id": "uuid",
      "subtopicId": "uuid-1",
      "subtopicName": "Light reactions",
      "concept": "Photosynthesis light reactions convert sunlight into chemical energy...",
      "metaphor": "Think of chlorophyll as solar panels...",
      "whyItMatters": "Without this step, plants can't make food...",
      "related": ["ATP synthase", "Electron transport chain"]
    }
  ]
}
```

**Action**: Click "Start Quiz" on the first snack card.

**Expected result**: Navigates to `/quiz`.

---

### Step 4: Quiz Page (`/quiz`)

**URL**: `http://localhost:4200/quiz`

**What to verify**:
- Loading state: "Crafting challenging questions…"
- After load: question prompt displayed
- 4 option buttons labeled A, B, C, D
- Progress bar visible
- Subtopic chip visible at top
- Options are NOT disabled until one is clicked
- Clicking an option submits the answer immediately (no separate submit button)

**Selectors**:
```typescript
const LOADING    = '.loading';                   // "Crafting…"
const CHIP       = '.quiz-header .chip';         // subtopic name
const PROGRESS   = '.progress-bar__fill';
const QUESTION   = 'h2.question';                // question prompt
const OPTIONS    = '.option';                    // 4 buttons
const OPTION_TXT = '.option__text';
const LETTER     = '.option__letter';            // A, B, C, D
const FEEDBACK   = '.feedback';                  // appears after answer
const FEEDBACK_BAR = '.feedback__bar strong';    // "Correct! +10 XP" or "Not quite"
const EXPLANATION = '.feedback__explanation';
const NEXT_BTN   = '.feedback .primary-btn';     // "Next question" or "See results"
```

**API intercepted** (on page load):
```
POST /api/quiz/generate
Body: { "themeId": "uuid", "subtopicId": "uuid-1", "count": 4 }
Response: {
  "subtopicId": "uuid-1",
  "questions": [
    {
      "id": "q-uuid-1",
      "prompt": "What is the primary pigment in light reactions?",
      "options": ["Chlorophyll a", "Carotenoids", "Phycocyanin", "Xanthophyll"]
    }
    // ... 3 more questions (NO correctIndex or explanation in response)
  ]
}
```

**API intercepted** (on option click):
```
POST /api/quiz/answer
Body: { "questionId": "q-uuid-1", "chosenIndex": 0 }
Response: {
  "questionId": "q-uuid-1",
  "correct": true,
  "correctIndex": 0,
  "explanation": "Chlorophyll a is the primary pigment...",
  "xpEarned": 10
}
```

**Quiz flow**:
1. Question 1 → click option → feedback appears → click "Next question"
2. Question 2 → click option → feedback → "Next question"
3. Question 3 → click option → feedback → "Next question"
4. Question 4 → click option → feedback → **"See results"** (not "Next question")

**Expected result**: After last question, navigates to `/results`.

---

### Step 5: Results Page (`/results`)

**URL**: `http://localhost:4200/results`

**What to verify**:
- Loading state: "Crunching your results…"
- After load:
  - If all correct: "Perfect score!" + trophy icon + "+50 bonus XP"
  - Otherwise: "Quiz complete"
- Score grid shows: Total XP, Correct (X/Y), Accuracy (N%)
- Breakdown section: one card per question with:
  - ✓ or ✗ indicator
  - "Question N" label
  - Question prompt
  - All 4 options with "Correct" or "Yours" tags
  - Explanation text
- "Start a new theme" button → navigates to `/`

**Selectors**:
```typescript
const LOADING      = '.loading';
const TITLE        = 'h1.results-header__title';
const PERFECT      = '.perfect-badge';           // "+50 bonus XP" (only if perfect)
const TOTAL_XP     = '.score-card--accent .score-card__value';
const CORRECT      = '.score-card:nth-child(2) .score-card__value';  // "X/Y"
const ACCURACY     = '.score-card:nth-child(3) .score-card__value';  // "N%"
const BREAKDOWN    = '.breakdown-card';
const INDICATOR    = '.indicator';               // ✓ or ✗
const PROMPT       = '.breakdown-card__prompt';
const OPTION_TAG   = '.mini-option__tag';        // "Correct" or "Yours"
const EXPLANATION  = '.breakdown-card__explanation';
const RESTART_BTN  = 'button.primary-btn';       // "Start a new theme"
```

**API intercepted**:
```
GET /api/quiz/summary/{subtopicId}
Response: {
  "subtopicId": "uuid-1",
  "totalQuestions": 4,
  "correctAnswers": 3,
  "totalXp": 30,
  "questions": [
    {
      "questionId": "q-uuid-1",
      "prompt": "What is the primary pigment?",
      "options": ["Chlorophyll a", "Carotenoids", "Phycocyanin", "Xanthophyll"],
      "chosenIndex": 0,
      "correct": true,
      "correctIndex": 0,
      "explanation": "Chlorophyll a is..."
    }
    // ... 3 more
  ]
}
```

**Action**: Click "Start a new theme".

**Expected result**: Navigates to `/`, state is reset, fresh start.

---

## 4. Error Scenarios to Test

### 4.1 Empty / Short Input

**Action**: On `/`, type "a" (1 char), click submit.

**Expected**:
- Error banner appears: "Theme must be at least 2 characters long."
- No API call made.
- Button text remains "Suggest subtopics" (not "Thinking…").

### 4.2 LLM API Failure (500 / Timeout)

**Mock**: Intercept `POST /api/themes/suggest` → return 500.

**Expected on `/`**:
- Loading stops.
- Error banner appears with user-friendly message.
- User can retry.

**Mock**: Intercept `POST /api/snacks/generate` → return 503.

**Expected on `/snacks`**:
- Loading stops.
- Error banner: "Service unavailable" or similar.
- Snack cards not shown.

**Mock**: Intercept `POST /api/quiz/generate` → timeout (30s).

**Expected on `/quiz`**:
- Loading spinner persists for up to 30s.
- Then error banner appears.
- No quiz questions shown.

### 4.3 Direct Navigation (No State)

**Action**: Navigate directly to `http://localhost:4200/quiz` without going through the flow.

**Expected**: Redirects to `/snacks` (or `/` if no state at all). The app guards against missing state.

### 4.4 Backend Down

**Action**: Stop the backend, then use the frontend.

**Expected**:
- API calls fail with network error.
- Error banners appear on each page.
- Frontend remains functional (no crash).

---

## 5. Mock Strategy (No LLM Required)

For CI/E2E tests that shouldn't call a real LLM, intercept all API calls:

```typescript
// Playwright fixture or beforeEach
await page.route('**/api/themes/suggest', async (route) => {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      themeId: 'test-theme-001',
      subtopics: [
        { id: 'sub-001', name: 'Light reactions' },
        { id: 'sub-002', name: 'Calvin cycle' },
        { id: 'sub-003', name: 'Chloroplast structure' },
      ],
    }),
  });
});

await page.route('**/api/snacks/generate', async (route) => {
  const body = JSON.parse(route.request().postData() || '{}');
  const snacks = (body.subtopicIds || []).map((id: string, i: number) => ({
    id: `snack-${i}`,
    subtopicId: id,
    subtopicName: `Subtopic ${i + 1}`,
    concept: `This is the concept explanation for subtopic ${i + 1}. It covers the core idea in one paragraph.`,
    metaphor: `Imagine it like a kitchen — the ingredients are raw materials and the chef is the enzyme that transforms them.`,
    whyItMatters: `Understanding this prevents the common misconception that plants only photosynthesize during the day.`,
    related: ['ATP synthase', 'Electron transport', 'Photophosphorylation'],
  }));
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ snacks }),
  });
});

await page.route('**/api/quiz/generate', async (route) => {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      subtopicId: 'sub-001',
      questions: [
        {
          id: 'q-001',
          prompt: 'What is the primary role of chlorophyll in photosynthesis?',
          options: [
            'Absorbing light energy',
            'Storing glucose',
            'Releasing oxygen',
            'Transporting water',
          ],
        },
        {
          id: 'q-002',
          prompt: 'Where do the light reactions occur in the chloroplast?',
          options: [
            'Thylakoid membrane',
            'Stroma',
            'Outer membrane',
            'Intermembrane space',
          ],
        },
        {
          id: 'q-003',
          prompt: 'What molecule is the final electron acceptor in the light reactions?',
          options: [
            'NADP+',
            'Oxygen',
            'Carbon dioxide',
            'ATP',
          ],
        },
        {
          id: 'q-004',
          prompt: 'What is produced directly by the light reactions?',
          options: [
            'ATP and NADPH',
            'Glucose',
            'Carbon dioxide',
            'RuBP',
          ],
        },
      ],
    }),
  });
});

let answerCallCount = 0;
const answerKey: Record<string, { correctIndex: number; explanation: string }> = {
  'q-001': { correctIndex: 0, explanation: 'Chlorophyll absorbs light energy to drive the photosynthetic process.' },
  'q-002': { correctIndex: 0, explanation: 'The thylakoid membrane contains the photosystems and electron transport chain.' },
  'q-003': { correctIndex: 0, explanation: 'NADP+ accepts electrons at the end of the electron transport chain.' },
  'q-004': { correctIndex: 0, explanation: 'The light reactions produce ATP (energy) and NADPH (reducing power).' },
};

await page.route('**/api/quiz/answer', async (route) => {
  const body = JSON.parse(route.request().postData() || '{}');
  const key = answerKey[body.questionId] || { correctIndex: 0, explanation: 'Test explanation.' };
  const correct = body.chosenIndex === key.correctIndex;
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      questionId: body.questionId,
      correct,
      correctIndex: key.correctIndex,
      explanation: key.explanation,
      xpEarned: correct ? 10 : 0,
    }),
  });
});

await page.route('**/api/quiz/summary/*', async (route) => {
  // Extract subtopicId from URL
  const url = new URL(route.request().url());
  const subtopicId = url.pathname.split('/').pop();
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      subtopicId,
      totalQuestions: 4,
      correctAnswers: 3,
      totalXp: 30,
      questions: [
        { questionId: 'q-001', prompt: 'What is the primary role of chlorophyll?', options: ['Absorbing light energy', 'Storing glucose', 'Releasing oxygen', 'Transporting water'], chosenIndex: 0, correct: true, correctIndex: 0, explanation: 'Chlorophyll absorbs light energy.' },
        { questionId: 'q-002', prompt: 'Where do light reactions occur?', options: ['Thylakoid membrane', 'Stroma', 'Outer membrane', 'Intermembrane space'], chosenIndex: 1, correct: false, correctIndex: 0, explanation: 'Thylakoid membrane.' },
        { questionId: 'q-003', prompt: 'Final electron acceptor?', options: ['NADP+', 'Oxygen', 'Carbon dioxide', 'ATP'], chosenIndex: 0, correct: true, correctIndex: 0, explanation: 'NADP+ accepts electrons.' },
        { questionId: 'q-004', prompt: 'Produced by light reactions?', options: ['ATP and NADPH', 'Glucose', 'Carbon dioxide', 'RuBP'], chosenIndex: 0, correct: true, correctIndex: 0, explanation: 'ATP and NADPH.' },
      ],
    }),
  });
});
```

---

## 6. Critical Test Scenarios (Priority Order)

### P0 — Must Pass (Core Loop)

| # | Scenario | Steps | Expected |
|---|----------|-------|----------|
| 1 | Full happy path | Home → enter "Photosynthesis" → Subtopics → Snacks → Quiz (4 answers) → Results → Restart | Completes without errors, all pages render, XP shown |
| 2 | Input validation | Home → type "a" → submit | Error banner, no API call |
| 3 | Subtopic deselection | Subtopics → deselect 2 → "Generate snacks (N)" shows correct count | Only selected subtopics sent to API |
| 4 | Quiz answer feedback | Quiz → answer correctly → "Correct! +10 XP" shown | Feedback bar green, explanation visible |
| 5 | Quiz answer wrong | Quiz → answer wrong → "Not quite" shown | Feedback bar red, correct answer highlighted |
| 6 | Results accuracy | Results → verify accuracy % matches correct/total | Math is correct |
| 7 | Restart clears state | Results → "Start a new theme" → Home → enter new theme | Old state gone, fresh flow |

### P1 — Important

| # | Scenario | Steps | Expected |
|---|----------|-------|----------|
| 8 | Back navigation | Each page → "← Back" → previous page | Correct navigation, state preserved |
| 9 | Loading states | Each page → verify spinner + loading text appears during API call | No flash of empty content |
| 10 | Error recovery | Mock API 500 → verify error banner → fix mock → retry | App recovers, flow completes |
| 11 | Direct URL access | Navigate to `/quiz` directly | Redirects to appropriate guard page |

### P2 — Nice to Have

| # | Scenario | Steps | Expected |
|---|----------|-------|----------|
| 12 | Perfect score | Answer all 4 correctly → Results | "Perfect score!" + trophy + bonus XP |
| 13 | Long theme name | Enter 500-char theme | Accepted (max length) |
| 14 | Over max length | Enter 501-char theme | Error or truncated |

---

## 7. Common Failure Modes & Fixes

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| "Suggest subtopics" button stays "Thinking…" forever | Backend not running or `LLM_API_KEY` not set | Start backend, verify env var |
| Error banner: "Failed to fetch" | Frontend can't reach backend | Check proxy config, ensure backend on :8080 |
| Error banner: "Service unavailable" (503) | LLM provider down or rate-limited | Check LLM_BASE_URL, try different provider |
| Quiz page shows "Crafting…" forever | LLM quiz generation slow (>30s) | Check LLM timeout config, try faster model |
| Results page redirects to `/snacks` | `quizSubTopicId` not set in state | Bug in quiz component — should set state on generate |
| Subtopic cards not showing | `themeId` missing in state | Home page didn't store theme response properly |
| 404 on `/api/quiz/summary/*` | Backend summary endpoint not hit yet | Ensure quiz was answered before viewing results |
| MySQL connection error | MySQL not running | `docker start endia-mysql` or check port 3306 |
| Angular build fails | Node modules outdated | `cd frontend && npm install` |
| CORS error in browser | Frontend hitting backend directly (no proxy) | Use `ng serve --proxy-config proxy.conf.json` |

---

## 8. Selector Cheat Sheet

```typescript
// Home
'h1.hero__title'                    // "Learn anything, bite by bite."
'input.search__input'               // Theme input (aria-label: "Theme to study")
'button.search__button'             // "Suggest subtopics" / "Thinking…"

// Subtopics
'h1.page__title'                    // "Pick what to study"
'.subtopic-card'                    // Each subtopic card
'.subtopic-card--selected'          // Selected state
'.subtopic-card__name'              // Subtopic name text
'button.primary-btn'                // "Generate snacks (N)"

// Snacks
'.snack-card'                       // Each snack card
'.snack-card__chip'                 // Subtopic name chip
'.snack-card__concept'              // Concept text
'.snack-card__metaphor'             // Metaphor text
'.snack-card__why'                  // Why it matters text
'.snack-card__related .tag'         // Related concept tags
'.quiz-btn'                         // "Start Quiz" button

// Quiz
'.quiz-header .chip'                // Current subtopic name
'.progress-bar__fill'               // Progress bar fill element
'h2.question'                       // Question prompt text
'.option'                           // Each option button (A-D)
'.option__letter'                   // "A", "B", "C", "D"
'.option__text'                     // Option text
'.option--selected'                 // User's selection
'.option--correct'                  // Correct answer highlight
'.option--wrong'                    // Wrong selection highlight
'.feedback'                         // Feedback container
'.feedback__bar strong'             // "Correct! +10 XP" or "Not quite"
'.feedback__explanation'            // Explanation text
'.feedback .primary-btn'            // "Next question" / "See results"

// Results
'h1.results-header__title'          // "Perfect score!" or "Quiz complete"
'.perfect-badge'                    // "+50 bonus XP" (conditional)
'.score-card--accent .score-card__value'  // Total XP number
'.breakdown-card'                   // Each question breakdown
'.indicator'                        // ✓ or ✗
'.breakdown-card__prompt'           // Question text
'.mini-option__tag'                 // "Correct" or "Yours"
'.breakdown-card__explanation'      // Explanation text
'button.primary-btn'                // "Start a new theme"
```

---

## 9. API Contract Quick Reference

| Endpoint | Method | Request | Response (key fields) |
|----------|--------|---------|----------------------|
| `/api/themes/suggest` | POST | `{ "prompt": "string(2-500)" }` | `{ "themeId": "uuid", "subtopics": [{ "id", "name" }] }` |
| `/api/snacks/generate` | POST | `{ "themeId": "uuid", "subtopicIds": ["uuid"] }` | `{ "snacks": [{ "id", "subtopicId", "subtopicName", "concept", "metaphor", "whyItMatters", "related" }] }` |
| `/api/quiz/generate` | POST | `{ "themeId": "uuid", "subtopicId": "uuid", "count": 4 }` | `{ "subtopicId", "questions": [{ "id", "prompt", "options[4]" }] }` — **NO** correctIndex |
| `/api/quiz/answer` | POST | `{ "questionId": "uuid", "chosenIndex": 0-3 }` | `{ "questionId", "correct", "correctIndex", "explanation", "xpEarned" }` |
| `/api/quiz/summary/{id}` | GET | — | `{ "subtopicId", "totalQuestions", "correctAnswers", "totalXp", "questions": [{ "questionId", "prompt", "options", "chosenIndex", "correct", "correctIndex", "explanation" }] }` |

**Error responses**:
| Status | Body | When |
|--------|------|------|
| 400 | `{ "error": "Validation failed", "details": [...] }` | Invalid request body |
| 404 | `{ "error": "Not found", "message": "..." }` | Entity not found |
| 409 | `{ "error": "Conflict", "message": "..." }` | Duplicate answer |
| 500 | `{ "error": "Internal server error" }` | Unhandled exception |

---

*Handover complete. A Playwright agent should be able to write a full E2E suite from this document alone.*
