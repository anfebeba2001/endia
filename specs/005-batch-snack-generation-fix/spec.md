# Feature Specification: Batch Snack Generation Fix

**Feature Branch**: `005-batch-snack-generation-fix`

**Created**: 2026-09-11

**Status**: Draft

**Input**: User description: "Fix the batch snack generation flow. Root causes already established: (1) `BatchService.runBatch` is async and non-transactional, loads `StudySessionItem`s detached and passes a lazy `Subtopic` proxy into `@Transactional SnackService.generateSingleSnack`, which dereferences `subtopic.getTheme().getPrompt()` outside a session → `LazyInitializationException`, so every batch snack fails and is recorded as `FAILED`/`UNAVAILABLE`; (2) nested quiz tasks are submitted to the same bounded `batchExecutor` that runs `runBatch`, which then blocks on `CompletableFuture.allOf(...).join()` and self-deadlocks, so the batch never reaches a terminal status; (3) the frontend clears `creating` before the first poll returns, flashing the 'No snacks were prepared…' empty state, and keeps polling/retrying forever while the batch is stuck `IN_PROGRESS`."

## Context & Root Causes *(established — do not re-investigate)*

This is a defect-fix feature. The following root causes are treated as confirmed inputs and are encoded as requirements below; the specification does not re-open the investigation.

1. **Detached related-data access.** The background batch worker runs asynchronously and outside an active persistence context. It loads batch items and passes a lazily-associated subtopic into the transactional snack generation step, which accesses the subtopic's theme data while no persistence session is open. The access fails, the failure is caught by a generic handler, and every batch snack is recorded as failed with cause "service unavailable". No snack is ever produced for a batch.
2. **Worker self-deadlock on a shared bounded executor.** The batch worker runs on a bounded worker pool and then submits its nested per-subtopic quiz tasks back onto that same pool and blocks until they all finish. Because the pool only grows beyond its small core size when its queue is full, the nested tasks are queued behind the blocked worker and never run. The worker waits forever, so the batch never reaches a terminal status.
3. **Premature empty-state and non-terminating polling.** The snack screen stops showing its initial loading state as soon as the batch is created, but the first poll response has not arrived and the item list is still empty, so the "No snacks were prepared…" empty state is shown briefly. Because the backend batch is stuck non-terminal, the screen also keeps polling and showing a retrying state indefinitely.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Batch Snack Generation Produces Readable Snacks (Priority: P1)

When the user confirms their selected subtopics, the batch generates a snack for each selected subtopic. Each snack's theme context is available at the moment the snack is generated and persisted, so generation does not fail because related study data cannot be read. When the language model responds successfully, the generated snacks are persisted and become visible to the user through the batch status the screen polls.

**Why this priority**: The reported defect makes *every* batch snack fail, so the core learning loop never yields content. Restoring a persisted, readable snack is the minimum viable fix and the primary value of this feature.

**Independent Test**: Can be fully tested by starting a batch for one or more subtopics and confirming that (a) no detached-related-data failure is recorded for any item, (b) at least one snack is persisted with status ready when the model responds, and (c) the ready snack is returned by the batch status the screen polls. Delivers the value of "the batch actually produces snacks."

**Acceptance Scenarios**:

1. **Given** a batch has been created for one or more selected subtopics, **When** the background generation runs, **Then** each snack is generated using theme context loaded within a valid persistence scope and no detached-related-data failure is recorded for any item.
2. **Given** the language model returns a valid snack for a subtopic, **When** generation completes, **Then** that snack is persisted with status ready and is returned for that subtopic in the batch status response.
3. **Given** a full batch run over multiple subtopics, **When** generation finishes, **Then** no detached-data-access error is logged from the batch snack path.
4. **Given** one subtopic's generation fails for a legitimate reason, **When** the remaining subtopics generate successfully, **Then** the successful snacks are still persisted and reported ready.

---

### User Story 2 — Batch Always Reaches a Terminal Status (Priority: P1)

A batch always finishes. The background worker never waits indefinitely on work that it scheduled onto itself, and nested per-subtopic work cannot starve the worker. Every batch ends in a terminal aggregate status — complete, partial, or failed — and never remains in progress with items still pending.

