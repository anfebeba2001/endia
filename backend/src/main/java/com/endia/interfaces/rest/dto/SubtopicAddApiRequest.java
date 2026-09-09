package com.endia.interfaces.rest.dto;

import java.util.List;
import java.util.UUID;

public record SubtopicAddApiRequest(
        @jakarta.validation.constraints.NotEmpty(message = "At least one subtopic name is required")
        @jakarta.validation.constraints.Size(min = 1, max = 10, message = "Add between 1 and 10 subtopics")
        List<@jakarta.validation.constraints.Size(min = 2, max = 200, message = "Subtopic name must be between 2 and 200 characters") String> names
) {
}
