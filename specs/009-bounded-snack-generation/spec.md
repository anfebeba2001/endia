# Feature Specification: Guarantee Bounded Snack Generation

**Feature Branch**: `009-bounded-snack-generation`

**Created**: 2026-09-11

**Status**: Draft

**Input**: User description: "let's make sure there are no infinite loops possible in the way of generating snacks, not any request not being responded infinitely." (Batch backlog Task 9.)

## Context & Scope *(established — do not re-investigate)*

This is a hardening/defect-prevention feature on the backend snack/batch path. It closes the remaining liveness gaps that can leave a snack generation running forever or a request unanswered. The following behavior was **already delivered** by earlier features and is treated here as preserved, non-regression behavior — it is referenced, not re-designed:

- **002 — Bounded snack reattempts and polling termination**: automatic application reattempts are capped (3 total per cycle), and frontend status polling is bounded (default 120 seconds) and terminates on any terminal status.
- **005 — Batch snack generation fix**: batch worker cannot self-deadlock on nested work, related data is loaded in a valid persistence context, and the batch always reaches a terminal aggregate status.
- **008 — Accurate step-by-step snack waiting messages**: per-phase wait messages driven by the real backend step.

**Confirmed current code state (inputs, not to be re-investigated):**

- The outbound LLM HTTP client is already built with a connect-plus-read timeout taken from `llm.timeout-seconds` (default 30 seconds).
- `RetryingLlmProvider` retries retryable `LlmGenerationException` up to `batch.retry.max-attempts` (default 3) with backoff and jitter.
- `SnackService.generateValidSnack` calls the provider once and re-rolls **at most once** on invalid content, then throws if still invalid — there is currently **no per-subtopic wall-clock bound** around snack generation.
- `BatchService.runBatch` runs asynchronously on the batch executor, generates snacks **sequentially** with no per-item timeout, runs quizzes in parallel with a join timeout (`batch.quiz-join-timeout-seconds`, default 240), and aggregates the results. A never-returning snack provider call would block the batch worker indefinitely.
- `createBatch` registers a trigger and returns immediately; the batch status endpoint is read-only. No `batch.snack-timeout-seconds` property exists yet.
- Frontend bounded polling (default 120 seconds) and terminal-status handling exist from 002/005.

This feature adds the missing guarantees: every outbound LLM call is time-bounded, retries and re-rolls are provably finite, a single subtopic's generation cannot block the batch worker, the batch always reaches a terminal aggregate status even against a never-returning provider, and no synchronous HTTP request is left unanswered. It does **not** redesign the UI, prompts, provider selection, or the LLM retry algorithm.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Every Outbound LLM Call Is Time-Bounded (Priority: P1)

When a snack is being generated and the language model accepts the connection but never responds, the outbound call does not wait forever. Every call made while generating a snack is bounded by a configured connect-plus-read timeout, so a hung provider fails within that bound instead of holding the request or the worker open.

**Why this priority**: An unbounded outbound call is the lowest-level infinite wait and the root enabler of every other hang. Without it, no higher-level bound can be trusted. It is independently testable by pointing the snack path at a provider that accepts but never responds.

**Independent Test**: Drive a snack generation against a provider that hangs, and confirm the call fails at or before the configured timeout rather than remaining in flight. Delivers the value of "a stuck model call fails fast."

**Acceptance Scenarios**:

1. **Given** a provider call is made while generating a snack, **When** the provider never responds, **Then** the call terminates no later than the configured connect-plus-read timeout (`llm.timeout-seconds`, default 30 seconds).
2. **Given** the timeout is configured to a smaller value, **When** a hanging provider call is made, **Then** the call terminates within that smaller configured bound.
3. **Given** a test with a hanging provider call, **When** the test runs, **Then** the call cannot exceed the configured bound (the test fails if it does).

---

### User Story 2 — Retries and Re-Rolls Are Provably Finite (Priority: P1)

