package com.endia.application.service;

import com.endia.application.port.SnackPhaseListener;
import com.endia.domain.enums.BatchStatus;
import com.endia.domain.enums.GenerationStatus;
import com.endia.domain.enums.LlmErrorCause;
import com.endia.domain.enums.SnackGenerationPhase;
import com.endia.domain.exception.EntityNotFoundException;
import com.endia.domain.exception.LlmGenerationException;
import com.endia.domain.model.QuizQuestion;
import com.endia.domain.model.Snack;
import com.endia.domain.model.StudySession;
import com.endia.domain.model.StudySessionItem;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaQuizQuestionRepository;
import com.endia.infrastructure.persistence.JpaStudySessionItemRepository;
import com.endia.infrastructure.persistence.JpaStudySessionRepository;
import com.endia.infrastructure.persistence.JpaSubtopicRepository;
import com.endia.infrastructure.persistence.JpaThemeRepository;
import com.endia.infrastructure.tracking.SnackPhaseRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Orchestrates the batch flow (FR-001..004): one request persists a {@link StudySession}
 * and returns immediately; a background {@code @Async} worker then generates each
 * subtopic's snack in sequence and its quiz in parallel, updating per-item status.
 */
@Service
public class BatchService {

    private static final Logger log = LoggerFactory.getLogger(BatchService.class);

    /** Documented default for the per-subtopic snack wall-clock bound (009, FR-011). */
    static final int DEFAULT_SNACK_TIMEOUT_SECONDS = 240;

    private final JpaThemeRepository themeRepository;
    private final JpaSubtopicRepository subtopicRepository;
    private final JpaStudySessionRepository studySessionRepository;
    private final JpaStudySessionItemRepository itemRepository;
    private final JpaQuizQuestionRepository quizQuestionRepository;
    private final SnackService snackService;
    private final QuizService quizService;
    private final SnackPhaseRegistry phaseRegistry;
    private final TaskExecutor batchExecutor;
    private final TaskExecutor quizExecutor;
    private final TaskExecutor snackExecutor;
    private final int quizJoinTimeoutSeconds;
    private final int snackTimeoutSeconds;
    private final BatchService self;

    public BatchService(JpaThemeRepository themeRepository,
                        JpaSubtopicRepository subtopicRepository,
                        JpaStudySessionRepository studySessionRepository,
                        JpaStudySessionItemRepository itemRepository,
                        JpaQuizQuestionRepository quizQuestionRepository,
                        SnackService snackService,
                        QuizService quizService,
                        SnackPhaseRegistry phaseRegistry,
                        @Qualifier("batchExecutor") TaskExecutor batchExecutor,
                        @Qualifier("quizExecutor") TaskExecutor quizExecutor,
                        @Qualifier("snackExecutor") TaskExecutor snackExecutor,
                        @Value("${batch.quiz-join-timeout-seconds:240}") int quizJoinTimeoutSeconds,
                        @Value("${batch.snack-timeout-seconds:240}") int snackTimeoutSeconds,
                        @Lazy BatchService self) {
        this.themeRepository = themeRepository;
        this.subtopicRepository = subtopicRepository;
        this.studySessionRepository = studySessionRepository;
        this.itemRepository = itemRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.snackService = snackService;
        this.quizService = quizService;
        this.phaseRegistry = phaseRegistry;
        this.batchExecutor = batchExecutor;
        this.quizExecutor = quizExecutor;
        this.snackExecutor = snackExecutor;
        this.quizJoinTimeoutSeconds = quizJoinTimeoutSeconds;
        // A non-positive bound must never hang or fail unpredictably: normalize to the default.
        this.snackTimeoutSeconds = snackTimeoutSeconds > 0
                ? snackTimeoutSeconds
                : DEFAULT_SNACK_TIMEOUT_SECONDS;
        this.self = self;
    }

