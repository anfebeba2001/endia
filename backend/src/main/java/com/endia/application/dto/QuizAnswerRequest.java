package com.endia.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record QuizAnswerRequest(
        @NotNull(message = "questionId is required")
        UUID questionId,
        @NotNull(message = "chosenIndex is required")
        @Min(value = 0, message = "Option index must be between 0 and 3")
        @Max(value = 3, message = "Option index must be between 0 and 3")
        Integer chosenIndex
) {
}
