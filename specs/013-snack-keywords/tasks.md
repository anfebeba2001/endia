---
description: "Task list for feature 013-snack-keywords"
---

# Tasks: Colored Keywords with Click-to-Show Short Definition Cards

**Input**: Design documents from `/specs/013-snack-keywords/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: Tests are **required** for this feature (the spec's Success Criteria and the plan's Testing Strategy explicitly demand them). Test tasks are written first, before the corresponding implementation, per TDD ordering.

**Organization**: Tasks are grouped by user story (US1–US5) so each story can be implemented and verified independently, and map to the plan's milestones M1–M6 (M1 prompt, M2 domain+persistence, M3 parse/normalize/contract, M4 highlight renderer, M5 definition card + surfaces, M6 security + regression).

**Explicitly out of scope (do NOT create tasks for these)**: the definition-card "Generate snack" action and the "meaning snack" backbone entry. These belong to a separate backlog task (Task 9) and are excluded by FR-031 and spec Non-Goals.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Which user story this task belongs to (US1–US5)
- Every task includes exact file path(s) and the FR/SC references it satisfies

## Path Conventions

- **Backend**: `backend/src/main/...`, `backend/src/test/...`
- **Frontend**: `frontend/src/app/...`, `frontend/e2e/...`
- **Feature docs**: `specs/013-snack-keywords/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm constraints and create the one new frontend component directory before any story work.

- [X] T001 [P] Confirm no new runtime dependency is required (no `backend/pom.xml` and no `frontend/package.json` change) per plan.md/research.md R1/R6; record the pre-change baseline by running `mvn test` in `backend/` and `npx playwright test` in `frontend/` (baseline for FR-030, SC-009).
- [X] T002 [P] Create the shared definition-card directory `frontend/src/app/components/keyword-definition-card/` (alongside the existing `frontend/src/app/components/rich-text/`) to hold the standalone card component, template, and styles (FR-018, plan.md §Project Structure).

---

## Phase 2: Foundational (Blocking Prerequisites) — Milestone M2

**Purpose**: The domain value object and JSON persistence layer that US1's contract work (M3) and US5's persistence round-trip both depend on. **Additive nullable schema only; no new layer.**

**⚠️ CRITICAL**: US1's contract tasks (T011–T023) and US5 cannot complete until this phase is done. **M1 (prompt, T010) and M4 (highlight renderer, US2) are independent of this phase and may run in parallel** (plan.md: M1, M2, M4 parallelizable).

- [X] T003 [P] Create the pure domain value object `backend/src/main/java/com/endia/domain/model/SnackKeyword.java` as `public record SnackKeyword(String term, String definition)` with a compact constructor normalizing `null` → `""` (no truncation/dropping — that is service policy); Jackson-serializable (FR-011, data-model.md §1).
- [X] T004 [P] Create `backend/src/main/java/com/endia/infrastructure/persistence/JpaSnackKeywordListConverter.java` as `AttributeConverter<List<SnackKeyword>, String>` mirroring `JpaJsonObjectConverter`: `null`/empty → `"[]"`, else Jackson `writeValueAsString`; entity side `null`/blank → `List.of()`, else `readValue(..., new TypeReference<List<SnackKeyword>>(){})`; `RuntimeException` on `JsonProcessingException` (FR-011, FR-012, data-model.md §3).
- [X] T005 Extend `backend/src/main/java/com/endia/domain/model/Snack.java`: add `@Convert(converter = JpaSnackKeywordListConverter.class) @Column(nullable = true, columnDefinition = "JSON") private List<SnackKeyword> keywords = new ArrayList<>();`; keep the existing 5-arg constructor delegating with `List.of()` and add the 6-arg constructor `(Subtopic, concept, metaphor, whyItMatters, related, keywords)` (FR-011, FR-012, FR-030, data-model.md §2). **Depends on T003, T004.**

**Checkpoint**: The keyword value object, its JSON converter, and the nullable `snack.keywords` mapping exist; existing constructors still compile.

---

## Phase 3: User Story 1 — Each snack identifies its key terms with a very short definition (Priority: P1) 🎯 MVP

**Goal**: The prompt emits a `keywords` array of `{ term, definition }` items (definitions ≤ 3 words); the provider parses it defensively; `SnackService` normalizes it (trim/drop blanks/truncate to 3 tokens/case-insensitive dedupe) without ever affecting validity; and `keywords` round-trips through all four snack-returning API surfaces. This phase covers milestones **M1 (prompt)** and **M3 (parse/normalize/contract)**.

