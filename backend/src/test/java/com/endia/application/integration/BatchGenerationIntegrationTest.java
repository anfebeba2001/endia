package com.endia.application.integration;

import com.endia.application.port.LlmProvider;
import com.endia.application.service.BatchService;
import com.endia.domain.enums.BatchStatus;
import com.endia.domain.enums.GenerationStatus;
import com.endia.domain.enums.SnackGenerationPhase;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaSubtopicRepository;
import com.endia.infrastructure.persistence.JpaThemeRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Regression test for the batch generation defects fixed by 005:
 * (1) detached related-data access (LazyInitializationException) and
 * (2) the executor self-deadlock.
 *
 * <p>Runs the real Spring context, real {@code batchExecutor}/{@code quizExecutor} pools and a
 * stub {@link LlmProvider}. Several batches are started concurrently — the condition that
 * saturates the {@code batchExecutor} core pool and deadlocked before the fix. Every batch must
 * reach a terminal status with {@code READY} snacks and no lazy-init cause.
 *
 * <p>Tagged {@code integration} and excluded from the default {@code mvn test}; run with
 * {@code mvn test -Pintegration} (requires Docker).
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class BatchGenerationIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("endia")
            .withUsername("root")
            .withPassword("root");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // Keep the bounded quiz join short so a stuck child cannot make the test slow.
        registry.add("batch.quiz-join-timeout-seconds", () -> "10");
    }

    @MockitoBean
    private LlmProvider llmProvider;

    @Autowired
    private BatchService batchService;
    @Autowired
    private JpaThemeRepository themeRepository;
    @Autowired
    private JpaSubtopicRepository subtopicRepository;

    @Test
    void concurrentBatchesReachTerminalStatusWithReadySnacksAndNoLazyFailure() throws Exception {
        stubLlm();

        Theme theme = themeRepository.save(new Theme("Photosynthesis"));
        List<UUID> subtopicIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Subtopic subtopic = subtopicRepository.save(new Subtopic(theme, "Subtopic " + i));
            subtopicIds.add(subtopic.getId());
        }

        // Start several batches concurrently: this saturates the batchExecutor core pool,
        // which is exactly the condition that deadlocked before the separate quizExecutor.
        int batchCount = 3;
        ExecutorService submitter = Executors.newFixedThreadPool(batchCount);
        List<UUID> batchIds = new ArrayList<>();
        try {
            List<Future<BatchService.BatchCreateResult>> creates = new ArrayList<>();
            for (int i = 0; i < batchCount; i++) {
                creates.add(submitter.submit(() -> batchService.createBatch(theme.getId(), subtopicIds)));
            }
            for (Future<BatchService.BatchCreateResult> create : creates) {
                batchIds.add(create.get(30, TimeUnit.SECONDS).batchId());
            }
        } finally {
            submitter.shutdownNow();
        }

        long deadline = System.currentTimeMillis() + Duration.ofSeconds(60).toMillis();
        for (UUID batchId : batchIds) {
            awaitTerminalAndAssertReady(batchId, deadline);
        }
    }

    /**
     * Regression for the commit-ordering defect: a warm {@code batchExecutor} could start the
     * worker before {@code createBatch}'s transaction committed, read zero items, and leave the
     * batch stuck IN_PROGRESS. Two consecutive batches must both terminate with READY snacks.
     */
    @Test
    void twoConsecutiveBatchesBothReachTerminalStatus() throws Exception {
        stubLlm();

        Theme theme = themeRepository.save(new Theme("Physics"));
        Subtopic sub = subtopicRepository.save(new Subtopic(theme, "Gravity"));

        // Sequential (not concurrent): the second create exercises the warm-pool path that
        // previously raced the commit and produced a permanently PENDING batch.
        UUID first = batchService.createBatch(theme.getId(), List.of(sub.getId())).batchId();
        UUID second = batchService.createBatch(theme.getId(), List.of(sub.getId())).batchId();

        long deadline = System.currentTimeMillis() + Duration.ofSeconds(60).toMillis();
        awaitTerminalAndAssertReady(first, deadline);
        awaitTerminalAndAssertReady(second, deadline);
    }

    /**
     * Feature 008: with a slow (latched) LLM, a poll observes a non-null {@code snackPhase}
     * mid-generation, and the phase is cleared once the item is terminal (FR-003/FR-004).
     */
    @Test
    void phaseIsVisibleMidGenerationAndClearedAtTerminal() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(llmProvider.generateSnack(anyString(), anyString())).thenAnswer(inv -> {
            entered.countDown();
            release.await(30, TimeUnit.SECONDS);
            return new LlmProvider.GeneratedSnack("Concept", "Metaphor", "Why it matters", List.of("Related"));
        });
        when(llmProvider.generateQuiz(anyString(), anyString(), anyInt(), any()))
                .thenReturn(List.of(new LlmProvider.GeneratedQuestion(
                        "Question?", LlmProvider.GeneratedQuestion.QuestionType.MULTIPLE_CHOICE,
                        List.of("A", "B", "C"), 0, null, null, "Explanation")));

        Theme theme = themeRepository.save(new Theme("Biology"));
        Subtopic sub = subtopicRepository.save(new Subtopic(theme, "Mitosis"));
        UUID batchId = batchService.createBatch(theme.getId(), List.of(sub.getId())).batchId();

        assertTrue(entered.await(30, TimeUnit.SECONDS), "LLM call never started");

        SnackGenerationPhase observed = null;
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
        while (System.currentTimeMillis() < deadline && observed == null) {
            for (BatchService.BatchItemStatus item : batchService.getStatus(batchId).items()) {
                if (item.snackPhase() != null) {
                    observed = item.snackPhase();
                }
            }
            if (observed == null) {
                Thread.sleep(100);
            }
        }
        assertNotNull(observed, "no snackPhase was observable mid-generation");

        release.countDown();

        long terminalDeadline = System.currentTimeMillis() + Duration.ofSeconds(60).toMillis();
        BatchStatus status = BatchStatus.IN_PROGRESS;
        while (System.currentTimeMillis() < terminalDeadline && status == BatchStatus.IN_PROGRESS) {
            status = batchService.getStatus(batchId).status();
            if (status == BatchStatus.IN_PROGRESS) {
                Thread.sleep(200);
            }
        }
        assertNotEquals(BatchStatus.IN_PROGRESS, status, "batch did not reach a terminal status");
        for (BatchService.BatchItemStatus item : batchService.getStatus(batchId).items()) {
            assertNull(item.snackPhase(), "phase must be cleared once the item is terminal");
        }
    }

    private void awaitTerminalAndAssertReady(UUID batchId, long deadline) throws InterruptedException {
        BatchStatus status = null;
        while (System.currentTimeMillis() < deadline) {
            status = batchService.getStatus(batchId).status();
            if (status != BatchStatus.IN_PROGRESS) {
                break;
            }
            Thread.sleep(200);
        }
        assertNotEquals(BatchStatus.IN_PROGRESS, status, "Batch did not reach a terminal status: " + batchId);

        BatchService.BatchStatusResult result = batchService.getStatus(batchId);
        for (BatchService.BatchItemStatus item : result.items()) {
            assertEquals(GenerationStatus.READY, item.snackStatus(),
                    "Snack not READY (detached-data signature?) for " + item.subtopicId());
            assertNull(item.snackCause(), "Unexpected snack cause for " + item.subtopicId());
            assertNotNull(item.snack(), "Persisted snack missing for " + item.subtopicId());
        }
    }

    private void stubLlm() {
        when(llmProvider.generateSnack(anyString(), anyString()))
                .thenReturn(new LlmProvider.GeneratedSnack("Concept", "Metaphor", "Why it matters", List.of("Related")));
        when(llmProvider.generateQuiz(anyString(), anyString(), anyInt(), any()))
                .thenReturn(List.of(new LlmProvider.GeneratedQuestion(
                        "Question?", LlmProvider.GeneratedQuestion.QuestionType.MULTIPLE_CHOICE,
                        List.of("A", "B", "C"), 0, null, null, "Explanation")));
    }
}
