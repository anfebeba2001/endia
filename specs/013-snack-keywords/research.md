# Research: Colored Keywords with Click-to-Show Short Definition Cards

**Phase 0 output** for `/specs/013-snack-keywords`. Each section records the **Decision**, **Rationale**,
and **Alternatives considered**, grounded in the real codebase (`backend/src/main/java/com/endia/**`,
`backend/src/main/resources/prompts/**`, `frontend/src/app/**`, `frontend/e2e/**`).

The governing constraints are: **additive nullable schema only, no new endpoint, no new runtime
dependency, backend normalizes / frontend renders safely, feature-012 security model preserved, and the
existing Playwright suite as the regression contract.**

---

## R1. Persistence of the structured keyword list: converter and column

### Options evaluated

**Option A — literally reuse `JpaJsonObjectConverter`.** Point `@Convert` at the existing converter.

**Option B — new `AttributeConverter<List<SnackKeyword>, String>` mirroring `JpaJsonObjectConverter`.**

**Option C — store keywords as `List<String>` with `JpaJsonConverter`** (e.g. `"term|definition"`).

**Option D — a separate `snack_keyword` table with a `@OneToMany`.**

### Decision

**Option B**, plus one additive nullable `JSON` column `snack.keywords`.

### Rationale

- **Option A is impossible.** `JpaJsonObjectConverter implements AttributeConverter<DragDropContent, String>`
  — an `AttributeConverter` is statically bound to one attribute type. It cannot be reused for
  `List<SnackKeyword>`; a new converter with the identical pattern is required. (The spec/decision wording
  "reuse `JpaJsonObjectConverter`" is honored as *reuse the pattern*, not the class — flagged for the
  orchestrator.)
- **Option B keeps the domain faithful.** `SnackKeyword` is a typed record; Jackson serializes it as
  `{term, definition}` with no manual string packing, and the null/blank → empty-list policy mirrors the
  existing converter exactly.
- **Option C loses type safety and invites delimiter bugs** (a definition containing `|`), and does not
  match the required `[{term, definition}]` shape without extra parsing.
- **Option D is over-engineering.** Keywords are a small, read-only, always-fetched-with-the-snack list
  (YAGNI, constitution V). A JSON column matches the existing `related`/`drag_drop_content` precedent and
  avoids a join, a second repository, and cascade/orphan-management complexity.
- **Nullable is required** for backward compatibility: pre-existing rows have no keywords and must load
  as an empty list (FR-012). A `NOT NULL` column would require a backfill and could fail on large tables.

### Alternatives considered and rejected

- `@ElementCollection` of an `@Embeddable` — a side table by another name; rejected for the same YAGNI
  reason as Option D, and it would change the fetch strategy of an always-needed field.
- Storing a single pre-rendered JSON string in a `TEXT` column — loses the MySQL JSON type and the
  existing converter convention; rejected.

---

## R2. Migration mechanism

### Options evaluated

