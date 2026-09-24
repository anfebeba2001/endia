---
description: "Task list for feature 017-custom-keyword-create-snack"
---

# Tasks: "Create snack" from a Custom Keyword's Generated Meaning (Task 12)

**Input**: Design documents from `/specs/017-custom-keyword-create-snack/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md, e2e-test-plan.md

**Tests**: Tests are **required** for this feature (spec §Success Criteria, plan.md §Testing Strategy and e2e-test-plan.md explicitly demand them). Test tasks are written **first**, before the corresponding implementation, per TDD ordering; they MUST FAIL (assertion) before the implementation task lands. The frontend's **only** runner is **Playwright E2E** (`frontend/package.json` has `@playwright/test`, no Karma/Jest/Vitest); `npm run build` is the type-check gate. There is **no frontend unit runner**, so the pure capture/state logic is covered through route-mocked E2E fixtures (research.md R9).

**Backend-change finding (encoded, do not re-litigate)**: **NO backend change.** The existing `POST /api/meaning-snacks/generate` (`{themeId, subtopicId, term, definition}`) and `GET /api/meaning-snacks?themeId=` fully cover this feature. This is **entirely a frontend change** — two source files (+ one template binding) and one new E2E spec (+ a helper addition only if needed). No entity, no schema change, no endpoint, no DTO, no service change, no new dependency.

**Capture-point decision (encoded, do not re-litigate)**: capture the source subtopic at `onDefineRequested` (card-open) time into the existing `activeKeywordSubtopicId` signal — regular entry → `subtopicId`; meaning entry → `sourceSubtopicId`; otherwise `null` (spec Clarification #1, Option A; research.md R1; plan.md §Frontend §1).

**Reuse decision (encoded)**: reuse the existing `onGenerateRequested` semantics unchanged (one-in-flight guard keyed by `${subtopicId}::${term.toLowerCase()}`, loading state, append-on-success deduped by `id`, `markGenerated`, error via `ErrorService.getMessage`, nothing added on failure) and the feature-014 action slot in `keyword-definition-card` (relabeled for custom mode) (plan.md §Frontend §2–§5; research.md R4/R5/R6).

**Organization**: Tasks are grouped by user story (US1 create-from-custom-keyword P1, US2 quiz-free & distinct P1, US4 safe rendering P1, US3 persistence/reload P2, US5 non-regression P2) so each story can be implemented and verified independently, and map to the plan's milestones **M1** (capture source subtopic + availability), **M2** (action-slot relabel + wiring), **M3** (persistence / quiz-free / security confirmation), **M4** (E2E + regression). The **Foundational** phase carries the shared additive card contract that every story exercises; **US1 is the MVP** that unblocks US2–US5.

**⚠️ HIGHEST-RISK TASK**: **T007** — capturing the **correct source subtopic** at `onDefineRequested` into `activeKeywordSubtopicId` (regular `subtopicId` / meaning `sourceSubtopicId` / `null`) and keying `activeGenerateState` off that captured id. This is the core risk (plan.md §Risks / research.md R1); a wrong capture attaches the snack to the wrong subtopic. The regular branch of `generateEnabled`/`activeGenerateState` MUST remain behavior-equivalent so feature 014 is not regressed. Coupled risks: T008 (the single-action-per-card rule) and T005 (the E2E POST-body/subtopic assertions for both a regular and a meaning active entry).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Which user story this task belongs to (US1–US5)
- Every task includes exact file path(s) and the FR/SC references it satisfies

## Path Conventions

- **Frontend**: `frontend/src/app/...`, `frontend/e2e/...`
- **Backend**: `backend/src/...` (**unchanged** — only run for regression evidence)
- **Feature docs**: `specs/017-custom-keyword-create-snack/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the narrow, backend-free scope and record the pre-change baseline before any story work.

