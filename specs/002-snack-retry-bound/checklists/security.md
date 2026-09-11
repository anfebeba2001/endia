# Security Requirements Quality Checklist: Bounded Snack Reattempts and Polling Termination

**Purpose**: Validate that the feature's sensitive-data and abuse-resistance requirements are complete, clear, and testable — before/while implementing `002-snack-retry-bound`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md)

**Note**: This checklist tests the *security requirements writing* for quality, not the implementation. Each item must be answerable "yes" at implementation completion.

## Sensitive Data Exposure

- [ ] CHK001 Is the set of forbidden data in messages/logs itemized (credentials, API keys, raw prompts, provider request/response payloads, personal data)? [Completeness, Spec §FR-015]
- [ ] CHK002 Is it required that terminal error messages use only the typed `LlmErrorCause` vocabulary and no raw provider content? [Clarity, Spec §FR-015, data-model.md §Generation Error]
- [ ] CHK003 Is the constraint applied to both user-visible messages and logs? [Completeness, Spec §FR-015, §SC-008]
- [ ] CHK004 Is there a verification requirement (inspection/automated check) for the no-sensitive-data constraint? [Measurability, Spec §SC-008, tasks.md T032]
- [ ] CHK005 Is it specified that no new logging of request/response payloads is introduced by this feature? [Clarity, plan.md §Mapping FR-015]

## Abuse / Resource-Exhaustion Resistance

- [ ] CHK006 Is the retry bound defined as a security-relevant control against resource exhaustion (unbounded loop)? [Coverage, Spec §US1, §FR-002]
- [ ] CHK007 Is the in-flight guard required to prevent duplicate/repeated invocation from multiplying requests? [Completeness, Spec §FR-007, §US3.5]
- [ ] CHK008 Is the bounded polling window required to prevent indefinite client-side polling? [Completeness, Spec §FR-010]
- [ ] CHK009 Is it required that no automatic attempt is triggered without an explicit failure signal (no blind resource use)? [Completeness, Spec §FR-003, §FR-004]
- [ ] CHK010 Is the worst-case request volume bounded and documented (3 cycles × up to 3 internal LLM tries, only 3 user-visible)? [Clarity, Spec §FR-014, §Assumptions]

## Authentication / Scope

- [ ] CHK011 Is it documented that authentication is unchanged (anonymous stub user context) and no new auth surface is introduced? [Consistency, Spec §Assumptions]
- [ ] CHK012 Is it stated that no new endpoint, request shape, or persistence is introduced that could expand the attack surface? [Clarity, Spec §Out of Scope, plan.md §Technical Context]
- [ ] CHK013 Is the existing single-shot endpoint's contract explicitly unchanged (no security-relevant behavior change)? [Consistency, Spec §FR-012]

## Failure-Path Safety

- [ ] CHK014 Are requirements defined for the client to cancel the in-flight poll request when the window elapses? [Gap, Exception Flow, contracts/batch-status.md §Polling window]
- [ ] CHK015 Is the behavior on a failed batch-creation request specified to be bounded and non-leaking? [Coverage, Spec §FR-003 scenario 3, §FR-015]
- [ ] CHK016 Is it specified that a non-retryable cause is surfaced terminally without repeated attempts (no forced retry of a hard failure)? [Coverage, Spec §Edge Cases, §Assumptions]

## Notes

- Check items off as completed: `[x]`.
- Reference the spec section or task ID that satisfies each item.
- All items must be checked at implementation completion (tasks.md T037).
