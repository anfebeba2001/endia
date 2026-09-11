# Behavioral Contract: Batch Generation Execution (Backend)

**Scope**: The backend batch worker (`BatchService.runBatch`), the transactional snack generation (`SnackService`), and the executor configuration (`LlmConfig`). This is a **backend-behavioral** contract — it constrains persistence scope and execution isolation; it does not change any HTTP request/response shape.

**Feature**: `005-batch-snack-generation-fix` (US1/US2, FR-001..FR-008, FR-016..FR-020).

---

## C1. Persistence scope for batch snack generation (FR-001 / FR-002 / FR-003)

- Each batch snack MUST be generated from a `Subtopic` and its `Theme` resolved **inside a valid persistence context**.
- The worker MUST NOT pass a detached `Subtopic` (or any lazily-associated related entity) into a step that dereferences it after the persistence context has closed.
- Implementation: `BatchService.runBatch` reads only the subtopic **id** from the item and calls `@Transactional SnackService.generateSingleSnack(UUID)`, which fetches the subtopic (and lazily its theme) within the transaction before generating.
- A full batch run MUST NOT log a detached/lazy related-data access failure (`LazyInitializationException`) from the batch snack path.
- A failure of one item MUST NOT prevent the remaining items from generating (the per-item `try`/`catch` is preserved).

## C2. Single-shot path preserved (FR-017)

- `SnackService.generateSingleSnack(Subtopic)` and `generateSingleSnack(Subtopic, Consumer)` MUST remain available with unchanged behavior and contract.
- All overloads MUST share the same generation/validation logic; the batch-only by-id overload is additive.

## C3. Executor isolation for nested work (FR-004 / FR-005)

- Nested per-subtopic quiz prefetch work MUST run on a worker pool that is **distinct** from the pool running the batch worker.
- The batch worker MUST NOT submit nested work only to the pool it occupies and then await it.
- Implementation: `quizExecutor` (`quiz-worker-`) runs the nested `CompletableFuture` quiz tasks; `batchExecutor` (`batch-worker-`) runs only top-level `runBatch` workers.
- `quizExecutor` MUST apply `CallerRunsPolicy` so that under saturation the submitting batch worker executes the task itself and can always make progress.
- `batchExecutor` MUST NOT apply a caller-runs fallback: it is invoked from `createBatch`'s `@Transactional` method, and running the batch on the request thread would execute the whole LLM sequence inside the create transaction.

## C4. Guaranteed terminal aggregate status (FR-005 / FR-006)

- Every batch MUST reach a terminal aggregate status (`COMPLETE`, `PARTIAL`, or `FAILED`); it MUST NOT remain `IN_PROGRESS` with items pending indefinitely.
- The worker MUST NOT block indefinitely on nested tasks. The quiz join MUST be bounded (configurable, default 240 s) so that even a hung child cannot prevent aggregation; on timeout the worker proceeds to aggregate the current item states.
- The overall terminal time is naturally bounded by the per-call LLM timeout multiplied by the bounded retry count across the sequentially-generated snacks.

## C5. Successful generation is persisted and observable (FR-007 / FR-008)

- When the model returns a valid snack, the snack MUST be persisted with `snackStatus = READY` and returned for that subtopic by `GET /api/batches/{id}`.
- Mixed outcomes MUST aggregate to `PARTIAL`; all-ready to `COMPLETE`; none-ready to `FAILED`.

## C6. Preserved surfaces (FR-016 / FR-018 / FR-019 / FR-020)

- `POST /api/batches`, `POST /api/snacks/generate`, all DTO shapes, `RetryingLlmProvider`, the LLM port/prompts, `QuizService`, `QuizQualityGate`, and `SnackRetryService` MUST be unchanged.
- The bounded automatic retry behavior (≤3 cycles per target) is preserved unchanged (`002`).
- Error messages and logs MUST NOT expose sensitive data (credentials, API keys, raw prompts, provider request/response payloads, personal data).
- Persistence-scope handling MUST remain in the application layer; executor configuration in the infrastructure layer; presentation state in the interface layer. No framework leakage into the domain.

---

## Concurrency note

The cyclic wait is structurally present whenever all `batchExecutor` core threads are held by batch workers (e.g. ≥2 concurrent batches). A single batch can still complete because the second core thread drains the queue. `005` removes the cycle unconditionally by separating the pools; the regression test must exercise concurrent batches (or otherwise saturate the core pool) to be a meaningful guard.
