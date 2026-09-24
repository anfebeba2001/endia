---
description: "Task list for feature 015-keyword-meaning-20-words"
---

# Tasks: Expand Keyword Meaning Limit from 3 to 20 Words

**Input**: Design documents from `/specs/015-keyword-meaning-20-words/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, quickstart.md

**Tests**: Tests are **required** for this feature (spec §FR-015/§SC-003/§SC-004 and plan.md §Testing Strategy). Test tasks are written/updated **first**, before the corresponding implementation, per TDD ordering.

**Organization**: Tasks are grouped by the plan's milestones **M1** (backend length policy), **M2** (prompt + docs), **M3** (backend tests), **M4** (frontend rendering verification + focused test), **M5** (regression + consistency), and are ordered so test updates fail before their implementation lands.

**Scope guard**: This is a small, focused policy change. It adds **no** endpoint, entity, user action, port method, runtime dependency, or schema change. Out of scope: Tasks 11/12 (custom-selection "Define" / "Create snack"), snack content fields (`concept`/`metaphor`/`whyItMatters`), the `related` 2–3 **item** cap, the 2–5 keyword-count guideline, and quiz behavior (FR-007, FR-017).

## Format: `[ID] [P?] [Milestone] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Milestone]**: The plan milestone the task belongs to (M1–M5)
- Every task includes exact file path(s) and the FR/SC references it satisfies

## Path Conventions