- [X] T001 [P] Confirm no backend/schema/endpoint change is required and no new runtime dependency is added: verify `backend/pom.xml` and `frontend/package.json` are unchanged, that no entity/column/index/table is added or altered, and that Hibernate `ddl-auto=update` issues **no DDL** for this feature (plan.md §Backend finding, data-model.md §2, quickstart.md §1); record the pre-change baseline by running `cd frontend && npm run build` and `cd frontend && npx playwright test` (baseline for FR-016, SC-007, SC-008).
- [X] T002 [P] Confirm the reused feature-014 contract and the exact frontend integration call sites against the current code state before editing: read `specs/017-custom-keyword-create-snack/contracts/meaning-snack-contract.md` and verify `frontend/src/app/pages/snacks/snacks.component.ts` (`generateEnabled`, `activeGenerateState`, `onDefineRequested`, `onGenerateRequested`, `activeKeywordSubtopicId`) and `frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.{ts,html}` match the plan's assumptions (plan.md §Exact integration call sites) (FR-004, FR-016, SC-007).

---

## Phase 2: Foundational (Blocking Prerequisites) — Milestone M2 (shared card contract)

**Purpose**: The additive, default-`false` card input that all card-state work depends on. It is a **no-op for the results/history/regular-keyword surfaces**, so feature-013/014 behavior and E2E assertions are preserved (plan.md §Frontend §2; research.md R4; data-model.md §3.3).

**⚠️ CRITICAL**: US1's label branch (T008) and every verification story (US2/US3/US4/US5) render through this contract. **No behavior change** — the input only selects the action label; availability comes from `generateEnabled` and state from `generateState` (both already existing inputs).

- [X] T003 [P] Add `readonly customDefinition = input<boolean>(false);` to `frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.ts` (additive; default `false` so feature-013/014 surfaces are unchanged). Leave the feature-013 open/dismiss/focus code and the existing `generateEnabled`/`generateState`/`generateError`/`generateRequested` declarations untouched (FR-001, FR-003, data-model.md §3.3).
- [X] T004 Bind `[customDefinition]="customDefinition()"` on the single `<app-keyword-definition-card>` in `frontend/src/app/pages/snacks/snacks.component.html` (the page already owns the `customDefinition` signal). **Depends on T003** (the input must exist before binding) (FR-003).

**Checkpoint**: The shared definition card carries the custom-mode flag; every existing surface renders identically because the input defaults to `false`.

---

## Phase 3: User Story 1 — Turn a custom keyword's generated meaning into a snack (Priority: P1) 🎯 MVP

**Goal**: Once feature 016's "Define" completes and the generated meaning is displayed, the card offers exactly one **Create snack** action; activating it generates a meaning snack from the custom term + generated meaning via the existing feature-014 path, showing a loading state, adding exactly one backbone entry on success, and showing a meaningful error adding nothing on failure. This phase covers milestones **M1 (capture + availability)** and **M2 (action-slot relabel + wiring)**.

**Independent Test**: Define a custom term, wait for the generated meaning, confirm exactly one `Create snack` action; activate it and confirm loading → one backbone meaning snack; repeat with a forced 503 and confirm a meaningful error with no entry added; rapid-double-activate and confirm one request/one entry; re-define the same term and confirm the already-generated (`done`) state.

### Tests for User Story 1 (write first, must FAIL) ⚠️