**Independent Test**: Render the prompt and assert the keyword instructions and unchanged existing shape; feed a keyword-bearing model response through the provider and service and assert normalization + no re-roll; assert `keywords` is present with identical items on fresh generation, batch status, results recap, and history.

### Tests for User Story 1 (write first, must FAIL) ⚠️

- [X] T006 [P] [US1] Extend `backend/src/test/java/com/endia/infrastructure/llm/GenerateSnackPromptTest.java` asserting the rendered `generate-snack.md` asks for a `keywords` array, mentions `term`/`definition`, states the ≤ 3-word limit and the 2–5 guideline, requires terms to appear verbatim in the body text, forbids markup in keyword text, and still specifies exactly `concept`, `metaphor`, `whyItMatters`, `related` with the feature-012 formatting rules (FR-001, FR-002, FR-003, FR-004, SC-001).
- [X] T007 [P] [US1] Extend `backend/src/test/java/com/endia/infrastructure/llm/OpenAiLlmProviderTest.java` asserting a well-formed `keywords` array parses into `SnackKeyword`s, a missing array yields an empty list, and non-object elements / missing fields are skipped without throwing (FR-006).
- [X] T008 [P] [US1] Extend `backend/src/test/java/com/endia/application/service/SnackServiceTest.java` with normalization cases: an over-limit definition truncated to its first 3 whitespace tokens; blank term/definition dropped; a keyword whose definition becomes blank after truncation dropped; duplicate terms de-duplicated case-insensitively keeping the first; an all-invalid keyword list yields an empty list with **no** re-roll (provider called once); an invalid core field still re-rolls exactly once (FR-005, FR-007, FR-008, FR-009, SC-001).
- [X] T009 [P] [US1] Extend `backend/src/test/java/com/endia/interfaces/rest/SnackMarkupRoundTripTest.java` asserting `keywords` is present with the same items on fresh generation, batch status, results recap, and `GET /api/themes`, the shape is `[{term,definition}]`, values are not stripped or HTML-escaped, and **update the existing object key-count assertions from 7/7/7/5 to 8/8/8/6** (FR-006, FR-011, SC-008).

### Implementation for User Story 1

