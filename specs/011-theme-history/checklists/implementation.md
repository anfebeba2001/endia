# Implementation Requirements Quality Checklist: Read-Only History of Created Themes

**Purpose**: Validate that the implementation requirements for the backend read path, the layered mapping, and the frontend wiring are complete, clear, ordered, and internally consistent before/while implementing `011-theme-history`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md) | [data-model.md](../data-model.md) | [contracts/get-themes.md](../contracts/get-themes.md)

**Note**: This checklist tests the *implementation requirements writing* (completeness, clarity, ordering, consistency), not the implementation. Every item must be answerable "yes" at implementation completion; mark `[x]` with the evidence (section, requirement ID, or task ID) that satisfies it.

## Change-Set Completeness

- [x] CHK001 Is the exact backend file set enumerated (`ThemeHistoryApiResponse.java` new; `ThemeService.java`, `JpaThemeRepository.java`, `JpaSnackRepository.java`, `JpaQuizQuestionRepository.java`, `ThemeController.java` modified)? [Completeness, plan.md §Project Structure, tasks.md §Organization]
- [x] CHK002 Is the exact frontend file set enumerated (`history.model.ts`, `history.component.{ts,html,scss}`, `theme-history.spec.ts` new; `theme.service.ts`, `app.routes.ts`, `app.html`, `app.scss`, `helpers.ts` modified)? [Completeness, plan.md §Project Structure, tasks.md §Organization]
- [x] CHK003 Is the new DTO required to be a record with nested records under `interfaces/rest/dto`, matching the existing convention? [Completeness, Spec §FR-006, data-model.md §3]
- [x] CHK004 Is the service required to return its own application-layer read model rather than an interface DTO? [Completeness, Spec §FR-022, research.md R2, data-model.md §2]
- [x] CHK005 Is the controller required to perform the API mapping and return `ResponseEntity<List<ThemeHistoryApiResponse>>` (a JSON array)? [Completeness, Spec §FR-001/§FR-022, research.md R2]

## Read Strategy & Ordering

- [x] CHK006 Is `findAllByOrderByCreatedAtDesc()` required to fetch **only** the `subtopics` bag via `@EntityGraph`? [Clarity, data-model.md §4, tasks.md T012]
- [x] CHK007 Is the two-query batch load (`findBySubtopicIdIn` for snacks and questions) specified, with the empty-ids skip? [Completeness, data-model.md §4, tasks.md T013, T014, T015]
- [x] CHK008 Is the `MultipleBagFetchException` hazard documented and the single-bag strategy stated as the mitigation? [Clarity, research.md R1, tasks.md preamble]
- [x] CHK009 Is the read model required to be assembled **inside** the `@Transactional(readOnly = true)` method so no lazy proxy escapes? [Clarity, Spec §FR-005, tasks.md T015, T033]
- [x] CHK010 Is the no-N+1 / bounded-query expectation stated (1 themes+subtopics + up to 2 batch queries)? [Measurability, plan.md §Performance, tasks.md T033]
- [x] CHK011 Is newest-first ordering pinned to `created_at` descending, with ties accepted? [Clarity, Spec §FR-001, contracts/get-themes.md §Ordering]

## Layering & Non-Regression

- [x] CHK012 Is it required that no domain entity, field, annotation, or enum changes? [Completeness, Spec §FR-022, data-model.md §1]
- [x] CHK013 Is it required that no schema, migration, or `ddl-auto` delta is introduced? [Completeness, Spec §FR-022, data-model.md §6]
- [x] CHK014 Are the existing endpoints named and required to keep their contracts (`POST /api/themes/suggest`, `POST /api/themes/{themeId}/subtopics`, `POST /api/snacks/generate`, `/api/batches`, quiz)? [Completeness, Spec §FR-007, tasks.md T030]
- [x] CHK015 Is the read-only guarantee stated (only finder calls, no save/update/delete)? [Completeness, Spec §FR-008, research.md R7, tasks.md T032]
- [x] CHK016 Is the "no new dependency" constraint stated for both projects? [Completeness, plan.md §Technical Context, tasks.md T002]

## Frontend Wiring & Ordering

- [x] CHK017 Is `ThemeService.listHistory()` required to call `GET /api/themes` and map to `ThemeHistory[]`? [Completeness, Spec §FR-009, tasks.md T019]
- [x] CHK018 Is the lazy `/history` route required to be placed before the `**` wildcard with **no** guard/redirect? [Clarity, Spec §FR-010/§FR-017, research.md R3, tasks.md T020]
- [x] CHK019 Is the header link required to be wrapped with the theme toggle in `.app-shell__nav` rather than added as a third direct child? [Clarity, plan.md §Risks, research.md R4, tasks.md T024]
- [x] CHK020 Is the component required to use standalone `OnPush` + signals with `loading`/`errorMessage`/`themes` and clear `loading` on both settle paths? [Completeness, Spec §FR-012, data-model.md §5, tasks.md T021]
- [x] CHK021 Is `createdAt` kept as the raw ISO-8601 string and formatted locally (no backend `@JsonFormat`)? [Clarity, research.md R5, data-model.md §5]
- [x] CHK022 Is the model→service→component→template→scss implementation order stated? [Clarity, tasks.md §Dependencies]

## Scope, Assumptions & Traceability

- [x] CHK023 Is the global (no-auth) history scope recorded? [Assumption, Spec §Non-Goals, §Assumptions]
- [x] CHK024 Are delete/edit/regenerate, pagination, export, and mobile-menu treatment explicitly out of scope? [Assumption, Spec §Non-Goals]
- [x] CHK025 Is the OSIV-masking risk recorded with the integration-test mitigation? [Assumption, plan.md §Risks, tasks.md preamble]
- [x] CHK026 Is every FR-001..FR-022 mapped to at least one task? [Traceability, tasks.md §Requirement Coverage]
- [x] CHK027 Is every SC-001..SC-008 mapped to at least one task? [Traceability, tasks.md §Requirement Coverage]
- [x] CHK028 Does every task carry exact file paths and a verifiable done-definition? [Measurability, tasks.md]
- [x] CHK029 Are tests authored before their implementation targets (TDD ordering)? [Consistency, tasks.md §Dependencies]

## Notes

- Check items off as completed: `[x]`.
- Add the satisfying spec/plan/task reference inline.
- All items must be checked at implementation completion (tasks.md T036).