- [X] T005 [P] [US1] Create `frontend/e2e/custom-keyword-create-snack.spec.ts` (route-mocked) covering the US1 blocks from e2e-test-plan.md §1–§2: (1.1) with a **delayed** definitions mock, while defining the card offers **no** `.keyword-card__generate`, and after it resolves exactly **one** action labelled `Create snack`; (1.2) a 503 definitions mock shows a `role="alert"` error and **no** Create snack action and adds no entry; (1.3) a regular highlighted-keyword card still shows `Generate snack` (not `Create snack`); (2.1) activate `Create snack` on a regular active entry and assert the captured `POST /api/meaning-snacks/generate` body is `{themeId, subtopicId: 'sub-001', term, definition}` (definition = generated meaning), then `Added to your snacks` and exactly one `.snack-index__item--meaning`; (2.2) a delayed meaning-snacks mock shows the loading state (`Creating…` / `.keyword-card__action-status`) then `done`; (2.3) two synchronous clicks via `evaluate` issue exactly **one** generate call and **one** meaning entry; (2.4) a 503 meaning-snacks mock shows `.keyword-card__error` `role="alert"` with non-empty user-safe text, **no** meaning entry, backbone count unchanged; (2.5) after creating and dismissing (Escape), re-defining the same term shows the `done` state, exactly one entry, and no extra generate request; **and the server-side idempotent branch (CHK029): a fresh create request whose mock returns `{alreadyExisted: true, meaningSnack: <same snack>}` still resolves to `done` with exactly one backbone entry and no duplicate**; (2.6) with a seeded meaning snack (`sourceSubtopicId: 'sub-003'`) selected, defining a term from its content and creating asserts `subtopicId === 'sub-003'` (Option A). Use `setupAllMocks` + `overrideBatchWithSnack` + `goToSnackCard` + `setupDefinitionsMock` + `setupMeaningSnacksMock` (FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008, FR-009, FR-010, SC-001, SC-002, SC-003).
- [X] T006 [P] [US1] Extend `frontend/e2e/helpers.ts` **only if** a custom-create fixture helper is needed; reuse the existing `setupAllMocks`, `overrideBatchWithSnack`, `goToSnackCard`, `meaningSnack`, `setupMeaningSnacksMock`, `setupDefinitionsMock` (the existing meaning-snack + definition mocks already cover the flow). Add a benign default only if `setupAllMocks` lacks one, keeping every existing spec deterministic (FR-004, SC-007).

### Implementation for User Story 1 — M1 (capture + availability) + M2 (relabel + wiring)

- [X] T007 [US1] **⚠️ HIGHEST-RISK TASK** — Edit `frontend/src/app/pages/snacks/snacks.component.ts`: (a) in `onDefineRequested()`, before opening the card, capture the active entry's source subtopic into `activeKeywordSubtopicId` (`entry?.kind === 'regular' ? entry.subtopicId : entry?.kind === 'meaning' ? entry.sourceSubtopicId : null`); (b) add a custom branch to `generateEnabled` that returns `!!themeId && !definitionLoading && !definitionError && !!activeKeyword()?.definition && !!activeKeywordSubtopicId()` when `customDefinition()` is true (and keep the regular branch byte-for-byte equivalent: `!!themeId && !customDefinition() && currentEntry()?.kind === 'regular'`); (c) rewrite `activeGenerateState` to key off the captured `activeKeywordSubtopicId()` (`idle` when no keyword/subtopic; else `generateStates()[generateKey(subtopicId, term)]`, falling back to `meaningSnackService.hasGenerated(...) ? 'done' : 'idle'`). Reuse `onGenerateRequested` **unchanged**. Do **not** alter batch/poll/retry/progressive-delivery paths (FR-002, FR-004, FR-005, FR-006, FR-007, FR-008, FR-009, FR-010, SC-001, SC-002, SC-003, data-model.md §3.1).
- [X] T008 [US1] Edit `frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.html`: relabel the **existing single** `.keyword-card__generate` action slot using `customDefinition()` — idle `Create snack` (custom) / `Generate snack` (regular), loading `Creating…` (custom) / `Generating…` (regular); keep the `done` label `Added to your snacks` and the `idle｜loading｜done` states, the live region, and the `disabled`/`aria-disabled` handling unchanged. Do **not** add a second control (FR-001, FR-003, FR-006, SC-001). **Depends on T003.**

**Checkpoint**: Defining a custom term yields exactly one `Create snack` action once a valid meaning exists; activating it reuses the feature-014 flow (loading → one backbone entry, or a meaningful error adding nothing); the regular keyword card still shows `Generate snack`.

---

## Phase 4: User Story 2 — The created snack is quiz-free and visually distinct, per feature 014 (Priority: P1)

