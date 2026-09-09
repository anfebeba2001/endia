package com.endia.interfaces.rest.dto;

import java.util.List;
import java.util.UUID;

public record QuizAnswerApiResponse(
        UUID questionId,
        String type,
        boolean correct,
        Integer correctIndex,
        List<String> correctArrangement,
        String explanation,
        int xpEarned
) {
}
