# Data Model: Return to Snacks from Results + Snacks Navigation

**Feature**: `010-snacks-navigation`

**Persistence**: **NONE.** This is a frontend/UI-only feature. No MySQL table, column, index,
constraint, JPA entity, enum, DTO, or migration changes. The backend model is frozen (FR-022/SC-010).

> This document models the **frontend navigation state** — the signals in `AppStateService` and the
> derived component state in `SnacksComponent`/`ResultsComponent`. It is the UI analogue of the
> backend `data-model.md` used by prior features. It is compatible with
> `/specs/001-batch-mvp-enhancements/data-model.md` (which owns the `Snack`/`BatchItem` shapes) and
> reuses the existing `Snack` and `BatchItem` interfaces unchanged.

---

## 1. Shared UI State — `AppStateService`

Existing signals; the feature only changes **which of them are read/written** by the pages.

| Signal | Type | Existing? | Role in this feature |
|--------|------|-----------|----------------------|
| `snacks` | `WritableSignal<Snack[]>` | Yes | The authoritative "current set" (READY snacks). Source for return-path hydration (FR-004) and the `subtopicId` match (FR-003). |
| `selectedSubtopic` | `WritableSignal<Subtopic \| null>` | Yes | The subtopic the user quizzed on. Matched against `Snack.subtopicId` on return (FR-003). |
| `currentSnackIndex` | `WritableSignal<number>` | Yes (previously unused by the page) | **Single source of truth** for the active position. Read/written by `SnacksComponent`; set by `ResultsComponent` on return (FR-013/FR-014). |
| `subtopics` | `WritableSignal<Subtopic[]>` | Yes | Resolves the `Subtopic` object for `startQuiz`; not changed. |
| `batchId`, `quizQuestions`, `quizSubTopicId`, `quizPrefetched`, `quizPrefetchBySubtopic` | — | Yes | Unchanged by this feature. |

### Lifecycle / reset rules (preserved)

| Operation | Effect on `currentSnackIndex` | Notes |
|-----------|-------------------------------|-------|
| `setBatch(batchId)` | `.set(0)` | New batch starts at the first snack. |
| `reset()` | `.set(0)` | "Start a new theme" clears everything (FR-005). |
| `backToSnacks()` (new) | `.set(matchedIndex)` if found, else unchanged | Must **not** call `reset()` (FR-002/FR-005). |
| `next()` / `prev()` / `selectIndex(i)` | `.set(target)` | Clamped target, in range (FR-012). |

### New/changed service members (optional helpers)

To keep matching logic in one place, add small helpers (names illustrative; exact shape is an
implementation choice):

| Member | Signature | Purpose |
|--------|-----------|---------|
| `setSnackIndex` | `(index: number) => void` | Write the active index (bounds-checked or clamped by the caller). |
| `snackIndexForSubtopic` | `(subtopicId: string \| null) => number` | Return the index in `snacks()` whose `subtopicId` matches, or `-1` (FR-003/FR-018). |

`advanceSnack()`/`resetSnackProgress()` already exist and may be reused or superseded by the
component writing `currentSnackIndex` directly.

---

## 2. View Model — `SnacksComponent`

| Member | Kind | Type | Definition | Spec link |
|--------|------|------|------------|-----------|
| `items` | signal | `BatchItem[]` | Current batch items. During live generation filled by polling; on return hydrated from `appState.snacks()`. | FR-007/FR-010 |
| `currentIndex` | computed | `number` | `clamp(appState.currentSnackIndex(), 0, items.length - 1)` (0 when empty). Replaces the local signal. | FR-013/FR-012 |
| `currentSnack` | computed | `BatchItem \| null` | `items()[currentIndex()] ?? null`. | FR-009/FR-019 |
| `nextSnack` | computed | `BatchItem \| null` | `items()[currentIndex() + 1] ?? null`. | FR-016 |
| `hasPrev` | computed | `boolean` | `currentIndex() > 0`. | FR-015 |
| `hasNext` | computed | `boolean` | `currentIndex() < items().length - 1`. | FR-012 |
| `canNext` | computed | `boolean` | `hasNext() && nextSnack()?.snackStatus === 'READY'`. | FR-016 |
| `creating` / `polling` | signal | `boolean` | On the return path both set `false` (no spinner). | FR-004 |
| `showEmptyState` | computed | `boolean` | Unchanged; already excludes the return path because `items().length > 0`. | FR-004 |

### Derived-state invariants

