package com.endia.application.service;

import com.endia.domain.enums.SubtopicSource;
import com.endia.domain.exception.EntityNotFoundException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubtopicServiceTest {

    @Mock
    private JpaThemeRepository themeRepository;
    @Mock
    private JpaSubtopicRepository subtopicRepository;

    private SubtopicService subtopicService;
    private Theme theme;
    private UUID themeId;

    @BeforeEach
    void setUp() {
        subtopicService = new SubtopicService(themeRepository, subtopicRepository);
        themeId = UUID.randomUUID();
        theme = new Theme("Photosynthesis");
        theme.setId(themeId);
    }

    @Test
    void addSubtopicsPersistsWithUserProposedSource() {
        when(themeRepository.findById(themeId)).thenReturn(Optional.of(theme));
        when(subtopicRepository.findByThemeId(themeId)).thenReturn(List.of());
        when(subtopicRepository.save(any(Subtopic.class))).thenAnswer(inv -> inv.getArgument(0));

        SubtopicService.AddResult result = subtopicService.addSubtopics(themeId, List.of("Photorespiration"));

        assertEquals(1, result.added().size());
        assertEquals(SubtopicSource.USER_PROPOSED, result.added().get(0).getSource());
        assertTrue(result.duplicates().isEmpty());
    }

    @Test
    void addSubtopicsThrows404WhenThemeNotFound() {
        when(themeRepository.findById(themeId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> subtopicService.addSubtopics(themeId, List.of("X")));
    }

    @Test
    void dedupNormalizedAgainstExistingAiSubtopic() {
        Subtopic existing = new Subtopic(theme, "Calvin cycle");
        when(themeRepository.findById(themeId)).thenReturn(Optional.of(theme));
        when(subtopicRepository.findByThemeId(themeId)).thenReturn(List.of(existing));

        SubtopicService.AddResult result = subtopicService.addSubtopics(themeId, List.of("  CALVIN   CYCLE "));

        assertTrue(result.added().isEmpty());
        assertEquals(List.of("calvin cycle"), result.duplicates());
    }

    @Test
    void dedupWithinRequestKeepsFirst() {
        when(themeRepository.findById(themeId)).thenReturn(Optional.of(theme));
        when(subtopicRepository.findByThemeId(themeId)).thenReturn(List.of());
        when(subtopicRepository.save(any(Subtopic.class))).thenAnswer(inv -> inv.getArgument(0));

        SubtopicService.AddResult result = subtopicService.addSubtopics(themeId, List.of("C4 Pathway", "c4   pathway"));

        assertEquals(1, result.added().size());
        assertEquals("C4 Pathway", result.added().get(0).getName());
        assertEquals(List.of("c4 pathway"), result.duplicates());
    }

    @Test
    void blankNamesRejectedAndSkipped() {
        when(themeRepository.findById(themeId)).thenReturn(Optional.of(theme));
        when(subtopicRepository.findByThemeId(themeId)).thenReturn(List.of());
        when(subtopicRepository.save(any(Subtopic.class))).thenAnswer(inv -> inv.getArgument(0));

        SubtopicService.AddResult result = subtopicService.addSubtopics(themeId, List.of("  ", "Valid name"));

        assertEquals(1, result.added().size());
        assertEquals("Valid name", result.added().get(0).getName());
    }
}