- [X] T010 [P] [US1] Rewrite `backend/src/main/resources/prompts/generate-snack.md` to request a `keywords` array of `{ term, definition }` items (2–5 guideline, definitions ≤ 3 words, terms verbatim in the body text, plain text only) while keeping the exact existing JSON shape and feature-012 formatting rules unchanged (FR-001, FR-002, FR-003, FR-004, FR-005, FR-010). **(M1 — may run in parallel with Phase 2.)**
- [X] T011 [US1] Extend `backend/src/main/java/com/endia/application/port/LlmProvider.java`: add `List<SnackKeyword> keywords` as the fifth component of `GeneratedSnack` and add a 4-arg constructor overload delegating with `List.of()` so existing callers/tests compile unchanged (FR-006, FR-030). **Depends on T003.**
- [X] T012 [US1] Extend `backend/src/main/java/com/endia/infrastructure/llm/OpenAiLlmProvider.java` `generateSnack` to parse `node.path("keywords")`: for each object element read `term`/`definition` via `asText(null)`; skip non-object elements; missing/absent → empty list; malformed keyword elements never throw by themselves (FR-006). **Depends on T011.**
- [X] T013 [US1] Add a pure `normalizeKeywords(List<SnackKeyword>)` step to `backend/src/main/java/com/endia/application/service/SnackService.java` (drop nulls/blanks, trim, truncate definitions to the first 3 whitespace tokens, drop empty-after-truncation, case-insensitive dedupe preserving order) invoked in `generateValidSnack` before constructing the 6-arg `Snack`; **leave `isValid` unchanged** so keywords never cause a re-roll (FR-005, FR-007, FR-008, FR-009, FR-030). **Depends on T005, T011.**
- [X] T014 [P] [US1] Create `backend/src/main/java/com/endia/interfaces/rest/dto/SnackKeywordDto.java` as `record SnackKeywordDto(String term, String definition)` with `static SnackKeywordDto from(SnackKeyword)` (FR-006).
- [X] T015 [P] [US1] Add `List<SnackKeywordDto> keywords` as the last component of `SnackGenerateApiResponse.SnackItem` in `backend/src/main/java/com/endia/interfaces/rest/dto/SnackGenerateApiResponse.java` (FR-006).
- [X] T016 [P] [US1] Add `List<SnackKeywordDto> keywords` as the last component of `BatchStatusApiResponse.SnackDto` in `backend/src/main/java/com/endia/interfaces/rest/dto/BatchStatusApiResponse.java` (FR-006).
- [X] T017 [P] [US1] Add `List<SnackKeywordDto> keywords` as the last component of `QuizSummaryApiResponse.SnackDto` in `backend/src/main/java/com/endia/interfaces/rest/dto/QuizSummaryApiResponse.java` (FR-006).
- [X] T018 [P] [US1] Add `List<SnackKeywordDto> keywords` as the last component of `ThemeHistoryApiResponse.SnackItem` in `backend/src/main/java/com/endia/interfaces/rest/dto/ThemeHistoryApiResponse.java` (FR-006).
- [X] T019 [US1] Update the `SnackGenerateApiResponse.SnackItem` mapping in `backend/src/main/java/com/endia/interfaces/rest/SnackController.java` to map `s.getKeywords().stream().map(SnackKeywordDto::from).toList()` (FR-006). **Depends on T014, T015.**
- [X] T020 [US1] Update the `BatchStatusApiResponse.SnackDto` mapping in `backend/src/main/java/com/endia/interfaces/rest/BatchController.java` to pass `keywords` (`BatchService.BatchItemStatus` already carries the raw `Snack`, so no service change) (FR-006). **Depends on T014, T016.**
- [X] T021 [US1] Update the `QuizSummaryApiResponse.SnackDto` mapping in `backend/src/main/java/com/endia/interfaces/rest/QuizController.java` to pass `keywords` (`QuizService.Recap` already carries the raw `Snack`, so no service change) (FR-006). **Depends on T014, T017.**
- [X] T022 [US1] Update the `ThemeHistoryApiResponse.SnackItem` mapping in `backend/src/main/java/com/endia/interfaces/rest/ThemeController.java` to pass `keywords` (FR-006). **Depends on T014, T018, T023.**
- [X] T023 [US1] Add `List<SnackKeyword> keywords` to the `SnackHistory` record in `backend/src/main/java/com/endia/application/service/ThemeService.java` and populate it in the history mapping (FR-006, FR-011). **Depends on T003.**

**Checkpoint**: Keywords are generated, parsed, normalized at the service boundary, persisted, and returned on all four snack surfaces; the JSON shape is `[{term,definition}]` and no core behavior changed.

---

## Phase 4: User Story 2 — Key terms appear in a distinct color inside the snack text (Priority: P1)

**Goal**: The feature-012 typed AST gains a `keyword` inline node and a recursive `bold` node; a pure highlighter matches terms case-insensitively, literally, whole-word, all occurrences, longest-wins/non-overlapping, scoped to `concept`/`metaphor`/`whyItMatters`; matched terms render as keyboard-focusable `<button>`s in a distinct color on all surfaces. This phase covers milestone **M4 (highlight renderer)**.

**Independent Test**: With route-mocked snacks containing keywords, assert every occurrence of a present term renders with the highlight class in a distinct color, absent terms produce no artifact, case-insensitive and regex-like terms match literally, overlapping terms highlight only the longer one, and a term inside `**bold**` renders both `<strong>` and the keyword control.

### Tests for User Story 2 (write first, must FAIL) ⚠️

- [X] T024 [P] [US2] Create `frontend/e2e/snack-keywords.spec.ts` with route-mocked `POST /api/snacks/generate` fixtures asserting highlight behavior: every occurrence of a present term has the keyword class/color distinct from body text; absent terms produce no artifact; matching is case-insensitive; overlapping terms highlight only the longer with no nested spans; a term inside `**bold**` renders both `<strong>` and the keyword control; regex-like terms (`C++`, `(x)`, `a.b`) match literally (FR-013, FR-014, FR-015, FR-016, FR-017, SC-002).
- [X] T025 [P] [US2] Extend `frontend/e2e/theme-history.spec.ts` with a mocked `GET /api/themes` whose nested snacks carry keywords, asserting highlight rendering in the expanded history detail and no literal markers (FR-025, SC-009).
- [X] T026 [P] [US2] Extend `frontend/e2e/snack-keywords.spec.ts` with a cross-surface block asserting the same snack renders consistent highlighting on the snack card and the results recap (FR-025, SC-002).

### Implementation for User Story 2

