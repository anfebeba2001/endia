# Research: "Generate snack" from a Keyword Definition into the Snack Backbone (No Quiz)

**Phase 0 output** for `/specs/014-meaning-snacks`. Each section records the **Decision**,
**Rationale**, and **Alternatives considered**, grounded in the real codebase
(`backend/src/main/java/com/endia/**`, `backend/src/main/resources/prompts/**`,
`frontend/src/app/**`, `frontend/e2e/**`) and the feature-013 precedents
(`specs/013-snack-keywords/{plan,data-model,research}.md`).

The governing constraints are: **the confirmed clarifications** (backbone = snacks reading surface;
theme-scoped retrieval path; distinct entity attached to theme + source subtopic), **additive/backward-
compatible schema only**, **no quiz for a meaning snack**, **LLM-Abstraction with an externalized
prompt**, **backend validates/normalizes, frontend renders safely**, and **the existing Playwright
suite as the regression contract**.

---

## R1. Modelling the meaning snack: distinct entity vs. a kind flag on `Snack`

### Options evaluated

**Option A — add a `kind`/`isMeaning` flag (and nullable `subtopic`/quiz fields) to the existing `Snack`.**
**Option B — a new `MeaningSnack` entity with its own table, repository, service, DTOs and endpoint.**
**Option C — reuse `Snack` as-is and infer "meaning" from a special subtopic/quiz absence.**

### Decision

**Option B**, per the confirmed Clarification #3 ("a new distinct entity/kind … explicitly not a
regular quiz-bearing `Snack`, and not a kind flag on `Snack`").

### Rationale

- **Structural no-quiz invariant.** A regular `Snack` is always subtopic-bound and quiz-bearing; the
  batch/quiz/results/history code paths all assume that. A separate entity/table means a meaning snack
  can never accidentally enter those paths: `BatchService` reads `StudySessionItem`, `QuizService`
  reads `QuizQuestion`, and history reads `JpaSnackRepository` — none of which can see
  `meaning_snack` (FR-014/FR-016, SC-005).
- **No nullable-semantics pollution.** Option A would force `Snack.subtopic` to become nullable and
  add kind-conditional branches across `SnackService`, `BatchController`, `QuizController`,
  `ThemeService` and every DTO — a large regression surface for a small feature (constitution V).
- **Clean architecture.** A new aggregate gets a clean domain entity, an application service and a
  controller/DTO group, matching the existing per-aggregate structure (`Theme`, `Subtopic`, `Snack`,
  `QuizQuestion`, `StudySession`).
- **Backward compatibility is trivial.** A brand-new table leaves every existing table, column and row
  untouched (FR-019, SC-007). Option A would add nullable columns to a live table and require every
  snack-returning DTO to carry the discriminator.

### Alternatives considered and rejected

- **Option A** — violates the confirmed clarification and inflates the regression surface.
- **Option C** — no explicit discriminator; fragile and unverifiable; rejected.

### Open decision

The API-level `kind` marker is a `SnackKind` enum (`MEANING`) rather than a lone string, so the
discriminator is typed; if the orchestrator prefers the absolute minimum, the enum can be replaced by
a DTO constant without changing the contract.

---

## R2. Retrieval path: theme-scoped endpoint vs. in-memory `AppStateService` only

### Options evaluated

**Option A — rely on `AppStateService.meaningSnacks` only (no backend read).**
**Option B — a theme-scoped `GET /api/meaning-snacks?themeId=` read by the snacks page on load.**
**Option C — fold meaning snacks into the batch status payload.**

### Decision

**Option B**, per the confirmed Clarification #2 ("a theme-scoped backend listing read by the
snacks-page load").

### Rationale

- **FR-018 is impossible with Option A.** `AppStateService` is in-memory; a page reload / new session
  wipes it, so a persisted meaning snack would vanish. The spec explicitly requires survival
  (US3 AS2, SC-006).
- **A dedicated endpoint keeps the regular flow frozen.** Option C would change the batch status
  contract and couple meaning snacks to `StudySessionItem`/`BatchService` — precisely the coupling the
  no-quiz invariant must avoid (FR-016/FR-026). Option B adds a small, independent read.
