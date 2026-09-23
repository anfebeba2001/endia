---
description: "Task list for feature 012-snack-rich-formatting"
---

# Tasks: Snack Rich Formatting (Bold Emphasis, Bullets, Short Paragraphs)

**Input**: Design documents from `/specs/012-snack-rich-formatting/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: Tests are **required** for this feature (the spec's Success Criteria and plan's Testing Strategy explicitly demand them). Test tasks are written first, before the corresponding implementation, per TDD ordering.

**Organization**: Tasks are grouped by user story (US1–US5) so each story can be implemented and verified independently. There is **no schema change and no JSON-contract change** (data-model.md, contracts/snack-text-format.md); the only backend production change is one prompt file.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Which user story this task belongs to (US1–US5)
- Every task includes exact file path(s)

## Path Conventions

- **Backend**: `backend/src/main/...`, `backend/src/test/...`
- **Frontend**: `frontend/src/app/...`, `frontend/e2e/...`
- **Feature docs**: `specs/012-snack-rich-formatting/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm constraints and create the one new frontend directory before any story work.

- [X] T001 [P] Confirm no new runtime dependency is required (no `backend/pom.xml` and no `frontend/package.json` change) per plan.md/research.md R1/R5; record the pre-change baseline by running `mvn test` in `backend/` and `npx playwright test` in `frontend/` (baseline for FR-021, SC-008).
- [X] T002 [P] Create the shared renderer directory `frontend/src/app/components/rich-text/` (alongside the existing `frontend/src/app/components/drag-drop-question/`) to hold the model, parser, component, template, and styles.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The single shared rich-text rendering boundary (parser + component) that US2, US3, and US5 all depend on. **No `[innerHTML]`, no `DomSanitizer`, no new dependency.**

**⚠️ CRITICAL**: US2, US3, and US5 cannot complete until this phase is done. **US1 (backend prompt) is independent of this phase and may run in parallel** (plan.md M1 || M3).

- [X] T003 [P] Define the render AST types (`RichInline`, `RichBlock`) in `frontend/src/app/components/rich-text/rich-text.model.ts` exactly as specified in data-model.md §4.
- [X] T004 Implement the pure, synchronous `parseRichText(text: string): RichBlock[]` in `frontend/src/app/components/rich-text/rich-text.parser.ts`: normalize line endings; blank line → paragraph boundary; `/^\s*[-*•]\s+/` → bullet item (consecutive items form one list block); inline `**` toggle tokenizer with the non-space-boundary rule; unclosed `**` emitted as literal text; single `\n` → break node; minimal HTML-entity decode for text nodes; empty/whitespace input → zero blocks (FR-010, FR-011, FR-012, FR-014, FR-015, FR-019).
- [X] T005 Implement the standalone, signals-first, `ChangeDetectionStrategy.OnPush` `RichTextComponent` with `readonly text = input<string>('')` and `protected readonly blocks = computed(() => parseRichText(this.text()))` in `frontend/src/app/components/rich-text/rich-text.component.ts` (FR-018).
- [X] T006 [P] Implement `frontend/src/app/components/rich-text/rich-text.component.html` using only `@for`/`@if`/`@switch` and interpolation to render `<p>`, `<ul><li>`, `<strong>`, and `<br>` from the AST — **no `[innerHTML]` anywhere** (FR-010, FR-011, FR-012, FR-018).
- [X] T007 [P] Implement token-based typography and paragraph/list rhythm styles in `frontend/src/app/components/rich-text/rich-text.component.scss`, normalizing inner `<p>`/`<ul>` margins so host classes from the three pages keep applying (plan.md Design).

**Checkpoint**: The shared renderer exists and can be imported by page components.

---

## Phase 3: User Story 1 — Snacks are written to be scannable (Priority: P1) 🎯 MVP

**Goal**: The snack prompt instructs the model to emit `**bold**`, `- ` bullets, and short paragraphs while keeping the existing JSON shape; generated content stays non-empty and passes existing validation.

**Independent Test**: Inspect `generate-snack.md` and the rendered prompt for the formatting/whitelist/shape rules; assert a markup-bearing model response parses to non-empty `concept`/`metaphor`/`whyItMatters` with `related` 2–3 entries and no re-roll.

### Tests for User Story 1 (write first, must FAIL) ⚠️

