package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import com.endia.domain.exception.EntityNotFoundException;
import com.endia.domain.exception.InvalidSelectionException;
import com.endia.domain.exception.ServiceUnavailableException;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaThemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Feature 016 (US1/US2): unit coverage of {@link DefinitionService} — theme resolution, term
 * normalization and the ≤10-token bound (reject blank/oversized before any LLM call), the generated
 * definition's non-blank validation with exactly one re-roll, and the ≤20-token truncation
 * (FR-004, FR-005, FR-010, FR-011, FR-012, SC-005).
 */
@ExtendWith(MockitoExtension.class)
class DefinitionServiceTest {

    @Mock
    private LlmProvider llmProvider;
    @Mock
    private JpaThemeRepository themeRepository;

    private DefinitionService service;
    private Theme theme;
    private UUID themeId;

    @BeforeEach
    void setUp() {
        service = new DefinitionService(llmProvider, themeRepository);
        themeId = UUID.randomUUID();
        theme = new Theme("Photosynthesis");
        theme.setId(themeId);
    }

    private void stubResolvable() {
        when(themeRepository.findById(themeId)).thenReturn(Optional.of(theme));
    }

    private static String tokens(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "w" + i).collect(Collectors.joining(" "));
    }

    private LlmProvider.GeneratedDefinition valid() {
        return new LlmProvider.GeneratedDefinition("the process plants use to convert light into energy");
    }

    @Test
    void unknownThemeThrowsEntityNotFoundWithoutCallingTheProvider() {
        when(themeRepository.findById(themeId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.generate(themeId, "photosynthesis"));
        verifyNoInteractions(llmProvider);
    }

    @Test
    void blankTermIsRejectedWithoutCallingTheProvider() {
        stubResolvable();

        assertThrows(InvalidSelectionException.class, () -> service.generate(themeId, "   "));
        assertThrows(InvalidSelectionException.class, () -> service.generate(themeId, "\t\n "));
        assertThrows(InvalidSelectionException.class, () -> service.generate(themeId, null));

        verifyNoInteractions(llmProvider);
    }

    @Test
    void tenTokenTermIsAccepted() {
        stubResolvable();
        when(llmProvider.generateDefinition(anyString(), anyString())).thenReturn(valid());

        DefinitionService.DefinitionResult result = service.generate(themeId, tokens(10));

        assertEquals(tokens(10), result.term());
        verify(llmProvider).generateDefinition(theme.getPrompt(), tokens(10));
    }

    @Test
    void elevenTokenTermIsRejectedWithoutCallingTheProvider() {
        stubResolvable();

        assertThrows(InvalidSelectionException.class, () -> service.generate(themeId, tokens(11)));

        verifyNoInteractions(llmProvider);
    }

    @Test
    void termIsTrimmedAndWhitespaceCollapsed() {
        stubResolvable();
        when(llmProvider.generateDefinition(anyString(), anyString())).thenReturn(valid());

        DefinitionService.DefinitionResult result =
                service.generate(themeId, "  photo   synthesis\t across\nlines  ");

        assertEquals("photo synthesis across lines", result.term());
        verify(llmProvider).generateDefinition(theme.getPrompt(), "photo synthesis across lines");
    }

    @Test
    void hyphenatedCompoundCountsAsOneToken() {
        stubResolvable();
        when(llmProvider.generateDefinition(anyString(), anyString())).thenReturn(valid());

        // 10 hyphenated compounds are 10 whitespace tokens, so this is accepted.
        String term = IntStream.rangeClosed(1, 10).mapToObj(i -> "state-of-the-art-" + i)
                .collect(Collectors.joining(" "));
        DefinitionService.DefinitionResult result = service.generate(themeId, term);

        assertEquals(term, result.term());
    }

    @Test
    void validGenerationReturnsABoundedDefinition() {
        stubResolvable();
        when(llmProvider.generateDefinition(anyString(), anyString())).thenReturn(valid());

        DefinitionService.DefinitionResult result = service.generate(themeId, "photosynthesis");

        assertTrue(result.definition().split("\\s+").length <= 20);
    }

    @Test
    void generatedDefinitionOverTwentyTokensIsTruncatedToTwenty() {
        stubResolvable();
        when(llmProvider.generateDefinition(anyString(), anyString()))
                .thenReturn(new LlmProvider.GeneratedDefinition(tokens(25)));

        DefinitionService.DefinitionResult result = service.generate(themeId, "photosynthesis");

        assertEquals(tokens(20), result.definition());
        assertEquals(20, result.definition().split("\\s+").length);
    }

    @Test
    void blankGeneratedDefinitionReRollsExactlyOnceThenFails() {
        stubResolvable();
        when(llmProvider.generateDefinition(anyString(), anyString()))
                .thenReturn(new LlmProvider.GeneratedDefinition("   "));

        assertThrows(ServiceUnavailableException.class, () -> service.generate(themeId, "photosynthesis"));

        verify(llmProvider, times(2)).generateDefinition(anyString(), anyString());
    }

    @Test
    void blankThenValidReRollsOnceAndReturnsTheValidDefinition() {
        stubResolvable();
        when(llmProvider.generateDefinition(anyString(), anyString()))
                .thenReturn(new LlmProvider.GeneratedDefinition(""))
                .thenReturn(valid());

        DefinitionService.DefinitionResult result = service.generate(themeId, "photosynthesis");

        assertEquals(valid().definition(), result.definition());
        verify(llmProvider, times(2)).generateDefinition(anyString(), anyString());
    }

    @Test
    void providerExceptionIsMappedToServiceUnavailable() {
        stubResolvable();
        when(llmProvider.generateDefinition(anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        assertThrows(ServiceUnavailableException.class, () -> service.generate(themeId, "photosynthesis"));
    }

    @Test
    void providerIsCalledWithThemePromptAndNormalizedTerm() {
        stubResolvable();
        when(llmProvider.generateDefinition(anyString(), anyString())).thenReturn(valid());

        service.generate(themeId, "  Photosynthesis  ");

        verify(llmProvider).generateDefinition(eq(theme.getPrompt()), eq("Photosynthesis"));
    }

    @Test
    void nullGeneratedResultReRollsOnceThenFails() {
        stubResolvable();
        when(llmProvider.generateDefinition(anyString(), anyString())).thenReturn(null);

        assertThrows(ServiceUnavailableException.class, () -> service.generate(themeId, "photosynthesis"));

        verify(llmProvider, times(2)).generateDefinition(anyString(), anyString());
    }
}
