package com.endia.interfaces.rest.dto;

import com.endia.domain.enums.GenerationStatus;
import com.endia.domain.enums.LlmErrorCause;

import java.util.List;
import java.util.UUID;

public record BatchStatusApiResponse(
        UUID batchId,
        UUID themeId,
        String status,
        boolean retrying,
        List<BatchItem> items
) {
    public record BatchItem(
            UUID subtopicId,
            String subtopicName,
            GenerationStatus snackStatus,
            String snackPhase,
            SnackDto snack,
            LlmErrorCause snackCause,
            GenerationStatus quizStatus,
            LlmErrorCause quizCause,
            List<QuizGenerateApiResponse.QuestionItem> quizQuestions
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