- [X] T027 [P] [US2] Extend `frontend/src/app/components/rich-text/rich-text.model.ts`: add the `keyword` inline node `{ kind: 'keyword'; text: string; term: string; definition: string }` and make `bold` recursive `{ kind: 'bold'; nodes: RichInline[] }`; keep the output vocabulary closed (FR-013, FR-029, data-model.md §7).
- [X] T028 [US2] Create the pure `frontend/src/app/components/rich-text/keyword-highlighter.ts` implementing `highlightKeywords(blocks, keywords): RichBlock[]`: case-insensitive, literal index-scan (never compiled as patterns), whole-word boundary checks with edge exemption, all occurrences, longest-term-wins with a non-overlapping left-to-right cursor, recursive into bold children, never crossing a block/text-run boundary (FR-014, FR-015, FR-016, FR-017, FR-029, contracts §2). **Depends on T027.**
- [X] T029 [US2] Update `frontend/src/app/components/rich-text/rich-text.parser.ts` to `parseRichText(text, keywords?)` performing the existing base parse and, when keywords are non-empty, returning `highlightKeywords(baseBlocks, keywords)`; base grammar and entity decoding unchanged (FR-013, FR-014, FR-015, FR-016, FR-017). **Depends on T028.**
- [X] T030 [US2] Update `frontend/src/app/components/rich-text/rich-text.component.ts`: add `readonly keywords = input<SnackKeyword[]>([])` and `readonly keywordSelected = output<{ keyword: SnackKeyword; anchor: HTMLElement }>()`; make `blocks = computed(() => parseRichText(this.text(), this.keywords()))`; hold **no** card state (FR-013, FR-018). **Depends on T029.**
- [X] T031 [US2] Update `frontend/src/app/components/rich-text/rich-text.component.html` to render the recursive `bold` node as `<strong>` and the `keyword` node as `<button type="button" class="rich-text__keyword" (click)="onKeywordClick($event, node)">` displaying the original-cased substring — **no `[innerHTML]` anywhere** (FR-013, FR-029). **Depends on T030.**
- [X] T032 [P] [US2] Update `frontend/src/app/components/rich-text/rich-text.component.scss` with the keyword highlight color token, visually distinct from body text in light and dark themes, preserving feature-012 typography (FR-013).

**Checkpoint**: Keywords are highlighted in a distinct color on the snack card, recap, and history; feature-012 formatting is preserved; no HTML binding was introduced.

---

## Phase 5: User Story 3 — Clicking/tapping a highlighted term shows a short definition card (Priority: P1)

**Goal**: A standalone `KeywordDefinitionCardComponent` shows only the selected term's ≤3-word definition and is dismissible via outside pointerdown, a visible close control, and Escape, with focus moved in on open and returned to the anchor on close, fully usable at desktop and mobile widths. Exactly **one** card is mounted per page so opening another term closes the previous card. This phase covers milestone **M5 (definition card + surfaces)**.

**Independent Test**: With mocked keywords, click/tap a highlighted term and assert the card shows the correct definition; dismiss it via outside-click, close control, and Escape with no navigation; open a different term and assert the previous card closes; repeat at desktop and mobile viewports with no horizontal overflow.

### Tests for User Story 3 (write first, must FAIL) ⚠️

- [X] T033 [P] [US3] Extend `frontend/e2e/snack-keywords.spec.ts` with the definition-card block: click/tap opens a card showing only the ≤3-word definition; outside-click, the close control, and Escape each dismiss it with no navigation; opening a different term closes the previous and opens the new one; keyboard Tab + Enter opens and Escape returns focus to the term; repeat at a desktop and a mobile viewport with no horizontal overflow (FR-018, FR-019, FR-020, FR-021, FR-022, FR-023, SC-003, SC-004, SC-005).

### Implementation for User Story 3

