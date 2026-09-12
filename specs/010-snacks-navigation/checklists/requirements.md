# Requirements Quality Checklist: Return to Snacks from Results + Snacks Navigation

**Purpose**: Validate that the feature's navigation and return-flow requirements are complete, clear, consistent, and measurable before/while implementing `010-snacks-navigation`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md)

**Note**: This checklist tests the *requirements writing* (completeness, clarity, consistency, measurability), not the implementation. Every item must be answerable "yes" at implementation completion; mark `[x]` with the evidence (section or requirement ID) that satisfies it.

## Return-to-Snacks Completeness

- [x] CHK001 Is the new results action's exact label ("Back to snacks") and placement (`.results-footer`, next to "Start a new theme") specified? [Completeness, Spec §FR-001]
- [x] CHK002 Is it required to appear in BOTH completion states ("Quiz complete" and "Perfect score!")? [Completeness, Spec §FR-001, §US1 AS1]
- [x] CHK003 Is "no new batch / no regeneration" on return stated explicitly (0 `POST /api/batches`)? [Completeness, Spec §FR-002]
- [x] CHK004 Is preservation of the quizzed subtopic's context required via `selectedSubtopic` ↔ `Snack.subtopicId` matching? [Completeness, Spec §FR-003]
- [x] CHK005 Is preservation of already-generated snacks (no empty/creation/loading state) required? [Completeness, Spec §FR-004]
- [x] CHK006 Is the existing "Start a new theme" behavior required to keep working (restart test preserved)? [Completeness, Spec §FR-005]

## Snacks Index Completeness

- [x] CHK007 Is a left-side index listing EVERY snack in the current set required? [Completeness, Spec §FR-007]
- [x] CHK008 Is each index entry required to be labelled by `Snack.subtopicName`? [Completeness, Spec §FR-007]
- [x] CHK009 Is active-entry highlighting specified with `aria-current="true"` plus an active CSS class? [Completeness, Spec §FR-008]
- [x] CHK010 Is click-to-jump (active index + displayed content update) required? [Completeness, Spec §FR-009]
- [x] CHK011 Are non-READY (generating/failed) index entries required to be disabled? [Completeness, Spec §FR-010]

## Next/Previous & Persistence Completeness

- [x] CHK012 Are Next and Previous controls required to move the index by exactly one? [Completeness, Spec §FR-011]
- [x] CHK013 Is boundary behavior (first/last disabled or safe no-op, no out-of-range, no blank card) required? [Completeness, Spec §FR-012]
- [x] CHK014 Is `AppStateService.currentSnackIndex` named as the single store for the active index, replacing the local index? [Completeness, Spec §FR-013]
- [x] CHK015 Is index persistence across navigate-away-and-back (including via "Back to snacks") required? [Completeness, Spec §FR-014]
- [x] CHK016 Is Previous explicitly able to return to a previously-READY snack, disabled only at index 0? [Completeness, Spec §FR-015]
- [x] CHK017 Is Next required to still advance only to a READY snack during progressive delivery? [Completeness, Spec §FR-016]
- [x] CHK018 Is "Start Quiz uses the active snack's `subtopicId`" required? [Completeness, Spec §FR-017]
- [x] CHK019 Is the no-READY-snack-on-return fallback (keep current index) required? [Completeness, Spec §FR-018]

## Consistency & Accessibility Completeness

- [x] CHK020 Is cross-control consistency (content/highlight/enabled state) required after any interaction sequence? [Completeness, Spec §FR-019]
- [x] CHK021 Are real interactive elements, accessible labels, keyboard reachability, and `:focus-visible` required for all controls? [Completeness, Spec §FR-020]
- [x] CHK022 Is non-regression of the named E2E suites (progressive delivery, back navigation, loading states, restart) required? [Completeness, Spec §FR-021, §FR-005]
- [x] CHK023 Is "backend unchanged" (no endpoints/response/schema changes) required? [Completeness, Spec §FR-022]

## Requirement Clarity

- [x] CHK024 Is "current set" defined as the snacks held in `AppStateService.snacks`? [Clarity, Spec §Assumptions, §Key Entities]
- [x] CHK025 Is "active snack" defined (the snack at the stored index, identified by `subtopicId`)? [Clarity, Spec §Key Entities, §FR-003]
- [x] CHK026 Is "safe no-op" clarified as never producing an out-of-range index or blank card? [Clarity, Spec §FR-012, §Edge Cases]
- [x] CHK027 Is "preserve context" concretely defined (matching READY snack active; else keep current index)? [Clarity, Spec §FR-003, §FR-018]
- [x] CHK028 Is "disabled" for index entries distinguished from the Next/Previous boundary disable? [Clarity, Spec §FR-010, §FR-012]
- [x] CHK029 Is the stale/out-of-range stored index behavior specified (clamp to a valid index)? [Clarity, Spec §Edge Cases]