Automatic LLM retries stay within the configured maximum attempts, and the content re-roll inside snack generation cannot loop. A provider that fails or hangs every time produces a finite, bounded number of LLM calls and then gives up with a typed failure, rather than retrying or re-rolling forever.

**Why this priority**: Retry and re-roll loops are the second way an infinite loop can be created. Even with a per-call timeout, an unbounded number of retries/re-rolls could still run forever. This is independently testable by counting calls against an always-failing provider.

**Independent Test**: Point snack generation at a provider that fails or hangs on every call, and confirm the total number of LLM calls is finite and bounded by the configured attempt budget. Delivers the value of "failure gives up after a known, small number of tries."

**Acceptance Scenarios**:

1. **Given** a provider that fails every retryable call, **When** snack generation runs, **Then** the number of LLM calls never exceeds the configured maximum attempts (`batch.retry.max-attempts`, default 3) times the bounded re-roll allowance, and no further calls are issued.
2. **Given** a provider that returns invalid content every time, **When** the snack is validated and re-rolled, **Then** the re-roll occurs at most once and the generation fails with a typed cause instead of re-rolling again.
3. **Given** a provider that hangs every call, **When** snack generation runs, **Then** a test asserts a finite, bounded number of calls is produced and the generation terminates.
4. **Given** a failing or hanging provider, **When** the bound is reached, **Then** no additional retry, re-roll, or background call is started.

---

### User Story 3 — A Single Subtopic's Generation Fails Fast Instead of Blocking the Worker (Priority: P1)

A single subtopic's snack generation is bounded by a configurable maximum (a wall-clock timeout and/or a maximum number of attempts). When that bound is exceeded, the item is marked `FAILED` with a typed cause and the batch worker moves on; one slow or stuck subtopic cannot consume the worker or hold up the rest of the batch.

**Why this priority**: This converts a per-call bound into a per-item guarantee and is the mechanism that keeps the batch worker moving. It is independently testable by forcing one item to exceed its bound and confirming it fails while other items still progress.

**Independent Test**: Force one subtopic's generation to exceed its configured bound, and confirm that item becomes `FAILED` with a typed cause and the worker proceeds to the next item without blocking. Delivers the value of "one bad subtopic does not freeze the batch."

**Acceptance Scenarios**:

1. **Given** a subtopic whose snack generation exceeds the configured per-subtopic maximum, **When** the bound is reached, **Then** the item is marked `FAILED` with a typed cause.
2. **Given** a subtopic is marked `FAILED` for exceeding its bound, **When** the batch worker continues, **Then** the worker is not blocked and proceeds with the remaining items.
3. **Given** the per-subtopic bound is configured to a different value, **When** generation exceeds it, **Then** the item fails at that configured bound.
4. **Given** one subtopic exceeds its bound while others complete successfully, **When** the batch finishes, **Then** the successful items are preserved and the batch reflects the mix.

---

### User Story 4 — The Batch Always Reaches a Terminal Status, Even Against a Never-Returning Provider (Priority: P1)

Even if one or all snack generations hang, the batch worker always reaches a terminal aggregate status — `COMPLETE`, `PARTIAL`, or `FAILED` — within a bounded time. The batch never remains `IN_PROGRESS` with items pending, regardless of provider behavior.

**Why this priority**: This is the top-level liveness guarantee the user asked for ("no infinite loops possible"). It is the outcome that makes the screen settle and is independently testable with a never-returning provider.

**Independent Test**: Run a batch against a provider that never returns, and confirm the batch reaches `COMPLETE`/`PARTIAL`/`FAILED` within the bounded window and does not stay `IN_PROGRESS`. Delivers the value of "the batch always finishes, no matter what the model does."

**Acceptance Scenarios**:

