# Feature Specification: Endia MVP — Batch Enhancements

**Feature Branch**: `001-batch-mvp-enhancements`

**Created**: 2026-09-09

**Status**: Draft

**Input**: User description: "Batch of post-MVP enhancements across five groups: (1) async snack delivery — request all snacks and quiz questions in one batch, render snacks progressively one at a time, prefetch quiz async; (2) quiz enhancements — add LLM-generated drag-and-drop exercises and an end-of-test snack recap; (3) dark mode — manual toggle, persisted, deriving palette from existing design tokens; (4) resilience — specific LLM error messages and auto-retry with spinner; (5) AI subtopics — let the user propose their own subtopics, persisted and deduplicated with AI-suggested ones."

## User Scenarios & Testing

### User Story 1 — Batch Snack + Quiz Generation with Progressive Delivery (Priority: P1)

When the user confirms their selected subtopics, the frontend issues **one logical batch request** that triggers generation of all snacks for the selected subtopics AND their quiz questions together. The user does not see one long wait for everything: exactly ONE snack is received, rendered, and displayed at a time. While the user reads the current snack, the next snack is generated asynchronously in the background. The user always sees a single snack card being loaded progressively, one after another. Because quiz generation is kicked off with the batch, the quiz is ready as soon as the last snack is read, so "Start Quiz" is instant.

> **Note**: The previously partially-implemented snack delivery behavior is replaced. This item is treated as greenfield — the old partial implementation is discarded and rebuilt from scratch.

**Why this priority**: This is the central interaction of the learning loop and the biggest perceived-latency problem. A user who has to wait for all snacks before seeing anything abandons the flow. Progressive delivery turns a long wait into a continuous reading experience. P1 because the core UX of the product depends on it.

**Independent Test**: Can be fully tested by selecting subtopics and confirming that (a) a single batch trigger fires, (b) snack cards appear one at a time rather than all at once, and (c) the quiz becomes available immediately after the last snack without a separate generation wait. Delivers the value of "I start learning instantly and the quiz is ready when I finish reading."

**Acceptance Scenarios**:

1. **Given** the user has selected 3 subtopics and confirms them, **When** they submit, **Then** exactly one batch request is issued that requests generation of all 3 snacks AND the quiz questions for those subtopics together (no separate quiz request is needed afterward).
2. **Given** the batch request was issued for 3 subtopics, **When** the first snack is generated, **Then** it is rendered and displayed immediately while the other two snacks continue generating in the background (the user sees one snack at a time, never a blank "waiting for all" screen).
3. **Given** the user is reading snack 1 of 3, **When** snack 2 finishes generating, **Then** the UI does not interrupt the current snack; the user finishes reading snack 1 before snack 2 is presented.
4. **Given** the user has finished reading all snacks, **When** they tap "Start Quiz", **Then** the quiz is displayed immediately without a loading state (it was prefetched during snack reading).
5. **Given** the batch is in progress, **When** one snack fails but others generate successfully, **Then** the successfully generated snacks are still rendered progressively and only the failed one is flagged (generation of multiple snacks does not block rendering of already-received snacks).

---

### User Story 2 — Resilience: Specific Errors and Auto-Retry (Priority: P1)

Instead of a generic "problem with the LLM" error, the app tells the user WHAT specifically went wrong (LLM unavailable, timeout, network error, quota/rate-limit, malformed response). When a snack or quiz generation call fails, the app automatically retries the call up to 3 total attempts and shows a spinner/loading indicator during the retry so the user knows progress is happening.

**Why this priority**: The entire MVP depends on a live LLM. Generic errors frustrate users and hide real failure modes (e.g. a quota limit vs. an outage need different handling). Automatic retry dramatically reduces perceived failure rate. P1 because robustness directly determines whether the core flow is reliable enough to ship.

**Independent Test**: Can be tested by simulating each failure mode (provider down, timeout, network drop, quota exceeded, malformed JSON) and verifying the user gets a specific message naming the cause, and by verifying a transient failure is retried up to 3 times with a spinner shown during retry. Delivers the value of "the app recovers from transient LLM issues and explains real problems."

**Acceptance Scenarios**:

