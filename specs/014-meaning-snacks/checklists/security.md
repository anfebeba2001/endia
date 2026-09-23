# Security Checklist: Meaning Snacks (Sanitization & Injection Safety)

**Purpose**: Validate the quality, clarity, and completeness of the *security and sanitization
requirements* for meaning snacks and their source keyword text — not the implementation. This is a
"unit test for the requirements" for the meaning-snack security boundary (US4, FR-009, FR-021–FR-023,
SC-008).
**Created**: 2026-09-23
**Feature**: [spec.md](../spec.md) — sections §User Story 4, §Requirements (Security / sanitization),
§Edge Cases, §Success Criteria; [contracts/meaning-snack-contract.md](../contracts/meaning-snack-contract.md) §1.2, §3.3–§3.4

**Note**: Items test whether the requirements are well-written, complete, and verifiable — they are not
runtime test cases. The keyword `term`/`definition` and the generated `concept`/`metaphor`/
`whyItMatters`/`related` are all treated as **untrusted model output**.

## Requirement Completeness

- [x] CHK001 Is it stated that keyword `term`/`definition` and generated meaning-snack content are untrusted and rendered through the same safe allowlist mechanism as regular snacks? [Completeness, Spec §FR-021] — satisfied by FR-021 / contract §3.3
- [x] CHK002 Are all injection payload classes enumerated (`<script>`, event handlers such as `onerror`, `javascript:` URLs, `<iframe>`, `<img>`, `<style>`, inline styles)? [Completeness, Spec §FR-022] — satisfied by FR-022 / contract §3.3
- [x] CHK003 Is it required that keyword/meaning markup is neither executed nor injected into the DOM? [Completeness, Spec §FR-022, §SC-008] — satisfied by FR-022, SC-008 / T043–T045
- [x] CHK004 Is preservation of readable text (no silent loss) required when markup is neutralized? [Completeness, Spec §Edge Cases, US4 AS3] — satisfied by spec §Edge Cases / US4 AS3
- [x] CHK005 Is preservation of the feature-012/013 sanitization model (render-time allowlist, no `[innerHTML]`, no `DomSanitizer`, escaped interpolation) required unchanged? [Completeness, Spec §FR-023] — satisfied by FR-023 / plan.md §Constraints
- [x] CHK006 Is the definition card's own DOM (term, definition, action labels) explicitly covered by the escaping requirement, not just the backbone card? [Completeness, Spec §FR-021, contracts §3.5] — satisfied by FR-021 / contract §3.5 / T029, T044
- [x] CHK007 Is the generated content (`concept`/`metaphor`/`whyItMatters`/`related`) explicitly covered, in addition to the source term/definition? [Completeness, Spec §FR-009, §FR-021] — satisfied by FR-009, FR-021 / contract §3.3 / T045
- [x] CHK008 Is it specified that meaning content carries **no keywords**, so it exposes no highlight controls / nested definition card? [Completeness, contracts §3.3, Spec §Clarifications #3] — satisfied by contract §3.3 / plan.md M5

## Requirement Clarity

- [x] CHK009 Is "neutralized" defined with a concrete outcome (rendered as inert, escaped text; never executed/injected/dropped)? [Clarity, Spec §FR-022, §Edge Cases] — satisfied by FR-022 / spec §Edge Cases / contract §3.3
- [x] CHK010 Is the allowed output vocabulary for meaning-snack rendering closed and precise (typed AST nodes; only the feature-012 elements)? [Clarity, contracts §3.3, plan.md §Design] — satisfied by contract §3.3 / FR-023
- [x] CHK011 Is it unambiguous which layer owns escaping (frontend render boundary; backend stores verbatim and never HTML-escapes at rest)? [Clarity, Spec §FR-021, data-model.md §4.2] — satisfied by FR-021 / data-model.md §4.2
- [x] CHK012 Is it specified that text containing markup is still displayed (readable) rather than removed? [Clarity, Spec §Edge Cases, US4 AS3] — satisfied by spec §Edge Cases / US4 AS3
- [x] CHK013 Is it stated that escaped entities in stored text (`&lt;`, `&amp;`) display without double-escaping? [Clarity, Spec §Edge Cases, US4 AS4] — satisfied by spec §Edge Cases / US4 AS4

## Requirement Consistency

- [x] CHK014 Do the meaning-snack security requirements match feature-012/013's sanitization model with no divergence? [Consistency, Spec §FR-023] — satisfied by FR-023
- [x] CHK015 Is the security guarantee consistent across all meaning-snack surfaces (backbone card, definition card on snacks/results/history)? [Consistency, Spec §FR-021, §SC-008, contracts §3.3] — satisfied by FR-021, SC-008 / contract §3.3
- [x] CHK016 Does the backend "store verbatim" requirement agree with the frontend "escape at render" requirement (no double-escaping)? [Consistency, Spec §FR-021, data-model.md §4.2] — satisfied by FR-021 / data-model.md §4.2
- [x] CHK017 Is the no-quiz invariant consistent with the security boundary (the meaning DTO exposes no quiz fields, so no quiz surface renders untrusted content)? [Consistency, Spec §FR-016, §FR-022] — satisfied by FR-016, FR-022 / contract §1.2

## Acceptance Criteria Quality

- [x] CHK018 Can "0 executable or injected markup from keyword/meaning-snack content" be objectively measured across all surfaces? [Measurability, Spec §SC-008] — satisfied by SC-008 / T043–T044
- [x] CHK019 Is the injection test set that must produce zero executions/injections explicitly identified? [Measurability, Spec §SC-008] — satisfied by SC-008 / T043–T044
- [x] CHK020 Can "readable text is not silently lost" be objectively verified per payload class? [Measurability, Spec §Edge Cases, US4 AS3] — satisfied by spec §Edge Cases / T043

## Scenario & Edge Case Coverage

- [x] CHK021 Are requirements defined for injection payloads inside a `term` specifically? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T043
- [x] CHK022 Are requirements defined for injection payloads inside a `definition` shown on the card? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T043, T044
- [x] CHK023 Are requirements defined for injection payloads inside generated `concept`/`metaphor`/`whyItMatters`/`related`? [Coverage, Spec §Edge Cases, contracts §3.3] — satisfied by spec §Edge Cases / contract §3.3 / T043
- [x] CHK024 Are escaped entities in stored term/definition/content required to display without double-escaping? [Coverage, Spec §Edge Cases, US4 AS4] — satisfied by spec §Edge Cases / US4 AS4
- [x] CHK025 Is very long generated content addressed for layout/performance with no overflow break? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T038
- [x] CHK026 Is Unicode/emoji/RTL text in term, definition or content addressed as displayable and non-breaking? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases

## Non-Functional & Boundary

- [x] CHK027 Is the trust boundary (untrusted LLM/user-derived term/definition/content) explicitly identified? [Traceability, Spec §Key Entities, §Assumptions] — satisfied by spec §Key Entities / §Assumptions
- [x] CHK028 Is the requirement that no raw HTML is bound stated as a hard constraint and traceable to the chosen approach (RichTextComponent reuse)? [Traceability, Spec §FR-023, research.md R7] — satisfied by FR-023 / research.md R7
- [x] CHK029 Is the security requirement independent of any third-party sanitizer dependency, and is that constraint stated? [Dependency, Spec §Assumptions, plan.md §Technical Context] — satisfied by spec §Assumptions / plan.md §Technical Context

## Notes

- Check items off as completed: `[x]`
- Add findings inline; link to spec/contract sections where resolved
- Items are numbered sequentially for easy reference; all must be checked at implementation completion
