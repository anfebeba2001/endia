package com.endia.interfaces.rest.dto;

import java.util.UUID;

public record QuizAnswerApiResponse(
        UUID questionId,
        boolean correct,
        int correctIndex,
        String explanation,
        int xpEarned
) {
}