**Why this priority**: The self-deadlock is what makes the screen hang forever. A batch that never terminates is unusable regardless of whether individual snacks succeed. This is independently testable and delivers the value of "the screen always settles."

**Independent Test**: Can be tested by running a batch with multiple subtopics and confirming that the aggregate status reaches complete/partial/failed within a bounded time and that the worker is not blocked waiting on nested tasks. Delivers the value of "the batch is guaranteed to finish."

**Acceptance Scenarios**:

1. **Given** a batch is created, **When** the background worker runs, **Then** nested per-subtopic work runs on a worker pool/mechanism that cannot starve the batch worker, and the worker does not block on tasks that can only run after it finishes.
2. **Given** a batch is created, **When** generation completes or fails, **Then** the batch reaches a terminal aggregate status (complete, partial, or failed) and does not remain in progress with items pending.
3. **Given** a batch with many subtopics that saturates the worker pool, **When** generation runs, **Then** the batch still reaches a terminal status rather than deadlocking.
4. **Given** a batch reaches a terminal status, **When** the client polls, **Then** the terminal status is observable and stable.

---

### User Story 3 — Snack Screen Shows Progress Without Empty-State Flash and Stops Polling (Priority: P1)

While a batch is being created and generated, the snack screen shows a progress/loading state rather than the "No snacks were prepared…" empty state. The empty state is reserved for a genuinely finished batch that produced no snacks. Polling stops as soon as any terminal status is reached and does not display an infinite retrying state.

**Why this priority**: The empty-state flash and the endless retry spinner are the user-visible symptoms of the defect. Even after the backend is fixed, the flash window (between batch creation and the first poll response) and non-terminating polling remain user-visible defects. P1 because the flow must look correct to the user, not just terminate server-side.

**Independent Test**: Can be tested by starting a batch and observing the screen: no empty state appears before the first items arrive, a loading/progress state is shown instead, and polling stops within one interval of a terminal status (complete, partial, or failed) with no perpetual retry indicator. Delivers the value of "the screen looks right and always settles."

**Acceptance Scenarios**:

1. **Given** a batch has just been created and the first poll response has not yet arrived, **When** the screen renders, **Then** it shows a loading/progress state and does not show the "No snacks were prepared…" empty state.
2. **Given** the batch is still in progress, **When** the item list is momentarily empty, **Then** the empty state is suppressed and progress is shown instead.
3. **Given** the batch reaches complete, partial, or failed, **When** the client evaluates the status, **Then** polling stops within one poll interval and the retrying indicator is cleared.
4. **Given** a batch finished with no ready snacks (all items failed or the batch failed), **When** the terminal state is rendered, **Then** a terminal, user-visible outcome is shown (not a silent empty list and not an infinite spinner).

---

### User Story 4 — No Regression to Bounded Retry or Existing Generation Flows (Priority: P2)

The fix preserves the previously delivered bounded-retry behavior (at most 3 total automatic attempts per target) and does not change the single-shot snack generation or quiz generation flows. Existing behavior that users rely on continues to work exactly as before.

**Why this priority**: A defect fix must not undo prior hardening. The bounded retry prevents runaway loops and the single-shot and quiz flows are used independently of batches. P2 because it protects already-working behavior rather than adding new value.

**Independent Test**: Can be tested by running the existing bounded-retry tests, the single-shot snack generation endpoint, and quiz generation, and confirming they behave as before. Delivers the value of "the fix is surgical."

**Acceptance Scenarios**:

1. **Given** a target fails repeatedly, **When** automatic attempts run, **Then** no more than 3 total automatic attempts are made, as before.
2. **Given** the single-shot snack generation flow, **When** it is invoked, **Then** its behavior and contract are unchanged.
3. **Given** the per-subtopic quiz generation flow, **When** it is invoked, **Then** it continues to work with no regression.
4. **Given** the batch flow fails or partially fails, **When** the failure is surfaced, **Then** error messages and logs contain no sensitive data (no prompt bodies, API keys, or personal data).

---

### Edge Cases