1. **Given** a batch where a snack generation never returns, **When** the bounded window elapses, **Then** the batch reaches a terminal aggregate status (`COMPLETE`/`PARTIAL`/`FAILED`).
2. **Given** a batch where **all** snack generations never return, **When** the bounded window elapses, **Then** the batch still reaches a terminal aggregate status.
3. **Given** a batch has reached a terminal aggregate status, **When** the status is read, **Then** no snack or quiz item remains in a pending/generating state.
4. **Given** a test using a never-returning provider, **When** it runs, **Then** it asserts the batch reaches a terminal status within the bounded time.

---

### User Story 5 — No Synchronous HTTP Request Is Left Unanswered (Priority: P1)

Starting a batch returns immediately with an accepted response, and reading batch status completes without waiting on background generation. No synchronous HTTP request in the snack/batch path can be left hanging because the server is blocked generating snacks.

**Why this priority**: A request that never gets a response is exactly the "request not being responded infinitely" failure the user reported. It is independently testable at the interface boundary.

**Independent Test**: Call batch creation and batch status while generation is deliberately slow or hung, and confirm both requests return promptly. Delivers the value of "the API always answers."

**Acceptance Scenarios**:

1. **Given** a valid batch creation request, **When** it is submitted, **Then** `POST /api/batches` returns an accepted response immediately and does not wait for generation.
2. **Given** a batch is still generating (or hung), **When** `GET /api/batches/{id}` is called, **Then** the request completes promptly without waiting on background generation.
3. **Given** a provider that never returns, **When** creation and status requests are issued, **Then** neither request is left unanswered.
4. **Given** a test with a never-returning provider, **When** the HTTP calls are made, **Then** the responses arrive within the expected prompt window.

---

### User Story 6 — Bounded Polling Is Preserved (Priority: P2)

Frontend status polling remains bounded and terminates on any terminal status or on the configured timeout, surfacing a terminal error. This behavior was delivered by earlier features and must not regress.

**Why this priority**: It protects already-working behavior and ensures the client cannot reintroduce an infinite wait. P2 because the server-side bounds are the primary fix; the client bound is the last line of defense.

**Independent Test**: Return terminal and never-terminal statuses and confirm polling stops within one interval of a terminal status and at the configured timeout (default 120 seconds) otherwise. Delivers the value of "the screen always settles."

**Acceptance Scenarios**:

1. **Given** the batch reports a terminal status (`COMPLETE`/`PARTIAL`/`FAILED`), **When** the client polls, **Then** polling stops within one poll interval.
2. **Given** the batch never leaves `IN_PROGRESS`, **When** the configured polling window (default 120 seconds) elapses, **Then** polling stops and a terminal, user-visible error is shown.
3. **Given** a terminal status or timeout has been observed, **When** the client evaluates terminality, **Then** no further poll is scheduled.

---

### User Story 7 — Bounded Outcomes Are Logged With the Real Cause and Without Sensitive Data (Priority: P2)

When a timeout or failure bound is hit, the system records the bounded outcome together with the actual cause, so the failure is diagnosable. Logs and error messages never include prompt bodies, API keys, provider request/response payloads, or personal data.

**Why this priority**: Bounds without observability make failures look like silent hangs. Correct, safe logging is what lets operators distinguish "timed out" from "provider errored." P2 because it does not change the liveness guarantee itself but is required for production readiness.

**Independent Test**: Trigger each bound (call timeout, retry exhaustion, per-subtopic timeout, batch terminal) and inspect the logs for the real cause and for absence of sensitive data. Delivers the value of "we can see why it stopped, safely."

**Acceptance Scenarios**:

1. **Given** an outbound LLM call exceeds its timeout, **When** the outcome is logged, **Then** the log records the bounded timeout outcome with the actual cause.
2. **Given** a subtopic fails for exceeding its bound, **When** the failure is logged and surfaced, **Then** the typed cause is recorded.
3. **Given** any timeout/failure log or user-facing error, **When** it is inspected, **Then** it contains no prompt bodies, API keys, provider payloads, or personal data.

---

### Edge Cases