1. **Given** the LLM provider returns a 503 (unavailable), **When** snack generation is attempted, **Then** the user sees a message identifying the LLM service as unavailable (not a generic "LLM problem").
2. **Given** the LLM call times out, **When** snack generation fails, **Then** the app automatically retries the call, shows a spinner during the retry, and only fails to the user after exhausting 3 total attempts.
3. **Given** the LLM returns a malformed response (missing/empty required fields), **When** the response is processed, **Then** the app identifies the response as malformed and triggers a retry (up to 3 total attempts).
4. **Given** the LLM returns a quota/rate-limit error, **When** quiz generation is attempted, **Then** the user sees a specific quota/rate-limit message rather than a generic failure.
5. **Given** a snack generation fails on the first two attempts, **When** the third attempt succeeds, **Then** the user ultimately sees the snack with no error and the retries were transparent (only a spinner was shown).
6. **Given** all 3 retry attempts fail, **When** the final failure occurs, **Then** the user sees a specific, user-friendly error naming the cause, and no partial/corrupt content is served.

---

### User Story 3 — Quiz Enhancements: Drag-and-Drop Exercises (Priority: P1)

A new question type, "drag-and-drop", is added alongside the existing multiple-choice questions. All drag-and-drop variants are valid — (a) sequencing/ordering, (b) classification into categories, (c) matching pairs — and ONE variant is picked at random per quiz. These questions are generated by the LLM just like MCQ questions and COMPLEMENT the existing quiz (drag-and-drop is added to the quiz, never replacing the multiple-choice questions). Both types can coexist within a single quiz.

**Why this priority**: Drag-and-drop exercises significantly raise engagement and deepen learning for certain topics (order/classification/matching are common knowledge patterns). Adding a second question type enriches the quiz without touching the core MCQ flow. P1 because it directly enhances the quiz's educational value, the primary engagement hook.

**Independent Test**: Can be tested by generating a quiz and verifying it contains at least one MCQ question and at least one valid drag-and-drop question whose variant (sequencing, classification, or matching) is one of the three defined types. Delivers the value of "the quiz is more engaging and covers more learning patterns."

**Acceptance Scenarios**:

1. **Given** the user starts a quiz for a subtopic, **When** questions are generated, **Then** the quiz contains one or more drag-and-drop questions in addition to multiple-choice questions (both types coexist).
2. **Given** a quiz is generated, **When** the drag-and-drop variant is chosen, **Then** it is exactly one of sequencing/ordering, classification into categories, or matching pairs (validated against the allowed set).
3. **Given** the same subtopic quiz is regenerated, **When** the drag-and-drop variant is selected, **Then** it is chosen at random among the valid variants (not fixed to one variant every time).
4. **Given** a drag-and-drop question is generated, **When** the response is validated, **Then** the question contains the items to be positioned/classified/matched and the correct arrangement (the source of truth for grading).
5. **Given** a drag-and-drop question lacks the expected structure (e.g. missing matching pairs), **When** it fails validation, **Then** it is treated as an invalid question and re-rolled (per the quality gate retry rules) rather than served broken.
6. **Given** a drag-and-drop question is included, **When** the user answers it, **Then** the result is graded against the correct arrangement and included in the same score/XP/xp summary as MCQ questions.

---

### User Story 4 — Quiz Enhancements: End-of-Test Snack Recap (Priority: P2)

On the full/complete test (results) screen, the related snack is shown at the end so the user can re-read it. The recap MUST say WHICH snack and subtopic the test was about — i.e. it associates the quiz with its snack/subtopic and displays the subtopic/snack name as a label.

**Why this priority**: Learning science shows that reviewing the source material right after an assessment consolidates memory. It closes the loop (quiz → review → reinforce). P2 because the core quiz flow already delivers value without it; this is a meaningful but additive enhancement.

**Independent Test**: Can be tested by completing a quiz and verifying the results screen displays, after the score/summary, the related snack card labeled with the name of the subtopic/snack the test was about. Delivers the value of "I can re-read the source material right after being tested on it."

**Acceptance Scenarios**:

1. **Given** the user has completed a quiz, **When** the results screen is shown, **Then** the related snack for the subtopic is rendered at the end of the screen (re-readable content).
2. **Given** the results screen is shown for a quiz about the subtopic "Light reactions", **When** the recap block appears, **Then** it is labeled with the subtopic/snack name "Light reactions" so the user knows which snack the test was about.
3. **Given** a quiz covers a single subtopic/snack, **When** the results screen shows the recap, **Then** exactly one snack recap is presented, associated with that subtopic/snack.
4. **Given** the snack associated with the quiz was not available (e.g. could not be persisted), **When** the results screen is rendered, **Then** it degrades gracefully (shows the subtopic name as a label without breaking the results display).

---

### User Story 5 — AI Subtopics: User-Proposed Subtopics with Deduplication (Priority: P2)

The user can propose their own subtopics (free text) in addition to / among the AI-suggested ones. User-proposed subtopics are saved and processed EXACTLY the same way as AI-suggested ones — they receive a snack and a quiz. If a user-proposed subtopic duplicates an AI-suggested one, only ONE of the two is kept/shown (deduplicated).