- [X] T034 [P] [US3] Create `frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.ts` (standalone, `ChangeDetectionStrategy.OnPush`) with `keyword: SnackKeyword | null` / `anchor: HTMLElement | null` inputs and a `closed` output; dismiss on outside pointerdown, Escape, and close; move focus into the card on open and return it to the anchor on close; viewport-clamped positioning with a bottom-sheet fallback on narrow screens (FR-018, FR-019, FR-020, FR-021, FR-022, FR-023). **Depends on T002.**
- [X] T035 [P] [US3] Create `frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.html` with `role="dialog"`, a visible close control, and interpolation-only rendering of the term label and the definition (escaped) — **no `[innerHTML]`** (FR-019, FR-026, FR-027, FR-028).
- [X] T036 [P] [US3] Create `frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.scss` with responsive anchor/sheet layout and viewport clamping so the card never overflows horizontally (FR-022, SC-005).
- [X] T037 [US3] Wire `frontend/src/app/pages/snacks/snacks.component.ts` + `.html`: hold a single `activeKeyword`/`anchor` signal pair, render exactly one `<app-keyword-definition-card>` at page level, and pass `[keywords]="snack.keywords ?? []"` + `(keywordSelected)="openKeyword($event)"` to every `<app-rich-text>` (FR-018, FR-021, FR-024, FR-025). **Depends on T030, T031, T034–T036.**
- [X] T038 [US3] Wire `frontend/src/app/pages/results/results.component.ts` + `.html` with the same single-card pattern for the results recap (FR-018, FR-021, FR-025). **Depends on T030, T031, T034–T036.**
- [X] T039 [US3] Wire `frontend/src/app/pages/history/history.component.ts` + `.html` with the same single-card pattern for the read-only history view, leaving `related` untouched (FR-018, FR-021, FR-025).

**Checkpoint**: A single dismissible definition card per page opens from any highlighted term on all three surfaces, with keyboard support and responsive behavior.

---

## Phase 6: User Story 4 — Keyword text is always rendered as safe, escaped text (Priority: P1)

**Goal**: Untrusted keyword `term`/`definition` text is rendered through the same allowlist mechanism as body text; markup and injection payloads are inert and never executed or injected; readable text is preserved. The feature-012 security model (no `[innerHTML]`, no `DomSanitizer`) is preserved unchanged. This phase covers the security portion of milestone **M4/M6**.

**Independent Test**: Feed `<script>`, `<img onerror=...>`, `javascript:` URLs, `<iframe>`, `<style>`, and inline styles into keyword terms/definitions on all surfaces; assert no execution, no injected nodes, and readable text preserved; assert the card's definition text is escaped.

### Tests for User Story 4 (write first, must FAIL) ⚠️

- [X] T040 [P] [US4] Extend `frontend/e2e/snack-keywords.spec.ts` with a security block: keyword `term`/`definition` containing `<script>window.__xss=1</script>`, `<img src=x onerror="window.__xss=1">`, `javascript:` URLs, `<iframe>`, `<style>`, and inline styles; assert `window.__xss` stays `undefined`, no keyword-injected `<script>`/`<img>`/`<iframe>`/`<style>`/`<a>` nodes exist, readable text is preserved, and the definition card shows escaped text (FR-026, FR-027, FR-028, FR-029, SC-007).
- [X] T041 [P] [US4] Extend the same security block to the results recap and history surfaces, asserting the same neutralization guarantees (FR-025, FR-027, SC-007).

### Implementation for User Story 4

- [X] T042 [US4] Audit `frontend/src/app/components/rich-text/` (model, parser, highlighter, component, template) and `frontend/src/app/components/keyword-definition-card/` to confirm there is no `[innerHTML]`/`DomSanitizer`, that the only producible elements are `<p>/<ul>/<li>/<strong>/<br>/<button>`, and that all keyword text is emitted as escaped interpolation; add a short boundary comment documenting this (FR-026, FR-027, FR-028, FR-029, SC-007).

**Checkpoint**: Keyword text is safe; injection payloads are inert on every surface.

---

## Phase 7: User Story 5 — Keywords round-trip and older snacks degrade gracefully (Priority: P2)

**Goal**: Keyword data survives persistence and the API byte-for-byte (except FR-007 normalization) on every surface; snacks with null/missing/empty keywords and terms not present in the text render exactly as before; the existing flow and E2E suites are unregressed. This phase covers the persistence test (M2) and the regression close-out of milestone **M6**.

**Independent Test**: Persist a keyword-bearing snack and reload it via JPA (identical terms/definitions); save null/empty keywords and reload as an empty list; render empty/absent keywords and confirm no highlight, no card affordance, no artifact; run the existing suites with `keywords: []` fixtures.

### Tests for User Story 5 (write first, must FAIL) ⚠️

