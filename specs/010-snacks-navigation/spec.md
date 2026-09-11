# Feature Specification: Return to Snacks from Results + Snacks Navigation

**Feature Branch**: `010-snacks-navigation`

**Created**: 2026-09-11

**Status**: Draft

**Input**: User description: "After finishing a quiz, the results screen only offers 'Start a new theme' (which resets and goes home) with no way back to the knowledge snacks. Add a 'Back to snacks' action in the results footer next to 'Start a new theme' (both completion states) that returns to `/snacks` preserving the generated snacks and the quizzed subtopic's context. Additionally, expand the snacks page so the user can browse every snack: a left-side index/list of all snacks in the current set (active one highlighted, click any to jump), plus Next/Previous controls to move through all snacks. Navigation state (active index) must be wired through `AppStateService.currentSnackIndex` (currently unused; the component keeps a local `currentIndex`) so it stays in sync and survives re-entry from the results screen." (Batch backlog Task 7.)

## Context & Scope *(established — do not re-investigate)*

This is primarily a **frontend/UI** feature. The backend is expected to be unchanged. The following current behavior is treated as confirmed input and is referenced, not re-designed:

- The results screen (`frontend/src/app/pages/results/results.component.html`) renders a single `.results-footer` containing one `.primary-btn` labelled "Start a new theme", which calls `restart()` (`AppStateService.reset()` + navigate to `/`). There is no path back to `/snacks`.
- The results screen has two completion states — "Quiz complete" and "Perfect score!" (when all answers are correct) — both of which render the same `.results-footer`.
- The snacks page (`frontend/src/app/pages/snacks/snacks.component.ts` / `.html`) renders exactly one snack card at a time and keeps the active position in a **local** `currentIndex` signal. It exposes a single "Next →" `.primary-btn` inside `.snack-card__footer` that advances only when the next snack is `READY`.
- `AppStateService.currentSnackIndex` already exists but is effectively unused by the snacks page; `setBatch()` resets it to 0 and `reset()` clears it.
- `AppStateService.snacks` holds the already-generated `Snack[]`; `Snack` carries `subtopicId` and `subtopicName`. `AppStateService.selectedSubtopic` holds the subtopic the user quizzed on.
- The snacks page short-circuits batch creation when `AppStateService.snacks().length > 0` (`init()` returns early), so returning to `/snacks` with existing snacks already avoids a new `POST /api/batches`.
- Progressive delivery: snacks may be `PENDING`/`GENERATING`/`READY`/`FAILED`; the current page only lets the user advance to a `READY` snack (delivered by 001 and hardened by 005/009). Existing E2E tests `progressive-delivery.spec.ts`, `back-navigation.spec.ts`, and `loading-states.spec.ts` protect this behavior.

This feature adds (1) a "Back to snacks" action to the results footer and (2) a browsable left-side snack index plus Next/Previous navigation, moving the active position into `AppStateService.currentSnackIndex` so it survives the round trip. It does **not** change generation, prompts, provider selection, batch status semantics, or persistence.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Return to the snacks from the quiz results (Priority: P1)

After finishing a quiz, the user wants to go back to the knowledge snacks they were reading, not restart the whole theme. The results screen offers a "Back to snacks" action beside "Start a new theme". Clicking it returns to `/snacks` with the already-generated snacks still there, and the snack the quiz was about (the quizzed subtopic) is the one shown, not an arbitrary first snack. No new generation is triggered.

**Why this priority**: This is the reported gap — the learning loop currently dead-ends at results unless the user restarts. Restoring the return path closes the loop (snacks → quiz → review → snacks) and is the core value of the feature. It is independently testable.

**Independent Test**: Complete a quiz, click "Back to snacks" on the results screen, and confirm the snacks page renders the previously generated snacks with the quizzed subtopic's snack active, and that no batch-creation request was issued. Delivers the value of "I can go back to what I was learning without losing it."

**Acceptance Scenarios**:

1. **Given** the user has completed a quiz and the results screen shows either "Quiz complete" or "Perfect score!", **When** the footer renders, **Then** a visible `<button>` labelled "Back to snacks" is present inside `.results-footer` next to "Start a new theme".
2. **Given** the "Back to snacks" button is shown, **When** the user clicks it, **Then** the app navigates to `/snacks` and no new batch is created (no additional `POST /api/batches`) and no snack is regenerated.
3. **Given** the user quizzed the subtopic stored in `AppStateService.selectedSubtopic`, **When** they return to `/snacks`, **Then** the snack whose `Snack.subtopicId` matches that subtopic is the active snack (not always the first snack).
4. **Given** snacks were already generated before the quiz, **When** the user returns to `/snacks`, **Then** the generated snacks remain available and no empty state, creation state, or loading state is shown for the existing snacks.
5. **Given** the user clicks "Start a new theme" instead, **When** the results footer is used, **Then** state is still reset and the user lands on the home page (existing restart behavior preserved; `restart-clears-state.spec.ts` keeps passing).
6. **Given** the "Back to snacks" button is added to the footer, **When** existing selectors target the restart action, **Then** the restart button remains uniquely selectable (the new action must not make the existing `.results-footer .primary-btn` selector ambiguous).

---

### User Story 2 — Browse every snack via a left-side index (Priority: P1)

The user can see an index/list of all snacks in the current set on the left side of the snacks page. The currently active snack is highlighted, and clicking any entry jumps directly to that snack. Entries for snacks that are still generating or that failed are not selectable, consistent with progressive delivery.

**Why this priority**: With several subtopics selected, the user currently can only step forward one snack at a time and cannot see what else is available. A visible index turns the set into browsable content and is the second half of the reported gap. It is independently testable.

**Independent Test**: Generate a set of snacks, confirm the left index lists every snack labelled by its subtopic name, confirm the active entry is highlighted, and confirm clicking another (ready) entry makes it active and updates the displayed card. Delivers the value of "I can see and jump to any snack."

**Acceptance Scenarios**:

1. **Given** the snacks page has a set of snacks, **When** it renders, **Then** a left-side index/list shows an entry for every snack in the current set, each labelled by its `Snack.subtopicName`.
2. **Given** the index is rendered, **When** a snack is active, **Then** exactly that entry is highlighted with `aria-current="true"` and the active CSS class.
3. **Given** the user clicks an index entry for a selectable (READY) snack, **When** the click is handled, **Then** that snack becomes active — the active index and the displayed content update together.
4. **Given** an entry corresponds to a snack that is still `GENERATING` (or otherwise not `READY`), **When** the index renders, **Then** that entry is disabled and cannot be selected (consistent with progressive delivery).
5. **Given** an entry corresponds to a snack that is `FAILED`, **When** the index renders, **Then** that entry is disabled and cannot be selected.

---

### User Story 3 — Step through snacks with Next/Previous and a persistent active index (Priority: P1)

The user can move through the snacks with Next and Previous controls, one snack at a time, reaching every snack. The active index is stored in `AppStateService.currentSnackIndex` (replacing the component-local index), so leaving the snacks page to take a quiz and coming back — including via "Back to snacks" — restores the snack the user was on.

**Why this priority**: The persisted active index is what makes the return-from-results flow preserve context, and Next/Previous completes the navigation model. P1 because without persistence the return action would still land on the wrong snack.

**Independent Test**: Move to a middle snack, start a quiz, return to `/snacks` (or navigate away and back), and confirm the same snack is active. Confirm Next/Previous move exactly one position and are safe at the boundaries. Delivers the value of "the app remembers where I was."

**Acceptance Scenarios**:

1. **Given** the snacks page is showing a snack that is not the last, **When** the user activates Next, **Then** the active index increases by exactly one and the displayed content matches the new active snack.
2. **Given** the snacks page is showing a snack that is not the first, **When** the user activates Previous, **Then** the active index decreases by exactly one and the displayed content matches the new active snack.
3. **Given** the active snack is the first, **When** the user activates Previous, **Then** the control is disabled (or a safe no-op), the index does not go out of range, and no blank card is shown.
4. **Given** the active snack is the last, **When** the user activates Next, **Then** the control is disabled (or a safe no-op), the index does not go out of range, and no blank card is shown.
5. **Given** the user can traverse the set, **When** they repeatedly activate Next (or Previous), **Then** every snack is reachable exactly once per pass, with no skipping and no out-of-range state.
6. **Given** the active index is stored in `AppStateService.currentSnackIndex`, **When** the user navigates away to the quiz and then returns to `/snacks`, **Then** the same snack is active (the index is preserved, not reset to 0).
7. **Given** a previously-READY snack is behind the current one, **When** the user activates Previous, **Then** they can move back to it; Previous is disabled only at index 0.
8. **Given** progressive delivery is still in progress, **When** the user activates Next, **Then** Next still advances only to a snack that is `READY` (preserved behavior; no advancing onto a generating/failed snack).
9. **Given** the user starts a quiz from the snacks page, **When** they activate "Start Quiz", **Then** the quiz uses the active snack's `subtopicId` (not a stale or first snack).

