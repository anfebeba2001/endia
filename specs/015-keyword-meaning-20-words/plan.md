# Implementation Plan: Expand Keyword Meaning Limit from 3 to 20 Words

**Branch**: `015-keyword-meaning-20-words` | **Date**: 2026-09-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/015-keyword-meaning-20-words/spec.md`

## Summary

A **small, focused policy change** to the keyword-meaning feature delivered by `014-meaning-snacks`
(built on the colored-keyword highlight and definition card of `013-snack-keywords`). Today a keyword
`definition` ("meaning") is capped at **at most 3 words** in three coordinated places — the generation
prompt, the backend normalization at the service boundary, and the docs/comments that state the rule.
This feature raises that cap to **at most 20 words** consistently, so keyword meanings can be richer,
while changing nothing else about snacks, quizzes, meaning snacks, or the existing keyword interaction.

The change is deliberately narrow:

1. **Length policy (FR-001..FR-007, FR-019)** — the two service-boundary normalization helpers change
   their bound from 3 to 20: `SnackService.normalizeKeywords` and `MeaningSnackService.normalizeDefinition`.
   Truncation (never reject/re-roll), blank-dropping, trim and case-insensitive de-duplication are all
   unchanged; only the constant changes. Additionally, the meaning-snack **source definition** is
   safely bounded to **at most 200 characters on a word boundary** (decision (a), FR-019) so it always
   fits the existing `VARCHAR(200)` column; `SnackKeyword.definition` lives in a JSON column and needs
   no character bound.
2. **Prompt (FR-008, FR-010, FR-018)** — `generate-snack.md` example JSON and explicit rule move from
   "at most three words" to "at most twenty words"; `generate-meaning-snack.md` is reworded so it no
   longer calls the supplied definition "very short"/"short" (wording only — JSON shape and content
   rules unchanged). The flow stays externalized and provider-agnostic.
3. **Documentation (FR-009)** — javadoc/comments stating the old 3-word limit in `SnackKeyword`,
   `SnackKeywordDto`, `snack.model.ts`, and the two service comments are corrected to 20.
4. **Rendering (FR-011..FR-014)** — **verification only** (no structural change): the definition card
   already clamps its width, uses `overflow-wrap: anywhere`, and renders via escaped interpolation
   only. A focused frontend assertion is added for a 20-word definition wrapping with no horizontal
   overflow (FR-015 / SC-004).
5. **Tests (FR-015, FR-016)** — the three tests that assert the 3-token cap are updated to 20 with
   added 19/20/21 boundary coverage; the stale `≤3-word` comment/assertion in
   `frontend/e2e/snack-keywords.spec.ts` is updated; existing suites must pass unchanged.

The feature adds **no new endpoint, no new entity, no new port method, no schema change, and no new
runtime dependency**. Definitions were already stored as free text/JSON; previously stored 3-word
definitions remain valid and are **not** migrated (FR-017).

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.4.x (backend); TypeScript 5.9.x + Angular 21.2.x
(frontend, standalone components, signals-first, `ChangeDetectionStrategy.OnPush`).

**Primary Dependencies**: Spring Web / Spring Data JPA / Hibernate / Jackson (backend); Angular core,
Router, signals, RxJS (frontend). **No new runtime dependency in either project.**

**Storage**: MySQL 8.x. **No schema change.** `SnackKeyword.definition` lives inside the `snack`
`keywords` JSON column (`JpaSnackKeywordListConverter`, unconstrained); `MeaningSnack.sourceDefinition`
is an existing `VARCHAR(200)` column and is clamped to ≤200 chars on a word boundary by the service
(FR-019). No migration, no `ddl-auto` impact.

**Testing**:
- Backend: JUnit 5 + Mockito unit tests; MockMvc contract tests; Testcontainers MySQL integration
  tests (tagged `integration`, `mvn verify -Pintegration`). No new test infrastructure.
- Frontend: Playwright E2E only — `frontend/package.json` has **no** Karma/Jest/Vitest runner wired
  (consistent with features 012/013/014). `npm run build` type-checks.

**Target Platform**: Modern desktop + mobile browsers (Chrome/Firefox/Safari last 2 versions); Linux
server.

**Project Type**: Web-application monorepo (`backend/` + `frontend/`).

**Performance Goals**: No measurable change. Truncation is a single `split("\\s+")` on a bounded
string; prompt text is unchanged in size class.

**Constraints**:
- Constant/policy change only; **no** structural or layer change; dependency direction preserved.
- LLM-Abstraction principle: only prompt text changes; no provider-specific logic in services.
- Security model (feature 012/013/014) preserved unchanged: escaped interpolation only, no
  `[innerHTML]`, no `DomSanitizer`.
- Backend is the single normalization boundary; the frontend renders whatever valid content it
  receives.
- Existing backend/frontend/E2E suites are the regression contract and are updated (not replaced).
- Out of scope: Tasks 11/12 (custom-selection "Define" / "Create snack"); snack content fields
  (`concept`/`metaphor`/`whyItMatters`); the `related` 2–3 **item** cap; keyword count guideline
  (2–5); quiz behavior.

**Scale/Scope**: 4 backend main files + 2 prompts + 3 backend test files; 1 frontend model comment +
card/rich-text rendering verification + 1 focused E2E assertion + 1 stale E2E comment/assertion.
No new classes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment | Verdict |
|-----------|------------|---------|
| **I. Spec-Driven Development** | `spec.md` exists with 19 FRs and 8 SCs and is the source of truth; this `plan.md` and downstream `tasks.md` follow. | PASS |
| **II. Clean Architecture (Layered)** | Only the normalization bound inside `application/service` changes; the domain record (`SnackKeyword`) and DTO (`SnackKeywordDto`) receive comment-only edits. No class, layer, or dependency-direction change. | PASS |
| **III. LLM Abstraction First** | Only externalized prompt text changes (`generate-snack.md`, `generate-meaning-snack.md`); no provider class or provider-specific logic is introduced (FR-010). | PASS |
| **IV. Quality Gate for Generated Content** | The service boundary still validates/normalizes (now ≤20 tokens); blank handling, de-duplication and no-re-roll behavior are unchanged. | PASS |
| **V. MVP-First, Iterate / YAGNI** | Smallest viable change: a constant, prompt wording, comments and tests. No new endpoint/entity/dependency, no card redesign (verification only). | PASS |

**Re-check after Phase 1 design**: No new violations. **Complexity Tracking table is intentionally
empty.**

## Project Structure

### Documentation (this feature)

```text
specs/015-keyword-meaning-20-words/
├── plan.md              # This file
├── research.md          # Phase 0 output (tokenization, constant, natural card growth, doc surfaces)
├── data-model.md        # Phase 1 output (no schema change; existing fields; 20-token policy)
├── quickstart.md        # Phase 1 output (run + manual verification + rollback)
├── contracts/
│   ├── keyword-definition-length-contract.md   # touched contracts; shapes unchanged, length rule 20
│   └── keyword-definition-length.sample.json   # example payloads (snack keywords, meaning-snack)
├── checklists/
│   ├── requirements.md  # (existing — provided with the spec)
│   ├── security.md      # (existing — injection/escaping requirements checklist)
│   └── ux.md            # (existing — definition-card/highlight readability checklist)
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root) — files touched by this feature