**Why this priority**: It gives the user agency and control over what they learn, which improves relevance and motivation. It is a natural extension of the existing subtopic flow with minimal new machinery. P2 because it enhances, but does not block, the core learning loop.

**Independent Test**: Can be tested by submitting a theme, adding a custom subtopic, and verifying (a) the custom subtopic appears among the selected subtopics, (b) it receives a snack and a quiz identical to an AI-suggested subtopic, and (c) if the custom subtopic matches an AI-suggested one, only one of the two is retained. Delivers the value of "I can steer what I study, without duplicates."

**Acceptance Scenarios**:

1. **Given** the user is on the subtopic selection screen, **When** they add a free-text custom subtopic, **Then** it is added to the list of selected subtopics alongside the AI-suggested ones.
2. **Given** the user adds a custom subtopic and confirms the selection, **When** the learning flow proceeds, **Then** the custom subtopic receives a snack and a quiz exactly like an AI-suggested subtopic (full parity in processing and persistence).
3. **Given** the user adds a custom subtopic that matches an existing AI-suggested subtopic (e.g. identical or near-identical text ignoring case/whitespace), **When** the list is processed, **Then** only one of the two is kept and shown (deduplicated), no duplicate snack/quiz is generated.
4. **Given** a user-proposed subtopic is empty or a duplicate of another user-proposed subtopic, **When** it is added, **Then** it is rejected or collapsed to a single entry (no empty/duplicate subtopic is accepted).
5. **Given** the user has both AI-suggested and user-proposed subtopics selected, **When** the batch generation triggers, **Then** ALL selected subtopics (AI and user-proposed) are generated in the same single batch, and all receive snacks and quizzes.

---

### User Story 6 — Dark Mode (Priority: P3)

The user can toggle the app between light and dark mode manually. The preference persists across reloads and browser sessions. The dark palette is a generic inversion/adaptation of the existing design tokens (not a fully separate redesign) — the existing color tokens are re-derived/inverted so the whole app UI adapts consistently. The dark mode respects the entire app: all pages, all components, all shared styles.

**Why this priority**: Dark mode is a comfort/polish feature, not a functional necessity. The MVP learning loop works entirely in light mode. P3 because it improves user comfort but does not unlock core value or block the loop.

**Independent Test**: Can be tested by toggling dark mode, reloading the page, and verifying the dark preference is retained and every page/component renders readable in the dark palette. Delivers the value of "comfortable reading in any lighting."

**Acceptance Scenarios**:

1. **Given** the user is in the app, **When** they toggle dark mode, **Then** the entire UI switches between light and dark palettes immediately (all pages and components reflect the change).
2. **Given** the user toggled dark mode, **When** they reload the page / reopen the app, **Then** dark mode is retained (the preference was persisted).
3. **Given** the design tokens define the light palette, **When** dark mode is applied, **Then** the dark palette is derived by inverting/adapting those existing tokens (no unrelated one-off colors; consistent with the design system).
4. **Given** dark mode is active, **When** the user navigates to any page or component (home, subtopics, snack reading, quiz, results, snack recap), **Then** all text remains readable and all interactive elements remain visible/usable against the dark background.
5. **Given** the user toggles back to light mode, **When** they reload, **Then** light mode is retained (the latest persisted preference wins).

---

### Edge Cases

- **Batch generation partial failure**: What happens when one snack in a batch fails but others succeed?
  - Successfully generated snacks are rendered progressively; the failed snack is flagged with a specific error and can be retried (up to 3 attempts). No snack failure blocks rendering of already-received snacks.
- **Batch generation all-fail**: What happens when every snack in a batch fails after retries?
  - The user sees a specific error message identifying the cause; no blank screen and no partial/corrupt content. Retry remains available.
- **Quiz prefetch failure**: What happens if quiz questions fail to generate during snack reading?
  - The batch does not block snack rendering; quiz generation is retried (up to 3 attempts). If it still fails, the user can retry quiz generation separately before starting.
- **Malformed snack/quiz response**: What happens when the LLM returns content missing required fields?
  - Identified as a malformed-response failure, retried (up to 3 attempts), and validated by the quality gate before serving. Never serve broken/empty content.
- **Bad drag-and-drop structure**: What happens when a drag-and-drop question is missing its correct arrangement or has invalid items?
  - Fails the quality gate and is re-rolled (max 1 retry per question per existing convention), and falls back to serving the best available result.
- **Duplicate custom subtopic**: What happens when the user proposes a subtopic identical to an AI-suggested one?
  - Deduplicated — only one entry is kept/shown; no duplicate snack or quiz is generated.
