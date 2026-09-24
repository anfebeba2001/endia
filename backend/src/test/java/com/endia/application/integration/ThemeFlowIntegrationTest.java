package com.endia.application.integration;

import com.endia.application.service.SubtopicService;
import com.endia.domain.enums.SubtopicSource;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for theme -> subtopics flow using a real MySQL container.
 * Run with: mvn verify -Pintegration
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class ThemeFlowIntegrationTest {

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
    private SubtopicService subtopicService;

    @BeforeEach
    void cleanDatabase() {
        snackRepository.deleteAll();
        subtopicRepository.deleteAll();
        themeRepository.deleteAll();
    }

    @Test
    void contextLoads() {
        assertEquals(0, themeRepository.count());
    }

    // ---- T076: user-proposed subtopic persisted with source + dedup ----

    @Test
    void addSubtopicsPersistsUserProposedWithSource() {
        Theme theme = new Theme("Photosynthesis");
        theme = themeRepository.save(theme);

        SubtopicService.AddResult result = subtopicService.addSubtopics(theme.getId(), List.of("Photorespiration"));

        assertEquals(1, result.added().size());
        Subtopic persisted = subtopicRepository.findById(result.added().get(0).getId()).orElseThrow();
        assertEquals(SubtopicSource.USER_PROPOSED, persisted.getSource());
    }

    @Test
    void addSubtopicsDeduplicatesAgainstExistingAiSuggestion() {
        Theme theme = new Theme("Photosynthesis");
        theme = themeRepository.save(theme);
        Subtopic existing = new Subtopic(theme, "Calvin cycle");
        existing = subtopicRepository.save(existing);

        SubtopicService.AddResult result = subtopicService.addSubtopics(theme.getId(), List.of("  CALVIN   CYCLE "));

        assertTrue(result.added().isEmpty());
        assertEquals(1, result.duplicates().size());
        assertEquals(existing.getId(), subtopicRepository.findByThemeId(theme.getId()).get(0).getId());
    }
}