```text
backend/
└── src/
    ├── main/
    │   ├── resources/prompts/
    │   │   ├── generate-snack.md                     # [EDIT] example JSON + rule: 3 → 20 (FR-008)
    │   │   └── generate-meaning-snack.md             # [EDIT] wording only: "very short"/"short" → up to 20 (FR-018)
    │   └── java/com/endia/
    │       ├── application/service/
    │       │   ├── SnackService.java                 # [EDIT] normalizeKeywords bound 3 → 20 + comment (FR-001/002)
    │       │   └── MeaningSnackService.java          # [EDIT] normalizeDefinition bound 3 → 20 + 200-char word-boundary clamp + javadoc (FR-003, FR-019)
    │       ├── domain/model/
    │       │   └── SnackKeyword.java                 # [EDIT] javadoc "at most 3 words" → 20 (FR-009)
    │       └── interfaces/rest/dto/
    │           └── SnackKeywordDto.java              # [EDIT] javadoc "at most 3 words" → 20 (FR-009)
    └── test/java/com/endia/
        ├── application/service/
        │   ├── SnackServiceTest.java                 # [EDIT] 3-token test → 20 + 19/20/21 boundary (FR-015)
        │   └── MeaningSnackServiceTest.java          # [EDIT] 3-token test → 20 + 19/20/21 boundary (FR-015)
        └── infrastructure/llm/
            └── GenerateSnackPromptTest.java          # [EDIT] assert 20-word limit, not 3 (FR-008/FR-015)

frontend/
├── src/app/
│   ├── models/
│   │   └── snack.model.ts                            # [EDIT] comment "at most 3 words" → 20 (FR-009)
│   └── components/
│       ├── keyword-definition-card/
│       │   ├── keyword-definition-card.component.html  # [VERIFY] full definition, escaped only (FR-011)
│       │   └── keyword-definition-card.component.scss  # [VERIFY] width clamp + overflow-wrap: anywhere (FR-012)
│       └── rich-text/
│           ├── rich-text.component.html             # [VERIFY] keyword button escaped interpolation only (FR-014)
│           ├── rich-text.component.scss             # [VERIFY] highlight wrapping (FR-012)
│           └── keyword-highlighter.ts               # [VERIFY] term-verbatim matching unaffected (FR-016)
└── e2e/
    └── snack-keywords.spec.ts                       # [EDIT] stale "≤3-word" comment + `<= 3` assertion (FR-015)
```

