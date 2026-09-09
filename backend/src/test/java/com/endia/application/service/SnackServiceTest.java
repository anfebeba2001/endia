package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import com.endia.domain.exception.EntityNotFoundException;
import com.endia.domain.model.Snack;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaSnackRepository;
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
class SnackServiceTest {

    @Mock
    private LlmProvider llmProvider;
    @Mock
    private JpaThemeRepository themeRepository;
    @Mock
    private JpaSubtopicRepository subtopicRepository;
    @Mock
    private JpaSnackRepository snackRepository;

    private SnackService snackService;

    private Theme theme;
    private Subtopic subtopic;
    private UUID themeId;
    private UUID subtopicId;

    @BeforeEach
    void setUp() {
        snackService = new SnackService(llmProvider, themeRepository, subtopicRepository, snackRepository);
        themeId = UUID.randomUUID();
        theme = new Theme("Photosynthesis");
        theme.setId(themeId);
        subtopicId = UUID.randomUUID();
        subtopic = new Subtopic(theme, "Light reactions");
        subtopic.setId(subtopicId);
    }

    private LlmProvider.GeneratedSnack validSnack() {
        return new LlmProvider.GeneratedSnack(
                "The light reactions convert light into chemical energy.",
                "Like a solar-powered factory floor.",
                "Without them, no ATP or NADPH is produced.",
                List.of("Calvin cycle", "Chloroplast")
        );
    }

    @Test
    void generateSnacksGeneratesAndPersistsOnePerSubtopic() {
        when(themeRepository.findById(themeId)).thenReturn(Optional.of(theme));
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.of(subtopic));
        when(llmProvider.generateSnack("Photosynthesis", "Light reactions")).thenReturn(validSnack());
        when(snackRepository.save(any(Snack.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Snack> snacks = snackService.generateSnacks(themeId, List.of(subtopicId));

        assertEquals(1, snacks.size());
        assertEquals("The light reactions convert light into chemical energy.", snacks.get(0).getConcept());
        verify(snackRepository).save(any(Snack.class));
    }

    @Test
    void generateSnacksThrows404WhenThemeNotFound() {
        when(themeRepository.findById(themeId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> snackService.generateSnacks(themeId, List.of(subtopicId)));
    }

    @Test
    void generateSnacksThrows404WhenSubtopicNotFound() {
        when(themeRepository.findById(themeId)).thenReturn(Optional.of(theme));
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> snackService.generateSnacks(themeId, List.of(subtopicId)));
    }

    @Test
    void rerollsSnackWhenFieldEmpty() {
        when(themeRepository.findById(themeId)).thenReturn(Optional.of(theme));
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.of(subtopic));
        LlmProvider.GeneratedSnack bad = new LlmProvider.GeneratedSnack(
                "Concept", "", "WhyItMatters", List.of("Related"));
        when(llmProvider.generateSnack("Photosynthesis", "Light reactions"))
                .thenReturn(bad)
                .thenReturn(validSnack());
        when(snackRepository.save(any(Snack.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Snack> snacks = snackService.generateSnacks(themeId, List.of(subtopicId));

        assertEquals(1, snacks.size());
        assertTrue(snacks.get(0).getMetaphor().length() > 0);
    }
}
