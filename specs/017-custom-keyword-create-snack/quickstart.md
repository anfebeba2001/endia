# Quickstart: "Create snack" from a Custom Keyword's Generated Meaning (Task 12)

**Feature**: `017-custom-keyword-create-snack`

This guide shows how to run the app, define a custom term (feature 016), activate **Create snack**, and
verify that a meaning snack derived from the custom term + generated meaning appears in the backbone —
quiz-free, visually distinct, and persistent — plus the failure, dedup, and non-regression behavior.

This is a **frontend-only** feature. The backend is unchanged and no schema/endpoint changes are made.

---

## Prerequisites

- Java 21 + Maven (backend), Node 20+/npm (frontend).
- MySQL 8.x reachable by the backend (see `backend/src/main/resources/application.yml`; defaults
  `localhost:3306/endia`, user/pass `root`/`root`). **No schema change** is introduced, so no table is
  created or altered.
- Docker, only for the backend integration tests (Testcontainers).
- An OpenAI-compatible LLM endpoint configured **only** if you want real definitions/meaning snacks
  (`LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`). The E2E suites mock the API and bypass the LLM.

---

## 1. Schema

**No action required.** This feature adds no entity, column, index or table; Hibernate
`ddl-auto=update` issues **no DDL**. Nothing to migrate and nothing to roll back at the database level
(see data-model.md §2).

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

## 3. Create a snack from a custom keyword (with a real LLM configured)

1. Open `http://localhost:4200`, enter a theme (e.g. `Photosynthesis`), and pick 2–3 subtopics.
2. Let the snack batch complete, then read a card on `/snacks`.
3. Select one or more words in the reading content (e.g. drag over `chemical`) and **hold ~2s**.
4. Click **Define**; wait for the generated meaning to appear in the card.
5. The card now shows a **Create snack** action. Click it.

**Expected**: the action shows a loading state (`Creating…`), then `Added to your snacks`, and a new
**Meaning snack** entry appears in the left-hand snack list — distinct badge/color, and selecting it
shows its content with **no** "Start Quiz".

To inspect the reused backend contract directly (the request body the page sends):

```bash
curl -s -X POST http://localhost:8080/api/meaning-snacks/generate \
  -H 'Content-Type: application/json' \
  -d '{"themeId":"<themeId>","subtopicId":"<activeEntrySourceSubtopicId>","term":"chemical","definition":"the energy stored in the bonds of molecules"}' \
  | python -m json.tool
```

The response is `{ "alreadyExisted": <bool>, "meaningSnack": { "kind": "MEANING", ... } }`. Repeating
the same `(subtopicId, term)` returns `alreadyExisted: true` and creates no new row.

---

## 4. Verify without an LLM (deterministic fixtures)

Because the Playwright suites mock the API, you can inspect the action, generation, dedup, persistence,
quiz-free and security behavior without a live model. Either:

- **Run the new E2E spec** (recommended):
  ```bash
  cd frontend
  npx playwright test e2e/custom-keyword-create-snack.spec.ts
  ```
- **Or mock in the browser**: intercept `POST /api/definitions/generate` (feature 016) and
  `POST /api/meaning-snacks/generate` (feature 014) using the bodies in
  `specs/017-custom-keyword-create-snack/contracts/meaning-snack.sample.json` and the feature-016
  `contracts/definition.sample.json`.

---

## 5. Manual verification checklist

### Action availability (US1 / FR-001–FR-003, SC-001)

- [ ] While "Define" is still loading, the card offers **no** snack-creation action.
- [ ] After the generated meaning is displayed, the card shows exactly **one** action labelled
      **Create snack**.
- [ ] If "Define" fails or the meaning is blank, **no** Create snack action is offered.
- [ ] A regular highlighted-keyword card still shows **Generate snack** (unchanged).

### Creation (US1 / FR-004–FR-010, SC-002/SC-003)

- [ ] Activating **Create snack** shows a loading state, then `Added to your snacks`.
- [ ] Exactly **one** meaning-snack entry is added to the backbone.
- [ ] Rapid repeated activation starts **at most one** request and creates **no** duplicate.
- [ ] Re-defining the same term (same source subtopic) reflects the already-generated (`done`) state
      and issues no duplicate.
- [ ] A forced failure (stop the LLM / mock a 503) shows a meaningful, user-safe error and adds **no**
      entry.
- [ ] The request's `subtopicId` is the active entry's source subtopic: for a regular entry its
      `subtopicId`; for a meaning entry its `sourceSubtopicId` (Option A).

### Quiz-free & distinct (US2 / FR-011–FR-013, SC-004)

- [ ] The created entry shows the distinct **Meaning snack** badge/color (same as other meaning snacks).
- [ ] Selecting it shows its content and **no** "Start Quiz"; it never routes to `/quiz`.
- [ ] A regular snack keeps its **Start Quiz** affordance and behavior.

### Persistence (US3 / FR-014/FR-015, SC-005)

- [ ] After a page reload / new session for the same theme, the custom meaning snack is still listed in
      the backbone with its content, distinct treatment, and no quiz.
- [ ] Pre-existing snacks and data are unaffected (no schema change).

### Security (US4 / FR-017–FR-019, SC-006)

Define a term and/or return a meaning containing each payload and confirm nothing executes and no
element is injected:

| Payload in term/meaning/content | Expected |
|---------------------------------|----------|
| `<script>window.__xss=1</script>` | visible inert text; `window.__xss` stays `undefined` |
| `<img src=x onerror="window.__xss=1">` | no `onerror` run; no `<img>` injected |
| `javascript:` URL / `<iframe>` / `<style>` | neutralized; readable text preserved |
| inline `style=` / headings / tables / code | neutralized; readable text preserved |

**Quick DOM check** in devtools console:

```js
document.querySelectorAll('.keyword-card script, .keyword-card img, .keyword-card iframe, .keyword-card style, .keyword-card a').length; // 0
document.querySelectorAll('.snack-card--meaning script, .snack-card--meaning img, .snack-card--meaning iframe, .snack-card--meaning style, .snack-card--meaning a').length; // 0
window.__xss; // undefined
```

### Non-regression (US5 / FR-020–FR-024, SC-008)

- [ ] Feature 016's selection-Define trigger/affordance/generation/display is unchanged apart from the
      added action.
- [ ] Feature 014's keyword "Generate snack" flow, feature 013's highlight/card behavior, feature 012's
      rich formatting, the regular snack/quiz/results/history flow are all unchanged.

---

## 6. Rollback

Revert the frontend edits (the page's `onDefineRequested` capture + `generateEnabled`/`activeGenerateState`
changes, the card's `customDefinition` input + label branch, the snacks-page binding, and the new E2E
spec). There is **no backend change and no data change**, so rollback is lossless and requires no SQL;
any snacks created during testing are ordinary feature-014 `MeaningSnack` rows and remain valid.

---

## 7. Automated verification

```bash
# Backend: unchanged — must pass as-is (reused feature-014 contract)
cd backend && mvn test

# Frontend: type-check/build
cd frontend && npm run build

# Frontend: new custom "Create snack" E2E
cd frontend && npx playwright test e2e/custom-keyword-create-snack.spec.ts

# Frontend: regression (must pass unchanged)
cd frontend && npx playwright test
```

Expected: the new spec passes; the existing suites (`custom-keyword-define`, `meaning-snacks`,
`snack-keywords`, `snack-rich-formatting`, `snacks-navigation`, `progressive-delivery`, `results-*`,
`theme-history`, `happy-path`, …) pass unchanged (FR-020–FR-024, SC-008).
