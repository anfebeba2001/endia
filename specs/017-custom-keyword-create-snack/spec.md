# Feature Specification: "Create snack" from a Custom Keyword's Generated Meaning (Task 12)

**Feature Branch**: `017-custom-keyword-create-snack`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "After a custom selected keyword's meaning has been generated and shown, offer a 'Create snack' action that turns that term + meaning into a meaning snack in the main topics backbone, reusing the feature-014 flow." (Batch backlog Task 12, from item 13.)

## Context & Scope *(established — do not re-investigate)*

This is a **predominantly frontend** feature that completes the custom-keyword journey started by feature 016: after the learner defines an arbitrary selected term, they can turn that **term + generated meaning** into a **meaning snack** in the main topics backbone, reusing the feature-014 generation, persistence, retrieval, visual-distinction, and quiz-free behavior **unchanged**. It builds directly on:

- **Feature 013** (`013-snack-keywords`): highlighted keywords + click-to-open single definition card.
- **Feature 014** (`014-meaning-snacks`): the meaning-snack kind, `/api/meaning-snacks` generate + theme-scoped list endpoints, `MeaningSnackService`, `MeaningSnack` entity, the backbone interleave, the distinct visual treatment, the quiz-free invariant, and the theme-scoped retrieval/reload path.
- **Feature 016** (`016-custom-keyword-define`): the held-selection (≈2s) "Define" flow that generates a short definition (≤20 words, feature 015) for an arbitrary selected term via `POST /api/definitions/generate` and shows it in the **same** definition card.

Feature 016 explicitly deferred this task as future work:

> Feature 016, **Out of scope (explicit)**: "the custom-selection 'Define' flow (Task 11) and 'Create snack' from a custom keyword (Task 12)."
>
> Feature 016, **FR-019**: "The feature MUST NOT implement Task 12 ('Create snack' from the custom meaning); that remains future work."

This feature adds (1) a **"Create snack"** action to the definition card that appears once a **valid generated meaning** exists for a custom keyword, (2) wiring of that action to the **existing feature-014** meaning-snack generate + append + mark-generated + backbone flow, and (3) confirmation that the resulting snack is **quiz-free, visually distinct, and persistent** exactly as feature 014 defines. It does **not** change what a meaning snack is, the regular snack/quiz flow, the selection-Define trigger/affordance/generation/display of feature 016, or feature 014's generation/persistence contract.

