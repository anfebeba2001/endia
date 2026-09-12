package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import com.endia.domain.enums.LlmErrorCause;
import com.endia.domain.enums.QuestionType;
import com.endia.domain.exception.LlmGenerationException;
import com.endia.domain.exception.ServiceUnavailableException;
import com.endia.domain.model.QuizQuestion;
import com.endia.domain.model.Snack;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaQuizQuestionRepository;
import com.endia.infrastructure.persistence.JpaSnackRepository;
import com.endia.infrastructure.persistence.JpaSubtopicRepository;
import com.endia.infrastructure.persistence.JpaThemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThemeServiceTest {

    @Mock
    private LlmProvider llmProvider;
    @Mock
    private JpaThemeRepository themeRepository;
    @Mock
    private JpaSubtopicRepository subtopicRepository;
    @Mock
    private JpaSnackRepository snackRepository;
    @Mock
    private JpaQuizQuestionRepository quizQuestionRepository;

    private ThemeService themeService;

    @BeforeEach
    void setUp() {
        themeService = new ThemeService(llmProvider, themeRepository, subtopicRepository,
                snackRepository, quizQuestionRepository);
    }

    @Test
    void suggestSubtopicsPersistsThemeAndSubtopics() {
        when(llmProvider.suggestSubtopics("Photosynthesis"))
                .thenReturn(List.of("Light reactions", "Calvin cycle", "Chloroplast structure", "Photorespiration"));
        when(themeRepository.save(any(Theme.class))).thenAnswer(inv -> inv.getArgument(0));
        when(subtopicRepository.save(any(Subtopic.class))).thenAnswer(inv -> inv.getArgument(0));

        ThemeService.ThemeSuggestResult result = themeService.suggestSubtopics("Photosynthesis");

        verify(themeRepository).save(any(Theme.class));
        verify(subtopicRepository, org.mockito.Mockito.times(4)).save(any(Subtopic.class));
        assertEquals(4, result.subtopics().size());
        assertEquals("Light reactions", result.subtopics().get(0).getName());
    }

    @Test
    void suggestSubtopicsFiltersBlankNames() {
        when(llmProvider.suggestSubtopics("Theme"))
                .thenReturn(List.of("Good one", "", "  ", "Another"));
        when(themeRepository.save(any(Theme.class))).thenAnswer(inv -> inv.getArgument(0));
        when(subtopicRepository.save(any(Subtopic.class))).thenAnswer(inv -> inv.getArgument(0));

        ThemeService.ThemeSuggestResult result = themeService.suggestSubtopics("Theme");

        assertEquals(2, result.subtopics().size());
    }

    @Test
    void suggestSubtopicsThrowsServiceUnavailableWhenEmptyResult() {
        when(llmProvider.suggestSubtopics("asdfghjkl")).thenReturn(List.of());

        assertThrows(ServiceUnavailableException.class, () -> themeService.suggestSubtopics("asdfghjkl"));
    }

    @Test
    void suggestSubtopicsPropagatesTypedLlmGenerationExceptionUnchanged() {
        LlmGenerationException llmError = new LlmGenerationException(LlmErrorCause.UNAVAILABLE, true, "boom");
        when(llmProvider.suggestSubtopics("Theme")).thenThrow(llmError);

        LlmGenerationException thrown = assertThrows(LlmGenerationException.class,
                () -> themeService.suggestSubtopics("Theme"));

        assertEquals(LlmErrorCause.UNAVAILABLE, thrown.getCauseType());
    }

    @Test
    void suggestSubtopicsMapsUnexpectedErrorsToServiceUnavailable() {
        when(llmProvider.suggestSubtopics("Theme"))
                .thenThrow(new RuntimeException("LLM down"));

        assertThrows(ServiceUnavailableException.class, () -> themeService.suggestSubtopics("Theme"));
    }

    // ---- listHistory() (FR-005, FR-019, SC-003) ----

    @Test
    void listHistoryReturnsEntriesInRepositoryOrderWithNestedChildrenGrouped() {
        Subtopic light = subtopic("Light reactions");
        Subtopic calvin = subtopic("Calvin cycle");
        Theme newer = theme("Photosynthesis v2", LocalDateTime.of(2026, 9, 11, 12, 0), List.of(light));
        Theme older = theme("Photosynthesis", LocalDateTime.of(2026, 9, 10, 10, 0), List.of(calvin));

        when(themeRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(newer, older));

        Snack lightSnack = snack(light, "Chlorophyll absorbs light", "Solar kitchen", "Prevents misconception",
                List.of("ATP synthase", "Electron transport"));
        Snack calvinSnack = snack(calvin, "Carbon fixation", "Assembly line", "Builds sugar",
                List.of("RuBP"));
        when(snackRepository.findBySubtopicIdIn(anyCollection()))
                .thenReturn(List.of(lightSnack, calvinSnack));

        QuizQuestion lightQuestion = question(light, "What absorbs light?");
        when(quizQuestionRepository.findBySubtopicIdIn(anyCollection()))
                .thenReturn(List.of(lightQuestion));

        List<ThemeService.ThemeHistory> history = themeService.listHistory();

        assertEquals(2, history.size());
        // Repository order is preserved (newest first).
        assertEquals(newer.getId(), history.get(0).themeId());
        assertEquals(older.getId(), history.get(1).themeId());

        ThemeService.ThemeHistory first = history.get(0);
        assertEquals("Photosynthesis v2", first.prompt());
        assertEquals(newer.getCreatedAt(), first.createdAt());
        assertEquals(1, first.subtopics().size());

        ThemeService.SubtopicHistory firstSubtopic = first.subtopics().get(0);
        assertEquals(light.getId(), firstSubtopic.id());
        assertEquals("Light reactions", firstSubtopic.name());
        assertEquals(1, firstSubtopic.snacks().size());
        ThemeService.SnackHistory firstSnack = firstSubtopic.snacks().get(0);
        assertEquals(lightSnack.getId(), firstSnack.id());
        assertEquals("Chlorophyll absorbs light", firstSnack.concept());
        assertEquals("Solar kitchen", firstSnack.metaphor());
        assertEquals("Prevents misconception", firstSnack.whyItMatters());
        assertEquals(List.of("ATP synthase", "Electron transport"), firstSnack.related());

        assertEquals(1, firstSubtopic.quizQuestions().size());
        ThemeService.QuestionHistory firstQuestion = firstSubtopic.quizQuestions().get(0);
        assertEquals(lightQuestion.getId(), firstQuestion.id());
        assertEquals(QuestionType.MULTIPLE_CHOICE, firstQuestion.type());
        assertEquals("What absorbs light?", firstQuestion.prompt());
        assertEquals("Because light drives it.", firstQuestion.explanation());

        // The second theme keeps its own (question-less) nested child.
        assertEquals(1, history.get(1).subtopics().size());
        assertEquals("Calvin cycle", history.get(1).subtopics().get(0).name());
        assertTrue(history.get(1).subtopics().get(0).quizQuestions().isEmpty());
    }

    @Test
    void listHistoryReturnsEmptyAndSkipsChildFindersWhenNoThemes() {
        when(themeRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        List<ThemeService.ThemeHistory> history = themeService.listHistory();

        assertTrue(history.isEmpty());
        verify(snackRepository, never()).findBySubtopicIdIn(anyCollection());
        verify(quizQuestionRepository, never()).findBySubtopicIdIn(anyCollection());
    }

    @Test
    void listHistorySkipsChildFindersWhenThemesHaveNoSubtopics() {
        Theme noSubtopics = theme("Empty theme", LocalDateTime.of(2026, 9, 11, 9, 0), List.of());
        when(themeRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(noSubtopics));

        List<ThemeService.ThemeHistory> history = themeService.listHistory();

        assertEquals(1, history.size());
        assertTrue(history.get(0).subtopics().isEmpty());
        verify(snackRepository, never()).findBySubtopicIdIn(anyCollection());
        verify(quizQuestionRepository, never()).findBySubtopicIdIn(anyCollection());
    }

    @Test
    void listHistoryYieldsEmptyNestedListsWithoutError() {
        Subtopic bare = subtopic("Bare subtopic");
        Theme theme = theme("Theme", LocalDateTime.of(2026, 9, 11, 9, 0), List.of(bare));
        when(themeRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(theme));
        when(snackRepository.findBySubtopicIdIn(anyCollection())).thenReturn(List.of());
        when(quizQuestionRepository.findBySubtopicIdIn(anyCollection())).thenReturn(List.of());

        List<ThemeService.ThemeHistory> history = themeService.listHistory();

        assertEquals(1, history.size());
        ThemeService.SubtopicHistory subtopicHistory = history.get(0).subtopics().get(0);
        assertTrue(subtopicHistory.snacks().isEmpty());
        assertTrue(subtopicHistory.quizQuestions().isEmpty());
    }

    private static Theme theme(String prompt, LocalDateTime createdAt, List<Subtopic> subtopics) {
        Theme theme = new Theme(prompt);
        theme.setId(UUID.randomUUID());
        theme.setCreatedAt(createdAt);
        theme.setSubtopics(subtopics);
        for (Subtopic subtopic : subtopics) {
            subtopic.setTheme(theme);
        }
        return theme;
    }

    private static Subtopic subtopic(String name) {
        Subtopic subtopic = new Subtopic();
        subtopic.setId(UUID.randomUUID());
        subtopic.setName(name);
        return subtopic;
    }

    private static Snack snack(Subtopic subtopic, String concept, String metaphor, String whyItMatters,
                               List<String> related) {
        Snack snack = new Snack(subtopic, concept, metaphor, whyItMatters, related);
        snack.setId(UUID.randomUUID());
        return snack;
    }

    private static QuizQuestion question(Subtopic subtopic, String prompt) {
        QuizQuestion question = new QuizQuestion(subtopic, prompt, List.of("a", "b"), 0, "Because light drives it.");
        question.setId(UUID.randomUUID());
        return question;
    }
}
