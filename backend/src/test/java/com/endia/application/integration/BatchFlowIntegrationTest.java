package com.endia.application.integration;

import com.endia.domain.enums.BatchStatus;
import com.endia.domain.enums.GenerationStatus;
import com.endia.domain.enums.LlmErrorCause;
import com.endia.domain.enums.SubtopicSource;
import com.endia.domain.model.StudySession;
import com.endia.domain.model.StudySessionItem;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaStudySessionItemRepository;
import com.endia.infrastructure.persistence.JpaStudySessionRepository;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test for the batch/session model with a real MySQL container.
 * Covers the StudySession + StudySessionItem persistence contract (FR-001..004).
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class BatchFlowIntegrationTest {

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
    private JpaStudySessionRepository studySessionRepository;
    @Autowired
    private JpaStudySessionItemRepository itemRepository;

    @Test
    void persistsBatchWithPerItemStatusAndCauseAndProgressiveQueries() {
        Theme theme = new Theme("Photosynthesis");
        theme = themeRepository.save(theme);

        Subtopic sub1 = new Subtopic(theme, "Light reactions");
        sub1.setSource(SubtopicSource.AI_SUGGESTED);
        sub1 = subtopicRepository.save(sub1);
        Subtopic sub2 = new Subtopic(theme, "Calvin cycle");
        sub2.setSource(SubtopicSource.USER_PROPOSED);
        sub2 = subtopicRepository.save(sub2);

        StudySession session = new StudySession(theme);
        StudySessionItem item1 = new StudySessionItem(session, sub1);
        StudySessionItem item2 = new StudySessionItem(session, sub2);
        session.addItem(item1);
        session.addItem(item2);
        session = studySessionRepository.save(session);

        item1.setSnackStatus(GenerationStatus.READY);
        item1.setQuizStatus(GenerationStatus.GENERATING);
        item2.setSnackStatus(GenerationStatus.FAILED);
        item2.setSnackCause(LlmErrorCause.QUOTA);
        itemRepository.save(item1);
        itemRepository.save(item2);

        // Progressive read: per-item status observable from the DB for the poller.
        // findBySessionId has no defined row order, so assert on the set of statuses
        // and locate the failed item by its status instead of by position.
        List<StudySessionItem> items = itemRepository.findBySessionId(session.getId());
        assertEquals(2, items.size());

        Set<GenerationStatus> snackStatuses = new HashSet<>();
        for (StudySessionItem item : items) {
            snackStatuses.add(item.getSnackStatus());
        }
        assertEquals(Set.of(GenerationStatus.READY, GenerationStatus.FAILED), snackStatuses);

        StudySessionItem failed = items.stream()
                .filter(item -> item.getSnackStatus() == GenerationStatus.FAILED)
                .findFirst()
                .orElseThrow();
        assertEquals(LlmErrorCause.QUOTA, failed.getSnackCause());
        assertNotNull(session.getId());
    }

    @Test
    void batchDefaultStatusIsInProgressAndItemsPending() {
        Theme theme = new Theme("Physics");
        theme = themeRepository.save(theme);
        Subtopic sub = new Subtopic(theme, "Gravity");
        sub = subtopicRepository.save(sub);

        StudySession session = new StudySession(theme);
        session.addItem(new StudySessionItem(session, sub));
        session = studySessionRepository.save(session);

        assertEquals(BatchStatus.IN_PROGRESS, session.getStatus());
        assertEquals(GenerationStatus.PENDING, session.getItems().get(0).getSnackStatus());
        assertEquals(GenerationStatus.PENDING, session.getItems().get(0).getQuizStatus());
    }
}
