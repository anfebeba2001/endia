# Security Requirements Quality Checklist: Guarantee Bounded Snack Generation

**Purpose**: Validate that the feature's sensitive-data and abuse/resource-exhaustion requirements are complete, clear, and testable — before/while implementing `009-bounded-snack-generation`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md)

**Note**: This checklist tests the *security requirements writing* for quality, not the implementation. Each item must be answerable "yes" at implementation completion.

## Sensitive Data Exposure

- [ ] CHK001 Is the set of forbidden data in messages/logs itemized (prompt bodies, API keys, provider request/response payloads, personal data)? [Completeness, Spec §FR-020]
- [ ] CHK002 Is it required that timeout/failure messages and logs use only the typed `LlmErrorCause` vocabulary and ids? [Clarity, Spec §FR-019, data-model.md §Typed Failure Cause]
- [ ] CHK003 Is the constraint applied to both user-visible messages and backend logs? [Completeness, Spec §FR-020, §SC-007]
- [ ] CHK004 Is it specified that the new per-subtopic bound log names only the subtopic id, the bound, and the cause — no payload? [Clarity, plan.md §Architecture Decisions 1, research.md §R9]
- [ ] CHK005 Is it specified that the quiz-join outcome log carries no child exception/payload detail? [Clarity, plan.md §Architecture Decisions 2, research.md §R9]
- [ ] CHK006 Is there a verification requirement (inspection) for the no-sensitive-data constraint? [Measurability, Spec §SC-007, tasks.md T028]

## Abuse / Resource-Exhaustion Resistance

- [ ] CHK007 Is the per-call HTTP timeout required as a control against a hung outbound connection? [Completeness, Spec §FR-001]
- [ ] CHK008 Is the retry bound (≤ `batch.retry.max-attempts`, default 3) preserved as a control against runaway calls? [Coverage, Spec §FR-004, §SC-002]
- [ ] CHK009 Is the single re-roll bound required to prevent an unbounded generation loop? [Completeness, Spec §FR-005]
- [ ] CHK010 Is the per-subtopic wall-clock bound required to prevent an indefinitely held batch worker? [Completeness, Spec §FR-008/§FR-010]
- [ ] CHK011 Is the `snackExecutor` required to be bounded (core/max/queue), daemon, and non-caller-runs to prevent unbounded thread/queue growth and JVM-shutdown blocking? [Coverage, Spec §FR-008, plan.md §Architecture Decisions 1, tasks.md T013]
- [ ] CHK012 Is the worst-case thread/queue footprint bounded and documented for all three pools? [Clarity, data-model.md §Executors]
- [ ] CHK013 Is the bounded polling window required to prevent indefinite client-side polling? [Completeness, Spec §FR-018]
- [ ] CHK014 Is the empty-batch terminal guard required so a degenerate request cannot leave a batch non-terminal? [Coverage, Spec §Edge Cases, contracts/snack-generation-bounds.md §B5]

## Authentication / Scope

- [ ] CHK015 Is it documented that authentication is unchanged (anonymous stub user context) and no new auth surface is introduced? [Consistency, Spec §Assumptions]
- [ ] CHK016 Is it stated that no new endpoint, request shape, or persistence is introduced that could expand the attack surface? [Clarity, Spec §Out of Scope, plan.md §Technical Context]
- [ ] CHK017 Is the single-shot `POST /api/snacks/generate` contract explicitly unchanged? [Consistency, Spec §Out of Scope, contracts/snack-generation-bounds.md §Frozen surfaces]

## Failure-Path Safety

- [ ] CHK018 Is the behavior on a hung snack provider specified to mark the item `FAILED`/`TIMEOUT` and let the worker proceed? [Coverage, Spec §FR-009/§FR-010]
- [ ] CHK019 Is the behavior on a stuck quiz task specified to finalize the item and aggregate a terminal status? [Coverage, Spec §FR-012/§FR-014]
- [ ] CHK020 Is it specified that one item's failure does not block others or leak error detail (isolation + typed causes)? [Coverage, Spec §FR-010, §FR-020]
- [ ] CHK021 Is the known `orTimeout`-does-not-interrupt limitation documented with its bounded-resource mitigation? [Clarity, plan.md §Risks, contracts/snack-generation-bounds.md §Known limitation]

## Notes

- Check items off as completed: `[x]`.
- Reference the spec section or task ID that satisfies each item.
- All items must be checked at implementation completion (tasks.md T032).