**Structure Decision**: Web-application monorepo; the existing layered layout
(`domain → application → infrastructure → interfaces`) does **not** change. This is a constant/policy
change plus documentation and tests. Backend and frontend edits are independent; the frontend card and
highlight files are **verification targets**, not redesigns — a change there is only justified if a
verification failure is found (see Risks).

### Exact integration call sites

| Surface | Component | File(s) | Change |
|---------|-----------|---------|--------|
| Regular snack keyword normalization | `SnackService.normalizeKeywords` | `application/service/SnackService.java` | bound `> 3` → `> 20`; `copyOf(tokens, 3)` → `copyOf(tokens, 20)`; comment |
| Meaning-snack source normalization | `MeaningSnackService.normalizeDefinition` | `application/service/MeaningSnackService.java` | same token bound change; 200-char word-boundary clamp (FR-019); javadoc |
| Generation prompt | prompt template | `resources/prompts/generate-snack.md` | example JSON + explicit rule |
| Meaning-snack prompt | prompt template | `resources/prompts/generate-meaning-snack.md` | wording only |
| Definition card | `KeywordDefinitionCardComponent` | `components/keyword-definition-card/*` | verify at 20 words |
| Highlight | `RichTextComponent` + `keyword-highlighter.ts` | `components/rich-text/*` | verify readability |

`related` rendering, all feature-012/013/014 rendering paths, and every response DTO are unchanged.

## Design

### Backend

**`SnackService.normalizeKeywords` [EDIT]** — the only behavioral change. The current code
(`SnackService.java` ~lines 144–170) splits each definition on `\\s+` and, when
`tokens.length > 3`, joins `Arrays.copyOf(tokens, 3)`. Change the bound to 20. Recommended form: a
private named constant instead of the bare literal, so the policy is stated once per service:

```java
private static final int MAX_DEFINITION_TOKENS = 20;
...
String[] tokens = definition.split("\\s+");
if (tokens.length > MAX_DEFINITION_TOKENS) {
    definition = String.join(" ", Arrays.copyOf(tokens, MAX_DEFINITION_TOKENS));
}
```

