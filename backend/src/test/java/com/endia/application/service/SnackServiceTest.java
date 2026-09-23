package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import com.endia.application.port.SnackPhaseListener;
import com.endia.domain.enums.SnackGenerationPhase;
import com.endia.domain.exception.EntityNotFoundException;
import com.endia.domain.exception.ServiceUnavailableException;
import com.endia.domain.model.Snack;
import com.endia.domain.model.SnackKeyword;
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
import static org.mockito.Mockito.times;
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

    // ---- 012: formatted markup passes validation with no spurious re-roll (FR-006) ----

    @Test
    void formattedMarkupContentPassesValidationWithoutReRoll() {
        when(themeRepository.findById(themeId)).thenReturn(Optional.of(theme));
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.of(subtopic));
        LlmProvider.GeneratedSnack formatted = new LlmProvider.GeneratedSnack(
                "**Light reactions** convert light into chemical energy.\n\n"
                        + "They happen in two stages:\n"
                        + "- **Capture** light energy.\n"
                        + "- **Fix** carbon into sugar.",
                "Think of a **solar-powered kitchen**.",
                "Without them, **no ATP or NADPH** is produced.",
                List.of("Calvin cycle", "Chloroplast"));
        when(llmProvider.generateSnack("Photosynthesis", "Light reactions")).thenReturn(formatted);
        when(snackRepository.save(any(Snack.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Snack> snacks = snackService.generateSnacks(themeId, List.of(subtopicId));

        assertEquals(1, snacks.size());
        // The markup-bearing text is persisted verbatim and is not treated as empty.
        assertEquals(formatted.concept(), snacks.get(0).getConcept());
        assertEquals(formatted.metaphor(), snacks.get(0).getMetaphor());
        assertEquals(formatted.whyItMatters(), snacks.get(0).getWhyItMatters());
        // Validation accepted the formatted content, so exactly one provider call happened.
        verify(llmProvider, times(1)).generateSnack("Photosynthesis", "Light reactions");
    }

    // ---- 013: keyword normalization at the service boundary (FR-007/FR-008/FR-009) ----

    private LlmProvider.GeneratedSnack validSnackWithKeywords(List<SnackKeyword> keywords) {
        return new LlmProvider.GeneratedSnack(
                "The light reactions convert light into chemical energy.",
                "Like a solar-powered factory floor.",
                "Without them, no ATP or NADPH is produced.",
                List.of("Calvin cycle", "Chloroplast"),
                keywords);
    }

    private Snack generateWith(LlmProvider.GeneratedSnack generated) {
        when(themeRepository.findById(themeId)).thenReturn(Optional.of(theme));
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.of(subtopic));
        when(llmProvider.generateSnack("Photosynthesis", "Light reactions")).thenReturn(generated);
        when(snackRepository.save(any(Snack.class))).thenAnswer(inv -> inv.getArgument(0));
        return snackService.generateSnacks(themeId, List.of(subtopicId)).get(0);
    }

    @Test
    void overLimitDefinitionIsTruncatedToFirstThreeTokens() {
        Snack snack = generateWith(validSnackWithKeywords(List.of(
                new SnackKeyword("mitosis", "one two three four five"))));

        assertEquals(1, snack.getKeywords().size());
        assertEquals("one two three", snack.getKeywords().get(0).definition());
    }

    @Test
    void blankTermOrDefinitionIsDropped() {
        Snack snack = generateWith(validSnackWithKeywords(List.of(
                new SnackKeyword("  ", "cell division"),
                new SnackKeyword("ATP", "   "),
                new SnackKeyword("kept", "ok"))));

        assertEquals(1, snack.getKeywords().size());
        assertEquals("kept", snack.getKeywords().get(0).term());
    }

    @Test
    void definitionBlankAfterTruncationIsDropped() {
        Snack snack = generateWith(validSnackWithKeywords(List.of(
                new SnackKeyword("x", "   "),
                new SnackKeyword("y", "valid one"))));

        assertEquals(1, snack.getKeywords().size());
        assertEquals("y", snack.getKeywords().get(0).term());
    }

    @Test
    void duplicateTermsAreDeduplicatedCaseInsensitivelyKeepingFirst() {
        Snack snack = generateWith(validSnackWithKeywords(List.of(
                new SnackKeyword("ATP", "energy currency"),
                new SnackKeyword("atp", "other definition"),
                new SnackKeyword("ATP", "third"))));

        assertEquals(1, snack.getKeywords().size());
        assertEquals("ATP", snack.getKeywords().get(0).term());
        assertEquals("energy currency", snack.getKeywords().get(0).definition());
    }

    @Test
    void valuesAreTrimmedAndOrderPreserved() {
        Snack snack = generateWith(validSnackWithKeywords(List.of(
                new SnackKeyword("  first  ", "  a b c  "),
                new SnackKeyword("second", "d e"))));

        assertEquals(List.of("first", "second"),
                snack.getKeywords().stream().map(SnackKeyword::term).toList());
        assertEquals("a b c", snack.getKeywords().get(0).definition());
    }

    @Test
    void allInvalidKeywordsYieldEmptyListWithNoReRoll() {
        Snack snack = generateWith(validSnackWithKeywords(List.of(
                new SnackKeyword("", ""),
                new SnackKeyword("  ", "  "))));

        assertTrue(snack.getKeywords().isEmpty());
        // Keywords never affect validity: exactly one provider call, no re-roll.
        verify(llmProvider, times(1)).generateSnack("Photosynthesis", "Light reactions");
    }

    @Test
    void nullKeywordListYieldsEmptyListWithoutReRoll() {
        Snack snack = generateWith(validSnackWithKeywords(null));

        assertTrue(snack.getKeywords().isEmpty());
        verify(llmProvider, times(1)).generateSnack("Photosynthesis", "Light reactions");
    }

    @Test
    void keywordsNeverCauseAReRollEvenWhenPresent() {
        Snack snack = generateWith(validSnackWithKeywords(List.of(
                new SnackKeyword("light", "electromagnetic radiation"))));

        assertEquals(1, snack.getKeywords().size());
        verify(llmProvider, times(1)).generateSnack("Photosynthesis", "Light reactions");
    }

    @Test
    void invalidCoreFieldStillReRollsExactlyOnceWithKeywordsPresent() {
        when(themeRepository.findById(themeId)).thenReturn(Optional.of(theme));
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.of(subtopic));
        LlmProvider.GeneratedSnack bad = new LlmProvider.GeneratedSnack(
                "Concept", "", "WhyItMatters", List.of("Related"),
                List.of(new SnackKeyword("concept", "an idea")));
        when(llmProvider.generateSnack("Photosynthesis", "Light reactions"))
                .thenReturn(bad)
                .thenReturn(validSnackWithKeywords(List.of(new SnackKeyword("light", "energy"))));
        when(snackRepository.save(any(Snack.class))).thenAnswer(inv -> inv.getArgument(0));

        Snack snack = snackService.generateSnacks(themeId, List.of(subtopicId)).get(0);

        assertEquals("light", snack.getKeywords().get(0).term());
        verify(llmProvider, times(2)).generateSnack("Photosynthesis", "Light reactions");
    }

    // ---- 009: the re-roll is exactly one (FR-005/FR-006/SC-002) ----

    @Test
    void invalidContentTwiceReRollsExactlyOnceThenThrowsTypedFailure() {
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.of(subtopic));
        LlmProvider.GeneratedSnack bad = new LlmProvider.GeneratedSnack(
                "Concept", "", "WhyItMatters", List.of("Related"));
        // Both the initial call and the single re-roll return invalid content.
        when(llmProvider.generateSnack("Photosynthesis", "Light reactions")).thenReturn(bad);

        assertThrows(ServiceUnavailableException.class,
                () -> snackService.generateSingleSnack(subtopicId));

        // Exactly two calls: the initial attempt plus one bounded re-roll. No third call.
        verify(llmProvider, times(2)).generateSnack("Photosynthesis", "Light reactions");
    }
}
