package com.endia.application.integration;

import com.endia.domain.model.AnswerAttempt;
import com.endia.domain.model.QuizQuestion;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaAnswerAttemptRepository;
import com.endia.infrastructure.persistence.JpaQuizQuestionRepository;
import com.endia.infrastructure.persistence.JpaSubtopicRepository;
import com.endia.infrastructure.persistence.JpaThemeRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the quiz generate -> answer -> summary flow with a real MySQL container.
 * Requires Docker with API version compatible with Testcontainers.
 * Run with: mvn verify -Pintegration
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class QuizFlowIntegrationTest {

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
    private JpaQuizQuestionRepository quizQuestionRepository;
    @Autowired
    private JpaAnswerAttemptRepository answerAttemptRepository;

    @Test
    void persistsAndRetrievesQuizWithAnswerAttempts() {
        Theme theme = new Theme("Photosynthesis");
        theme = themeRepository.save(theme);

        Subtopic subtopic = new Subtopic(theme, "Light reactions");
        subtopic = subtopicRepository.save(subtopic);

        QuizQuestion q = new QuizQuestion(subtopic,
                "What is the role of Photosystem II?",
                List.of("Option A", "Option B", "Option C", "Option D"),
                1,
                "Photosystem II splits water.");
        q = quizQuestionRepository.save(q);

        AnswerAttempt correct = new AnswerAttempt(q, 1, true, 10);
        answerAttemptRepository.save(correct);

        List<QuizQuestion> questions = quizQuestionRepository.findBySubtopicId(subtopic.getId());
        assertEquals(1, questions.size());

        List<AnswerAttempt> attempts = answerAttemptRepository.findByQuizQuestionId(q.getId());
        assertEquals(1, attempts.size());
        assertTrue(attempts.get(0).isCorrect());
        assertEquals(10, attempts.get(0).getXpEarned());
        assertEquals(q.getId(), attempts.get(0).getQuizQuestion().getId());
    }

    @Test
    void uniqueConstraintPreventsDuplicateAnswers() {
        Theme theme = new Theme("Theme");
        theme = themeRepository.save(theme);
        Subtopic subtopic = new Subtopic(theme, "Subtopic");
        subtopic = subtopicRepository.save(subtopic);

        QuizQuestion q = new QuizQuestion(subtopic, "Q?", List.of("A", "B", "C", "D"), 0, "Expl");
        QuizQuestion savedQuiz = quizQuestionRepository.save(q);

        answerAttemptRepository.save(new AnswerAttempt(savedQuiz, 2, false, 0));

        // Second answer with the SAME chosen_index violates the unique constraint.
        assertThrows(Exception.class, () ->
                answerAttemptRepository.saveAndFlush(new AnswerAttempt(savedQuiz, 2, true, 10)));
    }
}
