package com.endia.application.integration;

import com.endia.application.service.ThemeService;
import com.endia.domain.enums.QuestionType;
import com.endia.domain.model.QuizQuestion;
import com.endia.domain.model.Snack;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaQuizQuestionRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the read-only history endpoint using a real MySQL container.
 * Run with: mvn verify -Pintegration
 *
 * <p>The test intentionally calls {@link ThemeService#listHistory()} outside a web request and
 * accesses the returned nested read model after the service transaction has ended, so an
 * uninitialized lazy association would surface as a {@code LazyInitializationException}.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class ThemeHistoryIntegrationTest {

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
    private JpaQuizQuestionRepository quizQuestionRepository;
    @Autowired
    private ThemeService themeService;

    @BeforeEach
    void cleanDatabase() {
        quizQuestionRepository.deleteAll();
        snackRepository.deleteAll();
        subtopicRepository.deleteAll();
        themeRepository.deleteAll();
    }

    @Test
    void listHistoryReturnsNestedDataAccessibleAfterTheTransactionEnds() {
        Theme theme = themeRepository.save(new Theme("Photosynthesis"));
        Subtopic subtopic = subtopicRepository.save(new Subtopic(theme, "Light reactions"));
        snackRepository.save(new Snack(subtopic, "Chlorophyll absorbs light", "A solar kitchen",
                "Prevents a misconception", List.of("ATP synthase", "Electron transport")));
        quizQuestionRepository.save(new QuizQuestion(subtopic, "What absorbs light?",
                List.of("Chlorophyll", "Glucose"), 0, "Chlorophyll drives photosynthesis."));

        List<ThemeService.ThemeHistory> history = themeService.listHistory();

        assertEquals(1, history.size());
        // Access every nested field AFTER the service transaction has closed. A lazy leak would
        // raise LazyInitializationException here.
        ThemeService.ThemeHistory entry = history.get(0);
        assertEquals("Photosynthesis", entry.prompt());
        assertEquals(1, entry.subtopics().size());

        ThemeService.SubtopicHistory subtopicHistory = entry.subtopics().get(0);
        assertEquals("Light reactions", subtopicHistory.name());
        assertEquals(1, subtopicHistory.snacks().size());
        ThemeService.SnackHistory snack = subtopicHistory.snacks().get(0);
        assertEquals("Chlorophyll absorbs light", snack.concept());
        assertEquals("A solar kitchen", snack.metaphor());
        assertEquals("Prevents a misconception", snack.whyItMatters());
        assertEquals(List.of("ATP synthase", "Electron transport"), snack.related());

        assertEquals(1, subtopicHistory.quizQuestions().size());
        ThemeService.QuestionHistory question = subtopicHistory.quizQuestions().get(0);
        assertEquals(QuestionType.MULTIPLE_CHOICE, question.type());
        assertEquals("What absorbs light?", question.prompt());
        assertEquals("Chlorophyll drives photosynthesis.", question.explanation());
    }

    @Test
    void listHistoryOrdersThemesNewestFirst() throws InterruptedException {
        Theme older = themeRepository.save(new Theme("Older theme"));
        Thread.sleep(20);
        Theme newer = themeRepository.save(new Theme("Newer theme"));

        List<ThemeService.ThemeHistory> history = themeService.listHistory();

        assertEquals(2, history.size());
        assertEquals(newer.getId(), history.get(0).themeId());
        assertEquals("Newer theme", history.get(0).prompt());
        assertEquals(older.getId(), history.get(1).themeId());
        assertEquals("Older theme", history.get(1).prompt());
    }

    @Test
    void listHistoryReturnsEmptyListWhenNoThemesExist() {
        List<ThemeService.ThemeHistory> history = themeService.listHistory();

        assertTrue(history.isEmpty());
    }
}
