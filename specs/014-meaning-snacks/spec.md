# Feature Specification: "Generate snack" from a Keyword Definition into the Main Topics Backbone (No Quiz)

**Feature Branch**: `014-meaning-snacks`

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "the key meanings are going to have a 'generate snack' button, when clicked, it adds a 'meaning snack' into the main topics backbone, it has to have a different color and it does not have a quiz test like the other main snacks." (Batch backlog Task 9, from item 12.)

## Context & Scope *(established — do not re-investigate)*

This is an **end-to-end feature** spanning the keyword definition card (frontend), the generation and persistence of a new kind of snack entry (backend), and the main topics backbone that lists snacks (frontend + the API surface the backbone reads from). It builds directly on feature 013, which introduced the colored-keyword highlight and the click-to-show definition card, and it implements the behavior feature 013 **explicitly deferred**:

> Feature 013, **FR-031**: "The feature MUST NOT add the 'Generate snack' action on the definition card or the 'meaning snack' backbone entry (these belong to a separate backlog task)."

That separate task is this feature. It adds a **"Generate snack"** action to the existing definition card; activating it generates a **meaning snack** — a snack derived from that keyword's `term` + short `definition` — and adds it to the **main topics backbone** as a persisted, visually distinct entry that carries **no quiz**.

The following current code state is treated as confirmed input and is referenced, not re-designed:

**Backend**
- `backend/src/main/java/com/endia/domain/model/Snack.java` — the knowledge-card entity: `Subtopic subtopic`, `concept`, `metaphor`, `whyItMatters` (TEXT), `related` (JSON list), and (added in 013) `List<SnackKeyword> keywords` persisted via `JpaSnackKeywordListConverter`. Constructors: 5-arg delegating with `List.of()` and the 6-arg `(Subtopic, concept, metaphor, whyItMatters, related, keywords)`.
- `backend/src/main/java/com/endia/domain/model/SnackKeyword.java` — `record SnackKeyword(String term, String definition)`.
- `backend/src/main/java/com/endia/application/service/SnackService.java` — `generateValidSnack(...)` validates/persists snacks, re-rolls once, then raises `ServiceUnavailableException`; `normalizeKeywords(...)` normalizes the ≤3-word keyword list. Snacks are always attached to a resolved `Subtopic`.
- `backend/src/main/java/com/endia/interfaces/rest/SnackController.java` — `POST /api/snacks/generate` (theme + subtopic ids) returning `SnackGenerateApiResponse.SnackItem`.
- `backend/src/main/java/com/endia/interfaces/rest/BatchController.java` + `backend/src/main/java/com/endia/application/service/BatchService.java` — batch status returns `BatchStatusApiResponse.SnackDto` (carrying the raw `Snack` via `BatchItemStatus`). This is the payload the frontend backbone currently reads snacks from.
- `backend/src/main/java/com/endia/application/service/QuizService.java` + `backend/src/main/java/com/endia/interfaces/rest/QuizController.java` — quiz generation is per **subtopic** (`generateQuiz(subtopicId, count)`); `QuizService` also builds the results recap from `snackRepository.findBySubtopicId(...)`. There is currently **no** notion of a snack without a subtopic or without a quiz.
- `backend/src/main/java/com/endia/application/port/LlmProvider.java` + `backend/src/main/java/com/endia/infrastructure/llm/OpenAiLlmProvider.java` + `LlmPromptTemplates.java` + prompts under `backend/src/main/resources/prompts/` — externalized, provider-agnostic LLM prompt flow.
- `backend/src/main/java/com/endia/infrastructure/persistence/JpaSnackRepository.java` — `findBySubtopicId(...)`, `findBySubtopicIdIn(...)`. There is **no** theme-level snack listing beyond feature 011's read-only `GET /api/themes` history (`ThemeController` / `ThemeHistoryApiResponse`).
- Schema is managed by Hibernate `ddl-auto: update` (`backend/src/main/resources/application.yml`); there is no migration tool. Feature 013's schema change was additive/nullable (one JSON column).

