# Quickstart: Return to Snacks from Results + Snacks Navigation

**Feature**: `010-snacks-navigation`

This guide shows how to run and verify the feature manually and via tests. It is a **frontend-only**
change: the backend is unchanged, so the normal backend + frontend dev setup is all that is needed.

---

## Prerequisites

- Java 21 + Maven (to run the unchanged backend), Node 20+/npm, Angular CLI (via `npm` scripts).
- MySQL 8.x running and reachable by the backend (see `backend/src/main/resources/application.yml`).
- An OpenAI-compatible LLM endpoint configured (or the E2E mocks, which bypass the LLM).

---

## Run the app

### Backend (unchanged)

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

App on `http://localhost:4200` (Angular dev server proxies `/api` to the backend; see
`frontend/proxy.conf` / `angular.json`).

---

## Manual verification

### A. Return to snacks from results (US1)

1. Complete the loop: home → enter a theme → select subtopics → "Generate snacks" → wait for the
   first snack → "Start Quiz" → answer all questions → land on `/results`.
2. Confirm the footer shows **two** actions side by side: **"Back to snacks"** and
   **"Start a new theme"** — in both the "Quiz complete" and "Perfect score!" states (get a perfect
   score by answering every question correctly).
3. Open DevTools → Network, filter `/api/batches`, and clear the log.
4. Click **"Back to snacks"**.
   - You land on `/snacks` and the **already-generated snacks are shown immediately** — no
     "Warming up the knowledge oven…" spinner, no empty state.
   - **No new `POST /api/batches`** request appears in the Network log.
   - The **quizzed subtopic's snack** is the active card (its chip equals the subtopic you quizzed),
     not necessarily the first snack.
5. Go back to `/results` (browser back or re-run the quiz) and click **"Start a new theme"**.
   - State is reset and you land on the home page (`/`). This is the existing restart behavior.

### B. Left-side index and Next/Previous (US2/US3)

1. From `/snacks`, confirm a **left-side index** lists every snack in the set, each labelled by its
   subtopic name.
2. The active entry is highlighted; inspect it in DevTools and confirm `aria-current="true"` plus the
   active class.
3. Click a different **ready** entry → the displayed card switches to that snack and the highlight
   moves with it.
4. While generation is still in progress, confirm entries for snacks that are not `READY` are
   **disabled** and clicking them does nothing.
5. Use **Previous**/**Next**:
   - Each moves the active index by exactly one and updates the card.
   - **Previous** is disabled at the first snack; **Next** is disabled at the last snack.
   - Repeated Next reaches every ready snack with no blank card.
6. Move to a middle snack, start a quiz, finish it, and click **"Back to snacks"** (or navigate away
   and back). Confirm the **same snack** is active.

### C. Accessibility (US4)

1. Press `Tab` to move focus through the index entries, Previous, Next, and "Back to snacks".
2. Confirm each is reachable, shows a visible focus ring (`:focus-visible`), and activates with
   `Enter`/`Space`.
3. After any mix of Next/Previous/index clicks, confirm the displayed card, the highlighted entry, and
   the enabled/disabled states all agree.

---

## Automated tests

### Frontend E2E (Playwright)

```bash
cd frontend
npx playwright test
```

New specs for this feature:

```bash
npx playwright test e2e/results-back-to-snacks.spec.ts e2e/snacks-navigation.spec.ts
```

What they cover:

- `results-back-to-snacks.spec.ts` (US1): both completion states render "Back to snacks"; clicking it
  navigates to `/snacks` with **zero** additional `POST /api/batches`; the quizzed subtopic's snack is
  active; existing snacks render with no loading/empty state; "Start a new theme" still resets to `/`.
- `snacks-navigation.spec.ts` (US2/US3/US4): the index lists every snack by name; exactly one entry
  has `aria-current="true"` + active class; click-to-jump updates card and highlight together;
  non-READY entries are disabled; Next/Previous move exactly one and are disabled at boundaries; the
  index survives a quiz/return round trip; "Start Quiz" uses the active snack's `subtopicId`; keyboard
  focus is visible.

### Existing regression suite (must pass unchanged)

```bash
cd frontend
npx playwright test e2e/restart-clears-state.spec.ts e2e/back-navigation.spec.ts \
  e2e/progressive-delivery.spec.ts e2e/loading-states.spec.ts
```

Also keep the wider suite green (`polling-termination`, `retry-bound`, `manual-retry`,
`snack-wait-messages`, `bounded-snack-settle`, `happy-path`, `perfect-score`, `batch-empty-state`).

### Type-check / build

```bash
cd frontend
npm run build
```

### Backend

No backend tests are added or changed (the backend is frozen). Optionally confirm it still builds:

```bash
cd backend
mvn -q -DskipTests package
```

---

## Troubleshooting

| Symptom | Likely cause | Check |
|---------|--------------|-------|
| Returning to `/snacks` shows the "Warming up the knowledge oven…" spinner forever | `init()` short-circuited without hydrating `items()` | Hydrate `items` from `appState.snacks()` and set `creating=false`/`polling=false` on the return path |
| A new `POST /api/batches` fires on return | `backToSnacks()` called `reset()`/re-created the batch, or `snacks()` was empty | `backToSnacks()` must not reset; ensure snacks survive from the prior generation |
| `restart-clears-state.spec.ts` fails with a strict-mode violation | New button carries `primary-btn` | Use `.ghost-btn` + `data-testid` for "Back to snacks" |
| `progressive-delivery.spec.ts` fails with a strict-mode violation | A second `primary-btn`/`link-btn` inside `.snack-card` | Only Next uses `primary-btn`; Previous uses `.ghost-btn`; index uses `.snack-index__item` |
| Return lands on the first snack instead of the quizzed one | Match done by position, or `selectedSubtopic` not set | Match `snack.subtopicId === selectedSubtopic()?.id`; verify `startQuiz` sets `selectedSubtopic` |
| Blank card after the set changes | Stored index out of range | Clamp `currentIndex` on read; write the clamped value back in `init()` |
| Next cannot pass a FAILED snack | Intended: Next is READY-only (FR-016) | Use the left index to jump to a later READY snack |
