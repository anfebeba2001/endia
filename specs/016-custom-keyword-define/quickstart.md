# Quickstart: Custom Keyword "Define" from a Held Text Selection (2-Second Trigger)

**Feature**: `016-custom-keyword-define`

This guide shows how to run the app, select text in a snack's reading content, hold it ~2 seconds,
activate the transient **Define** affordance, and verify the generated short definition appears in
the existing card — plus the cancellation, bounds, failure, security, scope and non-regression
behaviour.

---

## Prerequisites

- Java 21 + Maven (backend), Node 20+/npm (frontend).
- MySQL 8.x reachable by the backend (see `backend/src/main/resources/application.yml`; defaults
  `localhost:3306/endia`, user/pass `root`/`root`). **No schema change** is introduced, so no table
  is created or altered.
- Docker, only for the backend integration tests (Testcontainers).
- An OpenAI-compatible LLM endpoint configured **only** if you want to generate real definitions
  (`LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`). The E2E suites mock the API and bypass the LLM.

---

## 1. Schema

**No action required.** This feature adds no entity, column, index or table; Hibernate
`ddl-auto=update` issues **no DDL**. Nothing to migrate and nothing to roll back at the database
level (see data-model.md §4).

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

## 3. Define a term (with a real LLM configured)

1. Open `http://localhost:4200`, enter a theme (e.g. `Photosynthesis`), and pick 2–3 subtopics.
2. Let the snack batch complete, then read a card on `/snacks`.
3. Select one or more words in the reading content (e.g. drag over `chlorophyll`).
4. **Hold the selection still for about 2 seconds.**

**Expected**: a small, clearly labelled **Define** button appears anchored to the selection. Click
it; the button disappears, the definition card opens for the selected term showing a loading state
("Defining…"), then the generated short definition (≤20 words).

To inspect what the backend returns directly:

```bash
curl -s -X POST http://localhost:8080/api/definitions/generate \
  -H 'Content-Type: application/json' \
  -d '{"themeId":"<themeId>","term":"chlorophyll"}' | python -m json.tool
```

The response is exactly `{ "term": "...", "definition": "..." }`.

---

## 4. Verify without an LLM (deterministic fixtures)

Because the Playwright suites mock the API, you can inspect the trigger, generation, bounds and
security behaviour without a live model. Either:

- **Run the new E2E spec** (recommended):
  ```bash
  cd frontend
  npx playwright test e2e/custom-keyword-define.spec.ts
  ```
- **Or mock in the browser**: intercept `POST /api/definitions/generate` using the bodies in
  `specs/016-custom-keyword-define/contracts/definition.sample.json`.

---

## 5. Manual verification checklist

### Hold trigger (US1 / FR-001..FR-003, SC-001)

- [ ] Selecting text inside a snack's reading content and holding ~2s shows a clearly labelled
      **Define** affordance anchored to the selection.
- [ ] No affordance appears before ~2s elapse.
- [ ] Changing or clearing the selection before ~2s cancels the timer and shows no affordance; a new
      held selection starts a fresh timer.
- [ ] Releasing the selection (mouseup) without activating shows no generation and leaves reading
      unaffected.
- [ ] The affordance is transient: it disappears when the selection is cleared, on Escape, on
      navigation, and when the definition card opens.

### Generation (US2 / FR-006..FR-009, SC-002/SC-003)

- [ ] Activating **Define** shows a loading state, then the card shows the generated definition for
      the selected term.
- [ ] Rapid repeated activation starts **at most one** request (no duplicate).
- [ ] A forced failure (stop the LLM / mock a 503) shows a meaningful, user-safe error and adds **no**
      empty/placeholder content.
- [ ] The generated definition is at most **20** words.

### Bounds (FR-010..FR-012, SC-005)

- [ ] A blank/whitespace-only selection is rejected with a meaningful error; no request is sent.
- [ ] A selection of more than **10** whitespace-separated tokens is rejected with a meaningful
      error; no request is sent.
- [ ] A 10-token selection is accepted; hyphenated compounds (e.g. `light-dependent`) count as one
      token.

### Reachability & non-interference (US4 / FR-003/FR-016..FR-018, SC-006)

- [ ] The affordance appears after a pointer drag selection and after a programmatic/keyboard
      selection (e.g. `document.getSelection()` from the console).
- [ ] Clicking an existing highlighted keyword still opens the existing definition card unchanged and
      does **not** trigger the selection flow.
- [ ] At most one definition card is ever shown; opening a new term (keyword or selected) replaces
      the previous card.
- [ ] Navigation, quiz start and existing interactions are unchanged.

### Scope (FR-017, SC-006)

- [ ] Selecting text on the **results recap** offers no **Define** affordance.
- [ ] Selecting text on the **read-only history** surface offers no **Define** affordance.
- [ ] Both surfaces render exactly as before.

### Security (US3 / FR-013..FR-015, SC-004)

Select/define a term whose text and whose generated definition contain each payload and confirm
nothing executes and no element is injected:

| Payload in term/definition | Expected |
|----------------------------|----------|
| `<script>window.__xss=1</script>` | visible inert text; `window.__xss` stays `undefined` |
| `<img src=x onerror="window.__xss=1">` | no `onerror` run; no `<img>` injected |
| `javascript:` URL / `<iframe>` / `<style>` | neutralized; readable text preserved |
| inline `style=` / headings / tables / code | neutralized; readable text preserved |

**Quick DOM check** in devtools console:

```js
document.querySelectorAll('.keyword-card script, .keyword-card img, .keyword-card iframe, .keyword-card style, .keyword-card a').length; // 0
window.__xss; // undefined
```

### Non-regression (FR-018, SC-007)

- [ ] The full existing flow (subtopics → snacks → quiz → results, plus history) behaves exactly as
      before.
- [ ] Feature 013's keyword highlighting and definition-card open/dismiss/focus behaviour are
      unchanged.
- [ ] Feature 014's meaning-snack flow and feature 012's rich formatting are unchanged.

---

## 6. Rollback

Revert the backend (prompt, port/provider, service, exception, DTOs, controller, tests) and frontend
(directive, affordance, model/service, card inputs, page wiring, E2E) changes and redeploy. There is
**no data change**, so rollback is lossless and requires no SQL.

---

## 7. Automated verification

```bash
# Backend: prompt + parser + service + contract tests
cd backend && mvn test

# Backend integration (Testcontainers MySQL), if Docker is available:
cd backend && mvn verify -Pintegration

# Frontend: type-check/build
cd frontend && npm run build

# Frontend: custom-define E2E
cd frontend && npx playwright test e2e/custom-keyword-define.spec.ts

# Frontend: regression (must pass unchanged)
cd frontend && npx playwright test
```

Expected: the new spec passes; the existing suites (`happy-path`, `progressive-delivery`,
`snack-wait-messages`, `results-*`, `snacks-navigation`, `snack-keywords`, `snack-rich-formatting`,
`meaning-snacks`, `theme-history`, …) pass unchanged (FR-018, SC-007).
