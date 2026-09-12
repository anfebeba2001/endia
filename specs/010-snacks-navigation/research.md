# Research: Return to Snacks from Results + Snacks Navigation

**Phase 0 output** for `/specs/010-snacks-navigation`. Each section records the Decision, Rationale,
and Alternatives considered, grounded in the real frontend code
(`frontend/src/app/pages/results/results.component.{ts,html}`,
`frontend/src/app/pages/snacks/snacks.component.{ts,html}`,
`frontend/src/app/services/app-state.service.ts`, `frontend/e2e/*.spec.ts`).

This is a **frontend/UI-only** feature. The backend is a confirmed input and is unchanged (FR-022).
The governing constraint is the smallest correct change set: no new endpoint/DTO/schema, no new
dependency, no new component/route, no generation change, and preservation of the existing Playwright
regression contract.

---

## R1. How the return path avoids creating a new batch (and still shows the snacks)

**Decision**: `ResultsComponent.backToSnacks()` navigates to `/snacks` **without** calling
`appState.reset()` and **without** any HTTP call. On the return path, `SnacksComponent.init()` detects
`appState.snacks().length > 0` and **hydrates its `items()` signal** from `appState.snacks()` instead
of calling `createBatch`, then sets `creating = false` and `polling = false`.

**Rationale**:
- The spec's confirmed input is that `init()` short-circuits when snacks already exist, so no
  `POST /api/batches` is issued. That satisfies FR-002 directly.
- **But the existing early-return does not populate `items()`**, which is the signal the template
  renders from. Without hydration, returning to `/snacks` would leave `creating` at its initial
  `true` and show the "Warming up the knowledge oven…" creation spinner indefinitely — violating
  FR-004 ("no empty/creation/loading state is shown for snacks that already exist").
- `appState.snacks()` is the spec-defined authoritative "current set" (Assumptions) and carries
  `subtopicId`, `subtopicName`, and the full `Snack` payload, so mapping it to READY `BatchItem`s is
  lossless for both the card and the index.
- Hydration is synchronous in the constructor-triggered `init()`, so there is no flash of loading
  state.

**Alternatives**:
- **Change `currentSnack`/template to read `snacks()` when `items()` is empty**: rejected — it
  creates two rendering paths and leaves `currentIndex` indexing a different array, inviting desync.
- **Persist the full `BatchItem[]` in `AppStateService` and restore it verbatim**: rejected — more
  state to maintain; `snacks` is already declared the authoritative set, and only READY items are
  meaningful after a completed round trip.
- **Re-create the batch on return**: rejected — violates FR-002/SC-002 and wastes generation.

---

## R2. How the "Back to snacks" button avoids breaking the existing strict selector

**Decision**: Render the new action as `<button class="ghost-btn" data-testid="back-to-snacks">`,
using the existing global `.ghost-btn` primitive. Do **not** give it the `primary-btn` class. Layout
`.results-footer` as a centered flex row with a gap.

**Rationale**:
- `restart-clears-state.spec.ts` uses `page.click('.results-footer .primary-btn')`. Playwright runs in
  strict mode by default: if a second element also carries `primary-btn`, that locator resolves to two
  nodes and the test fails. `.ghost-btn` is a distinct class, so the selector still matches exactly
  the restart button (FR-006/FR-023).
- `.ghost-btn` already exists in `styles.scss` with hover/active/disabled styling and inherits the
  global `:focus-visible` outline, so no new button primitive is needed and the visual language stays
  consistent.
- `data-testid` gives the new spec a stable, intention-revealing handle independent of styling.
- Both completion states render the same `.results-footer`, so one control covers "Quiz complete" and
  "Perfect score!" (FR-001/SC-001).

**Alternatives**:
- **`class="primary-btn ghost-btn"`**: rejected — still matches `.primary-btn` and breaks strict mode.
- **Update `restart-clears-state.spec.ts` to a more specific selector and use `primary-btn` for both**:
  rejected — the spec explicitly requires the restart selector to remain unambiguous and the existing
  test to pass unchanged (FR-005/FR-006/SC-009). Changing a regression test to accommodate a new
  control is the opposite of the intent.
- **Put the new button outside `.results-footer`**: rejected — FR-001 requires it inside the footer
  next to "Start a new theme".

---

## R3. Where the active index lives and how it is read

**Decision**: Store the active index only in `AppStateService.currentSnackIndex`. Remove the
component-local `currentIndex` signal. In `SnacksComponent`, expose a **clamped computed**
`currentIndex` that reads the service signal and clamps to `[0, items.length - 1]`; all writes
(`prev`/`next`/`selectIndex`) set the service signal.

