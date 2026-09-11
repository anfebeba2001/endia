# Security Requirements Quality Checklist: Batch Snack Generation Fix

**Purpose**: Validate that the feature's sensitive-data and abuse/resource-exhaustion requirements are complete, clear, and testable — before/while implementing `005-batch-snack-generation-fix`.
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md)

**Note**: This checklist tests the *security requirements writing* for quality, not the implementation. Each item must be answerable "yes" at implementation completion.

## Sensitive Data Exposure

- [x] CHK001 Is the set of forbidden data in messages/logs itemized (credentials, API keys, raw prompts, provider request/response payloads, personal data)? [Completeness, Spec §FR-019]
- [x] CHK002 Is it required that batch failure messages use only the typed `LlmErrorCause` vocabulary and no raw provider content? [Clarity, Spec §FR-019, data-model.md §Generation Error]
- [x] CHK003 Is the constraint applied to both user-visible messages and backend logs? [Completeness, Spec §FR-019, §SC-008]
- [x] CHK004 Is it specified that the new bounded-join timeout log contains no child exception/payload detail? [Clarity, plan.md §Architecture Decisions 2, research.md §R6]
- [x] CHK005 Is there a verification requirement (inspection/automated check) for the no-sensitive-data constraint? [Measurability, Spec §SC-008, tasks.md T020]

## Abuse / Resource-Exhaustion Resistance

- [x] CHK006 Is the retry bound (≤3 automatic attempts) preserved as a security-relevant control against runaway loops? [Coverage, Spec §FR-016, §SC-006]
- [x] CHK007 Is the bounded polling window required to prevent indefinite client-side polling? [Completeness, Spec §FR-015]
- [x] CHK008 Is the bounded quiz-join (`orTimeout`) required to prevent an indefinitely held batch worker? [Completeness, Spec §FR-006, contracts/batch-generation-behavior.md §C4]
- [x] CHK009 Is the separate `quizExecutor` with `CallerRunsPolicy` required to prevent self-deadlock/thread starvation (a denial-of-service condition)? [Coverage, Spec §FR-004, contracts/batch-generation-behavior.md §C3]
- [x] CHK010 Is the batch worker's inability to run the whole batch on the request thread (no caller-runs on `batchExecutor`) documented as a resource-safety constraint? [Clarity, contracts/batch-generation-behavior.md §C3, plan.md §Architecture Decisions 2]
- [x] CHK011 Is the worst-case thread/queue footprint bounded and documented (`batchExecutor` core=2/max=8/queue=50; `quizExecutor` core=4/max=16/queue=100)? [Clarity, data-model.md §Executors]

## Authentication / Scope

- [x] CHK012 Is it documented that authentication is unchanged (anonymous stub user context) and no new auth surface is introduced? [Consistency, Spec §Assumptions]
- [x] CHK013 Is it stated that no new endpoint, request shape, or persistence is introduced that could expand the attack surface? [Clarity, Spec §Out of Scope, plan.md §Technical Context]
- [x] CHK014 Is the existing single-shot endpoint's contract explicitly unchanged (no security-relevant behavior change)? [Consistency, Spec §FR-017]

## Failure-Path Safety

- [x] CHK015 Is it specified that a detached/lazy related-data failure is never logged from the batch snack path? [Completeness, Spec §FR-003]
- [x] CHK016 Is the behavior on a stuck/hung nested quiz task specified to terminate (bounded join) and aggregate a terminal status? [Coverage, Spec §FR-006, contracts/batch-generation-behavior.md §C4]
- [x] CHK017 Is it specified that one item's failure does not block others or leak error detail (isolation + typed causes)? [Coverage, Spec §FR-008, §FR-019]
- [x] CHK018 Is the client required to cancel the in-flight poll request when the window elapses? [Completeness, Spec §FR-015, contracts/batch-status.md §Polling window]

## Notes

- Check items off as completed: `[x]`.
- Reference the spec section or task ID that satisfies each item.
- All items must be checked at implementation completion (tasks.md T024).
