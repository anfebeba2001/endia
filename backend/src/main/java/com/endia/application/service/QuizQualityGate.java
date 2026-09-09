package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import com.endia.domain.enums.DragDropVariant;
import com.endia.domain.model.DragDropContent;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates the quality of LLM-generated quiz questions.
 * <p>
 * MCQ rules (existing):
 *  - Exactly 4 options
 *  - No duplicate options (case-insensitive trimmed)
 *  - No option shorter than 5 characters
 *  - Options are roughly similar in length (plausible distractors)
 * <p>
 * Drag-drop rules (FR-005, Constitution IV):
 *  - Variant in the allowed set
 *  - Items non-empty and distinct
 *  - Canonical arrangement composed of exactly the items
 *  - CLASSIFICATION: categories non-empty and every item categorized
 *  - MATCHING: every left key has a right value
 *  - SEQUENCING: correctOrder is a permutation of items
 */
@Component
public class QuizQualityGate {

    private static final int EXPECTED_OPTIONS = 4;
    private static final int MIN_OPTION_LENGTH = 5;
    private static final double MAX_LENGTH_RATIO_DEVIATION = 0.4;

    /**
     * Returns true if the given question passes all quality checks (by type).
     */
    public boolean validate(LlmProvider.GeneratedQuestion question) {
        if (question.type() == LlmProvider.GeneratedQuestion.QuestionType.DRAG_DROP) {
            return validateDragDrop(question);
        }
        return validate(question.prompt(), question.options(), question.correctIndex() == null ? -1 : question.correctIndex(), question.explanation());
    }

    public boolean validate(String prompt, List<String> options, int correctIndex, String explanation) {
        if (prompt == null || prompt.isBlank() || prompt.trim().length() < 5) {
            return false;
        }
        if (explanation == null || explanation.isBlank()) {
            return false;
        }
        if (options == null || options.size() != EXPECTED_OPTIONS) {
            return false;
        }
        if (correctIndex < 0 || correctIndex >= options.size()) {
            return false;
        }

        List<String> trimmed = options.stream()
                .map(o -> o == null ? "" : o.trim())
                .toList();

        // No empty or too-short options
        for (String option : trimmed) {
            if (option.isEmpty() || option.length() < MIN_OPTION_LENGTH) {
                return false;
            }
        }

        // No duplicate options (case-insensitive)
        Set<String> seen = new HashSet<>();
        for (String option : trimmed) {
            if (!seen.add(option.toLowerCase())) {
                return false;
            }
        }

        // Basic length similarity check (distractors should be plausibly similar length)
        int minLen = trimmed.stream().mapToInt(String::length).min().orElse(Integer.MAX_VALUE);
        int maxLen = trimmed.stream().mapToInt(String::length).max().orElse(0);
        if (minLen > 0 && (double) minLen / maxLen < MAX_LENGTH_RATIO_DEVIATION) {
            return false;
        }

        return true;
    }

    /**
     * Validates a drag-drop question's structure and canonical arrangement.
     */
    public boolean validateDragDrop(LlmProvider.GeneratedQuestion question) {
        DragDropContent content = question.dragDropContent();
        if (content == null) {
            return false;
        }
        if (question.prompt() == null || question.prompt().isBlank() || question.prompt().trim().length() < 5) {
            return false;
        }
        if (question.explanation() == null || question.explanation().isBlank()) {
            return false;
        }
        return validateDragDrop(content);
    }

    public boolean validateDragDrop(DragDropContent content) {
        DragDropVariant variant = content.variant();
        if (variant == null) {
            return false;
        }
        List<String> items = content.items() == null ? List.of() : content.items();
        if (items.isEmpty()) {
            return false;
        }
        if (new HashSet<>(items).size() != items.size()) {
            return false;
        }
        switch (variant) {
            case SEQUENCING -> {
                List<String> order = content.correctOrder() == null ? List.of() : content.correctOrder();
                return isPermutation(order, items);
            }
            case CLASSIFICATION -> {
                List<String> categories = content.categories() == null ? List.of() : content.categories();
                Map<String, String> pairs = content.pairs();
                if (categories.isEmpty() || pairs == null) {
                    return false;
                }
                return new HashSet<>(categories).containsAll(pairs.values())
                        && pairs.keySet().containsAll(items)
                        && new HashSet<>(categories).size() == categories.size();
            }
            case MATCHING -> {
                Map<String, String> pairs = content.pairs();
                if (pairs == null) {
                    return false;
                }
                return pairs.keySet().containsAll(items) && new HashSet<>(pairs.values()).size() == pairs.values().size();
            }
            default -> {
                return false;
            }
        }
    }

