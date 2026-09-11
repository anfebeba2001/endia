# Implementation Plan: Return to Snacks from Results + Snacks Navigation

**Branch**: `010-snacks-navigation` | **Date**: 2026-09-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-snacks-navigation/spec.md`

## Summary

Frontend-only feature that closes the learning loop (`snacks → quiz → results → snacks`) and turns
the snack set into browsable content. Two changes, both in the Angular app:

1. **Return path (US1, FR-001..FR-006)**: add a "Back to snacks" `<button>` to the shared
   `.results-footer` beside "Start a new theme". It navigates to `/snacks` **without** calling
   `AppStateService.reset()` and without triggering a new batch. Before navigating it sets
   `AppStateService.currentSnackIndex` to the index of the snack whose `subtopicId` matches
   `AppStateService.selectedSubtopic` (so the quizzed subtopic's snack is active), or leaves the
   index unchanged when there is no matching READY snack (FR-018).
2. **Browsable snacks (US2/US3/US4, FR-007..FR-020)**: add a left-side index of all snacks
   (labelled by `subtopicName`, active entry highlighted with `aria-current="true"` + active class,
   non-READY entries disabled, click to jump) and Previous/Next controls. Replace the component-local
   `currentIndex` signal with `AppStateService.currentSnackIndex` so the active position survives the
   round trip. Preserve progressive delivery: Next only advances to a READY snack; Previous is
   disabled only at index 0.

**Key discovered gap (must fix for FR-004)**: `SnacksComponent.init()` currently returns early when
`AppStateService.snacks().length > 0` **without hydrating its `items()` signal**. Returning to
`/snacks` today would render the "Warming up the knowledge oven…" creation state forever. The plan
hydrates `items()` from the already-generated `appState.snacks()` on the return path and skips
creation/polling — satisfying FR-004 ("no empty/creation/loading state for existing snacks") with no
backend call.

**Selector-safety decision (FR-006/FR-023)**: the existing global `.ghost-btn` class is used for both
the new "Back to snacks" action and the new "Previous" control. Neither carries `primary-btn` or
`link-btn`, so `.results-footer .primary-btn` (restart), `.snack-card .primary-btn` (Next), and
`button.link-btn` (back / Retry) each remain uniquely selectable in Playwright strict mode.

**No backend change** (FR-022/SC-010): no endpoint, DTO, batch/quiz/snack generation, provider,
prompt, or schema change. No new npm dependency.

## Technical Context

**Language/Version**: TypeScript 5.9.x, Angular 21.2.x (standalone components, signals, `OnPush`).
Backend is **untouched** (Java 21 / Spring Boot 3.4.x).

**Primary Dependencies**: Angular Router, Angular signals (`signal`/`computed`), RxJS. **No new
dependency.** Reuses the global design primitives in `frontend/src/styles.scss`
(`.primary-btn`, `.ghost-btn`, `.link-btn`, global `:focus-visible`).

**Storage**: None. The active index is session-scoped in-memory `AppStateService` state (spec
Assumptions: "no server persistence required"). MySQL is unaffected.

**Testing**: Playwright E2E (`frontend/e2e/`) — the project has no Karma/Jest runner (see
`specs/009/plan.md` Testing). Two new specs plus the existing regression suite. No backend tests
(backend unchanged). Type-check via `npm run build`.

**Target Platform**: Modern browsers (Chrome/Firefox/Safari last 2 versions), desktop. Responsive
layout for the left index is explicitly **out of scope** this pass (spec Non-Goals).

**Project Type**: Web application (monorepo: `backend/` + `frontend/`). Only `frontend/` changes.

**Performance Goals**: No change to generation latency or polling. Index rendering is `@for` over the
current item array (≤10 items per batch cap), no additional HTTP. Navigation is synchronous signal
updates.

**Constraints**:
- Frontend-only; backend API, DTOs, and schema frozen (FR-022).
- Must not regress `progressive-delivery.spec.ts`, `back-navigation.spec.ts`,
  `loading-states.spec.ts`, `restart-clears-state.spec.ts` (FR-021/SC-009).
- Must not make existing selectors ambiguous: `.results-footer .primary-btn`,
  `.snack-card .primary-btn`, `button.link-btn` (FR-006/FR-023).
- Next must still advance only to a READY snack (FR-016).
- "Start a new theme" keeps `restart()` → `reset()` + `/` (FR-005).
- No `[NEEDS CLARIFICATION]` markers remain (spec Clarifications).

**Scale/Scope**: 3 existing frontend source files modified + 1 service touched
(`app-state.service.ts`), 2 new Playwright specs. No new components, routes, services, or
dependencies.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Evidence |
|---|-----------|--------|----------|
| I | **Spec-Driven Development** | PASS | spec.md defines 4 user stories, 23 FRs, 10 SCs. Every FR/SC maps to a concrete file/method/test (see Mapping). |
| II | **Clean Architecture (Layered)** | PASS | This is a UI-only feature. The constitution's frontend clause — "Angular standalone component patterns with signals-first approach" — governs. Changes live in the presentation layer (standalone `ResultsComponent`/`SnacksComponent`) and the shared signals store (`AppStateService`); no backend layer is touched. See "Frontend architecture" below for the justification of documenting Angular structure instead of the backend domain/application/infrastructure/interfaces tree. |
| III | **LLM Abstraction First** | PASS | No LLM/provider/prompt code touched. No generation behavior change. |
| IV | **Quality Gate for Generated Content** | PASS | No quiz/snack generation or validation touched. |
| V | **MVP-First, Iterate** | PASS | Smallest change set: one button, one index, two nav controls, one shared signal already present. No new dependency, component, route, or state store. Reuses `currentSnackIndex`, `selectedSubtopic`, `snacks` that already exist. |

**All gates PASS. No violations to justify.**

## Complexity Tracking

> No constitution violations. No complexity justifications needed.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | N/A | N/A |

## Frontend Architecture (in lieu of the backend layer tree)

The backend domain/application/infrastructure/interfaces tree is **not applicable**: no backend
module is created or modified (FR-022). The constitution's layered rule for the frontend is
"standalone components + signals-first". This feature maps cleanly onto that:

- **Presentation (view)**: `ResultsComponent` and `SnacksComponent` — standalone, `OnPush`, template
  drives all user-visible behavior. Components own only view concerns (which card is displayed,
  enabled/disabled states, ARIA).
- **Shared UI state (store)**: `AppStateService` (`@Injectable({ providedIn: 'root' })`) — the single
  source of truth for cross-page navigation state (`snacks`, `selectedSubtopic`,
  `currentSnackIndex`). This is the signals-first equivalent of the application state boundary; the
  active index must live here (FR-013/FR-014), not in a component.
- **Services (data access)**: `BatchService`/`QuizService` unchanged; the return path deliberately
  issues **no** service call (FR-002).
- **No routing change**: `/snacks` and `/results` routes already exist (`app.routes.ts`); navigation
  uses the existing `Router`.

The only architectural rule this feature must respect is **single source of truth**: the active index
is stored once in `AppStateService.currentSnackIndex` and read through a clamped computed; no
component-local duplicate (FR-013/FR-019).

## Project Structure

### Documentation (this feature)

```text
specs/010-snacks-navigation/
├── spec.md                 # Feature specification (exists)
├── plan.md                 # This file
├── research.md             # Phase 0 — decisions + rationale
├── data-model.md           # Phase 1 — frontend navigation state model
├── quickstart.md           # Phase 1 — local run + manual verification + E2E commands
├── contracts/              # Phase 1 — UI contract + backend-unchanged statement
│   ├── ui-navigation.md    # Component methods, selectors, ARIA, E2E-testable surface
│   └── backend-unchanged.md# Confirms no HTTP/DTO/schema change (FR-022/SC-010)
├── checklists/
│   └── requirements.md     # Requirements-quality checklist (exists)
└── tasks.md                # Phase 2 — NOT created by planner
```

### Source Code (repository root)

Only the marked frontend files change. The backend tree is intentionally omitted.

```text
endia/
├── backend/…                                       # UNCHANGED (no endpoint/DTO/schema change)
└── frontend/
    ├── e2e/
    │   ├── results-back-to-snacks.spec.ts          # NEW — US1 (both completion states, no batch, quizzed subtopic active)
    │   ├── snacks-navigation.spec.ts               # NEW — US2/US3/US4 (index, highlight, disabled, Next/Prev, persistence, a11y)
    │   ├── helpers.ts                              # OPTIONAL: add a `goToResults`/return helper only if it removes duplication
    │   ├── restart-clears-state.spec.ts            # UNCHANGED (must pass: `.results-footer .primary-btn`)
    │   ├── progressive-delivery.spec.ts            # UNCHANGED (must pass: `.snack-card .primary-btn`, one card)
    │   ├── back-navigation.spec.ts                 # UNCHANGED (must pass: `button.link-btn`)
    │   └── loading-states.spec.ts                  # UNCHANGED (must pass: `.loading` copy)
    └── src/app/
        ├── services/
        │   └── app-state.service.ts                # MOD: currentSnackIndex is the store; add index helpers (see data-model)
        └── pages/
            ├── results/
            │   ├── results.component.ts            # MOD: add backToSnacks() (no reset)
            │   ├── results.component.html          # MOD: add "Back to snacks" `.ghost-btn` in `.results-footer`
            │   └── results.component.scss          # MOD: `.results-footer` flex row + gap
            └── snacks/
                ├── snacks.component.ts             # MOD: shared index, prev()/next()/selectIndex(), hydrate on return
                ├── snacks.component.html           # MOD: left index + Previous/Next controls
                └── snacks.component.scss           # MOD: two-column layout, index + nav styles, focus-visible
