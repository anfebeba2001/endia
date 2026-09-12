# Quickstart: Read-Only History of Created Themes

**Feature**: `011-theme-history`

This guide shows how to run and verify the feature manually and via tests. It is an additive,
read-only change: the backend gains `GET /api/themes`; the frontend gains a `/history` page and a
header link.

---

## Prerequisites

- Java 21 + Maven (backend), Node 20+/npm (frontend).
- MySQL 8.x reachable by the backend (see `backend/src/main/resources/application.yml`).
- Docker, only for the backend integration tests (Testcontainers).
- An OpenAI-compatible LLM endpoint configured **only** if you want to create real themes; the E2E
  mocks bypass the LLM.

---

## Run the app

### Backend

```bash
cd backend
mvn spring-boot:run
```

API on `http://localhost:8080`.

### Frontend

```bash
cd frontend
npm install
npm start
```

App on `http://localhost:4200` (the dev server proxies `/api` to the backend; see
`frontend/proxy.conf.json` and `frontend/playwright.config.ts`).

---

## Seed some history (optional)

Either create themes through the UI (home → subtopics → snacks → quiz), or insert directly via MySQL
to get a deterministic ordering:

```sql
-- Two themes, newest second; one subtopic with a snack and a question on each.
INSERT INTO theme (id, prompt, created_at)
VALUES (UUID(), 'Older theme', '2026-09-10 10:00:00'),
       (UUID(), 'Newer theme', '2026-09-11 12:00:00');

INSERT INTO subtopic (id, theme_id, name, selected, source)
SELECT UUID(), id, 'A subtopic', 1, 'AI_SUGGESTED' FROM theme WHERE prompt = 'Newer theme';

INSERT INTO snack (id, subtopic_id, concept, metaphor, why_it_matters, related)
SELECT UUID(), id, 'A concept', 'A metaphor', 'Why it matters', JSON_ARRAY('a', 'b')
FROM subtopic WHERE name = 'A subtopic';

INSERT INTO quiz_question (id, subtopic_id, question_type, prompt, options, correct_index, explanation)
SELECT UUID(), id, 'MULTIPLE_CHOICE', 'A question?', JSON_ARRAY('a', 'b'), 0, 'An explanation.'
FROM subtopic WHERE name = 'A subtopic';
```

---

## Manual verification

### A. Backend endpoint (curl)

```bash
# Populated: array, newest-first, nested content.
curl -s http://localhost:8080/api/themes | jq

# Empty case (use a fresh DB): must be HTTP 200 and [].
curl -s -o /tmp/themes.json -w '%{http_code}\n' http://localhost:8080/api/themes
cat /tmp/themes.json   # -> []
```

Expected:

1. HTTP status `200`.
2. The body is a JSON **array**, not an object.
3. Entries are ordered `createdAt` descending.
4. Each entry has `themeId`, `prompt`, `createdAt`, `subtopics[]`; each subtopic has `id`, `name`,
   `snacks[]`, `quizQuestions[]`; each snack has `id`, `concept`, `metaphor`, `whyItMatters`,
   `related`; each question has `id`, `type`, `prompt`, `explanation`.
5. With no rows, the body is `[]` and the status is still `200` (no error, no 404).
6. Server logs show **no** `LazyInitializationException`.

### B. Header link and deep link (US1)

1. Open `http://localhost:4200/`.
2. Confirm a visible **"History"** link in the header (`.app-shell__header`), next to the theme
   toggle.
3. Click it → the URL becomes `/history` and the history page renders.
4. In a fresh tab, navigate **directly** to `http://localhost:4200/history` → the page renders (it
   does **not** redirect away, unlike `/quiz` and `/results`).

### C. History list and nested detail (US1/US2)

1. On `/history`, confirm each theme entry shows its `prompt` and a formatted `createdAt`, newest
   first.
2. Expand a theme → its subtopics are listed by `name`.
3. Expand/scroll a subtopic → each snack shows `concept`, `metaphor`, `whyItMatters`, and `related`;
   each question shows `type`, `prompt`, and `explanation`.
4. For a subtopic with no snacks or no questions, confirm an appropriate "nothing generated yet"
   indication and **no error**.
