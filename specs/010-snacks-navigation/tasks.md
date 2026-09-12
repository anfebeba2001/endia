---

description: "Task list for Return to Snacks from Results + Snacks Navigation (frontend-only Angular feature over the 001/005/008/009 snack flow)"
---

# Tasks: Return to Snacks from Results + Snacks Navigation

**Input**: Design documents from `/specs/010-snacks-navigation/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Branch**: `010-snacks-navigation`

**Tests**: Playwright E2E is the project's only frontend test surface (no Karma/Jest runner). The two
new specs are included because the feature specification and `plan.md §Testing Strategy` explicitly
require them. Tests are authored first and are expected to fail until their implementation target lands.

## Organization

This is a **frontend/UI-only** Angular 21 feature. The backend is frozen and **no backend tasks exist**
(FR-022/SC-010): no endpoint, DTO, controller, service, prompt, provider, or schema change. The change
set is **3 existing frontend source files modified** (`results.component.{ts,html,scss}`,
`snacks.component.{ts,html,scss}` — 6 files across 2 components), **1 service touched**
(`app-state.service.ts`), and **2 new Playwright specs**. No new component, route, service, npm
dependency, or unit-test runner is introduced.

The tasks follow the plan's milestones (M0–M3) and are grouped into the requested five phases:
**Setup → Foundational → User Stories (US1–US4) → Integration → Polish**. Story labels
(`[US1]`..`[US4]`) provide traceability to `spec.md`; setup/foundational/integration/polish tasks carry
no label and are mapped by the Requirement Coverage table at the end.

### Milestone ↔ story ↔ phase map

| Milestone | Plan phase | Story | Conceptual phase |
|-----------|-----------|-------|------------------|
| — | Setup | none | Setup |
| M0 | Phase 0 — shared navigation state | US1/US3 (foundation) | Foundational |
| M1 | Phase 1 — return to snacks from results | US1 | User Story 1 |
| M2 | Phase 2 — left index + Next/Previous + persistence | US2/US3 | User Story 2 / User Story 3 |
| M3 | Phase 3 — accessibility hardening + tests | US4 | User Story 4 / Integration / Polish |

> **Discovered gap (must fix for FR-004)** — `SnacksComponent.init()` currently early-returns when
> `AppStateService.snacks().length > 0` **without populating `items()`**. Returning to `/snacks` today
> would show the "Warming up the knowledge oven…" creation spinner forever. T010 hydrates `items()`
> from `snacks()` and skips creation/polling; the index is clamped **after** hydration (ordering risk
> flagged in `plan.md §Risks`).

> **Clamp-after-hydrate ordering (flagged)** — `ResultsComponent.backToSnacks()` sets the shared index
> **before** navigation; `SnacksComponent` hydrates `items()` and only then clamps. If clamping ran
> before hydration it could reset the index to 0. T010 encodes the required order.

> **Progressive-delivery interpretation (flagged for orchestrator, not a defect)** — Next is READY-only
> (FR-016), so a `FAILED` middle snack cannot be passed linearly; it stays reachable via the index once
> READY. This is the intended tension between FR-012 and FR-016 documented in `research.md` R4.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Which user story this task belongs to (`US1`–`US4`); absent for setup/foundational/integration/polish
- Base paths (verified against the repo):
  - Frontend source: `frontend/src/app/`
  - Frontend E2E: `frontend/e2e/`
  - Feature docs: `specs/010-snacks-navigation/`

> **Selector-safety contract (FR-006/FR-023)**: the new "Back to snacks" and "Previous" controls use the
> existing global `.ghost-btn` (never `primary-btn`/`link-btn`); index entries use `.snack-index__item`.
> This keeps `.results-footer .primary-btn` (restart), `.snack-card .primary-btn` (Next), and
> `button.link-btn` (back / Retry) each uniquely selectable in Playwright strict mode.

> **Test constraint**: there is **no frontend unit runner**. Verification is Playwright
> (`cd frontend && npx playwright test`) plus `npm run build` for type-checking. No backend tests are
> added or changed.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify the baseline and confirm the frozen scope/selector surfaces before any behavior change. No production code is written here.

- [X] T001 Verify the baseline: run `cd frontend && npm run build` (green) and `cd frontend && npx playwright test` (existing suite green); record the baseline spec/test counts. Required to prove SC-009 (no regression) later. (FR-021, SC-009)
- [X] T002 [P] Confirm the frontend-only freeze: verify `frontend/package.json` / `package-lock.json` need no edit, `git status` shows no `backend/` change, and there is no schema/migration/DTO delta (consistent with `contracts/backend-unchanged.md` and `data-model.md §6`). Record the finding. (FR-022, SC-010)
- [X] T003 [P] Confirm the existing selector contract: in the current tree, record that `.results-footer .primary-btn`, `.snack-card .primary-btn`, and `button.link-btn` (snacks page) each match exactly one element; this is the strict-mode baseline the new controls must not disturb. (FR-006, FR-023)

**Checkpoint**: Baseline green, frontend-only freeze and selector contract confirmed — foundational state work can begin.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: M0 — make `AppStateService.currentSnackIndex` the single source of truth and expose a clamped read. This blocks US1 (return index) and US3 (persistence). No user story is complete without it.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T004 [P] In `frontend/src/app/services/app-state.service.ts`, add the navigation helpers `setSnackIndex(index: number): void` and `snackIndexForSubtopic(subtopicId: string | null): number` (returns the index in `snacks()` whose `subtopicId` matches, or `-1`); keep `setBatch()` and `reset()` resetting `currentSnackIndex` to `0` unchanged. (FR-013, FR-003, FR-018, data-model.md §1)
- [X] T005 [P] In `frontend/src/app/pages/snacks/snacks.component.ts`, remove the component-local `currentIndex = signal(0)` and add a clamped `readonly currentIndex = computed(...)` that reads `appState.currentSnackIndex()` and clamps to `[0, items().length - 1]` (returns `0` when empty); keep `currentSnack`/`nextSnack` derived from it. (FR-013, FR-012, FR-019, data-model.md §2)

**Checkpoint**: Shared index store + clamped read exist; US1/US3 can now read and write one source of truth.

---

## Phase 3: User Story 1 — Return to the snacks from the quiz results (Priority: P1) 🎯 MVP

**Goal**: Add a "Back to snacks" action beside "Start a new theme" that returns to `/snacks` with the already-generated snacks preserved and the quizzed subtopic's snack active — no new batch, no reset.

**Independent Test**: Complete a quiz, click "Back to snacks", and confirm `/snacks` renders the previously generated snacks with the quizzed subtopic's snack active and no `POST /api/batches`. Also confirm "Start a new theme" still resets to `/`.

### Tests for User Story 1 (write first, expect FAIL) ⚠️

- [X] T006 [P] [US1] Create `frontend/e2e/results-back-to-snacks.spec.ts` (NEW): drive the full flow with `setupAllMocks` to `/results`; assert both completion states ("Quiz complete" via `setupQuizSummaryMock`, "Perfect score!" via `setupQuizSummaryMock({ correctAnswers: 4 })`) render `.results-footer [data-testid="back-to-snacks"]` ("Back to snacks") and the restart `.primary-btn`; record `POST /api/batches` calls and assert the count does not increase after clicking "Back to snacks"; assert the quizzed subtopic's `.snack-card__chip` is active with the matching `.snack-index__item[aria-current="true"]`; assert no `.loading`/empty state for existing snacks; assert "Start a new theme" still navigates to `/`. (FR-001..FR-006, FR-018, SC-001..SC-003, SC-009)

### Implementation for User Story 1

- [X] T007 [US1] In `frontend/src/app/pages/results/results.component.ts`, add `backToSnacks(): void` that resolves the subtopic id from `appState.selectedSubtopic()?.id` (falling back to `appState.quizSubTopicId()`), sets `appState.currentSnackIndex` to the matching `snacks[].subtopicId` index when found (else leaves it unchanged), and calls `router.navigate(['/snacks'])` — **no `appState.reset()`, no HTTP call**. (FR-002, FR-003, FR-018, plan.md AD-1)
- [X] T008 [US1] In `frontend/src/app/pages/results/results.component.html`, add `<button class="ghost-btn" data-testid="back-to-snacks" (click)="backToSnacks()">Back to snacks</button>` inside the existing `.results-footer`, next to the restart `.primary-btn`; keep the restart button's `class="primary-btn"` and `restart()` binding unchanged. (FR-001, FR-006, FR-023)
- [X] T009 [P] [US1] In `frontend/src/app/pages/results/results.component.scss`, change `.results-footer` to a centered flex row with a gap so both actions sit side by side in both completion states. (FR-001, SC-001)
- [X] T010 [P] [US1] In `frontend/src/app/pages/snacks/snacks.component.ts`, fix the `init()` return path: when `appState.snacks().length > 0`, map each `Snack` to a READY `BatchItem` (preserving `subtopicId`/`subtopicName`/`snack`), set `items.set(...)`, set `creating=false` and `polling=false`, **then** clamp/write back the stored index, and return **without** calling `createBatch` or starting a poll. (FR-002, FR-004, FR-012, FR-018, plan.md AD-2 — discovered gap)

**Checkpoint**: US1 fully functional and independently testable — the learning loop closes with no new generation.

---

## Phase 4: User Story 2 — Browse every snack via a left-side index (Priority: P1)

**Goal**: Render a left-side index listing every snack in the current set, labelled by subtopic name, with the active entry highlighted (`aria-current="true"` + active class), READY entries clickable to jump, and non-READY/FAILED entries disabled.

**Independent Test**: Generate a set, confirm the index lists every snack by name, exactly one entry is highlighted, and clicking a READY entry makes it active and updates the card; non-READY entries cannot be selected.

### Tests for User Story 2 (write first, expect FAIL) ⚠️

- [X] T011 [US2] Create `frontend/e2e/snacks-navigation.spec.ts` (NEW) with the index coverage: with a 3-snack COMPLETE batch assert `.snack-index__item` count is 3 and each label equals the `subtopicName`; assert exactly one `.snack-index__item--active[aria-current="true"]`; click a READY entry and assert the `.snack-card__chip` and highlight update together; with one GENERATING and one FAILED item assert those entries are `disabled` and clicking them changes nothing. (FR-007..FR-010, SC-004)

### Implementation for User Story 2

- [X] T012 [US2] In `frontend/src/app/pages/snacks/snacks.component.ts`, add `selectIndex(i: number): void` that sets `appState.currentSnackIndex` only when `items()[i]?.snackStatus === 'READY'` (guard even though the button is disabled), and add any index-supporting computeds. (FR-009, FR-010, FR-019)
- [X] T013 [US2] In `frontend/src/app/pages/snacks/snacks.component.html`, wrap the existing card area in a two-column layout and add `<nav class="snack-index" aria-label="Snack list">` with an `<ol>` and `@for (item of items(); track item.subtopicId; let i = $index)` rendering a `<button type="button" class="snack-index__item">` per snack: text `item.subtopicName`, `[class.snack-index__item--active]="i === currentIndex()"`, `[attr.aria-current]="i === currentIndex() ? 'true' : null"`, `[disabled]="item.snackStatus !== 'READY'"`, `(click)="selectIndex(i)"`. (FR-007..FR-010, FR-020, plan.md AD-4)
- [X] T014 [P] [US2] In `frontend/src/app/pages/snacks/snacks.component.scss`, add the two-column `.snacks-layout`, the `.snack-index` panel (sticky/scrollable list), and the `.snack-index__item` base/active/disabled entry styles. (FR-007, FR-008, FR-020)

**Checkpoint**: The set is browsable; the index and card share the single clamped index source.

---

## Phase 5: User Story 3 — Step through snacks with Next/Previous and a persistent active index (Priority: P1)

**Goal**: Add Previous/Next controls that move exactly one position, keep the active index in `AppStateService.currentSnackIndex` across navigation, preserve READY-only Next, and make "Start Quiz" use the active snack's subtopic.

**Independent Test**: Move to a middle snack, start a quiz, return to `/snacks` (or navigate away and back), and confirm the same snack is active; Next/Previous move exactly one and are safe at boundaries; "Start Quiz" targets the active snack's `subtopicId`.

### Tests for User Story 3 (write first, expect FAIL) ⚠️

- [X] T015 [US3] Extend `frontend/e2e/snacks-navigation.spec.ts`: Next/Previous move the active index by exactly one and update the card; Previous is disabled at index 0; Next is disabled at the last index; repeated Next reaches every READY snack with no blank card; the active index survives a `/snacks → /quiz → /results → /snacks` round trip and a plain navigate-away-and-back; clicking "Start Quiz" on a non-first active snack uses that snack's `subtopicId`; assert Next still cannot advance onto a GENERATING/FAILED item (progressive delivery preserved). (FR-011..FR-018, SC-005, SC-006)

### Implementation for User Story 3

- [X] T016 [US3] In `frontend/src/app/pages/snacks/snacks.component.ts`, add `hasPrev` (`currentIndex() > 0`), `hasNext` (`currentIndex() < items().length - 1`), `canNext` (`hasNext() && nextSnack()?.snackStatus === 'READY'`), a private `goTo(index)` that rejects out-of-range targets and writes `appState.currentSnackIndex`, plus `prev()` (only when `hasPrev()`, no READY requirement) and `next()` (only when `canNext()`); replace the old local-index `next()`. (FR-011, FR-012, FR-015, FR-016, FR-019, plan.md AD-3)
- [X] T017 [US3] In `frontend/src/app/pages/snacks/snacks.component.ts`, make `startQuiz` derive the subtopic from the active snack (`currentSnack()?.snack?.subtopicId`) rather than a passed/stale id, set `selectedSubtopic`, and navigate to `/quiz`. (FR-017, plan.md AD-3)
- [X] T018 [US3] In `frontend/src/app/pages/snacks/snacks.component.html`, add the Previous (`class="ghost-btn nav-btn--prev"`, `[disabled]="!hasPrev()"`, `(click)="prev()"`) and Next (`class="primary-btn nav-btn--next"`, `[disabled]="!canNext()"`, `(click)="next()"`) controls inside `.snack-card__footer`; preserve the existing cooking-label / "All snacks served" affordances and keep only Next carrying `primary-btn`. (FR-011, FR-012, FR-016, FR-023)
- [X] T019 [US3] In `frontend/src/app/pages/snacks/snacks.component.ts`, clamp/write back `appState.currentSnackIndex` after hydration and whenever `items()` changes (e.g. in `applyStatus`/`mergeItem`) so a stale or out-of-range stored index never renders a blank card. (FR-012, SC-005, data-model.md §5)

**Checkpoint**: Navigation is complete and the active snack survives the round trip; progressive delivery is preserved.

---

## Phase 6: User Story 4 — Accessible, consistent navigation controls (Priority: P2)

**Goal**: Every new control is a real interactive element with an accessible name, keyboard reachable/activatable with a visible focus indicator; after any interaction sequence the card, highlight, and enabled states agree.

**Independent Test**: Keyboard-tab through index entries, Next, Previous, and "Back to snacks"; activate with Enter/Space; confirm a visible `:focus-visible` indicator; after mixed interactions confirm the card, highlighted entry, and enabled states all agree.

### Tests for User Story 4 (write first, expect FAIL) ⚠️

- [X] T020 [US4] Extend `frontend/e2e/snacks-navigation.spec.ts` with the accessibility/consistency coverage: tab to an index entry and a nav button and assert a visible `:focus-visible` outline and Enter/Space activation; after a mixed sequence of Next/Previous/index clicks assert the `.snack-card__chip` text, the single `aria-current="true"` entry, and the Next/Previous `disabled` states are mutually consistent. (FR-019, FR-020, SC-007, SC-008)

### Implementation for User Story 4

- [X] T021 [P] [US4] In `frontend/src/app/pages/snacks/snacks.component.scss`, add explicit `:focus-visible` rules for `.snack-index__item` and the nav buttons, plus disabled styling that clearly distinguishes non-selectable entries. (FR-020, SC-008)
- [X] T022 [US4] In `frontend/src/app/pages/snacks/snacks.component.html`, verify every new control is a `<button type="button">` with a text accessible name; ensure `aria-current="true"` is present only on the active entry and non-READY entries use the native `disabled` attribute. (FR-019, FR-020)
- [X] T023 [US4] In `frontend/src/app/pages/results/results.component.html`/`.scss`, ensure the new "Back to snacks" control is `<button type="button">` with a text accessible name and a visible `:focus-visible` indicator (inherited or explicit), without adding any `primary-btn`/`link-btn` class. (FR-020, FR-023)

**Checkpoint**: All four stories are independently functional and accessible.

---

## Phase 7: Integration

**Purpose**: Reconcile contracts/docs, run the new specs and the named regression suite, and confirm the frozen backend and selector surfaces.

- [X] T024 [P] Verify/update `specs/010-snacks-navigation/contracts/ui-navigation.md` so the component API, DOM/ARIA selectors, and state invariants match the shipped implementation (including `data-testid="back-to-snacks"`, `.snack-index__item`, `.snack-card .ghost-btn`). (FR-001, FR-006..FR-010, FR-020, FR-023)
- [X] T025 [P] Verify/update `specs/010-snacks-navigation/contracts/backend-unchanged.md`, `data-model.md`, and `quickstart.md` against the shipped behavior; confirm no HTTP/DTO/schema change and that the manual steps still reproduce the feature. (FR-022, SC-010)
- [X] T026 Run the two new specs and confirm green: `cd frontend && npx playwright test e2e/results-back-to-snacks.spec.ts e2e/snacks-navigation.spec.ts`. Depends on T006, T011, T015, T020 and their implementation targets. (FR-001..FR-020, SC-001..SC-008)
- [X] T027 Run the named regression suite and confirm green **unchanged**: `cd frontend && npx playwright test e2e/restart-clears-state.spec.ts e2e/progressive-delivery.spec.ts e2e/back-navigation.spec.ts e2e/loading-states.spec.ts`. (FR-005, FR-006, FR-016, FR-021, FR-023, SC-009)
- [X] T028 [P] Type-check/build: run `cd frontend && npm run build` and confirm the template/method changes compile with no errors. (SC-009)
- [X] T029 [P] Confirm the backend is frozen: `git status` shows no `backend/` change; optionally run `cd backend && mvn -q -DskipTests package`. Record the result. (FR-022, SC-010)

**Checkpoint**: New behavior verified; regression contract and backend freeze confirmed.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Final audits, full-suite run, manual verification, and checklist sign-off.

- [X] T030 [P] Selector-collision audit: run the app and assert `.results-footer .primary-btn`, `.snack-card .primary-btn`, and `button.link-btn` (snacks page) each match exactly one element in strict mode; confirm index entries never carry `link-btn` and "Back to snacks"/"Previous" never carry `primary-btn`. (FR-006, FR-023)
- [X] T031 [P] No-extra-request audit: confirm the return path and all navigation interactions issue **zero** HTTP requests (no `POST /api/batches`, no `/api/snacks/generate`); the return flow reuses in-memory `AppStateService.snacks`. (FR-002, SC-002)
- [X] T032 Run the full Playwright suite: `cd frontend && npx playwright test` and confirm all specs (including the wider set: `polling-termination`, `retry-bound`, `manual-retry`, `snack-wait-messages`, `bounded-snack-settle`, `happy-path`, `perfect-score`, `batch-empty-state`) are green. (FR-021, SC-009)
- [X] T033 Execute the `quickstart.md` manual verification: US1 (both completion states, return with no new batch, quizzed snack active), US2 (index lists all, one highlight, click-to-jump, disabled non-READY), US3 (Next/Previous ±1, boundaries, persistence across the round trip, Start Quiz uses the active subtopic), US4 (keyboard/focus/consistency). Record results. (SC-001..SC-008)
- [X] T034 Final validation: mark every completed task `[x]` in this file, confirm **all** checklists under `specs/010-snacks-navigation/checklists/` have **0 unchecked items**, and record the final task/phase counts in the Summary section. (SC-009)

**Checkpoint**: Feature complete, verified, and signed off.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup — **blocks all user stories** (shared index store + clamped read).
- **User Stories (Phases 3–6)**: Depend on Foundational. US1 and US2 are independent; US3 builds on the shared index and the index UI; US4 hardens US1–US3. They can be staffed in parallel once Foundational completes.
- **Integration (Phase 7)**: Depends on the relevant implementation tasks landing.
- **Polish (Phase 8)**: Depends on all user stories + Integration.

### Within each user story

- Tests (Playwright spec tasks) MUST be authored and failing before their implementation targets.
- The component `.ts` is the source of truth for computeds/methods; `.html` binds them; `.scss` styles them — implement in that order.

### Sequential vs parallel rules

- A task may be marked `[P]` only when it edits a **different file** than other in-flight tasks and has no dependency on an incomplete task.
- Tasks editing `snacks.component.ts` (T005, T010, T012, T016, T017, T019) are **never** `[P]` relative to each other.
- Tasks editing `snacks.component.html` (T013, T018, T022) are **never** `[P]` relative to each other.
- Tasks editing `snacks.component.scss` (T014, T021) are **never** `[P]` relative to each other.
- Tasks editing `snacks-navigation.spec.ts` (T011, T015, T020) are **never** `[P]` relative to each other.
- `app-state.service.ts` (T004) and `snacks.component.ts` (T005) are different files → parallel.
- `results.component.ts` (T007), `results.component.html` (T008), `results.component.scss` (T009) are different files, but T008 binds the method added by T007 → T007 before T008; T009 is independent of both.

### Parallel opportunities

- **Setup**: T002 and T003 are independent concerns — parallel.
- **Foundational**: T004 (service) and T005 (component) are different files — parallel.
- **US1**: T006 (new spec), T009 (results scss), and T010 (snacks ts) are different files and can proceed together; T007 → T008.
- **US2**: T014 (scss) is parallel with the `snacks.component.ts`/`.html` chain.
- **US3**: the `snacks.component.ts` chain is sequential; T018 (html) follows T016.
- **US4**: T021 (scss) and T023 (results html/scss) are parallel with the snacks markup verification.
- **Integration**: T024 and T025 (separate docs) are parallel; T028 (build) and T029 (backend freeze) are parallel with T026/T027.
- **Polish**: T030 and T031 are parallel with the full-suite run T032.

---

## Parallel Example: User Story 1

```bash
# Author the US1 spec and the independent-file edits together:
Task: "T006 Create frontend/e2e/results-back-to-snacks.spec.ts"
Task: "T009 Update frontend/src/app/pages/results/results.component.scss (.results-footer flex row)"
Task: "T010 Fix SnacksComponent.init() return-path hydration in frontend/src/app/pages/snacks/snacks.component.ts"