**Goal**: The created snack is a feature-014 `MeaningSnack` (`kind: 'MEANING'`) — visually distinct from regular snacks and structurally incapable of a quiz (no quiz generated/prefetched/stored/offered, no `Start Quiz`). This phase covers milestone **M3 (confirmation, no code)**.

**Independent Test**: Create a meaning snack from a custom keyword, select it in the backbone, and confirm `.snack-card--meaning` + the `Meaning snack` badge, a distinct border, and **no** `.quiz-btn`; select a regular snack and confirm its `.quiz-btn` routes to `/quiz`; walk every entry and confirm the meaning entry never triggers a quiz and always shows content.

### Tests for User Story 2 (write first, must FAIL) ⚠️

- [X] T009 [P] [US2] Extend `frontend/e2e/custom-keyword-create-snack.spec.ts` with the quiz-free/distinct block from e2e-test-plan.md §3: (3.1) after creating, select the created entry and assert it renders `.snack-card--meaning` with the `Meaning snack` badge, a top-border colour differing from a regular card, and **no** `.quiz-btn`; (3.2) select the regular entry and assert `.snack-card .quiz-btn` is visible and clicking it routes to `/quiz` (unchanged); (3.3) walk every backbone entry and assert the meaning entry never triggers a quiz request/route and always shows content (never a blank card) (FR-011, FR-012, FR-013, SC-004).

### Verification for User Story 2 (no code — confirmation pass)

- [X] T010 [US2] Confirm the created snack is the reused `MeaningSnack` kind and is never treated as a quiz-bearing regular snack: verify the backbone projects it as a `MeaningEntry` rendered by the existing `snack-card--meaning` branch with no `.quiz-btn`, that it is never stored in `AppState.snacks()` nor passed to any batch/quiz/prefetch path, and that no quiz is generated/stored for it (review `frontend/src/app/pages/snacks/snacks.component.ts` + `.html`, `frontend/src/app/models/batch.model.ts`, `frontend/src/app/services/meaning-snack.service.ts`; backend `MeaningSnack` has no quiz fields) (FR-011, FR-012, FR-013, SC-004).

**Checkpoint**: The custom meaning snack reads as the same quiz-free, distinct kind as the other meaning snacks; the regular snack keeps its quiz affordance.

---

## Phase 5: User Story 4 — Custom term and generated meaning are always rendered as safe, escaped text (Priority: P1)

**Goal**: The custom term (document text) and generated meaning (model output) are untrusted and rendered as plain escaped text through the feature-012/013/014/015/016 safe model — no `[innerHTML]`, no `DomSanitizer`. This phase covers the security portion of milestone **M3**.

**Independent Test**: Define a term and force a generated meaning containing `<script>`, `<img onerror=...>`, `javascript:` links, `<iframe>`, and `<style>`; activate `Create snack`; confirm nothing executes and only inert, readable text appears in the card and the backbone.

### Tests for User Story 4 (write first, must FAIL) ⚠️

- [X] T011 [P] [US4] Extend `frontend/e2e/custom-keyword-create-snack.spec.ts` with the security block from e2e-test-plan.md §5: (5.1) a definitions mock whose `definition` contains `<script>`, `<img onerror>`, `javascript:`, `<iframe>`, `<style>` and a selected term containing markup-like text — assert `window.__xss` stays `undefined`, no `script/img/iframe/style/a` nodes exist inside `.keyword-card`, and readable escaped text is preserved; (5.2) a created snack whose mocked `meaningSnack` content (`concept`/`metaphor`/`whyItMatters`) contains the payloads — select the created entry and assert `window.__xss` stays `undefined`, no `script/img/iframe/style/a` nodes inside `.snack-card--meaning`, and readable text preserved (FR-017, FR-018, FR-019, SC-006).

### Implementation for User Story 4

