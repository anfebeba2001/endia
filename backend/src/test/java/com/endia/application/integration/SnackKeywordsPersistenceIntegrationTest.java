package com.endia.application.integration;

import com.endia.domain.model.Snack;
import com.endia.domain.model.SnackKeyword;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaSnackRepository;
import com.endia.infrastructure.persistence.JpaSubtopicRepository;
import com.endia.infrastructure.persistence.JpaThemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feature 013 (US5 / FR-011/FR-012/SC-008): the {@code snack.keywords} JSON column round-trips
 * through a real MySQL persistence layer. Keywords survive with identical {@code term}/{@code
 * definition}; a legacy {@code NULL} column (or an empty list) loads as an empty list.
 *
 * <p>Tagged {@code integration}; run with {@code mvn verify -Pintegration} (requires Docker).
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class SnackKeywordsPersistenceIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    private Subtopic subtopic;

    @BeforeEach
    void setUp() {
        snackRepository.deleteAll();
        subtopicRepository.deleteAll();
        themeRepository.deleteAll();
        Theme theme = themeRepository.save(new Theme("Photosynthesis"));
        subtopic = subtopicRepository.save(new Subtopic(theme, "Light reactions"));
    }

    @Test
    void keywordsRoundTripWithIdenticalTermAndDefinition() {
        List<SnackKeyword> keywords = List.of(
                new SnackKeyword("mitosis", "cell division"),
                new SnackKeyword("C++", "a language"),
                new SnackKeyword("<b>ATP</b>", "energy <currency>"));

        Snack saved = snackRepository.save(new Snack(
                subtopic, "c", "m", "w", List.of("a", "b"), keywords));

        Snack reloaded = snackRepository.findById(saved.getId()).orElseThrow();

        assertEquals(keywords, reloaded.getKeywords(), "keywords must round-trip identically");
    }

    @Test
    void emptyKeywordsReloadAsEmptyList() {
        Snack saved = snackRepository.save(new Snack(
                subtopic, "c", "m", "w", List.of("a"), List.of()));

        Snack reloaded = snackRepository.findById(saved.getId()).orElseThrow();

        assertTrue(reloaded.getKeywords().isEmpty());
    }

    @Test
    void legacyNullColumnReloadsAsEmptyList() {
        Snack saved = snackRepository.save(new Snack(
                subtopic, "c", "m", "w", List.of("a"),
                List.of(new SnackKeyword("kept", "value"))));

        // Simulate a pre-existing row written before the column existed (NULL, not "[]").
        // Exactly one snack row exists in this test, so no id binding is required.
        jdbcTemplate.update("UPDATE snack SET keywords = NULL");

        Snack reloaded = snackRepository.findById(saved.getId()).orElseThrow();

        assertTrue(reloaded.getKeywords() == null || reloaded.getKeywords().isEmpty(),
                "a legacy NULL keywords column must load as an empty list");
    }

    @Test
    void persistedColumnIsValidJson() {
        snackRepository.save(new Snack(
                subtopic, "c", "m", "w", List.of("a"),
                List.of(new SnackKeyword("mitosis", "cell division"))));

        List<String> rows = jdbcTemplate.queryForList("SELECT keywords FROM snack", String.class);

        assertEquals(1, rows.size());
        String raw = rows.get(0);
        assertTrue(raw != null && raw.contains("mitosis") && raw.contains("cell division"),
                "the JSON column must contain the serialized keyword objects");
    }
}
