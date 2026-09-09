package com.endia.domain.model;

import com.endia.domain.enums.DragDropVariant;

import java.util.List;
import java.util.Map;

/**
 * Value object holding the structured drag-drop content and its server-side
 * canonical arrangement (the source of truth for grading, FR-008).
 * <p>
 * The canonical arrangement ({@code pairs} / {@code correctOrder} / per-item
 * category) is stored here and never serialized to the client before the answer.
 */
public record DragDropContent(
        DragDropVariant variant,
        List<String> items,
        List<String> categories,
        Map<String, String> pairs,
        List<String> correctOrder
) {
    public DragDropContent {
        items = items == null ? List.of() : List.copyOf(items);
        categories = categories == null ? List.of() : List.copyOf(categories);
        pairs = pairs == null ? Map.of() : Map.copyOf(pairs);
        correctOrder = correctOrder == null ? List.of() : List.copyOf(correctOrder);
    }
}
