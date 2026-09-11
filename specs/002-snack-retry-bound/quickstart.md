# Quickstart: Bounded Snack Reattempts and Polling Termination

How to run and verify feature `002-snack-retry-bound` locally (backend + frontend), including how to force failures and observe the 3-attempt bound and polling termination. Builds on `/specs/001-batch-mvp-enhancements/quickstart.md`.

> **No database migration is required for this feature.** The schema is unchanged; the attempt budget lives in frontend memory.

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

Same as `001`; **no new backend env vars** for this feature.

```bash
# LLM (point at an unreachable/slow host to force failures)
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
```

### Frontend polling window (new, client-side)

The max polling lifetime is an Angular `InjectionToken` (`MAX_POLLING_WINDOW_MS`), default **120000 ms (120 s)**. Override it in `frontend/src/app/app.config.ts` providers for local tuning/testing:

```ts
import { MAX_POLLING_WINDOW_MS } from './services/batch.service';
// ...
{ provide: MAX_POLLING_WINDOW_MS, useValue: 10_000 }, // 10s for quick manual checks
```

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

## Happy-path smoke test

Flow: home → subtopics → snacks (one at a time) → quiz → results.

```bash
# 1. Suggest a theme
curl -X POST http://localhost:8080/api/themes/suggest -H "Content-Type: application/json" \
  -d '{"prompt": "Photosynthesis"}' | jq

# 2. Create a batch (capture batchId)
THEME_ID="<themeId>"
curl -X POST http://localhost:8080/api/batches -H "Content-Type: application/json" \
  -d '{"themeId": "'"$THEME_ID"'", "subtopicIds": ["<subId1>", "<subId2>"]}' | jq

# 3. Poll and watch status + retrying
BATCH_ID="<batchId>"
watch -n 1 'curl -s http://localhost:8080/api/batches/'$BATCH_ID' | jq .status, .retrying, .items[].snackStatus'
```

Expected: `retrying` is `true` only while an item is `GENERATING` (not for the whole `IN_PROGRESS` lifetime), and the frontend stops polling as soon as `status` becomes `PARTIAL`, `COMPLETE`, or `FAILED`.

## Verify US2 — polling terminates on `PARTIAL` / any non-`IN_PROGRESS`

1. Open DevTools → Network and filter `batches`.
2. Produce a mixed batch (some subtopics succeed, some fail). The backend returns `PARTIAL`.
3. Confirm the `GET /api/batches/{id}` requests **stop** once `PARTIAL` is returned (no further polls), and ready snacks + failed-item errors render.

To force `PARTIAL` deterministically, make the LLM fail for one subtopic only (e.g. a mock that fails when the subtopic name matches, or inject a failure for a single target via a proxy).

## Verify US1 — exactly 3 automatic attempts, then terminal

1. Force every snack generation to fail (see "Force failures" below).
2. Watch the Network tab / backend logs for `POST /api/batches`.
3. Confirm:
   - exactly **3** cycles for the failing target (initial + 2 reattempts), and **no 4th** `POST /api/batches`;
   - after the 3rd failure the spinner stops and a terminal error naming the cause is shown;
   - no background activity continues.

Optional: count create calls in the backend log:

```bash
# In the backend terminal, count batch creations for the run
grep -c "POST /api/batches" backend.log   # or watch the request log
```

## Verify US3 — retries only on explicit error, never on a timer

1. Point the LLM at a slow-but-working endpoint so a batch stays `IN_PROGRESS` for a while with no failed item.
2. Watch the Network tab for the duration: **zero** additional `POST /api/batches` should be issued while the batch is `IN_PROGRESS`.
3. Then force one item to fail: exactly one new cycle is started for that target.
4. Rapidly click Retry twice: only one new cycle starts (in-flight guard).

## Verify US4 — manual retry resets the budget

1. Exhaust the automatic budget (3 failures) until the terminal error appears.
2. Confirm the Retry affordance is visible for the failed target.
3. Click Retry once: exactly one new cycle starts, progress is shown, and the target gets a fresh 3-attempt budget.
4. If the retry succeeds, the terminal error clears and the snack is displayed.

## Verify FR-010 — bounded polling window (stuck `IN_PROGRESS`)

1. Make the backend stay `IN_PROGRESS` indefinitely (e.g. block the worker / point the LLM at a hanging endpoint).
2. Override `MAX_POLLING_WINDOW_MS` to a short value (e.g. `10_000`) in `app.config.ts`.
3. Reload the snacks page and confirm that after ~10 s polling **stops** with a terminal, user-visible error — instead of spinning forever.

## Force failures

| Failure | How to trigger |
|---------|----------------|
| `UNAVAILABLE` | `LLM_BASE_URL` → a host returning 503 |
| `TIMEOUT` | set `LLM_TIMEOUT_SECONDS=1`, point at a slow endpoint |
| `NETWORK` | `LLM_BASE_URL` → an unreachable / wrong host |
| `QUOTA` | mock endpoint returns HTTP 429 |
| `MALFORMED` | mock endpoint returns non-JSON / missing fields |
| `PARTIAL` batch | make the LLM fail for a subset of subtopics only |

A simple mock: `nc -l 8081` returning a canned 429 body, or a WireMock/MockWebServer stub. In the browser, DevTools → Network → throttle/block to observe the spinner, retries, and typed message.

## Run the test suites

```bash
# Backend
cd backend && mvn test

# E2E (existing 17 specs must stay green — SC-007; plus the new 002 specs)
cd frontend && npx playwright test
```

## Common issues

- **Spinner never stops**: confirm `isTerminal` is the negative predicate (`status !== 'IN_PROGRESS'`); a `PARTIAL` status must terminate polling.
- **More than 3 attempts**: confirm `canRetry` gates on `attempts < 3` and the in-flight guard is set before issuing the create request.
- **Repeated retries on the same failure**: confirm the `ownerBatchId` guard prevents a stale original-batch poll from re-triggering a handed-off target.
- **Timeout test too slow**: override `MAX_POLLING_WINDOW_MS` to a few seconds.
- **`retrying` stays true after completion**: confirm `BatchService.getStatus` derives it from item `GENERATING` status, not `session.status == IN_PROGRESS`.
- **Port conflicts**: backend 8080, frontend 4200, MySQL 3306.
