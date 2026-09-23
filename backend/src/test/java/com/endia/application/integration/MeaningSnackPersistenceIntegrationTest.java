package com.endia.application.integration;

import com.endia.application.port.LlmProvider;
import com.endia.application.service.MeaningSnackService;
import com.endia.domain.model.MeaningSnack;
import com.endia.domain.model.Snack;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaMeaningSnackRepository;
import com.endia.infrastructure.persistence.JpaSnackRepository;
import com.endia.infrastructure.persistence.JpaSubtopicRepository;
import com.endia.infrastructure.persistence.JpaThemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Feature 014 (US1 / M1 / FR-017, FR-019, SC-007): the {@code meaning_snack} table round-trips
 * through a real MySQL persistence layer, the idempotency query matches case-insensitively, the
 * unique index rejects a duplicate {@code (source_subtopic_id, source_term)}, and pre-existing
 * snack/theme/subtopic rows are unaffected.
 *
 * <p>Tagged {@code integration}; run with {@code mvn verify -Pintegration} (requires Docker).
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class MeaningSnackPersistenceIntegrationTest {

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
    }

    @Autowired
    private JpaThemeRepository themeRepository;
    @Autowired
    private JpaSubtopicRepository subtopicRepository;
    @Autowired
    private JpaSnackRepository snackRepository;
    @Autowired
    private JpaMeaningSnackRepository meaningSnackRepository;
    @Autowired
    private MeaningSnackService meaningSnackService;

    @MockitoBean
    private LlmProvider llmProvider;

    private Theme theme;
    private Subtopic subtopic;

    @BeforeEach
    void setUp() {
        meaningSnackRepository.deleteAll();
        snackRepository.deleteAll();
        subtopicRepository.deleteAll();
        themeRepository.deleteAll();
        theme = themeRepository.save(new Theme("Photosynthesis"));
        subtopic = subtopicRepository.save(new Subtopic(theme, "Light reactions"));
    }

    @Test
    void meaningSnackRoundTripsWithIdenticalSourceAndRelated() {
        MeaningSnack saved = meaningSnackRepository.save(new MeaningSnack(
                theme, subtopic, "photosynthesis", "light to sugar",
                "**Concept**", "A **metaphor**.", "Why it matters.", List.of("Chlorophyll", "Glucose")));

        MeaningSnack reloaded = meaningSnackRepository.findById(saved.getId()).orElseThrow();

        assertEquals("photosynthesis", reloaded.getSourceTerm());
        assertEquals("light to sugar", reloaded.getSourceDefinition());
        assertEquals(List.of("Chlorophyll", "Glucose"), reloaded.getRelated());
        assertEquals(com.endia.domain.enums.SnackKind.MEANING, reloaded.getKind());
        assertEquals(subtopic.getId(), reloaded.getSourceSubtopic().getId());
        assertEquals(theme.getId(), reloaded.getTheme().getId());
        assertTrue(reloaded.getCreatedAt() != null);
    }

    @Test
    void idempotencyQueryMatchesCaseInsensitively() {
        meaningSnackRepository.save(new MeaningSnack(
                theme, subtopic, "Photosynthesis", "light to sugar",
                "c", "m", "w", List.of("r")));

        Optional<MeaningSnack> found = meaningSnackRepository
                .findBySourceSubtopicIdAndSourceTermIgnoreCase(subtopic.getId(), "photosynthesis");

        assertTrue(found.isPresent(), "the idempotency query must match case-insensitively");
    }

    @Test
    void uniqueIndexRejectsDuplicateSource() {
        meaningSnackRepository.save(new MeaningSnack(
                theme, subtopic, "photosynthesis", "light to sugar",
                "c", "m", "w", List.of("r")));

        assertThrows(DataIntegrityViolationException.class, () ->
                meaningSnackRepository.saveAndFlush(new MeaningSnack(
                        theme, subtopic, "photosynthesis", "other definition",
                        "c2", "m2", "w2", List.of("r2"))));
    }

    /**
     * D1 regression guard: a genuine check-then-insert race must be recovered inside
     * {@code generate()} through the <strong>real</strong> flush/commit path, not by a mocked
     * synchronous {@code save()} throw.
     *
     * <p>The competing winner is committed from the LLM mock, i.e. exactly between the fast-path
     * miss and the insert — the real runtime ordering. The duplicate insert then hits the
     * {@code ux_meaning_snack_source} unique index at flush/commit. If the recovery is unreachable
     * (as before the fix) the violation escapes {@code generate()} and this test fails with a
     * {@link DataIntegrityViolationException}; with the fix the winner is re-read in a fresh
     * transaction and returned with {@code alreadyExisted = true}.
     */
    @Test
    void concurrentDuplicateInsertIsRecoveredThroughTheRealFlushPath() {
        MeaningSnack winner = new MeaningSnack(theme, subtopic, "photosynthesis", "light to sugar",
                "winner concept", "winner metaphor", "winner why", List.of("r"));
        when(llmProvider.generateMeaningSnack(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    // The concurrent request wins the race and commits before our insert flushes.
                    meaningSnackRepository.saveAndFlush(winner);
                    return new LlmProvider.GeneratedMeaningSnack(
                            "loser concept", "loser metaphor", "loser why", List.of("r"));
                });

        MeaningSnackService.GenerateResult result = meaningSnackService.generate(
                theme.getId(), subtopic.getId(), "photosynthesis", "light to sugar");

        assertTrue(result.alreadyExisted(), "a concurrent duplicate must be idempotent, not a 500");
        assertEquals(winner.getId(), result.snack().getId());
        assertEquals("winner concept", result.snack().getConcept());
        assertEquals(1, meaningSnackRepository.count(), "exactly one row must survive");
    }

    @Test
    void preExistingSnackRowsAreUnaffected() {
        Snack snack = snackRepository.save(new Snack(
                subtopic, "regular concept", "regular metaphor", "regular why", List.of("a", "b")));

        meaningSnackRepository.save(new MeaningSnack(
                theme, subtopic, "photosynthesis", "light to sugar",
                "c", "m", "w", List.of("r")));

        Snack reloaded = snackRepository.findById(snack.getId()).orElseThrow();
        assertEquals("regular concept", reloaded.getConcept());
        assertEquals("regular metaphor", reloaded.getMetaphor());
        assertEquals(List.of("a", "b"), reloaded.getRelated());
        assertEquals(subtopic.getId(), reloaded.getSubtopic().getId());
    }

    @Test
    void listByThemeReturnsInsertedOrder() {
        meaningSnackRepository.save(new MeaningSnack(
                theme, subtopic, "first", "d", "c", "m", "w", List.of("r")));
        meaningSnackRepository.save(new MeaningSnack(
                theme, subtopic, "second", "d", "c", "m", "w", List.of("r")));

        List<MeaningSnack> list = meaningSnackRepository.findByThemeIdOrderByCreatedAtAsc(theme.getId());

        assertEquals(2, list.size());
        assertEquals("first", list.get(0).getSourceTerm());
    }
}