5. Open DevTools → Network, filter `/api`. Confirm the page issues only `GET /api/themes` and **no**
   `POST /api/batches`, `POST /api/snacks/generate`, or quiz request (read-only).
6. **Large history**: seed many themes with many nested items (repeat the SQL in "Seed some history"
   to create dozens of themes/subtopics/snacks/questions) and reload `/history`. Confirm the full
   list is returned and rendered without crashing, and the page remains usable (scroll/expand).
   Full history with no pagination is the specified contract (spec §Edge Cases; SC-008).

### D. Loading / error / empty states (US3)

1. **Loading**: throttle the network (DevTools → Network → Slow 3G) and reload `/history`; a loading
   state is visible while the request is in flight and disappears once it settles.
2. **Empty**: point the app at a backend with no themes (or mock `[]`); confirm the empty-state
   message and zero theme entries (no error).
3. **Error**: stop the backend (or mock a 500) and reload `/history`; confirm the page shows the
   `ErrorService`-derived message and no list, and the loading state is gone.

### E. Accessibility (US4)

1. Press `Tab` from the top of the page: the "History" header link is reachable with a visible focus
   ring.
2. On `/history`, tab to the theme-detail controls; they are reachable and activatable with
   `Enter`/`Space` and show a visible focus indicator.

---

## Automated tests

### Backend

```bash
# Unit + MockMvc contract tests (integration excluded by default).
cd backend
mvn test

# Integration tests against MySQL via Testcontainers (requires Docker).
mvn verify -Pintegration
```

New/updated tests:

- `application/service/ThemeServiceTest` — `listHistory()` ordering, empty case (no child-finder
  calls), nested assembly, no-snacks/no-questions cases.
- `interfaces/rest/ThemeHistoryContractTest` — `GET /api/themes` returns 200, an array with the full
  nested shape, and `[]` for empty; ordering preserved.
- `application/integration/ThemeHistoryIntegrationTest` — real MySQL: ordering, nested content, empty
  case, and nested data accessible after the service transaction ends (proves no
  `LazyInitializationException`).

### Frontend (Playwright)

```bash
cd frontend
npx playwright test e2e/theme-history.spec.ts    # the new spec
npx playwright test                              # full regression suite
npm run build                                    # type-check
```

`theme-history.spec.ts` covers: the header link and deep link; the populated list (order, `prompt`,
`createdAt`, nested snacks/questions); the empty state; the error state (from `ErrorService`); the
loading state; the read-only guarantee (0 batch/snack generation calls); and keyboard focus.

### Existing regression suite (must pass unchanged)

`happy-path.spec.ts`, `direct-url-access.spec.ts`, `restart-clears-state.spec.ts`,
`back-navigation.spec.ts`, `dark-mode.spec.ts`, `logo-visibility/*` (FR-021/SC-008).

---

## Troubleshooting

| Symptom | Likely cause | Check |
|---------|--------------|-------|
| `MultipleBagFetchException` on startup/first call | A `JOIN FETCH`/`@EntityGraph` fetches two or more bags at once | Fetch `subtopics` only via `@EntityGraph`; batch-load `snacks` and `quizQuestions` with `findBySubtopicIdIn`. |
| `LazyInitializationException` in logs / 500 on `/api/themes` | Nested data not materialized before the transaction closed | Assemble the `ThemeService.ThemeHistory` read model inside `listHistory()`; do not return managed entities. |
| `GET /api/themes` returns 404 | Route not added or `**` wildcard matched first | Add `@GetMapping` on `ThemeController`; keep the frontend `**` redirect last. |
| Empty history shows an error | Endpoint returns non-2xx for `[]` | It must return HTTP 200 with `[]`. |
| `/history` redirects to `/` | A guard/redirect was added, or the wildcard matched | `/history` needs no guard; keep the route before `**`. |
| Header layout shifts / logo-visibility tests fail | The link was added as a third direct header child | Wrap the link + toggle in `.app-shell__nav`. |
| `setupThemesHistoryMock` interferes with other specs | Added to `setupAllMocks` | Keep it out of `setupAllMocks`; install it only in `theme-history.spec.ts`. |
| History renders options/correct answers | Extra fields added to the DTO | The history contract exposes only `id/type/prompt/explanation` per question (Non-Goals). |
