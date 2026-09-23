# Security Checklist: Keyword Meanings Up to 20 Words (Escaped Rendering & No Clipping)

**Purpose**: Validate the quality, clarity, and completeness of the *security and sanitization
requirements* touched by the 3 → 20-word change — not the implementation. This is a "unit test for the
requirements" for the longer-definition rendering boundary (US3, FR-011, FR-013, FR-014, SC-005).
**Created**: 2026-09-23
**Feature**: [spec.md](../spec.md) — sections §User Story 3, §Requirements (Rendering and readability),
§Edge Cases, §Success Criteria; [contracts/keyword-definition-length-contract.md](../contracts/keyword-definition-length-contract.md) §4

**Note**: Items test whether the requirements are well-written, complete, and verifiable — they are not
runtime test cases. Keyword `term`/`definition` remain **untrusted model output**; this feature only
lengthens the `definition` and must not weaken the render-time safety model.

## Requirement Completeness

- [x] CHK001 Is it stated that the definition card shows the full up-to-20-word definition through escaped interpolation only? [Completeness, Spec §FR-011] — satisfied by FR-011 / T014
- [x] CHK002 Is the prohibition on `[innerHTML]` and `DomSanitizer` restated for the longer definition? [Completeness, Spec §FR-011, §FR-014] — satisfied by FR-011, FR-014 / T014, T015
- [x] CHK003 Are all injection payload classes enumerated (`<script>`, event handlers such as `onerror`, `javascript:` URLs, `<iframe>`, `<img>`, `<style>`, inline styles)? [Completeness, Spec §FR-013] — satisfied by FR-013 / T014
- [x] CHK004 Is it required that a longer definition neither executes nor injects markup into the DOM? [Completeness, Spec §FR-013, §SC-005] — satisfied by FR-013, SC-005 / T014, T015
- [x] CHK005 Is preservation of the feature-012/013/014 sanitization model required unchanged? [Completeness, Spec §FR-014] — satisfied by FR-014 / T015

## Requirement Clarity

- [x] CHK006 Is "escaped interpolation only" tied to a concrete outcome (inert text; no execution, no node injection)? [Clarity, Spec §FR-013, §Edge Cases] — satisfied by FR-013 / spec §Edge Cases
- [x] CHK007 Is it unambiguous that only the definition *length* changes and the render path does not? [Clarity, Spec §Project Structure Impact, §FR-014] — satisfied by spec §Project Structure Impact / plan.md §Design
- [x] CHK008 Is the trust boundary (untrusted LLM keyword text) explicitly identified for the longer definition? [Clarity, Spec §Key Entities, §Assumptions] — satisfied by spec §Key Entities / §Assumptions

## Requirement Consistency

- [x] CHK009 Do the longer-definition requirements match feature-012/013/014's sanitization model with no divergence? [Consistency, Spec §FR-014] — satisfied by FR-014
- [x] CHK010 Is the security guarantee consistent across the definition card and the highlighted-keyword rendering? [Consistency, Spec §FR-013, §FR-014] — satisfied by FR-013, FR-014 / T014, T015
- [x] CHK011 Does the "definitions remain plain escaped text" non-goal agree with the rendering requirements? [Consistency, Spec §Non-Goals, §FR-011] — satisfied by spec §Non-Goals / FR-011

## Acceptance Criteria Quality

- [x] CHK012 Can "0 executable or injected markup from any definition" be objectively measured? [Measurability, Spec §SC-005] — satisfied by SC-005 / T014
- [x] CHK013 Is the injection test set that must produce zero executions/injections identified? [Measurability, Spec §SC-005, §Edge Cases] — satisfied by SC-005 / spec §Edge Cases / T014

## Scenario & Edge Case Coverage

- [x] CHK014 Are injection payloads inside a longer (up to 20-word) definition covered? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T014
- [x] CHK015 Is Unicode/emoji/RTL text in a long definition addressed as displayable and non-breaking? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T014
- [x] CHK016 Is a very long single token addressed (one token within the 20-token bound must wrap, not overflow)? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases / T014

## Non-Functional & Boundary

- [x] CHK017 Is the requirement independent of any third-party sanitizer dependency, and is that constraint stated? [Dependency, Spec §Assumptions, plan.md §Technical Context] — satisfied by spec §Assumptions / plan.md §Technical Context
- [x] CHK018 Is the requirement that no raw HTML is bound stated as a hard constraint? [Traceability, Spec §FR-014] — satisfied by FR-014 / T015
- [x] CHK019 Is the FR-019 character clamp stated to affect storage only (never splitting a word) without introducing a security change? [Consistency, Spec §FR-019, §Assumptions] — satisfied by FR-019 / spec §Assumptions

## Notes

- Check items off as completed: `[x]`
- Add findings inline; link to spec/contract sections where resolved
- Items are numbered sequentially for easy reference; all must be checked at implementation completion
