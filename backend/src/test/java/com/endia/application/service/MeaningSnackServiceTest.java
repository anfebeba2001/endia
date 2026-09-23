package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import com.endia.domain.enums.SnackKind;
import com.endia.domain.exception.EntityNotFoundException;
import com.endia.domain.exception.ServiceUnavailableException;
import com.endia.domain.model.MeaningSnack;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaMeaningSnackRepository;
import com.endia.infrastructure.persistence.JpaSubtopicRepository;
import com.endia.infrastructure.persistence.JpaThemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Feature 014 (US1 / M3): unit coverage of {@link MeaningSnackService} — normalization,
 * idempotency (no LLM call on an existing snack), the one re-roll, persistence as a
 * {@code MEANING} entity, and the structural no-quiz guarantee (FR-002, FR-004..FR-008, FR-014,
 * SC-001..SC-003, SC-005).
 *
 * <p>The duplicate-race cases stub {@link MeaningSnackWriter}, whose methods really do flush and
 * commit in their own transactions, so the mock matches runtime behaviour. The end-to-end proof
 * that the violation is raised through the <em>real</em> flush/commit path and recovered lives in
 * {@code MeaningSnackPersistenceIntegrationTest
 * #concurrentDuplicateInsertIsRecoveredThroughTheRealFlushPath}.
 */
@ExtendWith(MockitoExtension.class)
class MeaningSnackServiceTest {

    @Mock
    private LlmProvider llmProvider;
    @Mock
    private JpaThemeRepository themeRepository;
    @Mock
    private JpaSubtopicRepository subtopicRepository;
    @Mock
    private JpaMeaningSnackRepository meaningSnackRepository;
    @Mock
    private MeaningSnackWriter meaningSnackWriter;

    private MeaningSnackService service;
    private Theme theme;
    private Subtopic subtopic;
    private UUID themeId;
    private UUID subtopicId;

    @BeforeEach
    void setUp() {
        service = new MeaningSnackService(llmProvider, themeRepository, subtopicRepository,
                meaningSnackRepository, meaningSnackWriter);
        themeId = UUID.randomUUID();
        theme = new Theme("Photosynthesis");
        theme.setId(themeId);
        subtopicId = UUID.randomUUID();
        subtopic = new Subtopic(theme, "Light reactions");
        subtopic.setId(subtopicId);
    }

    private LlmProvider.GeneratedMeaningSnack valid() {
        return new LlmProvider.GeneratedMeaningSnack(
                "The light reactions convert light into chemical energy.",
                "Like a solar-powered factory floor.",
                "Without them, no ATP or NADPH is produced.",
                List.of("Calvin cycle", "Chloroplast"));
    }

    private void stubResolvable() {
        when(themeRepository.findById(themeId)).thenReturn(Optional.of(theme));
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.of(subtopic));
    }

    @Test
    void blankTermOrDefinitionIsRejected() {
        stubResolvable();

        assertThrows(ServiceUnavailableException.class,
                () -> service.generate(themeId, subtopicId, "  ", "light to sugar"));
        assertThrows(ServiceUnavailableException.class,
                () -> service.generate(themeId, subtopicId, "photosynthesis", "   "));

        verifyNoInteractions(llmProvider);
        verifyNoInteractions(meaningSnackWriter);
    }

    @Test
    void definitionIsTruncatedToFirstThreeTokens() {
        stubResolvable();
        when(llmProvider.generateMeaningSnack(anyString(), anyString(), anyString())).thenReturn(valid());
        when(meaningSnackWriter.insert(any(MeaningSnack.class))).thenAnswer(inv -> inv.getArgument(0));

        MeaningSnackService.GenerateResult result =
                service.generate(themeId, subtopicId, "photosynthesis", "light to sugar and more words");

        assertEquals("light to sugar", result.snack().getSourceDefinition());
    }

    @Test
    void termIsTrimmed() {
        stubResolvable();
        when(llmProvider.generateMeaningSnack(anyString(), anyString(), anyString())).thenReturn(valid());
        when(meaningSnackWriter.insert(any(MeaningSnack.class))).thenAnswer(inv -> inv.getArgument(0));

        MeaningSnackService.GenerateResult result =
                service.generate(themeId, subtopicId, "  photosynthesis  ", "light to sugar");

        assertEquals("photosynthesis", result.snack().getSourceTerm());
    }

    @Test
    void existingMeaningSnackIsReturnedWithoutCallingTheProvider() {
        stubResolvable();
        MeaningSnack existing = new MeaningSnack(theme, subtopic, "photosynthesis", "light to sugar",
                "c", "m", "w", List.of("r"));
        existing.setId(UUID.randomUUID());
        when(meaningSnackRepository.findBySourceSubtopicIdAndSourceTermIgnoreCase(subtopicId, "Photosynthesis"))
                .thenReturn(Optional.of(existing));

        MeaningSnackService.GenerateResult result =
                service.generate(themeId, subtopicId, "Photosynthesis", "light to sugar");

        assertTrue(result.alreadyExisted());
        assertEquals(existing, result.snack());
        verifyNoInteractions(llmProvider);
        verifyNoInteractions(meaningSnackWriter);
    }

    @Test
    void concurrentDuplicateInsertIsReturnedIdempotently() {
        stubResolvable();
        when(llmProvider.generateMeaningSnack(anyString(), anyString(), anyString())).thenReturn(valid());
        // The fast-path lookup misses, then the transactional insert loses the check-then-insert
        // race and the writer surfaces the unique-index violation synchronously.
        when(meaningSnackRepository.findBySourceSubtopicIdAndSourceTermIgnoreCase(subtopicId, "photosynthesis"))
                .thenReturn(Optional.empty());
        when(meaningSnackWriter.insert(any(MeaningSnack.class)))
                .thenThrow(new DataIntegrityViolationException("ux_meaning_snack_source"));
        // The winner is re-queried through the writer (fresh transaction) and returned idempotently.
        MeaningSnack winner = new MeaningSnack(theme, subtopic, "photosynthesis", "light to sugar",
                "c", "m", "w", List.of("r"));
        winner.setId(UUID.randomUUID());
        when(meaningSnackWriter.findExisting(subtopicId, "photosynthesis"))
                .thenReturn(Optional.of(winner));

        MeaningSnackService.GenerateResult result =
                service.generate(themeId, subtopicId, "photosynthesis", "light to sugar");

        assertTrue(result.alreadyExisted());
        assertEquals(winner, result.snack());
        verify(meaningSnackWriter).findExisting(subtopicId, "photosynthesis");
    }

    @Test
    void concurrentDuplicateInsertRethrowsWhenTheWinnerCannotBeReQueried() {
        stubResolvable();
        when(llmProvider.generateMeaningSnack(anyString(), anyString(), anyString())).thenReturn(valid());
        when(meaningSnackRepository.findBySourceSubtopicIdAndSourceTermIgnoreCase(subtopicId, "photosynthesis"))
                .thenReturn(Optional.empty());
        when(meaningSnackWriter.insert(any(MeaningSnack.class)))
                .thenThrow(new DataIntegrityViolationException("ux_meaning_snack_source"));
        when(meaningSnackWriter.findExisting(subtopicId, "photosynthesis"))
                .thenReturn(Optional.empty());

        assertThrows(DataIntegrityViolationException.class,
                () -> service.generate(themeId, subtopicId, "photosynthesis", "light to sugar"));
    }

    @Test
    void validGenerationPersistsAMeaningEntityBoundToThemeAndSubtopic() {
        stubResolvable();
        when(llmProvider.generateMeaningSnack(theme.getPrompt(), "photosynthesis", "light to sugar"))
                .thenReturn(valid());
        when(meaningSnackWriter.insert(any(MeaningSnack.class))).thenAnswer(inv -> inv.getArgument(0));

        MeaningSnackService.GenerateResult result =
                service.generate(themeId, subtopicId, "photosynthesis", "light to sugar");

        assertFalse(result.alreadyExisted());
        MeaningSnack saved = result.snack();
        assertEquals(SnackKind.MEANING, saved.getKind());
        assertEquals(theme, saved.getTheme());
        assertEquals(subtopic, saved.getSourceSubtopic());
        assertEquals("photosynthesis", saved.getSourceTerm());
        assertEquals("light to sugar", saved.getSourceDefinition());
        assertEquals(valid().related(), saved.getRelated());
        verify(meaningSnackWriter).insert(any(MeaningSnack.class));
        verify(llmProvider, times(1)).generateMeaningSnack(anyString(), anyString(), anyString());
    }

    @Test
    void invalidCoreReRollsExactlyOnceThenFails() {
        stubResolvable();
        LlmProvider.GeneratedMeaningSnack bad =
                new LlmProvider.GeneratedMeaningSnack("c", "", "w", List.of("r"));
        when(llmProvider.generateMeaningSnack(anyString(), anyString(), anyString())).thenReturn(bad);

        assertThrows(ServiceUnavailableException.class,
                () -> service.generate(themeId, subtopicId, "photosynthesis", "light to sugar"));

        verify(llmProvider, times(2)).generateMeaningSnack(anyString(), anyString(), anyString());
        verifyNoInteractions(meaningSnackWriter);
    }

    @Test
    void invalidThenValidReRollsOnceAndPersists() {
        stubResolvable();
        LlmProvider.GeneratedMeaningSnack bad =
                new LlmProvider.GeneratedMeaningSnack("c", "", "w", List.of("r"));
        when(llmProvider.generateMeaningSnack(anyString(), anyString(), anyString()))
                .thenReturn(bad)
                .thenReturn(valid());
        when(meaningSnackWriter.insert(any(MeaningSnack.class))).thenAnswer(inv -> inv.getArgument(0));

        MeaningSnackService.GenerateResult result =
                service.generate(themeId, subtopicId, "photosynthesis", "light to sugar");

        assertFalse(result.alreadyExisted());
        verify(llmProvider, times(2)).generateMeaningSnack(anyString(), anyString(), anyString());
    }

    @Test
    void listByThemeResolvesThemeAndReturnsRepositoryOrder() {
        when(themeRepository.findById(themeId)).thenReturn(Optional.of(theme));
        MeaningSnack a = new MeaningSnack(theme, subtopic, "a", "d", "c", "m", "w", List.of("r"));
        when(meaningSnackRepository.findByThemeIdOrderByCreatedAtAsc(themeId)).thenReturn(List.of(a));

        List<MeaningSnack> result = service.listByTheme(themeId);

        assertEquals(List.of(a), result);
    }

    @Test
    void listByThemeThrows404ForUnknownTheme() {
        when(themeRepository.findById(themeId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.listByTheme(themeId));
    }

    @Test
    void generateThrows404ForUnknownTheme() {
        when(themeRepository.findById(themeId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> service.generate(themeId, subtopicId, "term", "definition"));
    }

    @Test
    void generateThrows404ForUnknownSubtopic() {
        when(themeRepository.findById(themeId)).thenReturn(Optional.of(theme));
        when(subtopicRepository.findById(subtopicId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> service.generate(themeId, subtopicId, "term", "definition"));
    }

    @Test
    void serviceHasNoQuizOrBatchDependency() {
        for (Field field : MeaningSnackService.class.getDeclaredFields()) {
            String type = field.getType().getName();
            assertFalse(type.contains("QuizService"), "must not depend on QuizService");
            assertFalse(type.contains("BatchService"), "must not depend on BatchService");
            assertFalse(type.contains("StudySession"), "must not depend on StudySession");
            assertFalse(type.contains("QuizQuestion"), "must not depend on QuizQuestion");
        }
    }

    @Test
    void definitionWithNoExtraTokensIsKeptWhole() {
        stubResolvable();
        when(llmProvider.generateMeaningSnack(anyString(), anyString(), anyString())).thenReturn(valid());
        when(meaningSnackWriter.insert(any(MeaningSnack.class))).thenAnswer(inv -> inv.getArgument(0));

        MeaningSnackService.GenerateResult result =
                service.generate(themeId, subtopicId, "photosynthesis", "light to sugar");

        ArgumentCaptor<String> definition = ArgumentCaptor.forClass(String.class);
        verify(llmProvider).generateMeaningSnack(eq(theme.getPrompt()), eq("photosynthesis"), definition.capture());
        assertEquals("light to sugar", definition.getValue());
        assertEquals("light to sugar", result.snack().getSourceDefinition());
    }
}
