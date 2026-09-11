package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import com.endia.domain.exception.EntityNotFoundException;
import com.endia.domain.exception.LlmGenerationException;
import com.endia.domain.exception.ServiceUnavailableException;
import com.endia.domain.model.Snack;
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
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class SnackService {

    private static final Logger log = LoggerFactory.getLogger(SnackService.class);

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
        Subtopic subtopic = subtopicRepository.findById(subtopicId)
                .orElseThrow(() -> new EntityNotFoundException("Subtopic", subtopicId));
        return generateAndPersist(subtopic);
    }

    /**
     * Generates and persists a single snack for a subtopic (reused by the batch worker).
     */
    @Transactional
    public Snack generateSingleSnack(Subtopic subtopic) {
        return generateAndPersist(subtopic);
    }

    /**
     * Generates a single snack and invokes the callback once it is persisted.
     */
    @Transactional
    public Snack generateSingleSnack(Subtopic subtopic, Consumer<Snack> onComplete) {
        Snack snack = generateAndPersist(subtopic);
        if (onComplete != null) {
            onComplete.accept(snack);
        }
        return snack;
    }

    /**
     * Shared generation/persistence logic for every overload. Callers must hold an open
     * persistence context so {@code subtopic.getTheme()} is resolvable.
     */
    private Snack generateAndPersist(Subtopic subtopic) {
        String themePrompt = subtopic.getTheme().getPrompt();
        Snack snack = generateValidSnack(themePrompt, subtopic.getName());
        snack.setSubtopic(subtopic);
        snackRepository.save(snack);
        return snack;
    }

    /**
     * Generates a snack and validates all fields are non-empty. Re-rolls once if a field is empty.
     */
    private Snack generateValidSnack(String theme, String subtopicName) {
        LlmProvider.GeneratedSnack generated = callGenerateSnack(theme, subtopicName);
        if (!isValid(generated)) {
            log.warn("Snack validation failed for '{}', re-rolling once", subtopicName);
            generated = callGenerateSnack(theme, subtopicName);
        }
        if (!isValid(generated)) {
            throw new ServiceUnavailableException("The AI service returned an incomplete snack. Please try again.");
        }
        return new Snack(null, generated.concept(), generated.metaphor(),
                generated.whyItMatters(), generated.related());
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