    /**
     * Persists the batch and returns immediately (202); generation happens in the background.
     */
    @Transactional
    public BatchCreateResult createBatch(UUID themeId, List<UUID> subtopicIds) {
        Theme theme = themeRepository.findById(themeId)
                .orElseThrow(() -> new EntityNotFoundException("Theme", themeId));

        StudySession session = new StudySession(theme);
        List<BatchItemCreate> items = new ArrayList<>();
        for (UUID subtopicId : subtopicIds) {
            Subtopic subtopic = subtopicRepository.findById(subtopicId)
                    .orElseThrow(() -> new EntityNotFoundException("Subtopic", subtopicId));
            StudySessionItem item = new StudySessionItem(session, subtopic);
            session.addItem(item);
            items.add(new BatchItemCreate(subtopicId, GenerationStatus.PENDING, GenerationStatus.PENDING));
        }
        studySessionRepository.save(session);

        // FR-005/FR-006: trigger the async worker only AFTER this transaction commits.
        // Otherwise the worker can start on a warm executor before the creating transaction
        // commits, read zero items, and leave the batch stuck IN_PROGRESS with PENDING items.
        UUID sessionId = session.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    self.runBatch(sessionId);
                }
            });
        } else {
            self.runBatch(sessionId);
        }

        return new BatchCreateResult(sessionId, themeId, BatchStatus.IN_PROGRESS, items);
    }

    /**
     * Background worker: snacks sequenced, quizzes in parallel per subtopic, then aggregate status.
     */
    @Async("batchExecutor")
    public void runBatch(UUID sessionId) {
        List<StudySessionItem> items = itemRepository.findBySessionId(sessionId);

        // Snacks sequentially (one READY snack is never blocked by a later one).
        for (StudySessionItem item : items) {
            item.setSnackStatus(GenerationStatus.GENERATING);
            itemRepository.save(item);
            // FR-001/FR-002: only the id crosses the boundary; the subtopic (and its lazy
            // theme) are resolved inside the transactional by-id overload, never detached.
            UUID subtopicId = item.getSubtopic().getId();
            // FR-004/FR-009: never expose a stale phase — clear before the new cycle starts.
            phaseRegistry.clear(subtopicId);
            try {
                SnackPhaseListener listener = phase -> phaseRegistry.update(subtopicId, phase);
                Snack snack = generateSnackBounded(subtopicId, listener);
                item.setSnack(snack);
                item.setSnackStatus(GenerationStatus.READY);
                item.setSnackCause(null);
            } catch (LlmGenerationException e) {
                item.setSnackStatus(GenerationStatus.FAILED);
                item.setSnackCause(e.getCauseType());
            } catch (Exception e) {
                log.warn("Batch snack generation failed for subtopic {}", item.getSubtopic().getId(), e);
                item.setSnackStatus(GenerationStatus.FAILED);
                item.setSnackCause(LlmErrorCause.UNAVAILABLE);
            } finally {
                // FR-004: the phase can never outlive the item, on success or failure.
                phaseRegistry.clear(subtopicId);
            }
            itemRepository.save(item);
        }

        // Quizzes in parallel on a separate pool so the batch worker can never be starved
        // by its own children (FR-004). The join is bounded so aggregation always runs (FR-005/FR-006).
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (StudySessionItem item : items) {
            UUID itemId = item.getId();
            UUID subtopicId = item.getSubtopic().getId();
            futures.add(CompletableFuture.runAsync(() -> generateQuizForItem(itemId, subtopicId), quizExecutor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(quizJoinTimeoutSeconds, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    LlmErrorCause cause = (ex instanceof TimeoutException
                            || ex.getCause() instanceof TimeoutException)
                            ? LlmErrorCause.TIMEOUT
                            : LlmErrorCause.UNAVAILABLE;
                    log.warn("Quiz prefetch join for batch {} did not complete within {}s; "
                                    + "bounded outcome cause {}; finalizing non-terminal items before aggregation",
                            sessionId, quizJoinTimeoutSeconds, cause);
                    return null;
                })
                .join();

        // 009/FR-012/FR-014: after the bounded join, any item still PENDING/GENERATING (snack or
        // quiz) must be finalized to a terminal state so aggregation can never fall through to
        // IN_PROGRESS. TIMEOUT is the accurate typed cause for an item that exceeded its bound.
        List<StudySessionItem> settledItems = itemRepository.findBySessionId(sessionId);
        for (StudySessionItem item : settledItems) {
            boolean changed = false;
            if (isNonTerminal(item.getSnackStatus())) {
                item.setSnackStatus(GenerationStatus.FAILED);
                item.setSnackCause(LlmErrorCause.TIMEOUT);
                changed = true;
            }
            if (isNonTerminal(item.getQuizStatus())) {
                item.setQuizStatus(GenerationStatus.FAILED);
                item.setQuizCause(LlmErrorCause.TIMEOUT);
                changed = true;
            }
            if (changed) {
                itemRepository.save(item);
            }
        }

        // Aggregate final batch status.
        StudySession session = studySessionRepository.findById(sessionId).orElseThrow();
        session.setStatus(aggregateStatus(settledItems));
        studySessionRepository.save(session);
    }

    /**
     * 009/FR-007..FR-011: runs a single subtopic's snack generation on the separate
     * {@code snackExecutor} pool and bounds the wait with {@code orTimeout}. A never-returning
     * provider therefore cannot block the batch worker: the bound fires, the item is marked
     * {@code FAILED} with the typed {@link LlmErrorCause#TIMEOUT}, and the worker proceeds.
     *
     * <p>{@code orTimeout} completes the future but does not interrupt the running task; the pool is
     * bounded with daemon threads and the default AbortPolicy so the blast radius stays bounded.
     */
    private Snack generateSnackBounded(UUID subtopicId, SnackPhaseListener listener) {
        CompletableFuture<Snack> future;
        try {
            future = CompletableFuture.supplyAsync(
                    () -> snackService.generateSingleSnack(subtopicId, listener), snackExecutor);
        } catch (RejectedExecutionException e) {
            log.warn("Snack generation for subtopic {} could not be scheduled (executor saturated); "
                    + "marking FAILED with cause {}", subtopicId, LlmErrorCause.UNAVAILABLE);
            throw new LlmGenerationException(LlmErrorCause.UNAVAILABLE, false,
                    "Snack generation could not be scheduled.", e);
        }
        try {
            return future.orTimeout(snackTimeoutSeconds, TimeUnit.SECONDS).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof TimeoutException) {
                log.warn("Snack generation for subtopic {} exceeded the {}s per-subtopic bound; "
                        + "marking FAILED with cause {}", subtopicId, snackTimeoutSeconds, LlmErrorCause.TIMEOUT);
                throw new LlmGenerationException(LlmErrorCause.TIMEOUT, false,
                        "Snack generation exceeded the per-subtopic time bound.", e);
            }
            if (e.getCause() instanceof LlmGenerationException lge) {
                // Preserve the typed cause (QUOTA/TIMEOUT/NETWORK/…) for the existing catch.
                throw lge;
            }
            throw e;
        }
    }

    private boolean isNonTerminal(GenerationStatus status) {
        return status == GenerationStatus.PENDING || status == GenerationStatus.GENERATING;
    }

    private void generateQuizForItem(UUID itemId, UUID subtopicId) {
        StudySessionItem item = itemRepository.findById(itemId).orElse(null);
        if (item == null) {
            return;
        }
        item.setQuizStatus(GenerationStatus.GENERATING);
        itemRepository.save(item);
        try {
            quizService.generateQuiz(subtopicId, 4);
            item.setQuizStatus(GenerationStatus.READY);
            item.setQuizCause(null);
        } catch (LlmGenerationException e) {
            item.setQuizStatus(GenerationStatus.FAILED);
            item.setQuizCause(e.getCauseType());
        } catch (Exception e) {
            log.warn("Batch quiz generation failed for subtopic {}", subtopicId, e);
            item.setQuizStatus(GenerationStatus.FAILED);
            item.setQuizCause(LlmErrorCause.UNAVAILABLE);
        }
        itemRepository.save(item);
    }

    private BatchStatus aggregateStatus(List<StudySessionItem> items) {
        // 009/FR-012: an empty batch must terminate immediately rather than fall through to
        // IN_PROGRESS (the only non-terminal status).
        if (items.isEmpty()) {
            return BatchStatus.FAILED;
        }
        boolean anyInProgress = false;
        boolean anyReady = false;
        boolean anyFailed = false;
        for (StudySessionItem item : items) {
            boolean snackDone = item.getSnackStatus() == GenerationStatus.READY;
            boolean quizDone = item.getQuizStatus() == GenerationStatus.READY;
            boolean snackFailed = item.getSnackStatus() == GenerationStatus.FAILED;
            boolean quizFailed = item.getQuizStatus() == GenerationStatus.FAILED;
            if (item.getSnackStatus() == GenerationStatus.PENDING
                    || item.getSnackStatus() == GenerationStatus.GENERATING
                    || item.getQuizStatus() == GenerationStatus.PENDING
                    || item.getQuizStatus() == GenerationStatus.GENERATING) {
                anyInProgress = true;
            }
            if (snackDone && quizDone) {
                anyReady = true;
            }
            if (snackFailed || quizFailed) {
                anyFailed = true;
            }
        }
        if (!anyInProgress && anyReady && !anyFailed) {
            return BatchStatus.COMPLETE;
        }
        if (!anyInProgress && anyReady && anyFailed) {
            return BatchStatus.PARTIAL;
        }
        if (!anyInProgress && !anyReady && anyFailed) {
            return BatchStatus.FAILED;
        }
        return BatchStatus.IN_PROGRESS;
    }

    @Transactional(readOnly = true)
    public BatchStatusResult getStatus(UUID batchId) {
        StudySession session = studySessionRepository.findById(batchId)
                .orElseThrow(() -> new EntityNotFoundException("Batch", batchId));

        List<StudySessionItem> items = itemRepository.findBySessionId(batchId);
        List<BatchItemStatus> statuses = new ArrayList<>();
        for (StudySessionItem item : items) {
            // FR-003 / SC-002: surface the prefetched quiz so the frontend can consume it
            // without a second /api/quiz/generate call.
            List<QuizQuestion> prefetchedQuiz = item.getQuizStatus() == GenerationStatus.READY
                    ? quizQuestionRepository.findBySubtopicId(item.getSubtopic().getId())
                    : List.of();
            // FR-003/FR-004: expose the phase only while the item is actively generating.
            SnackGenerationPhase snackPhase = item.getSnackStatus() == GenerationStatus.GENERATING
                    ? phaseRegistry.current(item.getSubtopic().getId())
                    : null;
            statuses.add(new BatchItemStatus(
                    item.getSubtopic().getId(),
                    item.getSubtopic().getName(),
                    item.getSnackStatus(),
                    snackPhase,
                    item.getSnack(),
                    item.getSnackCause(),
                    item.getQuizStatus(),
                    item.getQuizCause(),
                    prefetchedQuiz));
        }
        // FR-011: reflect *active* item generation, not the whole IN_PROGRESS batch lifetime.
        // The backend cannot observe the LLM-level retry loop, so "any item GENERATING" is the
        // narrowest truthful proxy available without new retry telemetry.
        boolean retrying = items.stream().anyMatch(i ->
                i.getSnackStatus() == GenerationStatus.GENERATING
                        || i.getQuizStatus() == GenerationStatus.GENERATING);
        return new BatchStatusResult(batchId, session.getTheme().getId(), session.getStatus(), retrying, statuses);
    }

    // ---- Result record types ----

    public record BatchCreateResult(UUID batchId, UUID themeId, BatchStatus status, List<BatchItemCreate> items) {
    }

    public record BatchItemCreate(UUID subtopicId, GenerationStatus snackStatus, GenerationStatus quizStatus) {
    }

    public record BatchStatusResult(UUID batchId, UUID themeId, BatchStatus status, boolean retrying,
                                    List<BatchItemStatus> items) {
    }

    public record BatchItemStatus(UUID subtopicId, String subtopicName, GenerationStatus snackStatus,
                                  SnackGenerationPhase snackPhase, Snack snack,
                                  LlmErrorCause snackCause, GenerationStatus quizStatus, LlmErrorCause quizCause,
                                  List<QuizQuestion> quizQuestions) {
    }
}
