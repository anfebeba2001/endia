# Feature Specification: Accurate Step-by-Step Snack Waiting Messages

**Feature Branch**: `008-snack-wait-messages`

**Created**: 2026-09-11

**Status**: Implemented

**Input**: User description: "The wait for snacks feels eternal. Add waiting messages such as 'waiting for llm response' / 'assembling response', but the messages must be ACCURATE for every real step — not generic/fake messages."

## Context & Scope *(established — do not re-investigate)*

Batch snack generation runs in the background while the snack screen polls the batch status. Today the screen shows a single generic "Preparing your snack…" message for the whole in-progress window, regardless of what the backend is actually doing. This feature replaces that single message with a distinct, accurate message per real generation phase, driven by the actual backend phase rather than a timer.

The real snack steps, in order, are: (1) resolving the subtopic and its theme context, (2) waiting for the language model response, (3) assembling and validating the returned snack (with at most one re-roll), and (4) persisting the snack and updating the item status.

This is Batch backlog Task 8 only. The separate liveness/termination concern ("no infinite loops / no request left unanswered", Task 9) is **out of scope**; this feature assumes the batch already reaches a terminal status and does not change that behavior.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — The User Sees What Is Actually Happening While Waiting (Priority: P1)

While a snack is being generated, the snack screen shows a distinct, plain-language message for the step the snack is currently in — for example, resolving the topic context, waiting for the AI response, assembling the snack, or saving it — instead of one generic "Preparing your snack…" message for the entire wait. The message changes as the backend advances through the real steps, so the user can tell the wait is progressing and what it is waiting on.

**Why this priority**: The reported problem is that the wait feels eternal and uninformative. Showing accurate per-step progress is the entire value of this feature. It is independently testable by observing the in-progress item's message change across a real run.

**Independent Test**: Start a batch, observe the in-progress item's message, and confirm it reflects distinct phases over the run and never displays a message for a phase the backend is not currently in. Delivers the value of "I can see what is happening."

**Acceptance Scenarios**:

1. **Given** a snack is being generated, **When** the item is in the context-resolution phase, **Then** the screen shows the context-resolution message (not the generic "Preparing your snack…").
2. **Given** the item advances to waiting for the AI response, **When** the status is polled, **Then** the screen shows the AI-response waiting message.
3. **Given** the item advances to assembling/validating the response, **When** the status is polled, **Then** the screen shows the assembling message.
4. **Given** the item advances to persisting the snack, **When** the status is polled, **Then** the screen shows the saving message.
5. **Given** the item becomes ready or failed, **When** the terminal item status is rendered, **Then** no in-progress phase message is shown for that item.

---

### User Story 2 — The Phase Reflects the Real Code Point, Not a Timer (Priority: P1)

The phase reported for each item is advanced at the actual moment the generation code enters each real step. The message is therefore always truthful: the screen can only ever show the phase the item is genuinely in, and never a fabricated or time-based approximation.

**Why this priority**: The user explicitly requires messages to be accurate for every real step, not generic or fake. Without a real backend phase, accurate messaging is impossible. This is the enabling half of the feature and is independently testable at the backend contract level.

**Independent Test**: Drive a generation and assert that the reported phase changes exactly when each real step is entered (context resolved, model call issued, response validated, snack persisted), and that no phase is advanced by elapsed time. Delivers the value of "the message is never a lie."

**Acceptance Scenarios**:

1. **Given** generation begins for an item, **When** the subtopic and theme context are being resolved, **Then** the item's reported phase is context resolution.
2. **Given** the model call is issued, **When** the item is awaiting the model, **Then** the item's reported phase is the model-waiting phase.
3. **Given** the model response is being validated/assembled (including a re-roll), **When** the item is in that step, **Then** the item's reported phase is assembling/validating.
4. **Given** the snack is being persisted and the item status updated, **When** the item is in that step, **Then** the item's reported phase is persisting.
5. **Given** the batch status is polled for an item that is ready or failed, **When** the response is built, **Then** the item exposes no active phase (the phase is cleared/ignored).

---

### User Story 3 — No Regression to Ready, Failed, Empty-State, Polling, or Privacy Behavior (Priority: P2)

Accurate waiting messages are added without changing any previously delivered behavior. Ready snacks render as before, failed items still show their cause and retry affordance, the empty state is still gated on a terminal batch, bounded polling from the prior features is unchanged, and no phase message or log exposes prompts, API keys, provider payloads, or personal data.

**Why this priority**: The feature must be surgical. The prior batch/status/polling behavior is relied upon and must not be disturbed. P2 because it protects existing behavior rather than adding new value.

**Independent Test**: Run the existing batch, retry, empty-state, and polling tests unchanged, and inspect phase messages/logs for sensitive content. Delivers the value of "only the waiting text changed."

**Acceptance Scenarios**:

1. **Given** a snack is ready, **When** the item is rendered, **Then** the ready snack is shown exactly as before with no phase message.
2. **Given** an item failed, **When** it is rendered, **Then** its cause and retry affordance are shown as before.
3. **Given** a genuinely terminal batch with no ready snacks, **When** the empty state is evaluated, **Then** the empty state still appears only under the prior gating rules.
4. **Given** an in-progress batch, **When** polling runs, **Then** the polling interval, bounded window, and terminal-stop behavior are unchanged.
5. **Given** any phase message or generation log, **When** it is inspected, **Then** it contains no prompts, API keys, provider request/response payloads, or personal data.