```

**Structure Decision**: Monorepo as-is. Because the change is presentation + shared UI state only,
all edits stay inside the existing `pages/` and `services/` folders; no new component/service/route
is introduced. The `AppStateService` is the correct home for `currentSnackIndex` (it already declares
it, and it is already cleared by `setBatch()`/`reset()`), which is exactly what makes the index
survive re-entry. `ResultsComponent` owns the one-time "which snack was quizzed" decision because it
has `selectedSubtopic` in scope at the moment the user chooses to return; `SnacksComponent` only reads
and clamps the stored index, so a later manual navigation is not overridden on the next mount.

## Architecture Decisions

### 1. Return path sets the shared index, then navigates; never resets (FR-001..FR-006, FR-018)

`ResultsComponent` gains:

```ts
/** Return to the generated snacks, preserving state. Never resets (FR-002/FR-005). */
backToSnacks(): void {
  const subtopicId =
    this.appState.selectedSubtopic()?.id ?? this.appState.quizSubTopicId();
  const snacks = this.appState.snacks();
  if (subtopicId) {
    const idx = snacks.findIndex((s) => s.subtopicId === subtopicId);
    if (idx >= 0) {
      this.appState.currentSnackIndex.set(idx); // FR-003
    }
    // else: keep the last/current index (FR-018) — no arbitrary jump.
  }
  this.router.navigate(['/snacks']); // no batch: SnacksComponent short-circuits + hydrates
}
```

Template (inside the existing `.results-footer`, which is rendered by both completion states):

```html
<div class="results-footer">
  <button class="ghost-btn" data-testid="back-to-snacks" (click)="backToSnacks()">Back to snacks</button>
  <button class="primary-btn" (click)="restart()">Start a new theme</button>
