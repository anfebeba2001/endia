# Security Checklist: Custom Keyword "Define" (Selected-Term & Generated-Definition Sanitization)

**Purpose**: Validate the quality, clarity, and completeness of the *security and sanitization
requirements* for the selected term and the generated definition — not the implementation. This is a
"unit test for the requirements" for the selection-Define security boundary (US3, FR-013–FR-015,
SC-004).
**Created**: 2026-09-24
**Feature**: [spec.md](../spec.md) — sections §User Story 3, §Requirements (Display and security),
§Edge Cases, §Success Criteria; [contracts/definition-contract.md](../contracts/definition-contract.md) §1.2, §3.3

**Note**: Items test whether the requirements are well-written, complete, and verifiable — they are not
runtime test cases. The selected term (document text) and the generated `definition` (model output)
are both treated as **untrusted**.

## Requirement Completeness

- [x] CHK001 Is it stated that the selected term and the generated definition are untrusted and rendered as plain escaped text? [Completeness, Spec §FR-013] — satisfied by FR-013 / contract §3.3
- [x] CHK002 Are all injection payload classes enumerated (`<script>`, event handlers such as `onerror`, `javascript:` URLs, `<iframe>`, `<img>`, `<style>`, inline styles)? [Completeness, Spec §FR-014] — satisfied by FR-014 / contract §3.3
- [x] CHK003 Is it required that the term/definition markup is neither executed nor injected into the DOM? [Completeness, Spec §FR-014, §SC-004] — satisfied by FR-014, SC-004 / T033–T034
- [x] CHK004 Is preservation of readable text (no silent loss) required when markup is neutralized? [Completeness, Spec §Edge Cases, §US3 AS3] — satisfied by spec §Edge Cases / US3 AS3
- [x] CHK005 Is preservation of the feature-012/013/014/015 sanitization model (no `[innerHTML]`, no `DomSanitizer`, escaped interpolation) required unchanged? [Completeness, Spec §FR-015] — satisfied by FR-015 / plan.md §Constraints
- [x] CHK006 Is the definition card's own DOM (term, definition, loading/error labels) explicitly covered by the escaping requirement? [Completeness, Spec §FR-013, contract §3.3] — satisfied by FR-013 / contract §3.3 / T030, T034
- [x] CHK007 Is the selected-term read path explicitly required to use plain text (`Selection.toString()`) and never `innerHTML`? [Completeness, Spec §FR-013, research.md R8] — satisfied by FR-013 / research.md R8 / T022, T034
- [x] CHK008 Is it specified that the affordance renders a **static** label only (no user/model content)? [Completeness, contract §3.3, plan.md §Design] — satisfied by contract §3.3 / plan.md §Design / T023, T024
- [x] CHK009 Is it stated that the backend returns the definition as plain text and never HTML-escapes/renders at rest? [Completeness, Spec §FR-013, data-model.md §5.2] — satisfied by FR-013 / data-model.md §5.2

## Requirement Clarity

- [x] CHK010 Is "escaped text only" defined with a concrete outcome (inert, readable text; never an element)? [Clarity, Spec §FR-013/§FR-014, §Edge Cases] — satisfied by FR-013/FR-014 / spec §Edge Cases
- [x] CHK011 Is it unambiguous which layer owns escaping (frontend render boundary; the API carries plain text)? [Clarity, Spec §FR-013, contract §3.3] — satisfied by FR-013 / contract §3.3
- [x] CHK012 Is it specified that text containing markup is still displayed (readable) rather than removed? [Clarity, Spec §Edge Cases, §US3 AS3] — satisfied by spec §Edge Cases / US3 AS3
- [x] CHK013 Is it stated that escaped entities in the term or definition (`&lt;`, `&amp;`) display without double-escaping? [Clarity, Spec §Edge Cases, §US3 AS4] — satisfied by spec §Edge Cases / US3 AS4

## Requirement Consistency

- [x] CHK014 Do the selection-Define security requirements match feature-012/013/014/015's sanitization model with no divergence? [Consistency, Spec §FR-015] — satisfied by FR-015
- [x] CHK015 Is the security guarantee consistent across the term read path, the affordance and the card? [Consistency, Spec §FR-013, §SC-004, contract §3.3] — satisfied by FR-013, SC-004 / contract §3.3
- [x] CHK016 Does the "read plain text" requirement agree with the "escape at render" requirement (no double-processing)? [Consistency, Spec §FR-013, research.md R8] — satisfied by FR-013 / research.md R8
- [x] CHK017 Is the no-"Create snack" scope consistent with the security boundary (the custom card is display-only, exposing no new content path)? [Consistency, Spec §FR-019, contract §3.4] — satisfied by FR-019 / contract §3.4

## Acceptance Criteria Quality

- [x] CHK018 Can "0 executable or injected markup from the selected term or generated definition" be objectively measured? [Measurability, Spec §SC-004] — satisfied by SC-004 / T033
- [x] CHK019 Is the injection test set that must produce zero executions/injections explicitly identified? [Measurability, Spec §SC-004, e2e-test-plan.md §5] — satisfied by SC-004 / e2e-test-plan.md §5
- [x] CHK020 Can "readable text is not silently lost" be objectively verified per payload class? [Measurability, Spec §Edge Cases, §US3 AS3] — satisfied by spec §Edge Cases / T033

## Scenario & Edge Case Coverage

- [x] CHK021 Are requirements defined for injection payloads inside the **selected term**? [Coverage, Spec §Edge Cases, e2e-test-plan.md §5.2] — satisfied by spec §Edge Cases / T033
- [x] CHK022 Are requirements defined for injection payloads inside the **generated definition** shown on the card? [Coverage, Spec §Edge Cases, e2e-test-plan.md §5.1] — satisfied by spec §Edge Cases / T033
- [x] CHK023 Are escaped entities in the term/definition required to display without double-escaping? [Coverage, Spec §Edge Cases, §US3 AS4] — satisfied by spec §Edge Cases / US3 AS4
- [x] CHK024 Is a very long generated definition addressed for layout stability with no overflow break? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T031
- [x] CHK025 Are Unicode/emoji/RTL text in the term or definition addressed as displayable and non-breaking? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases

## Non-Functional & Boundary

- [x] CHK026 Is the trust boundary (untrusted document text and untrusted model output) explicitly identified? [Traceability, Spec §Key Entities, §Assumptions] — satisfied by spec §Key Entities / §Assumptions
- [x] CHK027 Is the requirement that no raw HTML is bound stated as a hard constraint and traceable to the chosen approach (escaped interpolation, no `DomSanitizer`)? [Traceability, Spec §FR-013/§FR-015, research.md R8] — satisfied by FR-013/FR-015 / research.md R8
- [x] CHK028 Is the security requirement independent of any third-party sanitizer dependency, and is that constraint stated? [Dependency, Spec §Assumptions, plan.md §Technical Context] — satisfied by spec §Assumptions / plan.md §Technical Context

## Notes

- Check items off as completed: `[x]`
- Add findings inline; link to spec/contract sections where resolved
- Items are numbered sequentially for easy reference; all must be checked at implementation completion
