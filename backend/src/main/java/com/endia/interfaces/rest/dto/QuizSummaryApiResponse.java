package com.endia.interfaces.rest.dto;

import java.util.List;
import java.util.UUID;

public record QuizSummaryApiResponse(
        UUID subtopicId,
        int totalQuestions,
        int correctAnswers,
        int totalXp,
        List<QuestionBreakdown> questions
) {
    public record QuestionBreakdown(
            UUID questionId,
            String prompt,
            List<String> options,
            int chosenIndex,
            boolean correct,
            int correctIndex,
            String explanation
    ) {
    }
}