- **Backend**: `backend/src/main/...`, `backend/src/test/...`
- **Frontend**: `frontend/src/app/...`, `frontend/e2e/...`
- **Feature docs**: `specs/015-keyword-meaning-20-words/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the narrow scope and record the pre-change baseline before any edit.

- [X] T001 [P] [M5] Confirm no new runtime dependency is required (no `backend/pom.xml` and no `frontend/package.json` change) and record the pre-change baseline by running `cd backend && ./mvnw test` (fallback `mvn test`) and `cd frontend && npx playwright test` — the baseline for FR-016/FR-017/SC-006 (plan.md §Technical Context; research.md R7).
- [X] T002 [P] [M1] Confirm the no-schema-change baseline: verify there is no migration tool, that `SnackKeyword.definition` is stored inside the `snack.keywords` JSON column (no character constraint) and that `MeaningSnack.sourceDefinition` is an existing `@Column(length = 200)` `VARCHAR(200)` — no DDL is emitted for this feature (FR-007, FR-017, data-model.md §1/§4/§5).

---

## Phase 2: Tests (update/write first — must FAIL before implementation) — Milestone M3

**Purpose**: Move every test that asserts the old 3-token cap to the 20-token rule and add the 19/20/21 boundary and FR-019 character-clamp coverage. These tasks **MUST** be completed and observed to FAIL against the unmodified services/prompt before T007–T009 land.

- [X] T003 [P] [M3] Update `backend/src/test/java/com/endia/application/service/SnackServiceTest.java`: rename `overLimitDefinitionIsTruncatedToFirstThreeTokens` → `overLimitDefinitionIsTruncatedToFirstTwentyTokens` and change its input/assertion so a 21+-token definition is truncated to exactly its first 20; add boundary tests (19 tokens kept in full, 20 kept in full, 21 truncated to 20); keep `blankTermOrDefinitionIsDropped` and `definitionBlankAfterTruncationIsDropped` passing unchanged (FR-001, FR-002, FR-005, FR-006, FR-015, SC-003).
- [X] T004 [P] [M3] Update `backend/src/test/java/com/endia/application/service/MeaningSnackServiceTest.java`: rename `definitionIsTruncatedToFirstThreeTokens` → `definitionIsTruncatedToFirstTwentyTokens`; assert a 4–20-token source definition passes through unchanged (e.g. `"light to sugar and more words"` is now kept in full); add 19/20/21 boundary cases mirroring the snack-keyword rule; add FR-019 char-clamp cases — a first-20-token source definition exceeding 200 characters is clamped to ≤200 chars **on a word boundary** (no word split; result non-empty), a ≤200-char definition is unchanged, and a single token longer than 200 chars falls back to a hard 200-char cut (FR-003, FR-015, FR-019, SC-003, SC-008).
- [X] T005 [P] [M3] Update `backend/src/test/java/com/endia/infrastructure/llm/GenerateSnackPromptTest.java`: rename `promptLimitsDefinitionsToThreeWordsAndGuidesTwoToFive` → `promptLimitsDefinitionsToTwentyWordsAndGuidesTwoToFive`; assert the rendered prompt contains `"20 words"` or `"twenty words"` and does **not** contain `"3 words"`/`"three words"`; still assert the `"2-5"`/`"2–5"` keyword guideline (FR-008, FR-015, SC-007).
- [X] T006 [P] [M4] Update `frontend/e2e/snack-keywords.spec.ts`: change the stale `≤3-word` header comment (line ~12), the test title (line ~326), and replace `expect(...split(/\s+/).length).toBeLessThanOrEqual(3)` (line ~338) with a 20-word check; add a focused assertion that a 20-word definition renders in `.keyword-card__definition` with **no horizontal overflow** (`document.documentElement.scrollWidth <= window.innerWidth + 1`) at desktop and narrow widths, modeled on the existing `very long generated content … no horizontal overflow` test in `meaning-snacks.spec.ts` (FR-012, FR-015, SC-004).

---

## Phase 3: Core — Milestones M1 (length policy) + M2 (prompt + docs)

**Purpose**: Raise the bound from 3 to 20 in the two service normalization helpers (with the FR-019 200-char word-boundary clamp on the source definition only) and correct the prompt and documentation surfaces. No behavior change beyond the bound.

### M1 — Backend length policy

- [X] T007 [P] [M1] Edit `backend/src/main/java/com/endia/application/service/SnackService.java`: add `private static final int MAX_DEFINITION_TOKENS = 20;`; change `tokens.length > 3` / `Arrays.copyOf(tokens, 3)` to use the constant; update the `normalizeKeywords` comment from "first 3 whitespace-separated tokens" to 20. Trim, blank-drop, order-preserving and case-insensitive de-duplication are untouched; **no** character clamp is added here (JSON column) (FR-001, FR-002, FR-005, FR-006, FR-007).
- [X] T008 [P] [M1] Edit `backend/src/main/java/com/endia/application/service/MeaningSnackService.java`: add the same `MAX_DEFINITION_TOKENS = 20` constant and change the bound; add `private static final int MAX_SOURCE_DEFINITION_CHARS = 200;` and, **after** the token cap, clamp the normalized definition to ≤200 characters on a word boundary (`lastIndexOf(' ', 200)`, falling back to a hard 200-char cut for a single over-long token); update the `normalizeDefinition` javadoc from 3 → 20 (FR-003, FR-019, SC-008).

### M2 — Prompt + documentation

- [X] T009 [P] [M2] Edit `backend/src/main/resources/prompts/generate-snack.md`: change the example JSON `"definition": "at most three words"` → `"at most twenty words"` (both occurrences) and the explicit rule `at most 3 words (never more than three words)` → `at most 20 words (never more than twenty words)`. The 2–5 keyword guideline, term-verbatim rule, plain-text rule, formatting whitelist and forbidden list are unchanged (FR-008, FR-010).
- [X] T010 [P] [M2] Edit `backend/src/main/resources/prompts/generate-meaning-snack.md`: **wording only** — replace the "very short definition" / "short definition" descriptions of the supplied definition with wording accurate for a source definition of up to 20 words. The JSON shape, CONTENT RULES, formatting rules, forbidden list and "no quiz / no keywords" rules are unchanged (FR-018).
- [X] T011 [P] [M2] Edit `backend/src/main/java/com/endia/domain/model/SnackKeyword.java`: javadoc `at most 3 words` → `at most 20 words`. No signature/serialization change (FR-009).
- [X] T012 [P] [M2] Edit `backend/src/main/java/com/endia/interfaces/rest/dto/SnackKeywordDto.java`: javadoc `at most 3 words, guaranteed by service normalization` → 20 words. No field/shape change (FR-009).
- [X] T013 [P] [M2] Edit `frontend/src/app/models/snack.model.ts`: comment `(at most 3 words)` → `(at most 20 words)`. The `SnackKeyword` interface is unchanged (FR-009).

---

## Phase 4: Integration — Milestone M4 (frontend rendering verification)

**Purpose**: Verify (not redesign) that the definition card and highlighted-keyword rendering stay readable at up-to-20-word definitions and keep the feature-012/013/014 sanitization model. A change to these files is justified **only** if a verification failure is found (plan.md §Risks).

- [X] T014 [M4] Verify `frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.html` + `.scss` at an up-to-20-word definition: the full definition is shown via escaped interpolation only (**no** `[innerHTML]`, **no** `DomSanitizer`), the card keeps its width clamp `min(280px, calc(100vw - 16px))` and `overflow-wrap: anywhere`, grows naturally with no clipping and no horizontal overflow at desktop and narrow widths, and the narrow-viewport bottom-sheet fallback still applies (FR-011, FR-012, FR-013, FR-014, SC-004, SC-005).
- [X] T015 [P] [M4] Verify `frontend/src/app/components/rich-text/rich-text.component.html` + `.scss` and `frontend/src/app/components/rich-text/keyword-highlighter.ts`: keyword `term` matching stays verbatim/case-insensitive and unaffected by definition length (definitions are carried as data only), highlight buttons render escaped interpolation only with no `[innerHTML]`, and highlight wrapping is intact at longer definitions (FR-012, FR-013, FR-014, FR-016).

---

## Phase 5: Polish & Cross-Cutting Concerns — Milestone M5 (regression + consistency)

**Purpose**: Prove the change is consistent, bounded and non-regressive across both projects.

- [X] T016 [P] [M5] Grep the surfaces changed by this feature (`SnackService.java`, `MeaningSnackService.java`, `SnackKeyword.java`, `SnackKeywordDto.java`, `snack.model.ts`, `generate-snack.md`, `generate-meaning-snack.md`, `GenerateSnackPromptTest.java`) for stale wording — `at most 3 words`, `3 words`, `three words`, `≤3`, `<= 3`, `very short`, `short definition` — and fix any in-scope leftover. Do **not** rewrite historical `specs/013-*`/`specs/014-*` artifacts (they are outside SC-007 scope) (FR-009, FR-018, SC-007).
- [X] T017 [P] [M5] Run the full backend suite `cd backend && ./mvnw test` (fallback `mvn test`) and, where Docker is available, `./mvnw verify -Pintegration`; confirm the updated unit tests pass and the unchanged MockMvc contract / Testcontainers integration tests (`SnackMarkupRoundTripTest`, `MeaningSnackContractTest`, `SnackKeywordsPersistenceIntegrationTest`, `MeaningSnackPersistenceIntegrationTest`) remain green (FR-016, FR-017, FR-019, SC-006, SC-008).
- [X] T018 [P] [M5] Run the full frontend gate `cd frontend && npm run build` (type-check) and `npx playwright test`; confirm the updated `snack-keywords.spec.ts` passes and all existing suites (`happy-path`, `progressive-delivery`, `snack-wait-messages`, `results-*`, `snacks-navigation`, `snack-rich-formatting`, `meaning-snacks`, `theme-history`, …) pass unchanged (FR-012, FR-016, SC-004, SC-006).
- [X] T019 Final completion validation: run both gates (`cd backend && ./mvnw test` fallback `mvn test`; `cd frontend && npm run build` and `npx playwright test`), confirm every task above is complete, every `checklists/*.md` item is checked, and every FR-001..FR-019 / SC-001..SC-008 is covered by at least one task with no orphans (all FRs/SCs).

---

## Dependencies & Execution Order

### Milestone / Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Tests (Phase 2, M3)**: Depends on Setup; T003–T006 are written/updated first and **must fail** before T007–T009 land.
- **Core (Phase 3, M1 + M2)**: M1 (T007, T008) and M2 (T009–T013) are independent of each other and may run in parallel; both depend on Setup only.
- **Integration (Phase 4, M4)**: Rendering verification does **not** depend on the normalization constant and may run in parallel with M1/M2; it depends on Setup only.
- **Polish (Phase 5, M5)**: Depends on M1 + M2 + M3 + M4.

### Milestone Mapping

| Milestone | Phase | Key tasks |
|-----------|-------|-----------|
| **M1** Backend length policy | Core | T002, T007, T008 |
| **M2** Prompt + docs | Core | T009, T010, T011, T012, T013 |
| **M3** Backend tests | Tests | T003, T004, T005 |
| **M4** Frontend rendering verification + focused test | Tests, Integration | T006, T014, T015 |
| **M5** Regression + consistency | Setup, Polish | T001, T016, T017, T018, T019 |

### Within Each Phase

- Test updates (T003–T006) before their implementation (T007–T009) / verification (T014).
- `SnackService` (T007) and `MeaningSnackService` (T008) are separate files and independent.
- All M2 edits (T009–T013) touch different files and are independent.
- Do not edit the card/rich-text files unless T014/T015 reveals a failure (then apply the agreed fallback: a bounded `max-height` with internal scroll — Clarification #3).

### Parallel Opportunities

- T001 and T002 (Setup) run in parallel.
- T003, T004, T005, T006 (Tests) run in parallel (different files).
- T007 and T008 (M1) run in parallel; T009, T010, T011, T012, T013 (M2) run in parallel; M1 and M2 run in parallel.
- T014 and T015 (M4 verification) run in parallel and may run in parallel with M1/M2.
- T016, T017, T018 (Polish) run in parallel; T019 last.

---

## Implementation Strategy

1. Setup (T001–T002) → baseline recorded, scope confirmed.
2. Tests (T003–T006) → updated and failing.
3. Core (T007–T013) → M1 and M2 in parallel; tests now pass.
4. Integration (T014–T015) → card/highlight verified at 20 words.
5. Polish (T016–T019) → grep clean, both suites green, final validation.

---

## FR Traceability Matrix

Every functional requirement (FR-001–FR-019) maps to at least one task. No gaps.

| FR | Description (short) | Milestone | Task(s) |
|----|---------------------|-----------|---------|
| FR-001 | Accept/store/return keyword definitions up to 20 tokens | M1 | T003, T007 |
| FR-002 | Truncate >20 tokens to first 20 before persist/serve | M1 | T003, T007 |
| FR-003 | Same 20-token rule for the meaning-snack source definition | M1 | T004, T008 |
| FR-004 | 4–20-word definitions survive without 3-word truncation | M1 | T003, T004, T007, T008 |
| FR-005 | Whitespace-separated token rule (hyphenated = one) | M1 | T003, T007, T008 |
| FR-006 | Blank/trim/de-dupe/order behavior unchanged | M1 | T003, T004, T007 |
| FR-007 | Cap applies only to keyword meanings; content/`related` untouched | M1 | T002, T007, T008 |
| FR-008 | Snack prompt example JSON + rule state 20 words | M2 | T005, T009 |
| FR-009 | Update 3→20 javadoc/comments in changed surfaces | M2 | T011, T012, T013, T016 |
| FR-010 | Flow stays externalized/provider-agnostic | M2 | T001, T009 |
| FR-011 | Card shows full definition, escaped interpolation only | M4 | T014 |
| FR-012 | Card/highlight readable at 20 words, no overflow/clipping | M4 | T006, T014, T018 |
| FR-013 | Injection payloads never executed/injected | M4 | T014, T015 |
| FR-014 | feature-012/013/014 sanitization model preserved | M4 | T014, T015 |
| FR-015 | 19/20/21 boundary tests + focused frontend no-overflow assertion | M3/M4 | T003, T004, T005, T006 |
| FR-016 | Existing suites pass; 013/014 behavior not regressed | M5 | T001, T015, T017, T018 |
| FR-017 | No new endpoint/entity/action/schema change; no migration | M5 | T001, T002, T017 |
| FR-018 | Meaning-snack prompt wording only (no JSON/rule change) | M2 | T010, T016 |
| FR-019 | Source definition ≤200 chars on a word boundary (source only) | M1 | T004, T008, T016, T017 |

## SC Traceability Matrix

Every success criterion (SC-001–SC-008) maps to at least one task. No gaps.

| SC | Description (short) | Task(s) |
|----|---------------------|---------|
| SC-001 | 4–20-word definitions persisted/returned/displayed in full | T003, T004, T007, T008, T014 |
| SC-002 | 0 stored/returned definitions exceed 20 tokens | T003, T004, T007, T008 |
| SC-003 | 19/20/21 boundary tests pass for both surfaces | T003, T004 |
| SC-004 | 20-word card wraps with no horizontal overflow/clipping | T006, T014, T018 |
| SC-005 | 0 executable/injected markup from any definition | T014, T015 |
| SC-006 | Existing backend/frontend suites pass unchanged | T001, T017, T018, T019 |
| SC-007 | 0 stale "at most 3 words" statements in changed surfaces | T005, T009, T010, T011, T012, T013, T016 |
| SC-008 | 0 persisted source definitions exceed 200 chars; 0 words split | T004, T008, T016, T017 |

**Coverage summary**: 19/19 FRs covered; 8/8 SCs covered. FR/SC gaps: **none**.

---

## Notes

- `[P]` tasks touch different files with no incomplete dependency.
- No new runtime dependency, no new layer, no new endpoint/entity, no schema change.
- Historical `specs/013-*`/`specs/014-*` artifacts are intentionally **not** rewritten (SC-007 scope).
- Commit after each task or logical group; verify the updated tests fail before implementing.
