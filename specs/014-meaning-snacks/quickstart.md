# Quickstart: "Generate snack" from a Keyword Definition into the Snack Backbone (No Quiz)

**Feature**: `014-meaning-snacks`

This guide shows how to run the app, generate a meaning snack from a keyword definition card, verify
it appears as a distinct, quiz-free entry in the `/snacks` backbone, confirm it survives a reload/new
session for the same theme, and confirm no regression in the regular snack/quiz flow.

---

## Prerequisites

- Java 21 + Maven (backend), Node 20+/npm (frontend).
- MySQL 8.x reachable by the backend (see `backend/src/main/resources/application.yml`; defaults
  `localhost:3306/endia`, user/pass `root`/`root`). The new `meaning_snack` table is created
  automatically by Hibernate `ddl-auto=update` on first start.
- Docker, only for the backend integration tests (Testcontainers).
- An OpenAI-compatible LLM endpoint configured **only** if you want to generate real meaning snacks
  (`LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`). The E2E suites mock the API and bypass the LLM.

---

## 1. Apply the schema change

The only schema change is one additive **new table**, created automatically:

```sql
-- What Hibernate issues on backend start (MySQL 8):
create table meaning_snack ( ... );   -- see data-model.md §5 for the full DDL
```

Manual equivalent, if `ddl-auto` is disabled in your environment:

```sql
CREATE TABLE meaning_snack (
  id BINARY(16) NOT NULL,
  theme_id BINARY(16) NOT NULL,
  source_subtopic_id BINARY(16) NOT NULL,
  source_term VARCHAR(500) NOT NULL,
  source_definition VARCHAR(200) NOT NULL,
  concept TEXT NOT NULL,
  metaphor TEXT NOT NULL,
  why_it_matters TEXT NOT NULL,
  related JSON NOT NULL,
  kind VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY ux_meaning_snack_source (source_subtopic_id, source_term)
);
CREATE INDEX idx_meaning_snack_theme ON meaning_snack (theme_id);
CREATE INDEX idx_meaning_snack_subtopic ON meaning_snack (source_subtopic_id);
```

Verify:

```sql
SHOW TABLES LIKE 'meaning_snack';
SHOW COLUMNS FROM meaning_snack;
```

No existing table, column or row is modified.

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

## 3. Generate a meaning snack (with a real LLM configured)

1. Open `http://localhost:4200`, enter a theme (e.g. `Photosynthesis`), and pick 2–3 subtopics.
2. Let the snack batch complete, then read the cards on `/snacks`.
3. On a snack that has keywords, click/tap a highlighted term to open its definition card.
4. Click **Generate snack**.

**Expected**: the card shows a loading state while generating, then a "Added to your snacks" state;
the new meaning snack appears in the backbone index (distinct colour/badge, e.g. `Meaning snack`),
immediately after its source subtopic's regular snack, and it shows **no** "Start Quiz" button.

To inspect what the backend stored/returned:

```bash
# List persisted meaning snacks for a theme
curl -s "http://localhost:8080/api/meaning-snacks?themeId=<themeId>" | python -m json.tool

# Or inspect the raw table
mysql -uroot -proot endia -e "SELECT id, source_term, kind, created_at FROM meaning_snack;"
```

Each item should carry `"kind": "MEANING"`, its `sourceTerm`/`sourceDefinition`, generated content,
and **no** `subtopicId` quiz target / `quizStatus` / `quizQuestions` / `keywords`.

---

## 4. Verify without an LLM (deterministic fixtures)

Because the Playwright suites mock the API, you can inspect the action, distinct rendering, no-quiz
invariant, persistence and injection behaviour without a live model. Either:

- **Run the new E2E spec** (recommended):
  ```bash
  cd frontend
  npx playwright test e2e/meaning-snacks.spec.ts
  ```
- **Or mock in the browser**: intercept `POST /api/meaning-snacks/generate` and
  `GET /api/meaning-snacks?themeId=...` using the bodies in
  `specs/014-meaning-snacks/contracts/meaning-snack.sample.json`.

---