---

### User Story 4 — Accessible, consistent navigation controls (Priority: P2)

Every navigation control (the index entries, Next, Previous, and "Back to snacks") is a real interactive element with an accessible label, reachable and activatable by keyboard, with a visible focus indicator. After any combination of interactions, the displayed content, the highlighted index entry, and the Next/Previous enabled state stay in agreement.

**Why this priority**: Accessibility and state consistency are required for the controls to be usable and trustworthy, but the core return/browse value is delivered by US1–US3. P2 because it hardens rather than unlocks the feature.

**Independent Test**: Keyboard-tab through all controls, activate them with Enter/Space, confirm focus is visible, and after mixed interactions confirm the displayed snack, highlighted entry, and enabled state all agree. Delivers the value of "the navigation works for everyone and never lies about where I am."

**Acceptance Scenarios**:

1. **Given** the snacks page is rendered, **When** the user navigates by keyboard, **Then** every index entry, Next, Previous, and "Back to snacks" is reachable and activatable (Enter/Space for buttons; links behave as links), with a visible `:focus-visible` indicator.
2. **Given** any sequence of Next, Previous, and index clicks, **When** the interaction completes, **Then** the displayed snack content, the highlighted index entry (`aria-current="true"`), and the Next/Previous enabled/disabled states are all mutually consistent.
3. **Given** the index entries are rendered, **When** assistive technology reads them, **Then** each has an accessible name derived from the snack's subtopic name and its current/disabled state is conveyed.

---

### Edge Cases

- **Single snack in the set**: both Previous and Next are disabled (or safe no-ops); the single snack is active and the index shows one entry.
- **All snacks not yet READY (still generating)**: the index lists them but all entries are disabled; no card content is selectable; the existing progressive-delivery waiting/loading UI still applies.
- **All snacks FAILED**: the index lists them but all entries are disabled; the existing failure/retry affordances still apply.
- **Quizzed subtopic has no READY snack on return**: keep the last/current index rather than jumping to an arbitrary snack (per decision), and do not show an empty/creation state.
- **Stale/out-of-range stored index** (e.g. the snack set changed and the stored index now exceeds the set length): clamp to a valid in-range index (e.g. the last available) and never render a blank card.
- **No `selectedSubtopic` set** when returning: fall back to the current stored index (or first snack) without error.
- **Returning directly to `/snacks` with no snacks** (e.g. deep link): existing behavior is preserved (redirect to subtopics / normal creation path); this feature does not add a new empty state.
- **Rapid repeated Next/Previous or index clicks**: the index never leaves the valid range and content/highlight stay consistent.
- **Clicking a disabled index entry**: no state change and no error.
- **Perfect-score results screen**: the footer with "Back to snacks" renders identically to the non-perfect completion state.
- **Existing test selectors**: adding the new controls must not introduce extra matches for the selectors existing tests rely on (e.g. `.results-footer .primary-btn`, `.snack-card .primary-btn`, `button.link-btn` on snacks).

## Requirements *(mandatory)*

### Functional Requirements

**Return to snacks from results**

- **FR-001**: The results screen MUST render a visible `<button>` labelled "Back to snacks" inside `.results-footer`, next to "Start a new theme", in BOTH completion states ("Quiz complete" and "Perfect score!").
- **FR-002**: Activating "Back to snacks" MUST navigate to `/snacks` and MUST NOT trigger a new batch: no additional `POST /api/batches` is issued and no snack is regenerated.
- **FR-003**: Returning to `/snacks` MUST preserve the quizzed subtopic's context: the snack whose `Snack.subtopicId` matches `AppStateService.selectedSubtopic` MUST be the active snack, rather than always the first snack.
- **FR-004**: Already-generated snacks MUST remain available when returning from results; no empty state, creation state, or loading state is shown for snacks that already exist.
- **FR-005**: "Start a new theme" MUST continue to reset state and navigate to the home page; the existing restart behavior and `restart-clears-state.spec.ts` MUST keep passing.
- **FR-006**: The new "Back to snacks" control MUST NOT make the existing `.results-footer .primary-btn` selector ambiguous (the restart action remains uniquely selectable).