- **All items succeed**: batch status is complete; all snacks are ready and returned.
- **All items fail**: batch status is failed; a terminal, user-visible outcome is shown (no infinite spinner, no empty-state flash during the run).
- **Mixed success and failure**: batch status is partial; ready snacks render and failed items show their typed cause, with the existing bounded retry/manual retry behavior preserved.
- **One subtopic's theme data is missing or invalid**: only that item fails; the batch still reaches a terminal status and other items are unaffected.
- **Language model is slow or stalls**: the batch still reaches a terminal status within the bounded window; no indefinite in-progress state.
- **Many subtopics saturate the worker pool**: nested per-subtopic work cannot starve the batch worker; the batch still terminates.
- **First poll response is delayed**: the screen keeps showing progress and never flashes the empty state before items arrive.
- **Batch finishes with zero ready snacks**: the empty/failure outcome is only shown once the batch is terminal, and polling has stopped.
- **Client polls after a terminal status**: no further polling is scheduled and the retrying indicator stays cleared.
- **User navigates away mid-batch**: no background polling continues for the abandoned screen, and no duplicate batch is created.
- **Duplicate/rapid user actions**: only one in-flight batch cycle per target; repeated actions do not multiply work (preserved from the bounded-retry feature).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST generate each batch snack using the subtopic and its theme data loaded within a valid persistence context — either by resolving that data before the item is detached or by loading it by identifier inside the transactional generation step — so that theme context is always available when a snack is generated and persisted.
- **FR-002**: System MUST NOT pass detached or lazily-associated related data (a subtopic and its theme) into a step that accesses it after the persistence context has closed; batch snack generation MUST not fail because related data is unavailable.
- **FR-003**: System MUST NOT log any detached/lazy related-data access failure from the batch snack path during a full batch run.
- **FR-004**: System MUST run nested per-subtopic quiz prefetch work on a worker pool or execution mechanism that cannot starve the batch worker; nested work MUST NOT be submitted only to the same bounded pool that is currently running the batch worker and then awaited.
- **FR-005**: The batch worker MUST always reach a terminal aggregate status — complete, partial, or failed — and MUST NOT block indefinitely on nested tasks.
- **FR-006**: A batch MUST reach a terminal aggregate status within a bounded time; it MUST never remain in progress with snack or quiz status pending indefinitely.
- **FR-007**: When the language model responds successfully for a subtopic, at least one snack MUST be persisted with status ready and MUST be returned for that subtopic by the batch status endpoint the client polls.
- **FR-008**: Failure of a single batch item MUST NOT prevent other items from being generated or prevent the batch from reaching a terminal status.
- **FR-009**: The snack screen MUST show a progress/loading state while a batch is being created or is in progress and the item list is still empty; it MUST NOT show the "No snacks were prepared…" empty state during that window.
- **FR-010**: The snack screen MUST NOT show the empty state before the first poll response has been processed (i.e. during the interval between batch creation succeeding and the first items arriving).
- **FR-011**: The empty state MUST only be shown when the batch has reached a terminal status and produced no ready snacks, or when an explicit terminal error has occurred.
- **FR-012**: The client's terminal-status check MUST treat every batch status other than in-progress as terminal — specifically complete, partial, and failed — and MUST NOT be limited to an allow-list of only complete/failed.
- **FR-013**: Polling MUST stop within one poll interval after a terminal batch status is observed and MUST NOT continue after a terminal status.
- **FR-014**: The polling/retry UI MUST terminate on any terminal status and MUST NOT display an infinite retrying state; the retrying indicator MUST reflect only active generation/retry activity, not the whole lifetime of an in-progress batch.
- **FR-015**: Polling MUST have a bounded maximum lifetime that is configurable (default 120 seconds) so that it terminates even if the backend never leaves in-progress, surfacing a terminal, user-visible error rather than polling indefinitely.
- **FR-016**: System MUST preserve the bounded automatic retry behavior (at most 3 total automatic attempts per target) in the snack and quiz batch flow and MUST NOT exceed that bound.
- **FR-017**: System MUST preserve the existing single-shot snack generation flow and its contract unchanged (no regression).
- **FR-018**: System MUST preserve the existing per-subtopic quiz generation flow unchanged (no regression).
- **FR-019**: Error messages and logs MUST NOT expose sensitive data (credentials, API keys, raw prompts, provider request/response payloads, or personal data).
- **FR-020**: The fix MUST respect the project's layered architecture, keeping persistence-context handling in the application/infrastructure layers and the presentation state handling in the interface layer.

### Key Entities *(include if feature involves data)*