**Confirmed source-subtopic decision (see §Clarifications #1, Option A).** The custom keyword's meaning snack attaches to the **currently active backbone entry's source subtopic**: for a regular entry that is its `subtopicId`; for a meaning entry that is its `sourceSubtopicId`. This reuses the feature-014 persistence/retrieval path unchanged, works on both regular and meaning cards, places the new snack next to the snack it was read from, and gives the duplicate guard through the feature-014 unique key `(sourceSubtopicId, sourceTerm)`.

The following current code state is treated as confirmed input and is referenced, not re-designed:

**Frontend**
- `frontend/src/app/pages/snacks/snacks.component.{ts,html,scss}` — the snack reading page and the host of the single definition card. It owns `activeKeyword`, `activeKeywordAnchor`, `activeKeywordRect`, `customDefinition` (true when the card was opened by the custom-Define flow), `definitionLoading`, `definitionError`, `defining`, `activeKeywordSubtopicId` (captured at regular-keyword card-open as `entry?.kind === 'regular' ? entry.subtopicId : null`), `generateEnabled` computed (currently returns `false` when `customDefinition()` is `true`), `activeGenerateState` computed (returns `idle` when `customDefinition()` or a non-regular entry), and `onGenerateRequested(keyword)` which calls `meaningSnackService.generate(themeId, subtopicId, keyword.term, keyword.definition)`, appends to `meaningSnacks`, marks generated, and clamps the stored index. `onDefineRequested()` currently does **not** capture a source subtopic and sets `customDefinition=true`.
- `frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.{ts,html}` — the standalone, OnPush, signals-first card showing term + definition and the feature-014 "Generate snack" action gated by `generateEnabled`; action states are `idle | loading | done`; renders escaped interpolation only (no `[innerHTML]`, no `DomSanitizer`).
- `frontend/src/app/services/meaning-snack.service.ts` — `generate(themeId, subtopicId, term, definition)`, `listByTheme(themeId)`, `hasGenerated`, `markGenerated`; generated-key shape `${subtopicId}::${term.toLowerCase()}`.
- `frontend/src/app/models/{snack,meaning-snack,batch}.model.ts` — `SnackKeyword { term, definition }`; `MeaningSnack` carries `sourceSubtopicId`; the backbone union is `RegularEntry` (`entryId = subtopicId`, `kind: 'regular'`) and `MeaningEntry` (`entryId = meaning:<id>`, `kind: 'meaning'`, `sourceSubtopicId`).
- Existing E2E suites include `frontend/e2e/snack-keywords.spec.ts`, `meaning-snacks.spec.ts`, `snack-rich-formatting.spec.ts`, and others — the regression contract.

**Backend (reused unchanged)**
- `backend/src/main/java/com/endia/application/service/MeaningSnackService.java` — `generate(themeId, subtopicId, term, definition)` is idempotent on `(sourceSubtopicId, term)` case-insensitive (returns the existing snack with `alreadyExisted=true`), normalizes the source definition to ≤20 tokens / ≤200 chars, generates a full meaning snack via the externalized LLM flow, validates, re-rolls once, and raises a typed 503.
- `backend/src/main/java/com/endia/interfaces/rest/MeaningSnackController.java` — `POST /api/meaning-snacks/generate` and `GET /api/meaning-snacks?themeId=` (theme-scoped retrieval).
- `backend/src/main/java/com/endia/domain/model/MeaningSnack.java` + persistence — the meaning-snack kind with its no-quiz invariant.
- Schema is managed by Hibernate `ddl-auto: update` (no migration tool).

**Design intent (encoded below).** The custom definition produced by feature 016 is already a **short definition (≤20 words)**, so it is directly usable as the `definition` argument of the feature-014 meaning-snack generate call. The custom keyword's card therefore becomes the third surface from which a meaning snack can be created, using the **same** service call, dedup key, backbone append, mark-generated, distinct treatment, and no-quiz invariant. The only new behavior is capturing the correct **source subtopic** (Option A) and exposing the action **only** when a valid generated meaning exists.

## Project Structure Impact

**The existing layered layout does not change.** This feature fits the current architecture without introducing new layers, modules, entities, endpoints, or schema changes.

- **Frontend** (`src/app/`): the change is concentrated in `pages/snacks/snacks.component.ts` (capture the active entry's source subtopic for the custom-Define flow; expose the "Create snack" action state/availability; wire activation to the existing `MeaningSnackService.generate` + append/mark-generated/backbone logic with a loading state, one-in-flight guard, and meaningful error) and `components/keyword-definition-card/` (show the "Create snack" action for a custom keyword with a valid generated meaning, reusing/relabeling the feature-014 action slot and its `idle | loading | done` states). The card keeps its standalone/OnPush/signals-first shape and its escaped-interpolation-only security boundary (no `[innerHTML]`, no `DomSanitizer`). No new routing or module structure.
- **Backend** (`src/main/java/com/endia/`): **no change expected.** The feature reuses `MeaningSnackService.generate`, the existing `POST /api/meaning-snacks/generate` endpoint, and the theme-scoped `GET /api/meaning-snacks?themeId=` retrieval path. No new entity, no new port method, no new prompt, no new DTO, and no schema change. This is stated as a finding to be confirmed in the plan (see §Clarifications #5), not as an assumption that limits the spec: if the plan finds a backend change strictly required, it must remain additive and must not alter the feature-014 contract.
- **Data**: **no schema change.** The created snack is a feature-014 `MeaningSnack` persisted through the existing path; existing rows and columns remain valid and unchanged.
- **Security**: the feature-012/013/014/015/016 rendering model is preserved unchanged — escaped interpolation only, no `[innerHTML]`, no `DomSanitizer`; the custom term and generated meaning are treated as untrusted.
- **No deviation from the constitution's Clean Architecture / LLM-Abstraction principles**: the existing externalized, provider-agnostic meaning-snack generation flow is reused unchanged; generated content is validated at the service boundary and rendered through the safe allowlist.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Turn a custom keyword's generated meaning into a snack (Priority: P1)

After the learner holds a selection and activates "Define" (feature 016), the definition card shows the generated meaning. Once that meaning is displayed, the card offers a **"Create snack"** action for that keyword. Activating it generates a **meaning snack** derived from the custom term + generated meaning and adds it to the main topics backbone. While it is being generated, the card shows a loading/in-progress state; when it is ready, the new entry appears in the backbone. If generation fails, a meaningful error is shown and **nothing broken or empty is added**.

**Why this priority**: This is the new user-facing capability requested by the task and the source of the feature's value. It is independently testable by defining a custom term, activating "Create snack", and observing the loading state and the resulting backbone entry (or the error on failure). P1 because every other story depends on the custom meaning snack existing.

**Independent Test**: Select a term in a snack's reading content, hold ~2s, activate "Define", wait for the generated meaning, confirm the "Create snack" action appears, activate it, confirm a loading/in-progress state, and confirm that on success a meaning snack derived from that term+meaning appears in the backbone; repeat with a forced generation failure and confirm a meaningful error with no broken/empty entry. Delivers the value of "turn a word I defined into something I can study."

**Acceptance Scenarios**:

1. **Given** a custom keyword's meaning has been generated and displayed in the definition card, **When** the card is inspected, **Then** it shows a clearly labeled "Create snack" action for that keyword.
2. **Given** the "Create snack" action is available, **When** the user activates it, **Then** the UI shows a loading/in-progress state for that action.
3. **Given** generation succeeds, **When** it completes, **Then** a meaning snack derived from that custom term and its generated meaning is added to the main topics backbone via the existing feature-014 path and the in-progress state ends.
4. **Given** generation fails, **When** the failure is reported, **Then** a meaningful, user-safe error message is shown and no broken, empty, or placeholder entry is added to the backbone.
5. **Given** a generation is in progress for a custom keyword, **When** the user activates "Create snack" again, **Then** no second/duplicate generation is started and no duplicate entry is created.
6. **Given** a meaning snack already exists for the same (source subtopic, term), **When** the card is shown again, **Then** the action reflects the already-generated state and does not silently create a duplicate.

---

### User Story 2 — The created snack is quiz-free and visually distinct, per feature 014 (Priority: P1)

The snack created from a custom keyword is a feature-014 **meaning snack**: in the main topics backbone it is clearly distinguishable from a regular snack (same distinct color/badge as other meaning snacks) and it has **no quiz** — no quiz is generated, prefetched, stored, or offered for it.

**Why this priority**: The distinct look and the absence of a quiz are the defining properties of the meaning-snack kind and explicit in the task ("reusing the feature-014 flow"; quiz-free and visually distinct). It is independently testable by comparing the created entry against a regular snack and against a keyword-derived meaning snack. P1 because a custom meaning snack that behaved like a quiz-bearing regular snack would be incorrect.

**Independent Test**: Create a meaning snack from a custom keyword, place it in the backbone alongside a regular snack and a keyword-derived meaning snack, and confirm the custom one is visually distinct and exposes no quiz entry or "Start Quiz" affordance, while the regular snack keeps its quiz affordance. Delivers the value of "a custom meaning snack reads as the same quiz-free kind as the other meaning snacks."

**Acceptance Scenarios**:

1. **Given** a meaning snack created from a custom keyword is present in the backbone, **When** the backbone renders, **Then** it is visually distinct from regular snacks (same treatment as other meaning snacks).
2. **Given** the created snack is generated, **When** it is created, **Then** no quiz is generated or prefetched/stored for it.
3. **Given** the created snack is the active entry, **When** the card is displayed, **Then** no "Start Quiz" action (or any quiz entry/action) is offered for it.
4. **Given** a regular snack is present, **When** it renders, **Then** its existing quiz affordance, badge, and behavior are unchanged.
5. **Given** the created snack is selected in the backbone, **When** it renders, **Then** it shows its own content (never a blank card) and does not navigate to or imply a quiz.

---

### User Story 3 — The custom meaning snack persists and survives a reload/new session (Priority: P2)

A meaning snack created from a custom keyword is persisted by the backend through the existing feature-014 path and is still present in the main topics backbone after a page reload or in a new session for the same theme. Creation and persistence failures degrade gracefully, and regular snacks plus the existing snack/quiz flow are unaffected (no regression).

**Why this priority**: Persistence is what makes the created snack durable rather than a throwaway, but it is a data-integrity concern that can be verified independently of the visible creation interaction. P2 because it hardens the feature rather than unlocking the primary value (US1/US2).

**Independent Test**: Create a meaning snack from a custom keyword, reload the page / start a new session for the same theme, and confirm the snack is still listed in the backbone with its content and distinct treatment intact; confirm regular snacks, progressive delivery, retries, quiz, and results behave exactly as before. Delivers the value of "my custom meaning snacks are still there when I come back."

**Acceptance Scenarios**:

1. **Given** a custom meaning snack was created successfully, **When** it is read back through the API, **Then** it is present with its term, source definition, and generated content intact.
2. **Given** a custom meaning snack was created successfully, **When** the page is reloaded or a new session is started for the same theme, **Then** the snack remains present in the main topics backbone.
3. **Given** a custom meaning snack is loaded from persistence, **When** it renders, **Then** it keeps its distinct visual treatment and its no-quiz behavior.
4. **Given** the creation path, **When** it is used, **Then** pre-existing snacks and all existing data remain valid and unchanged (no schema change).
5. **Given** the retrieval path for persisted meaning snacks fails or returns nothing, **When** the backbone loads, **Then** it degrades gracefully with no crash, no broken entries, and regular snacks still render normally.
6. **Given** the existing flow (subtopics → snacks → quiz → results, plus history), **When** the feature ships, **Then** progressive delivery, retries, navigation, quiz generation, results, and history are unchanged and the existing E2E suites pass unchanged.

---

### User Story 4 — Custom term and generated meaning are always rendered as safe, escaped text (Priority: P1)

The custom term and its generated meaning are untrusted content (the term comes from the document, the meaning from a language model). Both are displayed as plain escaped text through the same safe allowlist mechanism used by the definition card and regular snacks, so markup or malicious content cannot execute or inject into the page. The feature-012/013/014/015/016 sanitization model is preserved unchanged.

**Why this priority**: The content reaches the DOM in the user's browser and is model output; treating it as HTML would reintroduce the cross-site-scripting risk feature 012 deliberately closed. P1 because it is a security invariant that must hold on every render, including on the new action.

**Independent Test**: Define a term and force a generated meaning containing `<script>`, `<img onerror=...>`, `javascript:` links, `<iframe>`, inline styles, and other non-whitelisted markup; activate "Create snack"; confirm nothing executes and only inert, readable text appears. Delivers the value of "creating snacks without opening an injection hole."

**Acceptance Scenarios**:

1. **Given** the custom term or generated meaning contains raw HTML such as `<script>alert(1)</script>`, **When** it renders (card or backbone), **Then** the script does not execute and is not injected into the DOM.
2. **Given** the text contains an event handler or dangerous URL (e.g. `<img src=x onerror=...>`, `javascript:` link), **When** it renders, **Then** no handler runs and no dangerous URL is activated.
3. **Given** the text contains markup outside the whitelist (links, images, headings, tables, code, iframes, inline styles), **When** it renders, **Then** it is neutralized as inert text and never becomes an element.
4. **Given** the text contains escaped entities, **When** it renders, **Then** the intended characters are shown without double-escaping.
5. **Given** any surface that shows the created snack or the custom term/meaning, **When** its content is shown, **Then** it is produced through the safe allowlist mechanism (no untrusted HTML binding) and the feature-012/013/014/015/016 whitelist is preserved unchanged.

---

### User Story 5 — Existing snack, keyword, meaning-snack, custom-Define, and formatting behavior is not regressed (Priority: P2)

The feature ships without changing the regular snack/quiz flow, the keyword highlight/definition-card interaction, the feature-014 meaning-snack generation from LLM-provided keywords, the feature-016 selection-Define flow, the rich-text rendering, or the results/history surfaces. Existing end-to-end suites pass unchanged.

**Why this priority**: This is a cross-cutting non-regression guarantee that is easy to verify independently but does not itself unlock user value. P2 because it hardens the feature.

**Independent Test**: Run the existing backend and frontend suites (including `snack-keywords`, `meaning-snacks`, `snack-rich-formatting`, and the selection-Define coverage) and confirm they pass unchanged; manually confirm the keyword highlight, definition card, regular "Generate snack" (LLM keyword) flow, and selection-Define flow behave as before. Delivers the value of "a new capability with no collateral damage."

**Acceptance Scenarios**:

1. **Given** the feature ships, **When** the existing E2E suites run, **Then** they pass unchanged.
2. **Given** features 012/013/014/015/016, **When** the feature ships, **Then** rich-text formatting, keyword highlighting, click-to-open/dismiss/focus, the regular meaning-snack ("Generate snack") flow for LLM-provided keywords, and the selection-Define trigger/affordance/generation/display are unchanged.
3. **Given** the results recap and read-only history surfaces, **When** the feature ships, **Then** they render as before and are not broken by the new action.
4. **Given** the regular snack generation and quiz flow, **When** the feature ships, **Then** it is unchanged and never treats the custom meaning snack as a regular quiz-bearing snack.

---

### Edge Cases

- **Action activated before the definition is generated** (i.e. while "Define" is still loading or after a definition error): the "Create snack" action is not offered; if somehow activated, it fails meaningfully and adds nothing.
- **Generated meaning is empty/blank**: the "Create snack" action is not offered for that keyword; no snack is created.
- **Generation failure / LLM unavailable**: the action reports a meaningful, user-safe error; no entry, no empty card, and no placeholder is added to the backbone; the user can retry.
- **Repeated/rapid activation of "Create snack"**: while a generation is in flight the action is disabled/guarded; rapid clicks start at most one generation and never create duplicates.
- **Duplicate meaning snack for the same (source subtopic, term)**: the feature-014 idempotent generate returns the existing snack (`alreadyExisted=true`); the UI reflects the already-generated state and does not add a second identical entry.
- **Active entry is a meaning snack when "Create snack" is activated**: the source subtopic is that meaning entry's `sourceSubtopicId` (Option A), so the new snack is placed next to the snack it was read from.
- **Active entry is a regular snack when "Create snack" is activated**: the source subtopic is that entry's `subtopicId` (Option A).
- **No active/source subtopic can be resolved** (e.g. the entry changed under the card): the action fails meaningfully and adds nothing.
- **The custom keyword is defined while a meaning snack is the active entry**: the created snack's placement and dedup key both use the meaning entry's source subtopic; no crash, no blank card.
- **Meaning snack created but retrieval/reload path unavailable**: the backbone degrades gracefully; no crash, no broken entries; regular snacks unaffected.
- **Custom meaning snack alongside zero regular snacks / empty backbone**: the created snack still renders and is selectable; empty-state logic is not falsely triggered.
- **Custom meaning snack selected in the backbone**: navigation/selection works; the card shows content and never a blank card; no "Start Quiz" is offered.
- **Custom meaning snack at the first/last position**: Previous/Next behavior and disabled states remain correct with a mixed list of regular and meaning entries.
- **Mixed ordering of regular, keyword-derived meaning, and custom meaning entries**: ordering/indices remain stable; selecting an entry never lands on a blank card.
- **Custom meaning snack content with rich formatting (feature 012)**: renders correctly through the safe renderer; formatting is not corrupted.
- **Unicode, emoji, and RTL text** in the term, generated meaning, or created content: display and interaction still work and remain readable.
- **Injection payloads** in the term, generated meaning, or created content (`<script>`, `onerror=`, `javascript:` URLs, `<style>`, `<iframe>`, `<img>`): no execution, no DOM injection, no layout breakage; readable text is preserved.
- **Escaped entities** in the term/meaning/content (e.g. `&lt;`, `&amp;`): displayed as the intended characters without double-escaping.
- **Very long generated meaning/content**: the card/backbone layout remains stable and performant; no overflow break.
- **Backend persistence failure during creation**: the UI shows an error and adds nothing; a later retry can succeed without leaving orphaned state.
- **Pre-existing data**: snacks and meaning snacks created before this feature are unaffected and still load/render as before.
- **Other surfaces unaffected**: the results recap, the read-only history view, and the quiz flow are unchanged and never treat the custom meaning snack as a regular quiz-bearing snack.

## Requirements *(mandatory)*

### Functional Requirements

**Action availability on the definition card**

- **FR-001**: After a custom keyword's meaning has been generated and displayed in the definition card, the card MUST present a clearly labeled "Create snack" action for that keyword.
- **FR-002**: The "Create snack" action MUST be offered **only** when a valid generated meaning for the selected term exists (the definition generation has finished successfully and the definition is non-empty); it MUST NOT be offered before "Define" completes or when the definition is blank/errored.
- **FR-003**: The "Create snack" action MUST reuse/relabel the feature-014 "Generate snack" action slot on the definition card; for a custom keyword it replaces/hides the feature-014 action so that exactly one snack-creation action is offered per card.

**Creation and backbone integration**

- **FR-004**: Activating "Create snack" MUST generate a meaning snack derived from the custom keyword's term + generated meaning using the **existing feature-014** meaning-snack generate flow and persistence path (the same service call, append, mark-generated, and backbone interleave used for LLM-provided keywords).
- **FR-005**: The source subtopic of the created snack MUST be the **currently active backbone entry's source subtopic**: for a regular entry that is its `subtopicId`; for a meaning entry that is its `sourceSubtopicId` (Option A, §Clarifications #1).
- **FR-006**: While generation is in progress, the UI MUST show a loading/in-progress state for the action, labeled `Creating…` (idle label `Create snack`; done label `Added to your snacks`), consistent with the feature-014 action's state machine.
- **FR-007**: Repeated/rapid activation MUST NOT start more than one concurrent generation for the same (source subtopic, term), and MUST NOT create a duplicate entry.
- **FR-008**: On generation failure, the UI MUST show a meaningful, user-safe error message and MUST NOT add any broken, empty, or placeholder entry to the backbone.
- **FR-009**: On success, the created meaning snack MUST be added to the main topics backbone as a first-class entry the user can select and read, via the existing feature-014 append path.
- **FR-010**: After a meaning snack exists for the same (source subtopic, term), opening the card again MUST reflect the already-generated state and MUST NOT silently create a duplicate.

**Quiz-free and visual distinction (per feature 014)**

- **FR-011**: The created snack MUST be quiz-free per feature 014: no quiz is generated, prefetched, stored, or offered for it, and it MUST NOT expose a "Start Quiz" (or any quiz) action.
- **FR-012**: The created snack MUST be visually distinct from regular snacks using the same meaning-snack treatment (color and/or badge/label) defined by feature 014.
- **FR-013**: The created snack MUST NOT be treated as a regular quiz-bearing snack by any surface (batch status/quiz prefetch, quiz flow, results, XP/accuracy, or history).

**Persistence and retrieval**

- **FR-014**: A successfully created meaning snack MUST be persisted through the existing feature-014 persistence path and MUST remain present in the main topics backbone after a page reload and in a new session for the same theme.
- **FR-015**: The feature MUST reuse the existing theme-scoped retrieval/listing path unchanged; failure or absence of that path MUST degrade gracefully (no crash, no broken entries, regular snacks unaffected).
- **FR-016**: The feature MUST NOT require a new entity, schema change, or new endpoint; the existing feature-014 `MeaningSnack` kind, endpoint, and persistence/retrieval contract MUST remain unchanged (to be confirmed by the plan — §Clarifications #5).

**Security / sanitization**

- **FR-017**: The custom term, the generated meaning, and the created snack's content MUST be rendered as escaped text through the same safe allowlist mechanism used for regular snacks and the definition card; untrusted text MUST NOT be bound as HTML (no `[innerHTML]`, no `DomSanitizer`).
- **FR-018**: Markup or arbitrary HTML in the custom term, generated meaning, or created content (including `<script>`, event handlers such as `onerror`, `javascript:` URLs, `<iframe>`, `<img>`, `<style>`, and inline styles) MUST NOT be executed or injected into the DOM.
- **FR-019**: The feature-012/013/014/015/016 sanitization model (render-time allowlist, no `[innerHTML]`, no `DomSanitizer`, escaped interpolation) MUST be preserved unchanged.

**Non-regression and scope**

- **FR-020**: Existing regular snack behavior, the snack/quiz flow, progressive delivery, retries, navigation, results, and the read-only history view MUST continue to work unchanged; the existing E2E suites MUST pass unchanged.
- **FR-021**: Feature 014's meaning-snack generation from LLM-provided keywords (the regular "Generate snack" flow) MUST remain unchanged.
- **FR-022**: Feature 016's selection-Define flow (trigger, transient affordance, generation, and card display) MUST remain unchanged apart from the added "Create snack" action.
- **FR-023**: Feature 013's keyword highlighting and definition-card behavior (open/dismiss/focus, single-card rule) MUST remain unchanged apart from the added action.
- **FR-024**: The feature MUST NOT change the regular snack generation flow, provider selection, batch semantics, quiz content/scoring, or feature 014's meaning-snack kind/persistence/backbone interleave.

### Key Entities *(include if feature involves data)*

- **Custom keyword** (feature 016): The user-selected term and its generated short definition (≤20 words) shown in the definition card; the source of a custom meaning snack.
- **MeaningSnack** (existing feature 014, reused unchanged): The quiz-free, visually distinct backbone entry created from a term + definition; the created snack is exactly this kind.
- **Source subtopic** (Option A, §Clarifications #1): The active backbone entry's source subtopic — a regular entry's `subtopicId` or a meaning entry's `sourceSubtopicId` — used as the created snack's placement and dedup scope.
- **Definition card** (existing, extended): The single, dismissible card that now shows the "Create snack" action for a custom keyword once a valid generated meaning exists.
- **MeaningSnackService** (existing, reused): The service exposing `generate(themeId, subtopicId, term, definition)`, `listByTheme(themeId)`, `hasGenerated`, and `markGenerated`.
- **Generated-key / dedup key** (existing feature 014): `${sourceSubtopicId}::${term.toLowerCase()}`, which provides the duplicate guard for repeated/rapid activation and re-opened cards.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of definition cards showing a valid generated meaning for a custom keyword offer a "Create snack" action; 0 cards offer it before "Define" completes or when the generated meaning is blank/errored.
- **SC-002**: 100% of successful "Create snack" activations add exactly one meaning snack derived from that custom term+meaning to the backbone via the feature-014 path; 0 duplicate entries are created by repeated/rapid activation or by re-opening the card for the same (source subtopic, term).
- **SC-003**: 100% of in-flight creations show a loading/in-progress state; 100% of failures show a meaningful error and add 0 broken/empty/placeholder entries.
- **SC-004**: 100% of created snacks are quiz-free and visually distinct per feature 014; 0 quizzes are generated/prefetched/stored for them and 0 offer a "Start Quiz" (or any quiz) action.
- **SC-005**: 100% of persisted custom meaning snacks remain present in the backbone after a page reload and in a new session for the same theme; 0 are lost or corrupted; 0 existing snacks or existing data are invalidated (no schema change).
- **SC-006**: 0 executable or injected markup from the custom term, generated meaning, or created content across all surfaces: injection payloads (`<script>`, `onerror=`, `javascript:` URLs, `<iframe>`, inline styles) execute 0 times and inject 0 nodes.
- **SC-007**: 0 new entities, schema changes, or endpoints are introduced (the feature reuses the feature-014 path unchanged) — confirmed by the plan.
- **SC-008**: Non-regression: the existing snack/quiz/results/progressive-delivery/history/keyword/meaning-snack E2E suites pass unchanged, and feature 012/013/014/015/016 behavior (rich formatting, keyword highlight/card, keyword meaning-snack generation, selection-Define) remains intact.

## Non-Goals

- **No change to feature 014's meaning-snack kind, endpoint, persistence, retrieval, backbone interleave, visual treatment, or no-quiz invariant**: the created snack reuses them exactly as they are.
- **No new entity, schema change, or new endpoint**: the feature is a frontend integration onto the existing feature-014 path (confirmed by the plan).
- **No change to the regular snack/quiz flow**: the created snack MUST NOT be treated as a regular quiz-bearing snack, and existing snack generation, batch/progressive delivery, retries, quiz content, scoring, XP, accuracy, and navigation MUST NOT be changed.
- **No quiz for the created snack**: no quiz is generated, prefetched, stored, or offered; no "Start Quiz" action appears for it.
- **No change to feature 016's selection-Define flow**: the held-selection trigger, transient affordance, generation, and card display are preserved; only the "Create snack" action is added once a valid generated meaning exists.
- **No change to feature 013's keyword highlighting or the definition card's open/dismiss/focus/single-card behavior** beyond the added action.
- **No change to meaning-snack generation from LLM-provided keywords** (the regular "Generate snack" flow).
- **No user editing/authoring of the created snack**: it is generated, read-only content.
- **No retro-generation of meaning snacks for previously defined custom terms**: only explicitly requested creations create snacks.
- **No full Markdown/HTML dialect**: custom term, generated meaning, and created content are plain/escaped; markup is neutralized, not rendered.
- **No redesign of the definition card or backbone layout** beyond adding the action and reusing the existing meaning-snack treatment; existing structure and design tokens are preserved.
- **No unrelated gamification, auth, or advanced features** (MVP-first / YAGNI).

## Assumptions

- **Source subtopic (confirmed, Option A)**: the created snack attaches to the currently active backbone entry's source subtopic — a regular entry's `subtopicId` or a meaning entry's `sourceSubtopicId` (see §Clarifications #1).
- **Action label and slot (confirmed)**: the action is labeled "Create snack" and reuses/relabels the feature-014 action slot, replacing/hiding the feature-014 "Generate snack" action for a custom keyword so exactly one snack-creation action is offered (see §Clarifications #2).
- **Availability (confirmed)**: the action is offered only after "Define" completes successfully and the generated meaning is non-empty (see §Clarifications #3).
- **Dedup (confirmed)**: duplicates are prevented by the existing feature-014 unique key `(sourceSubtopicId, term)` (case-insensitive), both in the backend (idempotent generate) and in the frontend generated-key set (see §Clarifications #4).
- **No backend/schema/endpoint change (confirmed by the plan)**: the feature reuses `MeaningSnackService.generate`, `POST /api/meaning-snacks/generate`, and `GET /api/meaning-snacks?themeId=` unchanged; no new entity or schema change (see §Clarifications #5).
- **Reused definition**: the custom definition from feature 016 is already a short definition (≤20 words), so it is directly usable as the `definition` argument of the feature-014 meaning-snack generate call.
- **Reused generation flow**: the created snack goes through the same externalized, provider-agnostic LLM flow (LLM-Abstraction principle), is validated/normalized at the service boundary, and is rendered through the feature-012 safe renderer.
- **Error handling**: errors surface through the existing `GlobalExceptionHandler`/`ErrorResponse` shapes with typed exceptions; the frontend shows the existing user-safe error message mechanism.
- **Security model**: the custom term, generated meaning, and created content are untrusted and rendered through the feature-012/013/014/015/016 safe model (escaped interpolation only, no `[innerHTML]`, no `DomSanitizer`).
- The existing stack is unchanged: Java 21 + Spring Boot backend, Angular 21 frontend, MySQL 8.
- The existing E2E suites remain the regression contract and are extended (not replaced) to cover the custom "Create snack" action, distinct rendering, no-quiz invariant, and persistence/reload.

## Clarifications

The following decisions were made to resolve open questions for this feature; all items are **confirmed** by the product owner and no clarification markers remain. Decisions marked **[CONFIRMED]** were raised as open questions and resolved exactly as recommended; the remaining items were resolved with reasonable defaults and are recorded as confirmed decisions.

1. **[CONFIRMED — source subtopic: Option A]** The custom keyword's meaning snack attaches to the **currently active backbone entry's source subtopic**: for a regular entry that is its `subtopicId`; for a meaning entry that is its `sourceSubtopicId`. Rationale: it reuses the feature-014 persistence/retrieval path unchanged, requires no backend/schema change, works on both regular and meaning cards, places the new snack next to the snack it was read from, and gives the duplicate guard through the feature-014 unique key `(sourceSubtopicId, sourceTerm)`. Encoded in §Context & Scope, §Project Structure Impact, FR-005, §Key Entities, US1 AS6, §Edge Cases.
2. **[CONFIRMED — action label and slot]** The action is labeled **"Create snack"** and reuses/relabels the feature-014 "Generate snack" action slot on the definition card; for a custom keyword it replaces/hides the feature-014 action so exactly one snack-creation action is offered per card. Encoded in FR-001/FR-003, US1 AS1, §Assumptions.
3. **[CONFIRMED — availability]** The "Create snack" action is offered **only** once "Define" has completed successfully and a non-empty generated meaning exists for the selected term; it is never offered before the definition generation completes or when the definition is blank/errored. Encoded in FR-002, US1 AS1, §Edge Cases, SC-001.
4. **[CONFIRMED — dedup]** Duplicate prevention uses the existing feature-014 unique key `(sourceSubtopicId, term)` case-insensitive: the backend generate is idempotent (`alreadyExisted=true`) and the frontend generated-key set (`${subtopicId}::${term.toLowerCase()}`) guards in-flight/rapid activation and re-opened cards. Encoded in FR-007/FR-010, US1 AS5/AS6, §Edge Cases, SC-002.
5. **[CONFIRMED — reuse, no backend/schema/endpoint change]** The feature reuses the existing feature-014 `MeaningSnackService.generate`, `POST /api/meaning-snacks/generate`, and `GET /api/meaning-snacks?themeId=` unchanged; **no new entity, no schema change, and no new endpoint** are introduced. Stated as a finding to be confirmed in the plan; if the plan finds a strictly required backend change, it must remain additive and must not alter the feature-014 contract. Encoded in §Project Structure Impact, FR-016, §Non-Goals, §Assumptions, SC-007.
6. **Failure and duplicate handling (decision)**: creation failures show a meaningful error and add nothing (FR-008); in-flight/rapid activation is guarded so no duplicate is created (FR-007); an existing snack for the same key is reflected rather than silently duplicated (FR-010). Rationale: matching the existing meaning-snack flow's "never leave a broken/empty entry" behavior and the task's explicit acceptance criteria. Encoded in US1 AS4/AS5/AS6, §Edge Cases, SC-002/SC-003.
7. **Quiz-free and visual-distinct invariant (decision)**: the created snack never generates/prefetches/stores a quiz, never offers a quiz affordance, and uses the feature-014 distinct meaning-snack treatment (FR-011–FR-013). Rationale: explicit in the task ("quiz-free and visually distinct, per feature 014"). Encoded in US2, §Non-Goals, SC-004.
8. **Security model (decision)**: the custom term, generated meaning, and created content are rendered through the feature-012/013/014/015/016 safe allowlist with escaped interpolation only — no `[innerHTML]`, no `DomSanitizer` (FR-017–FR-019). Rationale: preserve the existing cross-site-scripting defense. Encoded in US4, §Assumptions, SC-006.
9. **Persistence/reload (decision)**: the created snack persists and reappears after a page reload/new session for the same theme via the existing feature-014 theme-scoped retrieval path (FR-014/FR-015). Encoded in US3, §Assumptions, SC-005.