**Snacks navigation — index**

- **FR-007**: The snacks page MUST render a left-side index/list containing an entry for EVERY snack in the current set, each labelled by its `Snack.subtopicName`.
- **FR-008**: The active snack's index entry MUST be highlighted, exposing `aria-current="true"` and an active CSS class.
- **FR-009**: Activating an index entry for a selectable snack MUST make that snack active, updating both the active index and the displayed content.
- **FR-010**: Index entries whose snack is not `READY` (still `PENDING`/`GENERATING`, or `FAILED`) MUST be disabled and MUST NOT be selectable, consistent with progressive delivery.

**Snacks navigation — Next/Previous and persisted index**

- **FR-011**: The snacks page MUST provide Next and Previous controls that move the active index by exactly one position.
- **FR-012**: The user MUST be able to reach every snack through Next/Previous; the controls MUST be disabled or a safe no-op at the first and last positions, so the index never leaves the valid range and no blank card is shown.
- **FR-013**: The active index MUST be stored in `AppStateService.currentSnackIndex`; the snacks component MUST read from and write to it, replacing the component-local `currentIndex`.
- **FR-014**: Navigating away from `/snacks` (e.g. to the quiz or results) and back MUST preserve the active snack index, so the same snack is active on re-entry (including re-entry via "Back to snacks").
- **FR-015**: Previous MUST be able to move back to a previously-READY snack and MUST be disabled only at index 0.
- **FR-016**: Next MUST continue to advance only to a `READY` snack while progressive delivery is in progress (preserved behavior; no advancing onto a generating/failed snack).
- **FR-017**: "Start Quiz" MUST use the active snack's `subtopicId`.
- **FR-018**: When the quizzed subtopic has no `READY` snack on return, the system MUST keep the last/current index rather than jumping to an arbitrary snack.

**Consistency and accessibility**

- **FR-019**: After any combination of Next, Previous, and index interactions, the displayed content, the highlighted index entry, and the Next/Previous enabled/disabled states MUST remain mutually consistent (no desync).
- **FR-020**: All navigation controls (index entries, Next, Previous, "Back to snacks") MUST be real `<button>` or `<a>` elements with accessible labels, MUST be keyboard reachable and activatable, and MUST show a visible `:focus-visible` indicator.

**Non-regression and scope**

- **FR-021**: The feature MUST NOT regress `progressive-delivery.spec.ts`, `back-navigation.spec.ts`, or `loading-states.spec.ts`; in particular Next must still only advance to a READY snack during progressive delivery.
- **FR-022**: The backend MUST remain unchanged by this feature: no new endpoints, no response-shape changes, and no schema/persistence changes; the snacks page continues to short-circuit batch creation when snacks already exist.
- **FR-023**: New controls MUST NOT introduce additional matches for selectors existing tests depend on (e.g. `.results-footer .primary-btn`, `.snack-card .primary-btn`, `button.link-btn` on the snacks page).

### Key Entities *(include if feature involves data)*

