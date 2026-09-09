package com.endia.interfaces.rest.dto;

import java.util.List;
import java.util.UUID;

public record QuizGenerateApiResponse(
        UUID subtopicId,
        List<QuestionItem> questions
) {
    public record QuestionItem(
            UUID id,
            String prompt,
            List<String> options
    ) {
    }
}