- **Provider accepts but never responds (read hang)**: the read timeout fires at the configured bound; the call fails with a typed cause.
- **Provider is unreachable (connect hang)**: the connect timeout fires at the configured bound; the call fails with a typed cause.
- **Provider fails fast, retries exhaust**: the total call count is finite and bounded; generation fails with a typed cause.
- **Provider always returns invalid content**: the re-roll happens at most once, then generation fails; no further re-roll.
- **One subtopic hangs, others are healthy**: only the hanging item hits its bound and fails; healthy items still produce snacks; the batch reaches a terminal status.
- **All subtopics hang**: every item hits its bound and fails; the batch reaches `FAILED` (or `PARTIAL` if any item had already succeeded) within the bounded time.
- **Timeout configured to zero, negative, or absurdly small**: the configuration is rejected or normalized to a safe documented default; generation does not hang or fail unpredictably.
- **Timeout fires just after a successful response**: a successful, already-received response is not discarded because of a later timeout.
- **Batch with many subtopics processed sequentially**: the aggregate bound is finite (per-item bound × item count); the batch still terminates, though it may take longer than a single item's bound.
- **Worker pool saturated**: nested work cannot starve the batch worker (preserved from 005); bounds still apply per item.
- **Persistence fails after a successful model response**: the item fails with its own typed cause and does not block the worker.
- **Batch with no subtopics**: the batch reaches a terminal status immediately without issuing LLM calls.
- **Client disconnects mid-generation**: background generation remains bounded; no request handling thread is held by generation.
- **Duplicate/rapid batch creation**: bounded by existing single-cycle guard (preserved from 002); no multiplication of background work.

## Requirements *(mandatory)*

### Functional Requirements

**Bounded outbound LLM calls**

- **FR-001**: Every outbound LLM call made while generating a snack MUST be bounded by a configured connect-plus-read timeout (`llm.timeout-seconds`, default 30 seconds), so that a provider call that never responds cannot remain in flight beyond that bound.
- **FR-002**: The connect-plus-read timeout MUST apply to both connection establishment and response reading, and MUST be configurable via properties/environment with a documented default.
- **FR-003**: A test MUST verify that a hanging provider call cannot exceed the configured bound; the call MUST fail at or before the bound rather than hanging.

**Bounded retries and re-rolls**

- **FR-004**: Automatic LLM retries MUST remain bounded by `batch.retry.max-attempts` (default 3), and no retry may be issued beyond that maximum.
- **FR-005**: The content re-roll in the snack generation step MUST NOT create an unbounded loop; it MUST remain limited to a single bounded re-roll, after which the generation fails with a typed cause.
- **FR-006**: A test MUST assert that a failing or hanging provider produces a finite, bounded number of LLM calls (no unbounded retry or re-roll loop).
- **FR-007**: The combined worst-case wall-clock cost of a single subtopic's snack generation (per-call timeout × bounded LLM attempts, plus the bounded re-roll allowance) MUST be finite and computable, and MUST NOT exceed the configured per-subtopic bound.

**Per-subtopic bound**

- **FR-008**: A single subtopic's snack generation MUST be bounded by a configurable maximum (a wall-clock timeout and/or a maximum number of attempts).
- **FR-009**: When a subtopic's snack generation exceeds its configured bound, the item MUST be marked `FAILED` with a typed cause.
- **FR-010**: Exceeding the per-subtopic bound MUST NOT block the batch worker; the worker MUST proceed with the remaining items.
- **FR-011**: The per-subtopic bound MUST be configurable via properties/environment with a documented default.

**Batch terminal guarantee**

- **FR-012**: The batch worker MUST always reach a terminal aggregate status (`COMPLETE`/`PARTIAL`/`FAILED`) within a bounded time, even if one or all snack generations hang.
- **FR-013**: A test using a never-returning provider MUST verify that the batch reaches a terminal aggregate status within the bounded time.
- **FR-014**: When a batch reaches a terminal aggregate status, no snack or quiz item may remain in a pending or generating state.

