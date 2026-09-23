package com.endia.interfaces.rest.dto;

import com.endia.domain.model.MeaningSnack;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * API representation of a meaning snack (feature 014). It mirrors the snack reading shape but
 * deliberately exposes <strong>no</strong> {@code subtopicId} quiz target, {@code quizStatus},
 * {@code quizQuestions} or {@code keywords}: the absence of those fields is the API-level
 * expression of the no-quiz invariant (FR-016, SC-005, data-model.md §6).
 */
public record MeaningSnackItem(
        UUID id,
        String kind,
        UUID themeId,
        UUID sourceSubtopicId,
        String sourceSubtopicName,
        String sourceTerm,
        String sourceDefinition,
        String concept,
        String metaphor,
        String whyItMatters,
        List<String> related,
        LocalDateTime createdAt
) {

    public static MeaningSnackItem from(MeaningSnack snack, String subtopicName) {
        return new MeaningSnackItem(
                snack.getId(),
                snack.getKind() != null ? snack.getKind().name() : null,
                snack.getTheme() != null ? snack.getTheme().getId() : null,
                snack.getSourceSubtopic() != null ? snack.getSourceSubtopic().getId() : null,
                subtopicName,
                snack.getSourceTerm(),
                snack.getSourceDefinition(),
                snack.getConcept(),
                snack.getMetaphor(),
                snack.getWhyItMatters(),
                snack.getRelated(),
                snack.getCreatedAt());
    }
}