- [X] T008 [P] [US1] Add `backend/src/test/java/com/endia/infrastructure/llm/GenerateSnackPromptTest.java` asserting the rendered prompt (via `LlmPromptTemplates.generateSnack`) instructs `**...**` bold, `- ` bullet lines, short/blank-line paragraphs, restricts formatting to the whitelist, forbids arbitrary HTML (links/images/scripts/headings/tables/code/inline styles), and still specifies exactly `concept`, `metaphor`, `whyItMatters`, `related` (FR-001, FR-002, FR-003, FR-004).
- [X] T009 [P] [US1] Extend `backend/src/test/java/com/endia/application/service/SnackServiceTest.java` with a case where formatted (markup-bearing) fields pass validation and cause **no** spurious re-roll (FR-006).

### Implementation for User Story 1

- [X] T010 [US1] Rewrite `backend/src/main/resources/prompts/generate-snack.md` to require `**bold**` emphasis, `- ` bullets, and short blank-line paragraphs; keep the exact JSON shape and `related` as plain names; explicitly whitelist only bold/paragraphs/bullets and forbid arbitrary HTML. **This is the only backend production change** (FR-001, FR-002, FR-003, FR-004, FR-005).
- [X] T011 [US1] Add a case to `backend/src/test/java/com/endia/infrastructure/llm/OpenAiLlmProviderTest.java` asserting `generateSnack` returns markup-bearing `concept`/`metaphor`/`whyItMatters` verbatim, keeps `related` 2–3 plain entries, and does not strip/escape text (FR-005, FR-006).

**Checkpoint**: The prompt emits formatting; JSON shape and validation are intact.

---

## Phase 4: User Story 2 — The snack UI renders the formatting (Priority: P1)

**Goal**: Bold, bullets, and paragraphs render as real elements on the snack card, the results recap, and the read-only history view; `related` stays plain.

**Independent Test**: With route-mocked snacks containing `**bold**`, `- `/`* `/`• ` bullets, and blank-line paragraphs, assert `<strong>`, `<ul><li>`, and multiple `<p>` appear and zero literal `**` or leading-dash text is visible on all three surfaces.

### Tests for User Story 2 (write first, must FAIL) ⚠️

- [X] T012 [P] [US2] Create `frontend/e2e/snack-rich-formatting.spec.ts` with route-mocked `POST /api/snacks/generate` fixtures asserting on the snack card: `<strong>` for bold, no literal `**`, `<ul><li>` for bullets, multiple `<p>` for paragraphs, and `related` rendered as plain labels (FR-010, FR-011, FR-012, FR-013).
- [X] T013 [P] [US2] Extend `frontend/e2e/snack-rich-formatting.spec.ts` with a mocked `GET /api/quiz/summary/*` recap snack asserting the same rich rendering in the results recap (FR-013).
- [X] T014 [P] [US2] Extend `frontend/e2e/theme-history.spec.ts` with a mocked `GET /api/themes` whose nested snacks contain markup, asserting rich rendering in the expanded history detail and no literal markers (FR-013, FR-023).

### Implementation for User Story 2

- [X] T015 [US2] Import `RichTextComponent` in `frontend/src/app/pages/snacks/snacks.component.ts` and replace the plain `<p>` interpolations at lines 82, 85, 88 of `frontend/src/app/pages/snacks/snacks.component.html` with `<app-rich-text [text]="..." />`, keeping the existing host classes and leaving `related` unchanged (FR-010, FR-011, FR-012, FR-013).
- [X] T016 [US2] Import `RichTextComponent` in `frontend/src/app/pages/results/results.component.ts` and replace the plain `<p>` interpolations at lines 84, 85 of `frontend/src/app/pages/results/results.component.html` with `<app-rich-text [text]="..." />` (FR-013).
- [X] T017 [US2] Import `RichTextComponent` in `frontend/src/app/pages/history/history.component.ts` and replace the plain `<p>` interpolations at lines 62, 63, 64 of `frontend/src/app/pages/history/history.component.html` with `<app-rich-text [text]="..." />`, leaving `related` (`history-snack__related`) plain (FR-013, FR-023).

**Checkpoint**: US1 + US2 deliver the visible MVP: formatted text generated and rendered on all three surfaces.

---