- **Duplicate among user-proposed subtopics**: What happens when the user proposes two identical custom subtopics?
  - Collapsed to a single entry.
- **Very specific / narrow user-proposed subtopic**: What happens when a custom subtopic is too narrow or the LLM cannot generate content for it?
  - Served whatever the LLM generates (if any), consistent with existing MVP behavior; invalid/empty content is retried then flagged with a specific error.
- **Dark mode persistence unavailable (privacy mode/blocked storage)**: What happens when browser storage is blocked?
  - Dark mode still applies for the current session; persistence silently degrades (defaults to light on next load) without crashing.
- **Provider quota/rate-limit during batch**: What happens when the LLM rate-limits a large batch?
  - The specific quota/rate-limit error is surfaced, and automatic retry (up to 3 attempts) is applied; the progressive display does not block.

## Requirements

### Functional Requirements

- **FR-001**: System MUST allow the frontend to issue a single batch request that requests generation of all snacks for the selected subtopics AND their quiz questions together (one logical trigger; no separate sequential quiz request required afterward).
- **FR-002**: System MUST deliver snacks progressively — exactly ONE snack is received, rendered, and displayed at a time, while the next snack (or snacks) continue generating asynchronously in the background.
- **FR-003**: System MUST prefetch quiz questions asynchronously during snack reading so the quiz is immediately available when snack delivery completes.
- **FR-004**: Generation of multiple snacks MUST NOT block the rendering of an already-received snack (partial delivery allows progressive display).
- **FR-005**: System MUST support a "drag-and-drop" question type generated by the LLM alongside the existing multiple-choice type, within the same quiz.
- **FR-006**: System MUST randomly select the drag-and-drop variant per quiz among the valid set: sequencing/ordering, classification into categories, and matching pairs.
- **FR-007**: Drag-and-drop questions MUST complement (not replace) multiple-choice questions — both types can coexist in a single quiz.
- **FR-008**: System MUST grade drag-and-drop answers against a stored correct arrangement (source of truth), and include them in the same score/XP summary as multiple-choice questions.
- **FR-009**: System MUST render the related snack at the end of the completed test/results screen so the user can re-read it.
- **FR-010**: The end-of-test snack recap MUST be labeled with the snack/subtopic name to identify which subtopic the test was about.
- **FR-011**: System MUST provide a manual toggle to switch the UI between light and dark mode.
- **FR-012**: System MUST persist the user's chosen light/dark preference in browser storage so it survives reload/session.
- **FR-013**: The dark palette MUST be derived by inverting/adapting the existing design tokens (not a separate redesign).
- **FR-014**: Dark mode MUST apply to the entire app UI — all pages, all components, and all shared styles.
- **FR-015**: System MUST return error messages that identify the specific failure cause (LLM unavailable, timeout, network error, quota/rate-limit, malformed response) rather than a single generic "LLM problem" message.
- **FR-016**: System MUST automatically retry a failed LLM generation call up to 3 total attempts.
- **FR-017**: System MUST show a spinner/loading indicator while a retry is in progress.
- **FR-018**: Automatic retry MUST apply to both snack generation and quiz generation failures.
- **FR-019**: System MUST allow the user to add their own subtopics (free text) in addition to / among the AI-suggested subtopics.
- **FR-020**: User-proposed subtopics MUST be persisted and processed exactly like AI-suggested subtopics (receive a snack and a quiz).
- **FR-021**: System MUST deduplicate subtopics — if a user-proposed subtopic matches an AI-suggested (or another user-proposed) one, only a single entry is kept/shown and no duplicate snack/quiz is generated.

*Note on retries and quality gate coexistence:* Retry limits for LLM generation (FR-016, max 3 attempts) are distinct from the existing per-question quality-gate re-roll (max 1 retry, from the MVP spec). A malformed/quality-failing response may consume either or both budgets; the system MUST never serve invalid content.

### Key Entities