---

### Edge Cases

- **A phase is shorter than the poll interval**: the screen may skip a phase and jump to the next; it must never display a stale phase once a newer phase has been reported.
- **The item settles between polls**: once ready or failed, no phase message is shown for that item.
- **Multiple items in one batch**: each item carries its own phase; one item's phase must not be applied to another item.
- **A manual or automatic retry starts a new cycle**: the retried item re-enters the phases from the start and shows them again.
- **The model call is slow**: the AI-response waiting message remains visible for the whole wait (no timeout-driven message change).
- **Generation fails during a phase**: the item transitions to failed and the phase is cleared; the failure cause and retry affordance take over.
- **Single-shot snack generation is used**: it has no batch phase and is unaffected.
- **A batch is terminal**: no item exposes an active phase.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose, per in-progress batch item, a phase reflecting the REAL snack generation step the item is currently in — at minimum: resolving context, awaiting the language model response, assembling/validating the response, and persisting.
- **FR-002**: The system MUST advance each item's phase at the actual code point where each real step begins, and MUST NOT advance a phase on a timer, on elapsed time, or on a poll count.
- **FR-003**: The batch status response the snack screen polls MUST include the current phase for each item.
- **FR-004**: For an item that is ready or failed, the phase MUST be cleared or ignored; no in-progress phase may be reported for a settled item.
- **FR-005**: The snack screen MUST render a distinct, plain-language message for each phase, replacing the single generic in-progress message for that item.
- **FR-006**: The displayed message MUST correspond to the item's actual current backend phase; the screen MUST NOT invent, infer, or time-schedule a phase, and MUST NOT show a phase the backend did not report.
- **FR-007**: The displayed message MUST update as the backend phase changes during a real run.
- **FR-008**: Each item's phase and message MUST be tracked per item; one item's phase MUST NOT be shown for another item.
- **FR-009**: When an item is retried (automatic or manual), its phases MUST be shown again for the new cycle.
- **FR-010**: Ready snacks, failed items with cause and retry affordance, empty-state gating, and the bounded polling interval/window/terminal-stop behavior MUST remain unchanged (no regression).
- **FR-011**: Phase messages and generation logs MUST NOT expose prompts, API keys, provider request/response payloads, or personal data.
- **FR-012**: The feature MUST respect the project's layered architecture, keeping the phase progression in the application/domain layers and the message presentation in the interface layer.

### Key Entities *(include if feature involves data)*

- **Snack Generation Phase**: The real step an in-progress snack is in — resolving context, awaiting model response, assembling/validating, persisting. Associated with a single batch item; absent once the item is ready or failed.
- **Batch Item**: One subtopic's slot within a batch. Holds its own snack status, failure cause, and (while in progress) its current phase.
- **Batch Status Response**: The payload the snack screen polls. Carries each item's status, phase, snack, and cause.
- **Wait Message**: The user-facing text mapped from a phase. Distinct per phase; user-safe; never derived from a timer.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For an in-progress item, 100% of observed messages correspond to the item's actual current backend phase, with 0 occurrences of a message for a phase the backend is not in.
- **SC-002**: The single generic in-progress message is replaced: 0 runs show the same single message for every phase; at least the distinct context, model-waiting, assembling, and persisting messages are observable across a driven run.
- **SC-003**: Phase changes are observed to follow real step transitions, with 0 phase changes attributable to elapsed time or poll count.
- **SC-004**: Once an item is ready or failed, 0 in-progress phase messages are displayed for that item.
- **SC-005**: No regression: existing batch status, retry, empty-state, and bounded-polling tests pass unchanged.
- **SC-006**: 0 phase messages or logs contain prompts, API keys, provider payloads, or personal data, verified by inspection/automated checks.

## Assumptions

- The batch already reaches a terminal status and the snack screen already polls the batch status on a bounded interval; this feature only adds the phase to that flow. The separate liveness/termination work (Task 9) is a dependency and is out of scope.
- The real steps to surface are the four named in FR-001; if implementation reveals additional meaningful steps, they may be added as further phases without changing the "no timer" rule.
- Some phases (especially persisting) may be shorter than the poll interval, so a poll may skip a phase; showing the latest reported phase is acceptable and skipping is not a defect.
- Phase progression is tracked per batch item; a single-shot snack generation has no batch phase and is unchanged.
- Phase names/messages are static, user-safe labels and carry no generated content.
- No authentication changes; requests remain anonymous (stub user context) as in the MVP.
- The batch status response shape may gain a per-item phase field; existing fields and their meaning are preserved.

## Out of Scope

- Liveness/termination guarantees ("no infinite loops / no request left unanswered", Task 9).
- Redesigning the snack screen UI beyond replacing the in-progress waiting text with phase-specific text.
- Changing ready/failed rendering, the empty state, retry behavior, or the polling interval/window/terminal-stop rules.
- Changing prompt content, provider selection, model choice, validation rules, or persistence semantics.
- Progress percentages, estimated time remaining, or any timer/estimated-progress indicator.
- The single-shot snack generation contract.
