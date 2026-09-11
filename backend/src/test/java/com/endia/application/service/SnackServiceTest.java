package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import com.endia.application.port.SnackPhaseListener;
import com.endia.domain.enums.SnackGenerationPhase;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    // ---- T069: generateSingleSnack single-snack path ----

    @Test
    void generateSingleSnackPersistsOneSnack() {
        when(llmProvider.generateSnack("Photosynthesis", "Light reactions")).thenReturn(validSnack());
        when(snackRepository.save(any(Snack.class))).thenAnswer(inv -> inv.getArgument(0));

        Snack snack = snackService.generateSingleSnack(subtopic);

        assertEquals("The light reactions convert light into chemical energy.", snack.getConcept());
        assertEquals(subtopic, snack.getSubtopic());
        verify(snackRepository).save(any(Snack.class));
    }

    @Test
    void generateSingleSnackInvokesCallback() {
        when(llmProvider.generateSnack("Photosynthesis", "Light reactions")).thenReturn(validSnack());
        when(snackRepository.save(any(Snack.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean[] called = {false};
        Snack snack = snackService.generateSingleSnack(subtopic, s -> called[0] = true);

        assertTrue(called[0]);
        assertEquals(subtopic, snack.getSubtopic());
    }

    // ---- 005: batch by-id overload resolves related data inside the transaction ----

    @Test
    void generateSingleSnackByIdLoadsSubtopicAndThemeAndPersists() {
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.of(subtopic));
        when(llmProvider.generateSnack("Photosynthesis", "Light reactions")).thenReturn(validSnack());
        when(snackRepository.save(any(Snack.class))).thenAnswer(inv -> inv.getArgument(0));

        Snack snack = snackService.generateSingleSnack(subtopicId);

        assertEquals("The light reactions convert light into chemical energy.", snack.getConcept());
        assertEquals(subtopic, snack.getSubtopic());
        // The related subtopic (and lazily its theme) must be resolved via the repository inside the transaction.
        verify(subtopicRepository).findById(subtopicId);
        verify(snackRepository).save(any(Snack.class));
    }

    @Test
    void generateSingleSnackByIdThrowsWhenSubtopicNotFound() {
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> snackService.generateSingleSnack(subtopicId));
    }

    // ---- 008: listener is invoked at the real code points in order (FR-001/FR-002) ----

    @Test
    void listenerReceivesPhasesInRealOrder() {
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.of(subtopic));
        when(llmProvider.generateSnack("Photosynthesis", "Light reactions")).thenReturn(validSnack());
        when(snackRepository.save(any(Snack.class))).thenAnswer(inv -> inv.getArgument(0));

        List<SnackGenerationPhase> phases = new ArrayList<>();
        Snack snack = snackService.generateSingleSnack(subtopicId, phases::add);

        assertEquals("The light reactions convert light into chemical energy.", snack.getConcept());
        assertEquals(List.of(
                SnackGenerationPhase.RESOLVING_CONTEXT,
                SnackGenerationPhase.AWAITING_MODEL,
                SnackGenerationPhase.ASSEMBLING,
                SnackGenerationPhase.PERSISTING), phases);
    }

    @Test
    void listenerReceivesRerollSequenceForInvalidFirstResponse() {
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.of(subtopic));
        LlmProvider.GeneratedSnack bad = new LlmProvider.GeneratedSnack(
                "Concept", "", "WhyItMatters", List.of("Related"));
        when(llmProvider.generateSnack("Photosynthesis", "Light reactions"))
                .thenReturn(bad)
                .thenReturn(validSnack());
        when(snackRepository.save(any(Snack.class))).thenAnswer(inv -> inv.getArgument(0));

        List<SnackGenerationPhase> phases = new ArrayList<>();
        snackService.generateSingleSnack(subtopicId, phases::add);

        assertEquals(List.of(
                SnackGenerationPhase.RESOLVING_CONTEXT,
                SnackGenerationPhase.AWAITING_MODEL,
                SnackGenerationPhase.AWAITING_MODEL,
                SnackGenerationPhase.ASSEMBLING,
                SnackGenerationPhase.PERSISTING), phases);
    }

    @Test
    void singleShotOverloadsDoNotRequireAListener() {
        // The NOOP default keeps single-shot generation unchanged (spec edge case).
        assertDoesNotThrow(() -> SnackPhaseListener.NOOP.onPhase(SnackGenerationPhase.AWAITING_MODEL));
        when(llmProvider.generateSnack("Photosynthesis", "Light reactions")).thenReturn(validSnack());
        when(snackRepository.save(any(Snack.class))).thenAnswer(inv -> inv.getArgument(0));

        Snack snack = snackService.generateSingleSnack(subtopic);

        assertEquals("The light reactions convert light into chemical energy.", snack.getConcept());
        verify(snackRepository).save(any(Snack.class));
    }
}