- **Consistency with existing conventions.** The project already exposes theme-scoped reads
  (`GET /api/themes` history); a `/api/meaning-snacks?themeId=` read follows the same shape.
- **Graceful degradation is easy.** The page can treat any listing error as an empty list without
  affecting the regular batch/poll flow (FR-020, US3 AS5).

### Alternatives considered and rejected

- **Option A** — cannot satisfy reload persistence.
- **Option C** — couples the new kind to the batch/quiz aggregate and changes an existing contract.

---

## R3. Placement / ordering in the backbone

### Options evaluated

**Option A — append all meaning snacks after all regular snacks.**
**Option B — interleave each meaning snack immediately after its source subtopic's regular entry,**
appending unmatched meaning snacks at the end.

### Decision

**Option B** (with **A** as the fallback for meaning snacks whose source subtopic is not in the
current batch).

### Rationale

- Clarification #3 states the meaning snack is "linked to its source subtopic **for placement in the
  backbone**", which points at placement relative to the source, not a flat append.
- The interleave is a **pure function** of `(batchItems, meaningSnacks)`, so ordering is deterministic
  and stable regardless of which regular items have arrived (progressive delivery).
- Unmatched meaning snacks (e.g. a return visit where the batch is not re-created) still render, at
  the end, so nothing is lost.
- `entryId` (`subtopicId` vs `meaning:<id>`) prevents `@for` track collisions when a subtopic has both
  a regular and a meaning entry, and keeps index clamping correct (FR-011, FR-013).

### Alternatives considered and rejected

- **Flat append (A)** — simpler but ignores the confirmed source-subtopic linkage; kept only as the
  unmatched fallback.
- **Group under the subtopic index entry (a nested list)** — changes the established backbone layout,
  which the spec's Non-Goals forbid ("No redesign of the backbone layout beyond adding the distinct
  visual treatment and mixed-entry handling").

---

## R4. Duplicate handling and repeated/rapid activation

### Options evaluated

**Option A — let duplicates accumulate; dedupe only in the UI.**
**Option B — service-level idempotency keyed by `(sourceSubtopicId, sourceTerm)` case-insensitively,**
returning the existing row with `alreadyExisted: true`; unique index as defence-in-depth.
**Option C — return HTTP 409 on a duplicate.**

### Decision

**Option B**; reject **C**.

### Rationale

- FR-006 says the card must "reflect the already-generated state and MUST NOT silently create a
  duplicate" — returning the existing item (not an error) is the direct expression of "reflect".
- A 409 (C) forces the frontend to handle a "success-ish" error path and risks showing an error for a
  state that is actually fine.
- The UI additionally guards against a second concurrent request per keyword (FR-005) and marks the
  button `done` after a successful or existing result.
- Case-insensitive matching (derived `IgnoreCase` query) makes "Photosynthesis" and "photosynthesis"
  the same key; the unique index enforces it at the storage layer on the new empty table.

### Alternatives considered and rejected

- **A** — violates FR-006/SC-002 and would produce visible duplicates.
- **C** — see above.

---

## R5. How "no quiz" is enforced

### Options evaluated

**Option A — a runtime conditional that skips quiz generation for meaning snacks.**
**Option B — structural separation: a distinct entity/table, a dedicated service that never calls
`QuizService`/batch, dedicated DTOs with no quiz fields, and a template with no quiz affordance.**

### Decision

**Option B** at every layer, with A's conditional only as a redundant UI guard.

### Rationale

- A meaning snack is not attached to a `StudySessionItem`, so `BatchService` (which iterates session
  items and calls `quizService.generateQuiz`) cannot see it; `QuizQuestion` is subtopic-scoped and is
  never created by `MeaningSnackService`.
- The DTO (`MeaningSnackItem`) has **no** `subtopicId` quiz target, `quizStatus`, `quizQuestions` or
  `keywords`, so a frontend surface cannot render a quiz affordance even if it tried (contract §1.2).
- The meaning card template omits `.quiz-btn`, and `startQuiz()` early-returns unless the active entry
  is regular — a redundant guard (FR-015).
