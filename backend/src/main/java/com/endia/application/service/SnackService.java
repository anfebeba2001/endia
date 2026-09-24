package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import com.endia.application.port.SnackPhaseListener;
import com.endia.domain.enums.SnackGenerationPhase;
import com.endia.domain.exception.EntityNotFoundException;
import com.endia.domain.exception.LlmGenerationException;
import com.endia.domain.exception.ServiceUnavailableException;
import com.endia.domain.model.Snack;
import com.endia.domain.model.SnackKeyword;
import com.endia.domain.model.Subtopic;
import com.endia.domain.model.Theme;
import com.endia.infrastructure.persistence.JpaSnackRepository;
import com.endia.infrastructure.persistence.JpaSubtopicRepository;
import com.endia.infrastructure.persistence.JpaThemeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class SnackService {

    private static final Logger log = LoggerFactory.getLogger(SnackService.class);

    /** Maximum number of whitespace-separated tokens kept in a keyword definition (feature 015). */
    private static final int MAX_DEFINITION_TOKENS = 20;

    private final LlmProvider llmProvider;
    private final JpaThemeRepository themeRepository;
    private final JpaSubtopicRepository subtopicRepository;
    private final JpaSnackRepository snackRepository;

    public SnackService(LlmProvider llmProvider,
                        JpaThemeRepository themeRepository,
                        JpaSubtopicRepository subtopicRepository,
                        JpaSnackRepository snackRepository) {
        this.llmProvider = llmProvider;
        this.themeRepository = themeRepository;
        this.subtopicRepository = subtopicRepository;
        this.snackRepository = snackRepository;
    }

    @Transactional
    public List<Snack> generateSnacks(UUID themeId, List<UUID> subtopicIds) {
        Theme theme = themeRepository.findById(themeId)
                .orElseThrow(() -> new EntityNotFoundException("Theme", themeId));

        String themePrompt = theme.getPrompt();

        List<Snack> snacks = new ArrayList<>();
        for (UUID subtopicId : subtopicIds) {
            Subtopic subtopic = subtopicRepository.findById(subtopicId)
                    .orElseThrow(() -> new EntityNotFoundException("Subtopic", subtopicId));

            Snack snack = generateSingleSnack(subtopic);
            snacks.add(snack);
        }
        return snacks;
    }

    /**
     * Batch-safe overload: resolves the subtopic (and lazily its theme) inside the
     * transaction so related data is never accessed on a detached instance (FR-001/FR-002).
     */
    @Transactional
    public Snack generateSingleSnack(UUID subtopicId) {
        return generateSingleSnack(subtopicId, SnackPhaseListener.NOOP);
    }

    /**
     * Listener-aware overload used by the batch worker: resolves the subtopic inside the
     * transaction and reports each real generation step to the listener (feature 008, FR-002).
     */
    @Transactional
    public Snack generateSingleSnack(UUID subtopicId, SnackPhaseListener listener) {
        Subtopic subtopic = subtopicRepository.findById(subtopicId)
                .orElseThrow(() -> new EntityNotFoundException("Subtopic", subtopicId));
        return generateAndPersist(subtopic, listener == null ? SnackPhaseListener.NOOP : listener);
    }

    /**
     * Generates and persists a single snack for a subtopic (reused by the batch worker).
     */
    @Transactional
    public Snack generateSingleSnack(Subtopic subtopic) {
        return generateAndPersist(subtopic, SnackPhaseListener.NOOP);
    }

    /**
     * Generates a single snack and invokes the callback once it is persisted.
     */
    @Transactional
    public Snack generateSingleSnack(Subtopic subtopic, Consumer<Snack> onComplete) {
        Snack snack = generateAndPersist(subtopic, SnackPhaseListener.NOOP);
        if (onComplete != null) {
            onComplete.accept(snack);
        }
        return snack;
    }

    /**
     * Shared generation/persistence logic for every overload. Callers must hold an open
     * persistence context so {@code subtopic.getTheme()} is resolvable. Emits the phase at the
     * exact real code points (feature 008, FR-001/FR-002).
     */
    private Snack generateAndPersist(Subtopic subtopic, SnackPhaseListener listener) {
        listener.onPhase(SnackGenerationPhase.RESOLVING_CONTEXT);
        String themePrompt = subtopic.getTheme().getPrompt();
        Snack snack = generateValidSnack(themePrompt, subtopic.getName(), listener);
        snack.setSubtopic(subtopic);
        listener.onPhase(SnackGenerationPhase.PERSISTING);
        snackRepository.save(snack);
        return snack;
    }

    /**
     * Generates a snack and validates all fields are non-empty. Re-rolls once if a field is empty.
     * Emits {@code AWAITING_MODEL} before each LLM call and {@code ASSEMBLING} once validation passes.
     */
    private Snack generateValidSnack(String theme, String subtopicName, SnackPhaseListener listener) {
        listener.onPhase(SnackGenerationPhase.AWAITING_MODEL);
        LlmProvider.GeneratedSnack generated = callGenerateSnack(theme, subtopicName);
        if (!isValid(generated)) {
            log.warn("Snack validation failed for '{}', re-rolling once", subtopicName);
            listener.onPhase(SnackGenerationPhase.AWAITING_MODEL);
            generated = callGenerateSnack(theme, subtopicName);
        }
        if (!isValid(generated)) {
            throw new ServiceUnavailableException("The AI service returned an incomplete snack. Please try again.");
        }
        listener.onPhase(SnackGenerationPhase.ASSEMBLING);
        return new Snack(null, generated.concept(), generated.metaphor(),
                generated.whyItMatters(), generated.related(), normalizeKeywords(generated.keywords()));
    }

    /**
     * Normalizes the model-provided keyword list (feature 013, FR-007..FR-009), order-preserving:
     * drops nulls and blank terms/definitions, trims values, truncates definitions to the first
     * 20 whitespace-separated tokens, drops empty-after-truncation entries, and de-duplicates by
     * trimmed term case-insensitively (keeping the first).
     *
     * <p>The result may be empty; keywords are <strong>never</strong> consulted by {@link #isValid},
     * so their presence, absence or invalidity can never trigger a re-roll or a failure.
     */
    private List<SnackKeyword> normalizeKeywords(List<SnackKeyword> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<SnackKeyword> normalized = new ArrayList<>();
        Set<String> seenTerms = new HashSet<>();
        for (SnackKeyword keyword : raw) {
            if (keyword == null) {
                continue;
            }
            String term = keyword.term() == null ? "" : keyword.term().trim();
            String definition = keyword.definition() == null ? "" : keyword.definition().trim();
            if (term.isEmpty() || definition.isEmpty()) {
                continue;
            }
            String[] tokens = definition.split("\\s+");
            if (tokens.length > MAX_DEFINITION_TOKENS) {
                definition = String.join(" ", Arrays.copyOf(tokens, MAX_DEFINITION_TOKENS));
            }
            if (definition.isEmpty()) {
                continue;
            }
            if (!seenTerms.add(term.toLowerCase(Locale.ROOT))) {
                continue;
            }
            normalized.add(new SnackKeyword(term, definition));
        }
        return normalized;
    }

    private boolean isValid(LlmProvider.GeneratedSnack snack) {
        if (snack == null) {
            return false;
        }
        return isNonEmpty(snack.concept())
                && isNonEmpty(snack.metaphor())
                && isNonEmpty(snack.whyItMatters())
                && snack.related() != null
                && !snack.related().isEmpty()
                && snack.related().size() <= 3;
    }

    private boolean isNonEmpty(String value) {
        return value != null && !value.isBlank();
    }

    private LlmProvider.GeneratedSnack callGenerateSnack(String theme, String subtopicName) {
        try {
            return llmProvider.generateSnack(theme, subtopicName);
        } catch (LlmGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceUnavailableException("The AI service is temporarily unavailable. Please try again later.", e);
        }
    }
}