## 5. Manual verification checklist

### Generate action (US1 / FR-001..FR-006, SC-001..SC-003)

- [ ] Every open keyword definition card shows a clearly labelled **Generate snack** action.
- [ ] Clicking it shows a loading/in-progress state for that action.
- [ ] On success, exactly one meaning snack derived from that term+definition is added to the backbone.
- [ ] On a forced failure (e.g. stop the LLM / mock a 503), a meaningful error is shown and **no**
      entry, empty card or placeholder is added.
- [ ] Rapid double-clicking starts at most one generation and creates no duplicate.
- [ ] Re-opening the card for a keyword that already has a meaning snack shows the generated state and
      does not create a duplicate.

### Distinct rendering & no quiz (US2 / FR-012..FR-015, SC-004/SC-005)

- [ ] A meaning snack is visually distinct from a regular snack (colour **and** a text badge).
- [ ] A meaning snack exposes **no** "Start Quiz" action and no quiz entry of any kind.
- [ ] A regular snack keeps its existing quiz button, badge and behaviour unchanged.
- [ ] Selecting a meaning snack shows its content and never a blank card.
- [ ] With a mixed list, Previous/Next and their disabled states remain correct at the first/last
      position; selecting any entry never lands on a blank card.

### Persistence / reload (US3 / FR-018..FR-020, SC-006)

- [ ] A generated meaning snack is still listed in the backbone after a page reload.
- [ ] It is still listed in a **new session** for the same theme (`themeId`).
- [ ] It keeps its distinct treatment and no-quiz behaviour after reload.
- [ ] With the listing endpoint failing or returning `[]`, the backbone degrades gracefully: no crash,
      no broken entries, regular snacks still render.

### Security (US4 / FR-021..FR-023, SC-008)

Render a meaning snack (and its source keyword) whose term/definition/content contain each payload and
confirm nothing executes and no element is injected:

| Payload in term/definition/content | Expected |
|-------------------------------------|----------|
| `<script>window.__xss=1</script>` | visible inert text; `window.__xss` stays `undefined` |
| `<img src=x onerror="window.__xss=1">` | no `onerror` run; no `<img>` injected |
| `javascript:` URL / `<iframe>` / `<style>` | neutralized; readable text preserved |
| inline `style=` / headings / tables / code | neutralized; readable text preserved |

**Quick DOM check** in devtools console:

```js
document.querySelectorAll('.snack-card--meaning script, .snack-card--meaning img, .snack-card--meaning iframe, .snack-card--meaning style, .snack-card--meaning a').length; // 0
window.__xss; // undefined
```

### Non-regression (FR-024/FR-025, SC-009)

- [ ] The full existing flow (subtopics → snacks → quiz → results, plus history) behaves exactly as
      before: progressive delivery, retries, navigation, quiz generation, results, XP and history.
- [ ] Feature 013's keyword highlighting and definition-card open/dismiss/focus behaviour are
      unchanged apart from the added action.
- [ ] Feature 012's rich formatting in meaning-snack content renders correctly through the safe
      renderer.

---

## 6. Rollback

Revert the backend (entity, enum, repository, service, controller, DTOs, prompt, port/provider) and
frontend changes, then drop the table:

```sql
DROP TABLE meaning_snack;
```

No existing data is rewritten by this feature, so rollback is lossless.

---

## 7. Automated verification

```bash
# Backend: prompt + parser + service + contract tests
cd backend && mvn test
# Backend integration (Testcontainers MySQL), if Docker is available:
cd backend && mvn verify -Pintegration

# Frontend: type-check/build
cd frontend && npm run build

# Frontend: meaning-snack E2E
cd frontend && npx playwright test e2e/meaning-snacks.spec.ts

# Frontend: regression (must pass unchanged)
cd frontend && npx playwright test
```

Expected: the new spec passes; the existing suites (`happy-path`, `progressive-delivery`,
`snack-wait-messages`, `results-*`, `snacks-navigation`, `snack-keywords`,
`snack-rich-formatting`, `theme-history`, …) pass unchanged (FR-024, SC-009).