- Meaning snacks are excluded from `GET /api/themes` history, results recap, XP and accuracy, so no
  existing surface is asked to interpret them (FR-016).

### Alternatives considered and rejected

- **A alone** — a conditional can be forgotten in a new surface; structural separation cannot.

---

## R6. The definition card action vs. feature 013 behaviour

### Options evaluated

**Option A — the card owns the HTTP call and generation state.**
**Option B — the card emits `generateRequested`; the hosting page owns the call and state; the card
receives `generateState`/`generateError`.**
**Option C — a separate "Generate snack" control outside the card.**

### Decision

**Option B**; reject **C**.

### Rationale

- Feature 013's card is deliberately stateless with respect to page context (it only shows a
  keyword and dismisses). Keeping it a pure presentational component with a new output preserves the
  013 contract and its E2E assertions (FR-025).
- The page already owns the theme/subtopic context needed to call §1; putting the call there avoids
  injecting services/context into the shared card.
- Option C contradicts FR-001 ("the keyword definition card MUST present … Generate snack").
- A shared `MeaningSnackService` centralises the HTTP call, the generated-key set and the
  "already generated" state, so the three hosting pages add only a few lines each.

### Open decision

The card is hosted by the snack card, results recap and history (feature 013). SC-001 is absolute
("100% of open keyword definition cards show a Generate snack action"), so this plan renders the
action on **all** hosts and wires generation wherever theme + source subtopic are resolvable
(snacks: active entry's `subtopicId`; results: `recap.subtopicId`; history: `subtopic.id`). If a
snacks-page-only scope is preferred, gate the card with `generateEnabled` and wire only
`SnacksComponent` (smaller change; SC-001 would then read "on the snacks reading surface").

---

## R7. Security / sanitization

### Decision

Reuse the feature-012 `RichTextComponent` (typed AST; only `<p>`, `<ul>`, `<li>`, `<strong>`, `<br>`
can be emitted; no `[innerHTML]`, no `DomSanitizer`) for meaning-snack content, and escaped
interpolation for the source term/definition in the card and backbone. No new rendering path is
introduced.

### Rationale

- Meaning-snack content and the source term/definition are untrusted model/user-derived text and
  reach the DOM; routing them through the same allowlist preserves the feature-012/013 XSS defence
  (FR-021..FR-023, SC-008).
- The card already renders `term`/`definition` via escaped interpolation; the new action adds only
  static labels, so the boundary is unchanged.
- Because meaning content carries no `keywords`, it adds no new highlight surface.

### Alternatives considered and rejected

- **A dedicated sanitizer / `DomSanitizer`** — reintroduces the HTML-binding path feature 012 closed.
- **Plain text rendering without the rich-text parser** — would drop the feature-012 formatting the
  spec expects ("renders correctly through the safe renderer", Edge Cases).

---

## R8. Migration mechanism and schema

### Decision

A **new table** created by Hibernate `ddl-auto=update`; a unique index on
`(source_subtopic_id, source_term)` plus theme/subtopic indexes. Manual DDL and rollback documented in
data-model.md §5 / quickstart.md §6.

### Rationale

- The repository has **no migration tool** (`backend/src/main/resources` contains only
  `application*.yml` and `prompts/`); introducing Flyway/Liquibase is a project-wide decision, out of
  scope (constitution V). Feature 013 followed the same `ddl-auto=update` approach.
- Creating a new table is safer than adding nullable columns to a live table: zero risk to existing
  rows and zero DTO changes.
- The unique index is created on an empty table, so there is no duplicate-conflict at creation; the
  service-level idempotency query is the primary guard.

### Risks / watch items

- `ddl-auto=update` provides no version tracking or rollback history (pre-existing project property).
- MySQL 8 is the only supported database (constitution), so the `JSON` column type is available; the
  Testcontainers suite pins `mysql:8.0`.

---

## R9. LLM abstraction: prompt, port and provider

### Decision

A new externalized prompt `prompts/generate-meaning-snack.md` (placeholders `{{theme}}`, `{{term}}`,
`{{definition}}`), a new provider-agnostic port method
`LlmProvider.generateMeaningSnack(theme, term, definition) -> GeneratedMeaningSnack(concept, metaphor,
whyItMatters, related)`, and provider-side defensive parsing in `OpenAiLlmProvider`.

### Rationale

- Constitution III: prompts are externalized, and no provider class leaks into services/controllers.
  The service depends only on the port.
- The prompt requests the **existing** snack JSON shape (`concept/metaphor/whyItMatters/related`) so
  the same rich-text renderer and the same validation rules apply; it explicitly forbids quiz
  questions/options/answers and a `keywords` array.
- Parsing is defensive in the same style as `generateSnack`: missing fields become `null`/empty, a
  malformed overall response becomes a typed `MALFORMED` exception, and an emitted `keywords` field is
  ignored.

### Impact / required updates

Adding an interface method requires updating **both** main implementers and the test double:
- `RetryingLlmProvider` (delegate `generateMeaningSnack` through its existing `retry(...)`),
- `OpenAiLlmProvider` (implement + parse),
- `RetryingLlmProviderTest.StubProvider` (test double must implement the new method).
A `default` interface method that throws was rejected: it hides an unimplemented port and violates the
spirit of the abstraction.

### Alternatives considered and rejected

- **Reuse `generateSnack(theme, subtopicName)` by passing `term`/`definition` as the subtopic name** —
  abuses the port, produces quiz-shaped keyword output, and offers no clean prompt. Rejected.
- **Hardcode the prompt in Java** — violates constitution III. Rejected.

---

## R10. Testing approach given the repo's runners

### Decision

Cover the new backend behaviour with JUnit/Mockito unit tests, MockMvc contract tests and
Testcontainers integration tests; cover the card action, distinct rendering, no-quiz invariant,
reload persistence, duplicate guard and XSS through **Playwright E2E** with route-mocked API
responses. Introduce no new frontend runner.

### Rationale

- `frontend/package.json` has `@playwright/test` but **no** Karma/Jest/Vitest runner, matching features
  012/013. Adding one is a new tooling dependency beyond this feature's scope (constitution V).
- The snacks page now issues `GET /api/meaning-snacks` on load, so a default empty listing mock is
  added to `setupAllMocks`; this keeps every existing spec deterministic without changing their
  existing responses.
- Route mocking feeds deterministic generate/listing fixtures, including failure, duplicate, empty and
  injection payloads.

### Open question (flagged for orchestrator)

If true unit coverage of the pure interleave/`entryId` logic is wanted, Vitest as a dev-only
dependency is the minimal option; this plan does not assume it (same stance as 013 R6).

---

## Summary of decisions

| # | Decision | Key rationale |
|---|----------|---------------|
| R1 | New `MeaningSnack` entity/table (not a `Snack` kind flag) | Confirmed clarification; structural no-quiz invariant; trivial backward compatibility |
| R2 | Theme-scoped `GET /api/meaning-snacks?themeId=` | Reload persistence impossible in-memory; keeps the batch contract frozen |
| R3 | Interleave after the source subtopic's regular entry; unmatched appended | Confirmed source-subtopic placement; pure/stable ordering; no layout redesign |
| R4 | Idempotent generate returns existing with `alreadyExisted:true`; unique index | Directly expresses FR-006; avoids a spurious 409 error path |
| R5 | No-quiz enforced structurally at every layer | Cannot be forgotten by a future surface; asserted by unit + E2E |
| R6 | Card emits `generateRequested`; page owns the call/state | Preserves feature-013 card contract; page has the context; shared service centralises |
| R7 | Reuse the feature-012 `RichTextComponent`; escaped interpolation | Preserves the XSS defence; no new rendering path |
| R8 | New table via `ddl-auto=update`; explicit manual DDL + rollback | No migration tool; additive and safe |
| R9 | Externalized prompt + new provider-agnostic port method | Constitution III; same JSON shape and validation as regular snacks |
| R10 | Playwright E2E + backend JUnit/MockMvc/Testcontainers; no new frontend runner | Repo has no frontend unit runner; deterministic mocks |