- [X] T043 [P] [US5] Create `backend/src/test/java/com/endia/application/integration/SnackKeywordsPersistenceIntegrationTest.java` (Testcontainers MySQL, tagged `integration`) asserting a snack saved with keywords reloads with identical `term`/`definition`, a snack saved with `null`/empty keywords reloads as an empty list (legacy-row simulation), and the JSON column round-trips (FR-011, FR-012, SC-008). **Depends on T005.**
- [X] T044 [P] [US5] Extend `frontend/e2e/snack-keywords.spec.ts` with graceful-degradation fixtures: `keywords: []`, absent/`null` keywords, and terms not present in the body render exactly as today with no highlight, no card affordance, no error, and no layout artifact; a very large keyword list remains stable (FR-024, SC-006).
- [X] T045 [P] [US5] Add `keywords: []` to the snack fixtures in `frontend/e2e/snack-rich-formatting.spec.ts`, `frontend/e2e/happy-path.spec.ts`, `frontend/e2e/progressive-delivery.spec.ts`, `frontend/e2e/snack-wait-messages.spec.ts`, `frontend/e2e/results-*.spec.ts`, `frontend/e2e/snacks-navigation.spec.ts`, and `frontend/e2e/theme-history.spec.ts`, then confirm those suites pass unchanged (FR-030, SC-009).

### Implementation for User Story 5

- [X] T046 [US5] Verify and record that **no** `BatchService.BatchItemStatus` / `QuizService.Recap` change is needed (they carry the raw `Snack`), that `application/dto/SnackGenerateResponse.java` remains untouched (unused), and that the feature adds no "Generate snack" card action or "meaning snack" entry; run the round-trip and persistence tests green (FR-030, FR-031, data-model.md §6).

**Checkpoint**: Keyword data integrity is proven; legacy/empty content degrades gracefully; no regressions.

---

## Phase 8: Polish & Cross-Cutting Concerns — Milestone M6 close-out

**Purpose**: Final validation and cleanup across all stories.

- [X] T047 [P] Run the quickstart.md manual verification checklist (`specs/013-snack-keywords/quickstart.md` §5) against all three surfaces, including the security payload table and the desktop/mobile card checks (SC-001..SC-009).
- [X] T048 [P] Confirm the rollback path: reverting `generate-snack.md`, the backend domain/converter/DTO changes, and the frontend renderer/card changes plus `ALTER TABLE snack DROP COLUMN keywords;` requires no data rewrite and is lossless (FR-012, data-model.md §5, quickstart.md §6).
- [X] T049 [P] Verify checklist-to-task traceability: every FR-001..FR-031 and SC-001..SC-009 is covered by at least one task, and every task maps to a requirement (no orphans); confirm `checklists/requirements.md`, `checklists/security.md`, and `checklists/ux.md` remain consistent with tasks.md.
- [X] T050 Run the full backend suite: `cd backend && ./mvnw test` (fall back to `mvn test` if no wrapper is present) and, where Docker is available, `./mvnw verify -Pintegration` (FR-030).
- [X] T051 Run the full frontend gate: `cd frontend && npm run build` and `npx playwright test`; confirm the new spec passes and all existing suites pass unchanged (FR-030, SC-009).
- [X] T052 Final completion validation: run `cd backend && ./mvnw test` (fallback `mvn test`) and the frontend build/lint (`cd frontend && npm run build` and `npx prettier --check .` — the repo has no ESLint script), confirming all tasks above are complete and every checklist item is checked (FR-030, SC-009).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2, M2)**: Depends on Setup — **blocks US1's contract tasks (T011–T023) and US5**.
- **US1 (Phase 3, M1 + M3)**: The prompt task **T010 (M1) depends on Setup only and is independent of Foundational** (may run in parallel with Phase 2). The parse/normalize/contract tasks **T011–T023 (M3) depend on M1 (T010) + Foundational (T003–T005)**.
- **US2 (Phase 4, M4)**: Depends on Setup only — **independent of M1–M3 and Foundational; may run in parallel with Phase 2 and Phase 3**.
- **US3 (Phase 5, M5)**: Depends on US2 (M4 — the `keywordSelected` output and keyword node) and the card directory (T002).
- **US4 (Phase 6)**: Depends on US2 (renderer) and US3 (card) for the cross-surface/card security assertions.
- **US5 (Phase 7, M2 test + M6)**: The persistence integration test depends on Foundational (T005); the degradation/regression assertions depend on US2 + US3 wiring.
- **Polish (Phase 8, M6 close)**: Depends on all desired stories being complete.

### Milestone Mapping

| Milestone | Phase(s) | Key tasks |
|-----------|----------|-----------|
| **M1** Prompt emits keywords | US1 | T006, T010 |
| **M2** Domain + persistence | Foundational | T003, T004, T005 |
| **M3** Parse + normalize + contract | US1 | T007, T008, T009, T011–T023 |
| **M4** Highlight renderer | US2 | T024–T032 |
| **M5** Definition card + surfaces | US3 | T033–T039 |
| **M6** Security + regression | US4, US5, Polish | T040–T052 |

