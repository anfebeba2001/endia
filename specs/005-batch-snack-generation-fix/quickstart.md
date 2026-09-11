# Quickstart: Batch Snack Generation Fix

How to run and verify feature `005-batch-snack-generation-fix` locally (backend + frontend): create a theme, select subtopics, generate snacks, and confirm `READY` snacks appear with no hang, no detached-data failure, and no empty-state flash. Builds on `/specs/001-batch-mvp-enhancements/quickstart.md` and `/specs/002-snack-retry-bound/quickstart.md`.

> **No database migration is required for this feature.** The schema is unchanged. The fix is a persistence-scope/executor/UI-state change only.

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| **Java** | 21+ | Backend runtime |
| **Maven** | 3.9+ | Backend build |
| **Node.js** | 20+ / **npm** 10+ | Frontend |
| **Angular CLI** | 21+ | Frontend (`npm install -g @angular/cli`) |
| **MySQL** | 8.x | Database |
| **Playwright** | 1.63+ | E2E (`frontend/e2e`) — `npx playwright install` if needed |

## Environment Variables (`.env` at repo root)

Same as `001`/`002`; **no new backend env vars are required**. Optionally set the new quiz-join bound:

```bash
# LLM
LLM_API_KEY=sk-your-openai-compatible-api-key
LLM_BASE_URL=https://api.openai.com/v1
LLM_MODEL=gpt-4o-mini
LLM_TIMEOUT_SECONDS=30

# MySQL
DB_HOST=localhost
DB_PORT=3306
DB_NAME=endia
DB_USERNAME=root
DB_PASSWORD=root

# Existing LLM retry/backoff (unchanged)
BATCH_RETRY_MAX_ATTEMPTS=3
BATCH_RETRY_INITIAL_BACKOFF_MS=1000
BATCH_RETRY_BACKOFF_FACTOR=2.0
BATCH_RETRY_MAX_BACKOFF_MS=10000
BATCH_RETRY_JITTER_RATIO=0.2

# New (optional): bound on the nested quiz-prefetch join, seconds
BATCH_QUIZ_JOIN_TIMEOUT_SECONDS=240
```

The frontend polling window remains the `MAX_POLLING_WINDOW_MS` injection token (default 120 s), unchanged from `002`. For quick manual checks set `window.__MAX_POLLING_WINDOW_MS__` (e.g. via the Playwright `setPollingWindow` helper) or override the provider in `app.config.ts`.

## Database

```bash
mysql -u root -e "CREATE DATABASE IF NOT EXISTS endia CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

No schema change and no manual migration for this feature.

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

## Happy-path smoke test (the reported failure)

Flow: home → subtopics → snacks → quiz → results.

```bash
# 1. Suggest a theme
curl -X POST http://localhost:8080/api/themes/suggest -H "Content-Type: application/json" \
  -d '{"prompt": "Photosynthesis"}' | jq

# 2. Create a batch (capture batchId)
THEME_ID="<themeId>"
curl -X POST http://localhost:8080/api/batches -H "Content-Type: application/json" \
  -d '{"themeId": "'"$THEME_ID"'", "subtopicIds": ["<subId1>", "<subId2>", "<subId3>"]}' | jq

# 3. Poll and watch status, retrying, per-item snack status, and cause
BATCH_ID="<batchId>"
watch -n 1 'curl -s http://localhost:8080/api/batches/'$BATCH_ID' \
  | jq "{status, retrying, items: [.items[] | {subtopicName, snackStatus, snackCause, quizStatus}]}"'
