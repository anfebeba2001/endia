package com.endia.application.port;

import java.util.List;

/**
 * Port for LLM interactions. All LLM calls go through this interface so that
 * services and controllers never depend on any provider-specific classes.
 */
public interface LlmProvider {

    /**
     * Suggest 4-6 subtopics for a given theme.
     */
    List<String> suggestSubtopics(String theme);

    /**
     * Generate a knowledge snack card for a subtopic within a theme.
     * Returns a raw snack representation to be validated and persisted by the service.
     */
    GeneratedSnack generateSnack(String theme, String subtopicName);

    /**
     * Generate a list of quiz questions for a subtopic within a theme.
     */
    List<GeneratedQuestion> generateQuiz(String theme, String subtopicName, int count);

    record GeneratedSnack(String concept, String metaphor, String whyItMatters, List<String> related) {
    }

    record GeneratedQuestion(String prompt, List<String> options, int correctIndex, String explanation) {
    }
}