## Phase 5: User Story 3 — Only safe, whitelisted formatting is rendered (Priority: P1)

**Goal**: Untrusted model output can never execute or inject; only bold, paragraphs, and bullet lists are produced, and readable text is preserved.

**Independent Test**: Feed `<script>`, `<img onerror=...>`, `javascript:` links, `<iframe>`, `<style>`, and inline styles into snack fields on all three surfaces; assert no execution, no injected nodes, and readable text preserved.

### Tests for User Story 3 (write first, must FAIL) ⚠️

- [X] T018 [P] [US3] Extend `frontend/e2e/snack-rich-formatting.spec.ts` with a security block on the snack card: fields containing `<script>window.__xss=1</script>`, `<img src=x onerror="window.__xss=1">`, `javascript:` links, `<iframe>`, `<style>`, and inline styles; assert `window.__xss` stays `undefined`, no snack-injected `<script>`/`<img>`/`<iframe>`/`<style>`/`<a>` nodes exist, and readable text is still shown (FR-015, FR-016, FR-017, FR-019).
- [X] T019 [P] [US3] Extend the same security block to the results recap and history surfaces, asserting the same neutralization guarantees there (FR-024).

### Implementation for User Story 3

- [X] T020 [US3] Audit `frontend/src/app/components/rich-text/rich-text.component.html` and `frontend/src/app/components/rich-text/rich-text.parser.ts` to confirm there is no `[innerHTML]`/`DomSanitizer`, that the only producible elements are `<p>/<ul>/<li>/<strong>/<br>`, and that unknown markup is emitted as escaped text nodes; add a short boundary comment documenting this (FR-015, FR-017, FR-018, FR-019).

**Checkpoint**: Rich formatting is safe; injection payloads are inert.

---

## Phase 6: User Story 4 — Formatting survives storage and the API unchanged (Priority: P2)

**Goal**: Prove (without modifying production code) that `**`/bullets/paragraphs survive persistence and every snack-returning API surface byte-for-byte, with the JSON shape unchanged.

**Independent Test**: Persist a markup-bearing snack and read it back through fresh generation, batch status, recap, and `GET /api/themes`; assert the decoded string equals the input and the response has exactly the existing fields.

### Tests for User Story 4 (write first, must FAIL) ⚠️

- [X] T021 [P] [US4] Add `backend/src/test/java/com/endia/interfaces/rest/SnackMarkupRoundTripTest.java` (MockMvc, focused standalone setup mirroring `ThemeHistoryContractTest`) asserting `**bold**`, bullet lines, blank-line paragraphs, and `<script>`/`onerror` payloads are returned unmodified (not stripped, collapsed, or HTML-escaped) from the fresh generation response, batch status, results recap, and `GET /api/themes` (FR-007, FR-008, SC-006).
- [X] T022 [P] [US4] Add a Testcontainers MySQL integration test under `backend/src/test/java/com/endia/application/integration/` (tagged `integration`) that saves a markup-bearing `Snack`, reloads it via JPA, and asserts the `concept`/`metaphor`/`whyItMatters` strings are byte-for-byte identical (FR-007).
- [X] T023 [US4] Add a contract-shape assertion (in `backend/src/test/java/com/endia/interfaces/rest/SnackMarkupRoundTripTest.java` or `ThemeHistoryContractTest.java`) that the snack JSON keys/field types match `specs/012-snack-rich-formatting/contracts/snack-response.sample.json` with no new fields or endpoints (FR-009, SC-007).

### Implementation for User Story 4

- [X] T024 [US4] Verify and record that **no** persistence/DTO change is needed (confirm `backend/src/main/java/com/endia/domain/model/Snack.java` and the snack DTOs are untouched) and run the new round-trip tests green (FR-007, FR-008, FR-009, FR-022).

**Checkpoint**: Data integrity is proven; contract is unchanged.

---

## Phase 7: User Story 5 — Existing snack fields and the recap still work (Priority: P2)

**Goal**: Plain and legacy snacks render with no artifacts; quiz/recap, progressive delivery, retries, and navigation are unregressed.

**Independent Test**: Render no-markup fields (including `5 * 3 = 15` and an unclosed `**`) and confirm clean output; run the existing E2E suites unchanged.

### Tests for User Story 5 (write first, must FAIL) ⚠️