- **StudySession / Batch**: The aggregate run tying selected subtopics to a single generation trigger. Key attributes (conceptual): selected subtopics, overall status (in-progress / complete / partial / failed). In-progress is the only non-terminal status.
- **StudySessionItem**: One subtopic's slot within a batch. Key attributes: associated subtopic, snack generation status and cause, quiz generation status and cause, generated snack reference.
- **Subtopic / Theme**: The study slice being turned into a snack and the parent theme whose context informs generation. The theme context must be available at generation time.
- **Snack**: The generated knowledge card for a subtopic. Key attributes: concept, metaphor, why-it-matters, related, generation status (pending / generating / ready / failed).
- **QuizQuestion**: The prefetched quiz content for a subtopic, generated in parallel with snacks but on a non-starving execution path.
- **Generation Target**: A single unit tracked for bounded automatic retries (a snack for one subtopic within a batch). Maximum 3 total automatic attempts per cycle, preserved from the prior feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: No detached/lazy related-data access failure is logged from the batch snack path in 100% of full batch runs.
- **SC-002**: 100% of created batches reach a terminal aggregate status (complete, partial, or failed) within a bounded time (default 5 minutes, configurable), and 0% remain in progress with items pending after that window.
- **SC-003**: When the language model responds successfully, at least one snack is persisted with status ready and is returned by the batch status response in 100% of successful-generation runs.
- **SC-004**: The snack screen shows the "No snacks were prepared…" empty state 0 times during batch creation or while a batch is in progress, and 0 times before the first poll response has been processed, across 100% of observed runs.
- **SC-005**: Polling stops within one poll interval of the batch reaching complete, partial, or failed in 100% of status-sequence tests, and no infinite retrying state is displayed.
- **SC-006**: The bounded automatic retry behavior is preserved: a target that fails every attempt makes at most 3 total automatic attempts and no 4th, verified in 100% of failing-target tests.
- **SC-007**: No regression: the single-shot snack generation flow and the per-subtopic quiz generation flow behave as before, verified by existing unit/integration tests passing unchanged.
- **SC-008**: Error messages and logs contain no sensitive data (no prompt bodies, API keys, or personal data), verified by inspection/automated checks of the failure-path output.

## Out of Scope

- Re-investigating or redesigning the established root causes; the root causes are treated as confirmed inputs.
- A general overhaul of batch scheduling, LLM provider selection, prompt content, backoff strategy, or the LLM-level retry algorithm.
- Changes to the single-shot snack generation contract or the per-subtopic quiz generation contract.
- Introducing new batch statuses; the existing status vocabulary (in-progress / complete / partial / failed) is preserved.
- Persistence/schema changes (new entities, columns, or migrations) unless a later implementation decision requires them; this spec defines behavior, not storage.
- Redesigning the snack screen UI beyond suppressing the premature empty state and terminating polling/retry.
- Reworking progressive snack delivery, drag-and-drop, dark mode, or other features from `001-batch-mvp-enhancements`.
- Defining exact worker-pool sizing numbers beyond ensuring the batch worker cannot be starved or blocked by its own nested work.

## Assumptions

- The three root causes described in the Context section are correct and confirmed; this feature fixes them rather than re-diagnosing them.
- The existing batch status vocabulary (in-progress / complete / partial / failed) and the client model already support partial; no new statuses are introduced.
- The batch status endpoint already exposes per-item snack status, snack content, and causes; this feature does not change that response shape.
- The bounded automatic retry behavior (at most 3 total automatic attempts per target, with manual retry resetting the budget) was delivered by the prior bounded-retry feature and is preserved unchanged.
- A bounded default terminal window for a batch is acceptable at 5 minutes (configurable) to cover realistic model latency plus bounded retries; the exact value is an implementation/config detail.
- The client's bounded polling window remains configurable with a default of 120 seconds, as established previously.
- Only retryable typed causes are eligible for automatic reattempt, consistent with existing LLM error semantics; non-retryable causes surface terminally.
- No authentication changes; requests remain anonymous (stub user context) as in the MVP.
- The monorepo layout and the provider abstraction remain unchanged; no new external services are introduced.
- The empty state remains the correct terminal outcome when a genuinely finished batch produced no snacks; only its premature display is the defect.