**No unanswered HTTP request**

- **FR-015**: `POST /api/batches` MUST return an accepted (202) response immediately and MUST NOT wait for background snack generation.
- **FR-016**: `GET /api/batches/{id}` MUST complete without waiting on background generation, including while generation is slow or hung.
- **FR-017**: No synchronous HTTP request in the snack/batch path may be left unanswered indefinitely.

**Preserved bounded polling (non-regression)**

- **FR-018**: Frontend status polling MUST remain bounded (default 120 seconds) and MUST terminate on any terminal status or on timeout, surfacing a terminal, user-visible error (preserved from 002/005).

**Bounded, safe logging**

- **FR-019**: Timeout and failure paths MUST log the bounded outcome together with the actual cause.
- **FR-020**: Logs and user-facing error messages on timeout/failure paths MUST NOT expose sensitive data (prompt bodies, API keys, provider request/response payloads, or personal data).

**Architecture and non-regression**

- **FR-021**: The feature MUST respect the project's layered architecture (domain/application/infrastructure/interfaces) and the LLM abstraction; bounds belong in the application/infrastructure layers and MUST NOT leak provider-specific logic into services or controllers.
- **FR-022**: The feature MUST preserve the behavior delivered by 002 (bounded automatic reattempts, bounded polling) and 005 (non-starving worker execution, terminal aggregate status, no premature empty state) as non-regression, and MUST NOT redesign the UI, prompts, provider selection, or the LLM retry algorithm.
- **FR-023**: All new bounds MUST be configurable via properties/environment with documented defaults.

### Key Entities *(include if feature involves data)*

- **LLM Call Budget**: The finite bound on a single outbound model call — connect timeout plus read timeout, configured with a documented default. Applies to every call in the snack path.
- **Retry/Re-Roll Budget**: The finite bound on automatic retries (configured maximum attempts, default 3) and on the content re-roll (at most one), which together cap the total number of LLM calls per subtopic.
- **Per-Subtopic Snack Bound**: The configurable maximum (wall-clock timeout and/or maximum attempts) applied to generating one subtopic's snack; exceeding it marks the item `FAILED` with a typed cause.
- **Batch Worker**: The background execution unit that generates snacks; it must never block indefinitely and must always reach a terminal aggregate status.
- **Batch Aggregate Status**: The terminal state of a batch — `COMPLETE`, `PARTIAL`, or `FAILED`. `IN_PROGRESS` is the only non-terminal state (preserved from 002/005).
- **Typed Failure Cause**: A categorized, user-safe reason an item failed (for example, timeout, provider error, invalid content). Carries no sensitive data.
- **Timeout Configuration**: The externally configurable values with documented defaults — `llm.timeout-seconds` (default 30), `batch.retry.max-attempts` (default 3), and the per-subtopic snack bound.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of hanging provider calls terminate at or before the configured connect-plus-read timeout; 0% remain in flight beyond it (verified by a test with a hanging provider).
- **SC-002**: A failing or hanging provider produces a finite, bounded number of LLM calls in 100% of tests, with 0 unbounded retry/re-roll loops.
- **SC-003**: A subtopic that exceeds its configured bound is marked `FAILED` with a typed cause in 100% of cases, and the batch worker proceeds without blocking.
- **SC-004**: 100% of batches run against a never-returning provider reach a terminal aggregate status (`COMPLETE`/`PARTIAL`/`FAILED`) within the bounded time; 0% remain `IN_PROGRESS` with items pending.
- **SC-005**: `POST /api/batches` returns 202 immediately and `GET /api/batches/{id}` completes without waiting on background generation in 100% of calls, including against a never-returning provider.
- **SC-006**: Frontend polling terminates within one poll interval of any terminal status, or at the configured polling window (default 120 seconds) with a terminal user-visible error, in 100% of tests.
- **SC-007**: 100% of timeout/failure logs record the actual bounded cause and 0% contain prompt bodies, API keys, provider payloads, or personal data.
- **SC-008**: 0 tests observe an unbounded loop or an indefinitely unanswered request in the snack/batch path.
- **SC-009**: No regression: the 002 and 005 behavior (bounded reattempts, bounded polling, terminal aggregate status, non-starving worker execution) passes existing tests unchanged.
- **SC-010**: Every new bound is configurable and has a documented default, verified by configuration inspection and a test that changes a bound and observes the changed behavior.