- [X] T012 [US4] Audit the feature diff (`frontend/src/app/pages/snacks/snacks.component.{ts,html}`, `frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.{ts,html}`) to confirm there is **no** `[innerHTML]` and **no** `DomSanitizer`, that the term/definition are rendered as escaped interpolation only, and that the new label is static text; add a short boundary comment documenting that the custom term and generated meaning remain untrusted. **No new rendering surface is added** (FR-017, FR-018, FR-019, SC-006, research.md R8).

**Checkpoint**: Injection payloads in the term, generated meaning, or created content execute zero times and inject zero nodes; readable text is preserved.

---

## Phase 6: User Story 3 — The custom meaning snack persists and survives a reload/new session (Priority: P2)

**Goal**: A custom-created meaning snack is persisted by the backend through the existing feature-014 path and is still present in the backbone after a page reload / in a new session for the same theme; retrieval failure degrades gracefully. This phase covers milestone **M3 (persistence/reload confirmation, no code)**.

**Independent Test**: Create a meaning snack from a custom keyword, re-enter the theme (full load → `/subtopics` → `/snacks`), and confirm it is re-listed with its term, content, distinct badge, and no quiz; mock `GET /api/meaning-snacks` with a 500 and confirm regular snacks still render with no crash.

### Tests for User Story 3 (write first, must FAIL) ⚠️

- [X] T013 [P] [US3] Extend `frontend/e2e/custom-keyword-create-snack.spec.ts` with the persistence block from e2e-test-plan.md §4: (4.1) a `stored[]` listing mock appended on generate; define a term and create the snack, then re-enter the theme (full page load → `/subtopics` → `/snacks`) and assert the custom meaning snack is re-listed with its term, content, distinct badge, and no quiz; (4.2) mock `GET /api/meaning-snacks` with a 500 and assert regular snacks still render, no crash, no broken entries, no error banner (FR-014, FR-015, SC-005).

### Verification for User Story 3 (no code — confirmation pass)

- [X] T014 [US3] Confirm `SnacksComponent.init()` / `loadMeaningSnacks(themeId)` already calls `GET /api/meaning-snacks?themeId=` on every page load, stores the result in `meaningSnacks`/`AppState`, and marks each generated, so a custom-created row reappears after a reload/new session; confirm a failing/empty listing degrades gracefully to an empty list and that no new entity/schema/endpoint is introduced (review `frontend/src/app/pages/snacks/snacks.component.ts`; `specs/017-custom-keyword-create-snack/contracts/meaning-snack-contract.md` §2; data-model.md §2) (FR-014, FR-015, FR-016, SC-005, SC-007).

**Checkpoint**: The created snack persists and reappears with its distinct, quiz-free rendering; pre-existing data and regular snacks are unaffected.

---

## Phase 7: User Story 5 — Existing snack, keyword, meaning-snack, custom-Define, and formatting behavior is not regressed (Priority: P2)

**Goal**: The feature ships without changing the regular snack/quiz flow, the keyword highlight/definition-card interaction, feature-014's keyword meaning-snack generation, feature-016's selection-Define flow, rich-text rendering, or the results/history surfaces. This phase covers milestone **M4 (E2E + regression)**.

**Independent Test**: Run the existing frontend and backend suites (including `custom-keyword-define`, `meaning-snacks`, `snack-keywords`, `snack-rich-formatting`) and confirm they pass unchanged.

### Verification for User Story 5

- [X] T015 [P] [US5] Run the existing frontend E2E suites unchanged: `cd frontend && npx playwright test` (at minimum `custom-keyword-define.spec.ts`, `meaning-snacks.spec.ts`, `snack-keywords.spec.ts`, `snack-rich-formatting.spec.ts`, `snacks-navigation.spec.ts`, `progressive-delivery.spec.ts`, `results-*.spec.ts`, `theme-history.spec.ts`, `happy-path.spec.ts`); confirm feature-016's Define flow and feature-014's regular "Generate snack" flow are unchanged (FR-020, FR-021, FR-022, FR-023, FR-024, SC-008).
- [X] T016 [P] [US5] Run the existing backend suite unchanged: `cd backend && mvn test` (fallback `./mvnw test`); confirm `MeaningSnackServiceTest`, `MeaningSnackContractTest` and the rest pass as-is — evidence for the reused contract and the backend half of non-regression (FR-016, FR-020, SC-007, SC-008).

