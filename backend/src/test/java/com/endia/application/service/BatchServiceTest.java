package com.endia.application.service;

import com.endia.application.port.SnackPhaseListener;
import com.endia.domain.enums.BatchStatus;
import com.endia.domain.enums.GenerationStatus;
import com.endia.domain.enums.LlmErrorCause;
import com.endia.domain.enums.SnackGenerationPhase;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchServiceTest {

    @Mock
    private JpaThemeRepository themeRepository;
    @Mock
    private JpaSubtopicRepository subtopicRepository;
    @Mock
    private JpaStudySessionRepository studySessionRepository;
    @Mock
    private JpaStudySessionItemRepository itemRepository;
    @Mock
    private JpaQuizQuestionRepository quizQuestionRepository;
    @Mock
    private SnackService snackService;
    @Mock
    private QuizService quizService;
    @Mock
    private BatchService self;

    private int counter;
    private final SnackPhaseRegistry phaseRegistry = new SnackPhaseRegistry();
    private BatchService batchService;
    private Theme theme;
    private UUID themeId;
    private UUID item1Id;
    private UUID item2Id;

    @BeforeEach
    void setUp() {
        counter = 0;
        // Both executors execute runnables inline so runBatch is deterministic.
        TaskExecutor syncExecutor = Runnable::run;
        batchService = new BatchService(themeRepository, subtopicRepository, studySessionRepository,
                itemRepository, quizQuestionRepository, snackService, quizService, phaseRegistry, syncExecutor,
                syncExecutor, syncExecutor, 240, 240, self);
        themeId = UUID.randomUUID();
        theme = new Theme("Photosynthesis");
        theme.setId(themeId);
        item1Id = UUID.randomUUID();
        item2Id = UUID.randomUUID();
    }

    private BatchService buildService(TaskExecutor batchExecutor, TaskExecutor quizExecutor,
                                      TaskExecutor snackExecutor, int quizJoinTimeoutSeconds,
                                      int snackTimeoutSeconds) {
        return new BatchService(themeRepository, subtopicRepository, studySessionRepository,
                itemRepository, quizQuestionRepository, snackService, quizService, phaseRegistry,
                batchExecutor, quizExecutor, snackExecutor, quizJoinTimeoutSeconds, snackTimeoutSeconds, self);
    }

    @Test
    void createBatchReturnsInProgressAndKicksWorker() {
        UUID sessionId = UUID.randomUUID();
        UUID sub1 = UUID.randomUUID(), sub2 = UUID.randomUUID();
        Subtopic s1 = new Subtopic(theme, "S1");
        s1.setId(sub1);
        Subtopic s2 = new Subtopic(theme, "S2");
        s2.setId(sub2);
        when(themeRepository.findById(themeId)).thenReturn(Optional.of(theme));
        when(subtopicRepository.findById(sub1)).thenReturn(Optional.of(s1));
        when(subtopicRepository.findById(sub2)).thenReturn(Optional.of(s2));
        when(studySessionRepository.save(any(StudySession.class))).thenAnswer(inv -> {
            StudySession s = inv.getArgument(0);
            s.setId(sessionId);
            return s;
        });

        BatchService.BatchCreateResult result = batchService.createBatch(themeId, List.of(sub1, sub2));

        assertEquals(BatchStatus.IN_PROGRESS, result.status());
        assertEquals(sessionId, result.batchId());
        assertEquals(2, result.items().size());
        assertEquals(GenerationStatus.PENDING, result.items().get(0).snackStatus());
        verify(self).runBatch(sessionId);
    }

    @Test
    void createBatchDefersWorkerUntilAfterCommit() {
        // FR-005/FR-006 regression: with an active transaction the worker must only be
        // triggered in afterCommit, never before the creating transaction commits.
        UUID sessionId = UUID.randomUUID();
        UUID sub1 = UUID.randomUUID();
        Subtopic s1 = new Subtopic(theme, "S1");
        s1.setId(sub1);
        when(themeRepository.findById(themeId)).thenReturn(Optional.of(theme));
        when(subtopicRepository.findById(sub1)).thenReturn(Optional.of(s1));
        when(studySessionRepository.save(any(StudySession.class))).thenAnswer(inv -> {
            StudySession s = inv.getArgument(0);
            s.setId(sessionId);
            return s;
        });

        TransactionSynchronizationManager.initSynchronization();
        try {
            batchService.createBatch(themeId, List.of(sub1));

            // Still inside the transaction: the worker must not have started.
            verify(self, never()).runBatch(sessionId);

            // Simulate the transaction committing.
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(self).runBatch(sessionId);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void runBatchTransitionsStatusesWithPartialFailure() {
        UUID sessionId = UUID.randomUUID();
        StudySession session = new StudySession(theme);
        session.setId(sessionId);

        Subtopic s1 = new Subtopic(theme, "S1");
        s1.setId(UUID.randomUUID());
        Subtopic s2 = new Subtopic(theme, "S2");
        s2.setId(UUID.randomUUID());
        StudySessionItem item1 = new StudySessionItem(session, s1);
        item1.setId(item1Id);
        StudySessionItem item2 = new StudySessionItem(session, s2);
        item2.setId(item2Id);

        when(itemRepository.findBySessionId(sessionId)).thenReturn(List.of(item1, item2));
        when(studySessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        // item1 snack + quiz succeed (batch resolves the subtopic by id, never by entity)
        when(snackService.generateSingleSnack(eq(s1.getId()), any(SnackPhaseListener.class)))
                .thenReturn(new Snack(s1, "c1", "m1", "w1", List.of("r1")));
        // item2 snack fails with QUOTA
        when(snackService.generateSingleSnack(eq(s2.getId()), any(SnackPhaseListener.class)))
                .thenThrow(new LlmGenerationException(LlmErrorCause.QUOTA, true, "quota"));
        // quizzes both succeed (quizService.generateQuiz checks by subtopic id)
        when(quizService.generateQuiz(any(UUID.class), anyInt())).thenReturn(List.of());
        // parallel quiz reload
        when(itemRepository.findById(item1Id)).thenReturn(Optional.of(item1));
        when(itemRepository.findById(item2Id)).thenReturn(Optional.of(item2));
        when(itemRepository.save(any(StudySessionItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studySessionRepository.save(any(StudySession.class))).thenAnswer(inv -> inv.getArgument(0));

        batchService.runBatch(sessionId);

        assertEquals(GenerationStatus.READY, item1.getSnackStatus());
        assertEquals(GenerationStatus.READY, item1.getQuizStatus());
        assertEquals(GenerationStatus.FAILED, item2.getSnackStatus());
        assertEquals(LlmErrorCause.QUOTA, item2.getSnackCause());
        assertEquals(GenerationStatus.READY, item2.getQuizStatus());
        assertEquals(BatchStatus.PARTIAL, session.getStatus());
        // FR-002 regression guard: the worker must use the by-id overload, never pass a detached entity.
        verify(snackService).generateSingleSnack(eq(s1.getId()), any(SnackPhaseListener.class));
        verify(snackService).generateSingleSnack(eq(s2.getId()), any(SnackPhaseListener.class));
        verify(snackService, never()).generateSingleSnack(any(Subtopic.class));
    }

    @Test
    void getStatusReturnsSnackContentAndCause() {
        UUID sessionId = UUID.randomUUID();
        Subtopic s1 = new Subtopic(theme, "Light reactions");
        StudySession session = new StudySession(theme);
        session.setId(sessionId);
        StudySessionItem item = new StudySessionItem(session, s1);
        item.setId(item1Id);
        item.setSnackStatus(GenerationStatus.FAILED);
        item.setSnackCause(LlmErrorCause.QUOTA);
        item.setQuizStatus(GenerationStatus.GENERATING);

        when(studySessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(itemRepository.findBySessionId(sessionId)).thenReturn(List.of(item));

        BatchService.BatchStatusResult status = batchService.getStatus(sessionId);

        assertEquals(BatchStatus.IN_PROGRESS, status.status());
        assertTrue(status.retrying());
        assertEquals(1, status.items().size());
        assertEquals(GenerationStatus.FAILED, status.items().get(0).snackStatus());
        assertEquals(LlmErrorCause.QUOTA, status.items().get(0).snackCause());
        assertEquals("Light reactions", status.items().get(0).subtopicName());
    }

    @Test
    void getStatusReportsNotRetryingWhenAllItemsPending() {
        // FR-011: a freshly created batch (all items PENDING, worker not yet active) is IN_PROGRESS
        // but not actively retrying/generating.
        UUID sessionId = UUID.randomUUID();
        Subtopic s1 = new Subtopic(theme, "Light reactions");
        StudySession session = new StudySession(theme);
        session.setId(sessionId);
        StudySessionItem item = new StudySessionItem(session, s1);
        item.setId(item1Id);
        item.setSnackStatus(GenerationStatus.PENDING);
        item.setQuizStatus(GenerationStatus.PENDING);

        when(studySessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(itemRepository.findBySessionId(sessionId)).thenReturn(List.of(item));

        BatchService.BatchStatusResult status = batchService.getStatus(sessionId);

        assertEquals(BatchStatus.IN_PROGRESS, status.status());
        assertFalse(status.retrying());
    }

    @Test
    void getStatusReportsNotRetryingWhenAllItemsTerminal() {
        // FR-011: terminal items must not report retrying, even while the session aggregate
        // has not yet been written (session still IN_PROGRESS).
        UUID sessionId = UUID.randomUUID();
        Subtopic s1 = new Subtopic(theme, "Light reactions");
        StudySession session = new StudySession(theme);
        session.setId(sessionId);
        StudySessionItem item = new StudySessionItem(session, s1);
        item.setId(item1Id);
        item.setSnackStatus(GenerationStatus.READY);
        item.setQuizStatus(GenerationStatus.FAILED);

        when(studySessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(itemRepository.findBySessionId(sessionId)).thenReturn(List.of(item));

        BatchService.BatchStatusResult status = batchService.getStatus(sessionId);

        assertEquals(BatchStatus.IN_PROGRESS, status.status());
        assertFalse(status.retrying());
    }

    @Test
    void getStatusSurfacesPrefetchedQuizWhenReady() {
        UUID sessionId = UUID.randomUUID();
        UUID sub1 = UUID.randomUUID();
        Subtopic s1 = new Subtopic(theme, "Light reactions");
        s1.setId(sub1);
        StudySession session = new StudySession(theme);
        session.setId(sessionId);
        StudySessionItem item = new StudySessionItem(session, s1);
        item.setId(item1Id);
        item.setSnackStatus(GenerationStatus.READY);
        item.setQuizStatus(GenerationStatus.READY);

        QuizQuestion q = new QuizQuestion(s1, "What is chlorophyll?", java.util.List.of("A", "B", "C"), 0, "expl");

        when(studySessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(itemRepository.findBySessionId(sessionId)).thenReturn(List.of(item));
        when(quizQuestionRepository.findBySubtopicId(sub1)).thenReturn(List.of(q));

        BatchService.BatchStatusResult status = batchService.getStatus(sessionId);

        assertEquals(1, status.items().size());
        assertEquals(List.of(q), status.items().get(0).quizQuestions());
    }

    // ---- 008: phase exposure + registry lifecycle (FR-003/FR-004/FR-008/FR-009) ----

    @Test
    void getStatusExposesPhaseWhileGeneratingAndNullWhenSettled() {
        UUID sessionId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        Subtopic s1 = new Subtopic(theme, "Light reactions");
        s1.setId(subId);
        StudySession session = new StudySession(theme);
        session.setId(sessionId);
        StudySessionItem item = new StudySessionItem(session, s1);
        item.setId(item1Id);
        item.setSnackStatus(GenerationStatus.GENERATING);
        item.setQuizStatus(GenerationStatus.GENERATING);

        when(studySessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(itemRepository.findBySessionId(sessionId)).thenReturn(List.of(item));

        phaseRegistry.update(subId, SnackGenerationPhase.AWAITING_MODEL);
        assertEquals(SnackGenerationPhase.AWAITING_MODEL,
                batchService.getStatus(sessionId).items().get(0).snackPhase());

        item.setSnackStatus(GenerationStatus.READY);
        assertNull(batchService.getStatus(sessionId).items().get(0).snackPhase());

        item.setSnackStatus(GenerationStatus.FAILED);
        assertNull(batchService.getStatus(sessionId).items().get(0).snackPhase());
    }

    @Test
    void runBatchClearsPhaseBeforeAndAfterGenerationOnSuccess() {
        UUID sessionId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        Subtopic s1 = new Subtopic(theme, "S1");
        s1.setId(subId);
        StudySession session = new StudySession(theme);
        session.setId(sessionId);
        StudySessionItem item = new StudySessionItem(session, s1);
        item.setId(item1Id);

        when(itemRepository.findBySessionId(sessionId)).thenReturn(List.of(item));
        when(studySessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(itemRepository.findById(item1Id)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(StudySessionItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studySessionRepository.save(any(StudySession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quizService.generateQuiz(any(UUID.class), anyInt())).thenReturn(List.of());

        // A stale entry from a previous cycle must be cleared before the new generation starts.
        phaseRegistry.update(subId, SnackGenerationPhase.PERSISTING);
        List<SnackGenerationPhase> phaseAtStart = new ArrayList<>();
        when(snackService.generateSingleSnack(eq(subId), any(SnackPhaseListener.class))).thenAnswer(inv -> {
            phaseAtStart.add(phaseRegistry.current(subId));
            SnackPhaseListener listener = inv.getArgument(1);
            listener.onPhase(SnackGenerationPhase.RESOLVING_CONTEXT);
            listener.onPhase(SnackGenerationPhase.AWAITING_MODEL);
            return new Snack(s1, "c", "m", "w", List.of("r"));
        });

        batchService.runBatch(sessionId);

        assertEquals(1, phaseAtStart.size());
        assertNull(phaseAtStart.get(0), "registry entry must be cleared before each generation");
        assertNull(phaseRegistry.current(subId), "registry entry must be cleared once the item settles");
    }

    @Test
    void runBatchClearsPhaseOnFailurePath() {
        UUID sessionId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        Subtopic s1 = new Subtopic(theme, "S1");
        s1.setId(subId);
        StudySession session = new StudySession(theme);
        session.setId(sessionId);
        StudySessionItem item = new StudySessionItem(session, s1);
        item.setId(item1Id);

        when(itemRepository.findBySessionId(sessionId)).thenReturn(List.of(item));
        when(studySessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(itemRepository.findById(item1Id)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(StudySessionItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studySessionRepository.save(any(StudySession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quizService.generateQuiz(any(UUID.class), anyInt())).thenReturn(List.of());
        when(snackService.generateSingleSnack(eq(subId), any(SnackPhaseListener.class))).thenAnswer(inv -> {
            SnackPhaseListener listener = inv.getArgument(1);
            listener.onPhase(SnackGenerationPhase.AWAITING_MODEL);
            throw new LlmGenerationException(LlmErrorCause.QUOTA, true, "quota");
        });

        phaseRegistry.update(subId, SnackGenerationPhase.AWAITING_MODEL);
        batchService.runBatch(sessionId);

        assertNull(phaseRegistry.current(subId), "registry entry must be cleared on the failure path too");
    }

    @Test
    void retriedItemReentersFromResolvingContext() {
        UUID sessionId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        Subtopic s1 = new Subtopic(theme, "S1");
        s1.setId(subId);
        StudySession session = new StudySession(theme);
        session.setId(sessionId);
        StudySessionItem item = new StudySessionItem(session, s1);
        item.setId(item1Id);

        when(itemRepository.findBySessionId(sessionId)).thenReturn(List.of(item));
        when(studySessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(itemRepository.findById(item1Id)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(StudySessionItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studySessionRepository.save(any(StudySession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quizService.generateQuiz(any(UUID.class), anyInt())).thenReturn(List.of());

        List<SnackGenerationPhase> firstPhaseOfEachCycle = new ArrayList<>();
        when(snackService.generateSingleSnack(eq(subId), any(SnackPhaseListener.class))).thenAnswer(inv -> {
            // A retry starts a clean cycle: the registry entry must be absent at entry.
            firstPhaseOfEachCycle.add(phaseRegistry.current(subId));
            SnackPhaseListener listener = inv.getArgument(1);
            listener.onPhase(SnackGenerationPhase.RESOLVING_CONTEXT);
            return new Snack(s1, "c", "m", "w", List.of("r"));
        });

        batchService.runBatch(sessionId);
        batchService.runBatch(sessionId);

        assertEquals(2, firstPhaseOfEachCycle.size());
        assertNull(firstPhaseOfEachCycle.get(0));
        assertNull(firstPhaseOfEachCycle.get(1));
        assertNull(phaseRegistry.current(subId));
    }

    // ---- 009: per-subtopic bound, terminal aggregate guarantee, config (FR-007..FR-012/FR-014) ----

    @Test
    void perSubtopicSnackTimeoutMarksItemFailedAndWorkerProceeds() throws Exception {
        UUID sessionId = UUID.randomUUID();
        StudySession session = new StudySession(theme);
        session.setId(sessionId);
        Subtopic s1 = new Subtopic(theme, "Hangs");
        s1.setId(UUID.randomUUID());
        Subtopic s2 = new Subtopic(theme, "Healthy");
        s2.setId(UUID.randomUUID());
        StudySessionItem item1 = new StudySessionItem(session, s1);
        item1.setId(item1Id);
        StudySessionItem item2 = new StudySessionItem(session, s2);
        item2.setId(item2Id);

        when(itemRepository.findBySessionId(sessionId)).thenReturn(List.of(item1, item2));
        when(studySessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(itemRepository.save(any(StudySessionItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studySessionRepository.save(any(StudySession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quizService.generateQuiz(any(UUID.class), anyInt())).thenReturn(List.of());
        when(itemRepository.findById(item1Id)).thenReturn(Optional.of(item1));
        when(itemRepository.findById(item2Id)).thenReturn(Optional.of(item2));

        CountDownLatch hang = new CountDownLatch(1);
        when(snackService.generateSingleSnack(eq(s1.getId()), any(SnackPhaseListener.class))).thenAnswer(inv -> {
            SnackPhaseListener listener = inv.getArgument(1);
            listener.onPhase(SnackGenerationPhase.AWAITING_MODEL);
            hang.await();
            return new Snack(s1, "c", "m", "w", List.of("r"));
        });
        when(snackService.generateSingleSnack(eq(s2.getId()), any(SnackPhaseListener.class)))
                .thenReturn(new Snack(s2, "c2", "m2", "w2", List.of("r2")));

        ExecutorService snackPool = Executors.newFixedThreadPool(2);
        try {
            BatchService service = buildService(Runnable::run, Runnable::run, snackPool::execute, 240, 1);
            service.runBatch(sessionId);

            // The hung item is bounded and marked FAILED with the typed TIMEOUT cause.
            assertEquals(GenerationStatus.FAILED, item1.getSnackStatus());
            assertEquals(LlmErrorCause.TIMEOUT, item1.getSnackCause());
            assertNull(phaseRegistry.current(s1.getId()), "phase must be cleared on the timeout path");
            // The worker proceeded and the healthy item still became READY.
            assertEquals(GenerationStatus.READY, item2.getSnackStatus());
            assertEquals(BatchStatus.PARTIAL, session.getStatus());
        } finally {
            hang.countDown();
            snackPool.shutdownNow();
        }
    }

    @Test
    void quizJoinTimeoutFinalizesGeneratingItemAndBatchIsTerminal() throws Exception {
        UUID sessionId = UUID.randomUUID();
        StudySession session = new StudySession(theme);
        session.setId(sessionId);
        Subtopic s1 = new Subtopic(theme, "S1");
        s1.setId(UUID.randomUUID());
        StudySessionItem item1 = new StudySessionItem(session, s1);
        item1.setId(item1Id);

        when(itemRepository.findBySessionId(sessionId)).thenReturn(List.of(item1));
        when(studySessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(itemRepository.save(any(StudySessionItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studySessionRepository.save(any(StudySession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.findById(item1Id)).thenReturn(Optional.of(item1));
        when(snackService.generateSingleSnack(eq(s1.getId()), any(SnackPhaseListener.class)))
                .thenReturn(new Snack(s1, "c", "m", "w", List.of("r")));

        CountDownLatch hang = new CountDownLatch(1);
        when(quizService.generateQuiz(any(UUID.class), anyInt())).thenAnswer(inv -> {
            hang.await();
            return List.of();
        });

        ExecutorService quizPool = Executors.newFixedThreadPool(2);
        try {
            BatchService service = buildService(Runnable::run, quizPool::execute, Runnable::run, 1, 240);
            service.runBatch(sessionId);

            // The stuck quiz item is finalized to FAILED/TIMEOUT before aggregation.
            assertEquals(GenerationStatus.FAILED, item1.getQuizStatus());
            assertEquals(LlmErrorCause.TIMEOUT, item1.getQuizCause());
            assertNotEquals(BatchStatus.IN_PROGRESS, session.getStatus(),
                    "batch must reach a terminal status after the bounded quiz join");
        } finally {
            hang.countDown();
            quizPool.shutdownNow();
        }
    }

    @Test
    void emptyBatchReachesTerminalStatus() {
        UUID sessionId = UUID.randomUUID();
        StudySession session = new StudySession(theme);
        session.setId(sessionId);
        when(itemRepository.findBySessionId(sessionId)).thenReturn(List.of());
        when(studySessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(studySessionRepository.save(any(StudySession.class))).thenAnswer(inv -> inv.getArgument(0));

        batchService.runBatch(sessionId);

        assertEquals(BatchStatus.FAILED, session.getStatus());
    }

    @Test
    void nonPositiveSnackTimeoutNormalizesToDefaultSoHealthyGenerationSucceeds() throws Exception {
        UUID sessionId = UUID.randomUUID();
        StudySession session = new StudySession(theme);
        session.setId(sessionId);
        Subtopic s1 = new Subtopic(theme, "S1");
        s1.setId(UUID.randomUUID());
        StudySessionItem item1 = new StudySessionItem(session, s1);
        item1.setId(item1Id);

        when(itemRepository.findBySessionId(sessionId)).thenReturn(List.of(item1));
        when(studySessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(itemRepository.save(any(StudySessionItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studySessionRepository.save(any(StudySession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.findById(item1Id)).thenReturn(Optional.of(item1));
        when(quizService.generateQuiz(any(UUID.class), anyInt())).thenReturn(List.of());
        when(snackService.generateSingleSnack(eq(s1.getId()), any(SnackPhaseListener.class))).thenAnswer(inv -> {
            // Completes well after an un-normalized orTimeout(0) would have fired.
            Thread.sleep(100);
            return new Snack(s1, "c", "m", "w", List.of("r"));
        });

        ExecutorService snackPool = Executors.newFixedThreadPool(2);
        try {
            // snackTimeoutSeconds = 0 must normalize to the documented default (240).
            BatchService service = buildService(Runnable::run, Runnable::run, snackPool::execute, 240, 0);
            service.runBatch(sessionId);

            assertEquals(GenerationStatus.READY, item1.getSnackStatus());
            assertNull(item1.getSnackCause());
        } finally {
            snackPool.shutdownNow();
        }
    }

    @Test
    void alwaysFailingSnackProducesFiniteBoundedGenerationCalls() {
        UUID sessionId = UUID.randomUUID();
        StudySession session = new StudySession(theme);
        session.setId(sessionId);
        Subtopic s1 = new Subtopic(theme, "S1");
        s1.setId(UUID.randomUUID());
        Subtopic s2 = new Subtopic(theme, "S2");
        s2.setId(UUID.randomUUID());
        StudySessionItem item1 = new StudySessionItem(session, s1);
        item1.setId(item1Id);
        StudySessionItem item2 = new StudySessionItem(session, s2);
        item2.setId(item2Id);

        when(itemRepository.findBySessionId(sessionId)).thenReturn(List.of(item1, item2));
        when(studySessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(itemRepository.save(any(StudySessionItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studySessionRepository.save(any(StudySession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.findById(item1Id)).thenReturn(Optional.of(item1));
        when(itemRepository.findById(item2Id)).thenReturn(Optional.of(item2));
        when(quizService.generateQuiz(any(UUID.class), anyInt())).thenReturn(List.of());
        when(snackService.generateSingleSnack(any(UUID.class), any(SnackPhaseListener.class)))
                .thenThrow(new LlmGenerationException(LlmErrorCause.TIMEOUT, true, "timeout"));

        batchService.runBatch(sessionId);

        // Exactly one snack generation call per item: the batch worker never re-enters or loops.
        // The per-call retry (<= batch.retry.max-attempts) and the single re-roll bounds are
        // verified in RetryingLlmProviderTest and SnackServiceTest.
        verify(snackService, times(2)).generateSingleSnack(any(UUID.class), any(SnackPhaseListener.class));
        assertEquals(GenerationStatus.FAILED, item1.getSnackStatus());
        assertEquals(GenerationStatus.FAILED, item2.getSnackStatus());
        assertEquals(BatchStatus.FAILED, session.getStatus());
    }
}
