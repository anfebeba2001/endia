package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import com.endia.domain.exception.EntityNotFoundException;
import com.endia.domain.exception.InvalidSelectionException;
import com.endia.domain.exception.LlmGenerationException;
import com.endia.domain.exception.ServiceUnavailableException;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaThemeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.UUID;

/**
 * Generation of a short definition for an arbitrary term the learner selected in the snack reading
 * content (feature 016, FR-004/FR-005/FR-010/FR-011/FR-012).
 *
 * <p>This is the single normalization/validation boundary. It depends only on the
 * {@link LlmProvider} port (constitution III) and never references any provider class. A blank or
 * oversized term is rejected <strong>before</strong> any LLM call; the generated definition is
 * validated non-blank with exactly one re-roll and truncated to the {@value #MAX_DEFINITION_TOKENS}
 * token bound (constitution IV). Nothing is persisted.
 */
@Service
public class DefinitionService {

    private static final Logger log = LoggerFactory.getLogger(DefinitionService.class);

    /** Maximum number of whitespace-separated tokens accepted for a selected term (FR-010). */
    static final int MAX_TERM_TOKENS = 10;

    /** Maximum number of whitespace-separated tokens kept in the generated definition (FR-004). */
    static final int MAX_DEFINITION_TOKENS = 20;

    private final LlmProvider llmProvider;
    private final JpaThemeRepository themeRepository;

    public DefinitionService(LlmProvider llmProvider, JpaThemeRepository themeRepository) {
        this.llmProvider = llmProvider;
        this.themeRepository = themeRepository;
    }

    /**
     * Resolve the theme, normalize/validate the term, generate a bounded definition through the
     * provider-agnostic port and return it. The term is rejected (400) before any LLM call; the
     * definition is re-rolled once if blank, then a typed 503 is raised. Nothing is persisted.
     */
    public DefinitionResult generate(UUID themeId, String term) {
        Theme theme = themeRepository.findById(themeId)
                .orElseThrow(() -> new EntityNotFoundException("Theme", themeId));

        String normalizedTerm = term == null ? "" : term.trim().replaceAll("\\s+", " ");
        validateTerm(normalizedTerm);

        String definition = generateValid(theme.getPrompt(), normalizedTerm);
        return new DefinitionResult(normalizedTerm, definition);
    }

    /** Reject a blank or over-limit term with a typed 400 before any LLM call (FR-010/FR-011). */
    private void validateTerm(String normalizedTerm) {
        if (normalizedTerm.isEmpty()) {
            throw new InvalidSelectionException("Select a word or phrase to define.");
        }
        if (normalizedTerm.split("\\s+").length > MAX_TERM_TOKENS) {
            throw new InvalidSelectionException("Select no more than 10 words to define.");
        }
    }

    /** Generate and validate; re-roll exactly once on a blank definition, then fail with a typed 503. */
    private String generateValid(String theme, String term) {
        String definition = callGenerate(theme, term);
        if (!isValid(definition)) {
            log.warn("Definition validation failed for '{}', re-rolling once", term);
            definition = callGenerate(theme, term);
        }
        if (!isValid(definition)) {
            throw new ServiceUnavailableException(
                    "The AI service returned an incomplete definition. Please try again.");
        }
        return truncate(definition);
    }

    private boolean isValid(String definition) {
        return definition != null && !definition.isBlank();
    }

    /** Truncate to the first {@value #MAX_DEFINITION_TOKENS} whitespace tokens (never reject length). */
    private String truncate(String definition) {
        String trimmed = definition.trim().replaceAll("\\s+", " ");
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length <= MAX_DEFINITION_TOKENS) {
            return trimmed;
        }
        return String.join(" ", Arrays.copyOf(tokens, MAX_DEFINITION_TOKENS));
    }

    private String callGenerate(String theme, String term) {
        try {
            LlmProvider.GeneratedDefinition generated = llmProvider.generateDefinition(theme, term);
            return generated == null ? null : generated.definition();
        } catch (LlmGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceUnavailableException(
                    "The AI service is temporarily unavailable. Please try again later.", e);
        }
    }

    /** The normalized selected term and its generated, bounded definition. */
    public record DefinitionResult(String term, String definition) {
    }
}