# Then wire the results component:
Task: "T007 Add ResultsComponent.backToSnacks() in results.component.ts"
Task: "T008 Add the .ghost-btn[data-testid=back-to-snacks] to results.component.html"  # after T007
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete Setup (T001–T003).
2. Complete Foundational (T004–T005) — the shared clamped index.
3. Author the failing US1 spec (T006).
4. Implement US1 (T007–T010): "Back to snacks" + return-path hydration.
5. **STOP and VALIDATE**: `npx playwright test e2e/results-back-to-snacks.spec.ts` plus the restart/progressive/back-nav/loading regression suite.
6. Deploy/demo if ready — the learning loop is closed with no backend change.

### Incremental Delivery

1. Foundational → shared index ready.
2. US1 → return path (MVP).
3. US2 → browsable index.
4. US3 → Next/Previous + persistence.
5. US4 → accessibility hardening.
6. Integration/Polish → contracts, regression, build, manual verification, checklists.

### Parallel Team Strategy

Once Foundational completes: Developer A takes US1 (results + hydration), Developer B takes US2/US3
(snacks index + navigation), Developer C takes US4 (a11y) — then Integration/Polish together.

---

## Requirement Coverage

| Spec ID | Covered by |
|---------|-----------|
| FR-001 | T006, T008, T009 |
| FR-002 | T006, T007, T010, T031 |
| FR-003 | T004, T006, T007 |
| FR-004 | T006, T010 |
| FR-005 | T006, T008, T027 |
| FR-006 | T003, T008, T027, T030 |
| FR-007 | T011, T013, T014 |
| FR-008 | T011, T013, T014 |
| FR-009 | T011, T012, T013 |
| FR-010 | T011, T012, T013 |
| FR-011 | T015, T016, T018 |
| FR-012 | T005, T015, T016, T018, T019 |
| FR-013 | T004, T005, T016 |
| FR-014 | T004, T007, T010, T015 |
| FR-015 | T015, T016, T018 |
| FR-016 | T015, T016, T018, T027 |
| FR-017 | T015, T017 |
| FR-018 | T004, T006, T007, T010 |
| FR-019 | T005, T016, T020, T022 |
| FR-020 | T011, T013, T014, T020, T021, T022, T023 |
| FR-021 | T001, T027, T032 |
| FR-022 | T002, T025, T029 |
| FR-023 | T003, T008, T018, T027, T030 |
| SC-001 | T006, T009, T033 |
| SC-002 | T006, T007, T010, T031 |
| SC-003 | T006, T007 |
| SC-004 | T011, T013 |
| SC-005 | T005, T015, T016, T019 |
| SC-006 | T004, T010, T015 |
| SC-007 | T005, T016, T020, T022 |
| SC-008 | T011, T020, T021, T023 |
| SC-009 | T001, T026, T027, T028, T032, T034 |
| SC-010 | T002, T025, T029 |

