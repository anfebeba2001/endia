# Quickstart: Colored Keywords with Click-to-Show Short Definition Cards

**Feature**: `013-snack-keywords`

This guide shows how to run the app, generate snacks, and manually verify that key terms are highlighted
in a distinct color, that clicking/tapping one opens a short dismissible definition card, and that empty
or legacy keywords degrade gracefully — on the snack card, the results recap, and the history view.

---

## Prerequisites

- Java 21 + Maven (backend), Node 20+/npm (frontend).
- MySQL 8.x reachable by the backend (see `backend/src/main/resources/application.yml`; defaults
  `localhost:3306/endia`, user/pass `root`/`root`). The new `snack.keywords` column is created
  automatically by Hibernate `ddl-auto=update` on first start.
- Docker, only for the backend integration tests (Testcontainers).
- An OpenAI-compatible LLM endpoint configured **only** if you want to generate real snacks
  (`LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`). The E2E suites mock the API and bypass the LLM.

---

## 1. Apply the schema change

The only schema change is one additive nullable JSON column, applied automatically:

```sql
-- What Hibernate issues on backend start (MySQL 8):
alter table snack add column keywords JSON null;
```

Manual equivalent, if `ddl-auto` is disabled in your environment:

```sql
ALTER TABLE snack ADD COLUMN keywords JSON NULL;
```

Verify:

```sql
SHOW COLUMNS FROM snack LIKE 'keywords';   -- Type: json, Null: YES
```

Pre-existing rows have `keywords = NULL` and are read as an empty list (no backfill needed).

---

## 2. Run the app

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

App on `http://localhost:4200`; the dev server proxies `/api` to the backend
(`frontend/proxy.conf.json`).

---

## 3. Generate snacks (with a real LLM configured)

1. Open `http://localhost:4200`, enter a theme (e.g. `Photosynthesis`), and pick 2–3 subtopics.
2. Let the snack batch complete, then read the cards on `/snacks`.

**Expected**: 2–5 key terms per snack appear in a color distinct from the body text; clicking/tapping one
opens a short card showing a definition of at most 3 words.

To inspect what the backend actually stored/returned:

```bash
curl -s "http://localhost:8080/api/themes" | python -m json.tool
```

Each snack should carry `"keywords": [{ "term": "...", "definition": "..." }]` with no definition longer
than 3 words.

---

## 4. Verify without an LLM (deterministic fixtures)

Because the Playwright suites mock the API, you can inspect highlight, card, and injection behavior
without a live model. Either:

- **Run the new E2E spec** (recommended):
  ```bash
  cd frontend
  npx playwright test e2e/snack-keywords.spec.ts
  ```
- **Or mock in the browser**: use request interception to return the bodies from
  `specs/013-snack-keywords/contracts/snack-keywords.sample.json` for `POST /api/snacks/generate`,
  `GET /api/batches/{id}`, `GET /api/quiz/summary/{subtopicId}`, and `GET /api/themes`.

---

## 5. Manual verification checklist

### Highlight (US2 / FR-013..FR-017, SC-002)

On **`/snacks` (snack card)**, **`/results` (recap)**, and **`/history` (expanded theme)**:

- [ ] A key term that appears in `concept`/`metaphor`/`whyItMatters` is shown in a distinct color.
- [ ] **Every** occurrence of a matched term is highlighted.
- [ ] A term appearing in different letter casing is still highlighted (case-insensitive).
- [ ] A term that does not appear in the body text produces no highlight and no artifact.
- [ ] Overlapping terms highlight only the longer term; no nested/overlapping highlights.
- [ ] A term inside `**bold**` is both bold and highlighted.
- [ ] A term only in `related` is not highlighted (chips stay plain).
- [ ] Regex-like terms (`C++`, `(x)`, `a.b`) match literally and do not break rendering.

### Definition card (US3 / FR-018..FR-023, SC-003..SC-005)

- [ ] Click/tap a highlighted term → a card opens showing only that term's definition (≤ 3 words).
- [ ] Click/tap outside the card → it closes; you stay on the snack.
- [ ] The close control closes it; you stay on the snack.
- [ ] Escape closes it; you stay on the snack.
- [ ] Clicking a different highlighted term closes the previous card and opens the new one.
- [ ] Keyboard: Tab to a term, press Enter → card opens; Escape closes and focus returns to the term.
- [ ] Repeat at a desktop width and a narrow/mobile width: the card is fully visible, no horizontal
      overflow, no layout break.

### Graceful degradation (US5 / FR-024, SC-006)

- [ ] A snack with `keywords: []` renders exactly as before — no highlight, no card affordance, no error.
- [ ] A pre-existing snack (NULL keywords) renders normally with an empty keyword list.
- [ ] A snack whose terms do not appear in the text has no highlights and no artifacts.
- [ ] A very large keyword list does not break layout or performance.

### Security (US4 / FR-026..FR-029, SC-007)

Render a snack whose keyword `term`/`definition` contain each payload and confirm nothing executes and no
element is injected:

| Payload in a keyword term/definition | Expected |
|--------------------------------------|----------|
| `<script>window.__xss=1</script>` | visible inert text; `window.__xss` stays `undefined` |
| `<img src=x onerror="window.__xss=1">` | no `onerror` run; no `<img>` injected |
| `javascript:` URL / `<iframe>` / `<style>` | neutralized; readable text preserved |
| inline `style=` / headings / tables / code | neutralized; readable text preserved |

**Quick DOM check** in devtools console:

```js
document.querySelectorAll('script, img, iframe, style, a').length; // only framework-owned nodes
window.__xss; // undefined
```

---

## 6. Rollback

Revert the prompt, backend, and frontend changes, then drop the column:

```sql
ALTER TABLE snack DROP COLUMN keywords;
```

No stored data is rewritten by this feature, so rollback is lossless.

---

## 7. Automated verification

```bash
# Backend: prompt + parser + normalization + contract tests
cd backend && mvn test
# Backend integration (Testcontainers MySQL), if Docker is available:
cd backend && mvn verify -Pintegration

# Frontend: type-check/build
cd frontend && npm run build

# Frontend: keyword highlight + card + security E2E
cd frontend && npx playwright test e2e/snack-keywords.spec.ts

# Frontend: history keyword rendering
cd frontend && npx playwright test e2e/theme-history.spec.ts

# Frontend: regression (must pass unchanged after adding keywords: [] to fixtures)
cd frontend && npx playwright test
```

Expected: the new spec passes; the existing suites (`happy-path`, `progressive-delivery`,
`snack-wait-messages`, `results-*`, `snacks-navigation`, `theme-history`, `snack-rich-formatting`, …)
pass with their snack fixtures updated to include `keywords` (FR-030, SC-009).