**Option A — Hibernate `ddl-auto=update`** (the project's current mechanism).
**Option B — introduce Flyway/Liquibase and add a versioned migration.**

### Decision

**Option A**: declare the column nullable with `columnDefinition = "JSON"`; Hibernate adds
`alter table snack add column keywords JSON null` on startup. Document the manual DDL and rollback in
data-model.md §5 / quickstart.md §6.

### Rationale

- The repository has **no migration tool**: `backend/src/main/resources` contains only `application*.yml`
  and `prompts/`, and `application.yml` sets `spring.jpa.hibernate.ddl-auto=update`. Introducing Flyway
  is a project-wide infrastructure decision, not a per-feature one, and is out of scope for this feature
  (constitution V).
- The change is additive and nullable, so `update` is safe: it adds the column and leaves existing rows
  valid. No data rewrite occurs.
- The plan is explicit about the exact DDL so it is reproducible even where `ddl-auto` is disabled.

### Risks / watch items

- `ddl-auto=update` provides no version tracking or rollback history; this is a pre-existing project
  property, not introduced here. If production later disables `ddl-auto`, the manual `ALTER TABLE` is the
  authoritative migration.
- MySQL 8 is the only supported database (constitution), so the `JSON` column type is available; the
  Testcontainers suite pins `mysql:8.0`.

### Alternatives considered and rejected

- **Flyway/Liquibase now** — a new tooling dependency and a migration-governance decision beyond this
  feature's scope; would need to retrofit the existing schema baseline first. Rejected for this feature
  (recommended as a separate infrastructure task).

---

## R3. Keyword normalization: where and how

### Options evaluated

**Option A — normalize in the frontend** (renderer truncates/drops).
**Option B — normalize in the provider parser** (`OpenAiLlmProvider`).
**Option C — normalize at the service boundary** (`SnackService`).

### Decision

**Option C**, with the exact rules in data-model.md §4.

### Rationale

- The spec fixes the boundary: "validation/normalization belongs in `application/service` (`SnackService`)"
  and the frontend "renders whatever valid keywords it receives."
- Normalizing at the service boundary means **every** snack-returning surface (fresh generation, batch,
  recap, history) serves already-normalized data, so no surface can diverge.
- The provider parser stays a thin, tolerant decoder (missing array → empty; non-object element →
  skipped) and does not encode business policy.
- Keywords are excluded from `isValid`, so a keyword quality issue can never re-roll or fail the snack
  (FR-005/FR-008) — the whole point of the truncate-not-reject decision (spec Clarification 1).

### Alternatives considered and rejected

- Frontend normalization — would let invalid data persist and would duplicate policy per surface. Rejected.
- Rejecting/re-rolling on keyword defects — regresses the working generation flow. Explicitly rejected by
  spec Clarification 1.

---

## R4. Highlight matching algorithm and AST shape

### Options evaluated

**Option A — one combined `RegExp` of alternated terms** with `\b` boundaries, replacing matches.
**Option B — an index-scan matcher over escaped literal terms**, longest-term-first, with explicit
whole-word boundary checks, producing a typed AST.
**Option C — mark terms with HTML `<mark>`/spans and inject via `[innerHTML]`/`DomSanitizer`.**

### Decision

**Option B**, emitted as a new `keyword` inline node in feature-012's typed AST; `bold` nodes become
recursive (`nodes: RichInline[]`) so a keyword inside bold is both emphasized and highlighted.

### Rationale

- **Option C is forbidden** by FR-029: it reintroduces HTML-string construction and the `innerHTML` path
  that feature 012 deliberately closed. Rejected.
- **Option A is fragile for the spec's edge cases**: terms like `C++`, `(x)`, `a.b` are not `\b`-clean,
  and regex alternation + global replace makes "longest wins, non-overlapping" and boundary rules hard to
  reason about. `String.replace` also cannot emit typed nodes.
- **Option B is deterministic and testable**: terms are escaped/never compiled as patterns (FR-015
  literal matching), sorted by length descending (FR-016 longest wins), consumed left-to-right with a
  non-overlapping cursor (FR-016), and every occurrence is emitted (FR-017). Whole-word is checked with
  explicit "neighbour is not a word character" logic, with the edge exemption when the term itself starts
  or ends with a non-word character.
- **Recursive bold** is the minimal AST change that satisfies US2 AS4 ("term inside bold is both
  emphasized and highlighted") while keeping the output vocabulary tiny (`<p>`, `<ul>`, `<li>`, `<strong>`,
  `<br>`, `<button>`). With no keywords a bold node holds a single text child and renders exactly as
  before, so feature-012 E2E assertions stay green.
- **Scope is enforced structurally**: the renderer is only ever given `concept`/`metaphor`/`whyItMatters`
  (the three call sites), and `related` is never passed, satisfying FR-014 without special-casing.

### Alternatives considered and rejected

- Highlighting across block/bold boundaries — the spec confines matching to a contiguous text run
  (Edge Cases); crossing boundaries would corrupt formatting. Rejected.
- A pipe returning `SafeHtml` — pushes toward `innerHTML`/sanitizer. Rejected (same reasoning as 012 R4).

---

## R5. Definition card: ownership, dismissal, and responsiveness

### Options evaluated

**Option A — each `RichTextComponent` owns its own card.**
**Option B — one card per page, driven by a `keywordSelected` output from `RichTextComponent`.**
**Option C — `@angular/cdk` Overlay for positioning/focus management.**

### Decision

**Option B**: `RichTextComponent` renders keyword `<button>`s and emits `keywordSelected`; each page hosts
a single `KeywordDefinitionCardComponent` and holds the active keyword/anchor signal.

### Rationale

- **FR-021 (opening another term closes the previous card) is trivially guaranteed** when there is exactly
  one card per page: a new selection overwrites the single signal. Option A would allow one open card per
  field, violating FR-021 across fields.
- **FR-025 (consistent across surfaces)**: all three pages wire the same component and the same
  output/handler, so there is one behavior, not three.
- **No new dependency**: `@angular/cdk` is not in `frontend/package.json`; Option C would add one for
  positioning/focus that a small component can do directly (getBoundingClientRect + viewport clamping +
  media-query bottom sheet). Constitution V / the no-new-dependency constraint favor Option B.
- **Accessibility is native**: keyword is a real `<button>` (pointer + keyboard + focus for free); the card
  is a `role="dialog"` that moves focus in on open and returns it to the anchor on close (FR-023); Escape
  and outside-pointerdown close it (FR-020).

### Alternatives considered and rejected

- **Card owned by `RichTextComponent` (Option A)** — violates FR-021 across fields and duplicates card
  state per field. Rejected.
- **Global singleton card service** — more indirection than needed for three pages; a per-page signal is
  simpler and equally correct. Rejected as over-engineering.

---

## R6. Testing approach given the repo's runners

### Decision

Cover the pure highlighter/parser + rendering + sanitization + card through **Playwright E2E** with
route-mocked API responses, plus backend JUnit/MockMvc/Testcontainers tests for prompt, parsing,
normalization, contract, and persistence. Introduce no new frontend runner.

### Rationale

- `frontend/package.json` has `@playwright/test` but **no** Karma/Jest/Vitest runner (the `ng test` script
  has no runner dependency), matching feature 012's finding. Adding one would be a new tooling dependency
  beyond this feature's scope (constitution V).
- Route mocking feeds fixed keywords, overlapping terms, case variants, injection payloads, and
  empty/absent keywords deterministically, covering FR-013..FR-029 end to end.

### Open question (flagged for orchestrator)

If true unit coverage of `keyword-highlighter.ts` (the most logic-dense pure function) is desired, the
minimal option is **Vitest** as a dev-only dependency, testing the pure function with no DOM. This plan
does **not** assume it; it is recorded as a decision for the user/orchestrator (see report).

### Alternatives considered and rejected

- Adding Jest/Vitest now — new dev dependency and a deviation from the established feature-012 pattern;
  rejected for this plan unless the orchestrator requests it.
- Backend-only tests — cannot prove DOM safety, highlight rendering, or card interaction. Rejected.

---

## Summary of decisions

| # | Decision | Key rationale |
|---|----------|---------------|
| R1 | New `JpaSnackKeywordListConverter` mirroring `JpaJsonObjectConverter`; nullable `JSON` column | Converters are statically typed; typed record preserves shape; nullable for legacy rows |
| R2 | Hibernate `ddl-auto=update`; explicit manual DDL + rollback documented | Project has no migration tool; change is additive/nullable |
| R3 | Normalize keywords in `SnackService`; never part of validity | Single boundary, all surfaces consistent, no re-roll on keyword defects |
| R4 | Index-scan literal matcher → typed AST `keyword` node; recursive `bold`; no `innerHTML` | Literal/longest-wins/non-overlap are deterministic; preserves 012 security model |
| R5 | One card per page via `keywordSelected` output; no CDK | Guarantees FR-021/FR-025; no new dependency; native a11y |
| R6 | Playwright E2E + backend JUnit/MockMvc/Testcontainers; no new frontend runner | Repo has no frontend unit runner; deterministic mocks (Vitest flagged as an open question) |