</div>
```

**Rationale**:
- `restart()` is left completely untouched; the new method never calls `appState.reset()`, so snacks,
  subtopics, and `selectedSubtopic` survive (FR-002/FR-004/FR-005).
- Matching by `subtopicId` (not array position) is the spec's contract (FR-003) and is robust to the
  `snacks()` array being READY-only.
- Using the existing global `.ghost-btn` guarantees the new button has **no** `primary-btn` class, so
  `.results-footer .primary-btn` still resolves to exactly one element (FR-006/FR-023).
- Both completion states share the one `.results-footer`, so a single control satisfies SC-001.

**Rejected**:
- **Reusing `primary-btn` with a modifier class** (e.g. `class="primary-btn ghost-btn"`): rejected —
  any element carrying `primary-btn` matches the existing strict selector and breaks
  `restart-clears-state.spec.ts`. `ghost-btn` alone is visually appropriate and selector-safe.
- **Resetting and re-generating snacks on return**: rejected — violates FR-002/SC-002 and wastes
  generation.
- **Encoding the return decision in `SnacksComponent` on every mount**: rejected — it would override
  a user's manual navigation on subsequent re-entries; the "quizzed subtopic" intent is only valid at
  the moment of returning from results.

### 2. Hydrate `items()` from `appState.snacks()` on the return path (FR-004, gap fix)

`SnacksComponent.init()` becomes:

```ts
private async init(): Promise<void> {
  const existing = this.appState.snacks();
  if (existing.length > 0) {
    // Return path: reuse already-generated snacks. No POST /api/batches, no polling.
    this.items.set(existing.map(toReadyBatchItem)); // preserves subtopicId/subtopicName/snack
    this.creating.set(false);
    this.polling.set(false);
    this.clampStoredIndex();                        // edge case: stale/out-of-range index
    return;
  }
  // …existing creation path unchanged…
}

