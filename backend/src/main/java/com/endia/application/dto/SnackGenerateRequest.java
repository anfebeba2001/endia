package com.endia.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SnackGenerateRequest(
        @NotNull(message = "themeId is required")
        UUID themeId,
        @NotEmpty(message = "At least one subtopic ID is required")
        List<UUID> subtopicIds
) {
}