- **Snack** (existing): A knowledge card for a subtopic. Key attributes used here: `subtopicId` (identity used to match the quizzed subtopic and to start the quiz), `subtopicName` (label for the index entry).
- **AppStateService navigation state** (existing, now used): `currentSnackIndex` (the active position in the snack set), `selectedSubtopic` (the subtopic the user quizzed on), `snacks` (the already-generated set). `currentSnackIndex` becomes the single source of truth for the active snack across page navigation.
- **Snack set / index** (UI concept): The ordered list of snacks in the current batch, presented as a left-side index; one entry is active, and selectable entries correspond to READY snacks.
- **Results footer actions** (UI concept): The completion-screen actions — existing "Start a new theme" (reset + home) and new "Back to snacks" (return to `/snacks` preserving state).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of completed-quiz results screens (both "Quiz complete" and "Perfect score!") render a visible "Back to snacks" control in the footer; verified across both completion states.
- **SC-002**: Clicking "Back to snacks" issues 0 additional `POST /api/batches` requests and regenerates 0 snacks in 100% of return runs.
- **SC-003**: When the quizzed subtopic has a READY snack, the matching snack is active on return in 100% of tested cases (0 cases of always landing on the first snack).
- **SC-004**: The left index lists 100% of snacks in the current set, and exactly one entry is highlighted (`aria-current="true"`) at any time.
- **SC-005**: Next and Previous change the active index by exactly one in 100% of activations, and 0 activations produce an out-of-range index or a blank card.
- **SC-006**: The active index survives a quiz/return round trip (and any navigate-away-and-back) in 100% of tested cases; the same snack is active on return.
- **SC-007**: After any sequence of navigation interactions, the displayed content, highlighted entry, and enabled/disabled control states agree in 100% of tested sequences (0 desyncs).
- **SC-008**: 100% of navigation controls are keyboard reachable/activatable with a visible focus indicator and have an accessible name.
- **SC-009**: Non-regression: `progressive-delivery.spec.ts`, `back-navigation.spec.ts`, `loading-states.spec.ts`, and `restart-clears-state.spec.ts` pass unchanged.
- **SC-010**: Backend contract and persistence are unchanged — 0 new endpoints, 0 response-shape changes, 0 schema changes attributable to this feature.

## Non-Goals

- **No backend changes**: no new API endpoints, no batch/quiz/snack generation changes, no provider or prompt changes, no persistence/schema/migration changes.
- **No redesign of the snack card or quiz flow**: the single-card reading experience and progressive delivery are preserved, not replaced.
- **No new empty/creation state**: existing empty/loading/failure handling on the snacks page is preserved.
- **Responsive/mobile layout for the left index is NOT required this pass** (explicitly deferred).
- **Theme history (backlog Item 11) is out of scope.**
- **No changes to results scoring, XP, accuracy, or the end-of-test snack recap** (feature 001 behavior is preserved).
- **No changes to authentication/user context**; requests remain anonymous as in the MVP.
- **No auto-advance, autoplay, keyboard shortcuts beyond standard activation, or animated transitions** are required.

## Assumptions

- The snacks page already short-circuits batch creation when `AppStateService.snacks().length > 0`, so returning from results with existing snacks needs no backend change (confirmed input).
- `Snack.subtopicId` reliably identifies the quizzed subtopic via `AppStateService.selectedSubtopic`, and `Snack.subtopicName` is a suitable index label.
- The snack set in `AppStateService.snacks` is the authoritative "current set" for the index; snacks are not removed during the session.
- The active index is a session-scoped UI concern; no server persistence is required.
- The results footer is shared by both completion states, so one control satisfies both.
- The existing E2E suite (progressive delivery, back navigation, loading states, restart) remains the regression contract for this change.
- The feature is frontend-only and can ship independently of any backend work; the backend is expected unchanged.
- Where the spec references existing class names/selectors (`.results-footer`, `.snack-card`, `.primary-btn`, `.link-btn`), those are preserved conventions rather than new design decisions.

## Clarifications

The following decisions were made by the user and are encoded above; they resolve the open questions for this feature:

- **Label and placement (resolved)**: The new action is labelled exactly "Back to snacks" and is placed in the results footer next to "Start a new theme" (FR-001).
- **Non-READY index entries (resolved)**: Index entries for snacks still `GENERATING` or `FAILED` are disabled, consistent with progressive delivery (FR-010).
- **No READY snack for the quizzed subtopic (resolved)**: keep the last/current index rather than jumping to an arbitrary snack (FR-018).
- **Previous behavior (resolved)**: Previous can move back to a previously-READY snack and is disabled only at index 0 (FR-015).
- **Responsive left index (resolved)**: responsive behavior on narrow screens is NOT required this pass (Non-Goals).
- **Theme history (resolved)**: backlog Item 11 is out of scope (Non-Goals).
- **State ownership (resolved)**: the active index is wired through `AppStateService.currentSnackIndex` so it survives re-entry from the results screen (FR-013, FR-014).

No `[NEEDS CLARIFICATION]` markers remain; all blocking ambiguities were resolved by the decisions above and the confirmed current code state.