- [X] T025 [P] [US5] Extend `frontend/e2e/snack-rich-formatting.spec.ts` with no-markup fixtures: a plain field renders as clean text with no empty `<p>`/`<ul>` and no stray markers; `5 * 3 = 15` is not mangled; an unclosed `**important` renders literally without swallowing the rest of the field (FR-014).
- [X] T026 [P] [US5] Run the existing suites unchanged — `frontend/e2e/happy-path.spec.ts`, `progressive-delivery.spec.ts`, `snack-wait-messages.spec.ts`, `results-*.spec.ts`, `snacks-navigation.spec.ts`, and `theme-history.spec.ts` — and confirm they pass with no edits (FR-020, FR-021, SC-008).

### Implementation for User Story 5

- [X] T027 [US5] Verify the results recap still renders the snack and quiz/results behavior is unchanged, and confirm `related` remains plain labels on all surfaces (FR-013, FR-020).
- [X] T028 [US5] Verify scope: generation flow, provider selection, batch-status semantics, and the data model are unchanged except the prompt wording and the frontend rendering path (FR-022).

**Checkpoint**: No regressions; plain/legacy content is safe.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Final validation and cleanup across all stories.

- [X] T029 [P] Run the quickstart.md manual verification checklist (`specs/012-snack-rich-formatting/quickstart.md` §4) against all three surfaces, including the security payload table.
- [X] T030 [P] Run the full backend suite: `cd backend && mvn test` and, where Docker is available, `mvn verify -Pintegration` (FR-021).
- [X] T031 Run the full frontend gate: `cd frontend && npm run build` and `npx playwright test`; confirm the new spec passes and all existing suites pass unchanged (FR-021, SC-008).
- [X] T032 [P] Clean up: remove or repoint now-unused plain-`<p>` snack text styles in `frontend/src/app/pages/snacks/snacks.component.scss`, `frontend/src/app/pages/results/results.component.scss`, and `frontend/src/app/pages/history/history.component.scss` so host-class typography still applies through `<app-rich-text>`.
- [X] T033 Confirm the rollback path: reverting `backend/src/main/resources/prompts/generate-snack.md` and the `frontend/src/app/components/rich-text/` renderer plus the three call sites requires no data/schema migration (data-model.md §5).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup — **blocks US2, US3, US5**.
- **US1 (Phase 3)**: Depends on Setup only. **Independent of Foundational** (backend prompt vs. frontend renderer) — may run in parallel with Phase 2.
- **US2 (Phase 4)**: Depends on Foundational. Delivers the visible MVP with US1.
- **US3 (Phase 5)**: Depends on Foundational (renderer) and US2 (surfaces wired for the recap/history security assertions).
- **US4 (Phase 6)**: Depends on US1 (markup-bearing fixtures); no frontend dependency.
- **US5 (Phase 7)**: Depends on Foundational and US2 (rendering surfaces).
- **Polish (Phase 8)**: Depends on all desired stories being complete.

### User Story Dependencies

- **US1 (P1)**: Can start after Setup; no dependency on other stories.
- **US2 (P1)**: Can start after Foundational; no dependency on US1 (fixtures are mocked).
- **US3 (P1)**: Starts after Foundational; its recap/history assertions rely on US2 wiring.
- **US4 (P2)**: Starts after US1; independent of the frontend renderer.
- **US5 (P2)**: Starts after Foundational; regression assertions rely on US2 wiring.

### Within Each User Story

- Test tasks MUST be written and FAIL before the corresponding implementation task.
- Types before parser; parser before component.
- Shared component before call-site wiring.
- Story complete and validated before moving to the next priority.

### Parallel Opportunities

- T001 and T002 (Setup) run in parallel.
- T003, T006, T007 (Foundational) run in parallel; T004 after T003; T005 after T004.
- **US1 (T008, T009) can run in parallel with the whole Foundational phase.**
- Within US1: T008 and T009 in parallel; T010 after T008/T009.
- Within US2: T012, T013, T014 in parallel; then T015, T016, T017 in parallel (different page files).
- Within US3: T018 and T019 in parallel; T020 after.
- Within US4: T021 and T022 in parallel.
- Within US5: T025 and T026 in parallel.
- Polish: T029, T030, T032 in parallel.

---

## Parallel Example: User Story 2