### User Story Dependencies

- **US1 (P1)**: Can start after Setup; the prompt half (T010) is independent of other stories; the contract half needs Foundational.
- **US2 (P1)**: Can start after Setup; independent of US1 (fixtures are mocked).
- **US3 (P1)**: Starts after US2 (needs the `keywordSelected` output and keyword node).
- **US4 (P1)**: Starts after US2/US3 (asserts the renderer and card surfaces).
- **US5 (P2)**: Starts after Foundational; regression assertions rely on US2/US3 wiring.

### Within Each User Story

- Test tasks MUST be written and FAIL before the corresponding implementation task.
- Foundational types before entity; entity before service normalization.
- AST types before highlighter; highlighter before parser; parser before component.
- Shared card component before page wiring.
- Story complete and validated before moving to the next priority.

### Parallel Opportunities

- T001 and T002 (Setup) run in parallel.
- T003 and T004 (Foundational) run in parallel; T005 after T003/T004.
- **US1 prompt (T010) and the whole US2 phase (M4) can run in parallel with the Foundational phase.**
- Within US1: T006, T007, T008, T009 in parallel; T014–T018 in parallel (different DTO files); then T019–T022 in parallel (different controllers); T011–T013 sequential.
- Within US2: T024, T025, T026 in parallel; T027 and T032 in parallel; T028 after T027; T029 after T028; T030 after T029; T031 after T030.
- Within US3: T034, T035, T036 in parallel; then T037, T038, T039 in parallel (different pages).
- Within US4: T040 and T041 in parallel; T042 after.
- Within US5: T043, T044, T045 in parallel.
- Polish: T047, T048, T049 in parallel; T050 and T051 can run in parallel; T052 last.

---

## Parallel Example: User Story 1

```bash
# Write all US1 backend tests together (must fail first):
Task: "Extend GenerateSnackPromptTest with keyword prompt assertions"
Task: "Extend OpenAiLlmProviderTest with keyword parsing assertions"
Task: "Extend SnackServiceTest with normalization cases"
Task: "Extend SnackMarkupRoundTripTest with keywords on all surfaces + 8/8/8/6 counts"

# Then the DTOs (different files) in parallel:
Task: "Add keywords to SnackGenerateApiResponse.SnackItem"
Task: "Add keywords to BatchStatusApiResponse.SnackDto"
Task: "Add keywords to QuizSummaryApiResponse.SnackDto"
Task: "Add keywords to ThemeHistoryApiResponse.SnackItem"
```

## Parallel Example: User Story 3

```bash
# Create the card pieces together (different files):
Task: "Create keyword-definition-card.component.ts"
Task: "Create keyword-definition-card.component.html"
Task: "Create keyword-definition-card.component.scss"

# Then wire the three surfaces together (different pages):
Task: "Wire the single card into frontend/src/app/pages/snacks/"
Task: "Wire the single card into frontend/src/app/pages/results/"
Task: "Wire the single card into frontend/src/app/pages/history/"
```

---

## Implementation Strategy

### MVP First (US1 + US2)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (domain + persistence) — can proceed in parallel with M1 (T010) and US2 (M4).
3. Complete Phase 3: US1 (prompt + parse/normalize/contract).
4. Complete Phase 4: US2 (highlight on all surfaces).
5. **STOP and VALIDATE**: Run `frontend/e2e/snack-keywords.spec.ts` and inspect generated snacks — keywords are generated, persisted, and highlighted.
6. Deploy/demo the MVP.

### Incremental Delivery

1. Setup + Foundational → domain/persistence ready.
2. US1 + US2 → MVP (generation + highlight) → validate → demo.
3. US3 → definition card on all surfaces → validate → demo.
4. US4 → security hardening proven.
5. US5 → round-trip + graceful degradation proven.
6. Polish → full gates green.

### Notes

- `[P]` tasks touch different files with no incomplete dependency.
- M1 (prompt), M2 (domain+persistence), and M4 (highlight renderer) are parallelizable; M3 needs M1+M2; M5 needs M4; M6 closes.
- No new dependency, no new endpoint, no layout redesign; one additive nullable JSON column.
- **Out of scope**: no "Generate snack" button on the card and no "meaning snack" backbone entry (Task 9).
- Commit after each task or logical group; verify tests fail before implementing.

