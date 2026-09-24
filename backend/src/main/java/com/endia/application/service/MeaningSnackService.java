package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import com.endia.domain.exception.EntityNotFoundException;
import com.endia.domain.exception.LlmGenerationException;
import com.endia.domain.exception.ServiceUnavailableException;
import com.endia.domain.model.MeaningSnack;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaMeaningSnackRepository;
import com.endia.infrastructure.persistence.JpaSubtopicRepository;
import com.endia.infrastructure.persistence.JpaThemeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Generation and retrieval of meaning snacks (feature 014, FR-001..FR-008, FR-014..FR-020).
 *
 * <p>This is the single validation/normalization boundary. It depends only on the
 * {@link LlmProvider} port (constitution III) and <strong>never</strong> references
 * {@code QuizService}, {@code BatchService}, {@code StudySessionItem} or {@code QuizQuestion}:
 * a meaning snack is structurally quiz-free (FR-014/FR-016).
 */
@Service
public class MeaningSnackService {

    private static final Logger log = LoggerFactory.getLogger(MeaningSnackService.class);

    /** Maximum number of whitespace-separated tokens kept in a source definition (feature 015). */
    private static final int MAX_DEFINITION_TOKENS = 20;

    /**
     * The {@code meaning_snack.source_definition} column is {@code VARCHAR(200)}; the normalized
     * source definition is clamped to this many characters on a word boundary so it always fits
     * without a schema change (feature 015, FR-019).
     */
    private static final int MAX_SOURCE_DEFINITION_CHARS = 200;

    private final LlmProvider llmProvider;
    private final JpaThemeRepository themeRepository;
    private final JpaSubtopicRepository subtopicRepository;
    private final JpaMeaningSnackRepository meaningSnackRepository;
    private final MeaningSnackWriter meaningSnackWriter;

    public MeaningSnackService(LlmProvider llmProvider,
                               JpaThemeRepository themeRepository,
                               JpaSubtopicRepository subtopicRepository,
                               JpaMeaningSnackRepository meaningSnackRepository,
                               MeaningSnackWriter meaningSnackWriter) {
        this.llmProvider = llmProvider;
        this.themeRepository = themeRepository;
        this.subtopicRepository = subtopicRepository;
        this.meaningSnackRepository = meaningSnackRepository;
        this.meaningSnackWriter = meaningSnackWriter;
    }

    /**
     * Idempotently generate and persist a meaning snack from a keyword definition. If one already
     * exists for {@code (subtopicId, term)} (case-insensitive), it is returned with
     * {@code alreadyExisted = true} and the LLM is never called (FR-005/FR-006).
     *
     * <p><strong>Deliberately non-transactional orchestration.</strong> The insert and the
     * duplicate-recovery re-query must each run in their own transaction (see
     * {@link MeaningSnackWriter}); wrapping this method in {@code @Transactional} would defer the
     * unique-index flush to the proxy's commit after this method returns, making the recovery
     * unreachable at runtime.
     */
    public GenerateResult generate(UUID themeId, UUID subtopicId, String term, String definition) {
        Theme theme = themeRepository.findById(themeId)
                .orElseThrow(() -> new EntityNotFoundException("Theme", themeId));
        Subtopic subtopic = subtopicRepository.findById(subtopicId)
                .orElseThrow(() -> new EntityNotFoundException("Subtopic", subtopicId));

        String normalizedTerm = term == null ? "" : term.trim();
        String normalizedDefinition = normalizeDefinition(definition);
        if (normalizedTerm.isEmpty() || normalizedDefinition.isEmpty()) {
            throw new ServiceUnavailableException("The keyword definition is incomplete. Please try again.");
        }

        Optional<MeaningSnack> existing = meaningSnackRepository
                .findBySourceSubtopicIdAndSourceTermIgnoreCase(subtopicId, normalizedTerm);
        if (existing.isPresent()) {
            return new GenerateResult(existing.get(), true);
        }

        LlmProvider.GeneratedMeaningSnack generated =
                generateValid(theme.getPrompt(), normalizedTerm, normalizedDefinition);

        MeaningSnack snack = new MeaningSnack(theme, subtopic, normalizedTerm, normalizedDefinition,
                generated.concept(), generated.metaphor(), generated.whyItMatters(), generated.related());
        try {
            MeaningSnack persisted = meaningSnackWriter.insert(snack);
            return new GenerateResult(persisted, false);
        } catch (DataIntegrityViolationException ex) {
            // Lost a check-then-insert race: a concurrent request inserted the same
            // (sourceSubtopicId, sourceTerm) first and hit ux_meaning_snack_source. The writer
            // flushed in its own transaction, so the violation is raised here (not at a later
            // commit) and is catchable. Re-query the winner in a fresh transaction — the failed
            // insert's transaction is rollback-only — and return it idempotently (same 200 +
            // alreadyExisted contract, no 500).
            log.info("Concurrent meaning-snack insert detected for '{}'; returning the existing item",
                    normalizedTerm);
            return meaningSnackWriter.findExisting(subtopicId, normalizedTerm)
                    .map(winner -> new GenerateResult(winner, true))
                    .orElseThrow(() -> ex);
        }
    }

