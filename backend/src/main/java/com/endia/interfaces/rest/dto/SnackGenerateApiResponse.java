package com.endia.interfaces.rest.dto;

import java.util.List;
import java.util.UUID;

public record SnackGenerateApiResponse(
        List<SnackItem> snacks
) {
    public record SnackItem(
            UUID id,
            UUID subtopicId,
            String subtopicName,
            String concept,
            String metaphor,
            String whyItMatters,
            List<String> related,
            List<SnackKeywordDto> keywords
    ) {
    }
}
