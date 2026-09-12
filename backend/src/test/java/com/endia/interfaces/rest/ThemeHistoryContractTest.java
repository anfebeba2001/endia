package com.endia.interfaces.rest;

import com.endia.application.service.ThemeService;
import com.endia.domain.enums.QuestionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for feature 011: {@code GET /api/themes} returns HTTP 200 with a JSON array of
 * themes newest-first, each with nested subtopics/snacks/quiz questions, and {@code []} when
 * empty. Uses a focused MockMvc setup (no DB).
 */
@ExtendWith(MockitoExtension.class)
class ThemeHistoryContractTest {

    @Mock
    private ThemeService themeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Mirror the real Spring Boot Jackson setup (JavaTimeModule, ISO-8601 dates) that
        // standaloneSetup does not register by default.
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new ThemeController(themeService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private ThemeService.ThemeHistory themeHistory(UUID themeId, String prompt, LocalDateTime createdAt) {
        ThemeService.SnackHistory snack = new ThemeService.SnackHistory(
                UUID.randomUUID(), "A concept", "A metaphor", "Why it matters", List.of("a", "b"));
        ThemeService.QuestionHistory question = new ThemeService.QuestionHistory(
                UUID.randomUUID(), QuestionType.MULTIPLE_CHOICE, "A question?", "An explanation.");
        ThemeService.SubtopicHistory subtopic = new ThemeService.SubtopicHistory(
                UUID.randomUUID(), "A subtopic", List.of(snack), List.of(question));
        return new ThemeService.ThemeHistory(themeId, prompt, createdAt, List.of(subtopic));
    }

    @Test
    void getThemesReturnsArrayWithFullNestedShape() throws Exception {
        UUID themeId = UUID.randomUUID();
        UUID subtopicId = UUID.randomUUID();
        ThemeService.SnackHistory snack = new ThemeService.SnackHistory(
                UUID.randomUUID(), "Chlorophyll absorbs light", "A solar kitchen",
                "Prevents a misconception", List.of("ATP synthase", "Electron transport"));
        ThemeService.QuestionHistory question = new ThemeService.QuestionHistory(
                UUID.randomUUID(), QuestionType.DRAG_DROP, "Order the steps", "Light drives it.");
        ThemeService.SubtopicHistory subtopic = new ThemeService.SubtopicHistory(
                subtopicId, "Light reactions", List.of(snack), List.of(question));
        when(themeService.listHistory()).thenReturn(List.of(
                new ThemeService.ThemeHistory(themeId, "Photosynthesis",
                        LocalDateTime.of(2026, 9, 11, 19, 52), List.of(subtopic))));

        mockMvc.perform(get("/api/themes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].themeId").value(themeId.toString()))
                .andExpect(jsonPath("$[0].prompt").value("Photosynthesis"))
                .andExpect(jsonPath("$[0].createdAt").value(containsString("2026-09-11")))
                .andExpect(jsonPath("$[0].subtopics", hasSize(1)))
                .andExpect(jsonPath("$[0].subtopics[0].id").value(subtopicId.toString()))
                .andExpect(jsonPath("$[0].subtopics[0].name").value("Light reactions"))
                .andExpect(jsonPath("$[0].subtopics[0].snacks", hasSize(1)))
                .andExpect(jsonPath("$[0].subtopics[0].snacks[0].id").value(snack.id().toString()))
                .andExpect(jsonPath("$[0].subtopics[0].snacks[0].concept").value("Chlorophyll absorbs light"))
                .andExpect(jsonPath("$[0].subtopics[0].snacks[0].metaphor").value("A solar kitchen"))
                .andExpect(jsonPath("$[0].subtopics[0].snacks[0].whyItMatters").value("Prevents a misconception"))
                .andExpect(jsonPath("$[0].subtopics[0].snacks[0].related", hasSize(2)))
                .andExpect(jsonPath("$[0].subtopics[0].snacks[0].related[0]").value("ATP synthase"))
                .andExpect(jsonPath("$[0].subtopics[0].quizQuestions", hasSize(1)))
                .andExpect(jsonPath("$[0].subtopics[0].quizQuestions[0].id").value(question.id().toString()))
                .andExpect(jsonPath("$[0].subtopics[0].quizQuestions[0].type").value("DRAG_DROP"))
                .andExpect(jsonPath("$[0].subtopics[0].quizQuestions[0].prompt").value("Order the steps"))
                .andExpect(jsonPath("$[0].subtopics[0].quizQuestions[0].explanation").value("Light drives it."));
    }

    @Test
    void getThemesReturns200WithEmptyArrayWhenNoThemes() throws Exception {
        when(themeService.listHistory()).thenReturn(List.of());

        mockMvc.perform(get("/api/themes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getThemesPreservesServiceOrderingNewestFirst() throws Exception {
        UUID newestId = UUID.randomUUID();
        UUID oldestId = UUID.randomUUID();
        when(themeService.listHistory()).thenReturn(List.of(
                themeHistory(newestId, "Newer theme", LocalDateTime.of(2026, 9, 11, 12, 0)),
                themeHistory(oldestId, "Older theme", LocalDateTime.of(2026, 9, 10, 10, 0))));

        mockMvc.perform(get("/api/themes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].themeId").value(newestId.toString()))
                .andExpect(jsonPath("$[0].prompt").value("Newer theme"))
                .andExpect(jsonPath("$[1].themeId").value(oldestId.toString()))
                .andExpect(jsonPath("$[1].prompt").value("Older theme"));
    }
}