**Checkpoint**: The full existing frontend and backend suites pass unchanged; features 012/013/014/015/016 behavior is intact.

---

## Phase 8: Polish & Cross-Cutting Concerns — Milestone M4 close-out

**Purpose**: Final validation and cleanup across all stories.

- [X] T017 [P] Run the `specs/017-custom-keyword-create-snack/quickstart.md` §5 manual verification checklist (action availability, creation, quiz-free & distinct, persistence, security payload table, non-regression) (SC-001, SC-002, SC-003, SC-004, SC-005, SC-006, SC-007, SC-008).
- [X] T018 [P] Confirm the rollback path: reverting the frontend edits (the page's `onDefineRequested` capture + `generateEnabled`/`activeGenerateState` changes, the card's `customDefinition` input + label branch, the snacks-page binding, and the new E2E spec) requires **no** backend change and **no** data change / no SQL; any snacks created during testing are ordinary feature-014 `MeaningSnack` rows and remain valid (data-model.md §2, quickstart.md §6, SC-007).
- [X] T019 [P] Verify checklist-to-task traceability: every FR-001..FR-024 and SC-001..SC-008 is covered by at least one task, every task maps to a requirement (no orphans), and `checklists/requirements.md`, `checklists/security.md`, `checklists/ux.md` and `checklists/test.md` remain consistent with tasks.md (all FR/SC).
- [X] T020 Final completion validation: run the frontend build/type-check `cd frontend && npm run build`, run the new spec `cd frontend && npx playwright test e2e/custom-keyword-create-snack.spec.ts`, run the full frontend regression `cd frontend && npx playwright test`, and run `cd backend && mvn test`; confirm every task above is complete, every `checklists/*.md` item is checked, and every FR-001..FR-024 / SC-001..SC-008 is covered by at least one task with no orphans (all FR/SC).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2, M2-shared)**: Depends on Setup — the additive card input (T003) blocks US1's label branch (T008).
- **US1 (Phase 3, M1 + M2)**: Depends on Foundational; **unblocks US2–US5** (they verify the flow US1 creates).
- **US2 (Phase 4, M3)**: Depends on US1 (T007/T008) — it verifies the created snack's kind/rendering.
- **US4 (Phase 5, M3)**: Depends on US1 (T007/T008) — it verifies the card/backbone rendering paths.
- **US3 (Phase 6, M3)**: Depends on US1 (T007/T008) — it verifies the reused listing path.
- **US5 (Phase 7, M4)**: Depends on all implementation phases.
- **Polish (Phase 8, M4 close)**: Depends on all desired stories being complete.

### Milestone Mapping

| Milestone | Phase(s) | Key tasks |
|-----------|----------|-----------|
| **M1** Capture source subtopic + availability | US1 | T007 |
| **M2** Action-slot relabel + wiring | Foundational, US1 | T003, T004, T008 |
| **M3** Persistence / quiz-free / security confirmation | US2, US3, US4 | T009, T010, T011, T012, T013, T014 |
| **M4** E2E + regression | US1 (tests), US5, Polish | T005, T006, T015, T016, T017, T018, T019, T020 |

### User Story Dependencies