## Requirement Consistency

- [x] CHK030 Do FR-014 (persist index) and FR-013 (`currentSnackIndex` is the store) agree without a second source of truth? [Consistency, Spec §FR-013, §FR-014]
- [x] CHK031 Do FR-016 (Next only to READY) and FR-011/FR-012 (move by exactly one / boundary handling) agree during progressive delivery? [Consistency, Spec §FR-011, §FR-012, §FR-016]
- [x] CHK032 Do FR-010 (disabled non-READY entries) and FR-018 (no READY match → keep index) agree? [Consistency, Spec §FR-010, §FR-018]
- [x] CHK033 Does FR-006 (selector not ambiguous) avoid contradicting FR-001 (button inside `.results-footer`)? [Consistency, Spec §FR-001, §FR-006, §FR-023]
- [x] CHK034 Is the "backend unchanged" requirement (FR-022) consistent with the confirmed short-circuit behavior in §Context? [Consistency, Spec §FR-022, §Context]

## Acceptance Criteria Quality

- [x] CHK035 Is SC-001 expressed as 100% coverage across both completion states? [Measurability, Spec §SC-001]
- [x] CHK036 Is SC-002 expressed as a zero-request/zero-regeneration count on return? [Measurability, Spec §SC-002]
- [x] CHK037 Is SC-003 expressed as 100% correct active-snack match when a READY match exists? [Measurability, Spec §SC-003]
- [x] CHK038 Is SC-004 expressed as full index coverage plus exactly one highlighted entry? [Measurability, Spec §SC-004]
- [x] CHK039 Is SC-005 expressed as exactly-one-step movement with 0 out-of-range/blank outcomes? [Measurability, Spec §SC-005]
- [x] CHK040 Is SC-006 expressed as 100% index survival across the round trip? [Measurability, Spec §SC-006]
- [x] CHK041 Is SC-007 expressed as a zero-desync condition over tested interaction sequences? [Measurability, Spec §SC-007]
- [x] CHK042 Is SC-008 expressed as 100% keyboard/accessible-name/focus coverage? [Measurability, Spec §SC-008]
- [x] CHK043 Is SC-009 tied to named existing E2E suites passing unchanged? [Measurability, Spec §SC-009]
- [x] CHK044 Is SC-010 tied to a zero-change backend contract/persistence condition? [Measurability, Spec §SC-010]

## Edge Case & Scenario Coverage

- [x] CHK045 Is the single-snack set covered (both controls disabled)? [Coverage, Spec §Edge Cases]
- [x] CHK046 Are the all-generating and all-failed set cases covered? [Coverage, Spec §Edge Cases]
- [x] CHK047 Is the quizzed-subtopic-has-no-READY-snack case covered? [Coverage, Spec §Edge Cases, §FR-018]
- [x] CHK048 Is the stale/out-of-range index case covered? [Coverage, Spec §Edge Cases]
- [x] CHK049 Is the missing-`selectedSubtopic` case covered? [Coverage, Spec §Edge Cases]
- [x] CHK050 Are deep-link/no-snacks, rapid clicking, and disabled-entry-click cases covered? [Coverage, Spec §Edge Cases]
- [x] CHK051 Is the perfect-score results variant covered? [Coverage, Spec §Edge Cases, §US1 AS1]
- [x] CHK052 Is selector-collision regression (extra matches for existing test selectors) covered? [Coverage, Spec §Edge Cases, §FR-023]

## Scope, Assumptions & Dependencies

- [x] CHK053 Is the frontend-only / backend-unchanged scope recorded as a confirmed input? [Assumption, Spec §Context, §Assumptions]
- [x] CHK054 Is the existing batch-creation short-circuit recorded (so no backend change is needed)? [Assumption, Spec §Context, §Assumptions]
- [x] CHK055 Are the deferred items recorded (responsive index, Item 11 theme history)? [Assumption, Spec §Non-Goals, §Clarifications]
- [x] CHK056 Are the user-made decisions encoded as clarifications (label, disabled entries, fallback, Previous, persistence)? [Assumption, Spec §Clarifications]
- [x] CHK057 Is the session-scoped nature of the active index (no server persistence) recorded? [Assumption, Spec §Assumptions]

## Notes

- Check items off as completed: `[x]`.
- Add the satisfying section/requirement reference inline.
- Items are numbered sequentially for easy reference; all must be checked at implementation completion.