```

**Expected (fixed)**:

- Within a bounded time the batch reaches a terminal status (`COMPLETE` / `PARTIAL` / `FAILED`) — it never stays `IN_PROGRESS` with items pending.
- On a successful model response, at least one item is `READY` with a non-null `snack`; `snackCause` is `null`.
- `UNAVAILABLE` is **not** recorded for every item (the detached-data signature).
- `retrying` is `true` only while an item is `GENERATING`.

In the UI: the snacks screen shows a progress/loading state until the first items arrive; `READY` snacks render one at a time; the screen stops polling on the terminal status.

## Verify US1 — theme context is available (no detached-data failure)

1. Watch the backend log during a full batch. Confirm there is **no** `LazyInitializationException` and no "Batch snack generation failed" warning caused by detached related data.
2. Confirm every item has `snackCause: null` when the model succeeds, and `snack` is non-null for `READY` items.
3. Confirm `POST /api/snacks/generate` (single-shot) still behaves as before (FR-017).

**Regression catcher**: `BatchGenerationIntegrationTest` runs the real async path on the real executors and asserts `READY` snacks with no lazy failure.

## Verify US2 — the batch always terminates (no self-deadlock)

1. Trigger **two or more batches at once** (two browser tabs, or two `curl` calls) with several subtopics each — this is the condition that saturates `batchExecutor` core threads and previously deadlocked.
2. Confirm every batch reaches a terminal status within a bounded time and none remains `IN_PROGRESS`.
3. In the backend log / a thread dump, confirm two distinct pools: `batch-worker-*` (top-level) and `quiz-worker-*` (nested quiz prefetch).
4. Optional: temporarily point `LLM_BASE_URL` at a hanging endpoint to confirm the bounded quiz-join still lets the batch terminate (quiz items may be `FAILED`, but the aggregate is terminal).

**Regression catcher**: `BatchGenerationIntegrationTest` starts concurrent batches with the real `batchExecutor`/`quizExecutor` and asserts a terminal status within the timeout.

## Verify US3 — no empty-state flash; polling stops

1. Open DevTools → Network and filter `batches`.
2. On `POST /api/batches` returning 202 and before the first `GET` returns, confirm the screen shows the progress/loading state and **not** "No snacks were prepared…".
3. Throttle the network (or delay the first `GET`) to widen the window; the empty state must still not appear.
4. Once the batch is terminal, confirm polling **stops** (no further `GET`s) and the retry spinner is cleared.
5. Force a terminal batch with zero ready snacks (all items fail) and confirm the empty/terminal outcome appears only **after** the batch is terminal.

## Verify FR-019 — no sensitive data

1. Force failures (see below) and inspect the API error bodies and backend logs.
2. Confirm messages contain only typed causes (`UNAVAILABLE`/`TIMEOUT`/`NETWORK`/`QUOTA`/`MALFORMED`) and ids — no API keys, raw prompts, or provider payloads.

## Force failures

| Failure | How to trigger |
|---------|----------------|
| `UNAVAILABLE` | `LLM_BASE_URL` → a host returning 503 |
| `TIMEOUT` | set `LLM_TIMEOUT_SECONDS=1`, point at a slow endpoint |
| `NETWORK` | `LLM_BASE_URL` → an unreachable / wrong host |
| `QUOTA` | mock endpoint returns HTTP 429 |
| `MALFORMED` | mock endpoint returns non-JSON / missing fields |
| `PARTIAL` batch | make the LLM fail for a subset of subtopics only |
| Hang (deadlock check) | point `LLM_BASE_URL` at a hanging endpoint and start concurrent batches |

## Run the test suites

```bash
# Backend (unit + integration; integration uses Testcontainers MySQL)
cd backend && mvn test

# E2E (existing suite must stay green — SC-007; plus the new 005 spec)
cd frontend && npx playwright test
```

## Common issues

- **Every snack is `FAILED`/`UNAVAILABLE`**: confirm `runBatch` calls `snackService.generateSingleSnack(UUID)` (by id) and **not** the `Subtopic` overload; confirm `SnackService.generateSingleSnack(UUID)` is `@Transactional`.
- **Batch never terminates / hangs**: confirm the nested quiz tasks run on `quizExecutor` (prefix `quiz-worker-`), not `batchExecutor`; confirm the quiz join is bounded (`BATCH_QUIZ_JOIN_TIMEOUT_SECONDS`).
- **Empty state flashes**: confirm `polling` is set on create success and the empty state is gated on `!polling()`; confirm it also requires a terminal `batchStatus`.
- **Polling never stops**: confirm the worker now writes a terminal aggregate status; the client's terminal check is the negative form (`status !== 'IN_PROGRESS'`) from `002`.
- **More than 3 attempts**: unchanged from `002`; confirm `SnackRetryService` was not modified.
- **Port conflicts**: backend 8080, frontend 4200, MySQL 3306.
