# Quickstart: Snack Rich Formatting (Bold Emphasis, Bullets, Short Paragraphs)

**Feature**: `012-snack-rich-formatting`

This guide shows how to run the app, generate snacks, and manually verify that **bold**, **bullet
lists**, and **short paragraphs** render on all three surfaces — and that `<script>` / `onerror`
payloads are neutralized. It is a formatting-only change: no schema, no endpoint, no JSON-shape change.

---

## Prerequisites

- Java 21 + Maven (backend), Node 20+/npm (frontend).
- MySQL 8.x reachable by the backend (see `backend/src/main/resources/application.yml`; defaults
  `localhost:3306/endia`, user/pass `root`/`root`).
- Docker, only for the backend integration tests (Testcontainers).
- An OpenAI-compatible LLM endpoint configured **only** if you want to generate real snacks
  (`LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`). The E2E suites mock the API and bypass the LLM.

---

## 1. Run the app

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

## 2. Generate snacks (with a real LLM configured)

1. Open `http://localhost:4200`, enter a theme (e.g. `Photosynthesis`), and pick 2–3 subtopics.
2. Let the snack batch complete, then read the cards on `/snacks`.

**Expected**: each non-trivial field shows real **bold** text, long fields show bullet lists and/or
several short paragraphs, and no literal `**` or leading `- ` is visible.

---

## 3. Verify without an LLM (deterministic fixtures)

Because the Playwright suites mock the API, you can inspect marked-up and malicious payloads without a
live model. Either:

- **Run the new E2E spec** (recommended), which asserts the exact behavior:
  ```bash
  cd frontend
  npx playwright test e2e/snack-rich-formatting.spec.ts
  ```
- **Or mock in the browser**: use Playwright/browser devtools request interception to return a
  `POST /api/snacks/generate` (and `GET /api/batches/{id}` / `GET /api/themes`) body containing the
  example in `specs/012-snack-rich-formatting/contracts/snack-response.sample.json`.

To inspect what the backend actually stored/returned for a generated snack:

```bash
curl -s "http://localhost:8080/api/themes" | python -m json.tool
```

The `concept`/`metaphor`/`whyItMatters` strings should contain `**` and `\n` exactly as generated (JSON
encodes newlines as `\n` on the wire; the decoded value is identical).

---

## 4. Manual verification checklist

### Rich formatting (US2 / FR-010..FR-013, FR-023)

On **`/snacks` (snack card)**, **`/results` (recap)**, and **`/history` (expanded theme)**:

- [ ] `**word**` shows as actual bold text; no literal `**` anywhere.
- [ ] Lines starting with `- ` / `* ` / `• ` show as a real bullet list, not literal dashes.
- [ ] Blank-line-separated text shows as separate paragraphs, not one merged block.
- [ ] A long field with no bullets still shows as multiple readable paragraphs.
- [ ] The same snack renders identically on all three surfaces.
- [ ] `related` chips/labels remain plain text (no markup interpretation).

### Plain / legacy text (US5 / FR-014, FR-020)

- [ ] A field with no markup renders as clean text with no empty `<p>`/`<ul>` and no stray markers.
- [ ] `5 * 3 = 15` and an unclosed `**` are not mangled.
- [ ] Previously stored snacks (history) still render correctly.

### Security / neutralization (US3 / FR-015..FR-019, FR-024)

Render a field containing each payload and confirm **nothing executes and no element is injected**:

| Payload in a snack field | Expected |
|--------------------------|----------|
| `<script>window.__xss=1</script>` | visible inert text; `window.__xss` stays `undefined` |
| `<img src=x onerror="window.__xss=1">` | no `onerror` run; no `<img>` injected |
| `<a href="javascript:alert(1)">x</a>` | shown as text; no active link |
| `<iframe src="..."></iframe>` / `<style>…</style>` | neutralized; readable text preserved |
| `<h1>`, `<table>`, code fences, inline `style=` | neutralized; readable text preserved |

**Quick DOM check** in devtools console:

```js
document.querySelectorAll('script, img, iframe, style, a').length; // only framework-owned nodes, none from snack text
window.__xss; // undefined
```

---

## 5. Automated verification

```bash
# Backend: prompt + round-trip unit/contract tests
cd backend && mvn test
# Backend integration (Testcontainers MySQL), if Docker is available:
cd backend && mvn verify -Pintegration

# Frontend: type-check/build
cd frontend && npm run build

# Frontend: rich formatting + security E2E
cd frontend && npx playwright test e2e/snack-rich-formatting.spec.ts

# Frontend: regression (must pass unchanged)
cd frontend && npx playwright test
```

Expected: the new spec passes; the existing suites (`happy-path`, `progressive-delivery`,
`snack-wait-messages`, `results-*`, `snacks-navigation`, `theme-history`, …) pass unchanged (FR-021,
SC-008).

---

## 6. Rollback

No database or data migration is involved. Revert the prompt file
(`backend/src/main/resources/prompts/generate-snack.md`) and the frontend renderer changes; no stored
value is transformed by this feature.
