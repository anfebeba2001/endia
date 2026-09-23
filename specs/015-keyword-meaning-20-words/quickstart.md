# Quickstart: Expand Keyword Meaning Limit from 3 to 20 Words

**Feature**: `015-keyword-meaning-20-words`

This guide shows how to run the app, verify that keyword meanings of 4–20 words survive from
generation to display, confirm the 19/20/21-token boundary, and confirm the definition card and
highlight stay readable — with no schema change and no regression in the snack/quiz flow.

---

## Prerequisites

- Java 21 + Maven (backend), Node 20+/npm (frontend).
- MySQL 8.x reachable by the backend (see `backend/src/main/resources/application.yml`; defaults
  `localhost:3306/endia`, user/pass `root`/`root`). **No schema step is needed** for this feature.
- Docker, only for the backend integration tests (Testcontainers).
- An OpenAI-compatible LLM endpoint configured **only** if you want to generate real snacks
  (`LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`). The E2E suites mock the API and bypass the LLM.

---

## 1. No schema change

There is **no migration and no DDL** for this feature. Keyword definitions are stored as free text
inside the existing `snack.keywords` JSON column and `meaning_snack.source_definition`; previously
stored 3-word definitions remain valid and are served unchanged (FR-017).

> Watch item: `meaning_snack.source_definition` is `VARCHAR(200)`. A 20-word definition with unusually
> long words could exceed 200 characters; see plan.md Risks / data-model.md §4. No action unless
> observed.

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

## 3. Verify longer meanings end-to-end (with a real LLM configured)

1. Open `http://localhost:4200`, enter a theme (e.g. `Photosynthesis`), and pick 2–3 subtopics.
2. Let the snack batch complete, then read the cards on `/snacks`.
3. Inspect a snack's keywords: a definition may now be several words long (up to 20), not a 3-word
   gloss.
4. Click/tap a highlighted term to open its definition card.
5. Confirm the **full** definition (all words up to 20) is shown, wrapped inside the card.

To inspect what the backend returned:

```bash
# Snack-generation response (keyword definitions)
curl -s -X POST http://localhost:8080/api/snacks/generate \
  -H 'Content-Type: application/json' \
  -d '{"themeId":"<themeId>","subtopicIds":["<subtopicId>"]}' | python -m json.tool

# Meaning snacks (sourceDefinition)
curl -s "http://localhost:8080/api/meaning-snacks?themeId=<themeId>" | python -m json.tool
```

Expected: each `definition` / `sourceDefinition` is at most 20 whitespace-separated tokens and is
**not** shortened to 3.

---

## 4. Verify the boundary without an LLM (deterministic)

The boundary is enforced at the service layer; the fastest check is the backend unit tests:

```bash
cd backend
mvn -Dtest=SnackServiceTest,MeaningSnackServiceTest,GenerateSnackPromptTest test
```

Expected (FR-015 / SC-003):
- a 19-token definition is kept in full;
- a 20-token definition is kept in full;
- a 21-token definition is truncated to exactly its first 20 tokens;
- the same for the meaning-snack `sourceDefinition`;
- a source definition that is ≤20 tokens but >200 characters is bounded to ≤200 chars on a word
  boundary (FR-019 / SC-008);
- the rendered `generate-snack.md` prompt states the 20-word limit (and no longer states 3).

Sample definitions for manual/curl use are in
`specs/015-keyword-meaning-20-words/contracts/keyword-definition-length.sample.json`.

---

## 5. Manual verification checklist

### Length policy (US1/US2 / FR-001..FR-007, SC-001..SC-003)

- [ ] A generated keyword definition of 4–20 words is stored, returned and displayed in full — **not**
      truncated to 3.
- [ ] A definition of exactly 20 words is kept in full (inclusive bound).
- [ ] A definition of 21+ words is truncated to exactly the first 20 tokens before persist/serve.
- [ ] The same 20-token rule applies to a meaning snack's `sourceDefinition`.
- [ ] A meaning-snack `sourceDefinition` that is ≤20 tokens but >200 characters is bounded to **≤200
      characters on a word boundary** (no word split); no schema change is involved (FR-019, SC-008).
- [ ] `SnackKeyword.definition` (JSON column) is **not** character-clamped — only the token cap applies.
- [ ] A blank/whitespace-only definition is still dropped (snack keyword) / rejected (meaning-snack
      request).
- [ ] No stored/returned definition exceeds 20 tokens.
- [ ] Snack content fields (`concept`, `metaphor`, `whyItMatters`) and the `related` 2–3 item cap are
      unchanged; the 2–5 keyword guideline is unchanged.

### Prompt & docs (FR-008, FR-009, FR-018, SC-007)

- [ ] `generate-snack.md` example JSON and explicit rule both state **20** words; no "3 words"/"three
      words" instruction remains.
- [ ] `generate-meaning-snack.md` no longer calls the supplied definition "very short"/"short" (JSON
      shape and content rules unchanged).
- [ ] No stale "at most 3 words" statement remains in the changed in-code surfaces (`SnackKeyword`,
      `SnackKeywordDto`, `snack.model.ts`, the two service comments).
- [ ] The generation flow remains externalized/provider-agnostic (prompt text only; no provider logic
      in services).

### Rendering & readability (US3 / FR-011..FR-014, SC-004/SC-005)

- [ ] A definition card showing a 20-word definition wraps the text, with **no horizontal overflow**
      and no clipping, at desktop width.
- [ ] The same holds at a narrow (mobile) width, where the card uses its existing bottom-sheet
      presentation.
- [ ] A single very long token, and Unicode/emoji/RTL text, wrap without breaking the layout.
- [ ] Markup/injection payloads (`<script>`, `onerror=`, `javascript:`, `<iframe>`, `<style>`, inline
      styles) in a definition render as inert escaped text; nothing executes or is injected.
- [ ] Rendering still uses escaped interpolation only — no `[innerHTML]`, no `DomSanitizer`.

### Non-regression (FR-016, FR-017, SC-006)

- [ ] The full existing flow (subtopics → snacks → quiz → results, plus history) is unchanged.
- [ ] Feature 013's highlight/definition-card open/dismiss/focus and feature 014's meaning-snack
      action/distinct rendering/no-quiz behavior are unchanged.
- [ ] No new endpoint, entity, user action or schema change exists.

---

## 6. Rollback

Revert the source edits (two service constants, two prompts, four comments, three backend tests, one
frontend comment, one E2E spec) and redeploy. **No data change**, so rollback is lossless and requires
no SQL.

---

## 7. Automated verification

```bash
# Backend: unit + contract tests
cd backend && mvn test
# Backend integration (Testcontainers MySQL), if Docker is available:
cd backend && mvn verify -Pintegration

# Frontend: type-check/build
cd frontend && npm run build

# Frontend: keyword + rendering E2E (includes the 20-word no-overflow assertion)
cd frontend && npx playwright test e2e/snack-keywords.spec.ts

# Frontend: regression (must pass unchanged)
cd frontend && npx playwright test
```

Expected: the updated `snack-keywords.spec.ts` passes (including the 20-word wrapping assertion); the
existing suites (`happy-path`, `progressive-delivery`, `snack-wait-messages`, `results-*`,
`snacks-navigation`, `snack-rich-formatting`, `meaning-snacks`, `theme-history`, …) pass unchanged
(FR-016, SC-006).