function toReadyBatchItem(s: Snack): BatchItem {
  return {
    subtopicId: s.subtopicId,
    subtopicName: s.subtopicName,
    snackStatus: 'READY',
    snack: s,
    snackCause: null,
    quizStatus: 'READY',
    quizCause: null,
  };
}
```

**Rationale**:
- The current early-return leaves `items()` empty and `creating` true, so the page would show the
  creation spinner forever on return. Hydrating is the minimal fix and directly satisfies FR-004.
- `appState.snacks()` is the spec-defined authoritative "current set" (Assumptions), so hydrating
  from it makes the index list exactly the generated READY snacks — consistent with the index source
  during live delivery (`items()`), which additionally contains not-yet-READY entries for FR-010.
- `clampStoredIndex()` writes back an in-range value for a stale index so `currentSnackIndex` never
  holds an out-of-range position (spec edge case).

**Rejected**:
- **Persisting the full `BatchItem[]` in `AppStateService`**: rejected — more state to keep in sync;
  `snacks` is already declared authoritative by the spec and carries everything the card and index
  need.
- **Calling `setBatch()`/`createBatch()` again on return**: rejected — violates FR-002.

### 3. Move the active index into `AppStateService.currentSnackIndex`, read via a clamped computed (FR-011..FR-019)

`SnacksComponent`:

```ts
/** Single source of truth (FR-013); clamped so a stale value never renders a blank card. */
readonly currentIndex = computed(() => {
  const len = this.items().length;
  if (len === 0) return 0;
  return Math.min(Math.max(this.appState.currentSnackIndex(), 0), len - 1);
});

readonly currentSnack = computed<BatchItem | null>(() => this.items()[this.currentIndex()] ?? null);
readonly nextSnack = computed<BatchItem | null>(() => this.items()[this.currentIndex() + 1] ?? null);
readonly hasPrev = computed(() => this.currentIndex() > 0);
readonly hasNext = computed(() => this.currentIndex() < this.items().length - 1);
readonly canNext = computed(() => this.hasNext() && this.nextSnack()?.snackStatus === 'READY');

private goTo(index: number): void {
  const len = this.items().length;
  if (index < 0 || index >= len) return;      // boundary safe (FR-012)
  this.appState.currentSnackIndex.set(index);
}

