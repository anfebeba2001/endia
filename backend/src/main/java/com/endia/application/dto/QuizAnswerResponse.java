package com.endia.application.dto;

import java.util.UUID;

public record QuizAnswerResponse(
        UUID questionId,
        boolean correct,
        int correctIndex,
        String explanation,
        int xpEarned
) {
}
