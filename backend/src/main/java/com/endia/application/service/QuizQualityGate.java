package com.endia.application.service;

import com.endia.application.port.LlmProvider;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates the quality of LLM-generated quiz questions.
 * Rules:
 *  - Exactly 4 options
 *  - No duplicate options (case-insensitive trimmed)
 *  - No option shorter than 5 characters
 *  - Options are roughly similar in length (plausible distractors)
 */
@Component
public class QuizQualityGate {

    private static final int EXPECTED_OPTIONS = 4;
    private static final int MIN_OPTION_LENGTH = 5;
    private static final double MAX_LENGTH_RATIO_DEVIATION = 0.4;

    /**
     * Returns true if the given question passes all quality checks.
     */
    public boolean validate(LlmProvider.GeneratedQuestion question) {
        return validate(question.prompt(), question.options(), question.correctIndex(), question.explanation());
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
     * Returns a human-readable description of the first failure found (for logging).
     */
    public String firstFailureReason(LlmProvider.GeneratedQuestion question) {
        return firstFailureReason(question.prompt(), question.options(), question.correctIndex(), question.explanation());
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
}