    /** Theme-scoped listing, ordered by creation time (FR-018/FR-020). Empty list is valid. */
    @Transactional(readOnly = true)
    public List<MeaningSnack> listByTheme(UUID themeId) {
        themeRepository.findById(themeId)
                .orElseThrow(() -> new EntityNotFoundException("Theme", themeId));
        return meaningSnackRepository.findByThemeIdOrderByCreatedAtAsc(themeId);
    }

    /**
     * Normalize the source definition: truncate to the first {@value #MAX_DEFINITION_TOKENS}
     * whitespace-separated tokens, mirroring {@code SnackService.normalizeKeywords}, then clamp to
     * at most {@value #MAX_SOURCE_DEFINITION_CHARS} characters on a word boundary so it always fits
     * the existing {@code VARCHAR(200)} column without splitting a word (data-model.md §4.2, FR-019).
     */
    private String normalizeDefinition(String definition) {
        if (definition == null) {
            return "";
        }
        String trimmed = definition.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] tokens = trimmed.split("\\s+");
        String normalized = tokens.length > MAX_DEFINITION_TOKENS
                ? String.join(" ", Arrays.copyOf(tokens, MAX_DEFINITION_TOKENS))
                : trimmed;
        if (normalized.length() > MAX_SOURCE_DEFINITION_CHARS) {
            int cut = normalized.lastIndexOf(' ', MAX_SOURCE_DEFINITION_CHARS);
            normalized = cut > 0
                    ? normalized.substring(0, cut)
                    : normalized.substring(0, MAX_SOURCE_DEFINITION_CHARS);
        }
        return normalized;
    }

    /** Generate and validate; re-roll exactly once, then fail with a typed 503 (FR-007, SC-005). */
    private LlmProvider.GeneratedMeaningSnack generateValid(String theme, String term, String definition) {
        LlmProvider.GeneratedMeaningSnack generated = callGenerate(theme, term, definition);
        if (!isValid(generated)) {
            log.warn("Meaning-snack validation failed for '{}', re-rolling once", term);
            generated = callGenerate(theme, term, definition);
        }
        if (!isValid(generated)) {
            throw new ServiceUnavailableException(
                    "The AI service returned an incomplete meaning snack. Please try again.");
        }
        return generated;
    }

    private boolean isValid(LlmProvider.GeneratedMeaningSnack generated) {
        if (generated == null) {
            return false;
        }
        return isNonEmpty(generated.concept())
                && isNonEmpty(generated.metaphor())
                && isNonEmpty(generated.whyItMatters())
                && generated.related() != null
                && !generated.related().isEmpty()
                && generated.related().size() <= 3;
    }

    private boolean isNonEmpty(String value) {
        return value != null && !value.isBlank();
    }

    private LlmProvider.GeneratedMeaningSnack callGenerate(String theme, String term, String definition) {
        try {
            return llmProvider.generateMeaningSnack(theme, term, definition);
        } catch (LlmGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceUnavailableException(
                    "The AI service is temporarily unavailable. Please try again later.", e);
        }
    }

    /** Result of {@link #generate}: the persisted/found snack and whether it already existed. */
    public record GenerateResult(MeaningSnack snack, boolean alreadyExisted) {
    }
}