1. `currentIndex()` is always in `[0, items().length - 1]` (or `0` when empty) — never out of range
   (FR-012/SC-005).
2. `currentSnack()`, the highlighted index entry, and the Next/Previous enabled states are all derived
   from `currentIndex()` + `items()`; therefore they cannot desync (FR-019/SC-007).
3. `canNext()` is `false` whenever the next item is not `READY` (FR-016/FR-021).
4. `hasPrev()` is `false` only at index 0 (FR-015).

---

## 3. The Index View Model

The left index is derived from `items()` (no separate signal).

| Field | Source | Display | Spec link |
|-------|--------|---------|-----------|
| label | `item.subtopicName` | Button text | FR-007 |
| active | `i === currentIndex()` | `aria-current="true"` + `.snack-index__item--active` | FR-008 |
| selectable | `item.snackStatus === 'READY'` | `[disabled]` when not READY | FR-010 |
| identity | `item.subtopicId` (`@for` track) | Stable ordering | — |

```
items(): [ {subtopicId, subtopicName, snackStatus, snack, ...}, ... ]
                       │
        ┌──────────────┴───────────────┐
        ▼                              ▼
  currentIndex()                 index entry i
  (clamped shared signal)        label = subtopicName
        │                        active  ⇔ i === currentIndex()
        ▼                        disabled ⇔ snackStatus !== 'READY'
  currentSnack = items()[currentIndex()]
```

---

## 4. State Transitions

| Trigger | Guard | New `currentSnackIndex` | Side effects |
|---------|-------|-------------------------|--------------|
| `next()` | `hasNext() && nextSnack().snackStatus === 'READY'` | `+1` | Card + highlight update (FR-016). |
| `prev()` | `hasPrev()` | `-1` | Card + highlight update (FR-015). |
| `selectIndex(i)` | `items()[i].snackStatus === 'READY'` | `i` | Card + highlight update (FR-009). |
| `backToSnacks()` (results) | match found in `snacks()` | `matchedIndex` | Navigate `/snacks`; no HTTP (FR-003). |
| `backToSnacks()` (no match) | — | unchanged | Keep last index (FR-018). |
| `init()` return path | `appState.snacks().length > 0` | clamped existing value (written back) | Hydrate `items()`, `creating=false`, `polling=false` (FR-004). |
| `setBatch()` / `reset()` | — | `0` | Existing lifecycle behavior (FR-005). |

### Return-path hydration mapping

```
Snack → BatchItem (return path)
{
  subtopicId:    snack.subtopicId,
  subtopicName:  snack.subtopicName,
  snackStatus:   'READY',
  snack:         snack,
  snackCause:    null,
  quizStatus:    'READY',
  quizCause:     null
}
```

The mapping is lossless for the card (uses `snack.concept/metaphor/whyItMatters/related`) and the
index (uses `subtopicName`) and preserves the `subtopicId` used by "Start Quiz" (FR-017).

---

## 5. Boundary & Edge-Case Handling

| Case | Behavior | Spec link |
|------|----------|-----------|
| Single snack | `hasPrev=false`, `hasNext=false`; both controls disabled; one index entry active. | Edge Cases |
| All items not yet READY | Index lists all, all disabled; existing loading/waiting UI still applies. | Edge Cases, FR-010 |
| All items FAILED | Index lists all, all disabled; existing failure/retry affordance still applies. | Edge Cases, FR-010 |
| Quizzed subtopic has no READY snack | Keep the stored index (no arbitrary jump). | FR-018 |
| Stale/out-of-range stored index | Clamped on read (and written back in `init()`); never a blank card. | Edge Cases, FR-012 |
| No `selectedSubtopic` on return | Fall back to `quizSubTopicId()`, then keep the current index. | Edge Cases |
| Direct deep link to `/snacks` with no snacks | Existing creation/redirect path unchanged. | Edge Cases |
| Rapid Next/Previous/index clicks | `goTo` rejects out-of-range targets; no desync. | Edge Cases, SC-005/SC-007 |
| Click a disabled entry | Native `disabled` → no event, no state change. | Edge Cases, FR-010 |

---

## 6. Persistence / Schema Change Statement

**No persistence or schema change is required or made by this feature.**

- No JPA entity, field, column, index, constraint, enum, or DTO change.
- No MySQL interaction; no migration (`ddl-auto=update` produces no delta).
- The active index is session-scoped in-memory `AppStateService` state; it is intentionally **not**
  persisted to the server or `localStorage` (spec Assumptions).

This matches FR-022/SC-010: the backend contract and persistence are unchanged.
