# UI Contract: Snacks Navigation & Return-from-Results

**Feature**: `010-snacks-navigation`

**Type**: Frontend UI contract (component API + DOM/ARIA surface). No HTTP contract changes.

This document is the stable interface between the implementation and the Playwright E2E suite. It
lists the component methods, the DOM selectors, the ARIA contract, and the state semantics that the
tests rely on. Selector names are intentional contracts, not incidental styling.

---

## 1. `ResultsComponent`

### Public API

| Member | Signature | Contract |
|--------|-----------|----------|
| `restart` | `() => void` | Existing. Calls `AppStateService.reset()` and navigates to `/`. **Unchanged** (FR-005). |
| `backToSnacks` | `() => void` | **New.** Sets `AppStateService.currentSnackIndex` to the index of the snack whose `subtopicId` matches `selectedSubtopic()?.id` (falling back to `quizSubTopicId()`); if no match, leaves the index unchanged (FR-018). Navigates to `/snacks`. **Never** calls `reset()` and issues **no** HTTP request (FR-002/FR-003). |

### DOM / selector contract

| Selector | Element | Meaning |
|----------|---------|---------|
| `.results-footer` | `div` | Shared footer rendered in both completion states ("Quiz complete" and "Perfect score!"). |
| `.results-footer .primary-btn` | `button` | **"Start a new theme"** — the only `primary-btn` in the footer. Must remain uniquely selectable (FR-006). |
| `.results-footer .ghost-btn[data-testid="back-to-snacks"]` | `button` | **"Back to snacks"** — the new action. Carries **no** `primary-btn` class (FR-023). |

### Invariants

- The footer contains exactly two actions in both completion states.
- `.results-footer .primary-btn` matches exactly one element.
- Clicking "Back to snacks" does not increase the count of `POST /api/batches` requests (SC-002).

---

## 2. `SnacksComponent`

### Public API

| Member | Signature | Contract |
|--------|-----------|----------|
| `currentIndex` | `computed(): number` | Clamped read of `AppStateService.currentSnackIndex` into `[0, items().length - 1]` (FR-013/FR-012). |
| `currentSnack` | `computed(): BatchItem \| null` | `items()[currentIndex()]` (FR-009/FR-019). |
| `nextSnack` | `computed(): BatchItem \| null` | `items()[currentIndex() + 1]` (FR-016). |
| `hasPrev` | `computed(): boolean` | `currentIndex() > 0` (FR-015). |
| `hasNext` | `computed(): boolean` | `currentIndex() < items().length - 1` (FR-012). |
| `canNext` | `computed(): boolean` | `hasNext() && nextSnack()?.snackStatus === 'READY'` (FR-016). |
| `prev` | `() => void` | If `hasPrev()`, set the shared index to `currentIndex() - 1`. Disabled only at 0 (FR-015). |
| `next` | `() => void` | If `canNext()`, set the shared index to `currentIndex() + 1` (FR-016). |
| `selectIndex` | `(i: number) => void` | If `items()[i].snackStatus === 'READY'`, set the shared index to `i` (FR-009/FR-010). |
| `startQuiz` | `() => void` | Derives the subtopic from the active snack (`currentSnack()?.snack?.subtopicId`) and sets `selectedSubtopic` before navigating to `/quiz` (FR-017). |
| `init` (private) | `() => Promise<void>` | If `appState.snacks().length > 0`: hydrate `items()`, set `creating=false`/`polling=false`, clamp the index, and return **without** creating a batch (FR-002/FR-004). |

### DOM / selector contract

| Selector | Element | Meaning |
|----------|---------|---------|
| `.snack-index` | `nav` | Left-side index container; `aria-label="Snack list"`. |
| `.snack-index__item` | `button` | One entry per snack in `items()`, text = `subtopicName` (FR-007). |
| `.snack-index__item[aria-current="true"]` | `button` | The single active entry (FR-008). |
| `.snack-index__item--active` | class | Active-entry visual class; applied together with `aria-current` (FR-008). |
| `.snack-index__item[disabled]` | `button` | Entry whose snack is not `READY`; not selectable (FR-010). |
| `.snack-card` | `article` | The single displayed snack card (unchanged). |
| `.snack-card .primary-btn` | `button` | **"Next →"** — the only `primary-btn` inside the card (FR-023). |
| `.snack-card .ghost-btn` | `button` | **"← Previous"** — new; carries no `primary-btn`/`link-btn` class. |
| `.snack-card__chip` | `span` | Active snack's `subtopicName` (used by tests to assert the active card). |
| `button.link-btn` | `button` | Existing "← Back to subtopics" / "Retry"; unchanged and still uniquely selectable (FR-023). |

### ARIA contract

- Index entry accessible name = `subtopicName`.
- Active index entry: `aria-current="true"` and the active class.
- Non-READY entries: native `disabled` (not selectable, state conveyed).
- Next/Previous/Back-to-snacks: real `<button type="button">` with a text label; disabled at
  boundaries (Next: `!canNext()`; Previous: `!hasPrev()`).
- All new controls show a visible `:focus-visible` indicator (FR-020/SC-008).

### State invariants (testable)

1. **Single highlight**: at most one `.snack-index__item[aria-current="true"]` at any time (SC-004).
2. **Content/highlight agreement**: the `.snack-card__chip` text equals the active entry's text
   (FR-019/SC-007).
3. **Bounds**: `currentIndex()` never exceeds the item range; no blank card is rendered (FR-012).
4. **Persistence**: the active snack after a `/snacks → /quiz → /results → /snacks` round trip equals
   the snack that was active before leaving (FR-014/SC-006).
5. **No return-side generation**: re-entering `/snacks` with existing snacks issues no
   `POST /api/batches` and shows no creation/loading state (FR-002/FR-004).

---

## 3. `AppStateService` (navigation state)

| Member | Type | Contract |
|--------|------|----------|
| `currentSnackIndex` | `WritableSignal<number>` | **Single source of truth** for the active snack position (FR-013). |
| `snacks` | `WritableSignal<Snack[]>` | Authoritative current set used to hydrate on return (FR-004). |
| `selectedSubtopic` | `WritableSignal<Subtopic \| null>` | Quizzed subtopic matched by `subtopicId` (FR-003). |
| `setBatch(batchId)` | method | Resets `currentSnackIndex` to 0 (existing). |
| `reset()` | method | Resets all state including `currentSnackIndex` (existing; called only by `restart()`). |

---

## 4. E2E-Testable Surface Summary

| Story | Spec FRs | Primary assertions |
|-------|----------|--------------------|
| US1 | FR-001..FR-006, FR-018 | `.results-footer .ghost-btn[data-testid="back-to-snacks"]` visible in both states; click → `/snacks`; `POST /api/batches` count unchanged; `.snack-card__chip` = quizzed subtopic; no `.loading` for existing snacks; restart → `/`. |
| US2 | FR-007..FR-010 | `.snack-index__item` count = item count; labels = `subtopicName`; exactly one `[aria-current="true"]`; click jumps; non-READY entries `disabled`. |
| US3 | FR-011..FR-018 | Next/Previous ±1; disabled at boundaries; active snack survives round trip; Start Quiz uses active `subtopicId`. |
| US4 | FR-019..FR-020 | After mixed interactions, chip/highlight/enabled agree; keyboard reachable with `:focus-visible`. |
| Non-regression | FR-021..FR-023 | Existing specs pass unchanged; selectors remain unambiguous. |
