# UX Requirements Quality Checklist: Read-Only History of Created Themes

**Purpose**: Validate that the user-facing requirements for the History page — header entry point, list, nested detail, loading/error/empty states, and accessibility — are complete, clear, and consistent before/while implementing `011-theme-history`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md)

**Note**: This checklist tests the *UX requirements writing* for quality, not the implementation. Each item must be answerable "yes" at implementation completion.

## Entry Point & Navigation

- [x] CHK001 Is the exact label ("History") and placement (`.app-shell__header`, next to the theme toggle) specified? [Completeness, Spec §FR-011, research.md R4]
- [x] CHK002 Is it required that clicking the link navigates to `/history` and renders `HistoryComponent`? [Completeness, Spec §US1 AS2, tasks.md T008]
- [x] CHK003 Is direct (deep-link) access to `/history` required to render without redirect, unlike `/quiz` and `/results`? [Clarity, Spec §FR-017/§Edge Cases, tasks.md T008]
- [x] CHK004 Is it specified that the header link is grouped with the toggle so the brand/header layout is preserved? [Consistency, research.md R4, tasks.md T024]

## History List & Nested Detail

- [x] CHK005 Is each theme entry required to show `prompt` and `createdAt`? [Completeness, Spec §FR-015]
- [x] CHK006 Is newest-first presentation required (matching the backend ordering)? [Completeness, Spec §US1 AS5, tasks.md T008]
- [x] CHK007 Is the `createdAt` display required to be formatted in the user's local locale/timezone from the ISO-8601 value? [Clarity, Spec §Assumptions, research.md R5]
- [x] CHK008 Is it required that a theme's subtopics are viewable by `name`? [Completeness, Spec §US2 AS1]
- [x] CHK009 Are snack fields (`concept`, `metaphor`, `whyItMatters`, `related`) required to render? [Completeness, Spec §FR-003/§US2 AS2]
- [x] CHK010 Are question fields (`type`, `prompt`, `explanation`) required to render? [Completeness, Spec §FR-004/§US2 AS3]
- [x] CHK011 Is a "nothing generated yet" indication required when a subtopic has no snacks or no questions, with no error? [Clarity, Spec §US2 AS5/§Edge Cases, tasks.md T022]
- [x] CHK012 Is the nested view explicitly read-only (no generation/edit/delete/re-quiz controls)? [Clarity, Spec §FR-016/§Non-Goals, research.md R7]

## State Handling

- [x] CHK013 Is a loading state required while the request is in flight? [Completeness, Spec §FR-012]
- [x] CHK014 Is the loading state required to clear once the request settles (success or error)? [Completeness, Spec §FR-012/§US3 AS4]
- [x] CHK015 Is the error message required to come from `ErrorService.getMessage(error)` with the list hidden? [Clarity, Spec §FR-013, research.md R6]
- [x] CHK016 Is the empty state distinguished from an error (HTTP 200 `[]` → empty message, zero entries)? [Clarity, Spec §FR-014/§Edge Cases]
- [x] CHK017 Are the states required to be mutually exclusive (no list alongside error/empty; no infinite loading)? [Consistency, data-model.md §5, research.md R6]

## Accessibility & Consistency

- [x] CHK018 Is the header link required to be a real `<a>` with an accessible name? [Coverage, Spec §FR-018, research.md R4]
- [x] CHK019 Are the theme-detail controls required to be keyboard reachable and activatable? [Coverage, Spec §FR-018, tasks.md T011]
- [x] CHK020 Is a visible `:focus-visible` indicator required for the link and the detail controls? [Coverage, Spec §FR-018, tasks.md T025]

## Scope & Non-Goals

- [x] CHK021 Is a mobile/hamburger navigation redesign explicitly deferred? [Clarity, Spec §Non-Goals]
- [x] CHK022 Are pagination, filtering, sorting options, and search explicitly out of scope? [Clarity, Spec §Non-Goals]
- [x] CHK023 Are export, sharing, and print views excluded? [Clarity, Spec §Non-Goals]
- [x] CHK024 Are live/streaming updates while the page is open excluded (read-only snapshot from load time)? [Clarity, Spec §Edge Cases/§Non-Goals]
- [x] CHK025 Is rich rendering of drag-drop payloads, options, or answer correctness excluded? [Clarity, Spec §Non-Goals, contracts/get-themes.md §Field semantics]

## Notes

- Check items off as completed: `[x]`.
- Reference the spec/plan/contract section or task ID that satisfies each item.
- All items must be checked at implementation completion (tasks.md T036).
