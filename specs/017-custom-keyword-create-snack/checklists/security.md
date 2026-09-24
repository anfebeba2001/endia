# Security Checklist: Custom "Create snack" (Term, Generated Meaning & Created-Content Sanitization)

**Purpose**: Validate the quality, clarity, and completeness of the *security and sanitization
requirements* for the custom term, the generated meaning, and the created meaning-snack content — not
the implementation. This is a "unit test for the requirements" for the custom "Create snack" security
boundary (US4, FR-017–FR-019, SC-006).
**Created**: 2026-09-24
**Feature**: [spec.md](../spec.md) — sections §User Story 4, §Requirements (Security / sanitization),
§Edge Cases, §Success Criteria

**Note**: Items test whether the requirements are well-written, complete, and verifiable — they are not
runtime test cases. The custom term (document text) and the generated meaning (model output) are both
treated as **untrusted**.

## Requirement Completeness

- [x] CHK001 Is it stated that the custom term, generated meaning, and created content are untrusted and rendered as plain escaped text? [Completeness, Spec §FR-017] — satisfied by FR-017
- [x] CHK002 Are all injection payload classes enumerated (`<script>`, event handlers such as `onerror`, `javascript:` URLs, `<iframe>`, `<img>`, `<style>`, inline styles)? [Completeness, Spec §FR-018] — satisfied by FR-018
- [x] CHK003 Is it required that the term/meaning/created-content markup is neither executed nor injected into the DOM? [Completeness, Spec §FR-018, §SC-006] — satisfied by FR-018, SC-006
- [x] CHK004 Is preservation of readable text (no silent loss) required when markup is neutralized? [Completeness, Spec §Edge Cases, §US4 AS3] — satisfied by spec §Edge Cases / US4 AS3
- [x] CHK005 Is preservation of the feature-012/013/014/015/016 sanitization model (no `[innerHTML]`, no `DomSanitizer`, escaped interpolation) required unchanged? [Completeness, Spec §FR-019] — satisfied by FR-019
- [x] CHK006 Is the definition card's own DOM (term, generated meaning, action labels/states) explicitly covered by the escaping requirement? [Completeness, Spec §FR-017, §US4 AS5] — satisfied by FR-017 / US4 AS5
- [x] CHK007 Is the created snack's content, as rendered in the backbone, explicitly covered by the escaping requirement? [Completeness, Spec §FR-017, §US4 AS5] — satisfied by FR-017 / US4 AS5
- [x] CHK008 Is it specified that the "Create snack" action renders a **static** label only (no user/model content)? [Completeness, Spec §FR-001, §Project Structure Impact] — satisfied by FR-001 / Spec §Project Structure Impact
- [x] CHK009 Is it stated that the created content is passed to the existing feature-014 path and rendered through the feature-012 safe renderer? [Completeness, Spec §FR-004/§FR-017, §Assumptions] — satisfied by FR-004/FR-017 / spec §Assumptions

## Requirement Clarity

- [x] CHK010 Is "escaped text only" defined with a concrete outcome (inert, readable text; never an element)? [Clarity, Spec §FR-017/§FR-018, §Edge Cases] — satisfied by FR-017/FR-018 / spec §Edge Cases
- [x] CHK011 Is it unambiguous which layer owns escaping (frontend render boundary; the API carries plain text)? [Clarity, Spec §FR-017, §Context & Scope] — satisfied by FR-017 / Spec §Context & Scope
- [x] CHK012 Is it specified that text containing markup is still displayed (readable) rather than removed? [Clarity, Spec §Edge Cases, §US4 AS3] — satisfied by spec §Edge Cases / US4 AS3
- [x] CHK013 Is it stated that escaped entities in the term/meaning/content (`&lt;`, `&amp;`) display without double-escaping? [Clarity, Spec §Edge Cases, §US4 AS4] — satisfied by spec §Edge Cases / US4 AS4

## Requirement Consistency

- [x] CHK014 Do the custom "Create snack" security requirements match feature-012/013/014/015/016's sanitization model with no divergence? [Consistency, Spec §FR-019] — satisfied by FR-019
- [x] CHK015 Is the security guarantee consistent across the term/meaning display, the action, and the created backbone entry? [Consistency, Spec §FR-017, §SC-006] — satisfied by FR-017, SC-006
- [x] CHK016 Does the "reuse the feature-014 path" requirement agree with the "escape at render" requirement (no double-processing)? [Consistency, Spec §FR-004/§FR-017] — satisfied by FR-004/FR-017
- [x] CHK017 Is the scope (no new content path/entity) consistent with the security boundary? [Consistency, Spec §FR-016, §Non-Goals] — satisfied by FR-016 / spec §Non-Goals

## Acceptance Criteria Quality

- [x] CHK018 Can "0 executable or injected markup from the custom term, generated meaning, or created content" be objectively measured? [Measurability, Spec §SC-006] — satisfied by SC-006
- [x] CHK019 Is the injection test set that must produce zero executions/injections explicitly identified? [Measurability, Spec §SC-006, §Edge Cases] — satisfied by SC-006 / spec §Edge Cases
- [x] CHK020 Can "readable text is not silently lost" be objectively verified per payload class? [Measurability, Spec §Edge Cases, §US4 AS3] — satisfied by spec §Edge Cases / US4 AS3

## Scenario & Edge Case Coverage

- [x] CHK021 Are requirements defined for injection payloads inside the **custom term**? [Coverage, Spec §Edge Cases, §US4 AS1] — satisfied by spec §Edge Cases / US4 AS1
- [x] CHK022 Are requirements defined for injection payloads inside the **generated meaning** shown on the card? [Coverage, Spec §Edge Cases, §US4 AS1] — satisfied by spec §Edge Cases / US4 AS1
- [x] CHK023 Are requirements defined for injection payloads inside the **created snack content** rendered in the backbone? [Coverage, Spec §Edge Cases, §US4 AS5] — satisfied by spec §Edge Cases / US4 AS5
- [x] CHK024 Are escaped entities in the term/meaning/content required to display without double-escaping? [Coverage, Spec §Edge Cases, §US4 AS4] — satisfied by spec §Edge Cases / US4 AS4
- [x] CHK025 Is a very long generated meaning/content addressed for layout stability with no overflow break? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases
- [x] CHK026 Are Unicode/emoji/RTL text in the term/meaning/content addressed as displayable and non-breaking? [Coverage, Spec §Edge Cases] — satisfied by spec §Edge Cases

## Non-Functional & Boundary

- [x] CHK027 Is the trust boundary (untrusted document text and untrusted model output) explicitly identified? [Traceability, Spec §Key Entities, §Assumptions] — satisfied by spec §Key Entities / §Assumptions
- [x] CHK028 Is the requirement that no raw HTML is bound stated as a hard constraint and traceable to the chosen approach (escaped interpolation, no `DomSanitizer`)? [Traceability, Spec §FR-017/§FR-019] — satisfied by FR-017/FR-019
- [x] CHK029 Is the security requirement independent of any third-party sanitizer dependency, and is that constraint stated? [Dependency, Spec §Assumptions, §Project Structure Impact] — satisfied by spec §Assumptions / Spec §Project Structure Impact

## Notes

- Check items off as completed: `[x]`
- Add findings inline; link to spec sections where resolved
- Items are numbered sequentially for easy reference; all must be checked at implementation completion