## Out of Scope

- Redesigning the snack/batch UI, wait messages, or empty-state behavior (008 and 005 behavior is preserved).
- Changing prompt content, model/provider selection, validation rules, or the LLM-level retry algorithm (`RetryingLlmProvider` semantics are preserved, not redesigned).
- Changing the single-shot snack generation contract or the per-subtopic quiz generation contract.
- Introducing new batch statuses; the existing vocabulary (`IN_PROGRESS`/`COMPLETE`/`PARTIAL`/`FAILED`) is preserved.
- Persistence/schema changes (new entities, columns, or migrations) unless a later implementation decision requires them; this spec defines behavior, not storage.
- Defining exact numeric defaults beyond the documented ones; the per-subtopic bound value is an implementation/config decision as long as it is finite and configurable.
- General observability overhaul (metrics, tracing, dashboards) beyond the bounded-outcome logging required here.
- Authentication changes; requests remain anonymous (stub user context) as in the MVP.

## Assumptions

- The per-call HTTP timeout and the LLM retry mechanism already exist (confirmed inputs); this feature adds the per-subtopic bound, the finite-call guarantee, and the corresponding tests, and verifies the existing bounds rather than replacing them.
- The per-subtopic snack bound is a wall-clock timeout, a maximum-attempts cap, or both; a timeout is the primary mechanism because a hanging provider cannot be stopped by an attempt counter alone.
- The default per-subtopic snack bound is 240 seconds (matching the existing quiz join timeout and large enough to cover the finite LLM worst case of bounded attempts × per-call timeout plus one re-roll); it is configurable and the exact value is an implementation decision. This is an informed default, not a hard requirement.
- The per-call timeout default remains 30 seconds and the retry maximum remains 3, consistent with the confirmed current configuration.
- "Bounded time" for a batch means a finite function of the configured per-subtopic bound and the number of selected subtopics, since snacks are currently generated sequentially; the batch is guaranteed to terminate even though a large batch may take longer than a single item's bound.
- The frontend bounded polling window remains configurable with a default of 120 seconds, as established by 002/005.
- The batch status vocabulary and response shape already support terminal aggregate status and per-item failure causes; no new statuses or response fields are required for this feature.
- Only retryable typed causes are eligible for automatic reattempt, consistent with existing LLM error semantics; non-retryable causes surface terminally.
- The monorepo layout and the provider abstraction remain unchanged; no new external services are introduced.
- Logging uses the existing logging infrastructure; adding bounded-outcome log lines is sufficient and no new observability stack is required.

## Clarifications

- **Per-subtopic bound mechanism (resolved)**: The bound is a configurable maximum combining a wall-clock timeout and/or a maximum number of attempts, with a timeout as the primary mechanism because a never-returning provider cannot be interrupted by an attempt counter alone. See FR-008 and FR-011.
- **Default per-subtopic bound (resolved by informed guess)**: A default of 240 seconds is proposed, chosen to cover the finite LLM worst case and to align with the existing quiz join timeout. It is configurable; the exact value may be adjusted in planning. See Assumptions.
- **Relationship to existing bounds (resolved)**: The existing per-call timeout and retry maximum are preserved and verified; this feature does not replace or duplicate them. It adds the per-subtopic wall-clock bound and the finite-call-count guarantee. See FR-004, FR-007, and FR-022.