**Frontend**
- `frontend/src/app/components/keyword-definition-card/keyword-definition-card.component.{ts,html,scss}` — the standalone, OnPush, signals-first card opened from a highlighted keyword. It shows `term` + `definition` + a close control, renders both with escaped interpolation only (no `[innerHTML]`, no `DomSanitizer`), and emits `closed`. This is where the "Generate snack" button goes.
- `frontend/src/app/pages/snacks/snacks.component.{ts,html,scss}` — the snack reading page: the **backbone** the user reads from. It is driven by `BatchItem[]` (left index list keyed by `subtopicId` + one card at a time), with progressive delivery, retries, `startQuiz()`, and the single definition card wired via `activeKeyword`/`activeKeywordAnchor` + `openKeyword(event)`/`closeKeyword()`. On a return visit it hydrates from the in-memory `AppStateService.snacks()`; otherwise it creates a new batch. It has **no** persistence-reload path and **no** listing of previously stored snacks.
- `frontend/src/app/pages/subtopics/subtopics.component.{ts,html}` — the focus-areas ("topics") list.
- `frontend/src/app/services/{batch,snack,quiz,error,app-state,theme}.service.ts` and `frontend/src/app/models/{snack,theme,batch,quiz}.model.ts`.
- `frontend/src/app/components/rich-text/` — the sanitized rich-text renderer (feature 012): typed AST, no `[innerHTML]`, escaped interpolation.

**Confirmed interpretation of "main topics backbone" (user-confirmed, Option A):** "main topics backbone" is not a literal term in the code. Given the request's contrast "**it does not have a quiz test like the other main snacks**", the confirmed interpretation is the **snack list/reading surface on the snacks page** (`snacks.component`): the meaning snack becomes an additional persisted entry in that list, rendered alongside regular snacks, visually distinct, and with no quiz. Alternative interpretations — the focus-areas/subtopics list, or a brand-new dedicated meanings area — are explicitly rejected (see §Clarifications #1).

This feature adds (1) a "Generate snack" action on the definition card, (2) generation of a meaning snack from the keyword term + definition, (3) additive persistence and a retrieval path so the meaning snack survives a reload/new session, (4) a distinct visual treatment for meaning snacks in the backbone, and (5) explicit suppression of any quiz generation/affordance for meaning snacks. It does **not** change what a regular snack is, the existing batch/quiz flow, or feature 013's highlight/definition-card behavior beyond adding the button.

## Project Structure Impact

**The existing layered layout does not change.** This feature fits the current architecture without introducing new layers or restructuring modules.