prev(): void { if (this.hasPrev()) this.goTo(this.currentIndex() - 1); }   // disabled only at 0 (FR-015)
next(): void { if (this.canNext()) this.goTo(this.currentIndex() + 1); }   // READY-only (FR-016)
selectIndex(i: number): void { if (this.items()[i]?.snackStatus === 'READY') this.goTo(i); } // FR-009/FR-010
```

**Rationale**:
- `currentSnackIndex` already exists and is already reset by `setBatch()`/`reset()`, so it is the
  natural store; the component no longer declares a local `currentIndex` signal (FR-013).
- The clamped computed guarantees the displayed card always corresponds to an in-range item even if
  the stored index is stale (spec edge case), and it keeps `currentSnack`, `nextSnack`, `hasPrev`,
  `hasNext`, `canNext` mutually consistent from a single value (FR-019).
- `prev()` intentionally does **not** require the previous item to be READY (FR-015: disabled only at
  index 0); moving back to a previously-READY snack is the intended behavior.
- `next()` retains the exact progressive-delivery rule (only to a READY snack), preserving
  `progressive-delivery.spec.ts` (FR-016/FR-021).
- `selectIndex` guards on READY even though the button is disabled, so a programmatic/rapid click can
  never desync (FR-009/FR-010/FR-019).

**Rejected**:
- **Keeping a component-local index and mirroring it to the service**: rejected — two sources of
  truth cause desync (violates FR-013/FR-019).
- **Making Next advance regardless of READY**: rejected — breaks FR-016 and progressive delivery.
- **Disabling Previous whenever the previous item is not READY**: rejected — FR-015 says Previous is
  disabled only at index 0.

### 4. Left-side index sourced from `items()`, with distinct classes and ARIA (FR-007..FR-010, FR-020)

Template shape (inside `@if (!creating() && items().length)`):

```html
<div class="snacks-layout">
  <nav class="snack-index" aria-label="Snack list">
    <ol class="snack-index__list">
      @for (item of items(); track item.subtopicId; let i = $index) {
        <li>
          <button
            type="button"
            class="snack-index__item"
            [class.snack-index__item--active]="i === currentIndex()"
            [attr.aria-current]="i === currentIndex() ? 'true' : null"
            [disabled]="item.snackStatus !== 'READY'"
            (click)="selectIndex(i)"
          >{{ item.subtopicName }}</button>
        </li>
      }
    </ol>
  </nav>
  <div class="snack-content">
    <!-- existing READY / FAILED / loading card blocks, moved unchanged -->
    <div class="snack-card__footer">
      <button type="button" class="ghost-btn nav-btn--prev"
              [disabled]="!hasPrev()" (click)="prev()">← Previous</button>
      <button type="button" class="primary-btn nav-btn--next"
              [disabled]="!canNext()" (click)="next()">Next →</button>
      @if (nextSnack(); as nxt) {
        @if (nxt.snackStatus !== 'READY') {
          <span class="cooking-label">
            <span class="spinner spinner--sm" aria-hidden="true"></span>
            {{ nxt.snackStatus === 'FAILED' ? 'Next one failed' : 'Next snack is cooking…' }}
          </span>
        }
      } @else {
        <span class="cooking-label">All snacks served</span>
      }
    </div>
  </div>
