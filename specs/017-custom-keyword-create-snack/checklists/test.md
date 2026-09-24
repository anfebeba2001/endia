# Test-Requirements Quality Checklist: "Create snack" from a Custom Keyword's Generated Meaning (Task 12)

**Purpose**: Validate that the **verification/test requirements** for `017-custom-keyword-create-snack` are complete, clear, consistent, measurable, and traceable — i.e. that every success criterion and user story has a defined, executable verification path. This tests the *requirements writing*, not the implementation.
**Created**: 2026-09-24
**Feature**: [spec.md](../spec.md) · [e2e-test-plan.md](../e2e-test-plan.md) · [plan.md](../plan.md)

**Note**: Items are quality questions about the verification requirements. `[x]` items are satisfied by the spec/plan/e2e-test-plan (evidence cited); `[ ]` items flag genuine gaps or items to confirm during implementation. This is a NEW checklist file (the architect's `requirements.md`, `security.md`, and `ux.md` are preserved unchanged).

## Verification Approach & Tooling

- [x] CHK001 Is the test runner for this feature explicitly identified (Playwright E2E as the only frontend runner; `npm run build` as the type-check gate)? [Clarity, plan.md §Testing Strategy, research.md R9]
- [x] CHK002 Is the absence of a frontend unit-test runner acknowledged, with the pure capture/state logic mapped to E2E coverage instead? [Completeness, plan.md §Testing Strategy, research.md R9]
- [x] CHK003 Is the backend verification stance defined (no production change → existing backend suites pass unchanged; no new backend test)? [Clarity, plan.md §Testing Strategy, quickstart.md §7]
- [x] CHK004 Is the decision to introduce no new runner and no new dependency stated and justified? [Consistency, research.md R9, plan.md §Constraints]

## Acceptance-Criteria Measurability

- [x] CHK005 Is each success criterion (SC-001–SC-008) expressed as a measurable, testable outcome (counts/percentages and zero-violation conditions)? [Measurability, Spec §Success Criteria]
- [x] CHK006 Is "action appears only after Define completes" quantified with a concrete before/after observable state? [Clarity, Spec §SC-001, e2e-test-plan.md §1.1]
- [x] CHK007 Is "exactly one meaning snack / no duplicate" defined with a concrete count assertion? [Measurability, Spec §SC-002, e2e-test-plan.md §2.1/§2.3/§2.5]
- [x] CHK008 Is "meaningful error and nothing added" made observable (error region + unchanged backbone count)? [Clarity, Spec §SC-003, e2e-test-plan.md §2.4]
- [x] CHK009 Is "quiz-free" made observable (no quiz request/route, no quiz affordance) rather than asserted abstractly? [Measurability, Spec §SC-004, e2e-test-plan.md §3.1/§3.3]
- [x] CHK010 Is "visually distinct" made observable (distinct card variant + always-present text badge, not colour-only)? [Measurability, Spec §SC-004, e2e-test-plan.md §3.1]
- [x] CHK011 Is "persists across reload/new session" defined with a concrete re-entry sequence? [Clarity, Spec §SC-005, e2e-test-plan.md §4.1]
- [x] CHK012 Is "zero injected/executed markup" defined with observable sentinels and DOM node counts? [Measurability, Spec §SC-006, e2e-test-plan.md §5.1/§5.2]
- [x] CHK013 Is "no new entity/schema/endpoint" traceable to a confirmation source (plan §Backend finding + unchanged backend suites)? [Traceability, Spec §SC-007, plan.md §Backend finding]

## Scenario Coverage

- [x] CHK014 Are verification requirements defined for the primary flow (define → Create snack → one backbone entry)? [Coverage, e2e-test-plan.md §1.1/§2.1]
- [x] CHK015 Are verification requirements defined for the loading/in-progress state? [Coverage, e2e-test-plan.md §2.2]
- [x] CHK016 Are verification requirements defined for the failure/exception flow (forced 503) and the "nothing added" invariant? [Coverage, e2e-test-plan.md §2.4]
- [x] CHK017 Are verification requirements defined for the duplicate/concurrent flow (rapid double activation and card re-open)? [Coverage, e2e-test-plan.md §2.3/§2.5]
- [x] CHK018 Are verification requirements defined for the "action not offered" negative cases (while defining, on definition error, blank meaning)? [Coverage, e2e-test-plan.md §1.1/§1.2, Spec §Edge Cases]
- [x] CHK019 Are verification requirements defined for both source-subtopic resolutions (regular `subtopicId` and meaning `sourceSubtopicId`, Option A)? [Coverage, e2e-test-plan.md §2.1/§2.6, Spec §FR-005]
- [x] CHK020 Are verification requirements defined for the persistence/reload path and its graceful-degradation path? [Coverage, e2e-test-plan.md §4.1/§4.2]
- [x] CHK021 Are verification requirements defined for the security payload classes (`<script>`, `onerror=`, `javascript:`, `<iframe>`, `<style>`, inline styles)? [Coverage, e2e-test-plan.md §5.1/§5.2, Spec §FR-018]
- [x] CHK022 Are verification requirements defined for non-regression of feature-016's Define flow and feature-014's regular "Generate snack" flow? [Coverage, e2e-test-plan.md §6.1/§6.2, Spec §FR-021/§FR-022]

## Traceability & Completeness

- [x] CHK023 Is a requirement→scenario coverage map provided for every FR (FR-001–FR-024) and SC (SC-001–SC-008)? [Traceability, e2e-test-plan.md §Coverage Map, tasks.md §FR/SC Traceability]
- [x] CHK024 Does every task in tasks.md map to at least one FR/SC (no orphaned tasks)? [Traceability, tasks.md §Traceability Matrices]
- [x] CHK025 Are the verification items that are out of E2E scope (backend idempotency/error shapes, no `[innerHTML]`/`DomSanitizer`) explicitly assigned to a non-E2E evidence source? [Completeness, e2e-test-plan.md §Out of E2E scope, tasks.md T012/T016]
- [x] CHK026 Is a completion-validation task defined that runs the build/type-check, the new E2E spec, and the existing regression suites? [Completeness, tasks.md T020, quickstart.md §7]

## Gaps & Open Items

- [x] CHK027 Is the loading-state label text fixed by a requirement? **RESOLVED (orchestrator, 2026-09-24):** spec §FR-006 now fixes the labels (`Create snack` / `Creating…` / `Added to your snacks`) and plan Open Decision #2 is marked RESOLVED. T008 uses exactly these labels. [Gap, Spec §FR-006, plan.md §Open Decisions #2]
- [x] CHK028 Is true unit coverage of the pure capture/state logic required or explicitly waived? **RESOLVED (orchestrator, 2026-09-24):** E2E-only coverage is accepted and the unit gap is explicitly waived (research.md R9 §Resolution), consistent with 012–016. [Gap, research.md R9]
- [x] CHK029 Is there a defined assertion for the `alreadyExisted: true` idempotent response surfacing the `done` state (beyond the local `hasGenerated` fallback)? **RESOLVED (orchestrator, 2026-09-24):** e2e-test-plan §2.5 step 3 and tasks.md T005 now assert the server-side idempotent branch (`alreadyExisted: true` → `done`, one entry, no duplicate). [Coverage, Spec §FR-010, e2e-test-plan.md §2.5]

## Notes

- Check items off as completed: `[x]`
- Add findings inline; link to spec sections where resolved
- Items are numbered sequentially for easy reference; all must be checked at implementation completion
- The three architect-authored checklists (`requirements.md`, `security.md`, `ux.md`) remain the primary requirements-quality gates; this file adds the verification/test-requirements lens.