**Rationale**:
- `AppStateService.currentSnackIndex` already exists, is already reset by `setBatch()` and `reset()`,
  and is the natural session-scoped store — using it is exactly FR-013.
- A single stored value with a clamped read guarantees `currentSnack`, `nextSnack`, `hasPrev`,
  `hasNext`, and `canNext` are all derived from one source, so content/highlight/enabled states cannot
  desync (FR-019/SC-007).
- Clamping on read means a stale or out-of-range stored index (e.g. after the set changes) can never
  produce a blank card (spec edge case, FR-012). `init()` additionally writes the clamped value back
  for hygiene.
- Because the value survives component destruction, navigating to the quiz/results and back restores
  the same snack (FR-014/SC-006).

**Alternatives**:
- **Keep the local signal and mirror to the service with an effect**: rejected — two sources of truth
  and an ordering hazard; FR-013 explicitly says "replacing the component-local `currentIndex`".
- **Use a route query param (`?snack=2`)**: rejected — no route change is wanted, and it would add
  URL/history noise and a new contract for a purely session-scoped concern.
- **Persist to `localStorage`**: rejected — the spec says session-scoped, no server persistence; the
  in-memory service already covers navigation within the SPA.

---

## R4. Next vs. Previous semantics under progressive delivery

**Decision**:
- `next()` advances **only** when the next item is `READY`; the Next button is disabled unless
  `hasNext() && nextSnack()?.snackStatus === 'READY'`.
- `prev()` moves back whenever `currentIndex() > 0`, **without** requiring the previous item to be
  `READY`; the Previous button is disabled only at index 0.

**Rationale**:
- FR-016 explicitly preserves the progressive-delivery rule: "Next still advances only to a snack
  that is READY (no advancing onto a generating/failed snack)". Keeping the rule preserves
  `progressive-delivery.spec.ts` (FR-021).
- FR-015 explicitly says Previous can move back to a previously-READY snack and is disabled only at
  index 0. Since a READY snack never becomes un-ready, decrementing to any previous index is safe; the
  card either shows a READY snack or a loading/error state, never a blank.
- Boundaries are enforced by `goTo(index)` rejecting out-of-range values, so rapid clicks can never
  push the index out of range (FR-012/SC-005).

**Interpretation flagged for the orchestrator**: with a FAILED item in the middle, linear Next cannot
pass it (READY-only). Such snacks remain reachable via the left index once they are READY. This is
the intended interaction of FR-012 ("reach every snack") with FR-016 ("Next only to READY") and is
why the index exists. It is an interpretation, not a defect. In the normal all-READY completion state,
Next reaches every snack exactly once per pass.

**Alternatives**:
- **Make Next advance regardless of status**: rejected — breaks FR-016 and progressive delivery.
- **Disable Previous when the previous item is not READY**: rejected — contradicts FR-015.
- **Wrap-around Next/Previous**: rejected — the spec requires boundary disable / safe no-op, not
  cycling.

---

## R5. What the left index is sourced from

**Decision**: Render the index from `items()` — the component's current batch item array. During live
progressive delivery that array contains every item with its `snackStatus` (READY / GENERATING /
FAILED); on the return path it is hydrated from `appState.snacks()` (READY only). Entries are labelled
by `item.subtopicName`; non-READY entries are `disabled`.

**Rationale**:
- The spec defines the "current set" as the snacks held in `AppStateService.snacks` (Assumptions) and
  requires "an entry for EVERY snack in the current set" (FR-007). Sourcing from `items()` keeps the
  index and the displayed card perfectly aligned (same array, same order), which is what makes
  FR-008/FR-009/FR-019 consistent.
- During generation, `items()` also contains not-yet-READY entries, which the spec wants visible but
  disabled (FR-010); sourcing from `snacks()` (READY-only) would hide them and contradict FR-010.
- On return, the set is already fully generated, so `items()` == `appState.snacks()` in content; the
  index is shorter only because there are no pending/failed items left, which is correct.

**Alternatives**:
- **Source the index from `appState.snacks()` only**: rejected — hides generating/failed entries and
  desyncs from `items()` ordering.
- **Maintain a separate `indexItems` signal**: rejected — a second array invites desync; `items()` is
  already the view model.

---

## R6. Accessibility approach

**Decision**: All new controls are real `<button type="button">` elements with text accessible names.
The active index entry sets `aria-current="true"` and an active class; non-READY entries use the
native `disabled` attribute. The index is a `<nav aria-label="Snack list">`. Explicit `:focus-visible`
styles are added for `.snack-index__item` and the nav buttons (the global rule already covers plain
buttons).