</div>
```

**Rationale**:
- The index is a `<nav>` of real `<button>`s: keyboard reachable, native disabled, accessible name
  from `subtopicName` (FR-020/SC-008). `aria-current="true"` on exactly the active entry (FR-008).
- Non-READY entries are disabled (FR-010); disabled native buttons are not clickable and convey
  state to assistive tech.
- Only **Next** keeps `primary-btn`; **Previous** and **Back to snacks** use `ghost-btn`. Thus
  `.snack-card .primary-btn` still matches exactly one element (FR-023). Index entries use
  `.snack-index__item`, never `link-btn` (preserves `button.link-btn` for back/Retry).
- The card remains a single `.snack-card` element, so `toHaveCount(1)` assertions hold.
- The cooking label is preserved (text unchanged) so `snack-wait-messages`-style expectations and the
  progressive "cooking" affordance continue to work.

**Rejected**:
- **Rendering the index as `<a>` links**: rejected — there is no route per snack; buttons are the
  correct control and avoid inventing URLs/selectors.
- **Reusing `link-btn` for index entries**: rejected — would add matches for `button.link-btn` and
  break `back-navigation.spec.ts`.
- **Hiding the index while progressive delivery is in progress**: rejected — FR-007 requires an entry
  for every snack, and FR-010 requires non-READY entries to be visible but disabled.

### 5. Accessibility and focus (FR-020/SC-008)

- Every new control is a real `<button type="button">` with a text accessible name.
- The active index entry exposes `aria-current="true"`; disabled entries use the native `disabled`
  attribute.
- The global `:focus-visible` outline (styles.scss) applies to all buttons; explicit `:focus-visible`
  rules are added for `.snack-index__item` and the nav buttons for a consistent indicator.
- The index `<nav>` has `aria-label="Snack list"`.

### 6. No backend change (FR-022/SC-010)

The return path deliberately makes no HTTP call; `SnacksComponent` hydrates from in-memory state.
`POST /api/batches` short-circuit is preserved (now paired with hydration). No DTO, endpoint, batch
status, provider, prompt, or schema change. See `contracts/backend-unchanged.md`.

## Phase-by-Phase Implementation Plan

### Phase 0 — Shared navigation state (foundation)

| Step | File | Why |
|------|------|-----|
| 0.1 | `frontend/src/app/services/app-state.service.ts` | Confirm `currentSnackIndex` is the single store; add a `setSnackIndex(i)` helper and (optionally) `snackIndexForSubtopic(id)` so components do not duplicate matching logic (FR-013). Keep `setBatch()`/`reset()` resetting to 0. |
| 0.2 | `frontend/src/app/pages/snacks/snacks.component.ts` | Remove the local `currentIndex` signal; add the clamped `currentIndex` computed reading `appState.currentSnackIndex` (FR-013/FR-019). |

### Phase 1 — Return to snacks from results (US1, P1)

| Step | File | Why |
|------|------|-----|
| 1.1 | `results.component.ts` | Add `backToSnacks()`: match `selectedSubtopic`/`quizSubTopicId` against `snacks[].subtopicId`, set `currentSnackIndex` if found (else keep), navigate to `/snacks`; no `reset()` (FR-001..FR-004, FR-018). |
| 1.2 | `results.component.html` | Add the "Back to snacks" `.ghost-btn` (with `data-testid`) inside `.results-footer` next to the restart `.primary-btn` (FR-001, FR-006). |
| 1.3 | `results.component.scss` | Make `.results-footer` a centered flex row with gap so both actions sit side by side in both completion states (FR-001/SC-001). |
| 1.4 | `snacks.component.ts` | Hydrate `items()` from `appState.snacks()` on the return path; set `creating=false`, `polling=false`; clamp stored index; skip `createBatch` (FR-002/FR-004). |

### Phase 2 — Left index + Next/Previous + persistence (US2/US3, P1)

| Step | File | Why |
|------|------|-----|
| 2.1 | `snacks.component.ts` | Add `hasPrev`/`hasNext`/`canNext` computeds and `prev()`/`next()`/`selectIndex()` writing to `appState.currentSnackIndex`; keep `next()` READY-only (FR-011..FR-017). |
| 2.2 | `snacks.component.ts` | Ensure `startQuiz` derives the subtopic from the active snack (`currentSnack()?.snack?.subtopicId`) so it can never use a stale/first snack (FR-017). |
| 2.3 | `snacks.component.html` | Add the left `<nav class="snack-index">` listing every `items()` entry by `subtopicName`, with `aria-current`, active class, and `[disabled]` for non-READY (FR-007..FR-010). |
| 2.4 | `snacks.component.html` | Add Previous (`.ghost-btn`) and Next (`.primary-btn`) controls with boundary disabled states; preserve the cooking-label/all-served affordances (FR-011/FR-012/FR-016). |
| 2.5 | `snacks.component.scss` | Add the two-column `.snacks-layout`, `.snack-index` sticky panel, active/disabled entry styles, nav spacing, and `:focus-visible` rules (FR-020). |
| 2.6 | `snacks.component.ts` | Clamp/write-back the stored index after hydration and whenever the item set changes, so the index never leaves range (FR-012, edge cases). |

### Phase 3 — Accessibility hardening + tests (US4, P2)

| Step | File | Why |
|------|------|-----|
| 3.1 | `frontend/e2e/results-back-to-snacks.spec.ts` (NEW) | US1: both completion states render "Back to snacks"; clicking navigates to `/snacks`; assert `POST /api/batches` count is unchanged (0 new); quizzed subtopic's snack active; existing snacks shown (no creation/loading state); restart still resets to `/`. |
| 3.2 | `frontend/e2e/snacks-navigation.spec.ts` (NEW) | US2/US3/US4: index lists all snacks by name; exactly one `aria-current="true"` + active class; click jumps; non-READY entries disabled; Next/Previous move exactly one and are disabled at boundaries; index persists across navigate-away-and-back and across the quiz→results→return round trip; Start Quiz uses the active `subtopicId`; keyboard focus-visible. |
| 3.3 | Existing specs | Run `progressive-delivery`, `back-navigation`, `loading-states`, `restart-clears-state`, plus `polling-termination`, `manual-retry`, `snack-wait-messages`, `bounded-snack-settle`, `happy-path` unchanged (FR-021/SC-009). |
| 3.4 | `frontend` build | `npm run build` type-checks the template/method changes. |
| 3.5 | Manual verification | Follow `quickstart.md`: full loop and both completion states. |

## Milestones

- **M0** (P1, state): shared index in `AppStateService` + clamped computed (Phase 0). Foundation for US1/US3.
- **M1** (P1, US1): "Back to snacks" button + `backToSnacks()` + return-path hydration (Phase 1). Delivers FR-001..FR-006, FR-018, SC-001..SC-003.
- **M2** (P1, US2/US3): left index + Previous/Next + persistence (Phase 2). Delivers FR-007..FR-017, SC-004..SC-006.
- **M3** (P2, US4 + tests): accessibility polish, new E2E specs, non-regression (Phase 3). Delivers FR-019..FR-023, SC-007..SC-010.

## Risks

- **Strict-mode selector collision.** Adding a second `primary-btn` to `.results-footer` or a second
  `primary-btn`/`link-btn` inside `.snack-card` would break existing specs. Mitigated by using
  `.ghost-btn` for "Back to snacks" and "Previous" and `.snack-index__item` for index entries; verified
  by running the full suite (FR-006/FR-023).
- **Hydration order vs. `currentSnackIndex`.** `ResultsComponent` sets the index before navigation;
  `SnacksComponent` hydrates `items()` and clamps on init. If clamping ran before hydration it could
  reset to 0 — the plan clamps **after** `items.set(...)` (Phase 1.4/2.6). **Flagged** for task order.
- **Index vs. displayed set mismatch.** During live generation the index is sourced from `items()`
  (all statuses); on return it is sourced from `snacks()` (READY only). Both are consistent with the
  spec's "current set" definition, but the returned index is shorter. The `subtopicId`-based match
  (not positional) makes the return correct. Documented in `research.md` R5.
- **Progressive reachability of a FAILED middle snack.** Next (READY-only, FR-016) cannot linearly
  pass a FAILED item; such snacks remain reachable only via the index once READY. This is the
  intended tension between FR-012 and FR-016; documented in `research.md` R4. **Flagged** for the
  orchestrator as an interpretation, not a defect.
- **`items()` order on hydration.** Hydration preserves `snacks()` order (which mirrors generation
  order); the index and Next/Previous use the same order, so highlight and content cannot desync.
- **No unit test runner.** Coverage relies on Playwright; pure signal logic (clamping) is exercised
  through the UI. If a runner is later added, a unit test for the clamp is the natural addition.

## Mapping: Spec Requirements → Plan Items

| Spec ID | Requirement | Plan Item |
|---------|-------------|-----------|
| FR-001 | "Back to snacks" `<button>` in `.results-footer`, both completion states | 1.2 (shared footer) |
| FR-002 | Navigate to `/snacks`, no new batch/regeneration | 1.1 + 1.4 (no call; hydrate) |
| FR-003 | Quizzed subtopic's snack active via `subtopicId` match | 1.1 |
| FR-004 | Existing snacks available; no empty/creation/loading state | 1.4 |
| FR-005 | "Start a new theme" still resets + home | 1.1 (restart untouched) + 3.3 |
| FR-006 | `.results-footer .primary-btn` remains unambiguous | 1.2 (`.ghost-btn`) + 3.3 |
| FR-007 | Left index with an entry for every snack by `subtopicName` | 2.3 |
| FR-008 | Active entry `aria-current="true"` + active class | 2.3 |
| FR-009 | Click a READY entry → active index + content update | 2.1 + 2.3 |
| FR-010 | Non-READY/FAILED entries disabled | 2.3 |
| FR-011 | Next/Previous move exactly one | 2.1 + 2.4 |
| FR-012 | Boundary-safe; no out-of-range/blank card | 0.2 + 2.1 + 2.6 |
| FR-013 | Active index stored in `AppStateService.currentSnackIndex` | 0.1 + 0.2 |
| FR-014 | Index preserved across navigation/return | 0.1 + 1.1 + 1.4 |
| FR-015 | Previous disabled only at index 0 | 2.1 + 2.4 |
| FR-016 | Next only advances to READY | 2.1 (preserved) |
| FR-017 | Start Quiz uses active snack's `subtopicId` | 2.2 |
| FR-018 | No READY match on return → keep current index | 1.1 |
| FR-019 | Content/highlight/enabled state stay consistent | 0.2 + 2.1 (single computed source) |
| FR-020 | Real elements, accessible names, keyboard, `:focus-visible` | 2.3/2.4/2.5 + 3.2 |
| FR-021 | No regression of progressive/back-nav/loading suites | 2.1 + 3.3 |
| FR-022 | Backend unchanged | 1.4 + `contracts/backend-unchanged.md` |
| FR-023 | No extra matches for existing selectors | 1.2/2.4/2.5 + 3.3 |
| SC-001 | 100% of results screens show the control | 1.2 + 3.1 |
| SC-002 | 0 new `POST /api/batches` on return | 1.1/1.4 + 3.1 |
| SC-003 | Matching snack active on return | 1.1 + 3.1 |
| SC-004 | Index lists all; exactly one highlighted | 2.3 + 3.2 |
| SC-005 | Exactly-one movement; 0 out-of-range/blank | 0.2/2.1/2.6 + 3.2 |
| SC-006 | Index survives round trip | 0.1/1.4 + 3.2 |
| SC-007 | 0 desyncs across interaction sequences | 0.2/2.1 + 3.2 |
| SC-008 | 100% keyboard/accessible-name/focus coverage | 2.3/2.4/2.5 + 3.2 |
| SC-009 | Named E2E suites pass unchanged | 3.3 |
| SC-010 | 0 backend contract/persistence changes | 1.4 + `contracts/backend-unchanged.md` |

## Testing Strategy

### New Playwright specs (primary coverage)

- **`frontend/e2e/results-back-to-snacks.spec.ts`** (US1):
  - Drive the full flow with the existing `mockPage` fixture (`setupAllMocks`) to `/results`.
  - Assert both completion states render `.results-footer [data-testid="back-to-snacks"]`
    ("Back to snacks") and the restart `.primary-btn` (SC-001). Cover "Perfect score!" by overriding
    `setupQuizSummaryMock({ correctAnswers: 4 })`.
  - Record `POST /api/batches` calls (reuse `setupConfigurableBatchMock`/`setupAllMocks`) and assert
    the count does not increase after clicking "Back to snacks" (SC-002).
  - After return, assert the quizzed subtopic's `.snack-card__chip` is the active card and the
    matching index entry has `aria-current="true"` (SC-003).
  - Assert no `.loading`/empty state for existing snacks (FR-004).
  - Assert "Start a new theme" still navigates to `/` and clears state (FR-005).
- **`frontend/e2e/snacks-navigation.spec.ts`** (US2/US3/US4):
  - With a 3-snack COMPLETE batch, assert `.snack-index__item` count is 3 and each label equals the
    `subtopicName` (SC-004).
  - Assert exactly one `.snack-index__item--active[aria-current="true"]` (FR-008/SC-004).
  - Click a READY entry → the `.snack-card__chip` and highlight update together (FR-009/SC-007).
  - With a mock where one item is GENERATING and one FAILED, assert those entries are `disabled`
    (FR-010).
  - Next/Previous move by exactly one; Previous disabled at index 0; Next disabled at the last index;
    repeated Next reaches every READY snack with no blank card (FR-011/FR-012/SC-005).
  - Navigate to `/quiz` and back (and through the results round trip) → same snack active
    (FR-014/SC-006).
  - Click "Start Quiz" on a non-first active snack and assert the quiz request/summary uses that
    snack's `subtopicId` (FR-017).
  - Keyboard: tab to an index entry and a nav button, assert `:focus-visible` outline and Enter/Space
    activation (FR-020/SC-008).

### Existing regression suite (must pass unchanged)

`progressive-delivery.spec.ts`, `back-navigation.spec.ts`, `loading-states.spec.ts`,
`restart-clears-state.spec.ts`, `polling-termination.spec.ts`, `retry-bound.spec.ts`,
`manual-retry.spec.ts`, `snack-wait-messages.spec.ts`, `bounded-snack-settle.spec.ts`,
`happy-path.spec.ts`, `perfect-score.spec.ts`, `batch-empty-state.spec.ts`.

Run:

```bash
cd frontend
npx playwright test
```

### Type-check / build

```bash
cd frontend
npm run build
```

### Backend

No backend tests run or changed; the backend is frozen (FR-022/SC-010). If a quick smoke is desired,
`cd backend && mvn -q -DskipTests package` should be unaffected.

### Manual verification

See `quickstart.md` for the full-loop script and the return/navigation checks.

### What is explicitly not tested / not changed

- No DB/migration tests (no schema change). No new endpoint/DTO tests. No responsive/mobile layout
  tests (deferred). No theme-history tests (out of scope). No unit runner introduced.
