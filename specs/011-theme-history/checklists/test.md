# Test Requirements Quality Checklist: Read-Only History of Created Themes

**Purpose**: Validate that the feature's verification requirements are complete, unambiguous, and executable — covering the backend unit/contract/integration tests, the new Playwright spec, the named regression suite, the type-check build, and manual verification — before/while implementing `011-theme-history`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md) | [quickstart.md](../quickstart.md)

**Note**: This checklist tests the *test/verification requirements* for quality, not the implementation. The frontend has **no unit runner**; Playwright E2E is its verification surface. Each item must be answerable "yes" at implementation completion.

## Backend Coverage (FR-019)

- [x] CHK001 Is there a test requirement for `GET /api/themes` returning a JSON **array** with the full nested shape? [Coverage, Spec §SC-002, tasks.md T005]
- [x] CHK002 Is there a test requirement for newest-first ordering? [Coverage, Spec §SC-001, tasks.md T004, T005, T006]
- [x] CHK003 Is there a test requirement for the empty case returning HTTP 200 with `[]` (not 404/500)? [Coverage, Spec §SC-003, tasks.md T004, T005, T006]
- [x] CHK004 Is there a test requirement for nested subtopic/snack/question assembly? [Coverage, Spec §SC-002, tasks.md T004, T005]
- [x] CHK005 Is there a test requirement proving no `LazyInitializationException` by accessing nested data **after** the service transaction ends? [Coverage, Spec §FR-005/§SC-003, tasks.md T006]
- [x] CHK006 Is there a test requirement that the empty case never calls the child finders? [Coverage, Spec §FR-005, tasks.md T004]
- [x] CHK007 Is the integration test required to run under Testcontainers MySQL with `@Tag("integration")`? [Clarity, plan.md §Testing, tasks.md T006]
- [x] CHK008 Is the contract test required to mirror the existing `BatchStatusContractTest` MockMvc standalone style? [Consistency, plan.md §Testing, tasks.md T005]

## Frontend Coverage (FR-020)

- [x] CHK009 Is there a test requirement for the populated history list (entries with `prompt` and `createdAt`)? [Coverage, Spec §SC-007, tasks.md T008]
- [x] CHK010 Is there a test requirement for the empty state (message shown, zero entries, no error)? [Coverage, Spec §SC-006, tasks.md T010]
- [x] CHK011 Is there a test requirement for the error state using `ErrorService` (message shown, list hidden)? [Coverage, Spec §SC-006, tasks.md T010]
- [x] CHK012 Is there a test requirement for the loading state (visible in flight, cleared once settled)? [Coverage, Spec §SC-006, tasks.md T010]
- [x] CHK013 Is there a test requirement for the header link and the deep link to `/history` (no redirect)? [Coverage, Spec §SC-005, tasks.md T008]
- [x] CHK014 Is there a test requirement for nested read-only detail (subtopics, snacks, questions)? [Coverage, Spec §SC-007, tasks.md T009]
- [x] CHK015 Is there a test requirement asserting **0** `POST /api/batches` and **0** `POST /api/snacks/generate` from the page? [Coverage, Spec §SC-007, tasks.md T009]
- [x] CHK016 Is there a test requirement for keyboard reachability/activation and a visible focus indicator? [Coverage, Spec §FR-018, tasks.md T011]
- [x] CHK017 Is the "nothing generated yet" subtopic case covered by a test requirement? [Coverage, Spec §US2 AS5, tasks.md T009]

## Test Infrastructure & Clarity

- [x] CHK018 Is `setupThemesHistoryMock` required to fulfil `GET **/api/themes` and to stay **out** of `setupAllMocks`? [Consistency, research.md R8, tasks.md T007]
- [x] CHK019 Is the absence of a frontend unit runner documented, with Playwright named as the verification path? [Clarity, plan.md §Testing, tasks.md §Organization]
- [x] CHK020 Are the exact test/build commands recorded (`mvn test`, `mvn verify -Pintegration`, `npx playwright test e2e/theme-history.spec.ts`, `npm run build`)? [Clarity, quickstart.md §Automated tests, tasks.md T026–T029]
- [x] CHK021 Are the tests required to be authored first and to fail before implementation? [Consistency, tasks.md Phase 2]

## Regression, Boundaries & Gaps

- [x] CHK022 Are the named regression suites (`happy-path`, `direct-url-access`, `restart-clears-state`, `dark-mode`, `back-navigation`, `logo-visibility/*`) required to pass unchanged? [Consistency, Spec §SC-008, tasks.md T028]
- [x] CHK023 Is a full-suite Playwright run required? [Coverage, Spec §SC-008, tasks.md T034]
- [x] CHK024 Is `npm run build` required to type-check the new route/service/model/component? [Coverage, Spec §SC-005, tasks.md T029]
- [x] CHK025 Is there a requirement confirming no existing endpoint or schema change (non-regression at the contract level)? [Coverage, Spec §FR-007/§FR-022, tasks.md T030]
- [x] CHK026 Are the theme-with-no-subtopics and subtopic-with-no-snacks/no-questions cases covered? [Gap, Spec §Edge Cases, tasks.md T004, T009]
- [x] CHK027 Is the large-history (many themes / nested items) case acknowledged for manual verification? [Gap, Spec §Edge Cases, quickstart.md]
- [x] CHK028 Does the manual quickstart verification cover US1–US4 (endpoint, header/deep link, nested detail, states, a11y)? [Coverage, quickstart.md, tasks.md T035]

## Notes

- Check items off as completed: `[x]`.
- Reference the spec/plan/task ID that satisfies each item.
- All items must be checked at implementation completion (tasks.md T036).