- **US1 (P1)**: Starts after Foundational; delivers the only logic change and unblocks all verification stories.
- **US2 (P1)**: Starts after US1 (asserts the created entry's distinct/quiz-free rendering).
- **US4 (P1)**: Starts after US1 (asserts the card/backbone rendering paths are escaped-only).
- **US3 (P2)**: Starts after US1 (asserts the created row is re-listed by the reused retrieval path).
- **US5 (P2)**: Starts after all implementation tasks land.

### Within Each User Story

- Test tasks MUST be written and FAIL before the corresponding implementation task.
- The card input (T003) before the template binding (T004) and the label branch (T008).
- The E2E spec (T005) before the page capture (T007) and label branch (T008).
- Story complete and validated before moving to the next priority.

### ⚠️ Highest-Risk Task

- **T007 (source-subtopic capture in `onDefineRequested` + `activeGenerateState` key):** the single riskiest change. A wrong capture attaches the snack to the wrong subtopic, and an incorrectly rewritten `generateEnabled`/`activeGenerateState` could regress feature 014's regular card. Mitigations: capture at Define time into `activeKeywordSubtopicId` (regular `subtopicId` / meaning `sourceSubtopicId` / `null`), keep the regular branch behavior-equivalent, and assert the POST `subtopicId` for both a regular and a meaning active entry (T005 scenarios 2.1/2.6). Coupled risks: **T008** (must keep exactly one action per card) and **T005** (the E2E assertions). Do **not** move capture logic into the directive or the card.

### Parallel Opportunities

- T001 and T002 (Setup) run in parallel.
- T003 (Foundational) is independent; T004 depends on T003.
- US1: T005 and T006 run in parallel; T007 then T008 (different files, but T008 depends on T003 and both depend on T005 tests being written first).
- US2: T009 and T010 are sequential (test then confirm).
- US4: T011 and T012 are sequential (test then audit).
- US3: T013 and T014 are sequential (test then confirm).
- US5: T015 and T016 run in parallel.
- Polish: T017, T018, T019 in parallel; T020 last.
- **Note**: tasks that edit the same file (`custom-keyword-create-snack.spec.ts`, `snacks.component.ts`, `keyword-definition-card.component.*`) are conceptually independent blocks but must be serialized on disk; the `[P]` marker reflects independence of the work, not concurrent writes.

---

## Parallel Example: User Story 1

```bash
# Write the US1 tests together (must fail first):
Task: "Create frontend/e2e/custom-keyword-create-snack.spec.ts (availability/create/loading/dedup/failure/reopen/subtopic)"
Task: "Extend frontend/e2e/helpers.ts only if a custom-create fixture is needed"

# Then the implementation (T008 depends on T003):
Task: "Edit frontend/src/app/pages/snacks/snacks.component.ts (capture + generateEnabled + activeGenerateState)"
Task: "Edit frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.html (Create snack / Creating…)"
```

## Parallel Example: Non-Regression

```bash
# Run both regression gates together:
Task: "cd frontend && npx playwright test  (existing suites unchanged)"
Task: "cd backend && mvn test              (existing suites unchanged)"
```

---

## Implementation Strategy

### MVP First (US1)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (shared card contract).
3. Complete Phase 3: US1 (capture + availability + relabel + wiring) — the entire logic change.
4. **STOP and VALIDATE**: run `frontend/e2e/custom-keyword-create-snack.spec.ts` and inspect the create/loading/error/dedup flow.
5. Deploy/demo the MVP.

### Incremental Delivery

1. Setup + Foundational → shared card contract ready.
2. US1 → Create snack from a custom keyword → validate → demo.
3. US2 → quiz-free + distinct proven.
4. US4 → escaped-only rendering proven.
5. US3 → persistence/reload proven.
6. US5 → non-regression proven.
7. Polish → full gates green.

### Notes

- `[P]` tasks touch different files with no incomplete dependency.
- Foundational is the additive, default-`false` card input; US1 is the only logic change.
- No backend change, no schema change, no new endpoint/DTO/service/dependency, no new runner.
- **Highest risk**: T007 (source-subtopic capture) — pin the entry the meaning was read from and keep the regular branch unchanged.
- Commit after each task or logical group; verify tests fail before implementing.

---

## FR Traceability Matrix

Every functional requirement (FR-001–FR-024) maps to at least one task. No gaps.

| FR | Description (short) | User Story | Task(s) |
|----|---------------------|------------|---------|
| FR-001 | Clearly labeled "Create snack" action after a valid generated meaning | US1 | T003, T005, T008 |
| FR-002 | Action offered only when a valid generated meaning exists | US1 | T005, T007 |
| FR-003 | Reuse/relabel the feature-014 slot; exactly one action per card | Foundational, US1 | T003, T004, T005, T008 |
| FR-004 | Reuse the existing feature-014 generate/append/mark/backbone flow | Setup, US1 | T002, T005, T006, T007 |
| FR-005 | Source subtopic = active entry's `subtopicId`/`sourceSubtopicId` (Option A) | US1 | T005, T007 |
| FR-006 | Loading/in-progress state while creating | US1 | T005, T007, T008 |
| FR-007 | At most one concurrent generation; no duplicate | US1 | T005, T007 |
| FR-008 | Failure shows a meaningful error; nothing broken/empty/placeholder added | US1 | T005, T007 |
| FR-009 | Success adds the snack to the backbone via the feature-014 append path | US1 | T005, T007 |
| FR-010 | Re-opened card reflects already-generated state; no silent duplicate | US1 | T005, T007 |
| FR-011 | Created snack is quiz-free; no "Start Quiz" | US2 | T009, T010 |
| FR-012 | Created snack is visually distinct (feature-014 treatment) | US2 | T009, T010 |
| FR-013 | Never treated as a quiz-bearing regular snack by any surface | US2 | T009, T010 |
| FR-014 | Persisted through the feature-014 path; survives reload/new session | US3 | T013, T014 |
| FR-015 | Reuse the theme-scoped retrieval path; degrade gracefully | US3 | T013, T014 |
| FR-016 | No new entity, schema change, or endpoint | Setup, US3, US5 | T001, T002, T014, T016 |
| FR-017 | Escaped-text-only rendering (no `[innerHTML]`, no `DomSanitizer`) | US4 | T011, T012 |
| FR-018 | Markup/handlers/dangerous URLs never executed or injected | US4 | T011, T012 |
| FR-019 | feature-012/013/014/015/016 sanitization model preserved unchanged | US4 | T011, T012 |
| FR-020 | Existing snack/quiz/results/history flow + E2E suites pass unchanged | US5, Polish | T015, T016, T020 |
| FR-021 | Feature-014 regular "Generate snack" flow unchanged | US5, Polish | T015, T020 |
| FR-022 | Feature-016 selection-Define flow unchanged apart from the added action | US5, Polish | T015, T020 |
| FR-023 | Feature-013 highlight/definition-card behavior unchanged apart from the action | US5, Polish | T015, T020 |
| FR-024 | No change to regular snack/quiz/batch or feature-014 kind/persistence/interleave | US5, Polish | T015, T016, T020 |

## SC Traceability Matrix

Every success criterion (SC-001–SC-008) maps to at least one task. No gaps.

| SC | Description (short) | Task(s) |
|----|---------------------|---------|
| SC-001 | 100% of valid-meaning custom cards offer Create snack; 0 before/blank/errored | T005, T007, T008, T017 |
| SC-002 | 100% of successful activations add exactly one snack; 0 duplicates | T005, T007, T017 |
| SC-003 | 100% in-flight show loading; 100% failures show a meaningful error and add 0 entries | T005, T007, T017 |
| SC-004 | 100% created snacks are quiz-free and visually distinct; 0 quiz affordances | T009, T010, T017 |
| SC-005 | 100% persisted snacks survive reload/new session; 0 lost; 0 data invalidated | T013, T014, T017 |
| SC-006 | 0 executable/injected markup across all surfaces | T011, T012, T017 |
| SC-007 | 0 new entities/schema/endpoints (reused feature-014 path) | T001, T002, T014, T016, T018, T019, T020 |
| SC-008 | Existing E2E/backend suites pass unchanged; 012/013/014/015/016 intact | T001, T015, T016, T020 |

**Coverage summary**: 24/24 FRs covered; 8/8 SCs covered. FR/SC gaps: **none**. Every task maps to at least one FR/SC (no orphaned tasks).