**No FR or SC is left uncovered.** All 23 FRs and all 10 SCs map to at least one task.

---

## Summary

- **Total tasks: 34** — **all 34 complete** (`[X]`), 0 remaining.
- **Phase breakdown** (completed/total):
  - Setup: **3/3** (T001–T003)
  - Foundational: **2/2** (T004–T005)
  - User Story 1 (return to snacks): **5/5** (T006–T010)
  - User Story 2 (left index): **4/4** (T011–T014)
  - User Story 3 (Next/Previous + persistence): **5/5** (T015–T019)
  - User Story 4 (accessibility/consistency): **4/4** (T020–T023)
  - Integration: **6/6** (T024–T029)
  - Polish: **5/5** (T030–T034)
- **Verification**: `npm run build` green; full Playwright suite **67 passed** (48 pre-existing + 19 new: 6 `results-back-to-snacks.spec.ts` + 13 `snacks-navigation.spec.ts`); named regression suite green unchanged; backend untouched (`git status` shows no `backend/` change; no package/schema delta).
- **Checklists**: `requirements.md`, `implementation.md`, `test.md`, `ux.md` all have **0 unchecked items**.
- **Story task counts** (labeled tasks): US1 = 5, US2 = 4, US3 = 5, US4 = 4; unlabeled setup/foundational/integration/polish = 16.
- **Parallel opportunities**: 12 tasks marked `[P]` across Setup/Foundational/User Stories/Integration/Polish.
- **New files**: `frontend/e2e/results-back-to-snacks.spec.ts`, `frontend/e2e/snacks-navigation.spec.ts`.
- **Modified files**: `frontend/src/app/pages/results/results.component.{ts,html,scss}`, `frontend/src/app/pages/snacks/snacks.component.{ts,html,scss}`, `frontend/src/app/services/app-state.service.ts`.
- **No backend tasks** — the backend is frozen (FR-022/SC-010).

## Notes

- `[P]` tasks touch different files and have no dependency on incomplete tasks; tasks editing the same
  `snacks.component.*` file or the same spec file are never `[P]` relative to each other.
- Every task carries exact file paths (create or modify) and a clear done-definition.
- The frontend has no unit-test runner; verification is Playwright plus `npm run build`.
- The discovered hydration gap (T010) is the single most important correctness fix for FR-004 and must
  clamp **after** hydrating `items()`.
- Checklists live in `specs/010-snacks-navigation/checklists/` and must end with 0 unchecked items at
  implementation completion (T034).
