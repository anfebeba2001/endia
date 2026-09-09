package com.endia.application.dto;

import java.util.List;
import java.util.UUID;

public record QuizGenerateResponse(
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
