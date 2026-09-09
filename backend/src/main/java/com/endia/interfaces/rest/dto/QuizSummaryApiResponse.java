package com.endia.interfaces.rest.dto;

import java.util.List;
import java.util.UUID;

public record QuizSummaryApiResponse(
        UUID subtopicId,
        int totalQuestions,
        int correctAnswers,
        int totalXp,
        List<QuestionBreakdown> questions,
        Recap recap
) {
    public record QuestionBreakdown(
            UUID questionId,
            String type,
            String prompt,
            List<String> options,
            Integer chosenIndex,
            boolean correct,
            Integer correctIndex,
            String variant,
            List<String> correctArrangement,
            List<String> submittedArrangement,
            String explanation
    ) {
    }

    public record Recap(
            UUID subtopicId,
            String subtopicName,
            SnackDto snack
    ) {
    }

    public record SnackDto(
            UUID id,
            UUID subtopicId,
            String subtopicName,
            String concept,
            String metaphor,
            String whyItMatters,
            List<String> related
    ) {
    }
}
