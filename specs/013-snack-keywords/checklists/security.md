# Security Checklist: Snack Keywords (Sanitization & Injection Safety)

**Purpose**: Validate the quality, clarity, and completeness of the *security and sanitization
requirements* for snack keywords — not the implementation. This is a "unit test for the requirements"
for the keyword security boundary (US4, FR-026–FR-029, SC-007).
**Created**: 2026-09-23
**Feature**: [spec.md](../spec.md) — sections §User Story 4, §Requirements (Security/sanitization),
§Edge Cases, §Success Criteria; [contracts/snack-keywords-contract.md](../contracts/snack-keywords-contract.md) §2–§3

**Note**: Items test whether the requirements are well-written, complete, and verifiable — they are not
runtime test cases. All keyword `term`/`definition` values are treated as **untrusted model output**.

## Requirement Completeness

- [x] CHK001 Is it stated that keyword `term` and `definition` are untrusted and rendered through the same safe allowlist mechanism as body text? [Completeness, Spec §FR-026] — satisfied by FR-026 / contract §3.5
- [x] CHK002 Are all injection payload classes enumerated (`<script>`, event handlers such as `onerror`, `javascript:` URLs, `<iframe>`, `<img>`, `<style>`, inline styles)? [Completeness, Spec §FR-027] — satisfied by FR-027 / contract §3.5
- [x] CHK003 Is it required that keyword markup is neither executed nor injected into the DOM? [Completeness, Spec §FR-027, §SC-007] — satisfied by FR-027, SC-007 / T040–T042
- [x] CHK004 Is preservation of readable text (no silent loss) required when keyword markup is neutralized? [Completeness, Spec §FR-028] — satisfied by FR-028 / contract §3.5
- [x] CHK005 Is preservation of the feature-012 sanitization model (render-time allowlist, no `[innerHTML]`, no `DomSanitizer`) required unchanged? [Completeness, Spec §FR-029] — satisfied by FR-029 / plan.md §Constraints
- [x] CHK006 Is the definition-card definition text explicitly covered by the escaping requirement, not just the highlighted term? [Completeness, contracts/snack-keywords-contract.md §3.5] — satisfied by contract §3.5 / T035
- [x] CHK007 Is it specified that keyword text is returned as plain text and never HTML-escaped into entity characters by the backend? [Completeness, Spec §FR-010, contracts/snack-keywords-contract.md §1.4] — satisfied by FR-010 / contract §1.4

## Requirement Clarity

- [x] CHK008 Is "neutralized" defined with a concrete outcome (rendered as inert, escaped text; never executed/injected/dropped)? [Clarity, Spec §FR-027, §FR-028] — satisfied by FR-027, FR-028 / contract §2.2
- [x] CHK009 Is the allowed output vocabulary for keyword rendering closed and precise (typed AST nodes; only `<p>/<ul>/<li>/<strong>/<br>/<button>`)? [Clarity, contracts/snack-keywords-contract.md §2.2] — satisfied by contract §2.2 / FR-029
- [x] CHK010 Is it unambiguous which layer owns keyword escaping (frontend render boundary, not backend)? [Clarity, Spec §FR-026, research.md R3] — satisfied by FR-026, contract §1.4 / research.md R3
- [x] CHK011 Is it specified that a keyword term/definition containing markup is still displayed (readable) rather than removed? [Clarity, Spec §FR-028, §Edge Cases] — satisfied by FR-028 / spec §Edge Cases

## Requirement Consistency

- [x] CHK012 Do the keyword security requirements match feature-012's body-text sanitization model with no divergence? [Consistency, Spec §FR-029] — satisfied by FR-029
- [x] CHK013 Is the security guarantee consistent across all snack-displaying surfaces (snack card, recap, history)? [Consistency, Spec §FR-025, §SC-007] — satisfied by FR-025, SC-007 / contract §3.5
- [x] CHK014 Does the backend "plain text round-trip" requirement agree with the frontend "escape at render" requirement (no double-escaping)? [Consistency, Spec §FR-010, §FR-011, contracts §1.4] — satisfied by FR-010, FR-011 / contract §1.4

## Acceptance Criteria Quality

- [x] CHK015 Can "0 executable or injected markup from keyword text" be objectively measured across all surfaces? [Measurability, Spec §SC-007] — satisfied by SC-007 / T040–T041
- [x] CHK016 Is the keyword injection test set that must produce zero executions/injections explicitly identified? [Measurability, Spec §SC-007] — satisfied by SC-007 / T040–T041
- [x] CHK017 Can "readable keyword text is not silently lost" be objectively verified per payload class? [Measurability, Spec §FR-028] — satisfied by FR-028 / T040

## Scenario & Edge Case Coverage

- [x] CHK018 Are requirements defined for injection payloads inside a `term` specifically (which also drives matching)? [Coverage, Spec §Edge Cases, contracts §2.2] — satisfied by spec §Edge Cases / contract §2.2, T040
- [x] CHK019 Are requirements defined for injection payloads inside a `definition` shown in the card? [Coverage, Spec §Edge Cases, contracts §3.5] — satisfied by spec §Edge Cases / contract §3.5, T040
- [x] CHK020 Are escaped entities in stored keyword text (`&lt;`, `&amp;`) required to display without double-escaping? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / contract §1.4, US4 AS4
- [x] CHK021 Is a keyword term that fails to match (so no control renders) addressed for its injection risk? [Coverage, contracts §2.1 M7] — satisfied by contract §2.1 M7
- [x] CHK022 Is the definition card's own DOM (dialog/close control) required to avoid introducing new injection surface? [Coverage, contracts §3.5] — satisfied by contract §3.5 / T035, T042

## Non-Functional & Boundary

- [x] CHK023 Is the trust boundary (untrusted LLM keyword output) explicitly identified? [Traceability, Spec §Key Entities] — satisfied by spec §Key Entities (SnackKeyword, untrusted plain text)
- [x] CHK024 Is the requirement that no raw HTML is bound for keyword text stated as a hard constraint and traceable to the chosen approach? [Traceability, Spec §FR-026, research.md R4] — satisfied by FR-026 / research.md R4
- [x] CHK025 Is the security requirement independent of any third-party sanitizer dependency, and is that constraint stated? [Dependency, Spec §Assumptions, plan.md §Technical Context] — satisfied by spec §Assumptions / plan.md §Technical Context

## Notes

- Check items off as completed: `[x]`
- Add findings inline; link to spec/contract sections where resolved
- Items are numbered sequentially for easy reference
