package com.endia.application.service;

import com.endia.domain.enums.BatchStatus;
import com.endia.domain.enums.GenerationStatus;
import com.endia.domain.enums.LlmErrorCause;
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
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates the batch flow (FR-001..004): one request persists a {@link StudySession}
 * and returns immediately; a background {@code @Async} worker then generates each
 * subtopic's snack in sequence and its quiz in parallel, updating per-item status.
 */
@Service
public class BatchService {

    private static final Logger log = LoggerFactory.getLogger(BatchService.class);

    private final JpaThemeRepository themeRepository;
    private final JpaSubtopicRepository subtopicRepository;
    private final JpaStudySessionRepository studySessionRepository;
    private final JpaStudySessionItemRepository itemRepository;
    private final JpaQuizQuestionRepository quizQuestionRepository;
    private final SnackService snackService;
    private final QuizService quizService;
    private final TaskExecutor batchExecutor;
    private final TaskExecutor quizExecutor;
    private final int quizJoinTimeoutSeconds;
    private final BatchService self;

    public BatchService(JpaThemeRepository themeRepository,
                        JpaSubtopicRepository subtopicRepository,
                        JpaStudySessionRepository studySessionRepository,
                        JpaStudySessionItemRepository itemRepository,
                        JpaQuizQuestionRepository quizQuestionRepository,
                        SnackService snackService,
                        QuizService quizService,
                        @Qualifier("batchExecutor") TaskExecutor batchExecutor,
                        @Qualifier("quizExecutor") TaskExecutor quizExecutor,
                        @Value("${batch.quiz-join-timeout-seconds:240}") int quizJoinTimeoutSeconds,
                        @Lazy BatchService self) {
        this.themeRepository = themeRepository;
        this.subtopicRepository = subtopicRepository;
        this.studySessionRepository = studySessionRepository;
        this.itemRepository = itemRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.snackService = snackService;
        this.quizService = quizService;
        this.batchExecutor = batchExecutor;
        this.quizExecutor = quizExecutor;
        this.quizJoinTimeoutSeconds = quizJoinTimeoutSeconds;
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
            try {
                // FR-001/FR-002: only the id crosses the boundary; the subtopic (and its lazy
                // theme) are resolved inside the transactional by-id overload, never detached.
                UUID subtopicId = item.getSubtopic().getId();
                Snack snack = snackService.generateSingleSnack(subtopicId);
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
                    log.warn("Quiz prefetch join did not complete within {}s for batch {}; aggregating with current item states",
                            quizJoinTimeoutSeconds, sessionId);
                    return null;
                })
                .join();

        // Aggregate final batch status.
        List<StudySessionItem> finalItems = itemRepository.findBySessionId(sessionId);
        StudySession session = studySessionRepository.findById(sessionId).orElseThrow();
        session.setStatus(aggregateStatus(finalItems));
        studySessionRepository.save(session);
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
            statuses.add(new BatchItemStatus(
                    item.getSubtopic().getId(),
                    item.getSubtopic().getName(),
                    item.getSnackStatus(),
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

    public record BatchItemStatus(UUID subtopicId, String subtopicName, GenerationStatus snackStatus, Snack snack,
                                  LlmErrorCause snackCause, GenerationStatus quizStatus, LlmErrorCause quizCause,
                                  List<QuizQuestion> quizQuestions) {
    }
}