```bash
# Write all US2 tests together (must fail first):
Task: "Create frontend/e2e/snack-rich-formatting.spec.ts snack-card assertions"
Task: "Extend frontend/e2e/snack-rich-formatting.spec.ts with recap assertions"
Task: "Extend frontend/e2e/theme-history.spec.ts with rich-rendering assertions"

# Then wire the three surfaces together (different files):
Task: "Wire RichTextComponent into frontend/src/app/pages/snacks/snacks.component.html"
Task: "Wire RichTextComponent into frontend/src/app/pages/results/results.component.html"
Task: "Wire RichTextComponent into frontend/src/app/pages/history/history.component.html"
```

---

## Implementation Strategy

### MVP First (US1 + US2)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (shared renderer) — can proceed in parallel with US1.
3. Complete Phase 3: US1 (prompt emits formatting).
4. Complete Phase 4: US2 (render on snack card, recap, history).
5. **STOP and VALIDATE**: Run `frontend/e2e/snack-rich-formatting.spec.ts` and inspect generated snacks — formatted text is generated and rendered.
6. Deploy/demo the MVP.

### Incremental Delivery

1. Setup + Foundational → renderer ready.
2. US1 + US2 → MVP (generation + rendering) → validate → demo.
3. US3 → security hardening proven → validate → demo.
4. US4 → round-trip integrity proven.
5. US5 → regression contract confirmed.
6. Polish → full gates green.

### Notes

- `[P]` tasks touch different files with no incomplete dependency.
- US1 is the only backend production change; everything else is frontend + tests.
- No new dependency, no schema change, no JSON-contract change.
- Commit after each task or logical group; verify tests fail before implementing.

---

## FR Traceability Matrix

Every functional requirement (FR-001–FR-024) maps to at least one task. No gaps.

| FR | Description (short) | User Story | Task(s) |
|----|---------------------|------------|---------|
| FR-001 | Prompt instructs bold marker | US1 | T008, T010 |
| FR-002 | Prompt instructs bullets + short paragraphs | US1 | T008, T010 |
| FR-003 | JSON shape unchanged; `related` plain names | US1 | T008, T010, T011, T023 |
| FR-004 | Prompt constrains to whitelist; forbids HTML | US1 | T008, T010 |
| FR-005 | Generated text actually contains markup | US1 | T010, T011 |
| FR-006 | Content non-empty; existing validation passes | US1 | T009, T011 |
| FR-007 | Markup survives persistence unmodified | US4 | T021, T022, T024 |
| FR-008 | Markup survives API on every surface | US4 | T021, T024 |
| FR-009 | API/JSON contract unchanged | US4 | T023, T024 |
| FR-010 | Bold renders as `<strong>`; no literal `**` | US2 | T004, T006, T012, T015, T016, T017 |
| FR-011 | Bullets render as list items | US2 | T004, T006, T012, T015, T016, T017 |
| FR-012 | Long content renders as short paragraphs | US2 | T004, T006, T012, T015, T016, T017 |
| FR-013 | Consistent rich rendering on all surfaces; `related` plain | US2 | T012, T013, T014, T015, T016, T017, T027 |
| FR-014 | No-markup text clean; no artifacts/mangling | US5 | T004, T025 |
| FR-015 | Only whitelisted formatting rendered | US3 | T004, T018, T020 |
| FR-016 | Raw HTML/scripts/handlers never executed/injected | US3 | T006, T018, T019, T020 |
| FR-017 | Render-time sanitization (defense in depth) | US3 | T018, T020 |
| FR-018 | Safe allowlist mechanism, no raw HTML binding | US3 | T005, T006, T020 |
| FR-019 | Readable text preserved when markup neutralized | US3 | T004, T018, T020 |
| FR-020 | Existing fields/recap unchanged; quiz unchanged | US5 | T026, T027 |
| FR-021 | Progressive delivery/retries/navigation unchanged; E2E pass | US5 | T001, T026, T030, T031 |
| FR-022 | No flow/provider/batch/data-model change | US5 | T024, T028, T033 |
| FR-023 | History view rich rendering, same whitelist | US2 | T014, T017 |
| FR-024 | History view sanitization guarantees | US3 | T019, T020 |

**Coverage summary**: 24/24 FRs covered. FR gaps: **none**.