- **StudySession / Batch**: Represents one batch run tying a set of selected subtopics to a single generation trigger. Key attributes: selection of subtopics, batch trigger timestamp, overall generation status (in-progress / partial / complete / failed). Parent for the progressive snack delivery and the prefetched quiz.
- **Subtopic** (extended): A study slice that may now come from AI suggestion OR user proposal. Key attributes: name, source (AI-suggested or user-proposed), selection flag, foreign key to Theme. Deduplicated against sibling subtopics.
- **Snack**: A knowledge card for a subtopic. Unchanged core fields (concept, metaphor, whyItMatters, related), plus generation status (pending / generating / ready / failed) to support progressive delivery.
- **QuizQuestion** (extended): Now supports two types — `multiple-choice` (existing) and `drag-and-drop`. For drag-and-drop, key attributes: variant (sequencing / classification / matching), items, and correct arrangement (source of truth for grading). A quiz may contain both types.
- **QuizResult / SnackRecap**: The end-of-test recap referencing the Subtopic/Snack the test was about. Key attributes: reference to subtopic/snack, subtopic/snack name label, snack content for re-reading.
- **UserUiPreference**: Persisted UI preference. Key attributes: mode (light/dark), value stored in browser storage and associated with the user context.
- **GenerationError**: A typed error surfaced to the user. Key attributes: cause category (unavailable / timeout / network / quota / malformed), human-friendly message, associated generation target (snack or quiz).

## Success Criteria

### Measurable Outcomes

- **SC-001**: Snacks render progressively — a user selecting N subtopics sees the FIRST snack within a bounded time after the batch trigger, and sees snacks one at a time (never a blank "waiting for all N" state) across 100% of normal-success runs.
- **SC-002**: Quiz is ready as soon as snacks complete — "Start Quiz" displays without a loading state in 100% of cases where quiz prefetch succeeded during snack reading.
- **SC-003**: Automatic retry recovers transient failures — a snack or quiz generation that fails transiently succeeds within 3 total attempts for at least 95% of simulated transient-failure cases in integration tests; a spinner/loading indicator is shown during every retry.
- **SC-004**: Specific error classification — for each of the 5 cause categories (unavailable, timeout, network, quota, malformed), the user receives a message that identifies that specific cause (not the generic "LLM problem") in 100% of tested cases.
- **SC-005**: Dark mode persists — toggling dark mode and reloading retains dark mode; the latest user preference wins, verified across 100% of repeated toggle/reload cycles in tests.
- **SC-006**: Dark palette consistency — 100% of app pages/components render readable content (no unreadable-on-dark text, no invisibly-dark interactive elements) when dark mode is active, checked across all views (home, subtopics, snack reading, quiz, results, snack recap).
- **SC-007**: Drag-and-drop validity — every served drag-and-drop question belongs to one of the 3 valid variants (sequencing, classification, matching) and has a non-empty, stored correct arrangement; this holds for 100% of drag-and-drop questions served.
- **SC-008**: Quiz type coexistence — every quiz that includes drag-and-drop also includes at least one multiple-choice question; neither type replaces the other in any generated quiz.
- **SC-009**: User-proposed subtopic parity — a user-proposed subtopic receives a snack and a quiz identical in processing to an AI-suggested subtopic for 100% of tested user-proposed subtopics.
- **SC-010**: Deduplication — no duplicate subtopic is persisted or shown when a user-proposed subtopic duplicates an AI-suggested (or another user-proposed) one, verified in 100% of duplicate-input tests.
- **SC-011**: End-of-test recap — 100% of completed-test results screens render the related snack at the end with a label identifying the snack/subtopic the test was about.
- **SC-012**: No regression to MVP loop — the existing theme → subtopics → snacks → quiz → results flow still completes end-to-end (per MVP success criteria) after these enhancements, verified by the existing E2E tests passing unchanged.

## Assumptions

- The user has a stable internet connection and a modern web browser (persistence via browser storage is available; dark-mode persistence silently degrades if storage is blocked).
- The previous partial snack-delivery implementation is discarded and replaced from scratch; this batch is treated as greenfield for the async-delivery items.
- Quiz generation for a subtopic may include any mix of multiple-choice and drag-and-drop questions; the multiple-choice type is never removed.
- A single batch trigger performs ONE logical generation flow; whether it opens one or multiple underlying LLM calls is an implementation detail (spec is technology-agnostic).
- The LLM is capable of generating all required content (snacks, QBQ/multiple-choice questions, drag-and-drop exercises); quality gate + retry handle malformed/low-quality output.
- Drag-and-drop grading uses a server-side stored correct arrangement as the source of truth, matching the existing MCQ grading convention.
- Deduplication matches on normalized text (ignoring case/whitespace); semantic dedup beyond exact/near-identical text matching is deferred to post-MVP.
- Dark mode is derived from the existing design tokens (generic inversion/adaptation); a fully separate dark design system is out of scope.
- Automatic retry limits (max 3 attempts for LLM generation) are distinct from the per-question quality-gate re-roll (max 1 retry) and may coexist on the same generation.
- No authentication changes; all requests remain anonymous (stub user context) as in the MVP.
- The MVP monorepo layout, `LlmProvider` abstraction, and backend/frontend split remain unchanged; providers remain swappable via config.
