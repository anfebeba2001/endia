package com.endia.interfaces.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record QuizGenerateApiRequest(
        @NotNull(message = "themeId is required")
        UUID themeId,
        @NotNull(message = "subtopicId is required")
        UUID subtopicId,
        @Min(value = 4, message = "Count must be between 4 and 5")
        @Max(value = 5, message = "Count must be between 4 and 5")
        Integer count
) {
    public int countOrDefault() {
        return count == null ? 4 : count;
    }
}
