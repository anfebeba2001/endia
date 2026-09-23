package com.endia.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to generate a meaning snack from a keyword definition (feature 014, FR-001/FR-004).
 * {@code subtopicId} is the source/placement anchor, never a quiz target.
 */
public record MeaningSnackGenerateApiRequest(
        @NotNull(message = "themeId is required")
        UUID themeId,
        @NotNull(message = "subtopicId is required")
        UUID subtopicId,
        @NotBlank(message = "term must not be blank")
        String term,
        @NotBlank(message = "definition must not be blank")
        String definition
) {
}