    private boolean isPermutation(List<String> candidate, List<String> items) {
        if (candidate.size() != items.size()) {
            return false;
        }
        return new HashSet<>(candidate).equals(new HashSet<>(items));
    }

    /**
     * Returns a human-readable description of the first failure found (for logging).
     */
    public String firstFailureReason(LlmProvider.GeneratedQuestion question) {
        if (question.type() == LlmProvider.GeneratedQuestion.QuestionType.DRAG_DROP) {
            return firstFailureReasonDragDrop(question);
        }
        return firstFailureReason(question.prompt(), question.options(),
                question.correctIndex() == null ? -1 : question.correctIndex(), question.explanation());
    }

    public String firstFailureReason(String prompt, List<String> options, int correctIndex, String explanation) {
        if (prompt == null || prompt.isBlank() || prompt.trim().length() < 5) {
            return "prompt too short or empty";
        }
        if (explanation == null || explanation.isBlank()) {
            return "explanation empty";
        }
        if (options == null || options.size() != EXPECTED_OPTIONS) {
            return "expected " + EXPECTED_OPTIONS + " options but got " + (options == null ? 0 : options.size());
        }
        if (correctIndex < 0 || correctIndex >= options.size()) {
            return "correctIndex out of range: " + correctIndex;
        }

        List<String> trimmed = options.stream().map(o -> o == null ? "" : o.trim()).toList();

        for (String option : trimmed) {
            if (option.isEmpty() || option.length() < MIN_OPTION_LENGTH) {
                return "option too short: '" + option + "'";
            }
        }

        Map<String, Long> counts = trimmed.stream().collect(Collectors.groupingBy(String::toLowerCase, Collectors.counting()));
        String dup = counts.entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).findFirst().orElse(null);
        if (dup != null) {
            return "duplicate option: '" + dup + "'";
        }

        int minLen = trimmed.stream().mapToInt(String::length).min().orElse(Integer.MAX_VALUE);
        int maxLen = trimmed.stream().mapToInt(String::length).max().orElse(0);
        if (minLen > 0 && (double) minLen / maxLen < MAX_LENGTH_RATIO_DEVIATION) {
            return "options too dissimilar in length: " + minLen + " vs " + maxLen;
        }

        return null;
    }

    private String firstFailureReasonDragDrop(LlmProvider.GeneratedQuestion question) {
        DragDropContent content = question.dragDropContent();
        if (content == null) {
            return "dragDropContent null";
        }
        if (question.prompt() == null || question.prompt().isBlank() || question.prompt().trim().length() < 5) {
            return "prompt too short or empty";
        }
        if (question.explanation() == null || question.explanation().isBlank()) {
            return "explanation empty";
        }
        DragDropVariant variant = content.variant();
        if (variant == null) {
            return "variant null";
        }
        List<String> items = content.items() == null ? List.of() : content.items();
        if (items.isEmpty()) {
            return "items empty";
        }
        if (new HashSet<>(items).size() != items.size()) {
            return "duplicate items";
        }
        switch (variant) {
            case SEQUENCING: {
                List<String> order = content.correctOrder() == null ? List.of() : content.correctOrder();
                return isPermutation(order, items) ? null : "correctOrder is not a permutation of items";
            }
            case CLASSIFICATION: {
                List<String> categories = content.categories() == null ? List.of() : content.categories();
                if (categories.isEmpty()) {
                    return "categories empty";
                }
                if (content.pairs() == null) {
                    return "pairs null";
                }
                if (!content.pairs().keySet().containsAll(items)) {
                    return "some item not categorized";
                }
                return null;
            }
            case MATCHING: {
                if (content.pairs() == null) {
                    return "pairs null";
                }
                if (!content.pairs().keySet().containsAll(items)) {
                    return "some left key has no value";
                }
                return null;
            }
            default:
                return "unknown variant";
        }
    }
}