The surrounding trim, blank-drop, order-preserving and case-insensitive de-duplication steps are
**untouched**. The existing comment ("truncates each definition to the first 3 whitespace-separated
tokens") is updated to 20.

**`MeaningSnackService.normalizeDefinition` [EDIT]** — the same token bound change (`> 3` → `> 20`,
`copyOf(tokens, 3)` → `copyOf(tokens, 20)`) and the same named constant, mirroring `SnackService` so
the two rules stay visibly identical (FR-003). The javadoc ("Truncate the source definition to the
first 3 whitespace-separated tokens, mirroring …") is updated to 20.

In addition — and **only here** — the normalized source definition is clamped to **at most 200
characters on a word boundary** (decision (a), FR-019), because it is persisted into the existing
`meaning_snack.source_definition VARCHAR(200)` column and no schema change is permitted (FR-017):

```java
private static final int MAX_DEFINITION_TOKENS = 20;      // token cap (FR-001..FR-003)
private static final int MAX_SOURCE_DEFINITION_CHARS = 200; // VARCHAR(200) fit (FR-019)
...
// 1. token cap (as above), then:
// 2. char clamp without splitting a word:
if (definition.length() > MAX_SOURCE_DEFINITION_CHARS) {
    int cut = definition.lastIndexOf(' ', MAX_SOURCE_DEFINITION_CHARS);
    definition = cut > 0 ? definition.substring(0, cut) : definition.substring(0, MAX_SOURCE_DEFINITION_CHARS);
}
```

The clamp never splits a word: it backs up to the last whitespace at or before index 200. The
degenerate single-token-longer-than-200 case falls back to a hard 200-char cut (still non-empty, so
the existing blank-handling is unaffected). `SnackService.normalizeKeywords` does **not** receive this
clamp — `SnackKeyword.definition` is stored inside the `snack.keywords` JSON column and is not
character-constrained.

**`SnackKeyword` / `SnackKeywordDto` [EDIT]** — javadoc only: "at most 3 words" → "at most 20 words".
No signature, serialization or converter change (definitions stay free text inside the `keywords`
JSON).

**`generate-snack.md` [EDIT]** — three edits, all in the KEYWORDS RULES block:
- example JSON: `"definition": "at most three words"` → `"at most twenty words"` (both occurrences);
- explicit rule: `a very short gloss of at most 3 words (never more than three words)` →
  `a gloss of at most 20 words (never more than twenty words)`.

The keyword count guideline (2–5), term-verbatim rule, plain-text rule, formatting whitelist and
forbidden-markup list are unchanged (FR-008, FR-016).

**`generate-meaning-snack.md` [EDIT]** — wording only: replace "very short definition" / "short
definition" descriptions with wording accurate for a source definition of up to 20 words (FR-018).
The JSON shape, CONTENT RULES (including `related` 1–3), formatting rules, forbidden list and the
"no quiz / no keywords" rules are **unchanged**.

### Frontend

**`snack.model.ts` [EDIT]** — comment only: `(at most 3 words)` → `(at most 20 words)`. The
`SnackKeyword` interface is unchanged.

**Definition card + highlight [VERIFY, likely no change]** — at up-to-20-word definitions:
- `.keyword-card__definition` already has `overflow-wrap: anywhere` and the card clamps width to
  `min(280px, calc(100vw - 16px))` with a bottom-sheet fallback; the card **grows naturally** — no
  max-height/internal scroll is added now (Clarification #3; fallback only if verification shows
  clipping).
- `.rich-text__keyword` is a button whose text flows through interpolation; the term (single word) is
  unaffected by definition length. `keyword-highlighter.ts` consumes `definition` only as carried
  data and never counts words, so no logic change is needed.
- All rendering stays escaped interpolation — **no** `[innerHTML]`, **no** `DomSanitizer` (FR-014).

**`snack-keywords.spec.ts` [EDIT]** — update the stale `≤3-word` comment (line 12), the test title
(line 326), and the assertion `expect(...split(/\s+/).length).toBeLessThanOrEqual(3)` (line ~338)
which would otherwise fail for a long definition. Add/replace with a focused 20-word wrapping
assertion (FR-015 / SC-004) modeled on the existing "very long generated content keeps the layout
stable with no horizontal overflow" test in `meaning-snacks.spec.ts`:

```ts
// A 20-word definition must wrap inside the card with no horizontal overflow.
const def = Array.from({ length: 20 }, (_, i) => `word${i + 1}`).join(' ');
// mock a snack keyword with `def`, open the card, then:
const overflow = await page.evaluate(
  () => document.documentElement.scrollWidth > window.innerWidth + 1,
);
expect(overflow, 'no horizontal overflow for a 20-word definition').toBe(false);
```

## Milestones / Phases

| # | Milestone | Scope | FRs | Depends on |
|---|-----------|-------|-----|------------|
| **M1** | Backend length policy | `SnackService.normalizeKeywords` + `MeaningSnackService.normalizeDefinition` bound 3 → 20 (+ named constant); **200-char word-boundary clamp on the source definition** (FR-019); comments | FR-001, FR-002, FR-003, FR-005, FR-006, FR-019 | — |
| **M2** | Prompt + docs | `generate-snack.md` (JSON + rule), `generate-meaning-snack.md` (wording), `SnackKeyword`/`SnackKeywordDto`/`snack.model.ts` comments | FR-008, FR-009, FR-010, FR-018 | — |
| **M3** | Backend tests | `SnackServiceTest` / `MeaningSnackServiceTest` 3→20 + 19/20/21 boundary; `GenerateSnackPromptTest` asserts 20 | FR-015, FR-016 | M1, M2 |
| **M4** | Frontend rendering verification + focused test | Verify card/highlight at 20 words; update `snack-keywords.spec.ts` comment/assertion; add 20-word no-overflow assertion | FR-011..FR-014, FR-015 | M1, M2 |
| **M5** | Regression + consistency | Full backend `mvn test`; frontend `npm run build` + full Playwright suite; grep for stale "at most 3 words" in changed surfaces | FR-016, FR-017, FR-019, SC-006, SC-007, SC-008 | M3, M4 |

Rough effort ordering: M1 and M2 are independent and can run in parallel; M3 needs both; M4 is
independent of M3 (rendering does not depend on the normalization constant) and can run in parallel
with M3; M5 closes the loop. Surface area is very small: 4 backend main files, 2 prompts, 3 backend
tests, 1 frontend comment, 1 E2E spec, and verification of 5 rendering files.

## Requirement Traceability

| Spec item | Plan item(s) |
|-----------|--------------|
| FR-001 | `SnackService.normalizeKeywords` bound 3 → 20 (M1); `SnackServiceTest` (M3) |
| FR-002 | `SnackService.normalizeKeywords` truncation to 20 (M1); 21-word boundary test (M3) |
| FR-003 | `MeaningSnackService.normalizeDefinition` same bound (M1); boundary test (M3) |
| FR-004 | No truncation below 20 (M1); 4–20-word persistence/round-trip tests (M3) |
| FR-005 | `split("\\s+")` tokenization unchanged; hyphenated = one token (M1; research R1) |
| FR-006 | Trim/blank-drop/de-dup/order-preserving steps untouched (M1); existing tests pass (M3/M5) |
| FR-007 | No edit to snack content fields or the `related` 2–3 item cap (M1/M2 scope) |
| FR-008 | `generate-snack.md` example JSON + explicit rule → 20 (M2); `GenerateSnackPromptTest` (M3) |
| FR-009 | `SnackKeyword`, `SnackKeywordDto`, `snack.model.ts`, service comments (M2) |
| FR-010 | Prompt-only change; no provider logic (M2; research R4) |
| FR-011 | Card escaped interpolation only; verification (M4) |
| FR-012 | Card width clamp + `overflow-wrap: anywhere`; natural growth; verification (M4; research R3) |
| FR-013 | Existing safe render path; injection E2E unchanged (M4/M5) |
| FR-014 | No `[innerHTML]`/`DomSanitizer` introduced; grep + E2E (M4/M5) |
| FR-015 | Backend 19/20/21 boundary tests; focused frontend 20-word no-overflow assertion (M3/M4) |
| FR-016 | Full existing backend + Playwright suites pass; 013/014 E2E unchanged (M5) |
| FR-017 | No endpoint/entity/action/schema change; no migration (M2/M5; data-model.md) |
| FR-018 | `generate-meaning-snack.md` wording only; JSON/rules unchanged (M2); `GenerateMeaningSnackPromptTest` unchanged |
| FR-019 | 200-char **word-boundary** clamp on the meaning-snack source definition in `MeaningSnackService.normalizeDefinition` (M1); char-boundary unit tests (M3); contract/data-model note |
| SC-001..SC-008 | See Testing Strategy + quickstart verification checklist (SC-008 = ≤200-char source-definition bound, FR-019) |

## Testing Strategy

**Backend**
- *`SnackServiceTest` (extended)*: rename
  `overLimitDefinitionIsTruncatedToFirstThreeTokens` → `...ToFirstTwentyTokens` and assert a
  21+-token definition is truncated to its first 20. Add boundary tests: 19 tokens kept in full, 20
  tokens kept in full, 21 truncated to 20. Keep `blankTermOrDefinitionIsDropped` and
  `definitionBlankAfterTruncationIsDropped` passing unchanged (FR-006).
- *`MeaningSnackServiceTest` (extended)*: rename
  `definitionIsTruncatedToFirstThreeTokens` → `...ToFirstTwentyTokens`; assert a 4–20-token source
  definition passes through unchanged (currently `"light to sugar and more words"` would now be kept
  in full), and add 19/20/21 boundary cases mirroring the snack-keyword rule (FR-003, FR-015). Add
  char-clamp tests for FR-019: a source definition whose first 20 tokens exceed 200 characters is
  clamped to ≤200 chars **on a word boundary** (no word is split; the result is non-empty); a
  definition ≤200 chars is unchanged; a single token longer than 200 chars falls back to a hard
  200-char cut.
- *`GenerateSnackPromptTest` (extended)*: rename
  `promptLimitsDefinitionsToThreeWordsAndGuidesTwoToFive` → `promptLimitsDefinitionsToTwentyWordsAndGuidesTwoToFive`;
  assert the rendered prompt contains "20 words" or "twenty words" (and **not** "3 words"/"three
  words"), and still contains the "2-5"/"2–5" guideline (FR-008, FR-016).
- *Contract / integration tests*: no DTO shape changes, so `SnackMarkupRoundTripTest`,
  `MeaningSnackContractTest`, `SnackKeywordsPersistenceIntegrationTest` and
  `MeaningSnackPersistenceIntegrationTest` must pass **unchanged**. The service now guarantees the
  persisted `sourceDefinition` is ≤200 chars (FR-019), so the `VARCHAR(200)` column can never be
  overrun; an integration test may add a long-definition round-trip to confirm the clamp persists.
- *Existing suites*: `mvn test` must be green with no other edit.

**Frontend (Playwright E2E — the only runner in the repo)**
- *`snack-keywords.spec.ts` (extended)*: update the stale `≤3-word` header comment and test title; the
  existing `toBeLessThanOrEqual(3)` assertion is replaced by a 20-word check; add a focused assertion
  that a 20-word definition wraps in `.keyword-card__definition` with no horizontal overflow
  (`document.documentElement.scrollWidth <= window.innerWidth + 1`) at desktop and narrow widths
  (FR-015 / SC-004). The injection/escaping assertions remain and must still pass (FR-013/FR-014).
- *Regression*: run the full existing suite (`happy-path`, `progressive-delivery`,
  `snack-wait-messages`, `results-*`, `snacks-navigation`, `snack-keywords`,
  `snack-rich-formatting`, `meaning-snacks`, `theme-history`, …) unchanged (FR-016, SC-006).
- *`npm run build`* must type-check.

**Frontend unit-test gap (unchanged from 013/014)**: the repo has no unit-test runner; the focused
wrapping assertion is therefore an E2E test, following the 012/013/014 precedent.

## Risks / Watch Items

| Risk | Mitigation |
|------|------------|
| `MeaningSnack.sourceDefinition` is an existing `VARCHAR(200)` column. A 20-word definition with long words could exceed 200 characters, so MySQL (strict mode) could reject the insert. | **RESOLVED via decision (a) / FR-019**: no schema change; `MeaningSnackService.normalizeDefinition` clamps the normalized source definition to ≤200 chars on a word boundary (never splitting a word) in addition to the 20-token cap, so the column can never be overrun. `SnackKeyword.definition` is inside a `JSON` column and is not length-constrained (no clamp). |
| A test or comment still asserting the 3-word cap could fail or leave stale wording (e.g. the `<= 3` assertion in `snack-keywords.spec.ts`, or the 013 contract doc). | M5 greps the changed surfaces for "at most 3 words"/"3 words"/"≤3" and the full suites must pass (SC-007). |
| Long definitions could visibly clip the card at small viewports. | The card already clamps width and uses `overflow-wrap: anywhere`; M4 adds the focused no-overflow assertion. If clipping is found, the agreed fallback is a bounded max-height with internal scroll (Clarification #3). |
| Scope creep into card redesign or snack-content changes. | Non-Goals are explicit; card/rich-text files are verification targets only; snack content fields and the `related` cap are untouched. |
| Prior-feature spec artifact (`specs/013-snack-keywords/contracts/snack-keywords-contract.md`) still states "at most 3 words". | It is not a surface changed by this feature (SC-007 scope). Flagged below; updating historical spec artifacts is optional and would not change runtime behavior. |

## Open Decisions for the Orchestrator

1. ~~**`MeaningSnack.sourceDefinition VARCHAR(200)` sufficiency.**~~ **RESOLVED — option (a) chosen by
   the product owner** (encoded as spec FR-019). No schema change: the source definition is clamped to
   ≤200 characters on a word boundary in `MeaningSnackService.normalizeDefinition`, in addition to the
   20-token cap. `SnackKeyword.definition` (JSON) needs no char bound.
2. ~~**Historical 013 contract doc.**~~ **RESOLVED — intentionally out of scope.** The 013 artifact
   `specs/013-snack-keywords/contracts/snack-keywords-contract.md` is **not** changed: it is the record
   of feature 013, and SC-007 scopes the "0 stale statements" requirement to the surfaces changed by
   this feature. No historical spec artifact is edited.
3. ~~**Named constant vs. literal.**~~ **RESOLVED — named constants chosen.** Use private constants
   `MAX_DEFINITION_TOKENS = 20` (in each service) and `MAX_SOURCE_DEFINITION_CHARS = 200` (in
   `MeaningSnackService` only), rather than inline literals.

## Complexity Tracking

> No Constitution Check violations. This section is intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
