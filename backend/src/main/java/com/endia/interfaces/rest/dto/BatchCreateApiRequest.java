package com.endia.interfaces.rest.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BatchCreateApiRequest(
        @NotNull(message = "themeId is required")
        UUID themeId,
        @NotEmpty(message = "At least one subtopic ID is required")
        @Size(min = 1, max = 10, message = "Select between 1 and 10 subtopics")
        List<UUID> subtopicIds
) {
}
