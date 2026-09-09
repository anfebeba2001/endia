package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import com.endia.domain.exception.EntityNotFoundException;
import com.endia.domain.exception.ServiceUnavailableException;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaSubtopicRepository;
import com.endia.infrastructure.persistence.JpaThemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    private ThemeService themeService;

    @BeforeEach
    void setUp() {
        themeService = new ThemeService(llmProvider, themeRepository, subtopicRepository);
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
    void suggestSubtopicsThrowsServiceUnavailableWhenLlmFails() {
        when(llmProvider.suggestSubtopics("Theme"))
                .thenThrow(new RuntimeException("LLM down"));

        assertThrows(ServiceUnavailableException.class, () -> themeService.suggestSubtopics("Theme"));
    }
}