**Rationale**:
- FR-020/SC-008 require real interactive elements, accessible names, keyboard reachability/
  activation, and a visible focus indicator. Native buttons provide all of this for free, including
  Enter/Space activation and disabled semantics.
- `aria-current="true"` is the spec's exact requirement for the active entry (FR-008); native
  `disabled` conveys the non-selectable state (FR-010).
- The existing global `:focus-visible` rule is kept; per-class rules make the indicator explicit and
  testable.

**Alternatives**:
- **`<div role="button" tabindex="0">`**: rejected — reimplements native behavior and risks missing
  key handling.
- **`aria-disabled` instead of `disabled`**: rejected — native `disabled` prevents activation entirely
  and is simpler; the spec says "disabled and cannot be selected".
- **`aria-selected`/`role="tab"`**: rejected — the index is navigation, not a tablist, and FR-008
  specifically names `aria-current`.

---

## R7. Where the "quizzed subtopic" match happens

**Decision**: `ResultsComponent.backToSnacks()` performs the match:
`snacks.findIndex(s => s.subtopicId === selectedSubtopic()?.id)` (falling back to
`quizSubTopicId()`), sets `appState.currentSnackIndex` when found, and leaves it unchanged otherwise.
`SnacksComponent` does **not** re-apply `selectedSubtopic` on mount.

**Rationale**:
- The intent "return to the snack the quiz was about" is only valid at the moment the user chooses to
  return from results. Applying it on every `SnacksComponent` mount would override the user's manual
  navigation on subsequent re-entries and contradict FR-014 (preserve the active index).
- Matching by `subtopicId` (not array position) is robust and is exactly FR-003.
- If no READY snack matches, leaving the index unchanged satisfies FR-018 ("keep the last/current
  index rather than jumping to an arbitrary snack").
- `selectedSubtopic` is set by `SnacksComponent.startQuiz(...)` before navigating to the quiz, so it
  is reliably present on results; `quizSubTopicId()` is used by `loadSummary()` and provides a
  defensive fallback.

**Alternatives**:
- **Match inside `SnacksComponent.init()` on every mount**: rejected — overrides later manual
  navigation and duplicates the decision.
- **Navigate to a `/snacks?subtopic=...` URL**: rejected — adds a route/contract for a session-only
  concern.
- **Match by array position between `subtopics()` and `snacks()`**: rejected — brittle if any item is
  not READY or the order differs.

---

## R8. Preserving "Start a new theme" and the restart test

**Decision**: Leave `ResultsComponent.restart()` exactly as-is (`appState.reset()` + navigate `/`) and
keep the restart button as the only `.primary-btn` in `.results-footer`.

**Rationale**:
- FR-005 requires the existing reset-and-home behavior; `restart-clears-state.spec.ts` asserts both the
  navigation and the fresh home page.
- Because the new action uses `.ghost-btn`, the existing selector remains unique (FR-006/SC-009).

**Alternatives**:
- **Unify both actions behind one handler**: rejected — the two actions have opposite state semantics
  (reset vs. preserve) and must stay distinct.

---

## R9. Test surface and non-regression

**Decision**: Add two focused Playwright specs and keep the entire existing suite unchanged. No
Karma/Jest runner is introduced.

**Rationale**:
- Playwright is the project's established frontend test surface (see `specs/009/plan.md`: "No
  Karma/Jest runner exists"). The navigation/return behavior is end-to-end (routing + shared state +
  rendering), so E2E is the right level.
- The two new specs map directly to the user stories and success criteria; the existing suite is the
  regression contract named by FR-021/SC-009.
- Signal-only logic (clamping) is small and fully observable through the UI, so a unit runner would be
  extra infrastructure for little gain this pass.

**Alternatives**:
- **Add a Karma/Jest unit test for the clamp**: deferred; noted as a natural future addition if a
  runner is introduced.
- **A single combined spec**: rejected — US1 (return path) and US2/US3/US4 (browsing/navigation) have
  different fixtures and failure modes; separate specs are clearer and faster to diagnose.

---

## Cross-cutting: what is NOT changed

- No backend endpoint, DTO, controller, service, prompt, provider, or schema change (FR-022/SC-010).
- No change to `BatchService`/`batch.service.ts`, `SnackRetryService`, or the polling/retry logic.
- No new Angular component, route, service, or npm dependency.
- No change to generation, batch status semantics, progressive delivery, or the snack card content.
- No responsive/mobile layout for the index (explicitly deferred).
- No theme history (Item 11, out of scope).
- No change to results scoring, XP, accuracy, or the recap.
