package com.endia.application.integration;

import com.endia.domain.model.Snack;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Feature 012 (US4 / FR-007): markup-bearing snack text survives a real MySQL persistence round
 * trip byte-for-byte — bold markers, bullet lines, blank-line paragraph breaks and injection
 * payloads are stored and reloaded unmodified (not stripped, collapsed, or HTML-escaped).
 *
 * <p>Tagged {@code integration}; run with {@code mvn verify -Pintegration} (requires Docker).
 * Each repository call runs in its own transaction, so the reload reads the committed value
 * directly from the database rather than a first-level cache.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class SnackMarkupPersistenceIntegrationTest {

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

    @BeforeEach
    void cleanDatabase() {
        snackRepository.deleteAll();
        subtopicRepository.deleteAll();
        themeRepository.deleteAll();
    }

    @Test
    void markupSurvivesJpaRoundTripByteForByte() {
        Theme theme = themeRepository.save(new Theme("Photosynthesis"));
        Subtopic subtopic = subtopicRepository.save(new Subtopic(theme, "Light reactions"));

        String concept = "**Bold** concept.\n\nTwo stages:\n- bullet one\n- bullet two\n\n"
                + "<script>window.__xss=1</script> and <img src=x onerror=alert(1)>";
        String metaphor = "A **solar kitchen**.";
        String whyItMatters = "Line one.\nLine two with <script>alert(1)</script>.";
        List<String> related = List.of("Alpha", "Beta");

        Snack saved = snackRepository.save(new Snack(subtopic, concept, metaphor, whyItMatters, related));

        Snack reloaded = snackRepository.findById(saved.getId()).orElseThrow();

        assertEquals(concept, reloaded.getConcept(), "concept must round-trip unmodified");
        assertEquals(metaphor, reloaded.getMetaphor(), "metaphor must round-trip unmodified");
        assertEquals(whyItMatters, reloaded.getWhyItMatters(), "whyItMatters must round-trip unmodified");
        assertEquals(related, reloaded.getRelated());
    }
}
