package com.endia.application.integration;

import com.endia.application.port.LlmProvider;
import com.endia.domain.enums.GenerationStatus;
import com.endia.domain.enums.LlmErrorCause;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaSubtopicRepository;
import com.endia.infrastructure.persistence.JpaThemeRepository;
import com.endia.interfaces.rest.dto.BatchCreateApiRequest;
import com.endia.interfaces.rest.dto.BatchCreateApiResponse;
import com.endia.interfaces.rest.dto.BatchStatusApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 009 bounded-snack-generation integration test. Runs the real Spring context (HTTP + async pools +
 * Testcontainers MySQL) with a <strong>never-returning</strong> {@link LlmProvider} stub whose calls
 * block on an interruptible {@link CountDownLatch}. Short configured bounds
 * ({@code batch.snack-timeout-seconds=2}, {@code batch.quiz-join-timeout-seconds=2}) keep the suite
 * fast and prove that a changed bound changes behavior.
 *
 * <p>Covers FR-012/FR-013/FR-014 (terminal batch, no pending/generating items), FR-010 (worker
 * proceeds past a hung item), and FR-015/FR-016/FR-017 (prompt {@code POST}/{@code GET}). Tagged
 * {@code integration}; run with {@code mvn test -Pintegration} (requires Docker).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BoundedSnackGenerationIntegrationTest {

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
        // 009: drive every bound down so a never-returning provider fails fast (SC-010).
        registry.add("llm.timeout-seconds", () -> "1");
        registry.add("batch.snack-timeout-seconds", () -> "2");
        registry.add("batch.quiz-join-timeout-seconds", () -> "2");
    }

    private static final LlmProvider.GeneratedSnack VALID_SNACK =
            new LlmProvider.GeneratedSnack("Concept", "Metaphor", "Why it matters", List.of("Related"));
    private static final LlmProvider.GeneratedQuestion VALID_QUESTION =
            new LlmProvider.GeneratedQuestion("Question?", LlmProvider.GeneratedQuestion.QuestionType.MULTIPLE_CHOICE,
                    List.of("A", "B", "C"), 0, null, null, "Explanation");

    @MockitoBean
    private LlmProvider llmProvider;

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JpaThemeRepository themeRepository;
    @Autowired
    private JpaSubtopicRepository subtopicRepository;

    private CountDownLatch hang;

    @BeforeEach
    void stubNeverReturningProvider() {
        hang = new CountDownLatch(1);
        when(llmProvider.generateSnack(anyString(), anyString())).thenAnswer(inv -> {
            hang.await(30, TimeUnit.SECONDS);
            return VALID_SNACK;
        });
        when(llmProvider.generateQuiz(anyString(), anyString(), anyInt(), any())).thenAnswer(inv -> {
            hang.await(30, TimeUnit.SECONDS);
            return List.of(VALID_QUESTION);
        });
    }

    @AfterEach
    void releaseHangingTasks() {
        // Interruptible latch: releasing it lets abandoned tasks finish so the JVM/context exits.
        hang.countDown();
    }

    /**
     * T010 / US4: all items hang → every item settles FAILED/TIMEOUT, the batch reaches a terminal
     * status, and no item is left PENDING/GENERATING.
     */
    @Test
    void allItemsHangReachTerminalStatusWithNoPendingItems() {
        Theme theme = themeRepository.save(new Theme("All hang"));
        Subtopic sub1 = subtopicRepository.save(new Subtopic(theme, "Hang one"));
        Subtopic sub2 = subtopicRepository.save(new Subtopic(theme, "Hang two"));

        UUID batchId = createBatch(theme.getId(), List.of(sub1.getId(), sub2.getId()));
        BatchStatusApiResponse terminal = awaitTerminal(batchId, Duration.ofSeconds(20));

        assertNotEquals("IN_PROGRESS", terminal.status(), "batch must not stay IN_PROGRESS");
        assertEquals(2, terminal.items().size());
        for (BatchStatusApiResponse.BatchItem item : terminal.items()) {
            assertEquals(GenerationStatus.FAILED, item.snackStatus(),
                    "hung snack must settle FAILED for " + item.subtopicId());
            assertEquals(LlmErrorCause.TIMEOUT, item.snackCause());
            assertEquals(GenerationStatus.FAILED, item.quizStatus(),
                    "hung quiz must be finalized FAILED for " + item.subtopicId());
            assertEquals(LlmErrorCause.TIMEOUT, item.quizCause());
        }
    }

    /**
     * T011 / US3: one subtopic hangs while another is healthy → the hung item fails with TIMEOUT, the
     * healthy item is READY, and the worker proceeds to a terminal PARTIAL batch.
     */
    @Test
    void oneHungItemFailsWhileHealthyItemRemainsReadyAndBatchIsPartial() {
        Theme theme = themeRepository.save(new Theme("Mixed"));
        Subtopic hanging = subtopicRepository.save(new Subtopic(theme, "Hang"));
        Subtopic healthy = subtopicRepository.save(new Subtopic(theme, "Healthy"));

        // Override the never-returning defaults: only "Hang" blocks; the healthy item and all
        // quizzes return valid content.
        when(llmProvider.generateSnack(anyString(), eq("Healthy"))).thenReturn(VALID_SNACK);
        when(llmProvider.generateQuiz(anyString(), anyString(), anyInt(), any()))
                .thenReturn(List.of(VALID_QUESTION));

        UUID batchId = createBatch(theme.getId(), List.of(hanging.getId(), healthy.getId()));
        BatchStatusApiResponse terminal = awaitTerminal(batchId, Duration.ofSeconds(20));

        Map<UUID, BatchStatusApiResponse.BatchItem> bySubtopic = terminal.items().stream()
                .collect(Collectors.toMap(BatchStatusApiResponse.BatchItem::subtopicId, i -> i));

        BatchStatusApiResponse.BatchItem hung = bySubtopic.get(hanging.getId());
        assertNotNull(hung);
        assertEquals(GenerationStatus.FAILED, hung.snackStatus(), "hung snack status");
        assertEquals(LlmErrorCause.TIMEOUT, hung.snackCause(), "hung snack cause");

        BatchStatusApiResponse.BatchItem ok = bySubtopic.get(healthy.getId());
        assertNotNull(ok);
        assertEquals(GenerationStatus.READY, ok.snackStatus(), "healthy snack status");
        assertEquals(GenerationStatus.READY, ok.quizStatus(), "healthy quiz status");

        assertEquals("PARTIAL", terminal.status(), "batch status");
    }

    /**
     * T012 / US5: with a never-returning provider, {@code POST /api/batches} returns 202 promptly
     * (not waiting on generation) and {@code GET /api/batches/{id}} returns promptly while the batch
     * is still non-terminal.
     */
    @Test
    void httpRequestsRemainPromptUnderNeverReturningProvider() {
        Theme theme = themeRepository.save(new Theme("HTTP liveness"));
        Subtopic sub = subtopicRepository.save(new Subtopic(theme, "Hang"));

        BatchCreateApiRequest request = new BatchCreateApiRequest(theme.getId(), List.of(sub.getId()));
        long postStart = System.nanoTime();
        ResponseEntity<BatchCreateApiResponse> post =
                restTemplate.postForEntity("/api/batches", request, BatchCreateApiResponse.class);
        long postMs = (System.nanoTime() - postStart) / 1_000_000;

        assertEquals(HttpStatus.ACCEPTED, post.getStatusCode());
        assertNotNull(post.getBody());
        assertTrue(postMs < 2000, "POST must return promptly and not wait for generation; took " + postMs + "ms");

        UUID batchId = post.getBody().batchId();
        long getStart = System.nanoTime();
        ResponseEntity<BatchStatusApiResponse> get =
                restTemplate.getForEntity("/api/batches/" + batchId, BatchStatusApiResponse.class);
        long getMs = (System.nanoTime() - getStart) / 1_000_000;

        assertEquals(HttpStatus.OK, get.getStatusCode());
        assertNotNull(get.getBody());
        assertTrue(getMs < 2000, "GET must not wait on background generation; took " + getMs + "ms");
    }

    private UUID createBatch(UUID themeId, List<UUID> subtopicIds) {
        ResponseEntity<BatchCreateApiResponse> response = restTemplate.postForEntity(
                "/api/batches", new BatchCreateApiRequest(themeId, subtopicIds), BatchCreateApiResponse.class);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().batchId();
    }

    private BatchStatusApiResponse awaitTerminal(UUID batchId, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        BatchStatusApiResponse latest = null;
        while (System.currentTimeMillis() < deadline) {
            latest = restTemplate.getForObject("/api/batches/" + batchId, BatchStatusApiResponse.class);
            if (latest != null && !"IN_PROGRESS".equals(latest.status())) {
                return latest;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Batch " + batchId + " did not reach a terminal status within " + timeout
                + "; last=" + (latest == null ? null : latest.status()));
    }
}
