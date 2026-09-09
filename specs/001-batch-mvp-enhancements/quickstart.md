# Quickstart: Endia MVP — Batch Enhancements

How to run and test the whole batch (backend + frontend), including env vars. Builds on `/specs/mvp-e2e/quickstart.md`.

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| **Java** | 21+ | Backend runtime |
| **Maven** | 3.9+ | Backend build |
| **Node.js** | 20+ / **npm** 10+ | Frontend |
| **Angular CLI** | 21+ | Frontend (`npm install -g @angular/cli`) |
| **MySQL** | 8.x | Database |
| **Playwright** | 1.63+ | E2E (`frontend/e2e`) — install via `npx playwright install` if needed |

## Environment Variables (`.env` at repo root)

```bash
# LLM
LLM_API_KEY=sk-your-openai-compatible-api-key
LLM_BASE_URL=https://api.openai.com/v1
LLM_MODEL=gpt-4o-mini
# optional tuning (defaults in application.yml)
LLM_TIMEOUT_SECONDS=30

# MySQL
DB_HOST=localhost
DB_PORT=3306
DB_NAME=endia
DB_USERNAME=root
DB_PASSWORD=root

# Retry / backoff (new in this feature; defaults shown)
BATCH_RETRY_MAX_ATTEMPTS=3
BATCH_RETRY_INITIAL_BACKOFF_MS=1000
BATCH_RETRY_BACKOFF_FACTOR=2.0
BATCH_RETRY_MAX_BACKOFF_MS=10000
BATCH_RETRY_JITTER_RATIO=0.2
```

## Database

```bash
mysql -u root -e "CREATE DATABASE IF NOT EXISTS endia CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

`ddl-auto=update` creates the new `study_session`/`study_session_item` tables and adds new columns. **One manual step required** (breaking change to `answer_attempt`):

```bash
# The old unique (quiz_question_id, chosen_index) is invalid once chosen_index is
# nullable (drag-drop). Run once in dev:
mysql -u root -D endia -e "
ALTER TABLE answer_attempt DROP INDEX uk_answer_attempt_question_index;
ALTER TABLE answer_attempt MODIFY COLUMN chosen_index INT NULL;
ALTER TABLE answer_attempt ADD COLUMN arrangement JSON NULL;
ALTER TABLE answer_attempt ADD CONSTRAINT uk_answer_attempt_question UNIQUE (quiz_question_id);
"
```

## Build & Run

```bash
# Backend (repo root)
cd backend
mvn clean install -DskipTests
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# → http://localhost:8080

# Frontend (separate terminal)
cd frontend
npm install
ng serve --proxy-config proxy.conf.json
# → http://localhost:4200
```

`frontend/proxy.conf.json`:
```json
{ "/api": { "target": "http://localhost:8080", "secure": false, "changeOrigin": true } }
```

## Test the Whole Batch (progressive flow)

The live flow is: home → subtopics → (add custom subtopic) → **batch** → snacks (one at a time) → quiz → results (snack recap).

### 1. Suggest a theme

```bash
curl -X POST http://localhost:8080/api/themes/suggest -H "Content-Type: application/json" \
  -d '{"prompt": "Photosynthesis"}' | jq
```

### 2. Add a custom subtopic (FR-019)

```bash
THEME_ID="<themeId from step 1>"
curl -X POST http://localhost:8080/api/themes/$THEME_ID/subtopics -H "Content-Type: application/json" \
  -d '{"names": ["C4 photosynthesis pathway"]}' | jq
```

### 3. Create the batch (single trigger, FR-001)

```bash
curl -X POST http://localhost:8080/api/batches -H "Content-Type: application/json" -d '{
  "themeId": "'"$THEME_ID"'",
  "subtopicIds": ["<subtopicId1>", "<subtopicId2>", "<subtopicId3>"]
}' | jq
```

Capture `batchId`.

### 4. Poll status (progressive delivery, FR-002..004)

```bash
BATCH_ID="<batchId from step 3>"
watch -n 1 'curl -s http://localhost:8080/api/batches/'$BATCH_ID' | jq .status, .retrying, .items[].snackStatus'
```

- Snacks appear as `READY` **one at a time**; failed items show `FAILED` + `snackCause`.
- When `quizStatus: READY`, the quiz is prefetched. Fetch it for a subtopic:
  ```bash
  curl -s http://localhost:8080/api/quiz/summary/<subtopicId> | jq
  ```

### 5. Answer a question (drag-drop or MCQ, FR-008)

```bash
# MCQ
curl -X POST http://localhost:8080/api/quiz/answer -H "Content-Type: application/json" \
  -d '{"questionId": "<mcqQuestionId>", "chosenIndex": 0}' | jq

# Drag-drop (sequencing)
curl -X POST http://localhost:8080/api/quiz/answer -H "Content-Type: application/json" \
  -d '{"questionId": "<dragDropQuestionId>", "arrangement": ["Step A", "Step B", "Step C"]}' | jq
```

### 6. View summary (with snack recap, FR-009/010)

```bash
curl -s http://localhost:8080/api/quiz/summary/<subtopicId> | jq .recap
```

## Simulate failure modes (FR-015, SC-004)

Point the LLM at endpoints that exercise each cause and verify the typed message:

| Cause | How to trigger |
|-------|----------------|
| `UNAVAILABLE` | `LLM_BASE_URL` → a host returning 503 |
| `TIMEOUT` | set `LLM_TIMEOUT_SECONDS=1`, point at a slow endpoint |
| `NETWORK` | `LLM_BASE_URL` → an unreachable / wrong host |
| `QUOTA` | mock endpoint returns HTTP 429 |
| `MALFORMED` | mock endpoint returns non-JSON / missing fields |

> A simple mock: `nc -l 8081` returning a canned 429 body, or a WireMock/MockWebServer stub. In the browser, open devtools → Network → blocks/throttle to see spinner + retry + specific message.

## Verification of the 5 feature groups

1. **Async delivery**: batch returns in <1s; snacks appear one at a time; quiz prefetched.
2. **Resilience**: simulate each cause → specific message; transient fails recover ≤3 attempts with spinner.
3. **Quiz enhancements**: a generated quiz has ≥1 MCQ and ≥1 drag-drop (random variant); drag-drop grades correctly; results show labeled snack recap.
4. **Dark mode**: toggle in header → reload → persists; all pages readable.
5. **AI subtopics**: add custom → appears among selected → gets snack+quiz; duplicate is deduped.

## Test Suites

```bash
# Backend
cd backend && mvn test

# Frontend unit/component (if configured)
cd frontend && npm test

# E2E (existing 17 tests must stay green — SC-012)
cd frontend && npx playwright test
```

## Common Issues

- **MySQL unique index error**: run the manual `answer_attempt` migration above if `ddl-auto` did not drop the old constraint.
- **Quota during large batch**: retry + backoff handles transient quota; verify `retrying: true` appears in status.
- **Port conflicts**: backend 8080, frontend 4200, MySQL 3306.
- **No drag-drop on keyboard/touch**: the `drag-drop-question` component includes a keyboard reorder fallback; verify with Tab + arrow keys.
