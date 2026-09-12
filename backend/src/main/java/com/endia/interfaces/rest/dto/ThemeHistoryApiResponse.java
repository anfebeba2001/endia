package com.endia.interfaces.rest.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for feature 011 {@code GET /api/themes}: a read-only history entry with its nested
 * subtopics, snacks and quiz questions. The controller returns a {@code List} of these, so the
 * HTTP body is a JSON array (empty history → {@code []} with HTTP 200).
 */
public record ThemeHistoryApiResponse(
        UUID themeId,
        String prompt,
        LocalDateTime createdAt,
        List<SubtopicItem> subtopics
) {
    public record SubtopicItem(UUID id, String name, List<SnackItem> snacks,
                               List<QuestionItem> quizQuestions) {
    }

    public record SnackItem(UUID id, String concept, String metaphor, String whyItMatters,
                            List<String> related) {
    }

    public record QuestionItem(UUID id, String type, String prompt, String explanation) {
    }
}
