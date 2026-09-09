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
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
    private final BatchService self;

    public BatchService(JpaThemeRepository themeRepository,
                        JpaSubtopicRepository subtopicRepository,
                        JpaStudySessionRepository studySessionRepository,
                        JpaStudySessionItemRepository itemRepository,
                        JpaQuizQuestionRepository quizQuestionRepository,
                        SnackService snackService,
                        QuizService quizService,
                        @Qualifier("batchExecutor") TaskExecutor batchExecutor,
                        @Lazy BatchService self) {
        this.themeRepository = themeRepository;
        this.subtopicRepository = subtopicRepository;
        this.studySessionRepository = studySessionRepository;
        this.itemRepository = itemRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.snackService = snackService;
        this.quizService = quizService;
        this.batchExecutor = batchExecutor;
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

        self.runBatch(session.getId());

        return new BatchCreateResult(session.getId(), themeId, BatchStatus.IN_PROGRESS, items);
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
                Snack snack = snackService.generateSingleSnack(item.getSubtopic());
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

        // Quizzes in parallel (prefetch, FR-003).
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (StudySessionItem item : items) {
            UUID itemId = item.getId();
            UUID subtopicId = item.getSubtopic().getId();
            futures.add(CompletableFuture.runAsync(() -> generateQuizForItem(itemId, subtopicId), batchExecutor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

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
        boolean retrying = session.getStatus() == BatchStatus.IN_PROGRESS;
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
