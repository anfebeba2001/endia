# Quickstart: Accurate Step-by-Step Snack Waiting Messages

How to run and verify feature `008-snack-wait-messages` locally (backend + frontend): generate snacks and watch the in-progress message change from resolving context → waiting for the AI → assembling → saving, with no regression to ready/failed/empty-state/polling behavior. Builds on `/specs/001-batch-mvp-enhancements/quickstart.md`, `/specs/002-snack-retry-bound/quickstart.md`, and `/specs/005-batch-snack-generation-fix/quickstart.md`.

> **No database migration is required for this feature.** The schema is unchanged. The phase is in-memory runtime state only.

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

Same as `001`/`002`/`005`; **no new backend env vars are required** (the polling window remains the `MAX_POLLING_WINDOW_MS` injection token, default 120 s).

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

## Happy-path smoke test (the feature)

1. Open `http://localhost:4200`, enter a theme, select two or more subtopics, and click **Generate snacks**.
2. On the snacks page, watch the in-progress item's message. It must change with the real backend step:
   - `Finding the topic context…`
   - `Waiting for the AI response…` (stays for the whole model wait; no timeout-driven change)
   - `Assembling your snack…`
   - `Saving your snack…`
3. Confirm the message is **never** the single generic `Preparing your snack…` for the whole wait.
4. When the item becomes ready, the snack card renders and **no phase message** is shown for it. If it fails, the error banner with cause + retry appears and no phase message is shown.
5. With multiple subtopics, confirm each item shows its **own** phase (one item's phase is never applied to another).

### Observe the phase on the wire

While a batch is in progress, poll the API directly:

```bash
curl -s http://localhost:8080/api/batches/<batchId> | jq '.items[] | {subtopicId, snackStatus, snackPhase}'
```

A generating item shows `"snackPhase": "AWAITING_MODEL"` (or another phase); a ready/failed item shows `"snackPhase": null`.

### Making short phases observable

`ASSEMBLING` and `PERSISTING` are in-process and may complete between polls, so they can be skipped in a real run. That is expected and accepted (the screen never shows a stale phase). To observe all four deterministically, use the E2E mock below, which drives each phase across successive polls.

## Automated verification

```bash
# Backend unit + integration (Testcontainers MySQL)
cd backend && mvn test

# Frontend E2E (starts/uses the dev server per playwright.config.ts)
cd frontend && npx playwright test
```

### What the tests prove

- **Backend unit** (`SnackServiceTest`): a recording listener sees `RESOLVING_CONTEXT → AWAITING_MODEL → ASSEMBLING → PERSISTING`, and a re-roll yields `AWAITING_MODEL → AWAITING_MODEL → ASSEMBLING`. The `NOOP`/single-shot path is unchanged.
- **Backend unit** (`BatchServiceTest`): `getStatus` exposes the phase while `GENERATING` and `null` for `READY`/`FAILED`; `runBatch` clears the registry on success and failure.
- **Backend integration** (`BatchGenerationIntegrationTest`): a slow stub LLM lets a poll observe a phase mid-generation, which is cleared at terminal.
- **Contract**: the JSON includes `snackPhase` (value while `GENERATING`, `null` otherwise) with no other shape change.
- **E2E** (`snack-wait-messages.spec.ts`): each phase renders its exact message; two generating items render distinct messages; ready/failed render none; a retry re-enters the phases.
- **Regression**: `batch-empty-state.spec.ts`, `polling-termination.spec.ts`, `retry-bound.spec.ts`, `progressive-delivery.spec.ts`, `loading-states.spec.ts`, and the rest of the suite stay green.

## Manual failure-injection checks

- Point `LLM_BASE_URL` at an unreachable host / a mock returning 503 to force a failure: the item must show the `AWAITING_MODEL` message during the call, then transition to failed with the cause and retry affordance, and show **no** phase message.
- Click **Retry** on a failed item: its phases must appear again from `Finding the topic context…`.
- Inspect the browser network tab and backend logs: the phase payload and any logs must contain only static enum tokens/messages — no prompts, API keys, provider request/response payloads, or personal data (FR-011/SC-006).

## Rollback

Revert the feature branch. No data migration to undo; the phase registry is process-memory only.
