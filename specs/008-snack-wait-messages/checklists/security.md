# Security / Privacy Requirements Quality Checklist: Accurate Step-by-Step Snack Waiting Messages

**Purpose**: Validate that the feature's sensitive-data and runtime-state requirements are complete, clear, and testable — before/while implementing `008-snack-wait-messages`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md)

**Note**: This checklist tests the *security/privacy requirements writing* for quality, not the implementation. Each item must be answerable "yes" at implementation completion.

## Sensitive Data Exposure

- [x] CHK001 Is the set of forbidden data in phase messages/logs itemized (prompts, API keys, provider request/response payloads, personal data)? [Completeness, Spec §FR-011/§SC-006]
- [x] CHK002 Is it required that the `snackPhase` wire field carries only a static enum token? [Clarity, Spec §FR-011, contracts/batch-status.md §Rules]
- [x] CHK003 Is it required that the frontend message map is static and content-free (no generated text, identifiers, or provider content)? [Completeness, plan.md §Architecture Decisions 3]
- [x] CHK004 Is the constraint applied to both user-visible messages and backend logs added by the feature? [Completeness, Spec §FR-011]
- [x] CHK005 Is it specified that the backend sends no prose (enum only), so copy changes cannot leak server data? [Clarity, research.md §R3]
- [x] CHK006 Is there a verification requirement (inspection/automated check) for the no-sensitive-data constraint? [Measurability, Spec §SC-006, tasks.md T023]

## Runtime State & Isolation Safety

- [x] CHK007 Is the phase registry required to be thread-safe (`ConcurrentHashMap`) for the `@Async` worker write / request-thread read? [Clarity, research.md §R1, data-model.md §2]
- [x] CHK008 Is it required that a phase entry can never outlive a settled item (clear-before + clear-in-`finally`)? [Completeness, Spec §FR-004, plan.md §Architecture Decisions 2]
- [x] CHK009 Is per-item isolation required so one item's phase cannot leak to another? [Coverage, Spec §FR-008, data-model.md §2]
- [x] CHK010 Is the in-memory, non-persisted nature of the phase documented (no PII at rest, lost on restart)? [Clarity, data-model.md §Persistence]
- [x] CHK011 Is the `GENERATING`-only exposure rule required as defence-in-depth against a stale phase? [Coverage, Spec §FR-004, research.md §R4]

## Scope / Attack Surface

- [x] CHK012 Is it documented that no new endpoint, request shape, or persisted field is introduced that could expand the attack surface? [Clarity, Spec §Out of Scope, plan.md §Technical Context]
- [x] CHK013 Is it stated that authentication is unchanged (anonymous stub user context) and no new auth surface is added? [Consistency, Spec §Assumptions]
- [x] CHK014 Is the single-shot snack endpoint explicitly declared unchanged? [Consistency, plan.md §Constraints]
- [x] CHK015 Is it documented that no new dependency is added (no supply-chain delta)? [Clarity, plan.md §Primary Dependencies]

## Failure-Path Safety

- [x] CHK016 Is the behavior on generation failure specified (item → failed, phase cleared, no phase message)? [Coverage, Spec §Edge Cases, §US1.5]
- [x] CHK017 Is it specified that a phase shorter than the poll interval may be skipped but never shown stale/back-dated? [Completeness, Spec §Edge Cases]
- [x] CHK018 Is the clear-on-settle contract required to survive the out-of-scope Task 9 liveness work? [Dependency, plan.md §Risks, research.md §R8]

## Notes

- Check items off as completed: `[x]`.
- Reference the spec section or task ID that satisfies each item.
- Reviewed: 2026-09-11 — all 18 items verified against the artifacts.
- All items must be checked at implementation completion (tasks.md T027).