- **Backend** (`src/main/java/com/endia/`): the meaning snack is a distinct kind of snack entry, so the new value/entity type belongs in `domain/model`; the generation orchestration (deriving the meaning snack from a keyword and persisting it) belongs in `application/service`; any new prompt is an externalized file under `resources/prompts/` loaded through `LlmPromptTemplates`/`LlmProvider` (LLM-Abstraction principle — no provider-specific logic in services); the new request/response DTOs live in `interfaces/rest/dto` and the new endpoint in `interfaces/rest`; persistence stays in `infrastructure/persistence`. Dependency direction (domain → application → infrastructure → interfaces) is preserved. Because regular snacks are currently always attached to a `Subtopic` and always have a quiz, the meaning-snack kind must be modeled so it can exist **without** a subtopic-derived quiz while still being listed and persisted (see §Clarifications #1/#2).
- **Frontend** (`src/app/`): the `Snack`/backbone models in `models/` are extended with a discriminator for the meaning-snack kind; the "Generate snack" control is added to the existing `components/keyword-definition-card/` component (standalone, signals, OnPush, escaped interpolation — no `[innerHTML]`); the backbone page `pages/snacks/` learns to render meaning entries distinctly and to suppress the "Start Quiz" affordance for them; the meaning snack's content is rendered through the existing `components/rich-text/` safe renderer. No new routing or module structure.
- **Data**: an **additive** persistence change (a new kind/type marker and any new fields required to store a meaning snack that is not attached to a subtopic's quiz). Existing snack rows and existing columns MUST remain valid and unchanged; the change MUST be backward compatible. Schema is applied via Hibernate `ddl-auto=update` (no migration tool), consistent with feature 013.
- **Retrieval path**: the backbone currently only receives snacks through the batch/status flow and the in-memory `AppStateService`. Satisfying "remains present after a page reload/new session" requires a way to (re)load persisted meaning snacks for the current theme; the approach is a theme-scoped listing endpoint read by the snacks-page load path (§Clarifications #2, confirmed).
- **No deviation from the constitution's Clean Architecture / LLM-Abstraction principles**: the meaning-snack prompt stays externalized and provider-agnostic; generated content is validated at the service boundary and rendered through the feature-012 safe allowlist.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Generate a meaning snack from a keyword definition card (Priority: P1)

While reading a snack, the user opens a keyword's definition card (feature 013) and sees a **"Generate snack"** button on it. Activating it turns that keyword's term and its short definition into a full **meaning snack** and adds it to the main topics backbone. While it is being generated, the card shows an in-progress/loading state; when it is ready, the new entry appears in the backbone. If generation fails, a meaningful error is shown and **nothing broken or empty is added**.

**Why this priority**: This is the new user-facing capability requested by the task and the source of the feature's value. It is independently testable by opening a definition card, activating the action, and observing the loading state and the resulting backbone entry (or the error on failure). P1 because every other story depends on a meaning snack existing.

**Independent Test**: Open a snack that has keywords, open a keyword definition card, confirm the "Generate snack" button is present, activate it, confirm a loading/in-progress state appears, and confirm that on success a meaning snack derived from that term+definition appears in the backbone; repeat with a forced generation failure and confirm a meaningful error with no broken/empty entry. Delivers the value of "turn a key term's short definition into something I can study."

**Acceptance Scenarios**:

1. **Given** a highlighted keyword's definition card is open, **When** the card is inspected, **Then** it shows a clearly labeled "Generate snack" action in addition to the term and its definition.
2. **Given** the "Generate snack" action is available, **When** the user activates it, **Then** the UI shows a loading/in-progress state for that action.
3. **Given** generation succeeds, **When** it completes, **Then** a meaning snack derived from that keyword's term and definition is added to the main topics backbone and the in-progress state ends.
4. **Given** generation fails, **When** the failure is reported, **Then** a meaningful, user-safe error message is shown and no broken, empty, or placeholder entry is added to the backbone.
5. **Given** a generation is in progress for a keyword, **When** the user activates "Generate snack" again, **Then** no second/duplicate generation is started and no duplicate entry is created.
6. **Given** a meaning snack already exists for a keyword, **When** its definition card is opened again, **Then** the action reflects the already-generated state and does not silently create a duplicate.

---

### User Story 2 — Meaning snacks are visually distinct and offer no quiz (Priority: P1)

In the main topics backbone, a meaning snack is clearly distinguishable from a regular snack (different color and/or a badge/label), and it has **no quiz**: no quiz is generated for it, no quiz entry is offered, and no "Start Quiz" action appears for it — unlike the regular main snacks.

**Why this priority**: The distinct look and the absence of a quiz are explicit requirements of the task and the whole point of the "meaning snack" concept. It is independently testable by comparing a meaning snack entry against a regular one. P1 because a meaning snack that looks and behaves like a regular quiz-bearing snack would be incorrect and confusing.

**Independent Test**: Place a meaning snack and a regular snack in the backbone, confirm the meaning snack is visually distinct (color/badge) and that it exposes no quiz entry or "Start Quiz" affordance, while the regular snack keeps its quiz affordance. Delivers the value of "a meaning snack reads as a different, quiz-free kind of card."

**Acceptance Scenarios**:

1. **Given** a meaning snack is present in the backbone, **When** the backbone renders, **Then** the meaning snack is visually distinct from regular snacks (different color and/or badge/label).
2. **Given** a meaning snack is present, **When** it is generated, **Then** no quiz is generated or prefetched/stored for it.
3. **Given** a meaning snack is the active entry, **When** the card is displayed, **Then** no "Start Quiz" action (or any quiz entry/action) is offered for it.
4. **Given** a regular snack is present, **When** it renders, **Then** its existing quiz affordance, badge, and behavior are unchanged.
5. **Given** a meaning snack is selected in the backbone, **When** it renders, **Then** it shows its own content (never a blank card) and does not navigate to or imply a quiz.

---

### User Story 3 — Meaning snacks persist and survive a reload/new session (Priority: P2)

A meaning snack that was generated is persisted by the backend and is still present in the main topics backbone after a page reload or in a new session for the same theme. Generation and persistence failures degrade gracefully, and regular snacks plus the existing snack/quiz flow are unaffected (no regression).

**Why this priority**: Persistence is what makes the generated meaning snack durable rather than a throwaway, but it is a data-integrity/compatibility concern that can be verified independently of the visible generation interaction. P2 because it hardens the feature rather than unlocking the primary value (US1/US2).

**Independent Test**: Generate a meaning snack, reload the page / start a new session for the same theme, and confirm the meaning snack is still listed in the backbone with its content and distinct treatment intact; confirm regular snacks, progressive delivery, retries, quiz, and results behave exactly as before. Delivers the value of "my meaning snacks are still there when I come back."

**Acceptance Scenarios**:

1. **Given** a meaning snack was generated successfully, **When** it is read back through the API, **Then** it is present with its term, definition, and generated content intact.
2. **Given** a meaning snack was generated successfully, **When** the page is reloaded or a new session is started for the same theme, **Then** the meaning snack remains present in the main topics backbone.
3. **Given** a meaning snack is loaded from persistence, **When** it renders, **Then** it keeps its distinct visual treatment and its no-quiz behavior.
4. **Given** the persistence change, **When** it is applied to existing data, **Then** pre-existing snacks and all existing data remain valid and unchanged (additive/backward compatible).
5. **Given** the retrieval path for persisted meaning snacks fails or returns nothing, **When** the backbone loads, **Then** it degrades gracefully with no crash, no broken entries, and regular snacks still render normally.
6. **Given** the existing flow (subtopics → snacks → quiz → results, plus history), **When** the feature ships, **Then** progressive delivery, retries, navigation, quiz generation, results, and history are unchanged and the existing E2E suites pass unchanged.

---

### User Story 4 — Meaning snack and keyword content are always rendered as safe, escaped text (Priority: P1)

The keyword term, its definition, and the generated meaning-snack content are untrusted model output. They are displayed as plain escaped text through the same safe allowlist mechanism used for regular snacks, so markup or malicious content cannot execute or inject into the page. Feature 012/013's sanitization model is preserved unchanged.

**Why this priority**: Meaning-snack content reaches the DOM in the user's browser and is generated by a language model; treating it as HTML would reintroduce the cross-site-scripting risk feature 012 deliberately closed. P1 because it is a security invariant that must hold on every render, not an enhancement.

**Independent Test**: Feed keyword text and generated meaning-snack content containing `<script>`, `<img onerror=...>`, `javascript:` links, `<iframe>`, inline styles, and other non-whitelisted markup; render the meaning snack and its definition card; confirm nothing executes and only inert, readable text appears. Delivers the value of "helpful meaning snacks without opening an injection hole."

**Acceptance Scenarios**:

1. **Given** meaning-snack content or keyword text contains raw HTML such as `<script>alert(1)</script>`, **When** it renders, **Then** the script does not execute and is not injected into the DOM.
2. **Given** content contains an event handler or dangerous URL (e.g. `<img src=x onerror=...>`, `javascript:` link), **When** it renders, **Then** no handler runs and no dangerous URL is activated.
3. **Given** content contains markup outside the whitelist (links, images, headings, tables, code, iframes, inline styles), **When** it renders, **Then** it is neutralized as inert text and never becomes an element.
4. **Given** content contains escaped entities, **When** it renders, **Then** the intended characters are shown without double-escaping.
5. **Given** any surface that shows a meaning snack, **When** its content is shown, **Then** it is produced through the safe allowlist mechanism (no untrusted HTML binding) and the feature-012/013 whitelist is preserved unchanged.

---

### Edge Cases

- **Generation failure / LLM unavailable**: the action reports a meaningful, user-safe error; no entry, no empty card, and no placeholder is added to the backbone; the user can retry.
- **Repeated/rapid activation of "Generate snack"**: while a generation is in flight the action is disabled/guarded; rapid clicks start at most one generation and never create duplicates.
- **Duplicate meaning snack for the same keyword**: after a meaning snack exists for a keyword, re-opening its definition card does not silently create a second identical entry (the action reflects the existing state).
- **Same keyword term appearing in more than one snack/subtopic**: generating from a term is scoped so results are predictable and not duplicated unexpectedly (see §Clarifications).
- **Definition missing/blank or keyword no longer present** when the action is activated: the action is not offered or fails meaningfully; no broken entry is created.
- **Meaning snack generated but retrieval/reload path unavailable**: the backbone degrades gracefully; no crash, no broken entries; regular snacks unaffected.
- **Meaning snack alongside zero regular snacks / empty backbone**: the meaning snack still renders and is selectable; empty-state logic is not falsely triggered.
- **Meaning snack selected in the backbone**: navigation/selection works; the card shows content and never a blank card; no "Start Quiz" is offered.
- **Meaning snack at the first/last position**: Previous/Next behavior and disabled states remain correct with a mixed list of regular and meaning entries.
- **Mixed ordering of regular and meaning entries**: ordering/indices remain stable; selecting an entry never lands on a blank card.
- **Meaning snack content with rich formatting (feature 012)** such as bold/bullets: renders correctly through the safe renderer; formatting is not corrupted.
- **Unicode, emoji, and RTL text** in the term, definition, or generated content: display and interaction still work and remain readable.
- **Injection payloads** in term, definition, or generated content (`<script>`, `onerror=`, `javascript:` URLs, `<style>`, `<iframe>`, `<img>`): no execution, no DOM injection, no layout breakage; readable text is preserved.
- **Escaped entities** in stored term/definition/content (e.g. `&lt;`, `&amp;`): displayed as the intended characters without double-escaping.
- **Very long generated meaning-snack content**: the card/backbone layout remains stable and performant; no overflow break.
- **Backend persistence failure during generation**: the UI shows an error and adds nothing; a later retry can succeed without leaving orphaned state.
- **Pre-existing data**: snacks created before this feature are unaffected and still load/render as before.
- **Other surfaces unaffected**: the results recap, the read-only history view, and the quiz flow are unchanged and never treat a meaning snack as a regular quiz-bearing snack.

## Requirements *(mandatory)*

### Functional Requirements

**Action on the definition card**

- **FR-001**: The keyword definition card MUST present a clearly labeled "Generate snack" action alongside the keyword's term and definition.
- **FR-002**: Activating "Generate snack" MUST generate a meaning snack derived from that keyword's `term` and `definition`.
- **FR-003**: While generation is in progress, the UI MUST show a loading/in-progress state for the action.
- **FR-004**: On generation failure, the UI MUST show a meaningful, user-safe error message and MUST NOT add any broken, empty, or placeholder entry to the backbone.
- **FR-005**: Repeated activations MUST NOT start more than one concurrent generation for the same keyword, and MUST NOT create duplicate entries.
- **FR-006**: After a meaning snack exists for a keyword, opening that keyword's definition card again MUST reflect the already-generated state and MUST NOT silently create a duplicate.

**Meaning snack content**

- **FR-007**: A meaning snack MUST be derived from the keyword's `term` and `definition` and MUST present an explanation of the term's meaning in the snack reading structure (the same concept / metaphor / why-it-matters / related shape as regular snacks).
- **FR-008**: The meaning snack MUST retain a reference to its source keyword's term and definition so the user can see what it was generated from.
- **FR-009**: Meaning snack content MUST be treated as untrusted generated text and rendered through the same safe allowlist mechanism as regular snacks; untrusted content MUST NOT be bound as HTML.

**Backbone integration and visual distinction**

- **FR-010**: A generated meaning snack MUST be added to the main topics backbone as a first-class entry that the user can select and read.
- **FR-011**: The main topics backbone MUST render meaning snacks alongside regular snacks without corrupting ordering, selection, Previous/Next behavior, or progressive-delivery/retry behavior.
- **FR-012**: Meaning snacks MUST be visually distinct from regular snacks (different color and/or a badge/label).
- **FR-013**: Selecting a meaning snack in the backbone MUST show its content and MUST NEVER render a blank card.

**No quiz**

- **FR-014**: Generating a meaning snack MUST NOT trigger any quiz generation or quiz prefetch/storage for it.
- **FR-015**: The backbone and the meaning-snack card MUST NOT offer any quiz entry or "Start Quiz" action for a meaning snack, unlike regular snacks.
- **FR-016**: A meaning snack MUST NOT be treated as a regular quiz-bearing snack by any surface (batch status/quiz prefetch, quiz flow, results, XP/accuracy, or history).

**Persistence and retrieval**

- **FR-017**: A successfully generated meaning snack MUST be persisted by the backend.
- **FR-018**: A persisted meaning snack MUST remain present in the main topics backbone after a page reload and in a new session for the same theme.
- **FR-019**: The persistence change MUST be additive and backward compatible: pre-existing snacks and existing data MUST remain valid and unchanged.
- **FR-020**: The system MUST expose a retrieval path so persisted meaning snacks are re-loaded into the backbone; failure or absence of that path MUST degrade gracefully (no crash, no broken entries, regular snacks unaffected).

**Security / sanitization**

- **FR-021**: Keyword term/definition and generated meaning-snack content MUST be rendered as escaped text through the same safe allowlist mechanism used for regular snacks; untrusted text MUST NOT be bound as HTML.
- **FR-022**: Markup or arbitrary HTML in keyword or meaning-snack content (including `<script>`, event handlers such as `onerror`, `javascript:` URLs, `<iframe>`, `<img>`, `<style>`, and inline styles) MUST NOT be executed or injected into the DOM.
- **FR-023**: The feature-012/013 sanitization model (render-time allowlist, no `[innerHTML]`, no `DomSanitizer`, escaped interpolation) MUST be preserved unchanged.

**Non-regression and scope**

- **FR-024**: Existing regular snack behavior, the snack/quiz flow, progressive delivery, retries, navigation, results, and the read-only history view MUST continue to work unchanged; the existing E2E suites MUST pass unchanged.
- **FR-025**: Feature 013's keyword highlighting and definition-card behavior (open/dismiss/focus) MUST remain unchanged apart from the added "Generate snack" action.
- **FR-026**: The feature MUST NOT change the regular snack generation flow, provider selection, batch semantics, or quiz content/scoring beyond what is required to add and persist meaning snacks.

### Key Entities *(include if feature involves data)*

- **MeaningSnack** (new): A snack entry derived from a keyword definition. Key attributes: a reference to its source keyword (`term`, `definition`), generated snack content (concept / metaphor / why-it-matters / related), a kind/type marker distinguishing it from a regular snack, an association to the theme (and, per §Clarifications, possibly the source subtopic), a creation time, and the invariant that it has no quiz.
- **Snack** (existing, unchanged in meaning): The regular knowledge card, always attached to a `Subtopic` and accompanied by a quiz. A meaning snack shares the snack reading shape but is a distinct kind and MUST NOT be conflated with a regular snack.
- **SnackKeyword** (feature 013): The `term` + ≤3-word `definition` that is the source of a meaning snack.
- **Main topics backbone** (UI concept, working interpretation): The snack list/reading surface on the snacks page, which currently holds `BatchItem[]` keyed by subtopic. It must become able to hold regular and meaning entries together.
- **Meaning-snack kind marker** (concept): The discriminator carried through the data/API that lets the UI render meaning snacks distinctly and suppress quiz affordances.
- **Retrieval path** (concept): The mechanism by which persisted meaning snacks for the current theme are re-loaded into the backbone after a reload/new session.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of open keyword definition cards show a "Generate snack" action; 0 cards fail to render it.
- **SC-002**: 100% of successful "Generate snack" activations add exactly one meaning snack derived from that term+definition to the backbone; 0 duplicate entries are created by repeated/rapid activation.
- **SC-003**: 100% of in-flight generations show a loading/in-progress state; 100% of failures show a meaningful error and add 0 broken/empty/placeholder entries.
- **SC-004**: 100% of meaning snacks in the backbone are visually distinct from regular snacks; 0 meaning snacks are visually indistinguishable from regular snacks.
- **SC-005**: 0 quizzes are generated, prefetched, or stored for meaning snacks; 0 meaning snacks offer a "Start Quiz" (or any quiz) action.
- **SC-006**: 100% of persisted meaning snacks remain present in the backbone after a page reload and in a new session for the same theme; 0 are lost or corrupted.
- **SC-007**: 0 existing snacks or existing data are invalidated by the persistence change (100% backward compatible).
- **SC-008**: 0 executable or injected markup from keyword/meaning-snack content across all surfaces: injection payloads (`<script>`, `onerror=`, `javascript:` URLs, `<iframe>`, inline styles) execute 0 times and inject 0 nodes.
- **SC-009**: Non-regression: the existing snack/quiz/results/progressive-delivery/history E2E suites pass unchanged, and feature 013's highlight/definition-card and feature 012's rich formatting remain intact.

## Non-Goals

- **No change to the regular snack/quiz flow**: the meaning snack MUST NOT be treated as a regular quiz-bearing snack, and the existing snack generation, batch/progressive delivery, retries, quiz content, scoring, XP, accuracy, and navigation MUST NOT be changed.
- **No quiz for meaning snacks**: no quiz is generated, prefetched, stored, or offered for a meaning snack; no "Start Quiz" action appears for it.
- **No replacement or redesign of the definition card**: only the "Generate snack" action is added; feature 013's open/dismiss/focus behavior is preserved.
- **No new full snack-generation flow for regular snacks**: the feature does not alter how regular snacks are prompted, validated, or persisted.
- **No user editing/authoring of meaning snacks**: they are generated, read-only content.
- **No retro-generation of meaning snacks for previously stored keywords**: only explicitly requested generations create meaning snacks.
- **No full Markdown/HTML dialect**: keyword and meaning-snack text is plain/escaped; markup is neutralized, not rendered.
- **No change to the history view's read-only nature or data shape** beyond what is strictly required to not conflate meaning snacks with regular snacks.
- **No redesign of the backbone layout** beyond adding the distinct visual treatment and mixed-entry handling; existing structure and design tokens are preserved.
- **No unrelated gamification, auth, or advanced features** (MVP-first / YAGNI).

## Assumptions

- **Confirmed interpretation of "main topics backbone"** (see §Clarifications #1, Option A): the snack list/reading surface on the snacks page, not the subtopics list or a new dedicated area.
- A **meaning snack** is a full snack-shaped card (concept/metaphor/why-it-matters/related) derived from the keyword `term` + `definition`, generated via the externalized LLM flow (LLM-Abstraction principle), validated at the service boundary, and rendered through the feature-012 safe renderer.
- The keyword `definition` remains the ≤3-word definition from feature 013; the meaning snack expands that seed into readable content rather than re-defining it.
- Meaning snacks are associated with the **current theme** and additionally linked to the **source subtopic** for placement in the backbone (§Clarifications #3, confirmed).
- "No quiz" is an invariant of the meaning-snack kind: it is enforced both by not generating a quiz and by not offering any quiz affordance.
- Because the backbone currently reloads only from in-memory `AppStateService` and the batch/status flow, satisfying persistence-after-reload requires a theme-scoped retrieval path (§Clarifications #2, confirmed).
- The persistence change is additive and applied via Hibernate `ddl-auto=update` (no migration tool), consistent with feature 013; existing rows remain valid.
- The existing stack is unchanged: Java 21 + Spring Boot backend, Angular 21 frontend, MySQL 8.
- The backend validates/normalizes generated meaning-snack content at the service boundary; the frontend renders whatever valid content it receives through the safe allowlist and never binds it as HTML.
- Feature 013's single-definition-card-per-page mechanism remains the place where the "Generate snack" action lives.
- The existing E2E suites remain the regression contract and are extended (not replaced) to cover the meaning-snack action, distinct rendering, no-quiz invariant, and persistence/reload.

## Clarifications

The following decisions were made to resolve open questions for this feature. All items are **confirmed** (user-confirmed in the clarification session); no `[NEEDS CLARIFICATION]` markers remain.

1. **[CONFIRMED: backbone surface — Option A]** "Main topics backbone" means the **snack list/reading surface on the snacks page** (`snacks.component`), where the meaning snack becomes an additional persisted entry rendered alongside regular snacks. Rationale: the request contrasts meaning snacks with "the other main snacks" and their quiz, which points at the snack reading surface, not the subtopics list. Rejected alternatives: (a) the focus-areas/subtopics list; (b) a new dedicated meanings area. Encoded in §Context & Scope, §Project Structure Impact, FR-010/FR-011, US2, SC-004.
2. **[CONFIRMED: reload discovery]** A theme-scoped retrieval/listing path for persisted meaning snacks is acceptable and required so they reappear after a reload/new session for the same theme (implemented as a small theme-scoped backend listing read by the snacks-page load). "New session for the same theme" is identified by the persisted `themeId`. Encoded in §Project Structure Impact, FR-018/FR-020, US3 AS2, SC-006.
3. **[CONFIRMED: meaning-snack shape and attachment]** (a) The meaning snack is a **new distinct entity/kind** that is explicitly **not** a regular quiz-bearing `Snack` (not a kind flag on `Snack`); and (b) it is attached to the **theme** and linked to its **source subtopic** for placement in the backbone. Encoded in §Context & Scope, §Project Structure Impact, §Key Entities, FR-007/FR-008/FR-016/FR-019, US1 AS6, §Edge Cases.
4. **Failure and duplicate handling (decision)**: generation failures show a meaningful error and add nothing (FR-004); in-flight/rapid activation is guarded so no duplicate is created (FR-005); an existing meaning snack for a keyword is reflected rather than silently duplicated (FR-006). Rationale: matching the existing snack flow's "never leave a broken/empty entry" behavior and the task's explicit acceptance criteria. Encoded in US1 AS4/AS5/AS6, §Edge Cases, SC-002/SC-003.
5. **No-quiz invariant (decision)**: meaning snacks never generate/prefetch/store a quiz and never offer a quiz affordance, and are excluded from quiz-bearing surfaces (FR-014–FR-016). Rationale: explicit in the task ("it does not have a quiz test like the other main snacks"). Encoded in US2, §Non-Goals, SC-005.
6. **Security model (decision)**: keyword and generated meaning-snack content is rendered through the feature-012/013 safe allowlist with escaped interpolation only — no `[innerHTML]`, no `DomSanitizer` (FR-021–FR-023). Rationale: preserve the existing cross-site-scripting defense. Encoded in US4, §Assumptions, SC-008.
7. **Additive persistence (decision)**: the schema change is additive/backward compatible and applied via Hibernate `ddl-auto=update`, consistent with feature 013; existing data remains valid (FR-019). Encoded in §Project Structure Impact, §Assumptions, SC-007.
