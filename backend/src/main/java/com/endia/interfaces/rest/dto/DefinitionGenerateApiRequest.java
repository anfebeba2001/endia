package com.endia.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to generate a short definition for a selected term (feature 016, FR-004/FR-010).
 * {@code themeId} supplies the prompt context; the term is normalized and bounded to at most 10
 * whitespace-separated tokens at the service boundary.
 */
public record DefinitionGenerateApiRequest(
        @NotNull(message = "themeId is required")
        UUID themeId,
        @NotBlank(message = "term must not be blank")
        String term
) {
}