---

## FR Traceability Matrix

Every functional requirement (FR-001–FR-031) maps to at least one task. No gaps.

| FR | Description (short) | User Story | Task(s) |
|----|---------------------|------------|---------|
| FR-001 | Prompt emits `keywords` with ≤3-word definitions | US1 | T006, T010 |
| FR-002 | Prompt: terms appear verbatim in body text | US1 | T006, T010 |
| FR-003 | Prompt: existing fields/formatting unchanged | US1 | T006, T010 |
| FR-004 | Prompt: plain text only, no markup | US1 | T006, T010 |
| FR-005 | Non-trivial snacks have keywords; never fail/re-roll | US1 | T006, T008, T010, T013 |
| FR-006 | `keywords` on every snack-returning surface | US1 | T007, T009, T011–T023 |
| FR-007 | Definitions normalized to ≤3 words | US1 | T008, T013 |
| FR-008 | Blank terms/definitions dropped; empty valid, no re-roll | US1 | T008, T013 |
| FR-009 | Duplicate terms de-duplicated | US1 | T008, T013 |
| FR-010 | Keyword text treated as plain text | US1 | T010, T042 |
| FR-011 | Keywords persist and round-trip on every surface | Foundational, US1, US5 | T003, T004, T005, T009, T023, T043 |
| FR-012 | Backward-compatible nullable persistence | Foundational, US5 | T004, T005, T043 |
| FR-013 | Distinct highlight color for key terms | US2 | T024, T027, T028, T029, T030, T031, T032 |
| FR-014 | Only present terms highlighted; no artifacts | US2 | T024, T028, T029 |
| FR-015 | Case-insensitive, whole-word, literal matching | US2 | T024, T028 |
| FR-016 | Longest-wins, non-overlapping highlights | US2 | T024, T028 |
| FR-017 | Every occurrence highlighted | US2 | T024, T028 |
| FR-018 | Click/tap opens the definition card | US3 | T033, T034, T037, T038, T039 |
| FR-019 | Card shows only the ≤3-word definition | US3 | T033, T034, T035 |
| FR-020 | Dismiss via outside-click, close control, Escape; no nav | US3 | T033, T034, T035 |
| FR-021 | Opening another term closes the previous card | US3 | T033, T034, T037, T038, T039 |
| FR-022 | Desktop + mobile, no overflow | US3 | T033, T034, T036 |
| FR-023 | Keyboard activation + focus return | US3 | T033, T034 |
| FR-024 | Empty/absent keywords render unchanged | US5 | T037, T044 |
| FR-025 | Consistent across snack card, recap, history | US3, US4 | T025, T026, T037, T038, T039, T041 |
| FR-026 | Keyword text escaped via safe allowlist | US4 | T035, T040, T042 |
| FR-027 | Markup/handlers/URLs not executed or injected | US4 | T040, T041, T042 |
| FR-028 | Readable text preserved when neutralized | US4 | T040, T042 |
| FR-029 | Feature-012 sanitization model preserved | US2, US4 | T027, T031, T040, T042 |
| FR-030 | Existing behavior/E2E unchanged | US5, Polish | T001, T011, T013, T045, T046, T050, T051, T052 |
| FR-031 | No card "Generate snack", no "meaning snack" entry | US5 | T046 |

## SC Traceability Matrix

Every success criterion (SC-001–SC-009) maps to at least one task. No gaps.

| SC | Description (short) | Task(s) |
|----|---------------------|---------|
| SC-001 | 100% keywords; 0 over-limit definitions | T006, T008, T010 |
| SC-002 | 100% present terms highlighted; 0 false highlights | T024, T026 |
| SC-003 | 100% correct card content; 0 cards >3 words | T033 |
| SC-004 | 100% dismiss via all three means; 0 navigation | T033 |
| SC-005 | 100% desktop+mobile; 0 overflow | T033, T036 |
| SC-006 | 0 artifacts for empty/missing keywords | T044 |
| SC-007 | 0 executions / 0 injected nodes from keyword payloads | T040, T041 |
| SC-008 | 100% round-trip across all surfaces | T009, T043 |
| SC-009 | Non-regression; feature-012 formatting intact | T001, T025, T045, T050, T051, T052 |

**Coverage summary**: 31/31 FRs covered; 9/9 SCs covered. FR/SC gaps: **none**. Out-of-scope (Task 9) items intentionally excluded.
