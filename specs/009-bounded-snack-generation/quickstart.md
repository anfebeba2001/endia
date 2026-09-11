# Quickstart: Guarantee Bounded Snack Generation

**Feature**: `009-bounded-snack-generation`

This guide shows how to run and verify the bounded snack/batch generation behavior — manually and via
tests. The feature adds a per-subtopic wall-clock bound around snack generation, finalizes items after
the bounded quiz join, and proves the batch always reaches a terminal status even against a provider
that never returns.

---

## Prerequisites

- Java 21, Maven, Node/Angular CLI, Docker (for integration tests).
- MySQL 8.x running locally (or the Docker Testcontainers used by the integration profile).
- An OpenAI-compatible LLM endpoint reachable, or the ability to point the app at a hanging endpoint.

---

## Configuration

All bounds are configurable; defaults are documented in `data-model.md`.

| Property | Default | Env override |
|----------|---------|--------------|
| `llm.timeout-seconds` | `30` | `LLM_TIMEOUT_SECONDS` |
| `batch.retry.max-attempts` | `3` | `BATCH_RETRY_MAX_ATTEMPTS` |
| `batch.quiz-join-timeout-seconds` | `240` | `BATCH_QUIZ_JOIN_TIMEOUT_SECONDS` |
| `batch.snack-timeout-seconds` | `240` | `BATCH_SNACK_TIMEOUT_SECONDS` |

`application.yml`:

```yaml
llm:
  timeout-seconds: ${LLM_TIMEOUT_SECONDS:30}

batch:
  snack-timeout-seconds: ${BATCH_SNACK_TIMEOUT_SECONDS:240}
  quiz-join-timeout-seconds: ${BATCH_QUIZ_JOIN_TIMEOUT_SECONDS:240}
  retry:
    max-attempts: ${BATCH_RETRY_MAX_ATTEMPTS:3}
```

For local verification of the bound, set a **small** value so a hang fails fast:

```bash
export BATCH_SNACK_TIMEOUT_SECONDS=10
```

---

## Run the backend

```bash
cd backend
mvn spring-boot:run
```

The API listens on `http://localhost:8080`.

---

## Manual verification

### 1. Start a batch and confirm it is accepted immediately (FR-015)

```bash
curl -s -o /tmp/create.json -w "%{http_code} %{time_total}s\n" \
  -X POST http://localhost:8080/api/batches \
  -H 'Content-Type: application/json' \
  -d '{"themeId":"<THEME_UUID>","subtopicIds":["<SUB1_UUID>","<SUB2_UUID>"]}'
```

Expected: `202` within milliseconds, before any generation completes. The body contains
`"status":"IN_PROGRESS"` and all items `PENDING`.

### 2. Confirm status reads never wait on generation (FR-016)

```bash
BATCH_ID=$(python3 -c "import json;print(json.load(open('/tmp/create.json'))['batchId'])")
curl -s -o /dev/null -w "%{http_code} %{time_total}s\n" http://localhost:8080/api/batches/$BATCH_ID
```

Expected: `200` promptly even while generation is in flight or hung.

### 3. Simulate a hanging provider

Point the app at an endpoint that accepts the connection but never responds, e.g.:

```bash
export LLM_BASE_URL=http://10.255.255.1/v1   # non-routable → connect/read hang
export LLM_API_KEY=test
export BATCH_SNACK_TIMEOUT_SECONDS=10
mvn spring-boot:run
```

Create a batch (step 1) and poll (step 2). Expected over the next seconds:

- Each item transitions to `"snackStatus":"FAILED"` with `"snackCause":"TIMEOUT"` at or before the
  configured `batch.snack-timeout-seconds`.
- The batch reaches a terminal status (`COMPLETE` / `PARTIAL` / `FAILED`) and never stays
  `IN_PROGRESS` with `PENDING`/`GENERATING` items.
- `POST` and `GET` remain prompt throughout.

### 4. Inspect the logs (FR-019 / FR-020)

Look for a WARN line naming the subtopic id and the bound, e.g.:

```
Snack generation for subtopic <uuid> exceeded the 10s per-subtopic bound; marking FAILED with cause TIMEOUT
```

Confirm the logs contain **no** prompt bodies, API keys, provider request/response payloads, or
personal data.

### 5. Confirm bounded polling on the UI (FR-018)

Start the frontend (`cd frontend && npm start`), generate snacks, and watch the network tab: polling
stops within one interval of a terminal status, or at the configured 120 s window with a terminal
error if the batch never settles.

---

## Automated tests

### Backend unit tests (fast, no Docker)

```bash
cd backend
mvn test
```

Covers:
- `BatchServiceTest`: per-subtopic timeout → item `FAILED`/`TIMEOUT` while the next item still
  generates; quiz-join finalization → terminal status with no `GENERATING` items; empty batch →
  terminal; invalid `snack-timeout-seconds` normalized; existing `retrying`/phase/partial-failure
  assertions.
- `SnackServiceTest`: the re-roll is exactly one (two invalid responses → two calls, then a typed
  throw).
- `RetryingLlmProviderTest`: exactly `maxAttempts` calls; non-retryable pass-through.
- `OpenAiLlmProviderTest`: a `LlmConfig`-built `RestClient` with a small `llm.timeout-seconds` fails a
  delayed response with `TIMEOUT` within the bound.

### Backend integration tests (Docker required)

```bash
cd backend
mvn test -Pintegration
```

`BoundedSnackGenerationIntegrationTest` runs the real Spring context with a **never-returning**
`LlmProvider` stub and short `batch.snack-timeout-seconds=2` / `batch.quiz-join-timeout-seconds=2`,
asserting:
- `POST /api/batches` returns `202` promptly and does not wait for generation;
- `GET /api/batches/{id}` returns promptly while the batch is non-terminal;
- every item settles `FAILED`/`TIMEOUT`;
- the batch reaches a terminal status within the bounded window, with no `PENDING`/`GENERATING` items.

The existing `BatchGenerationIntegrationTest` and `BatchFlowIntegrationTest` must remain green.

### Frontend E2E (non-regression, Playwright)

```bash
cd frontend
npx playwright test e2e/polling-termination.spec.ts e2e/retry-bound.spec.ts \
  e2e/batch-empty-state.spec.ts e2e/progressive-delivery.spec.ts e2e/loading-states.spec.ts
```

These lock the preserved 002/005 behavior: polling terminates on any terminal status and at the
configured window; automatic reattempts stop at 3.

---

## Troubleshooting

| Symptom | Likely cause | Check |
|---------|--------------|-------|
| Batch stays `IN_PROGRESS` with `GENERATING` items | Finalization step missing / not reloading items after the join | `BatchService.runBatch` finalization before `aggregateStatus` |
| Item never reaches `FAILED` on a hang | `snack-timeout-seconds` not applied or generation ran on the batch worker | `generateSnackBounded` + `snackExecutor` wiring |
| `POST /api/batches` slow | Generation ran on the request thread | `createBatch` must only persist + trigger `afterCommit` |
| False `TIMEOUT` on healthy generation | Bound below the finite worst case (≈186 s) | `batch.snack-timeout-seconds` ≥ 186 s |
| Test JVM hangs at shutdown | Test double not interruptible | Use an interruptible latch in the hanging stub |
