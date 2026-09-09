package com.endia.interfaces.rest.dto;

import com.endia.domain.enums.DragDropVariant;
import com.endia.domain.model.DragDropContent;
import com.endia.domain.model.QuizQuestion;

import java.util.List;
import java.util.UUID;

public record QuizGenerateApiResponse(
        UUID subtopicId,
        List<QuestionItem> questions
) {
    public record QuestionItem(
            UUID id,
            String type,
            String prompt,
            List<String> options,
            String variant,
            DragDropDto dragDrop
    ) {
    }

    /**
     * Client-visible drag-drop payload — only the material to arrange/classify/match.
     * The canonical arrangement (pairs / correctOrder / category map) is NEVER sent here.
     */
    public record DragDropDto(
            List<String> items,
            List<String> categories,
            List<String> rightItems
    ) {
    }

    /**
     * Maps a persisted {@link QuizQuestion} to its client-visible DTO, reusing the same
     * shape for both POST /api/quiz/generate and the batch-status prefetch payload.
     */
    public static QuestionItem from(QuizQuestion q) {
        return new QuestionItem(
                q.getId(),
                q.getType().name(),
                q.getPrompt(),
                q.getOptions() == null ? List.of() : q.getOptions(),
                q.getVariant() == null ? null : q.getVariant().name(),
                toDragDropDto(q));
    }

    private static DragDropDto toDragDropDto(QuizQuestion q) {
        DragDropContent content = q.getDragDropContent();
        if (content == null) {
            return null;
        }
        List<String> rightItems = List.of();
        if (content.variant() == DragDropVariant.MATCHING) {
            rightItems = content.pairs().values().stream().distinct().toList();
        }
        return new DragDropDto(
                content.items(),
                content.categories(),
                rightItems);
    }
}
